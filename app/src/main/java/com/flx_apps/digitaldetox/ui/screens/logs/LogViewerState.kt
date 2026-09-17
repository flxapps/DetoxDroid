package com.flx_apps.digitaldetox.ui.screens.logs

import android.util.Log
import com.flx_apps.digitaldetox.util.LogEntry

/** The least severe entries a level filter still shows. */
enum class LogLevelFilter(val minPriority: Int) {
    ALL(Log.VERBOSE), INFO(Log.INFO), WARNINGS(Log.WARN), ERRORS(Log.ERROR)
}

data class LogFilter(
    val level: LogLevelFilter = LogLevelFilter.ALL,
    val tag: String? = null,
    val query: String = "",
) {
    val isActive: Boolean
        get() = level != LogLevelFilter.ALL || tag != null || query.isNotBlank()

    /** Everything but the level, which the level chips count across. */
    fun matchesTagAndQuery(entry: LogEntry): Boolean {
        if (tag != null && entry.tag != tag) return false
        val needle = query.trim()
        return needle.isEmpty() || entry.message.contains(needle, ignoreCase = true) ||
                entry.tag?.contains(needle, ignoreCase = true) == true
    }
}

/**
 * The entries one run of the app (one process) wrote, as far as they pass the filter, newest
 * first. [startedAt] and [endedAt] span all of the run's entries in the log, shown or not.
 */
data class LogRun(
    val pid: Int,
    val startedAt: Long,
    val endedAt: Long,
    val isCurrent: Boolean,
    val entries: List<LogEntry>,
) {
    /** Stays the same while the run grows, unlike its newest entry. */
    val key: Long get() = entries.lastOrNull()?.id ?: startedAt
}

data class LogViewerState(
    val total: Int,
    val shown: Int,
    /** How many entries each level filter would show, with the tag and query applied. */
    val levelCounts: Map<LogLevelFilter, Int>,
    /** Newest run first, leaving out runs without a shown entry. */
    val runs: List<LogRun>,
) {
    /** The shown entries oldest first, the way a log file reads. */
    val shownEntries: List<LogEntry>
        get() = runs.asReversed().flatMap { it.entries.asReversed() }
}

/**
 * Filters [entries] (oldest first, as the log keeps them) and splits them into runs.
 * [currentPid] marks the run that is still going.
 */
fun buildLogViewerState(entries: List<LogEntry>, filter: LogFilter, currentPid: Int): LogViewerState {
    val countsAtPriority = IntArray(Log.ASSERT + 1)
    val runs = mutableListOf<LogRun>()
    var runStart = 0
    for (index in entries.indices) {
        val entry = entries[index]
        if (filter.matchesTagAndQuery(entry)) {
            countsAtPriority[entry.priority.coerceIn(0, Log.ASSERT)]++
        }
        val runEnds = index == entries.lastIndex || entries[index + 1].pid != entry.pid
        if (!runEnds) continue
        val shown = entries.subList(runStart, index + 1).filter {
            it.priority >= filter.level.minPriority && filter.matchesTagAndQuery(it)
        }
        if (shown.isNotEmpty()) {
            runs += LogRun(
                pid = entry.pid,
                startedAt = entries[runStart].timestamp,
                endedAt = entry.timestamp,
                isCurrent = index == entries.lastIndex && entry.pid == currentPid,
                entries = shown.asReversed()
            )
        }
        runStart = index + 1
    }
    val levelCounts = LogLevelFilter.entries.associateWith { level ->
        (level.minPriority..Log.ASSERT).sumOf { countsAtPriority[it] }
    }
    return LogViewerState(
        total = entries.size,
        shown = levelCounts.getValue(filter.level),
        levelCounts = levelCounts,
        runs = runs.asReversed()
    )
}
