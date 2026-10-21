package com.flx_apps.digitaldetox

import android.app.KeyguardManager
import android.media.AudioManager
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.rules.GroupBudgets
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.AppDisabledOverlayService
import com.flx_apps.digitaldetox.ui.screens.feature.disable_apps.WaitBeforeOpeningActivity
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
import java.time.LocalDate
import java.time.ZoneId
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
 * What [DisableAppsFeature] does when an app of a group comes to the front: it waits first while
 * there is budget left (but never over the lock screen or during a call), opens right away after a
 * wait until the screen turns off, and locks once the group's budget is used up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisableAppsWaitTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var group: RuleGroup

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = RuntimeEnvironment.getApplication()
        group = RuleGroups.create {
            name = "Feeds"
            appExceptions = setOf(LISTED_APP)
            waitBeforeOpeningMs = 10_000L
            locks = false
        }
        DisableAppsFeature.operationMode = DisableAppsMode.BLOCK
    }

    @After
    fun tearDown() {
        // the shadow keeps the audio mode in a static field, so it would leak into other tests
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        DisableAppsFeature.onPause(context) // also forgets the apps that were waited for
        GroupBudgets.stopAll()
        RuleGroups.delete(group.id)
    }

    private fun open(packageName: String) =
        DisableAppsFeature.onAppOpened(context, packageName, AccessibilityEventUtil.createEvent())

    private fun waitScreenStarted() = shadowOf(context).nextStartedActivity?.component?.className ==
            WaitBeforeOpeningActivity::class.java.name

    private fun blockScreenStarted() = shadowOf(context).nextStartedService?.component?.className ==
            AppDisabledOverlayService::class.java.name

    /** Books [minutes] of use of the group's apps, ending a minute ago. */
    /** A moment today that is a budget window away from no midnight, whenever the test runs. */
    private fun noonToday(): Long =
        LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun useUp(minutes: Int) {
        val endMs = noonToday() - 60_000L
        GroupBudgets.onForeground(listOf(group), nowMs = endMs - minutes * 60_000L)
        GroupBudgets.onForeground(emptyList(), nowMs = endMs)
    }

    @Test
    fun `a listed app waits`() {
        open(LISTED_APP)
        assertTrue(waitScreenStarted())
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
    fun `the lock takes over from the wait once the budget is used up`() {
        group.budgetMs = 30 * 60_000L
        group.locks = true
        useUp(30)
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
        assertTrue(blockScreenStarted())
    }

    @Test
    fun `a lock without a budget locks right away`() {
        group.locks = true
        open(LISTED_APP)
        assertFalse(waitScreenStarted())
        assertTrue(blockScreenStarted())
    }

    @Test
    fun `time in a listed app stops counting when nothing is in front any more`() {
        group.budgetMs = 30 * 60_000L
        val startMs = noonToday() - 10 * 60_000L
        GroupBudgets.onForeground(listOf(group), nowMs = startMs)
        // ten minutes in the app, then the screen goes off while it is still open
        GroupBudgets.stopAll(nowMs = startMs + 10 * 60_000L)
        assertEquals(10 * 60_000L, GroupBudgets.usedMs(group, nowMs = startMs + 15 * 60_000L))
    }

    @Test
    fun `a group that only waits never locks`() {
        group.waitBeforeOpeningMs = 0L
        open(LISTED_APP)
        assertFalse(blockScreenStarted())
    }

    @Test
    fun `a group of all apps but a few cannot hold any back`() {
        group.appExceptionListType = com.flx_apps.digitaldetox.feature_types.AppExceptionListType.NOT_LIST
        group.locks = true
        open(OTHER_APP)
        assertFalse(waitScreenStarted())
        assertFalse(blockScreenStarted())
    }

    private companion object {
        const val LISTED_APP = "com.example.feed"
        const val OTHER_APP = "com.example.maps"
    }
}
