package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.MinScreenFilterIntensity
import com.flx_apps.digitaldetox.features.ScreenFilterEffects
import com.flx_apps.digitaldetox.system_integration.ScreenFilterOverlay
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.theme.labelVerySmall
import com.flx_apps.digitaldetox.ui.widgets.AdvancedSettings
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A tile for the grayscale apps feature settings screen.
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
    SettingsGroup {
        ShizukuWizardTile()
        OpenAppExceptionsTile(subtitleText = stringResource(
            id = if (GrayscaleAppsFeature.appExceptionListType == AppExceptionListType.NOT_LIST) {
                R.string.feature_grayscale_exceptions_summary_notList
            } else {
                R.string.feature_grayscale_exceptions_summary_onlyList
            },
            GrayscaleAppsFeature.appExceptions.size
        ))
        OpenScheduleTile()
        // grayscale and extra dim are secure settings, so without the permission they can only be
        // offered as something to set up (see the Shizuku tile), not as something to switch
        if (hasWriteSecureSettingsPermission()) {
            SystemGrayscaleTile()
            ExtraDimTile()
        }
        ScreenFilterTile()
        AllowedDailyColorScreenTimeTile()
    }
    AdvancedSettings(
        summary = stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen),
        initiallyExpanded = !viewModel.ignoreNonFullScreenApps.collectAsState().value
    ) {
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
    return remember(lifecycleState) {
        context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }
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
        trailing = {
            Checkbox(checked = viewModel.systemGrayscale.collectAsState().value,
                onCheckedChange = {
                    if (viewModel.toggleSystemGrayscale()) featureViewModel.refreshActiveState()
                })
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
        trailing = {
            Checkbox(checked = enabled, onCheckedChange = {
                if (viewModel.toggleScreenFilter()) featureViewModel.refreshActiveState()
            })
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
    ModalBottomSheet(onDismissRequest = { viewModel.setShowScreenFilterSheet(false) }) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
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

/** One setting in [ScreenFilterSheet]: a title with an optional value, a line on it, a control. */
@Composable
private fun SheetSetting(
    title: String,
    description: String,
    value: String? = null,
    control: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            value?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        control()
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
        titleText = stringResource(id = R.string.noPermissions_text_shizukuWizard_go),
        subtitleText = stringResource(id = R.string.noPermissions_text_shizukuWizard),
        leadingIcon = Icons.Default.AutoFixHigh,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        allowClickWhenLocked = true,
        onClick = { navViewModel.openRoute(NavigationRoutes.ShizukuSetup) })
}

/**
 * The UI element for toggling the extra dim setting.
 */
@Composable
fun ExtraDimTile(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_extraDim),
        subtitleText = stringResource(id = R.string.feature_grayscale_extraDim_description),
        trailing = {
            Checkbox(checked = viewModel.extraDimActivated.collectAsState().value,
                onCheckedChange = {
                    if (viewModel.toggleExtraDim()) featureViewModel.refreshActiveState()
                })
        },
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
        trailing = {
            Checkbox(checked = viewModel.ignoreNonFullScreenApps.collectAsState().value,
                onCheckedChange = {
                    viewModel.toggleIgnoreNonFullScreenApps()
                })
        },
        leadingIcon = Icons.Default.Fullscreen
    )
}

/**
 * The UI element for setting the allowed daily color screen time. On click, a dialog will be shown
 * that allows the user to set the allowed daily color screen time.
 */
@Composable
fun AllowedDailyColorScreenTimeTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val showAllowedDailyColorScreenTimeDialog =
        viewModel.showAllowedDailyColorScreenTimeDialog.collectAsState().value
    // includes the still-running tracking session, so the display doesn't lag behind
    val usedUpScreenTime =
        GrayscaleAppsFeature.currentUsedUpScreenTime().milliseconds.inWholeMinutes.toInt()
    val allowedDailyColorScreenTime =
        viewModel.allowedDailyColorScreenTime.collectAsState().value.toInt()
    if (showAllowedDailyColorScreenTimeDialog) {
        NumberPickerDialog(titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
            initialValue = allowedDailyColorScreenTime,
            onValueSelected = { viewModel.setAllowedDailyColorScreenTime(it.toLong()) },
            onDismissRequest = { viewModel.setShowAllowedDailyColorScreenTimeDialog(false) },
            range = 0..180 step 5,
            label = { it.minutes.toHrMinString(context) })
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
        subtitleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime_description),
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.time_minutes, allowedDailyColorScreenTime))
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(
                        id = R.string.time_minutes, usedUpScreenTime
                    ) + "\n" + stringResource(
                        id = R.string.time_minutes_used
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.labelVerySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        onClick = { viewModel.setShowAllowedDailyColorScreenTimeDialog(true) },
        leadingIcon = Icons.Default.ColorLens
    )
}