package com.flx_apps.digitaldetox

import android.app.KeyguardManager
import android.media.AudioManager
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.AppDisabledOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.WaitBeforeOpeningActivity
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What [DisableAppsFeature] does when a listed app comes to the front: it waits first while there
 * is screen time left (but never over the lock screen or during a call), opens right away after a
 * wait until the screen turns off, and locks once the daily time is used up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisableAppsWaitTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
        DisableAppsFeature.appExceptions = setOf(LISTED_APP)
        DisableAppsFeature.operationMode = DisableAppsMode.BLOCK
        DisableAppsFeature.allowedDailyScreenTime = DisableAppsFeature.NO_DAILY_LIMIT
        DisableAppsFeature.waitBeforeOpening = 10_000L
    }

    @After
    fun tearDown() {
        // the shadow keeps the audio mode in a static field, so it would leak into other tests
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        DisableAppsFeature.onPause(context) // also forgets the apps that were waited for
        DisableAppsFeature.trackingSinceTimestamp = 0L
        DisableAppsFeature.usedUpScreenTime = 0L
        DisableAppsFeature.appExceptions = emptySet()
        DisableAppsFeature.allowedDailyScreenTime = 0L
        DisableAppsFeature.waitBeforeOpening = 0L
    }

    private fun open(packageName: String) =
        DisableAppsFeature.onAppOpened(context, packageName, AccessibilityEventUtil.createEvent())

    private fun waitScreenStarted() = shadowOf(context).nextStartedActivity?.component?.className ==
            WaitBeforeOpeningActivity::class.java.name

    private fun blockScreenStarted() = shadowOf(context).nextStartedService?.component?.className ==
            AppDisabledOverlayService::class.java.name

    @Test
    fun `a listed app waits, and the wait is not screen time`() {
        open(LISTED_APP)
        assertTrue(waitScreenStarted())
        assertEquals(0L, DisableAppsFeature.trackingSinceTimestamp)
    }

    @Test
    fun `apps that are not listed never wait`() {
        open(OTHER_APP)
        assertFalse(waitScreenStarted())
    }

    @Test
    fun `an app that was waited for opens right away until the screen turns off`() {
        DisableAppsFeature.skipWaitUntilScreenOff(LISTED_APP)
        open(OTHER_APP)
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
        assertNotEquals(0L, DisableAppsFeature.trackingSinceTimestamp)

        DisableAppsFeature.onScreenTurnedOff(context)
        open(LISTED_APP)
        assertTrue(waitScreenStarted())
    }

    @Test
    fun `nothing is held back over the lock screen, where the wait screen cannot show`() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
    }

    @Test
    fun `nothing is held back while the phone is in a call`() {
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
    }

    @Test
    fun `the lock takes over from the wait once the daily time is used up`() {
        DisableAppsFeature.allowedDailyScreenTime = 30 * 60_000L
        DisableAppsFeature.usedUpScreenTime = 30 * 60_000L
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
        assertTrue(blockScreenStarted())
    }

    @Test
    fun `a daily limit of zero locks right away`() {
        DisableAppsFeature.allowedDailyScreenTime = 0L
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
        assertTrue(blockScreenStarted())
    }

    @Test
    fun `without a daily limit the apps never lock`() {
        DisableAppsFeature.waitBeforeOpening = 0L
        open(LISTED_APP)
        assertFalse(blockScreenStarted())
        assertNotEquals(0L, DisableAppsFeature.trackingSinceTimestamp)
    }

    private companion object {
        const val LISTED_APP = "com.example.feed"
        const val OTHER_APP = "com.example.maps"
    }
}
