package com.flx_apps.digitaldetox.system_integration

import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageExclusions
import com.flx_apps.digitaldetox.util.startOfDayMillis
import java.time.LocalDate

/**
 * How much screen time the days before today had reached by this time of day, for "35 min less
 * than usual by now". A whole day's total would mislead in the morning, when today has barely
 * started and the days it is held against are long over.
 *
 * It comes from the system's event log, like today's side of the comparison, so both are measured
 * alike. The log goes back about a week. Days that are over don't change, so they are read once a
 * day, and again when the apps left out change.
 */
object UsualScreenTime {
    /** The days looked back on. The event log rarely reaches further anyway. */
    private const val DAYS = 7L

    /** With fewer days than this to go by, nothing is usual yet. */
    private const val MIN_DAYS = 3

    /**
     * Each day's foreground phases as offsets from the start of that day, start and end in turn,
     * as read under [key].
     */
    private class Days(val key: Any, val phases: List<LongArray>)

    @Volatile
    private var cached: Days? = null

    /**
     * What the days before [today] had typically reached [sinceStartMs] after they began: the
     * median, so one long night does not make it. Null with fewer than [MIN_DAYS] days to go by.
     * Blocking: the first call of a day walks a week of the event log.
     */
    fun byNow(exclusions: UsageExclusions, today: LocalDate, sinceStartMs: Long): Long? {
        val days = days(exclusions, today)
        if (days.size < MIN_DAYS) return null
        return days.map { usedBefore(it, sinceStartMs) }.sorted()[days.size / 2]
    }

    private fun days(exclusions: UsageExclusions, today: LocalDate): List<LongArray> {
        val key = Triple(today, exclusions.apps, exclusions.categories)
        cached?.takeIf { it.key == key }?.let { return it.phases }
        val phases = (1..DAYS).mapNotNull { daysBack ->
            val day = today.minusDays(daysBack)
            val startMs = day.startOfDayMillis()
            val sessions = UsageStatsProvider.queryEventCounts(
                startMs, day.plusDays(1).startOfDayMillis()
            ) { !exclusions.excludes(it) }.sessions
            // a day the log does not reach, or one the phone stayed off, says nothing about what
            // is usual
            if (sessions.isEmpty()) return@mapNotNull null
            LongArray(sessions.size * 2).also { offsets ->
                sessions.forEachIndexed { i, session ->
                    offsets[2 * i] = session.startTimeMs - startMs
                    offsets[2 * i + 1] = session.endTimeMs - startMs
                }
            }
        }
        cached = Days(key, phases)
        return phases
    }

    /** The part of the phases in [offsets] that lies before [untilMs]. */
    private fun usedBefore(offsets: LongArray, untilMs: Long): Long {
        var usedMs = 0L
        for (i in offsets.indices step 2) {
            usedMs += (minOf(offsets[i + 1], untilMs) - offsets[i]).coerceAtLeast(0L)
        }
        return usedMs
    }
}
