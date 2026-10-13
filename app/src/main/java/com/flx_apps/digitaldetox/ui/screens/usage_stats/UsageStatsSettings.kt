package com.flx_apps.digitaldetox.ui.screens.usage_stats

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import com.flx_apps.digitaldetox.util.appCategory

/** User preferences of the usage stats screen (not tied to any detox feature). */
object UsageStatsSettings {
    /**
     * The user's self-declared value of one hour of their time, in whole units of the device's
     * local currency. 0 = not set — the life-cost reframing is strictly opt-in and stays
     * invisible until the user chooses a rate.
     */
    var hourlyRate: Int by DataStoreProperty(
        intPreferencesKey("UsageStatsScreen_hourlyRate"), 0
    )

    /**
     * Packages that are left out of every number on the usage screen. Screen time is a poor proxy
     * for attention: a sleep tracker that runs all night and a feed the user scrolled all night
     * look the same to the OS, and one of them is not what the screen is about.
     */
    var excludedApps: Set<String> by DataStoreProperty(
        stringSetPreferencesKey("UsageStatsScreen_excludedApps"), emptySet()
    )

    /**
     * Whole [ApplicationInfo.category] values left out, for the case where naming the apps one by
     * one is the wrong unit of work (every audio app, say).
     */
    var excludedCategories: Set<Int> by DataStoreProperty(
        stringSetPreferencesKey("UsageStatsScreen_excludedCategories"),
        emptySet(),
        DataStorePropertyTransformer.SetStorePropertyTransformer(
            { it.toString() }, { it.toIntOrNull() })
    )
}

/**
 * What [UsageStatsSettings] leaves out, read once, so that everything built from one reading
 * counts the same apps. The usage screen, the screen time widget and the notification all go
 * through this, which is what keeps their totals in agreement.
 */
class UsageExclusions(private val context: Context) {
    val apps: Set<String> = UsageStatsSettings.excludedApps
    val categories: Set<Int> = UsageStatsSettings.excludedCategories

    val isEmpty: Boolean get() = apps.isEmpty() && categories.isEmpty()

    fun excludes(packageName: String): Boolean = packageName in apps ||
            (categories.isNotEmpty() && context.appCategory(packageName) in categories)
}
