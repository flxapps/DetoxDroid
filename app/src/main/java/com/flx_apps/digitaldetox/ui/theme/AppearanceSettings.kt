package com.flx_apps.digitaldetox.ui.theme

import androidx.datastore.preferences.core.stringPreferencesKey
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.data.DataStorePropertyTransformer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level look and feel. Follows the system unless the user says otherwise.
 */
object AppearanceSettings {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private var persistedThemeMode: ThemeMode by DataStoreProperty(
        stringPreferencesKey("appearance_themeMode"),
        ThemeMode.SYSTEM,
        dataTransformer = DataStorePropertyTransformer.EnumStorePropertyTransformer(
            ThemeMode::class.java
        )
    )

    private val _themeMode = MutableStateFlow(persistedThemeMode)

    /** Observed by the theme, so a change applies to every open screen without a restart. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        persistedThemeMode = mode
        _themeMode.value = mode
    }
}
