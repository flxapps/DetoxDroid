package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.util.CooldownRegistry
import com.flx_apps.digitaldetox.util.FinishGraceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownRegistryTest {
    private var now = 0L
    private val registry = CooldownRegistry { now }

    @Test
    fun `whole-app cooldown blocks every surface and locks the app`() {
        registry.start("app", surfaceId = null, durationMs = 10_000)
        assertTrue(registry.activeCooldownFor("app", "feed")!!.wholeApp)
        assertTrue(registry.activeCooldownFor("app", null)!!.wholeApp)
        assertEquals(10_000L, registry.appLockEndMs("app"))
    }

    @Test
    fun `surface cooldown blocks only its own surface`() {
        registry.start("app", surfaceId = "reels", durationMs = 10_000)
        assertNotNull(registry.activeCooldownFor("app", "reels"))
        assertFalse(registry.activeCooldownFor("app", "reels")!!.wholeApp)
        // other surfaces (e.g. the DM list) and unidentified surfaces stay usable
        assertNull(registry.activeCooldownFor("app", "direct_messages"))
        assertNull(registry.activeCooldownFor("app", null))
        // and the app itself may still be opened
        assertNull(registry.appLockEndMs("app"))
    }

    @Test
    fun `cooldowns expire`() {
        registry.start("app", surfaceId = null, durationMs = 10_000)
        now = 10_000
        assertNull(registry.activeCooldownFor("app", "feed"))
        assertNull(registry.appLockEndMs("app"))
    }

    @Test
    fun `clear lifts all cooldowns of the app`() {
        registry.start("app", surfaceId = null, durationMs = 10_000)
        registry.start("app", surfaceId = "reels", durationMs = 10_000)
        registry.start("other", surfaceId = null, durationMs = 10_000)
        registry.clear("app")
        assertNull(registry.activeCooldownFor("app", "reels"))
        assertNotNull(registry.activeCooldownFor("other", "anything"))
    }

    @Test
    fun `restart replaces the previous end time`() {
        registry.start("app", surfaceId = null, durationMs = 10_000)
        now = 5_000
        registry.start("app", surfaceId = null, durationMs = 10_000)
        assertEquals(15_000L, registry.appLockEndMs("app"))
    }

    @Test
    fun `hasAnyCooldown sees surface cooldowns and honors expiry`() {
        assertFalse(registry.hasAnyCooldown("app"))
        registry.start("app", surfaceId = "reels", durationMs = 10_000)
        assertTrue(registry.hasAnyCooldown("app"))
        assertFalse(registry.hasAnyCooldown("other"))
        now = 10_000
        assertFalse(registry.hasAnyCooldown("app"))
    }
}

class FinishGraceTrackerTest {
    private var now = 0L
    private val tracker = FinishGraceTracker(
        timeoutMs = 90_000,
        maxScrollBursts = 3,
        burstSpacingMs = 800,
        maxTotalMs = 300_000,
        nowMs = { now }
    )

    @Test
    fun `scroll events without a grace are ignored`() {
        assertFalse(tracker.onScrollEvent("app"))
        assertFalse(tracker.isInGrace("app"))
    }

    @Test
    fun `events within one burst count as a single gesture`() {
        tracker.start("app", surfaceId = null)
        // one fling: many events in rapid succession → one gesture, grace continues
        repeat(10) {
            now += 100
            assertFalse(tracker.onScrollEvent("app"))
        }
    }

    @Test
    fun `scrolling on a few times uses the grace up`() {
        tracker.start("app", surfaceId = null)
        now += 5_000
        assertFalse(tracker.onScrollEvent("app")) // gesture 1
        now += 5_000
        assertFalse(tracker.onScrollEvent("app")) // gesture 2
        now += 5_000
        assertTrue(tracker.onScrollEvent("app")) // gesture 3 → guide out
    }

    @Test
    fun `the timeout uses the grace up even without new gestures`() {
        tracker.start("app", surfaceId = null)
        now += 90_000
        assertTrue(tracker.onScrollEvent("app"))
    }

    @Test
    fun `end returns the covered surface exactly once`() {
        tracker.start("app", surfaceId = "reels")
        assertEquals("reels", tracker.end("app")!!.surfaceId)
        assertNull(tracker.end("app"))
        assertFalse(tracker.isInGrace("app"))
    }

    @Test
    fun `graces are tracked per app`() {
        tracker.start("a", surfaceId = null)
        tracker.start("b", surfaceId = null)
        assertEquals(setOf("a", "b"), tracker.activePackages())
        tracker.end("a")
        assertEquals(setOf("b"), tracker.activePackages())
    }

    @Test
    fun `scrolling on within the triggering surface uses the grace up`() {
        tracker.start("app", surfaceId = "feed")
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "feed")) // gesture 1
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "feed")) // gesture 2
        now += 5_000
        assertTrue(tracker.onScrollEvent("app", "feed")) // gesture 3 → guide out
    }

    @Test
    fun `reading a different surface does not use the grace up`() {
        tracker.start("app", surfaceId = "feed")
        // scrolling the comments at leisure: never counts as "moving on"
        repeat(10) {
            now += 5_000
            assertFalse(tracker.onScrollEvent("app", "comments"))
        }
        // ...but paging on in the feed still does
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "feed"))
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "feed"))
        now += 5_000
        assertTrue(tracker.onScrollEvent("app", "feed"))
    }

    @Test
    fun `reading a different surface extends the timeout up to the hard cap`() {
        tracker.start("app", surfaceId = "feed")
        // each comments gesture re-arms the 90 s window, far past the base timeout...
        for (time in 60_000L..240_000L step 60_000L) {
            now = time
            assertFalse("still reading at ${time / 1000} s", tracker.onScrollEvent("app", "comments"))
        }
        // ...until the 300 s hard cap ends the grace regardless
        now = 300_000
        assertTrue(tracker.onScrollEvent("app", "comments"))
    }

    @Test
    fun `unidentified surfaces neither count nor extend when the trigger surface is known`() {
        tracker.start("app", surfaceId = "feed")
        repeat(5) {
            now += 5_000
            assertFalse(tracker.onScrollEvent("app", null))
        }
        // the base timeout was not re-armed by the null-surface events
        now = 90_000
        assertTrue(tracker.onScrollEvent("app", null))
    }

    @Test
    fun `without a trigger surface every scroll gesture counts`() {
        tracker.start("app", surfaceId = null)
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "comments"))
        now += 5_000
        assertFalse(tracker.onScrollEvent("app", "feed"))
        now += 5_000
        assertTrue(tracker.onScrollEvent("app", null))
    }

    @Test
    fun `remainingMs tracks the extended deadline`() {
        assertNull(tracker.remainingMs("app"))
        tracker.start("app", surfaceId = "feed")
        assertEquals(90_000L, tracker.remainingMs("app"))
        now = 60_000
        assertFalse(tracker.onScrollEvent("app", "comments"))
        // the deadline moved to 60 s + 90 s
        assertEquals(90_000L, tracker.remainingMs("app"))
        now = 150_000
        assertEquals(0L, tracker.remainingMs("app"))
    }
}
