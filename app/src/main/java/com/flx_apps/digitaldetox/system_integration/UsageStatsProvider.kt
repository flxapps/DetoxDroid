package com.flx_apps.digitaldetox.system_integration

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.util.startOfDayMillis
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * How long an app was actually on screen.
 *
 * Android counts two different things. [UsageStats.totalTimeInForeground] is the time an activity
 * spent resumed, which keeps running while the app sits behind the lock screen, a dialog or the
 * share sheet. [UsageStats.totalTimeVisible] is the time the app's window was really visible, and
 * it is what the system's own screen-time figures are built from. Reporting the first one is why
 * our numbers read higher than the ones the user can hold them up against.
 *
 * Visible time only exists from Android 10; below that there is nothing better to use.
 */
val UsageStats.screenTimeMs: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        totalTimeVisible.takeIf { it > 0 } ?: totalTimeInForeground
    } else {
        totalTimeInForeground
    }

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

    /** Today's foreground time per app so far, from the event log, see [foregroundTimeByPackage]. */
    fun foregroundTimeToday(): Map<String, Long> =
        foregroundTimeByPackage(LocalDate.now().startOfDayMillis(), System.currentTimeMillis())

    /**
     * The foreground time per app between [startMs] and [endMs], added up from the event log. The
     * daily buckets of [queryForPeriod] cannot give this for a part of a day: a bucket begins with
     * the first event after the previous one ran out and runs a day from there, so it is not
     * aligned to midnight and carries whatever came before it. The event log is kept for a few
     * days only, so this is for today and yesterday; the buckets remain the source further back.
     */
    fun foregroundTimeByPackage(startMs: Long, endMs: Long): Map<String, Long> =
        queryEventCounts(startMs, endMs).foregroundTimeByPackage()

    /**
     * Queries the OS daily usage buckets for [startMs]..[endMs] and merges them into one
     * [UsageStats] per package, dropping apps without foreground time in the period. A bucket is
     * a day long but begins where the previous one ran out, not at midnight, so the buckets of a
     * calendar day include what spilled over from the day before, see [foregroundTimeByPackage].
     */
    fun queryForPeriod(startMs: Long, endMs: Long): Map<String, UsageStats> {
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startMs, endMs
        ).filter {
            it.lastTimeUsed >= startMs && it.totalTimeInForeground > 0
        }.distinctBy {
            // One query can hand back the same daily bucket more than once, and add() below sums
            // whatever it is given, so a bucket that arrives twice reads as an app used for twice
            // as long as it was.
            it.packageName to it.firstTimeStamp
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
    ) {
        /** The foreground time per app, the [sessions] added up. */
        fun foregroundTimeByPackage(): Map<String, Long> {
            val totals = HashMap<String, Long>()
            sessions.forEach { totals.merge(it.packageName, it.totalTimeMs, Long::plus) }
            return totals
        }

        companion object {
            /** What the event log amounts to when there is no permission to read it. */
            val EMPTY = EventCounts(emptyMap(), IntArray(24), 0, IntArray(24), emptyList())
        }
    }

    /**
     * Walks the OS usage-event log for [startMs]..[endMs] and derives per-app launch counts,
     * launches-per-hour buckets, unlock counts (keyguard dismissals) and raw foreground sessions.
     *
     * Note: the OS keeps the event log only for a limited window (typically about a week), so for
     * longer ranges the results cover just the retained tail and undercount the full period.
     *
     * [includePackage] leaves apps out of everything that is attributed to an app. Unlocks are not:
     * the phone was unlocked whatever the user then opened.
     */
    fun queryEventCounts(
        startMs: Long, endMs: Long, includePackage: (String) -> Boolean = { true }
    ): EventCounts {
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
        // until the first app comes to the front inside the range, a pause is the app that was
        // already in front when the range began, in front since then
        var beforeFirstResume = true

        fun endCurrentSession(endTimeMs: Long) {
            val pkg = currentPkg ?: return
            if (currentStartMs > 0 && includePackage(pkg)) {
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
                    if (includePackage(event.packageName)) {
                        launchCounts.merge(event.packageName, 1, Int::plus)
                        cal.timeInMillis = event.timeStamp
                        hourBuckets[cal.get(Calendar.HOUR_OF_DAY)]++
                    }
                    endCurrentSession(event.timeStamp)
                    currentPkg = event.packageName
                    currentStartMs = event.timeStamp
                    beforeFirstResume = false
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (beforeFirstResume && currentPkg == null) {
                        currentPkg = event.packageName
                        currentStartMs = startMs
                        beforeFirstResume = false
                    }
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
     * The foreground time per app for each of the last [days] calendar days (today first): from
     * the event log where it still reaches, which is exact to the day, from the daily buckets
     * further back, which spill over from the day before (see [foregroundTimeByPackage]).
     */
    fun queryDailyUsage(days: Int): List<Pair<LocalDate, Map<String, Long>>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        return (0 until days).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            val startMs = date.startOfDayMillis(zone)
            val endMs = minOf(date.plusDays(1).startOfDayMillis(zone), System.currentTimeMillis())
            val events = queryEventCounts(startMs, endMs)
            val fromEvents = events.foregroundTimeByPackage()
            // a day the log no longer has leaves no session and no launch behind
            val retained = fromEvents.isNotEmpty() || events.launchCounts.isNotEmpty()
            date to if (retained) {
                fromEvents
            } else {
                queryForPeriod(startMs, endMs).mapValues { it.value.screenTimeMs }
            }
        }
    }
}
