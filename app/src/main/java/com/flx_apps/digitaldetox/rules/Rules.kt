package com.flx_apps.digitaldetox.rules

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.FeaturesProvider
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.DetoxDroidState
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.websites.WebPage
import com.flx_apps.digitaldetox.websites.Websites
import com.flx_apps.digitaldetox.workers.ServiceReliabilityScheduler
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Where the groups meet the features. The service tells it what is in front ([onForeground]); the
 * step features ask it what to do with that ([lockDecision], [grayDecision], [scrollBreak]).
 *
 * Several groups can cover one app, and each answer takes all of them into account: an app locks
 * when any covering group locks it, waits for the longest wait, turns gray when any group says so,
 * and gets its scroll break at the shortest time. "Everything else" (a group of all apps but a
 * few) therefore keeps covering an app that is also in a group of its own, which is also exactly
 * what the feature lists did before there were groups.
 */
object Rules {
    /** One group that covers what is in front, and the list entry it covers it under. */
    class Coverage(val group: RuleGroup, val entry: String)

    /**
     * What is in front and who covers it.
     * @param subject what the step features are told is in front: the app, or for a website the
     * most specific entry any group lists it under (or the page's own site under "every website
     * except"). An identity for waits, cooldowns and block screens, nothing more.
     */
    class Foreground(
        val app: String,
        val page: WebPage?,
        val subject: String,
        val covering: List<Coverage>,
    ) {
        fun groups(): List<RuleGroup> = covering.map { it.group }
    }

    sealed interface LockDecision {
        /** No active group locks or delays this. */
        data object NotCovered : LockDecision

        /** Every open waits [waitMs] first. */
        data class Wait(val waitMs: Long) : LockDecision

        /** It may be used; [exhaustedInMs] says how long until a covering group locks it. */
        data class Open(val exhaustedInMs: Long?) : LockDecision

        /** [group] keeps it out of reach. */
        data class Locked(val group: RuleGroup) : LockDecision
    }

    sealed interface GrayDecision {
        /** No active group turns this gray. */
        data object NotCovered : GrayDecision

        /** In color for now; [grayInMs] says how long until a covering group turns it gray. */
        data class Color(val grayInMs: Long?) : GrayDecision

        data object Gray : GrayDecision
    }

    /** A scroll break after [afterMs] of sustained scrolling, from the strictest covering group. */
    data class ScrollBreak(val afterMs: Long)

    @Volatile
    private var current: Foreground? = null

    /** What the service last said was in front, or null before the first app switch. */
    val foreground: Foreground? get() = current

    /**
     * Works out who covers [app] showing [page] at [now], without remembering it. The groups are
     * the active ones; a website is matched the way each group's own list sees it, see
     * [Websites.foregroundEntry].
     */
    fun resolve(app: String, page: WebPage?, now: LocalDateTime = LocalDateTime.now()): Foreground {
        val covering = RuleGroups.active(now).mapNotNull { group ->
            val entry = if (page != null) {
                Websites.foregroundEntry(group, page) ?: app
            } else {
                app
            }
            if (group.appliesTo(entry)) Coverage(group, entry) else null
        }
        val subject = if (page != null) {
            // the most specific website entry anyone listed wins over the browser itself
            Websites.bestMatch(covering.map { it.entry }, page)
                ?: covering.map { it.entry }.firstOrNull { Websites.isWebsite(it) }
                ?: app
        } else {
            app
        }
        return Foreground(app, page, subject, covering)
    }

    /**
     * The service hands over what came to the front. Budgets start and stop counting here, before
     * any feature acts on the answer.
     */
    fun onForeground(app: String, page: WebPage?): Foreground {
        val foreground = resolve(app, page)
        current = foreground
        GroupBudgets.onForeground(foreground.groups())
        return foreground
    }

    /** Nothing is in front: the screen went off, DetoxDroid paused or stopped. */
    fun onNothingInFront() {
        current = null
        GroupBudgets.stopAll()
    }

    /**
     * The groups covering [subject]. For what is in front that is the last answer; for anything
     * else (a test, a check on an app that is not open) it is worked out as if the app came to the
     * front now.
     */
    fun coverage(subject: String): List<Coverage> =
        current?.takeIf { it.subject == subject }?.covering ?: resolve(subject, null).covering

    /**
     * What the lock and the wait make of [subject]. A website is held back by every group that
     * covers it; an app only by groups that name it, see [RuleGroup.canHoldBackApps]. Under a
     * brake every lock takes hold, whatever is left of its budget.
     */
    fun lockDecision(subject: String): LockDecision {
        val isWebsite = Websites.isWebsite(subject)
        val holding = coverage(subject).map { it.group }.filter { isWebsite || it.canHoldBackApps }
        val locking = holding.filter { it.locks }
        if (locking.isNotEmpty()) {
            val braking = EmergencyBrakeFeature.isBraking()
            locking.firstOrNull { braking || !it.hasBudget || GroupBudgets.isExhausted(it) }?.let {
                return LockDecision.Locked(it)
            }
        }
        val waitMs = holding.filter { it.waits }.maxOfOrNull { it.waitBeforeOpeningMs } ?: 0L
        if (waitMs > 0L) return LockDecision.Wait(waitMs)
        if (locking.isEmpty()) return LockDecision.NotCovered
        return LockDecision.Open(locking.mapNotNull { GroupBudgets.leftMs(it) }.minOrNull())
    }

