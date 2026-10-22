package com.flx_apps.digitaldetox.rules

import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * How much of each group's budget is used. A group's time runs while any of its apps or websites
 * is in front and the screen is on, measured here rather than read from the system's usage
 * records: it needs no permission, it reaches the work profile, and it is exact to the moment the
 * app came to the front, which a limit that locks an app mid-session needs.
 *
 * The used time is kept per group and rolled over when the group's [BudgetWindow] changes, so
 * the next opening of a window starts from zero even if nothing happened in between. The total
 * and the window it belongs to are persisted, and a running session is folded into the total
 * every [CHECKPOINT_MS], so a process that dies mid-session loses at most that much, in the
 * user's favour.
 *
 * The service hands out the app switches on the main thread; the widgets and the notification read
 * the totals from a background one, hence the locking.
 */
object GroupBudgets {
    /** Just past a threshold, so the check finds it crossed rather than a few ms short. */
    private const val PAST_THRESHOLD_MS = 250L

    /** How often a running session is written down, see the class docs. */
    private const val CHECKPOINT_MS = 2 * 60_000L

    private class State(id: GroupId) {
        var usedMs: Long by DataStoreProperty(longPreferencesKey("${id}_usedUpScreenTime"), 0L)

        /** The window [usedMs] belongs to, see [windowKeyOf]. */
        var windowKey: String by DataStoreProperty(stringPreferencesKey("${id}_budgetWindow"), "")

        /** When the running session started, in wall-clock ms, or 0 without one. */
        var sessionSinceMs: Long = 0L
    }

    private val states = HashMap<GroupId, State>()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Fires when a running session is expected to cross a threshold, so a limit takes hold while
     * the user stays inside one app. No window event arrives in that case, and without this the
     * limit would only be noticed when they leave.
     */
    private val thresholdCheck = Runnable {
        runCatching {
            Rules.reevaluateForeground()
            // the widgets show what is left, and the launcher widget dims what is now locked
            ScreenTimeSurfaces.invalidate(DetoxDroidApplication.appContext)
        }.onFailure { Timber.e(it, "Could not apply a budget that ran out") }
    }

    /** Folds the running sessions into their totals and comes back while any is still running. */
    private val checkpoint = Runnable { checkpointSessions() }

    private fun stateOf(group: RuleGroup): State = states.getOrPut(group.id) { State(group.id) }

    /**
     * The time used in the current window, the running session included. Reading it rolls the
     * window over where it has changed since the last read.
     */
    @Synchronized
    fun usedMs(group: RuleGroup, nowMs: Long = System.currentTimeMillis()): Long {
        val state = stateOf(group)
        rollWindow(group, state, nowMs)
        val session = if (state.sessionSinceMs > 0L) (nowMs - state.sessionSinceMs).coerceAtLeast(0L) else 0L
        return state.usedMs + session
    }

    /** What is left of the budget, or null for a group without one. */
    fun leftMs(group: RuleGroup, nowMs: Long = System.currentTimeMillis()): Long? =
        if (group.hasBudget) (group.budgetMs - usedMs(group, nowMs)).coerceAtLeast(0L) else null

    /** Whether the group's budget is used up. Never, without one. */
    fun isExhausted(group: RuleGroup, nowMs: Long = System.currentTimeMillis()): Boolean =
        group.hasBudget && usedMs(group, nowMs) >= group.budgetMs

    /** Whether the group's gray step has been reached: from the start, or past its threshold. */
    fun isGrayReached(group: RuleGroup, nowMs: Long = System.currentTimeMillis()): Boolean =
        group.grays && (group.graysFromStart || usedMs(group, nowMs) >= group.grayAfterMs)

    /**
     * The time spent past the gray threshold in the current window, for the usage statistics'
     * "in grayscale". 0 for a group that does not turn gray.
     */
    fun grayMs(group: RuleGroup, nowMs: Long = System.currentTimeMillis()): Long = when {
        !group.grays -> 0L
        group.graysFromStart -> usedMs(group, nowMs)
        else -> (usedMs(group, nowMs) - group.grayAfterMs).coerceAtLeast(0L)
    }

