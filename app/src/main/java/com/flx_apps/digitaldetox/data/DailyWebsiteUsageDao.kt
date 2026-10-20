package com.flx_apps.digitaldetox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface DailyWebsiteUsageDao {
    @Query("SELECT * FROM daily_website_usage WHERE date BETWEEN :start AND :end")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<DailyWebsiteUsage>

    @Query("SELECT * FROM daily_website_usage WHERE date = :date")
    suspend fun getForDate(date: LocalDate): List<DailyWebsiteUsage>

    @Query("SELECT * FROM daily_website_usage WHERE site = :site AND date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getForSiteInRange(
        site: String, start: LocalDate, end: LocalDate
    ): List<DailyWebsiteUsage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usages: List<DailyWebsiteUsage>)

    @Query("DELETE FROM daily_website_usage WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: LocalDate): Int

    @Query("DELETE FROM daily_website_usage")
    suspend fun deleteAll()
}
