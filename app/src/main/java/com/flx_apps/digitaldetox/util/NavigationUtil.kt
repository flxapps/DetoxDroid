package com.flx_apps.digitaldetox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object NavigationUtil {
    /**
     * Opens the settings screen for the draw overlay permission.
     */
    @JvmStatic
    fun openOverlayPermissionsSettings(context: Context) {
        context.startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }

    /**
     * Opens the settings screen for the do not disturb permission.
     */
    @JvmStatic
    fun openDoNotDisturbSystemSettings(context: Context) {
        context.startActivity(Intent(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }

    /**
     * Opens the settings screen for the usage stats permission.
     */
    @JvmStatic
    fun openUsageAccessSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Opens the accessibility services settings screen.
     */
    @JvmStatic
    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}