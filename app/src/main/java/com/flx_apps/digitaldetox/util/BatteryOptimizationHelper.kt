package com.flx_apps.digitaldetox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * Battery-optimization exemption helpers. Uses the settings-list intent
 * ([Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]), which needs no permission, rather than
 * the restricted direct-request intent.
 */
object BatteryOptimizationHelper {
    /**
     * Whether DetoxDroid is currently exempt from battery optimization (Doze / App Standby).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system's battery-optimization list so the user can exempt DetoxDroid manually.
     * Falls back to the app's details screen if the list activity is unavailable on this device.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            Timber.w(it, "Battery optimization settings unavailable, opening app details")
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * Opens dontkillmyapp.com, the community guide for turning off OEM-specific background killers.
     */
    fun openDontKillMyAppGuide(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.w(it, "Could not open dontkillmyapp.com") }
    }
}
