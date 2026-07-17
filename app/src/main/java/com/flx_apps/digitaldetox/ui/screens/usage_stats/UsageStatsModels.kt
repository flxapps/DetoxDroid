package com.flx_apps.digitaldetox.ui.screens.usage_stats

import java.time.LocalDate

data class AppUsageStat(
    val packageName: String,
    val label: String,
    val totalTimeMs: Long,
    val launchCount: Int,
    val sessionCount: Int,
    val scrollEventCount: Int,
    val scrollIntensityScore: Float,
    val scrollsPerMinute: Float,
    /** Physical scroll distance (the app's share of the scroll odometer). */
    val scrollDistanceMeters: Double,
    val detoxBadges: List<DetoxBadge>
)

enum class DetoxBadge { GRAYSCALE, DISABLED, DOOM_SCROLL }

data class DailyUsage(
    val date: LocalDate,
    val totalTimeMs: Long
)

data class DetoxImpactData(
    val colorScreenTimeRemainingMs: Long?,
    val grayscaleTimeMs: Long?,
    val doomScrollBreakCount: Int?,
    val appsBlockedCount: Int?
)

/**
 * Per-app stats aggregated over a period, independent of whether the source was the OS
 * UsageStatsManager (today / recent days) or the Room history (longer ranges).
 */
data class AggregatedAppStats(
    val packageName: String,
    val totalTimeMs: Long,
    val launchCount: Int,
    val sessionCount: Int,
    val scrollCount: Int,
    val scrollDistancePx: Long
)

/** Everything the usage stats screen renders, emitted as one consistent snapshot. */
data class UsageStatsUiState(
    val timeFrame: TimeFrame = TimeFrame.TODAY,
    val customStart: LocalDate = LocalDate.now().minusDays(7),
    val customEnd: LocalDate = LocalDate.now().minusDays(1),
    val availableHistoryDays: Int = 0,
    val earliestHistoryDate: LocalDate? = null,
    val totalScreenTime: Long = 0L,
    val totalDelta: Long? = null,
    val topApps: List<AppUsageStat> = emptyList(),
    val byDay: List<DailyUsage> = emptyList(),
    val hourBuckets: IntArray = IntArray(24),
    val unlockCount: Int = 0,
    val unlockDelta: Int? = null,
    val unlockHourBuckets: IntArray = IntArray(24),
    val categoryDistribution: Map<String, Long> = emptyMap(),
    val weekdayAverages: FloatArray = FloatArray(7),
    val detoxImpact: DetoxImpactData? = null,
    val selectedDay: LocalDate? = null,
    val isRefreshing: Boolean = false,
    /** Number of calendar days in the selected period (1 for [TimeFrame.TODAY]). */
    val effectiveDays: Int = 1,
    /** Total physical scroll distance in the period (the "scroll odometer"). */
    val scrollDistanceMeters: Double = 0.0,
    /** Self-declared value of an hour, in local currency (0 = life-cost reframing disabled). */
    val hourlyRate: Int = 0
)
