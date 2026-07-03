package com.flx_apps.digitaldetox.features

import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.flx_apps.digitaldetox.system_integration.UsageStatsSnapshotEntryPoint
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.DailyAppCounter
import com.flx_apps.digitaldetox.util.ScrollDeltaCalibrator
import com.flx_apps.digitaldetox.util.ScrollDistanceEstimator
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * Always-active tracker that collects usage statistics (scroll events, app opens, etc.)
 * independently of any user-facing DetoxDroid feature.
 *
 * This is not a full [Feature] — it never appears in the feature settings list and cannot
 * be toggled by the user. It is called directly from the accessibility service and provides
 * event counters consumed by the Usage Stats screen and snapshot worker.
 *
 * To survive process restarts, scroll counters are persisted to Room on a debounced interval
 * after every scroll event, and all daily counters (scrolls, breaks, blocks) are restored from
 * Room on initialisation so the live display is correct immediately after the process comes back.
 */
object UsageStatsTracker {
    val scrollEventCounter = DailyAppCounter()

    /**
     * Per-app scrolled distance in screen pixels (the "scroll odometer"). Apps that report
     * [android.view.accessibility.AccessibilityEvent.getScrollDeltaY] in pixels (RecyclerView,
     * Compose — i.e. the typical doom-scrolling apps) are measured exactly; for the rest
     * (position-based deltas, missing deltas, Android 8) each scroll gesture is conservatively
     * estimated from the screen height — see [ScrollDistanceEstimator].
     */
    val scrollDistanceCounter = DailyAppCounter()

    /** Weights scroll events by their in-app relative scroll distance (see the class docs). */
    private val scrollCalibrator = ScrollDeltaCalibrator()

    private val scrollDistanceEstimator by lazy {
        ScrollDistanceEstimator(Resources.getSystem().displayMetrics.heightPixels)
    }

    /**
     * Minimum interval between consecutive scroll-triggered snapshots. The first scroll
     * event after initialisation, and each subsequent event after at least this much time,
     * causes a snapshot to Room so at most this much data is lost on a force-close.
     */
    private const val SCROLL_SNAPSHOT_DEBOUNCE_MS = 3L * 60 * 1000 // 3 minutes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var entryPoint: UsageStatsSnapshotEntryPoint? = null
    private var lastSnapshotMs = 0L
    private var initialized = false

    /**
     * Initialises the tracker. Idempotent — safe to call from both
     * [android.app.Application.onCreate] and [android.app.Service.onCreate].
     *
     * 1. Resolves the Hilt entry-point for the [com.flx_apps.digitaldetox.data.repository.UsageStatsRepository].
     * 2. Restores today's persisted counters (scrolls, doom-scroll breaks, app blocks) from Room
     *    into the in-memory counters so the UI shows accurate data right after a process restart;
     *    the repository also sets the snapshot baselines so nothing is double-counted.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsageStatsSnapshotEntryPoint::class.java
        )
        scope.launch {
            runCatching { entryPoint!!.repository().restoreTodayCounters() }
                .onFailure { Timber.w(it, "Failed to restore daily counters on init") }
        }
    }

    /**
     * Takes a final snapshot to preserve any in-memory scroll counts accumulated since
     * the last debounced snapshot. Call from [android.app.Service.onDestroy].
     */
    fun shutdown() {
        scope.launch {
            runCatching { entryPoint?.repository()?.snapshotToday() }
                .onFailure { Timber.w(it, "Final scroll snapshot on shutdown failed") }
        }
    }

    /**
     * Registers a scroll event for usage statistics and, every
     * [SCROLL_SNAPSHOT_DEBOUNCE_MS], persists the accumulated counter to Room so
     * the data survives process restarts.
     */
    fun onScrollEvent(accessibilityEvent: AccessibilityEvent) {
        val pkg = accessibilityEvent.packageName?.toString() ?: return
        val deltaMagnitude = abs(AccessibilityEventUtil.getScrollDeltaY(accessibilityEvent))
        scrollEventCounter.increment(pkg, scrollCalibrator.weightFor(pkg, deltaMagnitude))
        val distancePx = scrollDistanceEstimator.distancePxFor(pkg, deltaMagnitude)
        if (distancePx > 0) {
            scrollDistanceCounter.increment(pkg, distancePx.toDouble())
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastSnapshotMs >= SCROLL_SNAPSHOT_DEBOUNCE_MS) {
            lastSnapshotMs = now
            scope.launch {
                runCatching { entryPoint?.repository()?.snapshotToday() }
                    .onFailure { Timber.w(it, "Scroll-triggered snapshot failed") }
            }
        }
    }
}
