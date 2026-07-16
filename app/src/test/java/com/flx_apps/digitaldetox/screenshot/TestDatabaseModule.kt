package com.flx_apps.digitaldetox.screenshot

import android.content.Context
import androidx.room.Room
import com.flx_apps.digitaldetox.data.AppDatabase
import com.flx_apps.digitaldetox.data.DailyAppUsageDao
import com.flx_apps.digitaldetox.data.DailyDeviceStatsDao
import com.flx_apps.digitaldetox.data.DailyGrayscaleStatsDao
import com.flx_apps.digitaldetox.data.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces the app's [DatabaseModule] with an in-memory Room database for screenshot tests, so the
 * Usage-Stats screen can be populated with deterministic seed data (see [ScreenshotSeed]) instead
 * of the empty on-device history.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideDailyAppUsageDao(db: AppDatabase): DailyAppUsageDao = db.dailyAppUsageDao()

    @Provides
    fun provideDailyGrayscaleStatsDao(db: AppDatabase): DailyGrayscaleStatsDao =
        db.dailyGrayscaleStatsDao()

    @Provides
    fun provideDailyDeviceStatsDao(db: AppDatabase): DailyDeviceStatsDao =
        db.dailyDeviceStatsDao()
}
