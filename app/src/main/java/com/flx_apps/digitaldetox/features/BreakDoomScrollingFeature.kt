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
import com.flx_apps.digitaldetox.feature_types.BrakeAwareFeature
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.PausableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsDrawOverlayPermissionFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.feature_types.OnAppOpenedSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScreenTurnedOffSubscriptionFeature
import com.flx_apps.digitaldetox.feature_types.OnScrollEventSubscriptionFeature
import com.flx_apps.digitaldetox.rules.Rules
import com.flx_apps.digitaldetox.system_integration.OverlayService
import com.flx_apps.digitaldetox.system_integration.ScrollSurfaces
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
import com.flx_apps.digitaldetox.util.ShortVideoRuns
import com.flx_apps.digitaldetox.util.appLabel
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

val BreakDoomScrollingFeatureId = Feature.createId(BreakDoomScrollingFeature::class.java)

/**
 * How easily the scroll-intensity trigger of [BreakDoomScrollingFeature] fires. Thresholds apply
 * to the live [ScrollSessionSnapshot] of the current scroll session: STRICT intervenes at slower
 * scrolling, RELAXED only at frantic feed-flicking. On a pager of short videos the pace is counted
 * in videos instead, see [isDoomSwiping]. The endless-feed (growth) trigger is not affected by the
 * sensitivity.
 * @param minShortVideosPerMinute the pace of swiping through short videos that counts, averaged
 * over the run: a video every 15 s for RELAXED, every 30 s for STRICT
 */
enum class DoomScrollingSensitivity(
    val minScreenHeightsPerMinute: Float,
    val minDownScrollRatio: Float,
    val minShortVideosPerMinute: Float,
) {
    RELAXED(14f, 0.85f, 4f),
    BALANCED(9f, 0.8f, 3f),
    STRICT(6f, 0.75f, 2f);

    /**
     * Returns true if [snapshot] shows sustained (≥ [minSessionDurationMs]), fast, mostly-downward
     * scrolling — the intensity signature of doom scrolling.
     */
    fun isDoomScrolling(snapshot: ScrollSessionSnapshot, minSessionDurationMs: Long): Boolean =
        snapshot.sessionDurationMs >= minSessionDurationMs &&
                snapshot.screenHeightsPerMinute >= minScreenHeightsPerMinute &&
                snapshot.downScrollRatio >= minDownScrollRatio

    /**
     * Returns true if [run] shows sustained (≥ [minSessionDurationMs]) swiping through short
     * videos at this sensitivity's pace: at least [MIN_SHORT_VIDEOS] of them, so one long video
     * and its comments never count, and on average [minShortVideosPerMinute] of them a minute.
     */
    fun isDoomSwiping(run: ShortVideoRuns.Snapshot, minSessionDurationMs: Long): Boolean =
        run.durationMs >= minSessionDurationMs &&
                run.videos >= MIN_SHORT_VIDEOS &&
                run.videos * 60_000f / run.durationMs >= minShortVideosPerMinute

    companion object {
        /** Fewer short videos than this are never a break, however long the run. */
        const val MIN_SHORT_VIDEOS = 5
    }

    /** The next-stricter level; applied while an app is on probation after an incident. */
    fun stricter(): DoomScrollingSensitivity = when (this) {
        RELAXED -> BALANCED
        else -> STRICT
    }
}

