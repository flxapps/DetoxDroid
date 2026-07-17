package com.flx_apps.digitaldetox.util

import android.os.SystemClock

/**
 * Tracks per-app scroll *sessions* and derives a live scrolling-intensity snapshot from them.
 *
 * A session is a stretch of scroll activity in one app; it ends after [SESSION_GAP_MS] without
 * scroll events. Within the current session, velocity and direction are computed over a sliding
 * [WINDOW_MS] window so the snapshot reflects what the user is doing *right now*, while the
 * session duration and total distance keep growing as long as the user keeps scrolling.
 *
 * Unlike the scroll-view-based endless-feed detection in
 * [com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature], sessions are keyed by app, so
 * the signal survives scroll-view recycling, virtualized lists that never report growth, and
 * view-ID churn between feeds of the same app.
 *
 * Distances are expected in screen pixels (see [ScrollDistanceEstimator]); the snapshot expresses
 * them in screen heights so thresholds are device-independent. The velocity divisor is the full
 * window even while the window is still filling up, which deliberately *under*-reports intensity
 * for young sessions.
 */
class ScrollSessionMonitor(
    screenHeightPx: Int,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val screenHeightPx = screenHeightPx.coerceIn(320, 4000)
    private val sessions = HashMap<String, SessionState>()

    /**
     * Registers a scroll event and returns the intensity snapshot of the app's current session.
     * @param distancePx the event's scroll distance in screen pixels (0 if unknown)
     * @param isDownScroll whether the event scrolled the content down (unknown counts as down,
     * consistent with [AccessibilityEventUtil.isDownScrollEvent])
     */
    @Synchronized
    fun onScrollEvent(
        packageName: String,
        distancePx: Int,
        isDownScroll: Boolean
    ): ScrollSessionSnapshot {
        val now = nowMs()
        sessions.values.removeAll { now - it.lastEventMs > SESSION_GAP_MS }
        val state = sessions.getOrPut(packageName) { SessionState(startMs = now) }
        state.lastEventMs = now
        state.sessionPx += distancePx
        state.window.addLast(Sample(now, distancePx, isDownScroll))
        while (state.window.first().timeMs < now - WINDOW_MS) {
            state.window.removeFirst()
        }

        var windowPx = 0L
        var downEvents = 0
        for (sample in state.window) {
            windowPx += sample.px
            if (sample.down) downEvents++
        }
        return ScrollSessionSnapshot(
            sessionDurationMs = now - state.startMs,
            screenHeightsPerMinute = windowPx.toFloat() / screenHeightPx / (WINDOW_MS / 60_000f),
            downScrollRatio = downEvents.toFloat() / state.window.size,
            sessionScreenHeights = state.sessionPx.toFloat() / screenHeightPx
        )
    }

    /** Forgets the app's current session, e.g. after a warning fired or the user snoozed it. */
    @Synchronized
    fun reset(packageName: String) {
        sessions.remove(packageName)
    }

    companion object {
        /** A pause this long without scroll events ends the session. */
        const val SESSION_GAP_MS = 45_000L

        /** Sliding window over which the live velocity and direction ratio are computed. */
        const val WINDOW_MS = 60_000L
    }

    private class SessionState(val startMs: Long) {
        var lastEventMs = startMs
        var sessionPx = 0L
        val window = ArrayDeque<Sample>()
    }

    private class Sample(val timeMs: Long, val px: Int, val down: Boolean)
}

/**
 * The live intensity of an app's current scroll session.
 * @param sessionDurationMs how long the user has been scrolling this app (with at most
 * [ScrollSessionMonitor.SESSION_GAP_MS] pauses)
 * @param screenHeightsPerMinute scroll velocity over the sliding window; reading pace is roughly
 * 2–4, sustained feed-flicking 15–40+
 * @param downScrollRatio share of window events that scrolled down (1.0 = only downward)
 * @param sessionScreenHeights total distance scrolled this session, in screen heights
 */
data class ScrollSessionSnapshot(
    val sessionDurationMs: Long,
    val screenHeightsPerMinute: Float,
    val downScrollRatio: Float,
    val sessionScreenHeights: Float
)
