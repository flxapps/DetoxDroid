package com.flx_apps.digitaldetox.data.repository

import com.flx_apps.digitaldetox.data.DailyAppUsage
import com.flx_apps.digitaldetox.data.DailyAppUsageDao
import com.flx_apps.digitaldetox.data.DailyDeviceStats
import com.flx_apps.digitaldetox.data.DailyDeviceStatsDao
import com.flx_apps.digitaldetox.data.DailyGrayscaleStats
import com.flx_apps.digitaldetox.data.DailyGrayscaleStatsDao
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.util.DailyAppCounter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates historical usage stats between the OS UsageStatsManager and the Room DAO. */
@Singleton
class UsageStatsRepository @Inject constructor(
    private val dao: DailyAppUsageDao,
    private val grayscaleDao: DailyGrayscaleStatsDao,
    private val deviceStatsDao: DailyDeviceStatsDao
) {
    private val snapshotMutex = Mutex()
    private var counterBaselineDate: LocalDate? = null
    private val scrollCounterBaselines = mutableMapOf<String, Int>()
    private val scrollDistanceBaselines = mutableMapOf<String, Int>()
    private val breakCounterBaselines = mutableMapOf<String, Int>()
    private val blockCounterBaselines = mutableMapOf<String, Int>()

    /**
     * Merges the current OS usage stats and in-memory feature counters into today's Room rows.
     * Safe to call from multiple places (worker, ViewModel, scroll debounce) — runs serialized.
     */
    suspend fun snapshotToday() = snapshotMutex.withLock {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = System.currentTimeMillis()

        val osStats = UsageStatsProvider.queryForPeriod(startMs, endMs)
        val eventCounts = UsageStatsProvider.queryEventCounts(startMs, endMs)
        val launchCounts = eventCounts.launchCounts
        val groupedSessionCounts = UsageStatsProvider.groupSessionCounts(eventCounts.sessions)

        val scrollCounts = UsageStatsTracker.scrollEventCounter.snapshot()
        val scrollDistances = UsageStatsTracker.scrollDistanceCounter.snapshot()
        val breakCounts = BreakDoomScrollingFeature.breakCounter.snapshot()
        val blockCounts = DisableAppsFeature.blockCounter.snapshot()

        resetCounterBaselinesIfNeeded(today)
        val existing = dao.getForDate(today).associateBy { it.packageName }
        val packageNames = buildSet {
            addAll(osStats.keys)
            addAll(existing.keys)
            addAll(scrollCounts.keys)
            addAll(breakCounts.keys)
            addAll(blockCounts.keys)
        }

        val rows = packageNames.map { pkg ->
            val stat = osStats[pkg]
            val prior = existing[pkg]

            DailyAppUsage(
                rowId = DailyAppUsage.createRowId(today, pkg),
                date = today,
                packageName = pkg,
                totalTimeMs = stat?.totalTimeInForeground ?: prior?.totalTimeMs ?: 0L,
                sessionCount = groupedSessionCounts[pkg] ?: prior?.sessionCount ?: 0,
                launchCount = launchCounts[pkg] ?: prior?.launchCount ?: 0,
                scrollCount = mergeCounter(
                    packageName = pkg,
                    currentCount = scrollCounts[pkg] ?: 0,
                    persistedCount = prior?.scrollCount ?: 0,
                    baselines = scrollCounterBaselines
                ),
                scrollDistancePx = mergeCounter(
                    packageName = pkg,
                    currentCount = scrollDistances[pkg] ?: 0,
                    persistedCount = prior?.scrollDistancePx ?: 0,
                    baselines = scrollDistanceBaselines
                ),
                breakCount = mergeCounter(
                    packageName = pkg,
                    currentCount = breakCounts[pkg] ?: 0,
                    persistedCount = prior?.breakCount ?: 0,
                    baselines = breakCounterBaselines
                ),
                blockCount = mergeCounter(
                    packageName = pkg,
                    currentCount = blockCounts[pkg] ?: 0,
                    persistedCount = prior?.blockCount ?: 0,
                    baselines = blockCounterBaselines
                )
            )
        }

        if (rows.isNotEmpty()) {
            dao.upsertAll(rows)
        }

        // Device-level stats are recomputed from the full-day event log on every snapshot, so a
        // plain overwrite is correct (no baseline merging needed).
        deviceStatsDao.upsert(
            DailyDeviceStats(
                date = today,
                unlockCount = eventCounts.unlockCount,
                unlockHourBuckets = eventCounts.unlockHourBuckets,
                launchHourBuckets = eventCounts.hourBuckets
            )
        )

        snapshotGrayscaleToday()
    }

    private suspend fun snapshotGrayscaleToday() {
        val today = LocalDate.now()
        val liveGrayscaleMs = GrayscaleAppsFeature.currentGrayscaleTimeMs()
        val existingMs = grayscaleDao.getForDate(today) ?: 0L
        val mergedMs = maxOf(liveGrayscaleMs, existingMs)
        // don't litter the table with all-zero rows on days without grayscale usage
        if (mergedMs == 0L) return
        grayscaleDao.upsert(DailyGrayscaleStats(date = today, grayscaleTimeMs = mergedMs))
    }

    private fun resetCounterBaselinesIfNeeded(today: LocalDate) {
        if (counterBaselineDate == today) return
        counterBaselineDate = today
        scrollCounterBaselines.clear()
        scrollDistanceBaselines.clear()
        breakCounterBaselines.clear()
        blockCounterBaselines.clear()
    }

    /**
     * Reconciles a live in-memory counter with its persisted value. The baseline remembers the
     * live count at the previous snapshot, so only the delta since then is added on top of the
     * persisted count (the live counters reset on process restart, the persisted ones don't).
     */
    private fun mergeCounter(
        packageName: String,
        currentCount: Int,
        persistedCount: Int,
        baselines: MutableMap<String, Int>
    ): Int {
        val previousLiveCount = baselines[packageName]
        baselines[packageName] = currentCount
        return if (previousLiveCount == null) {
            persistedCount + currentCount
        } else {
            persistedCount + (currentCount - previousLiveCount).coerceAtLeast(0)
        }
    }

    /** Returns the total grayscale time across all days in [start]..[end] (inclusive). */
    suspend fun getTotalGrayscaleTimeInRange(start: LocalDate, end: LocalDate): Long {
        return grayscaleDao.getTotalGrayscaleTimeInRange(start, end) ?: 0L
    }

    suspend fun getHistorical(start: LocalDate, end: LocalDate): List<DailyAppUsage> {
        return dao.getRange(start, end)
    }

    suspend fun getHistoricalDayCount(start: LocalDate, end: LocalDate): Int {
        return dao.getDayCountInRange(start, end)
    }

    /** Returns the summed screen time of all Room rows in [start]..[end] (inclusive). */
    suspend fun getTotalScreenTimeInRange(start: LocalDate, end: LocalDate): Long {
        return dao.getTotalTimeInRange(start, end) ?: 0L
    }

    suspend fun getAppHistorical(
        packageName: String, start: LocalDate, end: LocalDate
    ): List<DailyAppUsage> {
        return dao.getForPackageInRange(packageName, start, end)
    }

    suspend fun getEarliestHistoryDate(): LocalDate? {
        return dao.getEarliestDate()
    }

    suspend fun pruneOlderThan(cutoff: LocalDate): Int {
        val prunedApps = dao.deleteOlderThan(cutoff)
        val prunedGrayscale = grayscaleDao.deleteOlderThan(cutoff)
        val prunedDeviceStats = deviceStatsDao.deleteOlderThan(cutoff)
        return prunedApps + prunedGrayscale + prunedDeviceStats
    }

    /** Returns the persisted device stats (unlocks, hour histograms) for [start]..[end]. */
    suspend fun getDeviceStats(start: LocalDate, end: LocalDate): List<DailyDeviceStats> {
        return deviceStatsDao.getRange(start, end)
    }

    /** Returns the number of days in [start]..[end] with persisted device stats. */
    suspend fun getDeviceStatsDayCount(start: LocalDate, end: LocalDate): Int {
        return deviceStatsDao.getDayCountInRange(start, end)
    }

    /** Returns the summed unlock count of all persisted device stats in [start]..[end]. */
    suspend fun getTotalUnlocksInRange(start: LocalDate, end: LocalDate): Int {
        return deviceStatsDao.getTotalUnlocksInRange(start, end) ?: 0
    }

    /**
     * Restores today's persisted per-app counts from Room into the live in-memory counters
     * (scrolls, doom-scroll breaks, app blocks) after a process restart, and records matching
     * baselines so the next [snapshotToday] doesn't double-count the restored values. Runs under
     * the snapshot mutex so a concurrent snapshot can't interleave with the restore.
     */
    suspend fun restoreTodayCounters() = snapshotMutex.withLock {
        val today = LocalDate.now()
        resetCounterBaselinesIfNeeded(today)
        val rows = dao.getForDate(today)
        restoreCounter(rows, DailyAppUsage::scrollCount, UsageStatsTracker.scrollEventCounter, scrollCounterBaselines)
        restoreCounter(rows, DailyAppUsage::scrollDistancePx, UsageStatsTracker.scrollDistanceCounter, scrollDistanceBaselines)
        restoreCounter(rows, DailyAppUsage::breakCount, BreakDoomScrollingFeature.breakCounter, breakCounterBaselines)
        restoreCounter(rows, DailyAppUsage::blockCount, DisableAppsFeature.blockCounter, blockCounterBaselines)
    }

    private fun restoreCounter(
        rows: List<DailyAppUsage>,
        persistedCount: (DailyAppUsage) -> Int,
        counter: DailyAppCounter,
        baselines: MutableMap<String, Int>
    ) {
        val counts = rows.associate { it.packageName to persistedCount(it) }.filterValues { it > 0 }
        if (counts.isEmpty()) return
        counter.restore(counts)
        // baseline = live value after the (max-merging) restore, so the next snapshot only adds
        // the delta of genuinely new events on top of the persisted count
        for (pkg in counts.keys) {
            baselines[pkg] = counter.countFor(pkg)
        }
    }

    companion object {
        /** How many months of historical usage snapshots are retained before pruning. */
        const val HISTORY_RETENTION_MONTHS = 3L
    }
}
