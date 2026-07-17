package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.flx_apps.digitaldetox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MinimalLauncherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateWidgetsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetsAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    MinimalLauncherWidgetConfigRepository.deleteSelectedPackagesAsync(appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    updateAppWidgetInternal(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** One process-wide scope for all widget work instead of a fresh scope per broadcast. */
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MinimalLauncherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            widgetScope.launch {
                appWidgetIds.forEach { appWidgetId ->
                    updateAppWidgetInternal(context, appWidgetManager, appWidgetId)
                }
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            widgetScope.launch {
                updateAppWidgetInternal(context, appWidgetManager, appWidgetId)
            }
        }

        private suspend fun updateAppWidgetInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val appContext = context.applicationContext
            val remoteViews = RemoteViews(appContext.packageName, R.layout.widget_minimal_launcher)
            val configureIntent = Intent(context, MinimalLauncherWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val configurePendingIntent = PendingIntent.getActivity(
                appContext,
                appWidgetId,
                configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            remoteViews.setOnClickPendingIntent(
                R.id.widget_minimal_launcher_empty,
                configurePendingIntent
            )

            val selectedApps = withContext(Dispatchers.IO) {
                MinimalLauncherWidgetConfigRepository.getSelectedAppsAsync(appWidgetId)
            }
            val launchableApps = withContext(Dispatchers.IO) {
                MinimalLauncherWidgetAppRepository.getLaunchableAppsByPackages(
                    appContext,
                    selectedApps.map { it.packageName }
                )
            }
            val labelOverridesByPackage = selectedApps.associateBy(
                keySelector = { it.packageName },
                valueTransform = { it.customLabel?.trim()?.ifBlank { null } }
            )
            remoteViews.removeAllViews(R.id.widget_minimal_launcher_items_container)

            if (launchableApps.isEmpty()) {
                remoteViews.setViewVisibility(R.id.widget_minimal_launcher_empty, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.widget_minimal_launcher_empty, View.GONE)
                val availableHeightDp = resolveWidgetHeightDp(appWidgetManager, appWidgetId)
                val textSizeSp = resolveTextSizeSp(availableHeightDp, launchableApps.size)
                launchableApps.forEachIndexed { index, app ->
                    val appItemViews =
                        RemoteViews(appContext.packageName, R.layout.widget_minimal_launcher_item).apply {
                            setTextViewText(
                                R.id.widget_minimal_launcher_item_text,
                                labelOverridesByPackage[app.packageName] ?: app.appName
                            )
                            setTextViewTextSize(
                                R.id.widget_minimal_launcher_item_text,
                                TypedValue.COMPLEX_UNIT_SP,
                                textSizeSp
                            )
                            val launchIntent =
                                Intent(context, MinimalLauncherWidgetClickReceiver::class.java).apply {
                                    action = MinimalLauncherWidgetClickReceiver.ACTION_LAUNCH_APP
                                    putExtra(
                                        MinimalLauncherWidgetClickReceiver.EXTRA_PACKAGE_NAME,
                                        app.packageName
                                    )
                                }
                            val launchPendingIntent = PendingIntent.getBroadcast(
                                appContext,
                                appWidgetId * 1000 + index,
                                launchIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            setOnClickPendingIntent(
                                R.id.widget_minimal_launcher_item_container,
                                launchPendingIntent
                            )
                        }
                    remoteViews.addView(R.id.widget_minimal_launcher_items_container, appItemViews)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        private fun resolveWidgetHeightDp(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Int {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return listOf(maxHeight, minHeight).filter { it > 0 }.maxOrNull() ?: 120
        }

        private fun resolveTextSizeSp(widgetHeightDp: Int, itemCount: Int): Float {
            val safeItemCount = itemCount.coerceAtLeast(1)
            val perRowHeightDp = widgetHeightDp.toFloat() / safeItemCount
            return (perRowHeightDp * 0.55f).coerceIn(14f, 42f)
        }
    }
}
