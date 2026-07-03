package com.flx_apps.digitaldetox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.util.CachingDebugTree
import com.flx_apps.digitaldetox.util.InMemoryLogStore
import com.flx_apps.digitaldetox.widgets.minimal_launcher.MinimalLauncherWidgetProvider
import com.flx_apps.digitaldetox.workers.UsageStatsSnapshotWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The main application class. It is used to initialize the dependency injection framework.
 * Implements [Configuration.Provider] to support [androidx.hilt.work.HiltWorker] with Hilt.
 */
@HiltAndroidApp
class DetoxDroidApplication : Application(), Configuration.Provider {
    companion object {
        lateinit var appContext: Application
        const val SERVICE_CHANNEL_ID = "detox_droid_service_channel"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // Only enable caching debug tree for debug builds
        if (BuildConfig.DEBUG) {
            val logFile = File(filesDir, "app_logs.txt")
            InMemoryLogStore.init(logFile)
            Timber.plant(CachingDebugTree())

            // Hook into uncaught exceptions
            val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Timber.e(throwable, "Uncaught Exception on thread ${thread.name}")
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        } else {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannel()
        scheduleUsageStatsSnapshot()
        UsageStatsTracker.init(this)
        // Re-render all minimal launcher widgets on startup — after a process kill
        // (e.g. hot restart) the system may have reset them to the initial layout.
        MinimalLauncherWidgetProvider.updateAllWidgets(this)
    }

    private fun scheduleUsageStatsSnapshot() {
        val snapshotRequest = PeriodicWorkRequestBuilder<UsageStatsSnapshotWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_stats_snapshot",
            ExistingPeriodicWorkPolicy.KEEP,
            snapshotRequest
        )
    }

    /**
     * Creates the notification channel for the foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DetoxDroid Service"
            val descriptionText = "Keeps DetoxDroid active in the background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(SERVICE_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}