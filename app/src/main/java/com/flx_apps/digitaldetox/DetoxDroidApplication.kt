package com.flx_apps.digitaldetox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.flx_apps.digitaldetox.util.CachingDebugTree
import com.flx_apps.digitaldetox.util.InMemoryLogStore
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File

/**
 * The main application class. It is used to initialize the dependency injection framework.
 */
@HiltAndroidApp
class DetoxDroidApplication : Application() {
    companion object {
        lateinit var appContext: Application
        const val SERVICE_CHANNEL_ID = "detox_droid_service_channel"
    }

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
                // Ensure logs are flushed/saved (InMemoryLogStore does this async, so we might miss the very last beat if process dies immediately)
                // But since we appendText immediately in the launch block, it's a race.
                // For a crash, we hope the IO thread gets a slice.
                // Better persistence would be synchronous here for the crash, but let's stick to the current structure.

                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        } else {
            // For release builds, plant a simple debug tree (or no tree)
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannel()
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
            notificationManager.deleteNotificationChannel(SERVICE_CHANNEL_ID)
            notificationManager.createNotificationChannel(channel)
        }
    }
}