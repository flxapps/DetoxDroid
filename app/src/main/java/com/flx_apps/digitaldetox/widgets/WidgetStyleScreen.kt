package com.flx_apps.digitaldetox.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.ui.widgets.SheetSetting
import com.flx_apps.digitaldetox.ui.widgets.ThemeModeSelector
import com.flx_apps.digitaldetox.ui.widgets.labelRes
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Where a widget's [WidgetStyle] and the parts it shows are picked: the widget itself on top, on
 * the wallpaper (the activity's window shows it, see `Theme.DetoxDroid.WidgetConfigure`), and the
 * controls below.
 * @param parts what the widget can show or leave out, [shownParts] what it shows
 * @param preview the widget as it looks in [style], at [previewSize] in dp, or null while it is
 * not ready
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <P : WidgetPart> WidgetStyleScreen(
    style: WidgetStyle,
    onStyleChange: (WidgetStyle) -> Unit,
    parts: List<P>,
    shownParts: Set<P>,
    onShownPartsChange: (Set<P>) -> Unit,
    partsDescription: String,
    preview: RemoteViews?,
    previewSize: SizeF,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.widget_style_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // the controls scroll rather than squeeze the widget out of sight
            val maxControlsHeight = maxHeight * 0.6f
            Column(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        // laid out at its real size, so it picks the layout it really gets,
                        // and only drawn smaller where the screen has no room for it
                        val size = DpSize(previewSize.width.dp, previewSize.height.dp)
                        val scale = minOf(
                            1f, (maxWidth - 32.dp) / size.width, (maxHeight - 32.dp) / size.height
                        ).coerceAtLeast(0f)
                        RemoteViewsPreview(
                            views = preview,
                            modifier = Modifier
                                .requiredSize(size)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier.heightIn(max = maxControlsHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            WidgetPartsControl(
                                parts = parts,
                                shownParts = shownParts,
                                onShownPartsChange = onShownPartsChange,
                                description = partsDescription
                            )
                            WidgetStyleControls(style = style, onStyleChange = onStyleChange)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = onConfirm) { Text(confirmLabel) }
                        }
                    }
                }
            }
        }
    }
}

/** A chip for each of [parts], on for the ones in [shownParts]. */
@Composable
private fun <P : WidgetPart> WidgetPartsControl(
    parts: List<P>,
    shownParts: Set<P>,
    onShownPartsChange: (Set<P>) -> Unit,
    description: String,
) {
    SheetSetting(
        title = stringResource(id = R.string.widget_parts_title),
        description = description
    ) {
        ChipFlowRow {
            parts.forEach { part ->
                val shown = part in shownParts
                FilterChip(
                    selected = shown,
                    onClick = {
                        onShownPartsChange(if (shown) shownParts - part else shownParts + part)
                    },
                    label = { Text(stringResource(id = part.labelRes)) },
                    leadingIcon = if (shown) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun WidgetStyleControls(style: WidgetStyle, onStyleChange: (WidgetStyle) -> Unit) {
    SheetSetting(
        title = stringResource(id = R.string.about_theme),
        description = stringResource(id = R.string.widget_style_theme_description)
    ) {
        ThemeModeSelector(
            mode = style.theme,
            onModeSelected = { onStyleChange(style.copy(theme = it)) }
        )
    }
    SheetSetting(
        title = stringResource(id = R.string.widget_style_background),
        description = stringResource(id = R.string.widget_style_background_description),
        value = stringResource(
            id = R.string.widget_style_background_percent, style.backgroundOpacity
        )
    ) {
        Slider(
            value = style.backgroundOpacity.toFloat(),
            onValueChange = { onStyleChange(style.copy(backgroundOpacity = it.roundToInt())) },
            valueRange = 0f..100f,
            steps = 100 / WidgetStyle.OPACITY_STEP - 1,
        )
    }
}

/**
 * "Dark, background 40 % · Time today": a [WidgetStyle] and the [shownParts] in one line, for
 * where they are not open for editing.
 */
@Composable
fun widgetStyleSummary(style: WidgetStyle, shownParts: List<WidgetPart>): String {
    val styleText = stringResource(
        id = R.string.widget_style_summary,
        stringResource(id = style.theme.labelRes),
        stringResource(id = R.string.widget_style_background_percent, style.backgroundOpacity)
    )
    val partLabels = shownParts.map { stringResource(id = it.labelRes) }
    return (listOf(styleText) + partLabels.joinToString(", ").ifEmpty { null })
        .filterNotNull().joinToString(" · ")
}

/**
 * [views] applied the way the launcher applies them. Taps don't reach them: a preview that opened
 * an app would be a surprise.
 */
@Composable
private fun RemoteViewsPreview(views: RemoteViews, modifier: Modifier = Modifier) {
    AndroidView(
        factory = ::InertFrame,
        update = { frame ->
            if (frame.tag === views) return@AndroidView
            frame.tag = views
            frame.removeAllViews()
            runCatching { views.apply(frame.context, frame) }
                .onSuccess { frame.addView(it) }
                .onFailure { Timber.w(it, "Could not show the widget preview") }
        },
        modifier = modifier
    )
}

private class InertFrame(context: Context) : FrameLayout(context) {
    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    override fun onInterceptTouchEvent(event: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = false
}
