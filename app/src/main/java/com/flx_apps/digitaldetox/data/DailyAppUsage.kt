package com.flx_apps.digitaldetox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Room entity representing a daily usage snapshot for a single app. */
@Entity(
    tableName = "daily_app_usage",
    indices = [Index(value = ["date"])]
)
data class DailyAppUsage(
    @PrimaryKey
    @ColumnInfo(name = "row_id")
    val rowId: String,
    val date: LocalDate,
    val packageName: String,
    val totalTimeMs: Long,
    val sessionCount: Int,
    val launchCount: Int,
    val scrollCount: Int,
    /**
     * Scrolled distance in screen pixels (sum of |scrollDeltaY|). Device pixels are converted to
     * physical distance at display time using the current screen's DPI.
     */
    @ColumnInfo(defaultValue = "0")
    val scrollDistancePx: Int = 0,
    val breakCount: Int,
    val blockCount: Int
) {
    companion object {
        fun createRowId(date: LocalDate, packageName: String): String = "${date}_$packageName"
    }
}