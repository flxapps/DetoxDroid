package com.flx_apps.digitaldetox.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Room entity storing device-level (not per-app) daily stats: unlock counts and per-hour
 * histograms. Persisting these makes the unlocks / time-of-day cards accurate for ranges longer
 * than the OS event-log retention window (about a week).
 */
@Entity(tableName = "daily_device_stats")
data class DailyDeviceStats(
    @PrimaryKey val date: LocalDate,
    val unlockCount: Int,
    /** Unlocks per hour of day (24 slots). */
    val unlockHourBuckets: IntArray,
    /** App launches per hour of day (24 slots). */
    val launchHourBuckets: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DailyDeviceStats) return false
        return date == other.date &&
            unlockCount == other.unlockCount &&
            unlockHourBuckets.contentEquals(other.unlockHourBuckets) &&
            launchHourBuckets.contentEquals(other.launchHourBuckets)
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + unlockCount
        result = 31 * result + unlockHourBuckets.contentHashCode()
        result = 31 * result + launchHourBuckets.contentHashCode()
        return result
    }
}
