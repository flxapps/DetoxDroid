package com.flx_apps.digitaldetox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import timber.log.Timber

object NavigationUtil {
    /**
     * Starts the first of [intents] that has an activity to handle it. Settings pages are not
     * uniform across ROMs: a page that AOSP has can be missing on a vendor build, and a hidden
     * action may or may not be wired up, so a plain [Context.startActivity] can throw for a tap
     * that works everywhere else. The last intent should be one every device has.
     * @return whether any of them started
     */
    private fun startFirstAvailable(context: Context, vararg intents: Intent): Boolean {
        intents.forEach { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onSuccess { return true }
                .onFailure { Timber.w("no activity for ${intent.action}: ${it.message}") }
        }
        return false
    }

    /**
     * An intent that takes the user to the home screen. Carries [Intent.FLAG_ACTIVITY_NEW_TASK], so
     * it can be started from any context, including services.
     */
    @JvmStatic
    fun homeScreenIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Opens the settings screen for the draw overlay permission.
     */
    @JvmStatic
    fun openOverlayPermissionsSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            appInfoSettingsIntent(context)
        )
    }

    /**
     * Opens the settings screen for the do not disturb permission.
     */
    @JvmStatic
    fun openDoNotDisturbSystemSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    /**
     * Opens the page where Do Not Disturb itself is set up (what gets through, schedules). Its
     * action is hidden from the SDK and some vendor builds have no page behind it, so the
     * permission page and the sound settings, where Do Not Disturb lives, stand in for it.
     */
    @JvmStatic
    fun openDoNotDisturbSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS"),
            Intent("android.settings.ZEN_MODE_SETTINGS"),
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SOUND_SETTINGS)
        )
    }

    /**
     * Opens the settings screen for the usage stats permission.
     */
    @JvmStatic
    fun openUsageAccessSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    /**
     * Opens this app's entry in the system settings. That page carries the overflow menu with
     * "Allow restricted settings", which is the only way past a greyed-out special-access switch.
     */
    @JvmStatic
    fun openAppInfoSettings(context: Context) {
        startFirstAvailable(context, appInfoSettingsIntent(context), Intent(Settings.ACTION_SETTINGS))
    }

    private fun appInfoSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /**
     * Opens the accessibility services settings screen.
     */
    @JvmStatic
    fun openAccessibilitySettings(context: Context) {
        startFirstAvailable(
            context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS)
        )
    }

    /**
     * Opens the system's Extra Dim page, where its intensity is set. The page's action is hidden
     * from the SDK and some devices lack the page, so the accessibility settings, where Extra Dim
     * is listed, stand in for it there.
     */
    @JvmStatic
    fun openExtraDimSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent("android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    /**
     * Opens the developer options settings screen (e.g. for enabling wireless debugging).
     */
    @JvmStatic
    fun openDeveloperSettings(context: Context) {
        startFirstAvailable(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }

    /**
     * Opens the app's notification settings screen, e.g. after the runtime notification
     * permission has been permanently denied and can no longer be requested via a dialog.
     */
    @JvmStatic
    fun openAppNotificationSettings(context: Context) {
        startFirstAvailable(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
            appInfoSettingsIntent(context)
        )
    }
}