/**
 * Runs the scroll break step of the groups (see [com.flx_apps.digitaldetox.rules.Step]): detects
 * doom scrolling in the apps a group covers and opens a break screen when it has been going on for
 * the group's time ([Rules.scrollBreak]). Three independent triggers feed the same break screen:
 * - **Endless screen**: a scroll view whose reported size keeps growing (classic infinite feed).
 * - **Scroll intensity**: sustained, fast, mostly-downward scrolling in an app, measured by
 *   [ScrollSessionMonitor] — catches virtualized feeds that never report growth. Its thresholds
 *   are user-tunable via [detectionSensitivity].
 * - **Short videos**: on the pagers of Reels, Shorts and their kind in the apps
 *   [ScrollSurfaces] knows, where one swipe is one video and even steady swiping never looks fast,
 *   the videos swiped past are counted ([ShortVideoRuns]) and time and pace decide. The table
 *   only adds to the two triggers above: an app or a pager it does not know is judged by its
 *   physics like any other.
 *
 * The break screen offers two ways out:
 * - **"Get me out of here"**: leave immediately (home screen, best-effort force-stop).
 * - **"I'll finish this one, then leave"**: a bounded grace ([FinishGraceTracker]) to finish the
 *   item currently on screen — including reading its comments/thread, which extends the grace a
 *   bit; once the user scrolls on to new content or the grace times out, they are guided out
 *   automatically.
 *
 * Either way the app enters a *cooldown* ([cooldownTime], [CooldownRegistry]) during which
 * re-entry is blocked. When the triggering scroll view had a view-id resource name, the cooldown
 * is scoped to that surface — e.g. Instagram's reels stay locked while the DM list remains
 * usable; otherwise the whole app is locked, including on app open.
 *
 * While any cooldown of an app is running, the app is on *probation*: its other surfaces stay
 * usable, but detection there runs with stricter thresholds ([DoomScrollingSensitivity.stricter])
 * and a much shorter time gate ([PROBATION_SESSION_GATE_MS]). A trigger firing during probation
 * proves that surface scoping was too permissive for this incident — the user just moved their
 * scrolling elsewhere (e.g. from the feed to a reel opened from a DM) — so the new cooldown
 * escalates to the whole app.
 */
