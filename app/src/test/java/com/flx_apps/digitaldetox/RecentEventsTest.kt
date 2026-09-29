package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.util.RecentEvents
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RecentEvents] counts what happened within a window that moves along, and forgets the rest.
 */
class RecentEventsTest {
    private val window = 30 * 60_000L

    @Test
    fun eventsWithinTheWindowAddUp() {
        val events = RecentEvents()
        assertEquals(1, events.record(0L, window))
        assertEquals(2, events.record(10 * 60_000L, window))
        assertEquals(3, events.record(29 * 60_000L, window))
    }

    @Test
    fun anEventThatLeftTheWindowNoLongerCounts() {
        val events = RecentEvents()
        events.record(0L, window)
        events.record(20 * 60_000L, window)
        assertEquals(2, events.record(30 * 60_000L, window))
    }

    @Test
    fun aClockThatWentBackwardsStartsOver() {
        val events = RecentEvents()
        events.record(50 * 60_000L, window)
        assertEquals(1, events.record(60_000L, window))
    }

    @Test
    fun clearingForgetsEverything() {
        val events = RecentEvents()
        events.record(0L, window)
        events.clear()
        assertEquals(1, events.record(1L, window))
    }
}
