package com.flx_apps.digitaldetox.widgets.today

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.system_integration.TodaySummary
import com.flx_apps.digitaldetox.ui.theme.AppearanceSettings.ThemeMode
import com.flx_apps.digitaldetox.widgets.WidgetSettingsStore
import com.flx_apps.digitaldetox.widgets.WidgetStyle
import com.flx_apps.digitaldetox.widgets.launchAsync
import com.flx_apps.digitaldetox.widgets.sizeDpOf

/**
 * Today's screen time, unlocks and scroll distance, on the home screen and in the widget area of
 * the lock screen where the phone has one. What it shows grows with its size, see
 * [TodayWidgetTier]. Its look, and which of the [TodayWidgetPart]s it shows, is set per widget in
 * [TodayWidgetConfigureActivity].
 *
 * While DetoxDroid runs, [ScreenTimeSurfaces] keeps it current. The host's own periodic update
 * covers the time it doesn't, and the first update after the widget is placed.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) = launchAsync {
        ScreenTimeSurfaces.refresh(context.applicationContext)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        // from Android 12 the host picks the layout for the new size by itself
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        launchAsync {
            ScreenTimeSurfaces.redraw(context.applicationContext, intArrayOf(appWidgetId))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = launchAsync {
        WidgetSettingsStore.delete(appWidgetIds)
    }

    companion object {
        /** The launcher's own widgets mostly look like this: a solid card that goes dark at night. */
        val defaultStyle = WidgetStyle(ThemeMode.SYSTEM, backgroundOpacity = 100)

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))

        /** What a widget shows where nothing was picked for it: everything. */
        val defaultParts = TodayWidgetPart.all

        suspend fun loadParts(appWidgetId: Int): Set<TodayWidgetPart> =
            WidgetSettingsStore.loadParts(appWidgetId, TodayWidgetPart.entries, defaultParts)

        /**
         * Shows [summary] in the widgets with [appWidgetIds], each in its own style and with its
         * own parts. Loads app icons: not on main. Called through [ScreenTimeSurfaces], which
         * keeps updates in order.
         */
        suspend fun update(context: Context, appWidgetIds: IntArray, summary: TodaySummary) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = TodayWidgetViews(
                context, summary, DetoxDroidAccessibilityService.instance != null
            )
            val idsBySetup = appWidgetIds.groupBy {
                WidgetSettingsStore.loadStyle(it, defaultStyle) to loadParts(it)
            }
            for ((setup, ids) in idsBySetup) {
                val (style, parts) = setup
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appWidgetManager.updateAppWidget(ids.toIntArray(), views.responsive(style, parts))
                    continue
                }
                ids.forEach { appWidgetId ->
                    val tier = appWidgetManager.sizeDpOf(appWidgetId)
                        ?.let { TodayWidgetTier.pick(it.width, it.height) } ?: TodayWidgetTier.Tiny
                    appWidgetManager.updateAppWidget(appWidgetId, views.build(tier, style, parts))
                }
            }
        }
    }
}
