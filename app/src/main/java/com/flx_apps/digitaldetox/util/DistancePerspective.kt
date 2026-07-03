package com.flx_apps.digitaldetox.util

import com.flx_apps.digitaldetox.R

/**
 * Turns abstract scroll distances into something tangible: physical meters and a playful
 * comparison against a well-known landmark. Deliberately kept light-hearted — the goal is a
 * moment of "huh, wow", not guilt.
 */
object DistancePerspective {

    /** A comparison of a scrolled distance against something with a graspable size. */
    sealed interface Comparison {
        /** "≈ 1.3 × the Eiffel Tower" */
        data class Landmark(val multiplier: Double, val nameRes: Int) : Comparison

        /** "≈ 12 floors" — for distances below the smallest landmark. */
        data class Floors(val floors: Int) : Comparison
    }

    /** Reference heights/lengths in meters, ascending. Names include their article ("the …"). */
    private val LANDMARKS = listOf(
        93.0 to R.string.landmark_statue_of_liberty,
        324.0 to R.string.landmark_eiffel_tower,
        443.0 to R.string.landmark_empire_state,
        828.0 to R.string.landmark_burj_khalifa,
        8_849.0 to R.string.landmark_mount_everest,
        42_195.0 to R.string.landmark_marathon
    )

    private const val METERS_PER_FLOOR = 3.0
    private const val MIN_FLOORS = 2

    /**
     * Picks the most fitting comparison for [meters]: the largest landmark that fits at least
     * once, floors for small distances, or null when the distance is too short to be interesting.
     */
    fun comparisonFor(meters: Double): Comparison? {
        val landmark = LANDMARKS.lastOrNull { (height, _) -> meters >= height }
        if (landmark != null) {
            return Comparison.Landmark(meters / landmark.first, landmark.second)
        }
        val floors = (meters / METERS_PER_FLOOR).toInt()
        return if (floors >= MIN_FLOORS) Comparison.Floors(floors) else null
    }

    /** Converts screen pixels to meters given the screen's physical vertical DPI. */
    fun pixelsToMeters(pixels: Long, ydpi: Float): Double {
        if (ydpi <= 0f) return 0.0
        return pixels / ydpi.toDouble() * METERS_PER_INCH
    }

    private const val METERS_PER_INCH = 0.0254
}