    /**
     * The groups whose app or website is in front changed. Sessions start for the ones that now
     * cover it and end for the ones that no longer do; then the next threshold is armed.
     */
    @Synchronized
    fun onForeground(covering: Collection<RuleGroup>, nowMs: Long = System.currentTimeMillis()) {
        val budgeted = covering.filter { it.hasBudget }
        val budgetedIds = budgeted.mapTo(mutableSetOf()) { it.id }
        // end what no longer runs first, so a group that is still covered keeps its session; a
        // group that lost its budget while covered has nothing left to count either
        states.forEach { (id, state) ->
            if (id !in budgetedIds) endSession(id, state, nowMs)
        }
        budgeted.forEach { group ->
            val state = stateOf(group)
            rollWindow(group, state, nowMs)
            if (state.sessionSinceMs == 0L) state.sessionSinceMs = nowMs
        }
        armThresholdCheck(covering, nowMs)
        mainHandler.removeCallbacks(checkpoint)
        if (budgeted.isNotEmpty()) mainHandler.postDelayed(checkpoint, CHECKPOINT_MS)
    }

    /**
     * Ends every running session: the screen went off, DetoxDroid paused or stopped. Nothing is in
     * front of a screen that is off, and a paused DetoxDroid sees no more app switches that could
     * end the session later.
     */
    @Synchronized
    fun stopAll(nowMs: Long = System.currentTimeMillis()) {
        states.forEach { (id, state) -> endSession(id, state, nowMs) }
        mainHandler.removeCallbacks(thresholdCheck)
        mainHandler.removeCallbacks(checkpoint)
    }

    /** Drops what is kept for a group that is gone. */
    @Synchronized
    fun forget(id: GroupId) {
        states.remove(id)
    }

    private fun endSession(id: GroupId, state: State, nowMs: Long) {
        if (state.sessionSinceMs == 0L) return
        foldSession(id, state, nowMs)
        state.sessionSinceMs = 0L
    }

    /**
     * Books the running session up to [nowMs] and lets it go on from there. The window may have
     * changed since the session began; rolling it first keeps what came after the boundary in the
     * new window and nothing of the old one.
     */
    private fun foldSession(id: GroupId, state: State, nowMs: Long) {
        RuleGroups.get(id)?.let { rollWindow(it, state, nowMs) }
        state.usedMs += (nowMs - state.sessionSinceMs).coerceAtLeast(0L)
        state.sessionSinceMs = nowMs
    }

    @Synchronized
    private fun checkpointSessions(nowMs: Long = System.currentTimeMillis()) {
        var running = false
        states.forEach { (id, state) ->
            if (state.sessionSinceMs == 0L) return@forEach
            foldSession(id, state, nowMs)
            running = true
        }
        if (running) mainHandler.postDelayed(checkpoint, CHECKPOINT_MS)
    }

    /**
     * Starts the count over when [nowMs] is in another window than the stored total. A session
     * that straddles the boundary continues from the boundary, so the minutes before it do not
     * count twice.
     */
    private fun rollWindow(group: RuleGroup, state: State, nowMs: Long) {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
        val start = BudgetWindow.startOf(group.scheduleRules, group.budgetPeriod, now)
        val key = windowKeyOf(start, group.budgetPeriod)
        if (key == state.windowKey) return
        state.usedMs = 0L
        state.windowKey = key
        if (state.sessionSinceMs > 0L) {
            val boundaryMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            state.sessionSinceMs = maxOf(state.sessionSinceMs, boundaryMs)
        }
    }

    private fun windowKeyOf(start: LocalDateTime, period: BudgetPeriod) = "$period:$start"

    /**
     * Arms the check for the first threshold a running session is going to cross: a gray step's
     * point in the budget, or the budget itself.
     */
    private fun armThresholdCheck(covering: Collection<RuleGroup>, nowMs: Long) {
        mainHandler.removeCallbacks(thresholdCheck)
        val nextMs = covering.filter { it.hasBudget }.flatMap { group ->
            val used = usedMs(group, nowMs)
            listOfNotNull(
                group.budgetMs.takeIf { it > used },
                group.grayAfterMs.takeIf { group.grays && !group.graysFromStart && it > used }
            ).map { it - used }
        }.minOrNull() ?: return
        mainHandler.postDelayed(thresholdCheck, nextMs + PAST_THRESHOLD_MS)
    }
}
