package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.util.ShortVideoRuns
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortVideoRunsTest {
    private var now = 0L
    private val runs = ShortVideoRuns { now }

    @Test
    fun `one swipe is one video, whether or not the pager reports positions, and a swipe back is none`() {
        // the momentum of one swipe reports the old page and then the new one, several times over
        var run = runs.onScrollEvent("app", "pager", fromIndex = 3, forward = true)
        repeat(4) {
            now += 50
            run = runs.onScrollEvent("app", "pager", fromIndex = 4, forward = true)
        }
        assertEquals(1, run.videos)
        now += 10_000
        run = runs.onScrollEvent("app", "pager", fromIndex = 3, forward = false)
        assertEquals(1, run.videos)

        // a pager that reports no positions: the events of one swipe are one video
        runs.reset("app")
        repeat(5) {
            now += 50
            run = runs.onScrollEvent("app", "pager", fromIndex = -1, forward = true)
        }
        assertEquals(1, run.videos)
        now += 10_000
        run = runs.onScrollEvent("app", "pager", fromIndex = -1, forward = true)
        assertEquals(2, run.videos)
    }
}
