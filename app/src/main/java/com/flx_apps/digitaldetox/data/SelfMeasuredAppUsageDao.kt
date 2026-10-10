package com.flx_apps.digitaldetox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface SelfMeasuredAppUsageDao {
    @Query("SELECT * FROM self_measured_app_usage WHERE date BETWEEN :start AND :end ORDER BY date DESC, foregroundTimeMs DESC")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<SelfMeasuredAppUsage>

    @Query("SELECT * FROM self_measured_app_usage WHERE date = :date ORDER BY foregroundTimeMs DESC")
    suspend fun getForDate(date: LocalDate): List<SelfMeasuredAppUsage>

    @Query("SELECT COUNT(DISTINCT date) FROM self_measured_app_usage WHERE date BETWEEN :start AND :end")
    suspend fun getDayCountInRange(start: LocalDate, end: LocalDate): Int

    @Query("SELECT SUM(foregroundTimeMs) FROM self_measured_app_usage WHERE date BETWEEN :start AND :end")
    suspend fun getTotalTimeInRange(start: LocalDate, end: LocalDate): Long?

    @Query("SELECT MIN(date) FROM self_measured_app_usage")
    suspend fun getEarliestDate(): LocalDate?

    @Query("SELECT * FROM self_measured_app_usage WHERE packageName = :packageName AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForPackageInRange(
        packageName: String, start: LocalDate, end: LocalDate
    ): List<SelfMeasuredAppUsage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usages: List<SelfMeasuredAppUsage>)

    @Query("DELETE FROM self_measured_app_usage WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: LocalDate): Int
}
