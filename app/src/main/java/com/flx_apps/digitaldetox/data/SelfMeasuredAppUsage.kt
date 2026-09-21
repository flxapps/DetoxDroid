package com.flx_apps.digitaldetox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A day of foreground time for one app as DetoxDroid measured it itself.
 *
 * Kept apart from [DailyAppUsage] on purpose: the two are measurements of the same thing by
 * different instruments, and merging them would produce a number that is neither.
 * @see com.flx_apps.digitaldetox.util.ForegroundTimeRecorder
 */
@Entity(
    tableName = "self_measured_app_usage",
    indices = [Index(value = ["date"])]
)
data class SelfMeasuredAppUsage(
    @PrimaryKey
    @ColumnInfo(name = "row_id")
    val rowId: String,
    val date: LocalDate,
    val packageName: String,
    val foregroundTimeMs: Long,
    val launchCount: Int
) {
    companion object {
        fun createRowId(date: LocalDate, packageName: String): String = "${date}_$packageName"
    }
}
