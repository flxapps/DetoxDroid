package com.flx_apps.digitaldetox.util

import android.os.SystemClock

/**
 * Tracks the "finish what you're watching" grace after a doom-scrolling warning: the user may
 * finish the item they were consuming (a reel, a post), but is guided out of the app once they
 * either start scrolling on again ([maxScrollBursts] distinct scroll gestures) or [timeoutMs]
 * passes — whichever comes first.
 *
 * This class only does the bookkeeping; the caller drives it from scroll events, schedules its
 * own timer for the timeout (scroll events stop while the user just watches), and performs the
 * actual guiding out.
 */
class FinishGraceTracker(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxScrollBursts: Int = DEFAULT_MAX_SCROLL_BURSTS,
    private val burstSpacingMs: Long = DEFAULT_BURST_SPACING_MS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    /** An ended grace, carrying the scroll surface the original incident happened on. */
    data class EndedGrace(val surfaceId: String?)

    private class Grace(val surfaceId: String?, val startedAtMs: Long) {
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
     * Registers a scroll event during [packageName]'s grace. Returns true once the grace is used
     * up — time to guide the user out (via [end]). Events closer than [burstSpacingMs] to the
     * previous one belong to the same gesture: accessibility scroll events arrive in bursts, and
     * a single fling must not count as several gestures.
     */
    @Synchronized
    fun onScrollEvent(packageName: String): Boolean {
        val grace = graces[packageName] ?: return false
        val now = nowMs()
        if (now - grace.startedAtMs >= timeoutMs) return true
        val last = grace.lastEventMs
        if (last == null || now - last >= burstSpacingMs) {
            grace.scrollBursts++
        }
        grace.lastEventMs = now
        return grace.scrollBursts >= maxScrollBursts
    }

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
    }
}
