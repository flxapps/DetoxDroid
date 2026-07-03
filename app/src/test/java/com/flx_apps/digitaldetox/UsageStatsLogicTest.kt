package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageStatsViewModel
import com.flx_apps.digitaldetox.util.DailyAppCounter
import com.flx_apps.digitaldetox.util.DistancePerspective
import com.flx_apps.digitaldetox.util.ScrollDeltaCalibrator
import com.flx_apps.digitaldetox.util.ScrollDistanceEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollIntensityScoreTest {
    @Test
    fun `zero scrolls or zero time yield zero score`() {
        assertEquals(0f, UsageStatsViewModel.scrollIntensityScore(0, 60_000L))
        assertEquals(0f, UsageStatsViewModel.scrollIntensityScore(10, 0L))
        assertEquals(0f, UsageStatsViewModel.scrollsPerMinute(0, 60_000L))
    }

    @Test
    fun `scrolls per minute is rate over foreground time`() {
        assertEquals(2f, UsageStatsViewModel.scrollsPerMinute(10, 5 * 60_000L))
    }

    @Test
    fun `sub-minute sessions are clamped so they cannot inflate the rate`() {
        // 10 scrolls in 1 second: rate is clamped to 10 scrolls per 0.1 min = 100/min
        assertEquals(100f, UsageStatsViewModel.scrollsPerMinute(10, 1_000L))
    }

    @Test
    fun `sustained scrolling outranks a brief burst`() {
        val burst = UsageStatsViewModel.scrollIntensityScore(50, 60_000L)
        val sustained = UsageStatsViewModel.scrollIntensityScore(1000, 60 * 60_000L)
        assertTrue(sustained > burst)
    }
}

class GroupSessionCountsTest {
    private fun session(pkg: String, startMs: Long, endMs: Long) =
        UsageStatsProvider.SessionInfo(pkg, startMs, endMs, endMs - startMs)

    @Test
    fun `phases within the gap threshold are one session`() {
        val counts = UsageStatsProvider.groupSessionCounts(
            listOf(
                session("a", 0, 1_000),
                session("a", 2_000, 3_000)
            ),
            gapThresholdMs = 5_000
        )
        assertEquals(1, counts["a"])
    }

    @Test
    fun `phases beyond the gap threshold are separate sessions`() {
        val counts = UsageStatsProvider.groupSessionCounts(
            listOf(
                session("a", 0, 1_000),
                session("a", 10_000, 11_000)
            ),
            gapThresholdMs = 5_000
        )
        assertEquals(2, counts["a"])
    }

    @Test
    fun `unsorted input is handled and packages are independent`() {
        val counts = UsageStatsProvider.groupSessionCounts(
            listOf(
                session("a", 10_000, 11_000),
                session("b", 0, 500),
                session("a", 0, 1_000)
            ),
            gapThresholdMs = 5_000
        )
        assertEquals(2, counts["a"])
        assertEquals(1, counts["b"])
    }
}

class DailyAppCounterTest {
    @Test
    fun `increments accumulate per package`() {
        val counter = DailyAppCounter()
        counter.increment("a")
        counter.increment("a")
        counter.increment("b")
        assertEquals(2, counter.countFor("a"))
        assertEquals(1, counter.countFor("b"))
        assertEquals(3, counter.total())
        assertEquals(mapOf("a" to 2, "b" to 1), counter.snapshot())
    }

    @Test
    fun `restore uses max-merge and never loses live increments`() {
        val counter = DailyAppCounter()
        counter.increment("a")
        counter.increment("a")
        counter.increment("a")
        counter.restore(mapOf("a" to 2, "b" to 5))
        // "a" keeps the higher live count, "b" is restored
        assertEquals(3, counter.countFor("a"))
        assertEquals(5, counter.countFor("b"))
    }

    @Test
    fun `weighted increments accumulate fractionally and round on read`() {
        val counter = DailyAppCounter()
        counter.increment("a", 0.4)
        assertEquals(0, counter.countFor("a"))
        counter.increment("a", 0.4)
        counter.increment("a", 0.4)
        // 1.2 accumulated → rounds to 1
        assertEquals(1, counter.countFor("a"))
        counter.increment("a", 4.0)
        assertEquals(5, counter.countFor("a"))
    }
}

class ScrollDeltaCalibratorTest {
    @Test
    fun `unknown delta yields the neutral weight`() {
        val calibrator = ScrollDeltaCalibrator()
        assertEquals(ScrollDeltaCalibrator.NEUTRAL_WEIGHT, calibrator.weightFor("a", 0), 0.0)
        assertEquals(ScrollDeltaCalibrator.NEUTRAL_WEIGHT, calibrator.weightFor("a", -5), 0.0)
    }

