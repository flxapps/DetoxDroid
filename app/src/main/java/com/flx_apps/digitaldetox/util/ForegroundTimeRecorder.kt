package com.flx_apps.digitaldetox.util

import android.os.SystemClock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * How long each app was in front today, measured by DetoxDroid itself. The same measurement keyed
 * by site instead of package is the time on websites (see
 * [com.flx_apps.digitaldetox.features.UsageStatsTracker.websiteTimeRecorder]).
 *
 * This is the fallback for phones where `UsageStatsManager` is not an option: on some ROMs the
 * usage-access grant sits behind a restricted-settings gate that a sideloaded app may never get
 * through, and without it every number on the usage screen is zero. The accessibility service
 * already knows which app is in front, so the time can be taken from there instead.
 *
 * It is a fallback and not the source of truth, because it can only measure what it was there for:
 * nothing before DetoxDroid was installed, nothing while it was stopped, and nothing from a work
 * profile the service does not see.
 */
class ForegroundTimeRecorder(
    /**
     * A clock that does not move when the device sleeps and cannot be set by the user, since a
     * session is the difference between two readings of it.
     */
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) {
    /** What one app added up to today. */
    data class AppTime(val foregroundMs: Long, val launchCount: Int)

    /**
     * A single stretch in one app longer than this is not a stretch in one app: the screen turning
     * off ends a session, so what is left is a clock that moved without us, and crediting it would
     * quietly invent hours.
     */
    private val maxSessionMs = 4L * 60 * 60 * 1000

    /** The day being measured, from the first call on. */
    private var day: LocalDate? = null
    private val foregroundMs = ConcurrentHashMap<String, Long>()
    private val launchCounts = ConcurrentHashMap<String, Int>()
    private var openPackage: String? = null
    private var openSinceMs = 0L

    /** The day whose persisted values have been folded in, see [restore]. */
    private var restoredDay: LocalDate? = null

    /**
     * Records that [packageName] came to the front, which also ends the session before it.
     */
    @Synchronized
    fun onForegroundApp(packageName: String, today: LocalDate = LocalDate.now()) {
        // the same app announced again (a dialog of it giving way to its full window) is the
        // session going on, not a new one
        if (packageName == openPackage) return
        closeSession(today)
        openPackage = packageName
        openSinceMs = nowMs()
        launchCounts.merge(packageName, 1, Int::plus)
    }

    /**
     * Ends the running session, which is what the screen turning off and the service shutting down
     * both mean.
     */
    @Synchronized
    fun closeSession(today: LocalDate = LocalDate.now()) {
        rollOverIfNeeded(today)
        val packageName = openPackage ?: return
        val durationMs = (nowMs() - openSinceMs).coerceAtLeast(0L)
        openPackage = null
        openSinceMs = 0L
        if (durationMs in 1..maxSessionMs) {
            foregroundMs.merge(packageName, durationMs, Long::plus)
        }
    }

    /**
     * Everything measured today, including the running session, so a screen that is refreshed
     * while the user sits in one app does not read as if that app were idle.
     */
    @Synchronized
    fun snapshot(today: LocalDate = LocalDate.now()): Map<String, AppTime> {
        rollOverIfNeeded(today)
        val running = openPackage
        if (running == null && foregroundMs.isEmpty() && launchCounts.isEmpty()) return emptyMap()
        val runningMs = if (running == null) 0L else {
            (nowMs() - openSinceMs).coerceAtLeast(0L).coerceAtMost(maxSessionMs)
        }
        val packageNames = foregroundMs.keys + launchCounts.keys
        return packageNames.associateWith { packageName ->
            AppTime(
                foregroundMs = (foregroundMs[packageName] ?: 0L) +
                        (if (packageName == running) runningMs else 0L),
                launchCount = launchCounts[packageName] ?: 0
            )
        }
    }

    /**
     * Folds in what was persisted before this process started, so a restart does not restart the
     * day's measurement at zero. Added rather than taken over, because the live values may already
     * hold a few seconds the persisted ones cannot; and only once per day, because the second time
     * they would be in there already.
     */
    @Synchronized
    fun restore(times: Map<String, AppTime>, today: LocalDate = LocalDate.now()) {
        rollOverIfNeeded(today)
        if (restoredDay == today) return
        restoredDay = today
        for ((packageName, time) in times) {
            foregroundMs.merge(packageName, time.foregroundMs, Long::plus)
            launchCounts.merge(packageName, time.launchCount, Int::plus)
        }
    }

    /**
     * Whether the live values hold everything measured [today]: either the persisted values have
     * been folded in ([restore]), or this process has been running since before the day began.
     * Until then a snapshot is incomplete and must not replace what is persisted.
     */
    @Synchronized
    fun isComplete(today: LocalDate = LocalDate.now()): Boolean {
        rollOverIfNeeded(today)
        return restoredDay == today
    }

    /**
     * Forgets everything measured today, the running session included, as if nothing had been. For
     * switching a measurement off, whose persisted values go at the same time.
     */
    @Synchronized
    fun clear(today: LocalDate = LocalDate.now()) {
        rollOverIfNeeded(today)
        foregroundMs.clear()
        launchCounts.clear()
        openPackage = null
        openSinceMs = 0L
        // nothing is persisted any more, so the live values are the whole of the day
        restoredDay = today
    }

    /**
     * A new day starts from nothing. A session that is open at that moment goes on, counted for the
     * new day from here: the part before midnight is gone, because nothing closed it on time, and
     * that is the conservative side to err on.
     */
    private fun rollOverIfNeeded(today: LocalDate) {
        if (today == day) return
        val isFirstDay = day == null
        day = today
        if (isFirstDay) return
        foregroundMs.clear()
        launchCounts.clear()
        if (openPackage != null) openSinceMs = nowMs()
        // nothing of the new day can be persisted yet, so the live values are the whole of it
        restoredDay = today
    }
}
