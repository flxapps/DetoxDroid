package com.flx_apps.digitaldetox.system_integration

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty

/**
 * App-level reliability settings. Opt-in and off by default.
 */
object ReliabilitySettings {
    /**
     * When on, the accessibility service runs as a foreground service (ongoing notification) for its
     * whole lifetime, keeping its process at foreground priority so it is less likely to be killed.
     */
    var keepServiceAliveEnabled: Boolean by DataStoreProperty(
        booleanPreferencesKey("reliability_keepServiceAlive"), false
    )
}
