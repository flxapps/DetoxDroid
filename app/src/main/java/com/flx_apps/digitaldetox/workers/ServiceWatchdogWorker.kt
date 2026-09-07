package com.flx_apps.digitaldetox.workers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.AccessibilityServiceController
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic health check for the accessibility service. If it is still enabled in secure settings
 * ([AccessibilityServiceController.isEnabledInSettings] - the signal the user flips when
 * starting/stopping DetoxDroid, which survives reboots and process kills) but no longer bound, the
 * process was killed unexpectedly: this tries a silent re-activate, and after two consecutive
 * failures notifies the user so they can re-enable it.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val shouldRun = AccessibilityServiceController.isEnabledInSettings(appContext)
        val isBound = DetoxDroidAccessibilityService.instance != null

        if (!shouldRun || isBound) {
            // Either the user turned DetoxDroid off, or it is healthy - reset outage tracking.
            resetOutageState()
            return Result.success()
        }

        Timber.w("ServiceWatchdogWorker: service enabled in settings but not bound")

        // Try a silent repair if we can write secure settings (adb / Shizuku granted the app
        // WRITE_SECURE_SETTINGS). activate() is idempotent and harmless when already enabled.
        if (hasWriteSecureSettings()) {
            runCatching { AccessibilityServiceController.activate(appContext) }
                .onFailure { Timber.w(it, "ServiceWatchdogWorker: silent repair failed") }
        }

        // Require two consecutive unhealthy checks before bothering the user, so we don't
        // false-alarm during the brief window while the OS is re-binding the service.
        val streak = incrementUnhealthyStreak()
        if (streak >= 2 && !wasOutageNotified()) {
            notifyServiceDown()
            setOutageNotified(true)
        }
        return Result.success()
    }

    private fun hasWriteSecureSettings(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled() and runCatching
    private fun notifyServiceDown() {
        if (!NotificationHelper.areNotificationsEnabled(appContext)) return
        val openIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification =
            NotificationCompat.Builder(appContext, DetoxDroidApplication.ALERT_CHANNEL_ID)
                .setContentTitle(appContext.getString(R.string.reliability_serviceDown_title))
                .setContentText(appContext.getString(R.string.reliability_serviceDown_message))
                .setSmallIcon(R.drawable.ic_pause)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
        runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(WATCHDOG_NOTIFICATION_ID, notification)
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun incrementUnhealthyStreak(): Int {
        val next = prefs().getInt(KEY_STREAK, 0) + 1
        prefs().edit().putInt(KEY_STREAK, next).apply()
        return next
    }

    private fun resetOutageState() {
        if (wasOutageNotified()) {
            NotificationManagerCompat.from(appContext).cancel(WATCHDOG_NOTIFICATION_ID)
        }
        prefs().edit().putInt(KEY_STREAK, 0).putBoolean(KEY_NOTIFIED, false).apply()
    }

    private fun wasOutageNotified() = prefs().getBoolean(KEY_NOTIFIED, false)

    private fun setOutageNotified(value: Boolean) =
        prefs().edit().putBoolean(KEY_NOTIFIED, value).apply()

    companion object {
        const val WORK_NAME = "service_watchdog"
        private const val WATCHDOG_NOTIFICATION_ID = 102
        private const val PREFS = "service_reliability_prefs"
        private const val KEY_STREAK = "unhealthy_streak"
        private const val KEY_NOTIFIED = "outage_notified"
    }
}
