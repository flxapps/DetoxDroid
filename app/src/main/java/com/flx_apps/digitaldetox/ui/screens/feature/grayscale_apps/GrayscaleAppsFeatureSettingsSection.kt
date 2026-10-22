package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.MaxScreenFilterBlurDp
import com.flx_apps.digitaldetox.features.MinScreenFilterIntensity
import com.flx_apps.digitaldetox.features.ScreenFilterEffects
import com.flx_apps.digitaldetox.system_integration.ScreenFilterOverlay
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.shizuku_setup.ShizukuSetupGoal
import com.flx_apps.digitaldetox.ui.theme.isAppInDarkTheme
import com.flx_apps.digitaldetox.ui.widgets.AdvancedSettings
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SheetSetting
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.hasWriteSecureSettings
import kotlin.math.roundToInt

/**
 * The settings of the gray step: which filters make the apps gray and how strongly. Which apps,
 * when and after how much use is set on the groups.
 */
@Composable
fun GrayscaleAppsFeatureSettingsSection(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()
) {
    // The Shizuku wizard lives on this screen and comes back to it, and granting
    // WRITE_SECURE_SETTINGS moves the screen filter from "on" to "off" under the AUTO rule. Nothing
    // re-reads that on its own, so the view model would keep showing a filter that stopped running.
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val context = LocalContext.current
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.Event.ON_RESUME) viewModel.refreshFromFeature(context)
    }
    // grayscale and extra dim are secure settings, so without the permission they can only be
    // offered as something to set up (see the Shizuku tile), not as something to switch
    val canWriteSecureSettings = hasWriteSecureSettingsPermission()
    val systemGrayscale = viewModel.systemGrayscale.collectAsState().value
    val screenFilterEnabled = viewModel.screenFilterEnabled.collectAsState().value
    val effects = effectiveScreenFilterEffects(viewModel.screenFilterEffects.collectAsState().value)
    val extraDim = viewModel.extraDimActivated.collectAsState().value
    GrayPreview(
        grayscale = canWriteSecureSettings && systemGrayscale,
        shade = screenFilterEnabled && effects != ScreenFilterEffects.BLUR,
        blur = screenFilterEnabled && effects != ScreenFilterEffects.SHADE,
        intensity = viewModel.screenFilterIntensity.collectAsState().value,
        dim = canWriteSecureSettings && extraDim,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )
    if (!screenFilterEnabled && !(canWriteSecureSettings && (systemGrayscale || extraDim))) {
        // the two sides look the same, and the line says why
        Text(
            text = stringResource(
                id = if (canWriteSecureSettings) {
                    R.string.feature_grayscale_noEffects
                } else {
                    R.string.feature_grayscale_noEffects_noPermission
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
    SettingsGroup {
        ShizukuWizardTile()
        if (canWriteSecureSettings) SystemGrayscaleTile()
        ScreenFilterTile()
    }
    AdvancedSettings(
        summary = listOfNotNull(
            stringResource(id = R.string.feature_grayscale_extraDim)
                .takeIf { canWriteSecureSettings },
            stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen)
        ).joinToString(),
        initiallyExpanded = !viewModel.ignoreNonFullScreenApps.collectAsState().value ||
                (canWriteSecureSettings && !extraDim)
    ) {
        if (canWriteSecureSettings) ExtraDimTile()
        IgnoreFullScreenAppsTile()
    }
}

/**
 * Whether DetoxDroid may write secure settings, re-checked whenever the screen comes back so a
 * permission granted in the meantime (Shizuku wizard, adb) is picked up without a restart.
 */
@Composable
private fun hasWriteSecureSettingsPermission(): Boolean {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    return remember(lifecycleState) { context.hasWriteSecureSettings() }
}

/**
 * The UI element for toggling the system grayscale filter. Separate from the feature's own switch,
 * because the feature can just as well run on the screen filter alone.
 */
@Composable
fun SystemGrayscaleTile(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_systemGrayscale),
        subtitleText = stringResource(id = R.string.feature_grayscale_systemGrayscale_description),
        checked = viewModel.systemGrayscale.collectAsState().value,
        onCheckedChange = {
            if (viewModel.toggleSystemGrayscale()) featureViewModel.refreshActiveState()
        },
        leadingIcon = Icons.Default.InvertColors
    )
}

/**
 * The screen filter, which stands in for the system grayscale filter where that one cannot be
 * reached, in a single tile: the checkbox switches it, the subtitle sums up what it does, and
 * tapping the tile opens [ScreenFilterSheet] for the details.
 */
