package com.flx_apps.digitaldetox

import android.content.Intent
import android.os.Looper
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.util.TrustedClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [TrustedClock] follows the time that really passes: the time the phone spends off counts, while
 * setting the date forward moves it nowhere, before a reboot or after one. So the commitment
 * password's recovery wait can't be skipped from the date settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustedClockTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val realClocks = Triple(TrustedClock.wallMs, TrustedClock.elapsedMs, TrustedClock.bootCount)

    private var wall = 1_800_000_000_000L
    private var elapsed = 60_000L
    private var boot = 1

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = context
        TrustedClock.wallMs = { wall }
        TrustedClock.elapsedMs = { elapsed }
        TrustedClock.bootCount = { boot }
        TrustedClock.sync()
    }

    @After
    fun tearDown() {
        CommitmentPasswordFeature.recoveryInitiatedAt = 0L
        TrustedClock.wallMs = realClocks.first
        TrustedClock.elapsedMs = realClocks.second
        TrustedClock.bootCount = realClocks.third
    }

    private fun pass(ms: Long) {
        wall += ms
        elapsed += ms
    }

    // the way the date settings do it: the wall clock jumps, and the system tells the receivers
    private fun setDateForward(ms: Long) {
        wall += ms
        context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun reboot(offMs: Long) {
        boot++
        wall += offMs
        elapsed = 0L
    }

    @Test
    fun `time that passes counts`() {
        val start = TrustedClock.now()
        pass(HOUR)
        assertEquals(HOUR, TrustedClock.now() - start)
    }

    @Test
    fun `setting the date forward counts for nothing`() {
        val start = TrustedClock.now()
        setDateForward(DAY)
        pass(MINUTE)
        assertEquals(MINUTE, TrustedClock.now() - start)
    }

    @Test
    fun `setting the date forward still counts for nothing after a reboot`() {
        val start = TrustedClock.now()
        setDateForward(DAY)
        reboot(offMs = HOUR)
        assertEquals(HOUR, TrustedClock.now() - start)
    }

    @Test
    fun `the time the phone was off counts`() {
        val start = TrustedClock.now()
        pass(MINUTE)
        reboot(offMs = HOUR)
        pass(MINUTE)
        assertEquals(MINUTE + HOUR + MINUTE, TrustedClock.now() - start)
    }

    @Test
    fun `a phone that lost its clock carries on from the last time it knew`() {
        val start = TrustedClock.now()
        pass(MINUTE)
        TrustedClock.sync()
        reboot(offMs = 0L)
        wall = 0L
        assertEquals(MINUTE, TrustedClock.now() - start)
    }

    @Test
    fun `the recovery wait does not end when the date is set forward`() {
        CommitmentPasswordFeature.recoveryInitiatedAt = TrustedClock.sync()
        setDateForward(2 * DAY)
        reboot(offMs = 0L)
        assertFalse(CommitmentPasswordFeature.isRecoveryReady())
        pass(CommitmentPasswordFeature.RECOVERY_DURATION_MS)
        assertTrue(CommitmentPasswordFeature.isRecoveryReady())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
