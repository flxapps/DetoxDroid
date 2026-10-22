package com.flx_apps.digitaldetox.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber
import java.util.Locale

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
        NavigationUtil.startFirstAvailable(
            context,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            NavigationUtil.appInfoSettingsIntent(context)
        )
    }

    /**
     * The vendors dontkillmyapp.com has a page for, by the page's path.
     */
    private val dontKillMyAppVendors = listOf(
        "asus", "blackview", "google", "hmd-global", "htc", "huawei", "lenovo", "meizu",
        "motorola", "nokia", "oneplus", "oppo", "realme", "samsung", "sony", "tecno", "ulefone",
        "unihertz", "vivo", "wiko", "xiaomi"
    )

    /**
     * Manufacturer names that share another vendor's page.
     */
    private val dontKillMyAppAliases =
        mapOf("honor" to "huawei", "redmi" to "xiaomi", "poco" to "xiaomi")

    /**
     * Opens dontkillmyapp.com, the community guide for turning off OEM-specific background killers,
     * on the page for this phone's manufacturer when there is one. The steps differ per vendor, and
     * the start page leaves the user to find theirs.
     */
    fun openDontKillMyAppGuide(context: Context) {
        val manufacturer =
            Build.MANUFACTURER.orEmpty().trim().lowercase(Locale.ROOT).replace(' ', '-')
        val vendor =
            dontKillMyAppAliases.entries.firstOrNull { manufacturer.startsWith(it.key) }?.value
                ?: dontKillMyAppVendors.firstOrNull { manufacturer.startsWith(it) }
        val url = "https://dontkillmyapp.com/${vendor.orEmpty()}"
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.w(it, "Could not open dontkillmyapp.com") }
    }
}