    /** What the gray step makes of [subject]. */
    fun grayDecision(subject: String): GrayDecision {
        val graying = coverage(subject).map { it.group }.filter { it.grays }
        if (graying.isEmpty()) return GrayDecision.NotCovered
        if (graying.any { GroupBudgets.isGrayReached(it) }) return GrayDecision.Gray
        val grayInMs = graying
            .filter { it.hasBudget && !it.graysFromStart }
            .minOfOrNull { (it.grayAfterMs - GroupBudgets.usedMs(it)).coerceAtLeast(0L) }
        return GrayDecision.Color(grayInMs)
    }

    /** The scroll break [subject] gets, or null when no covering group has the step. */
    fun scrollBreak(subject: String): ScrollBreak? =
        coverage(subject).map { it.group }.filter { it.breaksScrolling }
            .minOfOrNull { it.scrollBreakAfterMs }?.let { ScrollBreak(it) }

    /**
     * The apps that a group locks right now: named apps of groups whose lock has taken hold.
     * Websites are not in it, they are held back by the block screen when shown.
     */
    fun lockedApps(now: LocalDateTime = LocalDateTime.now()): Set<String> {
        val braking = EmergencyBrakeFeature.isBraking()
        return RuleGroups.active(now)
            .filter { it.locks && it.canHoldBackApps }
            .filter { braking || !it.hasBudget || GroupBudgets.isExhausted(it) }
            .flatMapTo(mutableSetOf()) { Websites.appsOf(it.appExceptions) }
    }

    /** Whether any active group needs to know which website a browser shows. */
    fun websitesMatter(now: LocalDateTime = LocalDateTime.now()): Boolean =
        RuleGroups.active(now).any(Websites::matterTo)

    /**
     * Whether [feature] has anything to do right now: a switched-on group uses one of its steps.
     */
    fun usesFeature(feature: Feature): Boolean = RuleGroups.enabled().any { it.usesFeature(feature) }

    /**
     * The steps of [group] (or of every switched-on group) whose feature is missing a permission
     * and so does nothing yet: the block screens need the overlay permission, gray needs the
     * system filter or the screen filter switched on.
     */
    fun stepsMissingPermission(context: Context, group: RuleGroup? = null): Set<Step> {
        val groups = group?.let { listOf(it) } ?: RuleGroups.enabled()
        return groups.flatMap { it.steps }.toSet().filterTo(mutableSetOf()) { step ->
            val feature = step.feature
            feature is NeedsPermissionsFeature && !feature.hasPermissions(context)
        }
    }

    /**
     * Judges the app in front again without a window event: a budget ran out, a group's window
     * opened or closed, a group was edited.
     */
    fun reevaluateForeground() {
        DetoxDroidAccessibilityService.instance?.redispatchForegroundApp()
    }

    /** The active groups the last time [onGroupsChanged] or a schedule reload looked. */
    @Volatile
    private var lastActiveGroupIds: Set<GroupId> = emptySet()

    /**
     * A schedule boundary passed: if it opened or closed a group, the app in front is judged
     * again. Called from the features' periodic reload, so an idle phone picks it up as well.
     * @return whether the set of active groups changed
     */
    fun applyScheduleTransitions(): Boolean {
        val active = RuleGroups.active().mapTo(mutableSetOf()) { it.id }
        if (active == lastActiveGroupIds) return false
        lastActiveGroupIds = active
        if (DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Active) {
            reevaluateForeground()
        }
        return true
    }

    /**
     * A group was added, edited or removed. The features whose steps gained or lost work start or
     * stop, the app in front is judged again, and everything that shows the groups is told.
     */
    fun onGroupsChanged() {
        // the service and the surfaces expect the main thread; the usage screen edits off it
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { onGroupsChanged() }
            return
        }
        val context = DetoxDroidApplication.appContext
        runCatching {
            val activeBefore = FeaturesProvider.activeFeatures
            FeaturesProvider.reloadActiveFeatures()
            val activeAfter = FeaturesProvider.activeFeatures
            if (DetoxDroidAccessibilityService.updateState() == DetoxDroidState.Active) {
                activeBefore.filter { it !in activeAfter }.forEach { it.onPause(context) }
                activeAfter.filter { it !in activeBefore }.forEach { it.onStart(context) }
            }
            lastActiveGroupIds = RuleGroups.active().mapTo(mutableSetOf()) { it.id }
            reevaluateForeground()
            DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
        }.onFailure { Timber.w(it, "Could not apply the changed groups to the running features") }
        runCatching { ScreenTimeSurfaces.invalidate(context) }
        runCatching { ServiceReliabilityScheduler.scheduleNextScheduleBoundary(context) }
            .onFailure { Timber.w(it, "Could not arm the next schedule boundary") }
    }
}
