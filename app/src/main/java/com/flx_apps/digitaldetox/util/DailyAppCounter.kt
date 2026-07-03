package com.flx_apps.digitaldetox.util

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * A thread-safe, per-app event counter that automatically resets when the calendar day rolls over.
 *
 * Features use this to track daily, per-app counts (e.g. scroll events, doom-scroll breaks, app
 * blocks) that are produced on the accessibility thread and read from the usage-stats ViewModel /
 * snapshot worker on background threads. The counts are intentionally in-memory and ephemeral; the
 * [com.flx_apps.digitaldetox.workers.UsageStatsSnapshotWorker] periodically persists them into the
 * usage-stats database, which is the durable store.
 *
 * Counts accumulate as fractions internally (so calibrated events can weigh more or less than 1,
 * see [com.flx_apps.digitaldetox.util.ScrollDeltaCalibrator]) but are exposed as rounded integers.
 */
class DailyAppCounter {
    @Volatile
    private var day: LocalDate = LocalDate.now()
    private val counts = ConcurrentHashMap<String, Double>()

    @Synchronized
    private fun rollOverIfNeeded() {
        val today = LocalDate.now()
        if (today != day) {
            day = today
            counts.clear()
        }
    }

    /** Increments the count for [packageName] by [weight] (1 for a plain, uncalibrated event). */
    fun increment(packageName: String, weight: Double = 1.0) {
        rollOverIfNeeded()
        counts.merge(packageName, weight) { a, b -> a + b }
    }

    /** Returns the count for [packageName] today (0 if none). */
    fun countFor(packageName: String): Int {
        rollOverIfNeeded()
        return counts[packageName]?.roundToInt() ?: 0
    }

    /** Returns the sum of all per-app counts today. */
    fun total(): Int {
        rollOverIfNeeded()
        return counts.values.sum().roundToInt()
    }

    /** Returns an immutable snapshot of the current per-app counts. */
    fun snapshot(): Map<String, Int> {
        rollOverIfNeeded()
        return counts.mapValues { it.value.roundToInt() }
    }

    /**
     * Restores previously persisted counts (e.g. from Room after a process restart)
     * so the in-memory counter reflects the cumulative total and the UI shows
     * accurate data without waiting for new events. Uses a max-merge so events counted
     * between reading the persisted values and restoring them are not lost.
     */
    fun restore(counts: Map<String, Int>) {
        rollOverIfNeeded()
        for ((pkg, count) in counts) {
            this.counts.merge(pkg, count.toDouble(), ::maxOf)
        }
    }
}