    @Test
    fun `first event and constant deltas behave like plain counting`() {
        val calibrator = ScrollDeltaCalibrator()
        assertEquals(1.0, calibrator.weightFor("a", 100), 1e-9)
        repeat(50) {
            assertEquals(1.0, calibrator.weightFor("a", 100), 1e-6)
        }
    }

    @Test
    fun `big flings weigh more, tiny nudges less, both bounded`() {
        val calibrator = ScrollDeltaCalibrator()
        repeat(50) { calibrator.weightFor("a", 100) } // establish an average of ~100
        val flingWeight = calibrator.weightFor("a", 100_000)
        val nudgeWeight = calibrator.weightFor("a", 1)
        assertTrue("fling should weigh more than 1, was $flingWeight", flingWeight > 1.0)
        assertTrue("fling weight must be bounded, was $flingWeight", flingWeight <= 4.0)
        assertTrue("nudge should weigh less than 1, was $nudgeWeight", nudgeWeight < 1.0)
        assertTrue("nudge weight must be bounded, was $nudgeWeight", nudgeWeight >= 0.25)
    }

    @Test
    fun `apps are calibrated independently`() {
        val calibrator = ScrollDeltaCalibrator()
        repeat(50) { calibrator.weightFor("pixels", 500) }
        // an app reporting deltas of 1 is not affected by another app's pixel-scale average
        assertEquals(1.0, calibrator.weightFor("positions", 1), 1e-9)
    }
}

class ScrollDistanceEstimatorTest {
    private var now = 0L
    private fun estimator(screenHeightPx: Int = 2000) =
        ScrollDistanceEstimator(screenHeightPx) { now }

    @Test
    fun `pixel deltas pass through and put the app in pixel mode`() {
        val estimator = estimator()
        assertEquals(500, estimator.distancePxFor("a", 500))
        // once classified as pixel-reporting, small and zero deltas are taken at face value
        assertEquals(5, estimator.distancePxFor("a", 5))
        assertEquals(0, estimator.distancePxFor("a", 0))
    }

    @Test
    fun `tiny deltas are estimated from screen height, rate-limited per gesture window`() {
        val estimator = estimator(screenHeightPx = 2000)
        now = 0
        assertEquals(1200, estimator.distancePxFor("b", 1)) // 0.6 × screen height
        now = 100
        assertEquals(0, estimator.distancePxFor("b", 1)) // same gesture window
        now = 600
        assertEquals(1200, estimator.distancePxFor("b", 0)) // next window
    }

    @Test
    fun `mid-size deltas count at face value without enabling pixel mode`() {
        val estimator = estimator(screenHeightPx = 2000)
        assertEquals(100, estimator.distancePxFor("c", 100))
        // still not pixel mode: tiny deltas keep being estimated
        assertEquals(1200, estimator.distancePxFor("c", 1))
    }

    @Test
    fun `single bogus deltas are capped`() {
        assertEquals(10_000, estimator().distancePxFor("d", 50_000))
    }

    @Test
    fun `apps are classified independently`() {
        val estimator = estimator(screenHeightPx = 2000)
        assertEquals(500, estimator.distancePxFor("pixels", 500))
        assertEquals(1200, estimator.distancePxFor("positions", 1))
    }
}

class DistancePerspectiveTest {
    @Test
    fun `pixels convert to meters via dpi`() {
        // 1 inch = 0.0254 m: 160 px at 160 dpi = 1 inch
        assertEquals(0.0254, DistancePerspective.pixelsToMeters(160, 160f), 1e-6)
        // ~1 m at 160 dpi
        assertEquals(1.0, DistancePerspective.pixelsToMeters(6300, 160f), 0.01)
        assertEquals(0.0, DistancePerspective.pixelsToMeters(1000, 0f), 0.0)
    }

    @Test
    fun `tiny distances have no comparison`() {
        assertNull(DistancePerspective.comparisonFor(0.0))
        assertNull(DistancePerspective.comparisonFor(5.0))
    }

    @Test
    fun `small distances compare to floors`() {
        val comparison = DistancePerspective.comparisonFor(45.0)
        assertTrue(comparison is DistancePerspective.Comparison.Floors)
        assertEquals(15, (comparison as DistancePerspective.Comparison.Floors).floors)
    }

    @Test
    fun `distances pick the largest fitting landmark`() {
        val eiffel = DistancePerspective.comparisonFor(420.0)
        assertTrue(eiffel is DistancePerspective.Comparison.Landmark)
        eiffel as DistancePerspective.Comparison.Landmark
        assertEquals(R.string.landmark_eiffel_tower, eiffel.nameRes)
        assertEquals(420.0 / 324.0, eiffel.multiplier, 1e-9)

        val marathon = DistancePerspective.comparisonFor(100_000.0)
        assertTrue(marathon is DistancePerspective.Comparison.Landmark)
        assertEquals(
            R.string.landmark_marathon,
            (marathon as DistancePerspective.Comparison.Landmark).nameRes
        )
    }
}
