package com.flx_apps.digitaldetox.features

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.feature_types.SupportsScheduleFeature
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakDoomScrollingOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling.BreakScreenMode
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.CooldownRegistry
import com.flx_apps.digitaldetox.util.DailyAppCounter
import com.flx_apps.digitaldetox.util.FinishGraceTracker
import com.flx_apps.digitaldetox.util.ScrollDistanceEstimator
import com.flx_apps.digitaldetox.util.ScrollSessionMonitor
import com.flx_apps.digitaldetox.util.ScrollSessionSnapshot
import com.flx_apps.digitaldetox.util.SelfExpiringHashMap
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

val BreakDoomScrollingFeatureId = Feature.createId(BreakDoomScrollingFeature::class.java)

/**
 * How easily the scroll-intensity trigger of [BreakDoomScrollingFeature] fires. Thresholds apply
 * to the live [ScrollSessionSnapshot] of the current scroll session: STRICT intervenes at slower
 * scrolling, RELAXED only at frantic feed-flicking. The endless-feed (growth) trigger is not
 * affected by the sensitivity.
 */
enum class DoomScrollingSensitivity(
    val minScreenHeightsPerMinute: Float,
    val minDownScrollRatio: Float
) {
    RELAXED(14f, 0.85f),
    BALANCED(9f, 0.8f),
    STRICT(6f, 0.75f);

    /**
     * Returns true if [snapshot] shows sustained (≥ [minSessionDurationMs]), fast, mostly-downward
     * scrolling — the intensity signature of doom scrolling.
     */
    fun isDoomScrolling(snapshot: ScrollSessionSnapshot, minSessionDurationMs: Long): Boolean =
        snapshot.sessionDurationMs >= minSessionDurationMs &&
                snapshot.screenHeightsPerMinute >= minScreenHeightsPerMinute &&
                snapshot.downScrollRatio >= minDownScrollRatio
}

/**
 * This feature detects doom scrolling and opens a break screen when it has been going on for
 * [timeUntilWarning]. Two independent triggers feed the same break screen:
 * - **Endless screen**: a scroll view whose reported size keeps growing (classic infinite feed).
 * - **Scroll intensity**: sustained, fast, mostly-downward scrolling in an app, measured by
 *   [ScrollSessionMonitor] — catches virtualized feeds that never report growth. Its thresholds
 *   are user-tunable via [detectionSensitivity].
 *
 * The break screen offers two ways out:
 * - **"Get me out of here"**: leave immediately (home screen, best-effort force-stop).
 * - **"I'll finish this one, then leave"**: a bounded grace ([FinishGraceTracker]) to finish the
 *   item currently on screen; once the user scrolls on a few more times or the grace times out,
 *   they are guided out automatically.
 *
 * Either way the app enters a *cooldown* ([cooldownTime], [CooldownRegistry]) during which
 * re-entry is blocked. When the triggering scroll view had a view-id resource name, the cooldown
 * is scoped to that surface — e.g. Instagram's reels stay locked while the DM list remains
 * usable; otherwise the whole app is locked, including on app open.
 */
