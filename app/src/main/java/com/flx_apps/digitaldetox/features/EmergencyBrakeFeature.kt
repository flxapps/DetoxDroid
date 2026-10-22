package com.flx_apps.digitaldetox.features

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.BrakeAwareFeature
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.EmergencyBrakeTileService
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.ui.screens.feature.emergency_brake.EmergencyBrakeFeatureSettingsSection
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import com.flx_apps.digitaldetox.util.RecentEvents
import com.flx_apps.digitaldetox.util.TrustedClock
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

val EmergencyBrakeFeatureId = Feature.createId(EmergencyBrakeFeature::class.java)

/**
 * The reverse of the [PauseButtonFeature]: for a chosen time, every feature that is switched on
 * runs as if its schedule were open and its budget spent, and the ways out are closed.
 *
 * It adds no rules of its own. Features that are off stay off, and what a feature does under the
 * brake is what it already does at its strictest. The one thing that widens is grayscale, which
 * takes the whole screen: gray breaks nothing, while blocking every app would take the phone app
 * with it, so blocking stays with the user's list.
 *
 * The brake can be ended early, but only by asking and then waiting [EndWaitMs]. A brake that could
 * not be ended at all would promise more than this app can keep, as the accessibility service can
 * always be switched off in the system settings.
 *
 * Its state is persisted and measured on [TrustedClock], so neither the process dying nor the date
 * being set forward ends it.
 */
