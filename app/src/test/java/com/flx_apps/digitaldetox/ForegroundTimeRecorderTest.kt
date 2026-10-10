package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.util.ForegroundTimeRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The fallback measurement is only worth having if it cannot invent time. The ways it could are a
 * session that outlives the day it started in, a clock that moved while nothing was being measured,
 * and a restart that either loses the morning or counts it twice.
 */
class ForegroundTimeRecorderTest {
    private val day = LocalDate.of(2026, 9, 21)
    private val nextDay = day.plusDays(1)

    private var clockMs = 0L
    private val recorder = ForegroundTimeRecorder { clockMs }

    private fun at(ms: Long) {
        clockMs = ms
    }

    @Test
    fun timeIsCreditedToTheAppThatWasInFront() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(60_000)
        recorder.onForegroundApp("com.spotify.music", today = day)
        at(90_000)
        recorder.closeSession(today = day)

        val times = recorder.snapshot(today = day)
        assertEquals(60_000L, times["com.instagram.android"]?.foregroundMs)
        assertEquals(30_000L, times["com.spotify.music"]?.foregroundMs)
        assertEquals(1, times["com.spotify.music"]?.launchCount)
    }

    @Test
    fun theRunningSessionCountsWhileItIsStillRunning() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(45_000)
        assertEquals(
            45_000L, recorder.snapshot(today = day)["com.instagram.android"]?.foregroundMs
        )
        // and is not counted a second time when it ends
        recorder.closeSession(today = day)
        at(60_000)
        assertEquals(
            45_000L, recorder.snapshot(today = day)["com.instagram.android"]?.foregroundMs
        )
    }

    @Test
    fun aSessionThatSpansMidnightIsNotCreditedToEitherDay() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(3_600_000)
        recorder.closeSession(today = nextDay)
        assertEquals(emptyMap<String, ForegroundTimeRecorder.AppTime>(), recorder.snapshot(nextDay))
    }

    @Test
    fun theSameAppAnnouncedAgainIsNotANewLaunch() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(30_000)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(60_000)
        recorder.closeSession(today = day)
        val time = recorder.snapshot(today = day)["com.instagram.android"]
        assertEquals(1, time?.launchCount)
        assertEquals(60_000L, time?.foregroundMs)
    }

    @Test
    fun aSessionNoticedAfterMidnightCountsFromThereOn() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        // the first look at the recorder on the new day comes from a snapshot, not from the
        // session ending
        at(3_600_000)
        recorder.snapshot(today = nextDay)
        at(3_900_000)
        recorder.closeSession(today = nextDay)
        assertEquals(
            300_000L, recorder.snapshot(today = nextDay)["com.instagram.android"]?.foregroundMs
        )
    }

    @Test
    fun theLiveValuesAreCompleteOnceRestoredOrOnceTheDayStartedHere() {
        assertFalse(recorder.isComplete(today = day))
        recorder.restore(emptyMap(), today = day)
        assertTrue(recorder.isComplete(today = day))
        // the next day began in this process, so nothing of it could be persisted elsewhere
        assertTrue(recorder.isComplete(today = nextDay))
    }

    @Test
    fun yesterdaysTimeIsGoneWhenTheDayRollsOver() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(60_000)
        recorder.closeSession(today = day)
        assertNull(recorder.snapshot(today = nextDay)["com.instagram.android"])
    }

    @Test
    fun aStretchLongerThanAnyRealSessionIsNotCredited() {
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        // the screen turning off ends a session, so five hours in one app is a clock that moved
        // without us rather than five hours of scrolling
        at(5L * 60 * 60 * 1000)
        recorder.closeSession(today = day)
        assertEquals(0L, recorder.snapshot(day)["com.instagram.android"]?.foregroundMs)
    }

    @Test
    fun restoringAfterARestartNeitherLosesNorDoublesTheDay() {
        val persisted = mapOf(
            "com.instagram.android" to ForegroundTimeRecorder.AppTime(600_000L, 4)
        )
        recorder.restore(persisted, today = day)
        at(0)
        recorder.onForegroundApp("com.instagram.android", today = day)
        at(60_000)
        recorder.closeSession(today = day)

        val times = recorder.snapshot(today = day)
        assertEquals(660_000L, times["com.instagram.android"]?.foregroundMs)
        assertEquals(5, times["com.instagram.android"]?.launchCount)

        // a second restore of the same persisted values adds nothing on top
        recorder.restore(persisted, today = day)
        assertEquals(
            660_000L, recorder.snapshot(today = day)["com.instagram.android"]?.foregroundMs
        )
    }
}
