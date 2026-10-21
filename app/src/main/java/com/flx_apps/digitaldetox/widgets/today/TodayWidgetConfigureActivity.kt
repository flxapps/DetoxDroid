package com.flx_apps.digitaldetox.widgets.today

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.ScreenTimeSurfaces
import com.flx_apps.digitaldetox.system_integration.TodaySummary
import com.flx_apps.digitaldetox.ui.theme.DetoxDroidTheme
import com.flx_apps.digitaldetox.widgets.WidgetSettingsStore
import com.flx_apps.digitaldetox.widgets.WidgetStyle
import com.flx_apps.digitaldetox.widgets.WidgetStyleScreen
import com.flx_apps.digitaldetox.widgets.configuredAppWidgetId
import com.flx_apps.digitaldetox.widgets.setConfiguredResult
import com.flx_apps.digitaldetox.widgets.sizeDpOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Where the look of one screen time widget and the parts it shows are picked, with the widget
 * itself as the preview. The widget works without it: from Android 12 the launcher opens this only
 * when asked to, from the widget's settings.
 */
class TodayWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = configuredAppWidgetId() ?: return finish()
        // leaving without saving keeps the widget: before Android 12 this opens as the widget is
        // placed, and a cancel there would take it off the home screen again
        setConfiguredResult(appWidgetId)

        val previewSize = AppWidgetManager.getInstance(this).sizeDpOf(appWidgetId)
            ?: PREVIEW_SIZE_UNKNOWN
        val previewTier = TodayWidgetTier.pick(previewSize.width, previewSize.height)

        enableEdgeToEdge()
        setContent {
            DetoxDroidTheme {
                val saved by produceState<Pair<WidgetStyle, Set<TodayWidgetPart>>?>(
                    initialValue = null, appWidgetId
                ) {
                    value = WidgetSettingsStore.loadStyle(
                        appWidgetId, TodayWidgetProvider.defaultStyle
                    ) to TodayWidgetProvider.loadParts(appWidgetId)
                }
                val views by produceState<TodayWidgetViews?>(initialValue = null) {
                    // with nothing to read, the preview still shows the look, just without numbers
                    val summary = ScreenTimeSurfaces.widgetSummary(applicationContext)
                        ?: TodaySummary(LocalDate.now(), 0L, emptyList(), null, 0.0, emptyList(), 0L)
                    value = TodayWidgetViews(
                        this@TodayWidgetConfigureActivity,
                        summary,
                        DetoxDroidAccessibilityService.instance != null
                    )
                }
                saved?.let { (initialStyle, initialParts) ->
                    var style by remember { mutableStateOf(initialStyle) }
                    var parts by remember { mutableStateOf(initialParts) }
                    WidgetStyleScreen(
                        style = style,
                        onStyleChange = { style = it },
                        parts = TodayWidgetPart.entries,
                        shownParts = parts,
                        onShownPartsChange = { parts = it },
                        partsDescription = stringResource(R.string.widget_today_parts_description),
                        preview = remember(views, style, parts) {
                            views?.build(previewTier, style, parts)
                        },
                        previewSize = previewSize,
                        confirmLabel = stringResource(id = R.string.action_save),
                        onConfirm = { leave(appWidgetId, style to parts) },
                        onBack = { leave(appWidgetId, null) },
                    )
                }
            }
        }
    }

    /**
     * Saves the style and the parts in [picked], unless it is null, and leaves. The widget is drawn
     * either way: a launcher that opens this as the widget is placed may leave the first update
     * to it.
     */
    private fun leave(appWidgetId: Int, picked: Pair<WidgetStyle, Set<TodayWidgetPart>>?) {
        lifecycleScope.launch {
            if (picked != null) {
                WidgetSettingsStore.saveStyle(appWidgetId, picked.first)
                WidgetSettingsStore.saveParts(appWidgetId, picked.second)
            }
            ScreenTimeSurfaces.redraw(applicationContext, intArrayOf(appWidgetId))
            finish()
        }
    }

    private companion object {
        /** A row of four cells, the size the widget is placed at. */
        val PREVIEW_SIZE_UNKNOWN = SizeF(300f, 90f)
    }
}