@Composable
fun ScreenFilterTile(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    val enabled = viewModel.screenFilterEnabled.collectAsState().value
    val intensity = viewModel.screenFilterIntensity.collectAsState().value
    val effects = viewModel.screenFilterEffects.collectAsState().value
    if (viewModel.showScreenFilterSheet.collectAsState().value) ScreenFilterSheet()
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_screenFilter),
        subtitleText = if (enabled) {
            stringResource(
                id = when (effectiveScreenFilterEffects(effects)) {
                    ScreenFilterEffects.SHADE -> R.string.feature_grayscale_screenFilter_summary_shade
                    ScreenFilterEffects.BLUR -> R.string.feature_grayscale_screenFilter_summary_blur
                    ScreenFilterEffects.SHADE_AND_BLUR -> R.string.feature_grayscale_screenFilter_summary_both
                }, intensity
            )
        } else {
            screenFilterDescription()
        },
        checked = enabled,
        onCheckedChange = {
            if (viewModel.toggleScreenFilter()) featureViewModel.refreshActiveState()
        },
        onClick = { viewModel.setShowScreenFilterSheet(true) },
        leadingIcon = Icons.Default.FilterAlt
    )
}

/**
 * Everything the screen filter can be told: on or off, what it does and how strongly. The
 * strength is a slider rather than a number to pick, since it is felt, not counted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenFilterSheet(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    val context = LocalContext.current
    val enabled = viewModel.screenFilterEnabled.collectAsState().value
    var intensity by remember { mutableFloatStateOf(viewModel.screenFilterIntensity.value.toFloat()) }
    ModalBottomSheet(
        onDismissRequest = { viewModel.setShowScreenFilterSheet(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.feature_grayscale_screenFilter)) },
                supportingContent = { Text(screenFilterDescription()) },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = {
                        if (viewModel.toggleScreenFilter()) featureViewModel.refreshActiveState()
                    })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            val canWriteSecureSettings = hasWriteSecureSettingsPermission()
            val effects = effectiveScreenFilterEffects(viewModel.screenFilterEffects.collectAsState().value)
            // follows the slider as it moves, so the strength is seen before it is set
            GrayPreview(
                grayscale = canWriteSecureSettings && viewModel.systemGrayscale.collectAsState().value,
                shade = enabled && effects != ScreenFilterEffects.BLUR,
                blur = enabled && effects != ScreenFilterEffects.SHADE,
                intensity = intensity.roundToInt(),
                dim = canWriteSecureSettings && viewModel.extraDimActivated.collectAsState().value,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            // a device that cannot blur has only one thing the filter can do, so there is nothing to pick
            if (ScreenFilterOverlay.isBlurAvailable()) {
                SheetSetting(
                    title = stringResource(id = R.string.feature_grayscale_screenFilter_effects),
                    description = stringResource(id = R.string.feature_grayscale_screenFilter_effects_description)
                ) {
                    OptionsRow(
                        options = mapOf(
                            R.string.feature_grayscale_screenFilter_effects_shade to ScreenFilterEffects.SHADE,
                            R.string.feature_grayscale_screenFilter_effects_blur to ScreenFilterEffects.BLUR,
                            R.string.feature_grayscale_screenFilter_effects_both to ScreenFilterEffects.SHADE_AND_BLUR,
                        ),
                        selectedOption = viewModel.screenFilterEffects.collectAsState().value,
                        onOptionSelected = { viewModel.setScreenFilterEffects(it as ScreenFilterEffects) },
                        enabled = enabled,
                    )
                }
            }
            SheetSetting(
                title = stringResource(id = R.string.feature_grayscale_screenFilter_intensity),
                description = stringResource(id = R.string.feature_grayscale_screenFilter_intensity_description),
                value = context.getString(
                    R.string.feature_grayscale_screenFilter_percent, intensity.roundToInt()
                )
            ) {
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = { viewModel.setScreenFilterIntensity(intensity.roundToInt()) },
                    valueRange = MinScreenFilterIntensity.toFloat()..100f,
                    steps = (100 - MinScreenFilterIntensity) / 10 - 1,
                    enabled = enabled,
                )
            }
        }
    }
}

/**
 * What the screen filter is, worded for whether the real grayscale filter is available as well.
 */
@Composable
private fun screenFilterDescription(): String = stringResource(
    id = if (hasWriteSecureSettingsPermission()) {
        R.string.feature_grayscale_screenFilter_description_granted
    } else {
        R.string.feature_grayscale_screenFilter_description
    }
)

/** The effects the screen filter really applies: on a device that cannot blur, only the shade. */
private fun effectiveScreenFilterEffects(effects: ScreenFilterEffects): ScreenFilterEffects =
    if (ScreenFilterOverlay.isBlurAvailable()) effects else ScreenFilterEffects.SHADE

