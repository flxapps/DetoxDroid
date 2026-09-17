package com.flx_apps.digitaldetox.util

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.data.DataStoreProperty

/**
 * A clock for waits that must not be cut short, like the commitment password's lockout and
 * recovery: setting the phone's date or time forward doesn't bring it any closer.
 *
 * Within one boot it only follows [SystemClock.elapsedRealtime], which no setting touches. A reboot
 * starts that count over, which leaves the wall clock, taken minus every change made to it by hand:
 * [sync] runs on each change ([Intent.ACTION_TIME_CHANGED]) and moves the jump into an offset.
 * After a reboot it never goes back behind the last time it knew, so a phone that lost its clock
 * while it was off carries on from there instead of waiting out the years in between.
 */
object TrustedClock {
    // the clocks it reads, swapped out in tests
    internal var wallMs: () -> Long = System::currentTimeMillis
    internal var elapsedMs: () -> Long = SystemClock::elapsedRealtime
    internal var bootCount: () -> Int = {
        Settings.Global.getInt(
            DetoxDroidApplication.appContext.contentResolver, Settings.Global.BOOT_COUNT, -1
        )
    }

    // the last sync: its time, and the boot and elapsed realtime it ran at
    private var anchorMs: Long by DataStoreProperty(longPreferencesKey("trustedClock_anchor"), 0L)
    private var anchorElapsedMs: Long by DataStoreProperty(
        longPreferencesKey("trustedClock_anchorElapsed"), Long.MAX_VALUE
    )
    private var anchorBoot: Int by DataStoreProperty(
        intPreferencesKey("trustedClock_anchorBoot"), -1
    )

    /** How far the wall clock runs ahead of this one, from the changes made to it by hand. */
    private var wallOffsetMs: Long by DataStoreProperty(
        longPreferencesKey("trustedClock_wallOffset"), 0L
    )

    /** The current time. The first call after a reboot syncs as well. */
    @Synchronized
    fun now(): Long {
        val elapsed = elapsedMs()
        return if (isSameBoot(elapsed)) anchorMs + (elapsed - anchorElapsedMs) else sync()
    }

    /**
     * Anchors the clock on the current moment and returns it. Whatever the wall clock jumped since
     * the last sync in this boot goes into the offset, so it counts for nothing, after a reboot too.
     */
    @Synchronized
    fun sync(): Long {
        val wall = wallMs()
        val elapsed = elapsedMs()
        val now = if (isSameBoot(elapsed)) {
            anchorMs + (elapsed - anchorElapsedMs)
        } else {
            maxOf(wall - wallOffsetMs, anchorMs)
        }
        anchorMs = now
        anchorElapsedMs = elapsed
        anchorBoot = bootCount()
        wallOffsetMs = wall - now
        return now
    }

    // elapsed realtime running backwards gives a reboot away where the boot count is missing
    private fun isSameBoot(elapsed: Long) = bootCount() == anchorBoot && elapsed >= anchorElapsedMs
}
