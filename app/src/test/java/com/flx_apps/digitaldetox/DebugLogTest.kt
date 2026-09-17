package com.flx_apps.digitaldetox

import android.util.Log
import com.flx_apps.digitaldetox.ui.screens.logs.LogFilter
import com.flx_apps.digitaldetox.ui.screens.logs.LogLevelFilter
import com.flx_apps.digitaldetox.ui.screens.logs.buildLogViewerState
import com.flx_apps.digitaldetox.util.LogEntry
import com.flx_apps.digitaldetox.util.LogLineFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the debug log survives in its file, and how the log viewer filters it and splits it into
 * runs of the app.
 */
class DebugLogTest {
    private var nextId = 0L

    private fun entry(pid: Int, priority: Int, tag: String?, message: String, timestamp: Long = 0) =
        LogEntry(++nextId, timestamp, priority, pid, tag, message)

    @Test
    fun `an entry with line breaks, pipes and backslashes reads back the same`() {
        val original = entry(
            pid = 4711,
            priority = Log.ERROR,
            tag = "Some|Tag",
            message = "Crash | here\njava.lang.IllegalStateException: C:\\no\\n\r\n\tat Foo.bar(Foo.kt:1)\\"
        )
        val line = LogLineFormat.encode(original)
        assertFalse(line.contains('\n'))
        assertEquals(
            original.copy(tag = "Some/Tag"), LogLineFormat.decode(line, id = original.id)
        )
    }

    @Test
    fun `an entry without a tag keeps having none`() {
        val original = entry(pid = 1, priority = Log.DEBUG, tag = null, message = "")
        assertEquals(original, LogLineFormat.decode(LogLineFormat.encode(original), original.id))
    }

    @Test
    fun `a line in another format is skipped`() {
        assertNull(LogLineFormat.decode("1700000000000|3|Tag|message from the old format", 1))
        assertNull(LogLineFormat.decode("\tat Foo.bar(Foo.kt:1)", 1))
    }

    @Test
    fun `entries are grouped by run, newest run and newest entry first`() {
        val entries = listOf(
            entry(pid = 1, priority = Log.INFO, tag = "A", message = "one", timestamp = 10),
            entry(pid = 1, priority = Log.DEBUG, tag = "B", message = "two", timestamp = 20),
            entry(pid = 2, priority = Log.WARN, tag = "A", message = "three", timestamp = 30),
            entry(pid = 2, priority = Log.ERROR, tag = "A", message = "four", timestamp = 40),
        )
        val state = buildLogViewerState(entries, LogFilter(), currentPid = 2)
        assertEquals(listOf(2, 1), state.runs.map { it.pid })
        assertEquals(listOf("four", "three"), state.runs[0].entries.map { it.message })
        assertTrue(state.runs[0].isCurrent)
        assertFalse(state.runs[1].isCurrent)
        assertEquals(10L to 20L, state.runs[1].startedAt to state.runs[1].endedAt)
        assertEquals(entries.map { it.message }, state.shownEntries.map { it.message })
    }

    @Test
    fun `a level shows that level and above, and each level counts what it would show`() {
        val entries = listOf(
            entry(pid = 1, priority = Log.DEBUG, tag = "A", message = "debug"),
            entry(pid = 1, priority = Log.INFO, tag = "A", message = "info"),
            entry(pid = 1, priority = Log.WARN, tag = "A", message = "warn"),
            entry(pid = 1, priority = Log.ERROR, tag = "A", message = "error"),
        )
        val state = buildLogViewerState(entries, LogFilter(level = LogLevelFilter.WARNINGS), 1)
        assertEquals(listOf("error", "warn"), state.runs.single().entries.map { it.message })
        assertEquals(2, state.shown)
        assertEquals(4, state.total)
        assertEquals(
            mapOf(
                LogLevelFilter.ALL to 4,
                LogLevelFilter.INFO to 3,
                LogLevelFilter.WARNINGS to 2,
                LogLevelFilter.ERRORS to 1
            ),
            state.levelCounts
        )
    }

    @Test
    fun `tag and search narrow the entries and the counts, and a run left empty is dropped`() {
        val entries = listOf(
            entry(pid = 1, priority = Log.INFO, tag = "Other", message = "Blocked com.example"),
            entry(pid = 2, priority = Log.INFO, tag = "DisableApps", message = "Blocked COM.EXAMPLE"),
            entry(pid = 2, priority = Log.ERROR, tag = "DisableApps", message = "unrelated"),
        )
        val byTag = buildLogViewerState(entries, LogFilter(tag = "DisableApps"), 2)
        assertEquals(listOf(2), byTag.runs.map { it.pid })
        assertEquals(2, byTag.levelCounts[LogLevelFilter.ALL])

        val bySearch = buildLogViewerState(entries, LogFilter(query = " com.example "), 2)
        assertEquals(listOf(2, 1), bySearch.runs.map { it.pid })
        assertEquals(2, bySearch.shown)
        assertEquals(0, bySearch.levelCounts[LogLevelFilter.ERRORS])

        val searchingTags = buildLogViewerState(entries, LogFilter(query = "disable"), 2)
        assertEquals(2, searchingTags.shown)
    }
}
