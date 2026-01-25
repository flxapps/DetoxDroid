package com.flx_apps.digitaldetox.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flx_apps.digitaldetox.DetoxDroidApplication

/**
 * Helper object for checking notification permissions and settings.
 */
object NotificationHelper {

    /**
     * Checks if notification permission is granted.
     * On Android 13+, checks POST_NOTIFICATIONS permission.
     * On older versions, always returns true (no permission required).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on older versions
            true
        }
    }

    /**
     * Checks if notifications are fully enabled, including:
     * - Notification permission granted (Android 13+)
     * - App-level notifications enabled
     * - Notification channel not set to IMPORTANCE_NONE
     *
     * @return true if notifications will be shown, false otherwise
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        // First check permission
        if (!hasNotificationPermission(context)) {
            return false
        }

        // Check if notifications are enabled at the app level
        val notificationManager = NotificationManagerCompat.from(context)
        val areNotificationsEnabled = notificationManager.areNotificationsEnabled()

        // Check if the specific channel is enabled (API 26+)
        val isChannelEnabled =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.getNotificationChannel(
                DetoxDroidApplication.SERVICE_CHANNEL_ID
            )?.importance != NotificationManager.IMPORTANCE_NONE

        return areNotificationsEnabled && isChannelEnabled
    }

    /**
     * Opens the Android notification settings for the app's service channel.
     * On Android O+, opens the channel-specific settings.
     * On older versions, opens the app notification settings.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, DetoxDroidApplication.SERVICE_CHANNEL_ID)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Gets the POST_NOTIFICATIONS permission string for use with permission launchers.
     * Only relevant on Android 13+.
     */
    fun getNotificationPermission(): String {
        return Manifest.permission.POST_NOTIFICATIONS
    }
}