/**
 * Entry into the guided Shizuku wizard, shown only while the WRITE_SECURE_SETTINGS permission is
 * missing — so the computer-free setup path is discoverable right where the feature lives, not
 * only from onboarding. Clickable even when settings are locked: granting a permission only makes
 * the feature work, it loosens nothing.
 */
@Composable
private fun ShizukuWizardTile(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    if (hasWriteSecureSettingsPermission()) return
    SimpleListTile(
        titleText = stringResource(id = R.string.noPermissions_shizukuWizard_go),
        subtitleText = stringResource(id = R.string.noPermissions_shizukuWizard),
        leadingIcon = Icons.Default.AutoFixHigh,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        allowClickWhenLocked = true,
        onClick = { navViewModel.openRoute(NavigationRoutes.ShizukuSetup(ShizukuSetupGoal.GRAYSCALE)) })
}

/**
 * The UI element for toggling the extra dim setting. How dark it gets is the system's to set, so a
 * long press takes the user to that setting.
 */
@Composable
fun ExtraDimTile(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    val context = LocalContext.current
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_extraDim),
        subtitleText = stringResource(id = R.string.feature_grayscale_extraDim_description),
        checked = viewModel.extraDimActivated.collectAsState().value,
        onCheckedChange = {
            if (viewModel.toggleExtraDim()) featureViewModel.refreshActiveState()
        },
        onLongClick = { NavigationUtil.openExtraDimSettings(context) },
        leadingIcon = Icons.Default.BrightnessLow
    )
}

/**
 * The UI element for toggling the ignore non full screen apps setting.
 */
@Composable
fun IgnoreFullScreenAppsTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen),
        subtitleText = stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen_description),
        checked = viewModel.ignoreNonFullScreenApps.collectAsState().value,
        onCheckedChange = { viewModel.toggleIgnoreNonFullScreenApps() },
        leadingIcon = Icons.Default.Fullscreen
    )
}

/**
 * Extra dim is the system's own setting and how dark it goes is not readable from here, so the
 * preview darkens by a fixed amount.
 */
private const val EXTRA_DIM_PREVIEW_ALPHA = 0.35f

/**
 * What the gray step does to an app, next to the same app untouched: the system filter drains the
 * color, the screen filter washes and blurs it, extra dim darkens it. Drawn from the settings as
 * they stand, with the same wash color, alpha and blur radius the filter itself uses.
 * @param intensity the screen filter strength in percent
 */
@Composable
internal fun GrayPreview(
    grayscale: Boolean,
    shade: Boolean,
    blur: Boolean,
    intensity: Int,
    dim: Boolean,
    modifier: Modifier = Modifier,
) {
    val strength = intensity.coerceIn(MinScreenFilterIntensity, 100) / 100f
    val wash = Color(
        if (isAppInDarkTheme()) ScreenFilterOverlay.WASH_COLOR_DARK else ScreenFilterOverlay.WASH_COLOR_LIGHT
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        PreviewScreen(
            label = stringResource(id = R.string.feature_grayscale_preview_normal),
            modifier = Modifier.weight(1f)
        ) {
            SampleFeed()
        }
        PreviewScreen(
            label = stringResource(id = R.string.feature_grayscale_preview_gray),
            modifier = Modifier.weight(1f)
        ) {
            Box {
                SampleFeed(
                    modifier = Modifier
                        .then(if (blur) Modifier.blur((strength * MaxScreenFilterBlurDp).dp) else Modifier)
                        .saturation(if (grayscale) 0f else 1f)
                )
                if (shade) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(wash.copy(alpha = strength * ScreenFilterOverlay.MAX_WASH_ALPHA))
                    )
                }
                if (dim) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = EXTRA_DIM_PREVIEW_ALPHA))
                    )
                }
            }
        }
    }
}

/** A small screen with [label] under it, one side of the [GrayPreview]. */
@Composable
private fun PreviewScreen(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A made-up feed in the brand colors: an avatar, a photo, a row of buttons, some text. */
@Composable
private fun SampleFeed(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface.copy(alpha = 0.55f)
    val faint = colors.onSurface.copy(alpha = 0.22f)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colorResource(id = R.color.blue))
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            ) {
                SampleLine(0.7f, ink)
                SampleLine(0.45f, faint)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorResource(id = R.color.pink),
                            colorResource(id = R.color.orange),
                            colorResource(id = R.color.yellow)
                        )
                    )
                )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.color.green, R.color.purple, R.color.pink).forEach { colorRes ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colorResource(id = colorRes))
                )
            }
        }
        SampleLine(1f, faint)
        SampleLine(0.8f, faint)
    }
}

@Composable
private fun SampleLine(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

/** Draws the content with its colors pulled towards gray, as the system filter does to the screen. */
private fun Modifier.saturation(saturation: Float): Modifier =
    if (saturation >= 1f) this else drawWithContent {
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
