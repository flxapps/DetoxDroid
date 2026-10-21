package com.flx_apps.digitaldetox.util

import android.os.SystemClock

/**
 * Counts the short videos swiped past on a pager, one run per app. A run is the stretch of
 * swiping on one surface; it ends after [GAP_MS] without a swipe, or when the swiping moves to
 * another surface.
 *
 * A video counts when the pager reports a higher first visible position than before, which the
 * list widgets do, so the many events of one swipe's momentum count once. A pager that reports no
 * position counts one video per forward swipe instead, the events closer together than
 * [BURST_SPACING_MS] taken as one swipe. Swiping back counts nothing either way: that is watching
 * again, not moving on.
 */
class ShortVideoRuns(private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }) {
    /**
     * How long the run has been going and how many videos were swiped past in it.
     * @param fresh whether this event started the run
     */
    data class Snapshot(val durationMs: Long, val videos: Int, val fresh: Boolean)

    private class Run(val surfaceId: String, val startMs: Long) {
        var lastEventMs = startMs
        var lastIndex = -1

        /** Far enough back that the first forward event of the run is a swipe of its own. */
        var lastSwipeMs = startMs - BURST_SPACING_MS
        var videos = 0
    }

    private val runs = HashMap<String, Run>()

    /**
     * Registers a scroll event on a short-video pager.
     * @param fromIndex the pager's first visible position after the event, or -1 where it reports
     * none
     * @param forward whether the event moved on rather than back
     */
    @Synchronized
    fun onScrollEvent(
        packageName: String,
        surfaceId: String,
        fromIndex: Int,
        forward: Boolean
    ): Snapshot {
        val now = nowMs()
        var run = runs[packageName]
        val fresh = run == null || run.surfaceId != surfaceId || now - run.lastEventMs > GAP_MS
        if (fresh || run == null) {
            run = Run(surfaceId, now)
            runs[packageName] = run
        }
        run.lastEventMs = now
        if (fromIndex >= 0) {
            if (run.lastIndex >= 0 && fromIndex > run.lastIndex) run.videos++
            run.lastIndex = fromIndex
        } else if (forward && now - run.lastSwipeMs >= BURST_SPACING_MS) {
            run.lastSwipeMs = now
            run.videos++
        }
        return Snapshot(now - run.startMs, run.videos, fresh)
    }

    /** Forgets the app's run, after a break fired on it. */
    @Synchronized
    fun reset(packageName: String) {
        runs.remove(packageName)
    }

    companion object {
        /** A pause this long without a swipe ends the run. */
        const val GAP_MS = 45_000L

        /** Events closer together than this belong to one swipe. */
        const val BURST_SPACING_MS = 800L
    }
}
