package com.flx_apps.digitaldetox.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DailyAppUsage::class,
        DailyGrayscaleStats::class,
        DailyDeviceStats::class,
        SelfMeasuredAppUsage::class,
        DailyWebsiteUsage::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(LocalDbTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun dailyGrayscaleStatsDao(): DailyGrayscaleStatsDao
    abstract fun dailyDeviceStatsDao(): DailyDeviceStatsDao
    abstract fun selfMeasuredAppUsageDao(): SelfMeasuredAppUsageDao
    abstract fun dailyWebsiteUsageDao(): DailyWebsiteUsageDao

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

        /** v4 adds the [SelfMeasuredAppUsage] table (foreground time we measured ourselves). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_measured_app_usage` (" +
                        "`row_id` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`foregroundTimeMs` INTEGER NOT NULL, " +
                        "`launchCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`row_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_self_measured_app_usage_date` " +
                        "ON `self_measured_app_usage` (`date`)"
                )
            }
        }

        /** v5 adds the [DailyWebsiteUsage] table (time on websites, while it is counted). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_website_usage` (" +
                        "`row_id` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`site` TEXT NOT NULL, " +
                        "`timeMs` INTEGER NOT NULL, " +
                        "`visitCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`row_id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_website_usage_date` " +
                        "ON `daily_website_usage` (`date`)"
                )
            }
        }
    }
}
