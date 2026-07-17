package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.features.DoomScrollingSensitivity
import com.flx_apps.digitaldetox.util.ScrollSessionMonitor
import com.flx_apps.digitaldetox.util.ScrollSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSessionMonitorTest {
    private val screenHeightPx = 1000
    private var now = 0L
    private val monitor = ScrollSessionMonitor(screenHeightPx) { now }

    /** Advances the clock by [stepMs] and registers one scroll event. */
    private fun scroll(
        px: Int = screenHeightPx,
        stepMs: Long = 6_000,
        down: Boolean = true,
        pkg: String = "app"
    ): ScrollSessionSnapshot {
        now += stepMs
        return monitor.onScrollEvent(pkg, px, down)
    }

    @Test
    fun `velocity reflects the distance scrolled in the window`() {
        // 10 events of one screen height each, 6 s apart → all inside the 60 s window
        var snapshot: ScrollSessionSnapshot? = null
        repeat(10) { snapshot = scroll() }
        assertEquals(10f, snapshot!!.screenHeightsPerMinute, 0.01f)
    }

    @Test
    fun `session duration grows while scrolling continues`() {
        scroll()
        var snapshot: ScrollSessionSnapshot? = null
        repeat(10) { snapshot = scroll() }
        assertEquals(60_000L, snapshot!!.sessionDurationMs)
    }

    @Test
    fun `samples leaving the window no longer count toward velocity`() {
        repeat(10) { scroll() }
        // over a minute of further activity without distance (e.g. rate-limited estimates)
        var snapshot: ScrollSessionSnapshot? = null
        repeat(11) { snapshot = scroll(px = 0) }
        assertEquals(0f, snapshot!!.screenHeightsPerMinute, 0.01f)
        // ...but the session totals keep the full distance
        assertEquals(10f, snapshot!!.sessionScreenHeights, 0.01f)
        assertEquals(120_000L, snapshot!!.sessionDurationMs)
    }

    @Test
    fun `down ratio is the share of downward events in the window`() {
        repeat(8) { scroll(down = true) }
        val snapshot = (1..2).map { scroll(down = false) }.last()
        assertEquals(0.8f, snapshot.downScrollRatio, 0.01f)
    }

    @Test
    fun `a long pause starts a fresh session`() {
        repeat(5) { scroll() }
        val snapshot = scroll(stepMs = ScrollSessionMonitor.SESSION_GAP_MS + 1)
        assertEquals(0L, snapshot.sessionDurationMs)
        assertEquals(1f, snapshot.sessionScreenHeights, 0.01f)
    }

    @Test
    fun `reset forgets the app's session`() {
        repeat(5) { scroll() }
        monitor.reset("app")
        val snapshot = scroll()
        assertEquals(0L, snapshot.sessionDurationMs)
        assertEquals(1f, snapshot.sessionScreenHeights, 0.01f)
    }

    @Test
    fun `sessions are tracked per app`() {
        repeat(5) { scroll(pkg = "a") }
        val snapshot = scroll(pkg = "b")
        assertEquals(0L, snapshot.sessionDurationMs)
        assertEquals(1f, snapshot.sessionScreenHeights, 0.01f)
    }
}

class DoomScrollingSensitivityTest {
    private fun snapshot(
        durationMs: Long = 5 * 60_000L,
        screensPerMinute: Float = 30f,
        downRatio: Float = 1f
    ) = ScrollSessionSnapshot(
        sessionDurationMs = durationMs,
        screenHeightsPerMinute = screensPerMinute,
        downScrollRatio = downRatio,
        sessionScreenHeights = 100f
    )

    @Test
    fun `sustained fast downward scrolling is doom scrolling`() {
        assertTrue(DoomScrollingSensitivity.BALANCED.isDoomScrolling(snapshot(), 3 * 60_000L))
    }

    @Test
    fun `young sessions never trigger regardless of speed`() {
        assertFalse(
            DoomScrollingSensitivity.STRICT.isDoomScrolling(
                snapshot(durationMs = 60_000L), 3 * 60_000L
            )
        )
    }

    @Test
    fun `reading pace stays below every sensitivity`() {
        val reading = snapshot(screensPerMinute = 3f)
        DoomScrollingSensitivity.entries.forEach {
            assertFalse(it.isDoomScrolling(reading, 3 * 60_000L))
        }
    }

    @Test
    fun `scrolling back and forth is not doom scrolling`() {
        // e.g. navigating around a document: fast, but not predominantly downward
        assertFalse(
            DoomScrollingSensitivity.BALANCED.isDoomScrolling(snapshot(downRatio = 0.6f), 0L)
        )
    }

    @Test
    fun `stricter sensitivities trigger at slower scrolling`() {
        val moderate = snapshot(screensPerMinute = 7f, downRatio = 0.78f)
        assertTrue(DoomScrollingSensitivity.STRICT.isDoomScrolling(moderate, 0L))
        assertFalse(DoomScrollingSensitivity.BALANCED.isDoomScrolling(moderate, 0L))
        assertFalse(DoomScrollingSensitivity.RELAXED.isDoomScrolling(moderate, 0L))
    }
}
