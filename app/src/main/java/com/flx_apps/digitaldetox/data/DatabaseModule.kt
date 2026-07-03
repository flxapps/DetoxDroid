package com.flx_apps.digitaldetox.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context, AppDatabase::class.java, "detoxdroid-stats"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
    }

    @Provides
    fun provideDailyAppUsageDao(database: AppDatabase): DailyAppUsageDao {
        return database.dailyAppUsageDao()
    }

    @Provides
    fun provideDailyGrayscaleStatsDao(database: AppDatabase): DailyGrayscaleStatsDao {
        return database.dailyGrayscaleStatsDao()
    }

    @Provides
    fun provideDailyDeviceStatsDao(database: AppDatabase): DailyDeviceStatsDao {
        return database.dailyDeviceStatsDao()
    }
}
