package com.flx_apps.digitaldetox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface DailyAppUsageDao {
    @Query("SELECT * FROM daily_app_usage WHERE date BETWEEN :start AND :end ORDER BY date DESC, totalTimeMs DESC")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<DailyAppUsage>

    @Query("SELECT * FROM daily_app_usage WHERE date = :date ORDER BY totalTimeMs DESC")
    suspend fun getForDate(date: LocalDate): List<DailyAppUsage>

    @Query("SELECT COUNT(DISTINCT date) FROM daily_app_usage WHERE date BETWEEN :start AND :end")
    suspend fun getDayCountInRange(start: LocalDate, end: LocalDate): Int

    @Query("SELECT SUM(totalTimeMs) FROM daily_app_usage WHERE date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(start: LocalDate, end: LocalDate): Long?

    @Query("SELECT MIN(date) FROM daily_app_usage")
    suspend fun getEarliestDate(): LocalDate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usages: List<DailyAppUsage>)

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForPackageInRange(packageName: String, start: LocalDate, end: LocalDate): List<DailyAppUsage>

    @Query("DELETE FROM daily_app_usage WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: LocalDate): Int
}