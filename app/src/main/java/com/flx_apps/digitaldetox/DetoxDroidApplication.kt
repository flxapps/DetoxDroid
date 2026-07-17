package com.flx_apps.digitaldetox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
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

        if (BuildConfig.DEBUG && isMainProcess()) {
            // file-backed log store only in the main process — a second process (see the
            // `:interactor` voice-interaction service) must not fight over the same log file
            val logFile = File(filesDir, "app_logs.txt")
            InMemoryLogStore.init(logFile)
            Timber.plant(CachingDebugTree())

            // Hook into uncaught exceptions
            val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Timber.e(throwable, "Uncaught Exception on thread ${thread.name}")
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        } else if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // release builds: keep warnings/errors in logcat, drop the verbose event chatter
            // (the accessibility service logs on every scroll/window event otherwise)
            Timber.plant(object : Timber.DebugTree() {
                override fun isLoggable(tag: String?, priority: Int): Boolean =
                    priority >= Log.WARN
            })
        }

        createNotificationChannel()

        // Everything below must only run in the main process. The `:interactor` process hosting
        // the voice-interaction shell would otherwise open DataStore/Room a second time (both are
        // single-process) and double-schedule WorkManager.
        if (!isMainProcess()) return

        scheduleUsageStatsSnapshot()
        UsageStatsTracker.init(this)
        // Re-render all minimal launcher widgets on startup — after a process kill
        // (e.g. hot restart) the system may have reset them to the initial layout.
        MinimalLauncherWidgetProvider.updateAllWidgets(this)
    }

    private fun isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val myPid = android.os.Process.myPid()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager)
                .runningAppProcesses?.firstOrNull { it.pid == myPid }?.processName
        }
        return processName == null || processName == packageName
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
            val name = getString(R.string.app_notification_channelName)
            val descriptionText = getString(R.string.app_notification_channelDescription)
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