object EmergencyBrakeFeature : Feature(), LockableFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_brake,
        subtitle = R.string.feature_brake_subtitle,
        description = R.string.feature_brake_description,
    )
    override val iconRes: Int = R.drawable.ic_brake
    override val settingsContent: @Composable () -> Unit = {
        EmergencyBrakeFeatureSettingsSection()
    }

    /** How long a request to end the brake early has to wait. */
    val EndWaitMs: Long = TimeUnit.MINUTES.toMillis(10)

    /** How long a brake that was pulled by hand can be taken back, for the tile tapped by accident. */
    val UndoWindowMs: Long = TimeUnit.SECONDS.toMillis(30)

    /** The durations on offer as chips, in minutes. Any other is picked by hand. */
    val DurationsInMinutes = listOf(30, 60, 120, 240, 480)

    /**
     * How long the last brake pulled by hand was set to run. The quick settings tile pulls this
     * one, as it has no room to ask.
     */
    var lastDuration: Long by DataStoreProperty(
        longPreferencesKey("${id}_lastDuration"), TimeUnit.MINUTES.toMillis(30)
    )

    /**
     * Whether the brake pulls itself after [autoPullBreaks] doom scrolling breaks within
     * [autoPullWindow]. The breaks are counted across all apps: one app is already escalated by
     * its own cooldown, moving on to the next app is what nothing else notices.
     */
    var autoPull: Boolean by DataStoreProperty(booleanPreferencesKey("${id}_autoPull"), false)

    var autoPullBreaks: Int by DataStoreProperty(intPreferencesKey("${id}_autoPullBreaks"), 3)

    var autoPullWindow: Long by DataStoreProperty(
        longPreferencesKey("${id}_autoPullWindow"), TimeUnit.MINUTES.toMillis(30)
    )

    var autoPullDuration: Long by DataStoreProperty(
        longPreferencesKey("${id}_autoPullDuration"), TimeUnit.MINUTES.toMillis(30)
    )

    // The running brake, all in TrustedClock time. 0 means there is none, which is also what every
    // check on the way of a scroll event looks at first.
    private var brakeUntil: Long by DataStoreProperty(longPreferencesKey("${id}_until"), 0L)
    private var pulledAt: Long by DataStoreProperty(longPreferencesKey("${id}_pulledAt"), 0L)
    private var endRequestedAt: Long by DataStoreProperty(
        longPreferencesKey("${id}_endRequestedAt"), 0L
    )
    private var pulledAutomatically: Boolean by DataStoreProperty(
        booleanPreferencesKey("${id}_pulledAutomatically"), false
    )

    /** The preference keys that hold a running brake. A backup carries none of them. */
    val runningStateKeys = setOf(
        "${id}_until", "${id}_pulledAt", "${id}_endRequestedAt", "${id}_pulledAutomatically"
    )

    private val _stateToken = MutableStateFlow(0L)

    /** Changes whenever the brake does, for the screens that show it. */
    val stateToken: StateFlow<Long> = _stateToken.asStateFlow()

    private val recentBreaks = RecentEvents()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val settleRunnable = Runnable {
        kotlin.runCatching { settle(DetoxDroidApplication.appContext) }
            .onFailure { Timber.w(it, "Could not release the brake") }
    }

    /** Takes the undo action off the notification once it has nothing left to undo. */
    private val undoExpiredRunnable = Runnable {
        _stateToken.update { it + 1 }
        DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
    }

    /**
     * When the running brake ends, in [TrustedClock] time, or 0 without one. A request to end it
     * early moves this forward.
     */
    private fun endsAt(): Long = when {
        brakeUntil == 0L -> 0L
        endRequestedAt > 0L -> minOf(brakeUntil, endRequestedAt + EndWaitMs)
        else -> brakeUntil
    }

    fun isBraking(): Boolean = brakeUntil != 0L && TrustedClock.now() < endsAt()

    /**
     * Whether a running brake tightens [feature]. There is no picking: every feature that can be
     * paused is covered, and one that is switched off stays off.
     */
    fun covers(feature: Feature): Boolean = feature is PausableFeature && isBraking()

    /**
     * Whether a running brake locks the settings of the feature or the groups with [featureId]
     * (see [RuleGroups.LOCK_ID]). The commitment password keeps its own lock.
     */
    fun locksSettings(featureId: FeatureId): Boolean {
        if (!isBraking() || featureId == CommitmentPasswordFeatureId) return false
        return featureId == RuleGroups.LOCK_ID || RuleGroups.isGroupId(featureId) ||
                FeaturesProvider.getFeatureById(featureId) is LockableFeature
    }

    /** Whether the end of the running brake has been asked for and is being waited out. */
    fun isEndRequested(): Boolean = isBraking() && endRequestedAt > 0L

    /** Whether the running brake can still be taken back. One that pulled itself cannot. */
    fun canUndo(): Boolean =
        isBraking() && !pulledAutomatically && TrustedClock.now() - pulledAt < UndoWindowMs

    /** How long the running brake still lasts, 0 without one. */
    fun remainingMs(): Long =
        if (isBraking()) (endsAt() - TrustedClock.now()).coerceAtLeast(0L) else 0L

    /** When the running brake ends on the clock the user reads, 0 without one. */
    fun endsAtWallClock(): Long =
        if (isBraking()) System.currentTimeMillis() + remainingMs() else 0L

    /** The end of the running brake as a time of day, e.g. "21:30". */
    fun endsAtText(): String =
        Instant.ofEpochMilli(endsAtWallClock()).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    /**
     * Pulls the brake for [durationMs], or extends a running one to last at least that long from
     * now. Extending also withdraws a request to end it.
     * @param automatic Whether the brake pulled itself, see [autoPull]. Such a brake never extends
     * a running one, so catches during a brake cannot stack up.
     * @return whether anything changed
     */
    fun pull(
        context: Context, durationMs: Long = lastDuration, automatic: Boolean = false
    ): Boolean {
        if (!isActivated || durationMs <= 0L) return false
        val now = TrustedClock.now()
        val until = now + durationMs
        if (isBraking()) {
            if (automatic || (until <= brakeUntil && endRequestedAt == 0L)) return false
            brakeUntil = maxOf(brakeUntil, until)
            endRequestedAt = 0L
            lastDuration = durationMs
            refresh(context)
            return true
        }
        val activeBefore = FeaturesProvider.activeFeatures
        brakeUntil = until
        pulledAt = now
        endRequestedAt = 0L
        pulledAutomatically = automatic
        if (!automatic) lastDuration = durationMs
        Timber.i("Brake pulled for ${durationMs / 1000}s (automatic=$automatic)")
        apply(context, activeBefore)
        return true
    }

    /**
     * Makes the running brake last [byMs] longer than it would have, counted from the end the
     * user was shown, so "until 21:30" plus half an hour reads 22:00. A request to end it early is
     * taken back by this: asking for more is the opposite of asking out.
     * @return whether anything changed
     */
    fun extend(context: Context, byMs: Long): Boolean {
        if (!isBraking() || byMs <= 0L) return false
        brakeUntil = endsAt() + byMs
        endRequestedAt = 0L
        Timber.i("Brake extended by ${byMs / 1000}s")
        refresh(context)
        return true
    }

    /** Takes a brake back right after it was pulled. */
    fun undo(context: Context): Boolean {
        if (!canUndo()) return false
        release(context)
        return true
    }

    /** Asks for the running brake to end. It does, [EndWaitMs] later. */
    fun requestEnd(context: Context) {
        if (!isBraking() || endRequestedAt > 0L) return
        endRequestedAt = TrustedClock.now()
        refresh(context)
    }

    /** Takes a request to end the brake back, which then runs its full time. */
    fun withdrawEndRequest(context: Context) {
        if (!isEndRequested()) return
        endRequestedAt = 0L
        refresh(context)
    }

    /**
     * Notes a doom scrolling break and pulls the brake if that was one too many.
     * @return whether the brake was pulled
     */
    fun onDoomScrollingBreak(context: Context): Boolean {
        if (!isActivated || !autoPull || isBraking()) return false
        val breaks = recentBreaks.record(SystemClock.elapsedRealtime(), autoPullWindow)
        if (breaks < autoPullBreaks) return false
        return pull(context, autoPullDuration, automatic = true)
    }

    /**
     * Picks a brake up again when the service starts. One that ran out while nothing was running
     * is dropped without a word: the features are about to start from their own settings anyway.
     */
    fun restore(context: Context) {
        if (brakeUntil == 0L) return
        if (isBraking()) scheduleEnd(context) else clearRunningState()
    }

    /**
     * Releases a brake that has run out. Cheap enough to be called on every app switch, which is
     * what catches the end after the phone slept through the timer. Synchronized, because the
     * timer, the scheduler and an app switch can all arrive at the end within a moment of each
     * other, and the features must hear of it once.
     * @return whether a brake was released
     */
    @Synchronized
    fun settle(context: Context): Boolean {
        if (brakeUntil == 0L || isBraking()) return false
        release(context)
        return true
    }

    /**
     * Takes hold again after the service has started under a brake that outlived the process.
     * The features start from their own settings; the ones with something to take hold of (apps
     * to suspend or hide) are told that a brake is on.
     */
    fun reapply(context: Context) {
        if (!isBraking()) return
        val service = DetoxDroidAccessibilityService.instance ?: return
        carryToFeatures(context, service)
    }

    private fun release(context: Context) {
        Timber.i("Brake released")
        val activeBefore = FeaturesProvider.activeFeatures
        clearRunningState()
        apply(context, activeBefore)
    }

    private fun clearRunningState() {
        brakeUntil = 0L
        pulledAt = 0L
        endRequestedAt = 0L
        pulledAutomatically = false
        // what led up to a brake is settled by it, so the next break starts a new count
        recentBreaks.clear()
    }

    /**
     * Carries a brake that started or ended to the features. The ones it opens the schedule of
     * start or stop, and then every running one looks at the app in front again: no window event
     * is on its way while the user stays where they are.
     * @param activeBefore the features that were active before the brake changed
     */
    private fun apply(context: Context, activeBefore: Set<Feature>) {
        refresh(context)
        val service = DetoxDroidAccessibilityService.instance ?: return
        if (PauseButtonFeature.isPausing()) {
            // a brake ends a pause; resuming starts every feature the pause had suspended, which
            // includes the ones the brake has just opened the schedule of
            FeaturesProvider.reloadActiveFeatures()
            PauseButtonFeature.resume()
            // a feature exempt from pauses was never suspended, so resuming leaves it alone, and
            // one the brake has just made active has not started yet
            FeaturesProvider.activeFeatures
                .filter { it !in activeBefore && it.id in PauseButtonFeature.pauseExemptFeatureIds }
                .forEach { it.onStart(context) }
        } else {
            FeaturesProvider.applyScheduleTransitions()
        }
        carryToFeatures(context, service)
    }

    private fun carryToFeatures(context: Context, service: DetoxDroidAccessibilityService) {
        if (DetoxDroidAccessibilityService.updateState() != DetoxDroidState.Active) return
        FeaturesProvider.activeFeatures.filter { it is PausableFeature }.forEach {
            kotlin.runCatching {
                (it as? BrakeAwareFeature)?.onBrakeChanged(context)
                // in a browser, each feature gets the website it lists, as on an app switch
                val foreground = service.foregroundFor(it)
                if (foreground.isNotEmpty() && it is OnAppOpenedSubscriptionFeature) {
                    it.onAppOpened(context, foreground, AccessibilityEventUtil.createEvent())
                }
            }.onFailure { e -> Timber.w(e, "Could not carry the brake to ${it.id}") }
        }
    }

    /** Brings everything that shows the brake up to date, and its timers. */
    private fun refresh(context: Context) {
        _stateToken.update { it + 1 }
        scheduleEnd(context)
        DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
        // the launcher widget dims the apps a brake blocks
        ScreenTimeSurfaces.invalidate(context)
        // a tile that is on screen right now is only redrawn when asked
        kotlin.runCatching {
            TileService.requestListeningState(
                context, ComponentName(context, EmergencyBrakeTileService::class.java)
            )
        }
    }

    /**
     * Arms the end of the brake twice: in this process, and through the scheduler for the case
     * that the process is gone by then.
     */
    private fun scheduleEnd(context: Context) {
        mainHandler.removeCallbacks(settleRunnable)
        mainHandler.removeCallbacks(undoExpiredRunnable)
        if (isBraking()) {
            mainHandler.postDelayed(settleRunnable, remainingMs() + 250)
            if (canUndo()) {
                val undoLeftMs = UndoWindowMs - (TrustedClock.now() - pulledAt)
                mainHandler.postDelayed(undoExpiredRunnable, undoLeftMs + 250)
            }
        }
        kotlin.runCatching { ServiceReliabilityScheduler.scheduleNextScheduleBoundary(context) }
            .onFailure { Timber.w(it, "Could not schedule the end of the brake") }
    }
}
