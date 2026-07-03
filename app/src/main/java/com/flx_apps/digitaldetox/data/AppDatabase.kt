package com.flx_apps.digitaldetox.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyAppUsage::class, DailyGrayscaleStats::class, DailyDeviceStats::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(LocalDbTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun dailyGrayscaleStatsDao(): DailyGrayscaleStatsDao
    abstract fun dailyDeviceStatsDao(): DailyDeviceStatsDao

    companion object {
        /** v2 adds the [DailyDeviceStats] table (persisted unlock counts and hour histograms). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_device_stats` (" +
                        "`date` TEXT NOT NULL, " +
                        "`unlockCount` INTEGER NOT NULL, " +
                        "`unlockHourBuckets` TEXT NOT NULL, " +
                        "`launchHourBuckets` TEXT NOT NULL, " +
                        "PRIMARY KEY(`date`))"
                )
            }
        }

        /** v3 adds the scroll-odometer column ([DailyAppUsage.scrollDistancePx]). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `daily_app_usage` " +
                        "ADD COLUMN `scrollDistancePx` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
