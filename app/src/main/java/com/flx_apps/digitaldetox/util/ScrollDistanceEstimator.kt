package com.flx_apps.digitaldetox.util

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Produces the odometer contribution of a single scroll event, in screen pixels.
 *
 * [android.view.accessibility.AccessibilityEvent.getScrollDeltaY] comes in three flavors:
 * - **Pixels** (RecyclerView, Compose — the typical doom-scrolling apps): trusted as-is.
 * - **Position/index deltas** (legacy list widgets, values like 1–5) or **0** (frameworks that
 *   don't populate the field), plus **Android 8** where the API doesn't exist at all: the real
 *   distance is unknown, so each scroll *gesture* is estimated as [ESTIMATED_SWIPE_SCREEN_FRACTION]
 *   of the screen height instead.
 *
 * Heuristics, chosen so that errors stay small and biased toward *under*counting:
 * - An app is permanently treated as pixel-reporting once a single delta ≥
 *   [PIXEL_MODE_MIN_DELTA_PX] is seen (any fling produces one); position deltas realistically
 *   never reach that per event. Misclassification therefore degrades to the old exact-but-tiny
 *   counting, never to overcounting.
 * - Mid-size deltas (≥ [MIN_COUNTABLE_DELTA_PX]) are counted at face value even before the app is
 *   classified — slow reading-scrolls in pixel apps land here, which keeps them exact instead of
 *   estimated.
 * - Estimates are rate-limited to one per [ESTIMATE_WINDOW_MS] per app (≈ 1.2 screen heights per
 *   second at most), well below what real fast flinging covers, so continuous event streams from
 *   a single gesture can't inflate the odometer.
 */
class ScrollDistanceEstimator(
    screenHeightPx: Int,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val estimatedSwipePx =
        (screenHeightPx.coerceIn(320, 4000) * ESTIMATED_SWIPE_SCREEN_FRACTION).toInt()
    private val pixelModeApps = ConcurrentHashMap.newKeySet<String>()
    private val lastEstimateMs = ConcurrentHashMap<String, Long>()

    /** Returns the distance in pixels to add to the odometer for this scroll event. */
    fun distancePxFor(packageName: String, deltaMagnitude: Int): Int {
        if (deltaMagnitude >= PIXEL_MODE_MIN_DELTA_PX) {
            pixelModeApps.add(packageName)
        }
        if (packageName in pixelModeApps || deltaMagnitude >= MIN_COUNTABLE_DELTA_PX) {
            return minOf(deltaMagnitude, MAX_PLAUSIBLE_SCROLL_DELTA_PX)
        }
        // unknown units → credit one estimated swipe per rate-limit window
        val now = nowMs()
        val last = lastEstimateMs[packageName]
        if (last != null && now - last < ESTIMATE_WINDOW_MS) return 0
        lastEstimateMs[packageName] = now
        return estimatedSwipePx
    }

    companion object {
        /** A single event delta this large is only plausible as pixels (flings easily reach it). */
        private const val PIXEL_MODE_MIN_DELTA_PX = 200

        /** Deltas below this are treated as position-based/unknown and trigger estimation. */
        private const val MIN_COUNTABLE_DELTA_PX = 24

        /** Per-event cap so a single bogus delta can't teleport the odometer. */
        private const val MAX_PLAUSIBLE_SCROLL_DELTA_PX = 10_000

        /** At most one estimated swipe is credited per app within this window. */
        private const val ESTIMATE_WINDOW_MS = 500L

        /** Assumed travel of one swipe when the real distance is unknown. */
        private const val ESTIMATED_SWIPE_SCREEN_FRACTION = 0.6
    }
}
