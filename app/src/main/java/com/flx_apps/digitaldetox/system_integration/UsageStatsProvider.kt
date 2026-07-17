package com.flx_apps.digitaldetox.system_integration

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.TenSecondsInMs
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object UsageStatsProvider {

    /**
     * Two foreground phases of the same app separated by less than this gap are counted as one
     * user-perceived "session" in [groupSessionCounts].
     */
    private const val SESSION_GAP_THRESHOLD_MS = 5L * 60L * 1000L

    private val usageStatsManager: UsageStatsManager
        get() = DetoxDroidApplication.appContext.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

    private var usageStatsTodayLastRefresh = 0L

    var usageStatsToday: Map<String, UsageStats> = mapOf()
        get() {
            val now = System.currentTimeMillis()
            if (now - usageStatsTodayLastRefresh > TenSecondsInMs) {
                val dayBeginningMs =
                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                field = queryForPeriod(dayBeginningMs, now)
                usageStatsTodayLastRefresh = now
            }
            return field
        }

    fun getUpdatedUsageStatsToday(): Map<String, UsageStats> {
        usageStatsTodayLastRefresh = 0L
        return usageStatsToday
    }

    fun getScreenTimeForApps(apps: List<String>): Long {
        return apps.sumOf { usageStatsToday[it]?.totalTimeInForeground ?: 0L }
    }

    /**
     * Queries the OS daily usage buckets for [startMs]..[endMs] and merges them into one
     * [UsageStats] per package, dropping apps without foreground time in the period.
     */
    fun queryForPeriod(startMs: Long, endMs: Long): Map<String, UsageStats> {
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        ).filter {
            it.lastTimeUsed >= startMs && it.totalTimeInForeground > 0
        }.groupingBy {
            it.packageName
        }.aggregate { _, accumulator: UsageStats?, element: UsageStats, first ->
            if (first) element else accumulator!!.apply { add(element) }
        }
    }

    data class SessionInfo(
        val packageName: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val totalTimeMs: Long
    )

    data class EventCounts(
        val launchCounts: Map<String, Int>,
        val hourBuckets: IntArray,
        val unlockCount: Int,
        val unlockHourBuckets: IntArray,
        val sessions: List<SessionInfo>
    )

    /**
     * Walks the OS usage-event log for [startMs]..[endMs] and derives per-app launch counts,
     * launches-per-hour buckets, unlock counts (keyguard dismissals) and raw foreground sessions.
     *
     * Note: the OS keeps the event log only for a limited window (typically about a week), so for
     * longer ranges the results cover just the retained tail and undercount the full period.
     */
    fun queryEventCounts(startMs: Long, endMs: Long): EventCounts {
        val events = usageStatsManager.queryEvents(startMs, endMs)

        val launchCounts = mutableMapOf<String, Int>()
        val hourBuckets = IntArray(24)
        val unlockHourBuckets = IntArray(24)
        var unlockCount = 0
        val sessions = mutableListOf<SessionInfo>()
        val cal = Calendar.getInstance()
        val event = UsageEvents.Event()

        var currentPkg: String? = null
        var currentStartMs: Long = 0

        fun endCurrentSession(endTimeMs: Long) {
            val pkg = currentPkg ?: return
            if (currentStartMs > 0) {
                val duration = endTimeMs - currentStartMs
                if (duration > 0) {
                    sessions.add(SessionInfo(pkg, currentStartMs, endTimeMs, duration))
                }
            }
            currentPkg = null
            currentStartMs = 0
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                // same wire values as the deprecated MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND,
                // so pre-API-29 events are matched as well
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    launchCounts.merge(event.packageName, 1, Int::plus)
                    cal.timeInMillis = event.timeStamp
                    hourBuckets[cal.get(Calendar.HOUR_OF_DAY)]++
                    endCurrentSession(event.timeStamp)
                    currentPkg = event.packageName
                    currentStartMs = event.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (currentPkg == event.packageName) {
                        endCurrentSession(event.timeStamp)
                    }
                }

                // KEYGUARD_HIDDEN marks an actual unlock; SCREEN_INTERACTIVE would also count
                // every notification glance / ambient-display wake-up.
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    unlockCount++
                    cal.timeInMillis = event.timeStamp
                    unlockHourBuckets[cal.get(Calendar.HOUR_OF_DAY)]++
                }
            }
        }
        endCurrentSession(endMs)

        return EventCounts(launchCounts, hourBuckets, unlockCount, unlockHourBuckets, sessions)
    }

    /**
     * Collapses raw foreground [sessions] into user-perceived session counts per package: phases
     * separated by no more than [gapThresholdMs] count as one session.
     */
    fun groupSessionCounts(
        sessions: List<SessionInfo>, gapThresholdMs: Long = SESSION_GAP_THRESHOLD_MS
    ): Map<String, Int> {
        return sessions.groupBy { it.packageName }.mapValues { (_, pkgSessions) ->
            val sorted = pkgSessions.sortedBy { it.startTimeMs }
            var count = 1
            for (i in 1 until sorted.size) {
                if (sorted[i].startTimeMs - sorted[i - 1].endTimeMs > gapThresholdMs) {
                    count++
                }
            }
            count
        }
    }

    /**
     * Queries the merged per-app usage for each of the last [days] calendar days (today first).
     * Limited by the OS retention window for daily buckets.
     */
    fun queryDailyUsage(days: Int): List<Pair<LocalDate, Map<String, UsageStats>>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        return (0 until days).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            date to queryForPeriod(startMs, endMs)
        }
    }
}
