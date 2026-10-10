package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.data.ConfigBackup
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureScheduleRule
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DoNotDisturbFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.util.AccessibilityEventUtil
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
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * A brake runs for the time it was pulled for, on a clock the date settings cannot move, and ends
 * early only after a wait. While it runs, the features it covers act as if their schedule were
 * open and their budget spent, and none of it touches what the user has set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyBrakeTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val realClocks =
        Triple(TrustedClock.wallMs, TrustedClock.elapsedMs, TrustedClock.bootCount)

    private var wall = 1_800_000_000_000L
    private var elapsed = 60_000L

    private val halfAnHour = TimeUnit.MINUTES.toMillis(30)
    private val listed = "com.example.feed"
    private val unlisted = "com.example.maps"

    @Before
    fun setUp() {
        DetoxDroidApplication.appContext = context
        TrustedClock.wallMs = { wall }
        TrustedClock.elapsedMs = { elapsed }
        TrustedClock.bootCount = { 1 }
        TrustedClock.sync()
        EmergencyBrakeFeature.isActivated = true
        GrayscaleAppsFeature.appExceptionListType = AppExceptionListType.ONLY_LIST
        GrayscaleAppsFeature.appExceptions = setOf(listed)
    }

    @After
    fun tearDown() {
        // whatever brake a test left running has run out a day later
        pass(TimeUnit.DAYS.toMillis(1))
        EmergencyBrakeFeature.settle(context)
        EmergencyBrakeFeature.isActivated = false
        EmergencyBrakeFeature.autoPull = false
        EmergencyBrakeFeature.lastDuration = halfAnHour
        // the singletons outlive the test class, so a filter left on would reach the next one
        GrayscaleAppsFeature.onPause(context)
        GrayscaleAppsFeature.isActivated = false
        GrayscaleAppsFeature.scheduleRules = emptySet()
        GrayscaleAppsFeature.appExceptionListType = AppExceptionListType.NOT_LIST
        GrayscaleAppsFeature.appExceptions = emptySet()
        DisableAppsFeature.allowedDailyScreenTime = 0L
        PauseButtonFeature.isActivated = false
        TrustedClock.wallMs = realClocks.first
        TrustedClock.elapsedMs = realClocks.second
        TrustedClock.bootCount = realClocks.third
    }

    private fun pass(ms: Long) {
        wall += ms
        elapsed += ms
    }

    @Test
    fun aBrakeRunsForItsDurationAndThenEnds() {
        assertTrue(EmergencyBrakeFeature.pull(context, halfAnHour))
        assertTrue(EmergencyBrakeFeature.isBraking())
        assertEquals(halfAnHour, EmergencyBrakeFeature.remainingMs())

        pass(halfAnHour - 1)
        assertTrue(EmergencyBrakeFeature.isBraking())
        pass(1)
        assertFalse(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun itCannotBePulledWhileSwitchedOff() {
        EmergencyBrakeFeature.isActivated = false
        assertFalse(EmergencyBrakeFeature.pull(context, halfAnHour))
        assertFalse(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun settingTheDateForwardDoesNotEndIt() {
        EmergencyBrakeFeature.pull(context, halfAnHour)
        wall += TimeUnit.DAYS.toMillis(2)
        TrustedClock.sync()
        assertTrue(EmergencyBrakeFeature.isBraking())
        assertEquals(halfAnHour, EmergencyBrakeFeature.remainingMs())
    }

    @Test
    fun pullingAgainExtendsButNeverShortens() {
        EmergencyBrakeFeature.pull(context, halfAnHour)
        pass(TimeUnit.MINUTES.toMillis(10))

        assertFalse(EmergencyBrakeFeature.pull(context, TimeUnit.MINUTES.toMillis(15)))
        assertEquals(TimeUnit.MINUTES.toMillis(20), EmergencyBrakeFeature.remainingMs())

        assertTrue(EmergencyBrakeFeature.pull(context, TimeUnit.HOURS.toMillis(1)))
        assertEquals(TimeUnit.HOURS.toMillis(1), EmergencyBrakeFeature.remainingMs())
    }

    @Test
    fun endingEarlyTakesTheWait() {
        EmergencyBrakeFeature.pull(context, TimeUnit.HOURS.toMillis(2))
        EmergencyBrakeFeature.requestEnd(context)
        assertTrue(EmergencyBrakeFeature.isEndRequested())
        assertEquals(EmergencyBrakeFeature.EndWaitMs, EmergencyBrakeFeature.remainingMs())

        pass(EmergencyBrakeFeature.EndWaitMs - 1)
        assertTrue(EmergencyBrakeFeature.isBraking())
        pass(1)
        assertFalse(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun askingToEndABrakeThatIsNearlyOverDoesNotProlongIt() {
        EmergencyBrakeFeature.pull(context, TimeUnit.MINUTES.toMillis(15))
        pass(TimeUnit.MINUTES.toMillis(12))
        EmergencyBrakeFeature.requestEnd(context)
        assertEquals(TimeUnit.MINUTES.toMillis(3), EmergencyBrakeFeature.remainingMs())
    }

    @Test
    fun aWithdrawnRequestLetsTheBrakeRunItsFullTime() {
        EmergencyBrakeFeature.pull(context, TimeUnit.HOURS.toMillis(2))
        EmergencyBrakeFeature.requestEnd(context)
        pass(TimeUnit.MINUTES.toMillis(5))
        EmergencyBrakeFeature.withdrawEndRequest(context)

        assertFalse(EmergencyBrakeFeature.isEndRequested())
        pass(EmergencyBrakeFeature.EndWaitMs)
        assertTrue(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun itCanBeTakenBackRightAwayAndNotLater() {
        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertTrue(EmergencyBrakeFeature.canUndo())
        assertTrue(EmergencyBrakeFeature.undo(context))
        assertFalse(EmergencyBrakeFeature.isBraking())

        EmergencyBrakeFeature.pull(context, halfAnHour)
        pass(EmergencyBrakeFeature.UndoWindowMs)
        assertFalse(EmergencyBrakeFeature.undo(context))
        assertTrue(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun extendingDoesNotReopenTheUndo() {
        EmergencyBrakeFeature.pull(context, halfAnHour)
        pass(EmergencyBrakeFeature.UndoWindowMs)
        EmergencyBrakeFeature.pull(context, TimeUnit.HOURS.toMillis(1))
        assertFalse(EmergencyBrakeFeature.canUndo())
    }

    @Test
    fun itOpensAClosedSchedule() {
        GrayscaleAppsFeature.isActivated = true
        val now = LocalDateTime.now()
        // a window that closed an hour ago
        GrayscaleAppsFeature.scheduleRules = setOf(
            FeatureScheduleRule(
                emptyList(),
                now.minusHours(3).toLocalTime().withSecond(0).withNano(0),
                now.minusHours(1).toLocalTime().withSecond(0).withNano(0)
            )
        )
        assertFalse(GrayscaleAppsFeature.isActive(now))

        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertTrue(GrayscaleAppsFeature.isActive(now))
    }

    @Test
    fun aFeatureThatIsOffStaysOff() {
        GrayscaleAppsFeature.isActivated = false
        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertFalse(GrayscaleAppsFeature.isActive())
    }

    @Test
    fun grayscaleTakesTheWholeScreenAndGivesTheListBack() {
        assertFalse(GrayscaleAppsFeature.appliesTo(unlisted))

        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertTrue(GrayscaleAppsFeature.appliesTo(listed))
        assertTrue(GrayscaleAppsFeature.appliesTo(unlisted))

        pass(halfAnHour)
        assertFalse(GrayscaleAppsFeature.appliesTo(unlisted))
        assertTrue(GrayscaleAppsFeature.appliesTo(listed))
        assertEquals(setOf(listed), GrayscaleAppsFeature.appExceptions)
        assertEquals(AppExceptionListType.ONLY_LIST, GrayscaleAppsFeature.appExceptionListType)
    }

    @Test
    fun theBudgetCountsAsSpent() {
        DisableAppsFeature.allowedDailyScreenTime = TimeUnit.HOURS.toMillis(1)
        assertTrue(DisableAppsFeature.hasScreenTimeLeft())

        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertFalse(DisableAppsFeature.hasScreenTimeLeft())

        pass(halfAnHour)
        assertTrue(DisableAppsFeature.hasScreenTimeLeft())
    }

    @Test
    fun itCoversEveryPausableFeatureAndNothingElse() {
        EmergencyBrakeFeature.pull(context, halfAnHour)

        assertTrue(EmergencyBrakeFeature.covers(DoNotDisturbFeature))
        assertTrue(EmergencyBrakeFeature.covers(BreakDoomScrollingFeature))
        assertFalse(EmergencyBrakeFeature.covers(CommitmentPasswordFeature))
        assertFalse(EmergencyBrakeFeature.covers(EmergencyBrakeFeature))
    }

    @Test
    fun extendingAddsToTheEndShownAndTakesBackAnEndRequest() {
        EmergencyBrakeFeature.pull(context, TimeUnit.HOURS.toMillis(2))
        assertTrue(EmergencyBrakeFeature.extend(context, halfAnHour))
        assertEquals(TimeUnit.MINUTES.toMillis(150), EmergencyBrakeFeature.remainingMs())

        EmergencyBrakeFeature.requestEnd(context)
        assertEquals(EmergencyBrakeFeature.EndWaitMs, EmergencyBrakeFeature.remainingMs())
        EmergencyBrakeFeature.extend(context, halfAnHour)
        assertFalse(EmergencyBrakeFeature.isEndRequested())
        assertEquals(EmergencyBrakeFeature.EndWaitMs + halfAnHour, EmergencyBrakeFeature.remainingMs())

        assertFalse(EmergencyBrakeFeature.extend(context, 0L))
    }

    @Test
    fun anAppThatIsGrayOnlyBecauseOfTheBrakeGetsTheSoftenedFilter() {
        GrayscaleAppsFeature.isActivated = true
        EmergencyBrakeFeature.pull(context, halfAnHour)

        GrayscaleAppsFeature.onAppOpened(context, unlisted, AccessibilityEventUtil.createEvent())
        assertTrue(GrayscaleAppsFeature.isScreenFilterSoftened)
        GrayscaleAppsFeature.onAppOpened(context, listed, AccessibilityEventUtil.createEvent())
        assertFalse(GrayscaleAppsFeature.isScreenFilterSoftened)

        pass(halfAnHour)
        GrayscaleAppsFeature.onAppOpened(context, listed, AccessibilityEventUtil.createEvent())
        assertFalse(GrayscaleAppsFeature.isScreenFilterSoftened)
    }

    @Test
    fun itLocksTheSettingsButNotTheCommitmentPassword() {
        assertFalse(EmergencyBrakeFeature.locksSettings(GrayscaleAppsFeature.id))

        EmergencyBrakeFeature.pull(context, halfAnHour)
        assertTrue(EmergencyBrakeFeature.locksSettings(GrayscaleAppsFeature.id))
        assertTrue(EmergencyBrakeFeature.locksSettings(PauseButtonFeature.id))
        assertTrue(EmergencyBrakeFeature.locksSettings(EmergencyBrakeFeature.id))
        assertFalse(EmergencyBrakeFeature.locksSettings(CommitmentPasswordFeature.id))
    }

    @Test
    fun aPauseIsRefusedWhileItRuns() {
        PauseButtonFeature.isActivated = true
        EmergencyBrakeFeature.pull(context, halfAnHour)

        PauseButtonFeature.togglePause(context)
        assertFalse(PauseButtonFeature.isPausing())
    }

    @Test
    fun itPullsItselfAfterOneBreakTooMany() {
        EmergencyBrakeFeature.autoPull = true
        EmergencyBrakeFeature.autoPullBreaks = 3

        assertFalse(EmergencyBrakeFeature.onDoomScrollingBreak(context))
        assertFalse(EmergencyBrakeFeature.onDoomScrollingBreak(context))
        assertTrue(EmergencyBrakeFeature.onDoomScrollingBreak(context))

        assertTrue(EmergencyBrakeFeature.isBraking())
        assertEquals(EmergencyBrakeFeature.autoPullDuration, EmergencyBrakeFeature.remainingMs())
        // nobody tapped anything by accident
        assertFalse(EmergencyBrakeFeature.canUndo())
    }

    @Test
    fun breaksDuringABrakeDoNotStackUp() {
        EmergencyBrakeFeature.autoPull = true
        EmergencyBrakeFeature.autoPullBreaks = 2
        EmergencyBrakeFeature.pull(context, halfAnHour)

        repeat(5) { assertFalse(EmergencyBrakeFeature.onDoomScrollingBreak(context)) }
        assertEquals(halfAnHour, EmergencyBrakeFeature.remainingMs())

        // and the first break after it starts a new count
        pass(halfAnHour)
        EmergencyBrakeFeature.settle(context)
        assertFalse(EmergencyBrakeFeature.onDoomScrollingBreak(context))
    }

    @Test
    fun withoutTheOptInItNeverPullsItself() {
        repeat(10) { assertFalse(EmergencyBrakeFeature.onDoomScrollingBreak(context)) }
        assertFalse(EmergencyBrakeFeature.isBraking())
    }

    @Test
    fun aPulledAutomaticBrakeLeavesTheTilesDurationAlone() {
        EmergencyBrakeFeature.lastDuration = TimeUnit.HOURS.toMillis(2)
        EmergencyBrakeFeature.pull(context, halfAnHour, automatic = true)
        assertEquals(TimeUnit.HOURS.toMillis(2), EmergencyBrakeFeature.lastDuration)
    }

    @Test
    fun aRunningBrakeIsNotInABackup() {
        EmergencyBrakeFeature.runningStateKeys.forEach { assertFalse(ConfigBackup.isBackedUp(it)) }
        assertTrue(ConfigBackup.isBackedUp("EmergencyBrakeFeature_autoPull"))
    }
}
