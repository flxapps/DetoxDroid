package com.flx_apps.digitaldetox.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.SizeF
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * One process-wide scope for all widget work instead of a fresh scope per broadcast. A widget that
 * fails to draw is logged rather than taking the app down with it.
 */
internal val widgetScope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Timber.e(e, "Widget update failed") }
)

/** Something a widget can show or leave out, picked per placed widget, see [WidgetSettingsStore]. */
interface WidgetPart {
    @get:StringRes
    val labelRes: Int
}

/** Runs [block] off the main thread, holding the broadcast open until it is done. */
internal fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    widgetScope.launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}

/**
 * Calls [onChange] on the main thread whenever the language or the display density changes, for
 * as long as the process lives. A widget keeps the text and the bitmaps it was given, and the host
 * only inflates the layout around them anew.
 */
internal fun Context.onLocaleOrDensityChanged(onChange: () -> Unit) {
    var current = Configuration(resources.configuration)
    registerComponentCallbacks(object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val changes = current.diff(newConfig)
            current = Configuration(newConfig)
            if (changes and (ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_DENSITY) != 0) {
                onChange()
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    })
}

/**
 * The size of the widget with [appWidgetId] in dp, as the launcher last reported it, or null while
 * it has not. Held upright, a widget is as wide as its minimum width and as tall as its maximum
 * height.
 */
fun AppWidgetManager.sizeDpOf(appWidgetId: Int): SizeF? {
    val options = getAppWidgetOptions(appWidgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else null
}

/** The widget a configure activity was opened for, or null when it came without one. */
fun Activity.configuredAppWidgetId(): Int? = intent?.extras
    ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

/** Tells the launcher that the widget with [appWidgetId] is set up and stays where it was put. */
fun Activity.setConfiguredResult(appWidgetId: Int) = setResult(
    Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
)
