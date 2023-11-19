package com.flx_apps.digitaldetox.feature_types

import java.time.LocalDate

/**
 * This feature can track the screen time of the user under certain conditions.
 * Example use cases are tracking the screen time of the user to disable or grayscale apps only
 * after a certain amount of time.
 */
interface ScreenTimeTrackingFeature {
    /**
     * The timestamp when the user did something that should be tracked.
     */
    var trackingSinceTimestamp: Long

    /**
     * The time the user has already used up their daily screen time.
     */
    var usedUpScreenTime: Long

    /**
     * The current date. If the date changes, the [usedUpScreenTime] is reset.
     */
    var today: LocalDate

    /**
     * This method should be called when the user does something that should be tracked, e.g. when
     * an app that should be disabled after a certain time is opened.
     */
    fun eventuallyStartTracking()

    /**
     * Increases the used up screen time by the time since [eventuallyStartTracking] was called.
     *
     * We track these times very tightly using this approach. We could consider using the
     * [UsageStatsProvider] as an alternative. This would require the usage stats permission,
     * but would also allow us to track the screen time even if DetoxDroid has been killed in the
     * meantime.
     *
     * Another advantage of the current approach is that we can also track the screen time for apps
     * that are installed within the Work Profile (whose usage stats are not available to the
     * main profile).
     */
    fun eventuallyIncreaseUsedUpScreenTime()

    class Impl : ScreenTimeTrackingFeature {
        override var trackingSinceTimestamp: Long = 0L
        override var usedUpScreenTime: Long = 0L
        override var today: LocalDate = LocalDate.now()

        override fun eventuallyStartTracking() {
            if (trackingSinceTimestamp == 0L) {
                // we are not tracking yet, so we start tracking now
                trackingSinceTimestamp = System.currentTimeMillis()
            }
        }

        override fun eventuallyIncreaseUsedUpScreenTime() {
            val today = LocalDate.now()
            if (today != this.today) {
                // the date has changed, so we reset the used up screen time
                usedUpScreenTime = 0L
                this.today = today
            }
            if (trackingSinceTimestamp == 0L) return // there is no tracking timestamp
            usedUpScreenTime += System.currentTimeMillis() - trackingSinceTimestamp
            trackingSinceTimestamp = 0L
        }
    }
}