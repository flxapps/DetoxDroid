package com.flx_apps.digitaldetox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Room entity storing the total daily grayscale time (global, not per-app). */
@Entity(tableName = "daily_grayscale_stats")
data class DailyGrayscaleStats(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "grayscale_time_ms") val grayscaleTimeMs: Long
)
