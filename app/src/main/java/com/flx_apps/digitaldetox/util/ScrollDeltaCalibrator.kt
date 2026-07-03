package com.flx_apps.digitaldetox.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Calibrates scroll events by their reported scroll distance so that, within an app, a long fling
 * counts more than a tiny nudge.
 *
 * [android.view.accessibility.AccessibilityEvent.getScrollDeltaY] units are framework-dependent
 * (list positions for some apps, pixels for others), so absolute deltas are meaningless across
 * apps. Instead, each event is weighted by its delta magnitude *relative to the app's own running
 * average* (exponential moving average). Consequences:
 *
 * - Apps that report no delta (always 0) or run on API < 28 get the neutral weight 1 — behavior
 *   is identical to plain event counting.
 * - Apps with constant deltas (e.g. always ±1) also get weight 1 — identical to plain counting.
 * - Only within apps with varying deltas do big scrolls weigh more than small ones, bounded by
 *   [minWeight]..[maxWeight] so no single event can distort the daily count.
 *
 * The long-run average weight stays ≈ 1, so calibrated counts remain on the same scale as
 * historical uncalibrated counts.
 */
class ScrollDeltaCalibrator(
    private val emaAlpha: Double = DEFAULT_EMA_ALPHA,
    private val minWeight: Double = DEFAULT_MIN_WEIGHT,
    private val maxWeight: Double = DEFAULT_MAX_WEIGHT
) {
    private val emaByApp = ConcurrentHashMap<String, Double>()

    /**
     * Returns the weight for a scroll event in [packageName] with the given |scrollDeltaY|.
     * [deltaMagnitude] <= 0 means the delta is unknown and yields the neutral weight 1.
     */
    fun weightFor(packageName: String, deltaMagnitude: Int): Double {
        if (deltaMagnitude <= 0) return NEUTRAL_WEIGHT
        val magnitude = deltaMagnitude.toDouble()
        val ema = emaByApp.merge(packageName, magnitude) { average, _ ->
            average + emaAlpha * (magnitude - average)
        }!!
        return (magnitude / ema).coerceIn(minWeight, maxWeight)
    }

    companion object {
        const val NEUTRAL_WEIGHT = 1.0
        private const val DEFAULT_EMA_ALPHA = 0.05
        private const val DEFAULT_MIN_WEIGHT = 0.25
        private const val DEFAULT_MAX_WEIGHT = 4.0
    }
}
