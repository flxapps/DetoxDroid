package com.flx_apps.digitaldetox.widgets

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStore
import com.flx_apps.digitaldetox.data.editLocked
import com.flx_apps.digitaldetox.ui.theme.AppearanceSettings.ThemeMode
import kotlinx.coroutines.flow.first

/**
 * How a widget looks: its theme, and how much of the wallpaper its background covers, in percent.
 * Each placed widget has its own, see [WidgetSettingsStore].
 */
data class WidgetStyle(val theme: ThemeMode, val backgroundOpacity: Int) {

    /**
     * [content] in the frame of this style. The frame carries the theme, which is where the
     * content's `?attr/widget…` colors come from, and the background, drawn at its opacity.
     */
    fun frame(context: Context, content: RemoteViews): RemoteViews =
        RemoteViews(context.packageName, frameLayout).apply {
            setImageViewResource(R.id.widget_frame_background, R.drawable.widget_background)
            setInt(R.id.widget_frame_background, "setImageAlpha", backgroundOpacity * 255 / 100)
            removeAllViews(R.id.widget_frame_content)
            addView(R.id.widget_frame_content, content)
        }

    @get:LayoutRes
    private val frameLayout: Int
        get() = when (theme) {
            ThemeMode.SYSTEM -> R.layout.widget_frame_system
            ThemeMode.LIGHT -> R.layout.widget_frame_light
            ThemeMode.DARK -> R.layout.widget_frame_dark
        }

    companion object {
        /** The opacity moves in steps this big; finer ones look the same on a wallpaper. */
        const val OPACITY_STEP = 10
    }
}

/**
 * What is set for every placed widget, by widget id: its [WidgetStyle], and which of its optional
 * parts it shows.
 */
object WidgetSettingsStore {
    /** A widget id means nothing on another phone, so settings files leave these keys out. */
    const val KEY_PREFIX = "widget_style_"

    /** The style picked for [appWidgetId], or [default] where nothing was picked yet. */
    suspend fun loadStyle(appWidgetId: Int, default: WidgetStyle): WidgetStyle {
        val preferences = DataStore.data.first()
        val theme = preferences[themeKey(appWidgetId)]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        }
        return WidgetStyle(
            theme = theme ?: default.theme,
            backgroundOpacity = preferences[opacityKey(appWidgetId)]?.coerceIn(0, 100)
                ?: default.backgroundOpacity
        )
    }

    suspend fun saveStyle(appWidgetId: Int, style: WidgetStyle) =
        DataStore.editLocked { preferences ->
            preferences[themeKey(appWidgetId)] = style.theme.name
            preferences[opacityKey(appWidgetId)] = style.backgroundOpacity
        }

    /** The parts out of [all] that [appWidgetId] shows, or [default] where nothing was picked yet. */
    suspend fun <P : Enum<P>> loadParts(appWidgetId: Int, all: List<P>, default: Set<P>): Set<P> {
        val names = DataStore.data.first()[partsKey(appWidgetId)] ?: return default
        return all.filterTo(mutableSetOf()) { it.name in names }
    }

    suspend fun saveParts(appWidgetId: Int, parts: Set<Enum<*>>) =
        DataStore.editLocked { preferences ->
            preferences[partsKey(appWidgetId)] = parts.mapTo(mutableSetOf()) { it.name }
        }

    suspend fun delete(appWidgetIds: IntArray) = DataStore.editLocked { preferences ->
        appWidgetIds.forEach {
            preferences.remove(themeKey(it))
            preferences.remove(opacityKey(it))
            preferences.remove(partsKey(it))
        }
    }

    private fun themeKey(appWidgetId: Int) =
        stringPreferencesKey("${KEY_PREFIX}theme_$appWidgetId")

    private fun opacityKey(appWidgetId: Int) =
        intPreferencesKey("${KEY_PREFIX}backgroundOpacity_$appWidgetId")

    private fun partsKey(appWidgetId: Int) =
        stringSetPreferencesKey("${KEY_PREFIX}parts_$appWidgetId")
}
