package com.flx_apps.digitaldetox

import com.flx_apps.digitaldetox.data.DailyAppUsage
import com.flx_apps.digitaldetox.data.DailyAppUsageDao
import com.flx_apps.digitaldetox.data.DailyDeviceStats
import com.flx_apps.digitaldetox.data.DailyDeviceStatsDao
import com.flx_apps.digitaldetox.data.DailyGrayscaleStats
import com.flx_apps.digitaldetox.data.DailyGrayscaleStatsDao
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for [UsageStatsRepository.snapshotCounters] — the cheap counters-only persistence used by
 * the scroll-debounce path and service shutdown. It must merge the live counters via the shared
 * baselines (so a later full snapshot doesn't double-count) and must not touch any other columns.
 */
class CountersOnlySnapshotTest {

    private class FakeAppUsageDao : DailyAppUsageDao {
        val rows = mutableMapOf<String, DailyAppUsage>()

        override suspend fun getRange(start: LocalDate, end: LocalDate) =
            rows.values.filter { it.date in start..end }

        override suspend fun getForDate(date: LocalDate) = rows.values.filter { it.date == date }

        override suspend fun getDayCountInRange(start: LocalDate, end: LocalDate) =
            rows.values.filter { it.date in start..end }.map { it.date }.distinct().size

        override suspend fun getTotalTimeInRange(start: LocalDate, end: LocalDate) =
            rows.values.filter { it.date in start..end }.sumOf { it.totalTimeMs }

        override suspend fun getEarliestDate() = rows.values.minOfOrNull { it.date }

        override suspend fun upsertAll(usages: List<DailyAppUsage>) {
            usages.forEach { rows[it.rowId] = it }
        }

        override suspend fun getForPackageInRange(
            packageName: String, start: LocalDate, end: LocalDate
        ) = rows.values.filter { it.packageName == packageName && it.date in start..end }

        override suspend fun deleteOlderThan(cutoff: LocalDate): Int {
            val removed = rows.values.filter { it.date < cutoff }
            removed.forEach { rows.remove(it.rowId) }
            return removed.size
        }
    }

    private class FakeGrayscaleDao : DailyGrayscaleStatsDao {
        override suspend fun getTotalGrayscaleTimeInRange(start: LocalDate, end: LocalDate): Long? = null
        override suspend fun getForDate(date: LocalDate): Long? = null
        override suspend fun upsert(stats: DailyGrayscaleStats) = Unit
        override suspend fun deleteOlderThan(cutoff: LocalDate) = 0
    }

    private class FakeDeviceStatsDao : DailyDeviceStatsDao {
        override suspend fun getRange(start: LocalDate, end: LocalDate) = emptyList<DailyDeviceStats>()
        override suspend fun getDayCountInRange(start: LocalDate, end: LocalDate) = 0
        override suspend fun getTotalUnlocksInRange(start: LocalDate, end: LocalDate): Int? = null
        override suspend fun upsert(stats: DailyDeviceStats) = Unit
        override suspend fun deleteOlderThan(cutoff: LocalDate) = 0
    }

    private val dao = FakeAppUsageDao()
    private val repository = UsageStatsRepository(dao, FakeGrayscaleDao(), FakeDeviceStatsDao())
    private val today: LocalDate = LocalDate.now()

    private fun row(
        pkg: String,
        totalTimeMs: Long = 0,
        sessionCount: Int = 0,
        launchCount: Int = 0,
        scrollCount: Int = 0,
        scrollDistancePx: Int = 0,
        breakCount: Int = 0,
        blockCount: Int = 0
    ) = DailyAppUsage(
        rowId = DailyAppUsage.createRowId(today, pkg),
        date = today,
        packageName = pkg,
        totalTimeMs = totalTimeMs,
        sessionCount = sessionCount,
        launchCount = launchCount,
        scrollCount = scrollCount,
        scrollDistancePx = scrollDistancePx,
        breakCount = breakCount,
        blockCount = blockCount
    )

    @Test
    fun `writes counters and preserves the other columns of an existing row`() = runTest {
        dao.rows[row("com.app").rowId] =
            row("com.app", totalTimeMs = 123L, sessionCount = 4, launchCount = 7, scrollCount = 10)

        repository.snapshotCounters(
            scrollCounts = mapOf("com.app" to 25),
            scrollDistances = mapOf("com.app" to 5000),
            breakCounts = mapOf("com.app" to 2),
            blockCounts = emptyMap()
        )

        val updated = dao.getForDate(today).single()
        // no baseline yet → the whole live count is added on top of the persisted one
        assertEquals(35, updated.scrollCount)
        assertEquals(5000, updated.scrollDistancePx)
        assertEquals(2, updated.breakCount)
        // untouched columns survive
        assertEquals(123L, updated.totalTimeMs)
        assertEquals(4, updated.sessionCount)
        assertEquals(7, updated.launchCount)
    }

    @Test
    fun `consecutive snapshots only add the delta since the previous one`() = runTest {
        repository.snapshotCounters(
            scrollCounts = mapOf("com.app" to 10),
            scrollDistances = emptyMap(),
            breakCounts = emptyMap(),
            blockCounts = emptyMap()
        )
        repository.snapshotCounters(
            scrollCounts = mapOf("com.app" to 15),
            scrollDistances = emptyMap(),
            breakCounts = emptyMap(),
            blockCounts = emptyMap()
        )

        // 10 on the first snapshot, +5 delta on the second — not 10 + 15
        assertEquals(15, dao.getForDate(today).single().scrollCount)
    }

    @Test
    fun `shares its baselines with the full snapshot logic`() = runTest {
        // counters-only snapshot records the live count as baseline …
        repository.snapshotCounters(
            scrollCounts = mapOf("com.app" to 10),
            scrollDistances = emptyMap(),
            breakCounts = emptyMap(),
            blockCounts = emptyMap()
        )
        // … so an unchanged live count must not be re-added by a later snapshot
        repository.snapshotCounters(
            scrollCounts = mapOf("com.app" to 10),
            scrollDistances = emptyMap(),
            breakCounts = emptyMap(),
            blockCounts = emptyMap()
        )

        assertEquals(10, dao.getForDate(today).single().scrollCount)
    }

    @Test
    fun `does nothing when there are no live counters`() = runTest {
        repository.snapshotCounters(
            scrollCounts = emptyMap(),
            scrollDistances = emptyMap(),
            breakCounts = emptyMap(),
            blockCounts = emptyMap()
        )

        assertTrue(dao.rows.isEmpty())
        assertNull(dao.getEarliestDate())
    }

    @Test
    fun `creates a fresh row for a package without one`() = runTest {
        repository.snapshotCounters(
            scrollCounts = emptyMap(),
            scrollDistances = emptyMap(),
            breakCounts = mapOf("com.new" to 3),
            blockCounts = emptyMap()
        )

        val created = dao.getForDate(today).single()
        assertEquals("com.new", created.packageName)
        assertEquals(3, created.breakCount)
        assertEquals(0L, created.totalTimeMs)
    }
}
