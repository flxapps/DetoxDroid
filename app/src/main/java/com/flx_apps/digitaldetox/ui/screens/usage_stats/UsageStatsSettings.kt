package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.datastore.preferences.core.intPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty

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
}
