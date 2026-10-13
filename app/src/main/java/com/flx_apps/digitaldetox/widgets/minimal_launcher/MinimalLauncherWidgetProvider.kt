package com.flx_apps.digitaldetox.widgets.minimal_launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.ui.theme.AppearanceSettings.ThemeMode
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.widgets.WidgetPart
import com.flx_apps.digitaldetox.widgets.WidgetSettingsStore
import com.flx_apps.digitaldetox.widgets.WidgetStyle
import com.flx_apps.digitaldetox.widgets.launchAsync
import com.flx_apps.digitaldetox.widgets.onLocaleOrDensityChanged
import com.flx_apps.digitaldetox.widgets.sizeDpOf
import com.flx_apps.digitaldetox.widgets.widgetScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * One line of the launcher widget: the app it opens, and the name it goes by there.
 * @param usedMs the time spent in the app today, where the widget shows it, null where not
 * @param blocked whether the app is blocked right now, where the widget dims those
 */
data class MinimalLauncherWidgetItem(
    val packageName: String,
    val label: String,
    val usedMs: Long? = null,
    val blocked: Boolean = false,
)

/** What a launcher widget can show next to the app names. Each widget picks its own. */
enum class MinimalLauncherWidgetPart(@StringRes override val labelRes: Int) : WidgetPart {
    TimeToday(R.string.widget_minimalLauncher_part_timeToday),
    DimBlocked(R.string.widget_minimalLauncher_part_dimBlocked),
}

class MinimalLauncherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) = launchAsync { update(context, appWidgetIds) }

    // the text size follows the height
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) = launchAsync { update(context, intArrayOf(appWidgetId)) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = launchAsync {
        appWidgetIds.forEach { appWidgetId ->
            MinimalLauncherWidgetConfigRepository.deleteSelectedPackagesAsync(appWidgetId)
        }
        WidgetSettingsStore.delete(appWidgetIds)
    }

    companion object {
        /** The names alone, light, straight on the wallpaper, like the launcher's own labels. */
        val defaultStyle = WidgetStyle(ThemeMode.DARK, backgroundOpacity = 0)

        /** Size assumed while the launcher has not reported the widget's size yet: 2×2. */
        val UNKNOWN_SIZE_DP = SizeF(180f, 120f)

        /** Less than a minute in an app shows no time: "0 min" says nothing. */
        private const val MINUTE_MS = 60_000L

        /** Names get no smaller than this to make room for their times. */
        private const val MIN_TEXT_SP = 14f

        /**
         * What the paddings of the widget and of a line take off its width, with a little to
         * spare for a launcher whose idea of the width is not quite ours.
         */
        private const val LINE_INSET_DP = 36f

        /** Between a name and its time, as in the layout. */
        private const val TIME_GAP_DP = 6f

        /**
         * Draws the widgets for this process, and again for as long as it lives whenever an app
         * is installed, removed or changed (a line must not open an app that is gone) or the
         * language changes (the lines carry the apps' names).
         */
        fun start(context: Context) {
            val appContext = context.applicationContext
            updateAll(appContext)
            appContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = updateAll(appContext)
            }, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            appContext.onLocaleOrDensityChanged { updateAll(appContext) }
        }

        fun updateAll(context: Context) {
            widgetScope.launch { update(context, widgetIds(context)) }
        }

        private fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, MinimalLauncherWidgetProvider::class.java))

        /** The parts picked for [appWidgetId]; none until some are. */
        suspend fun loadParts(appWidgetId: Int): Set<MinimalLauncherWidgetPart> =
            WidgetSettingsStore.loadParts(
                appWidgetId, MinimalLauncherWidgetPart.entries, emptySet()
            )

        /**
         * The widgets that show something which changes as the phone is used, the time in each
         * app or what is blocked. [ScreenTimeSurfaces] draws them along with the screen time.
         */
        suspend fun liveWidgetIds(context: Context): IntArray =
            widgetIds(context).filter { loadParts(it).isNotEmpty() }.toIntArray()

        /**
         * Updates run one at a time, each reading what it shows once the one before is done.
         * Side by side, one that read the apps before an uninstall could post after one that read
         * them after it, and bring the removed app's line back.
         */
        private val updateLock = Mutex()

        /** Draws the widgets with [appWidgetIds] from what is saved for them. */
        suspend fun update(context: Context, appWidgetIds: IntArray) =
            withContext(Dispatchers.Default) {
                val appContext = context.applicationContext
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                updateLock.withLock {
                    appWidgetIds.forEach { appWidgetId ->
                        // one widget that can't be drawn must not keep the others from it
                        runCatching {
                            appWidgetManager.updateAppWidget(
                                appWidgetId, savedViews(appContext, appWidgetManager, appWidgetId)
                            )
                        }.onFailure {
                            Timber.w(it, "Could not update launcher widget %d", appWidgetId)
                        }
                    }
                }
            }

        private suspend fun savedViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): RemoteViews {
            val selectedApps = withContext(Dispatchers.IO) {
                MinimalLauncherWidgetConfigRepository.getSelectedAppsAsync(appWidgetId)
            }
            val launchableApps = withContext(Dispatchers.IO) {
                MinimalLauncherWidgetAppRepository.getLaunchableAppsByPackages(
                    context,
                    selectedApps.map { it.packageName }
                )
            }
            val labelOverridesByPackage = selectedApps.associateBy(
                keySelector = { it.packageName },
                valueTransform = { it.customLabel?.trim()?.ifBlank { null } }
            )
            val parts = loadParts(appWidgetId)
            val usedMs = if (MinimalLauncherWidgetPart.TimeToday in parts) {
                ScreenTimeSurfaces.current(context)?.screenTimeByPackage.orEmpty()
            } else emptyMap()
            val blocked = if (MinimalLauncherWidgetPart.DimBlocked in parts) {
                DisableAppsFeature.blockedApps()
            } else emptySet()
            val items = launchableApps.map { app ->
                MinimalLauncherWidgetItem(
                    packageName = app.packageName,
                    label = labelOverridesByPackage[app.packageName] ?: app.appName,
                    usedMs = if (MinimalLauncherWidgetPart.TimeToday in parts) {
                        usedMs[app.packageName] ?: 0L
                    } else null,
                    blocked = app.packageName in blocked,
                )
            }
            val sizeDp = appWidgetManager.sizeDpOf(appWidgetId) ?: UNKNOWN_SIZE_DP
            val style = WidgetSettingsStore.loadStyle(appWidgetId, defaultStyle)
            return buildViews(context, appWidgetId, items, sizeDp, style, interactive = true)
        }

        /**
         * The widget with [appWidgetId] showing [items] in [style], at a size of [sizeDp].
         * @param interactive whether taps on it do anything. The configure screen's preview passes
         * false: the intents behind the lines are kept by the system per widget and line, so a
         * preview that set them up would rewire the placed widget before anything was saved.
         */
        fun buildViews(
            context: Context,
            appWidgetId: Int,
            items: List<MinimalLauncherWidgetItem>,
            sizeDp: SizeF,
            style: WidgetStyle,
            interactive: Boolean,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_minimal_launcher)
            if (interactive) {
                val configureIntent =
                    Intent(context, MinimalLauncherWidgetConfigureActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                val configurePendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    configureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                remoteViews.setOnClickPendingIntent(
                    R.id.widget_minimal_launcher_empty,
                    configurePendingIntent
                )
            }
            remoteViews.removeAllViews(R.id.widget_minimal_launcher_items_container)

            if (items.isEmpty()) {
                remoteViews.setViewVisibility(R.id.widget_minimal_launcher_empty, View.VISIBLE)
            } else {
                remoteViews.setViewVisibility(R.id.widget_minimal_launcher_empty, View.GONE)
                val times = items.map { item ->
                    item.usedMs?.takeIf { it >= MINUTE_MS }?.milliseconds?.toHrMinString(context)
                }
                val heightSizeSp = (sizeDp.height / items.size * 0.55f).coerceIn(MIN_TEXT_SP, 42f)
                val lineWidthPx =
                    context.px(sizeDp.width - LINE_INSET_DP, TypedValue.COMPLEX_UNIT_DIP)
                // a time takes width from its line, and the name gives up size rather than letters
                val textSizeSp = if (times.any { it != null }) {
                    fittingTextSizeSp(
                        context, items.map { it.label }, times, lineWidthPx, heightSizeSp
                    )
                } else heightSizeSp
                val measure = LineMeasure(context, textSizeSp)
                // smaller names keep the spacing they had at full size, so the lines close up
                // around the middle rather than spread out
                remoteViews.setFloat(
                    R.id.widget_minimal_launcher_items_container,
                    "setWeightSum",
                    items.size * heightSizeSp / textSizeSp
                )
                items.forEachIndexed { index, item ->
                    val layout = if (item.blocked) R.layout.widget_minimal_launcher_item_dimmed
                    else R.layout.widget_minimal_launcher_item
                    val appItemViews = RemoteViews(context.packageName, layout).apply {
                        setTextViewText(R.id.widget_minimal_launcher_item_text, item.label)
                        setTextViewTextSize(
                            R.id.widget_minimal_launcher_item_text,
                            TypedValue.COMPLEX_UNIT_SP,
                            textSizeSp
                        )
                        bindTime(times[index], timeSizeSpFor(textSizeSp))
                        // a name too long even at the smallest size gives up letters, not its time
                        times[index]?.let { time ->
                            setInt(
                                R.id.widget_minimal_launcher_item_text,
                                "setMaxWidth",
                                (lineWidthPx - measure.timeWidthPx(time)).toInt().coerceAtLeast(0)
                            )
                        }
                        if (interactive) {
                            launchPendingIntent(context, appWidgetId, index, item)?.let {
                                setOnClickPendingIntent(
                                    R.id.widget_minimal_launcher_item_container, it
                                )
                            }
                        }
                    }
                    remoteViews.addView(R.id.widget_minimal_launcher_items_container, appItemViews)
                }
            }
            return style.frame(context, remoteViews)
        }

        /** The [time] at the end of the line, or none. */
        private fun RemoteViews.bindTime(time: String?, sizeSp: Float) {
            if (time != null) {
                setTextViewText(R.id.widget_minimal_launcher_item_time, time)
                setTextViewTextSize(
                    R.id.widget_minimal_launcher_item_time, TypedValue.COMPLEX_UNIT_SP, sizeSp
                )
            }
            setViewVisibility(
                R.id.widget_minimal_launcher_item_time,
                if (time != null) View.VISIBLE else View.GONE
            )
        }

        private fun timeSizeSpFor(textSizeSp: Float) = (textSizeSp * 0.5f).coerceIn(11f, 18f)

        /**
         * The largest text size up to [maxSp] at which each of [labels] fits next to its time in
         * a line [lineWidthPx] wide. [MIN_TEXT_SP] where none does.
         */
        private fun fittingTextSizeSp(
            context: Context,
            labels: List<String>,
            times: List<String?>,
            lineWidthPx: Float,
            maxSp: Float,
        ): Float {
            var sizeSp = maxSp
            while (sizeSp > MIN_TEXT_SP) {
                val measure = LineMeasure(context, sizeSp)
                val fits = labels.indices.all { i ->
                    val timePx = times[i]?.let(measure::timeWidthPx) ?: 0f
                    measure.nameWidthPx(labels[i]) + timePx <= lineWidthPx
                }
                if (fits) return sizeSp
                sizeSp -= 1f
            }
            return MIN_TEXT_SP
        }

        private fun Context.px(value: Float, unit: Int) =
            TypedValue.applyDimension(unit, value, resources.displayMetrics)

        /** Measures a line at [textSizeSp] in the fonts the widget draws it in. */
        private class LineMeasure(private val context: Context, textSizeSp: Float) {
            private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textSize = context.px(textSizeSp, TypedValue.COMPLEX_UNIT_SP)
            }
            private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = context.px(timeSizeSpFor(textSizeSp), TypedValue.COMPLEX_UNIT_SP)
            }

            fun nameWidthPx(label: String) = namePaint.measureText(label)

            /** The width a time takes up next to its name, the gap included. */
            fun timeWidthPx(time: String) =
                context.px(TIME_GAP_DP, TypedValue.COMPLEX_UNIT_DIP) + timePaint.measureText(time)
        }

        /**
         * Opens the app itself, the way the launcher's own icons do. A detour through a broadcast
         * that starts the app from here would need DetoxDroid to be allowed to start activities
         * from the background, which it is not on every phone and launcher.
         */
        private fun launchPendingIntent(
            context: Context,
            appWidgetId: Int,
            index: Int,
            item: MinimalLauncherWidgetItem
        ): PendingIntent? {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                ?: return null
            return PendingIntent.getActivity(
                context,
                appWidgetId * 1000 + index,
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    }
}