object BreakDoomScrollingFeature : Feature(), OnScrollEventSubscriptionFeature,
    OnAppOpenedSubscriptionFeature, OnScreenTurnedOffSubscriptionFeature,
    NeedsPermissionsFeature by NeedsDrawOverlayPermissionFeature(), PausableFeature,
    BrakeAwareFeature {
    override val texts: FeatureTexts = FeatureTexts(
        R.string.feature_doomScrolling,
        R.string.feature_doomScrolling_subtitle,
        R.string.feature_doomScrolling_description,
    )
    override val iconRes = R.drawable.ic_scroll
    override val settingsContent: @Composable () -> Unit =
        { BreakDoomScrollingFeatureSettingsSection() }

    /** On while any switched-on group breaks up doomscrolling; the groups are the switch. */
    override var isActivated: Boolean
        get() = Rules.usesFeature(this)
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /** The time a new group's scroll break starts out with. */
    val DefaultTimeUntilWarningMs: Long = TimeUnit.MINUTES.toMillis(3)

    /**
     * How long the scrolling in [packageName] may run before the break screen shows: the shortest
     * time of the groups that cover it with the step, or null when none does.
     */
    private fun timeUntilWarningFor(packageName: String): Long? =
        Rules.scrollBreak(packageName)?.afterMs

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

    /** Counts the videos swiped past on a short-video pager, for the short-videos trigger. */
    private val shortVideoRuns = ShortVideoRuns()

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

    /**
     * The cooldown scope each app's last warning decided on: the triggering surface's view-id
     * resource name, or null for the whole app (no id, or escalated on probation). A subsequent
     * finish-grace inherits this scope.
     */
    private val lastCooldownScopes = HashMap<String, String?>()

    /**
     * Last time any break screen was shown per app. Scroll-momentum events arriving right after
     * an overlay opened must not re-deliver it in cooldown mode (which would e.g. replace a
     * showing warning and its finish button), so cooldown re-shows are throttled against this.
     */
    private val lastBreakScreenMs = HashMap<String, Long>()

    /**
     * Feeds every scroll event into both doom-scrolling triggers (see the class docs): the
     * app-level intensity session and the per-scroll-view endless-screen heuristic. Whichever
     * finds sustained doom scrolling first (≥ [timeUntilWarning], or the much shorter probation
     * gate while a cooldown of the app is still running) opens the break screen. Before detection
     * runs, the event is checked against a running finish-grace and against active cooldowns.
     */
    override fun onScrollEvent(
        context: Context,
        packageName: String,
        scrollViewId: Int,
        scrollViewSize: Int,
        viewIdResourceName: String?,
        accessibilityEvent: AccessibilityEvent
    ) {
        val pkg = packageName
        val surfaceId = viewIdResourceName

        if (finishGrace.isInGrace(pkg)) {
            onGraceScrollEvent(context, pkg, surfaceId, accessibilityEvent)
            return
        }

        cooldowns.activeCooldownFor(pkg, surfaceId)?.let { cooldown ->
            showCooldownScreen(context, pkg, cooldown)
            return
        }

        val timeUntilWarning = timeUntilWarningFor(pkg) ?: return

        // under a brake every app is on probation from the start, and
        // while another surface of this app is still cooling down, the app is on probation:
        // detection fires sooner and at stricter thresholds, so moving the scrolling to a
        // different surface right after a break is caught quickly (see the class docs)
        val onProbation = cooldowns.hasAnyCooldown(pkg) || EmergencyBrakeFeature.covers(this)
        val sensitivity = if (onProbation) detectionSensitivity.stricter() else detectionSensitivity
        val minSessionMs =
            if (onProbation) probationSessionGateMs(timeUntilWarning) else timeUntilWarning

        val isDownScroll = AccessibilityEventUtil.isDownScrollEvent(accessibilityEvent)

        // trigger 1: short videos — on a known pager, where a swipe is a video, time and the
        // pace of swiping decide, because the speed below never adds up on one
        val app = accessibilityEvent.packageName?.toString()
        if (surfaceId != null && app != null && ScrollSurfaces.isShortVideoPager(context, app, surfaceId)) {
            val run = shortVideoRuns.onScrollEvent(
                pkg, surfaceId, accessibilityEvent.fromIndex, isDownScroll
            )
            if (run.fresh) Timber.d("Short videos: %s swipes through %s", pkg, surfaceId)
            if (sensitivity.isDoomSwiping(run, minSessionMs)) {
                Timber.i(
                    "Short-video break triggered for pkg=%s (%d videos in %d ms)",
                    pkg, run.videos, run.durationMs
                )
                warn(
                    context, pkg, surfaceId, contextText = context.getString(
                        R.string.feature_doomScrolling_warning_context_shortVideos,
                        run.videos,
                        TimeUnit.MILLISECONDS.toMinutes(run.durationMs).coerceAtLeast(1)
                    )
                )
                return
            }
        }

        // trigger 2: scroll intensity — sustained, fast, mostly-downward scrolling in this app
        val deltaMagnitude = abs(AccessibilityEventUtil.getScrollDeltaY(accessibilityEvent))
        val intensity = scrollSessionMonitor.onScrollEvent(
            pkg, scrollDistanceEstimator.distancePxFor(pkg, deltaMagnitude), isDownScroll
        )
        if (sensitivity.isDoomScrolling(intensity, minSessionMs)) {
            Timber.i(
                "Intensity break triggered for pkg=%s (%.1f screens/min, downRatio=%.2f, %d ms)",
                pkg,
                intensity.screenHeightsPerMinute,
                intensity.downScrollRatio,
                intensity.sessionDurationMs
            )
            warn(
                context, pkg, surfaceId, contextText = context.getString(
                    R.string.feature_doomScrolling_warning_context_intensity,
                    intensity.sessionScreenHeights.roundToInt(),
                    TimeUnit.MILLISECONDS.toMinutes(intensity.sessionDurationMs).coerceAtLeast(1)
                )
            )
            return
        }

        // trigger 3: endless screen — a scroll view that keeps growing in size
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
                if (scrollingTime >= minSessionMs) {
                    // if the scroll view is seemingly infinite and the user has been too long on it,
                    // we assume that they are doomscrolling and open the break screen
                    Timber.i("Break screen triggered after %d ms for pkg=%s", scrollingTime, pkg)
                    scrollViewInfo.warned = true
                    warn(
                        context, pkg, surfaceId, contextText = context.getString(
                            R.string.feature_doomScrolling_warning_context_endless,
                            TimeUnit.MILLISECONDS.toMinutes(scrollingTime).coerceAtLeast(1)
                        )
                    )
                }
            }
        }
    }

    /**
     * Handles a scroll event while [packageName] is in a finish-grace. The user is finishing
     * their last item after a warning: reading a different surface (e.g. the item's comments) is
     * fine and keeps the grace alive, but scrolling on within the triggering surface — or
     * resuming outright doom scrolling anywhere in the app — uses the grace up and guides them
     * out (see [FinishGraceTracker]).
     */
    private fun onGraceScrollEvent(
        context: Context,
        packageName: String,
        surfaceId: String?,
        accessibilityEvent: AccessibilityEvent
    ) {
        if (finishGrace.onScrollEvent(packageName, surfaceId)) {
            guideOut(context, packageName)
            return
        }
        // off-surface scrolling is still measured: if it adds up to doom scrolling again (under
        // the stricter probation thresholds), the "one last item" deal is off — and since the
        // user moved their scrolling elsewhere, the whole app takes the cooldown
        val isDownScroll = AccessibilityEventUtil.isDownScrollEvent(accessibilityEvent)
        val deltaMagnitude = abs(AccessibilityEventUtil.getScrollDeltaY(accessibilityEvent))
        val intensity = scrollSessionMonitor.onScrollEvent(
            packageName,
            scrollDistanceEstimator.distancePxFor(packageName, deltaMagnitude),
            isDownScroll
        )
        val gateMs = probationSessionGateMs(timeUntilWarningFor(packageName) ?: DefaultTimeUntilWarningMs)
        if (detectionSensitivity.stricter().isDoomScrolling(intensity, gateMs)) {
            Timber.i("Doom scrolling resumed during finish-grace of %s", packageName)
            guideOut(context, packageName, escalateToWholeApp = true)
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

        if (timeUntilWarningFor(packageName) == null) return
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
     * "I'll finish this one" lifts the lock again) and show the warning screen. Firing again
     * while a cooldown of this app was still running means the user moved their scrolling to
     * another surface — surface scoping was too permissive for this incident, so the new
     * cooldown covers the whole app.
     */
    private fun warn(
        context: Context,
        packageName: String,
        surfaceId: String?,
        contextText: String
    ) {
        val onProbation = cooldowns.hasAnyCooldown(packageName)
        // the break that is one too many pulls the brake, and is then the first one under it
        val pulledBrake = EmergencyBrakeFeature.onDoomScrollingBreak(context)
        val braking = EmergencyBrakeFeature.covers(this)
        val escalateToWholeApp = onProbation || braking
        val cooldownScope = if (escalateToWholeApp) null else surfaceId
        breakCounter.increment(packageName)
        scrollSessionMonitor.reset(packageName)
        shortVideoRuns.reset(packageName)
        lastCooldownScopes[packageName] = cooldownScope
        // Under a brake the app stays away for the rest of it. Keeping messages reachable is what
        // scoping a cooldown to the feed is for, and what the brake was pulled to go without.
        val cooldownMs =
            if (braking) maxOf(cooldownTime, EmergencyBrakeFeature.remainingMs()) else cooldownTime
        cooldowns.start(packageName, cooldownScope, cooldownMs)
        val consequence = when {
            pulledBrake -> context.getString(
                R.string.feature_doomScrolling_warning_context_brakePulled,
                EmergencyBrakeFeature.autoPullBreaks,
                EmergencyBrakeFeature.endsAtText()
            )

            braking -> context.getString(
                R.string.feature_doomScrolling_warning_context_braking,
                EmergencyBrakeFeature.endsAtText()
            )

            onProbation -> context.getString(
                R.string.feature_doomScrolling_warning_context_probation
            )

            else -> null
        }
        val text = if (consequence != null) contextText + "\n" + consequence else contextText
        openBreakScreen(context, packageName, BreakScreenMode.WARNING, text)
    }

    /**
     * A cooldown that was started under a brake runs to the end of it. When the brake ends before
     * that, the cooldown comes down to what it would have been without one.
     */
    override fun onBrakeChanged(context: Context) {
        if (EmergencyBrakeFeature.covers(this)) return
        cooldowns.endNoLaterThan(System.currentTimeMillis() + cooldownTime)
    }

    /**
     * Pulls the brake from the break screen of [packageName]. The app the user was caught in
     * stays away for the rest of the brake, the way it would had the brake been on when they
     * were caught.
     */
    fun pullBrake(context: Context, packageName: String) {
        if (!EmergencyBrakeFeature.pull(context)) return
        cooldowns.start(packageName, null, EmergencyBrakeFeature.remainingMs())
        lastCooldownScopes[packageName] = null
    }

    /**
     * Starts the "finish what you're watching" grace after the user tapped the finish button on
     * the warning screen: the cooldown is lifted, and the user is guided out automatically once
     * they scroll on a few more times or the grace times out — whichever comes first.
     */
    fun startFinishGrace(context: Context, packageName: String) {
        // finishing first is a way out, and a brake closes those
        if (EmergencyBrakeFeature.covers(this)) return
        val appContext = context.applicationContext
        cooldowns.clear(packageName)
        activeScrollViews.clear()
        finishGrace.start(packageName, lastCooldownScopes[packageName])
        cancelGraceTimeout(packageName)
        scheduleGraceTimeout(appContext, packageName, FinishGraceTracker.DEFAULT_TIMEOUT_MS)
    }

    /**
     * Posts the grace timeout for [packageName]. When it fires, the deadline may have moved on —
     * the grace extends while the user reads a discussion (see [FinishGraceTracker]) — in which
     * case the timeout is re-posted for the remainder instead of guiding out.
     */
    private fun scheduleGraceTimeout(context: Context, packageName: String, delayMs: Long) {
        val timeout = object : Runnable {
            override fun run() {
                graceTimeouts.remove(packageName)
                val remainingMs = finishGrace.remainingMs(packageName) ?: return
                if (remainingMs <= 0) {
                    guideOut(context, packageName)
                } else {
                    graceTimeouts[packageName] = this
                    graceHandler.postDelayed(this, remainingMs)
                }
            }
        }
        graceTimeouts[packageName] = timeout
        graceHandler.postDelayed(timeout, delayMs)
    }

    /**
     * The finish-grace is over while the user is still in the app: start the cooldown and show
     * the guide-out screen, which takes them to the home screen automatically. If the grace ended
     * because doom scrolling resumed on another surface, the cooldown covers the whole app.
     */
    private fun guideOut(
        context: Context,
        packageName: String,
        escalateToWholeApp: Boolean = false
    ) {
        cancelGraceTimeout(packageName)
        val grace = finishGrace.end(packageName) ?: return
        if (!isActivated || PauseButtonFeature.isFeaturePaused(this)) return
        val cooldownScope = if (escalateToWholeApp) null else grace.surfaceId
        lastCooldownScopes[packageName] = cooldownScope
        cooldowns.start(packageName, cooldownScope, cooldownTime)
        scrollSessionMonitor.reset(packageName)
        shortVideoRuns.reset(packageName)
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
     * The session-duration gate for detection while an app is on probation — much shorter than
     * the group's [timeUntilWarning], but never longer (in case the user configured something
     * tiny).
     */
    private fun probationSessionGateMs(timeUntilWarning: Long): Long =
        minOf(timeUntilWarning, PROBATION_SESSION_GATE_MS)

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
                R.string.feature_doomScrolling_cooldown_context_app
            } else {
                R.string.feature_doomScrolling_cooldown_context_surface
            },
            context.appLabel(packageName),
            remainingMinutes
        )
        openBreakScreen(context, packageName, BreakScreenMode.COOLDOWN, text)
    }

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

    /** Upper bound of the session-duration gate while an app is on probation. */
    private const val PROBATION_SESSION_GATE_MS = 30_000L

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

