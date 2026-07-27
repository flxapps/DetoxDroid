package com.flx_apps.digitaldetox.util

import android.os.SystemClock

/**
 * Tracks the "finish what you're watching" grace after a doom-scrolling warning: the user may
 * finish the item they were consuming (a reel, a post) — including its discussion — but is guided
 * out of the app once they move on to new content.
 *
 * "Moving on" is judged against the scroll surface (view-id resource name) the warning fired on:
 * - Scrolling **the triggering surface** again means paging to the next item: [maxScrollBursts]
 *   distinct gestures use the grace up.
 * - Scrolling a **different identified surface** (the comments sheet, a thread) is reading, not
 *   consuming the next item: it never counts as a gesture and instead re-arms the [timeoutMs]
 *   window, so a discussion can be finished in peace — bounded by [maxTotalMs] overall.
 * - Events on **unidentified surfaces** are neutral (no gesture, no extension) — without an id we
 *   cannot tell reading from paging, and the timeout still bounds them.
 * - If the triggering surface itself had no id (common in Compose apps), every scroll event
 *   counts as before — there is nothing to scope against.
 *
 * This class only does the bookkeeping; the caller drives it from scroll events, schedules its
 * own timer for the timeout (scroll events stop while the user just watches, and the deadline
 * moves when the grace is extended — see [remainingMs]), and performs the actual guiding out.
 */
class FinishGraceTracker(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxScrollBursts: Int = DEFAULT_MAX_SCROLL_BURSTS,
    private val burstSpacingMs: Long = DEFAULT_BURST_SPACING_MS,
    private val maxTotalMs: Long = DEFAULT_MAX_TOTAL_MS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    /** An ended grace, carrying the scroll surface the original incident happened on. */
    data class EndedGrace(val surfaceId: String?)

    private inner class Grace(val surfaceId: String?, startedAtMs: Long) {
        var deadlineMs = startedAtMs + timeoutMs
        val hardDeadlineMs = startedAtMs + maxTotalMs
        var scrollBursts = 0
        var lastEventMs: Long? = null
    }

    private val graces = HashMap<String, Grace>()

    /** Starts a grace for [packageName]; [surfaceId] is carried into the eventual [EndedGrace]. */
    @Synchronized
    fun start(packageName: String, surfaceId: String?) {
        graces[packageName] = Grace(surfaceId, nowMs())
    }

    @Synchronized
    fun isInGrace(packageName: String): Boolean = graces.containsKey(packageName)

    @Synchronized
    fun activePackages(): Set<String> = graces.keys.toSet()

    /**
     * Registers a scroll event on [surfaceId] during [packageName]'s grace. Returns true once the
     * grace is used up — time to guide the user out (via [end]). Events closer than
     * [burstSpacingMs] to the previous one belong to the same gesture: accessibility scroll
     * events arrive in bursts, and a single fling must not count as several gestures.
     */
    @Synchronized
    fun onScrollEvent(packageName: String, surfaceId: String? = null): Boolean {
        val grace = graces[packageName] ?: return false
        val now = nowMs()
        if (now >= grace.deadlineMs) return true
        if (grace.surfaceId != null && surfaceId != grace.surfaceId) {
            // reading elsewhere (see the class docs): identified surfaces keep the grace
            // alive up to the hard cap, unidentified ones are neutral
            if (surfaceId != null) {
                grace.deadlineMs = (now + timeoutMs).coerceAtMost(grace.hardDeadlineMs)
            }
            return false
        }
        val last = grace.lastEventMs
        if (last == null || now - last >= burstSpacingMs) {
            grace.scrollBursts++
        }
        grace.lastEventMs = now
        return grace.scrollBursts >= maxScrollBursts
    }

    /**
     * Time until [packageName]'s grace times out (≥ 0), or null if it has no grace. The deadline
     * moves while the user is reading (see the class docs), so a timeout timer that fires should
     * re-check this and wait on if the grace was extended in the meantime.
     */
    @Synchronized
    fun remainingMs(packageName: String): Long? =
        graces[packageName]?.let { (it.deadlineMs - nowMs()).coerceAtLeast(0) }

    /** Ends the grace (if any) and returns what it covered; null if none was running. */
    @Synchronized
    fun end(packageName: String): EndedGrace? =
        graces.remove(packageName)?.let { EndedGrace(it.surfaceId) }

    companion object {
        /** Longest "one last thing" we wait for before guiding the user out. */
        const val DEFAULT_TIMEOUT_MS = 90_000L

        /** Scrolling on this many times during the grace means the user kept going. */
        const val DEFAULT_MAX_SCROLL_BURSTS = 3

        /** Events closer together than this count as one scroll gesture. */
        const val DEFAULT_BURST_SPACING_MS = 800L

        /** Hard cap on a grace, no matter how long the user keeps reading. */
        const val DEFAULT_MAX_TOTAL_MS = 300_000L
    }
}