object BreakDoomScrollingFeature : Feature(), OnScrollEventSubscriptionFeature,
    OnAppOpenedSubscriptionFeature, OnScreenTurnedOffSubscriptionFeature,
    SupportsAppExceptionsFeature by SupportsAppExceptionsFeature.Impl(
        BreakDoomScrollingFeatureId,
        defaultExceptionListType = AppExceptionListType.ONLY_LIST,
    ), SupportsScheduleFeature by SupportsScheduleFeature.Impl(BreakDoomScrollingFeatureId),
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(), LockableFeature, PausableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_doomScrolling,
        R.string.feature_doomScrolling_subtitle,
        R.string.feature_doomScrolling_description,
    )
    override val iconRes = R.drawable.ic_scroll
    override val settingsContent: @Composable () -> Unit =
        { BreakDoomScrollingFeatureSettingsSection() }

    /**
     * The time in milliseconds until the break screen is opened, if the user has been scrolling for
     * too long.
     */
    var timeUntilWarning: Long by DataStoreProperty(
        longPreferencesKey("${BreakDoomScrollingFeatureId}_timeUntilWarning"),
        TimeUnit.MINUTES.toMillis(3)
    )

    /**
     * How long an app (or the offending surface within it) stays locked after a doom-scrolling
     * incident, so the user cannot dive right back in.
     */
    var cooldownTime: Long by DataStoreProperty(
        longPreferencesKey("${BreakDoomScrollingFeatureId}_cooldownTime"),
        TimeUnit.MINUTES.toMillis(10)
    )

    /**
     * How easily the scroll-intensity trigger fires.
     * @see DoomScrollingSensitivity
     */
    var detectionSensitivity: DoomScrollingSensitivity by DataStoreProperty(
        stringPreferencesKey("${BreakDoomScrollingFeatureId}_detectionSensitivity"),
        DoomScrollingSensitivity.BALANCED,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
            DoomScrollingSensitivity::class.java
        )
    )

    /**
     * Estimates per-event scroll distances for the intensity trigger. Deliberately a separate
     * instance from the one in [UsageStatsTracker]: the estimator rate-limits its distance
     * estimates internally, so a shared instance would let this feature and the measurement
     * odometer steal each other's estimates.
     */
    private val scrollDistanceEstimator by lazy {
        ScrollDistanceEstimator(Resources.getSystem().displayMetrics.heightPixels)
    }

    /** Tracks per-app scroll sessions for the intensity trigger. */
    private val scrollSessionMonitor by lazy {
        ScrollSessionMonitor(Resources.getSystem().displayMetrics.heightPixels)
    }

    /**
     * Per-app count of doom-scroll breaks triggered today. Resets automatically at midnight and is
     * persisted into the usage-stats database by the snapshot worker.
     */
    val breakCounter = DailyAppCounter()

    /**
     * Contains information about all currently active scroll views, associated with their IDs.
     * The information is automatically removed, if a scroll view has not been scrolled for one
     * minute.
     */
    private val activeScrollViews: SelfExpiringHashMap<Int, ScrollViewInfo> =
        SelfExpiringHashMap(2.minutes.inWholeMilliseconds)

    /** Apps/surfaces that are locked after an incident until their cooldown expires. */
    private val cooldowns = CooldownRegistry()

    /** Running "finish what you're watching" graces (see the class docs). */
    private val finishGrace = FinishGraceTracker()

    /** Schedules the grace timeouts; everything here runs on the main thread. */
    private val graceHandler by lazy { Handler(Looper.getMainLooper()) }

    /** The pending grace-timeout runnable per app, so it can be cancelled when the grace ends. */
    private val graceTimeouts = HashMap<String, Runnable>()

    /** The scroll surface (view-id resource name) each app's last warning was triggered on. */
    private val lastTriggerSurfaceIds = HashMap<String, String?>()

    /**
     * Last time any break screen was shown per app. Scroll-momentum events arriving right after
     * an overlay opened must not re-deliver it in cooldown mode (which would e.g. replace a
     * showing warning and its finish button), so cooldown re-shows are throttled against this.
     */
    private val lastBreakScreenMs = HashMap<String, Long>()

    /**
     * Feeds every scroll event into both doom-scrolling triggers (see the class docs): the
     * app-level intensity session and the per-scroll-view endless-screen heuristic. Whichever
     * finds sustained doom scrolling first (≥ [timeUntilWarning]) opens the break screen.
     * Before detection runs, the event is checked against a running finish-grace and against
     * active cooldowns.
     */
    override fun onScrollEvent(
        context: Context,
        scrollViewId: Int,
        scrollViewSize: Int,
        accessibilityEvent: AccessibilityEvent
    ) {
        val pkg = accessibilityEvent.packageName.toString()
        val surfaceId = accessibilityEvent.source?.viewIdResourceName

        if (finishGrace.isInGrace(pkg)) {
            // the user is finishing their last item after a warning; guide them out once they
            // start scrolling on again or the grace times out
            if (finishGrace.onScrollEvent(pkg)) {
                guideOut(context, pkg)
            }
            return
        }

        cooldowns.activeCooldownFor(pkg, surfaceId)?.let { cooldown ->
            showCooldownScreen(context, pkg, cooldown)
            return
        }

        if (!appliesTo(pkg)) return

        // trigger 1: scroll intensity — sustained, fast, mostly-downward scrolling in this app
        val isDownScroll = AccessibilityEventUtil.isDownScrollEvent(accessibilityEvent)
        val deltaMagnitude = abs(AccessibilityEventUtil.getScrollDeltaY(accessibilityEvent))
        val intensity = scrollSessionMonitor.onScrollEvent(
            pkg, scrollDistanceEstimator.distancePxFor(pkg, deltaMagnitude), isDownScroll
        )
        if (detectionSensitivity.isDoomScrolling(intensity, timeUntilWarning)) {
            Timber.i(
                "Intensity break triggered for pkg=%s (%.1f screens/min, downRatio=%.2f, %d ms)",
                pkg,
                intensity.screenHeightsPerMinute,
                intensity.downScrollRatio,
                intensity.sessionDurationMs
            )
            warn(
                context, pkg, surfaceId, contextText = context.getString(
                    R.string.infiniteScroll_warning_context_intensity,
                    intensity.sessionScreenHeights.roundToInt(),
                    TimeUnit.MILLISECONDS.toMinutes(intensity.sessionDurationMs).coerceAtLeast(1)
                )
            )
            return
        }

        // trigger 2: endless screen — a scroll view that keeps growing in size
        if (!isDownScroll) return
        val scrollViewInfo = activeScrollViews[scrollViewId]

        if (scrollViewInfo == null) {
            activeScrollViews[scrollViewId] = ScrollViewInfo(scrollViewSize)
            Timber.d(
                "New scroll view registered id=%d size=%d pkg=%s", scrollViewId, scrollViewSize, pkg
            )
        } else {
            val growthSize = scrollViewSize - scrollViewInfo.sizeY
            scrollViewInfo.sizeY = scrollViewSize

            if (growthSize > 1) {
                // if the scroll view has grown by only one item, it is probably a "normal" scroll view,
                // because in infinite scroll views, the scroll view usually grows by a specific amount
                // (e.g. 10 items)
                scrollViewInfo.timesGrown++
            } else if (growthSize < 0) {
                // shrinking usually means the user scrolled back up a bit; instead of discarding
                // all evidence at once (which punished natural back-scrolling), step the counter
                // down and only re-arm the warning once the evidence is fully gone
                scrollViewInfo.timesGrown = (scrollViewInfo.timesGrown - 1).coerceAtLeast(0)
                if (scrollViewInfo.timesGrown == 0) scrollViewInfo.warned = false
            }

            if (scrollViewInfo.timesGrown >= 3 && !scrollViewInfo.warned) {
                // assume that a scroll view is infinite, if its size has grown three times
                val scrollingTime = System.currentTimeMillis() - scrollViewInfo.added
                if (scrollingTime >= timeUntilWarning) {
                    // if the scroll view is seemingly infinite and the user has been too long on it,
                    // we assume that they are doomscrolling and open the break screen
                    Timber.i("Break screen triggered after %d ms for pkg=%s", scrollingTime, pkg)
                    scrollViewInfo.warned = true
                    warn(
                        context, pkg, surfaceId, contextText = context.getString(
                            R.string.infiniteScroll_warning_context_endless,
                            TimeUnit.MILLISECONDS.toMinutes(scrollingTime).coerceAtLeast(1)
                        )
                    )
                }
            }
        }
    }

    /**
     * Ends any finish-grace of apps the user just switched away from — leaving during the grace
     * honors the deal, so the cooldown starts silently — and blocks re-entry into apps that are
     * under a whole-app cooldown.
     */
    override fun onAppOpened(
        context: Context,
        packageName: String,
        accessibilityEvent: AccessibilityEvent
    ) {
        finishGrace.activePackages().filter { it != packageName }.forEach { endGraceSilently(it) }

        if (!appliesTo(packageName)) return
        cooldowns.appLockEndMs(packageName)?.let { endsAtMs ->
            showCooldownScreen(
                context, packageName, CooldownRegistry.ActiveCooldown(endsAtMs, wholeApp = true)
            )
        }
    }

    /** Turning the screen off during a finish-grace also counts as leaving. */
    override fun onScreenTurnedOff(context: Context?) {
        finishGrace.activePackages().forEach { endGraceSilently(it) }
    }

    /**
     * A doom-scrolling trigger fired: count the break, lock the offending app/surface (choosing
     * "I'll finish this one" lifts the lock again) and show the warning screen.
     */
    private fun warn(
        context: Context,
        packageName: String,
        surfaceId: String?,
        contextText: String
    ) {
        breakCounter.increment(packageName)
        scrollSessionMonitor.reset(packageName)
        lastTriggerSurfaceIds[packageName] = surfaceId
        cooldowns.start(packageName, surfaceId, cooldownTime)
        openBreakScreen(context, packageName, BreakScreenMode.WARNING, contextText)
    }

    /**
     * Starts the "finish what you're watching" grace after the user tapped the finish button on
     * the warning screen: the cooldown is lifted, and the user is guided out automatically once
     * they scroll on a few more times or the grace times out — whichever comes first.
     */
    fun startFinishGrace(context: Context, packageName: String) {
        val appContext = context.applicationContext
        cooldowns.clear(packageName)
        activeScrollViews.clear()
        finishGrace.start(packageName, lastTriggerSurfaceIds[packageName])
        cancelGraceTimeout(packageName)
        val timeout = Runnable { guideOut(appContext, packageName) }
        graceTimeouts[packageName] = timeout
        graceHandler.postDelayed(timeout, FinishGraceTracker.DEFAULT_TIMEOUT_MS)
    }

    /**
     * The finish-grace is over while the user is still in the app: start the cooldown and show
     * the guide-out screen, which takes them to the home screen automatically.
     */
    private fun guideOut(context: Context, packageName: String) {
        cancelGraceTimeout(packageName)
        val grace = finishGrace.end(packageName) ?: return
        if (!isActivated || PauseButtonFeature.isFeaturePaused(this)) return
        cooldowns.start(packageName, grace.surfaceId, cooldownTime)
        scrollSessionMonitor.reset(packageName)
        openBreakScreen(context, packageName, BreakScreenMode.GUIDE_OUT)
    }

    /** Ends a grace without showing anything (the user already left); the cooldown still starts. */
    private fun endGraceSilently(packageName: String) {
        cancelGraceTimeout(packageName)
        finishGrace.end(packageName)?.let {
            cooldowns.start(packageName, it.surfaceId, cooldownTime)
        }
    }

    private fun cancelGraceTimeout(packageName: String) {
        graceTimeouts.remove(packageName)?.let { graceHandler.removeCallbacks(it) }
    }

    /**
     * (Re-)shows the break screen in cooldown mode, telling the user how long the app or surface
     * stays locked. Throttled per app, because a single fling emits many scroll events.
     */
    private fun showCooldownScreen(
        context: Context,
        packageName: String,
        cooldown: CooldownRegistry.ActiveCooldown
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastBreakScreenMs[packageName] ?: 0L) < COOLDOWN_SCREEN_THROTTLE_MS) return

        val remainingMinutes =
            ((cooldown.endsAtMs - System.currentTimeMillis() + 59_999) / 60_000).coerceAtLeast(1)
        val text = context.getString(
            if (cooldown.wholeApp) {
                R.string.infiniteScroll_cooldown_context_app
            } else {
                R.string.infiniteScroll_cooldown_context_surface
            },
            appLabel(context, packageName),
            remainingMinutes
        )
        openBreakScreen(context, packageName, BreakScreenMode.COOLDOWN, text)
    }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault(packageName)

    private fun openBreakScreen(
        context: Context,
        packageName: String,
        mode: BreakScreenMode,
        contextText: String? = null
    ) {
        lastBreakScreenMs[packageName] = SystemClock.elapsedRealtime()
        context.startService(
            Intent(
                context, BreakDoomScrollingOverlayService::class.java
            ).apply {
                putExtra(OverlayService.EXTRA_RUNNING_APP_PACKAGE_NAME, packageName)
                putExtra(BreakDoomScrollingOverlayService.EXTRA_MODE, mode.name)
                putExtra(BreakDoomScrollingOverlayService.EXTRA_CONTEXT_TEXT, contextText)
            })
    }

    /** How often the cooldown screen may re-open per app. */
    private const val COOLDOWN_SCREEN_THROTTLE_MS = 3_000L

    /**
     * Contains information about a scroll view, i.e. when it was added, how often it has grown
     * and how big it is.
     */
    data class ScrollViewInfo(var sizeY: Int) {
        val added: Long = System.currentTimeMillis()
        var timesGrown = 0
        var warned = false

        override fun toString(): String {
            return "ScrollViewInfo(sizeY=$sizeY, added=$added, timesGrown=$timesGrown, warned=$warned)"
        }
    }
}

