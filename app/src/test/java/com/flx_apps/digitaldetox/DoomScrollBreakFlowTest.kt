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
}

class FinishGraceTrackerTest {
    private var now = 0L
    private val tracker = FinishGraceTracker(
        timeoutMs = 90_000,
        maxScrollBursts = 3,
        burstSpacingMs = 800,
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
}
