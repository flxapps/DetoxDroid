package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.system_integration.ForegroundAppMonitor
import com.flx_apps.digitaldetox.system_integration.ForegroundDecision
import com.flx_apps.digitaldetox.system_integration.WindowSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The foreground decision is what every feature acts on, and the bugs it has to absorb are all
 * about timing: a share sheet or an app-switch animation makes two packages alternate for a few
 * hundred milliseconds, and acting on each one in turn is what makes the screen filter flicker.
 */
class ForegroundAppMonitorTest {
    private val own = "com.flx_apps.digitaldetox"

    private fun window(
        packageName: String, className: String = "$packageName.MainActivity",
        isFullScreen: Boolean = true
    ) = WindowSignal(packageName, className, isFullScreen)

    private fun monitor() = ForegroundAppMonitor(own, settleWindowMs = 300L)

    @Test
    fun theFirstAppAndOneOpenedOutOfQuietAreTakenAtFaceValue() {
        val monitor = monitor()
        assertEquals(
            ForegroundDecision.Changed("com.instagram.android", true),
            monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        )
        assertEquals(
            ForegroundDecision.Changed("com.spotify.music", true),
            monitor.onWindow(window("com.spotify.music"), nowMs = 9_000)
        )
        assertEquals("com.spotify.music", monitor.currentPackage)
    }

    @Test
    fun aSwitchOnTheHeelsOfTheLastOneWaitsOutTheRestOfTheWindow() {
        val monitor = monitor()
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        val decision = monitor.onWindow(window("com.spotify.music"), nowMs = 1_100)
        assertEquals(ForegroundDecision.Settling("com.spotify.music", 200), decision)
        // nothing is published until the window has run out
        assertEquals("com.instagram.android", monitor.currentPackage)
        assertEquals(
            ForegroundDecision.Changed("com.spotify.music", true), monitor.settle(nowMs = 1_300)
        )
    }

    @Test
    fun comingStraightBackCancelsTheSwitchThatWasStillSettling() {
        val monitor = monitor()
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        assertTrue(
            monitor.onWindow(window("com.spotify.music"), nowMs = 1_100)
                    is ForegroundDecision.Settling
        )
        assertEquals(
            ForegroundDecision.Cancelled,
            monitor.onWindow(window("com.instagram.android"), nowMs = 1_150)
        )
        assertEquals(null, monitor.settle(nowMs = 1_400))
        assertEquals("com.instagram.android", monitor.currentPackage)
    }

    @Test
    fun twoPackagesAlternatingProduceOneSwitchAtMost() {
        val monitor = monitor()
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        val published = mutableListOf<String>()
        // the share sheet pattern: the sheet's own package and the app underneath trade places
        var at = 1_050L
        repeat(8) {
            val signal =
                if (it % 2 == 0) window("com.google.android.apps.photos") else window("com.instagram.android")
            when (val decision = monitor.onWindow(signal, at)) {
                is ForegroundDecision.Changed -> published += decision.packageName
                else -> {}
            }
            at += 50
        }
        assertTrue(published.isEmpty())
        // whatever is in front once it stops moving is what the features are told about
        assertEquals(null, monitor.settle(at))
        assertEquals("com.instagram.android", monitor.currentPackage)
    }

    @Test
    fun transientWindowsNeverBecomeTheForegroundApp() {
        val monitor = monitor()
        monitor.ignorePackages(listOf("com.touchtype.swiftkey"))
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        val transient = listOf(
            window("com.android.systemui", "com.android.systemui.shade.NotificationPanelView"),
            window("com.touchtype.swiftkey", "com.touchtype.KeyboardService"),
            window("android", "com.android.internal.app.ChooserActivity"),
            window("com.android.intentresolver", "com.android.intentresolver.ChooserActivity"),
            window("com.oppo.launcher", "com.oppo.launcher.recents.RecentsActivity"),
            window(own, "android.widget.FrameLayout"),
        )
        transient.forEach {
            assertEquals(
                "${it.className} must not count as an app switch",
                ForegroundDecision.Unchanged,
                monitor.onWindow(it, nowMs = 5_000)
            )
        }
        assertEquals("com.instagram.android", monitor.currentPackage)
    }

    @Test
    fun ourOwnActivitiesStillCountButOurOverlaysDoNot() {
        val monitor = monitor()
        assertEquals(
            ForegroundDecision.Changed(own, true),
            monitor.onWindow(window(own, "$own.MainActivity"), nowMs = 1_000)
        )
    }

    @Test
    fun theLauncherAlwaysWaitsBecauseGestureNavigationPassesThroughIt() {
        val monitor = monitor()
        monitor.settleForPackages(listOf("com.android.launcher3"))
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        // far outside the settle window, so a normal app would be published right away
        val decision = monitor.onWindow(window("com.android.launcher3"), nowMs = 20_000)
        assertEquals(ForegroundDecision.Settling("com.android.launcher3", 300), decision)
        // swiping on through to another app never lets the launcher reach the features, and the
        // app that was actually wanted arrives without waiting for it
        assertEquals(
            ForegroundDecision.Changed("com.spotify.music", true),
            monitor.onWindow(window("com.spotify.music"), nowMs = 20_150)
        )
        assertEquals(null, monitor.settle(nowMs = 20_300))
    }

    @Test
    fun goingHomeAndStayingThereReachesTheFeatures() {
        val monitor = monitor()
        monitor.settleForPackages(listOf("com.android.launcher3"))
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        monitor.onWindow(window("com.android.launcher3"), nowMs = 20_000)
        assertEquals(
            ForegroundDecision.Changed("com.android.launcher3", true),
            monitor.settle(nowMs = 20_300)
        )
    }

    @Test
    fun aPartialWindowDoesNotSwallowTheAppsOwnFullScreenWindow() {
        val monitor = monitor()
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        // a dialog from another package opens over the app, covering only part of the screen
        assertEquals(
            ForegroundDecision.Changed("com.android.permissioncontroller", false),
            monitor.onWindow(
                window("com.android.permissioncontroller", isFullScreen = false), nowMs = 9_000
            )
        )
        // the app behind it comes back full screen, and the features have to hear about it
        assertEquals(
            ForegroundDecision.Changed("com.instagram.android", true),
            monitor.onWindow(window("com.instagram.android"), nowMs = 20_000)
        )
    }

    @Test
    fun anAppThatGrowsToFullScreenIsRepublished() {
        val monitor = monitor()
        assertEquals(
            ForegroundDecision.Changed("com.instagram.android", false),
            monitor.onWindow(window("com.instagram.android", isFullScreen = false), nowMs = 1_000)
        )
        assertEquals(
            ForegroundDecision.Changed("com.instagram.android", true),
            monitor.onWindow(window("com.instagram.android"), nowMs = 9_000)
        )
    }

    @Test
    fun forgettingMakesTheSameAppCountAsASwitchAgain() {
        val monitor = monitor()
        monitor.onWindow(window("com.instagram.android"), nowMs = 1_000)
        assertEquals(
            ForegroundDecision.Unchanged,
            monitor.onWindow(window("com.instagram.android"), nowMs = 9_000)
        )
        monitor.forget()
        assertEquals(
            ForegroundDecision.Changed("com.instagram.android", true),
            monitor.onWindow(window("com.instagram.android"), nowMs = 10_000)
        )
    }
}
