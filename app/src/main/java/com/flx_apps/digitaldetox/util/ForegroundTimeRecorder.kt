package com.flx_apps.digitaldetox.util

import android.os.SystemClock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * How long each app was in front today, measured by DetoxDroid itself.
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

    private var day: LocalDate = LocalDate.now()
    private val foregroundMs = ConcurrentHashMap<String, Long>()
    private val launchCounts = ConcurrentHashMap<String, Int>()
    private var openPackage: String? = null
    private var openSinceMs = 0L

    /**
     * Records that [packageName] came to the front, which also ends the session before it.
     */
    @Synchronized
    fun onForegroundApp(packageName: String, today: LocalDate = LocalDate.now()) {
        closeSession(today)
        openPackage = packageName
        openSinceMs = nowMs()
        launchCounts.merge(packageName, 1, Int::plus)
    }

    /**
     * Ends the running session, which is what the screen turning off and the service shutting down
     * both mean. A session that was open across midnight is dropped: how much of it belongs to
     * which day is no longer known, and a guess here would show up as usage on a day the phone was
     * not touched.
     */
    @Synchronized
    fun closeSession(today: LocalDate = LocalDate.now()) {
        val rolledOver = rollOverIfNeeded(today)
        val packageName = openPackage
        val startedAtMs = openSinceMs
        openPackage = null
        openSinceMs = 0L
        if (packageName == null || rolledOver) return
        val durationMs = (nowMs() - startedAtMs).coerceAtLeast(0L)
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
     * Seeds the live values from what was persisted, so a process restart does not restart the
     * day's measurement at zero. The persisted values were written from here, so the larger of the
     * two is the one that has everything.
     */
    @Synchronized
    fun restore(times: Map<String, AppTime>, today: LocalDate = LocalDate.now()) {
        rollOverIfNeeded(today)
        for ((packageName, time) in times) {
            foregroundMs.merge(packageName, time.foregroundMs, ::maxOf)
            launchCounts.merge(packageName, time.launchCount, ::maxOf)
        }
    }

    private fun rollOverIfNeeded(today: LocalDate): Boolean {
        if (today == day) return false
        day = today
        foregroundMs.clear()
        launchCounts.clear()
        return true
    }
}
