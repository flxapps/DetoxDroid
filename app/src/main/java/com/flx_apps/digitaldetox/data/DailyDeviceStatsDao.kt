package com.flx_apps.digitaldetox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface DailyDeviceStatsDao {
    @Query("SELECT * FROM daily_device_stats WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<DailyDeviceStats>

    @Query("SELECT COUNT(*) FROM daily_device_stats WHERE date BETWEEN :start AND :end")
    suspend fun getDayCountInRange(start: LocalDate, end: LocalDate): Int

    @Query("SELECT SUM(unlockCount) FROM daily_device_stats WHERE date BETWEEN :start AND :end")
    suspend fun getTotalUnlocksInRange(start: LocalDate, end: LocalDate): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyDeviceStats)

    @Query("DELETE FROM daily_device_stats WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: LocalDate): Int
}
