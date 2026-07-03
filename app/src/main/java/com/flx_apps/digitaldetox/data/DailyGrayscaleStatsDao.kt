package com.flx_apps.digitaldetox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface DailyGrayscaleStatsDao {
    @Query("SELECT SUM(grayscale_time_ms) FROM daily_grayscale_stats WHERE date BETWEEN :start AND :end")
    suspend fun getTotalGrayscaleTimeInRange(start: LocalDate, end: LocalDate): Long?

    @Query("SELECT grayscale_time_ms FROM daily_grayscale_stats WHERE date = :date")
    suspend fun getForDate(date: LocalDate): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyGrayscaleStats)

    @Query("DELETE FROM daily_grayscale_stats WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: LocalDate): Int
}
