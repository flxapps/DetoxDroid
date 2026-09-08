package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
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
    if (viewModel.screenFilterEnabled.collectAsState().value) {
        // a device that cannot blur has only one thing the filter can do, so there is nothing to pick
        if (ScreenFilterOverlay.isBlurAvailable()) ScreenFilterEffectsTile()
        ScreenFilterIntensityTile()
    }
    IgnoreFullScreenAppsTile()
    AllowedDailyColorScreenTimeTile()
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
 * The UI element for toggling the screen filter, which stands in for the system grayscale filter
 * where that one cannot be reached.
 */
@Composable
fun ScreenFilterTile(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel(),
    featureViewModel: FeatureViewModel = viewModel()
) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_screenFilter),
        subtitleText = stringResource(
            id = if (hasWriteSecureSettingsPermission()) {
                R.string.feature_grayscale_screenFilter_description_granted
            } else {
                R.string.feature_grayscale_screenFilter_description
            }
        ),
        trailing = {
            Checkbox(checked = viewModel.screenFilterEnabled.collectAsState().value,
                onCheckedChange = {
                    if (viewModel.toggleScreenFilter()) featureViewModel.refreshActiveState()
                })
        },
        leadingIcon = Icons.Default.FilterAlt
    )
}

/**
 * The UI element for setting the screen filter strength.
 */
@Composable
fun ScreenFilterIntensityTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val intensity = viewModel.screenFilterIntensity.collectAsState().value
    if (viewModel.showScreenFilterIntensityDialog.collectAsState().value) {
        NumberPickerDialog(titleText = stringResource(id = R.string.feature_grayscale_screenFilter_intensity),
            initialValue = intensity,
            onValueSelected = { viewModel.setScreenFilterIntensity(it) },
            onDismissRequest = { viewModel.setShowScreenFilterIntensityDialog(false) },
            range = MinScreenFilterIntensity..100 step 10,
            label = { context.getString(R.string.feature_grayscale_screenFilter_percent, it) })
    }
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_screenFilter_intensity),
        subtitleText = stringResource(id = R.string.feature_grayscale_screenFilter_intensity_description),
        trailing = {
            Text(stringResource(id = R.string.feature_grayscale_screenFilter_percent, intensity))
        },
        onClick = { viewModel.setShowScreenFilterIntensityDialog(true) },
        leadingIcon = Icons.Default.Opacity
    )
}

/**
 * The UI element for choosing whether the screen filter shades, blurs, or does both.
 */
@Composable
fun ScreenFilterEffectsTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    ListItem(
        leadingContent = { Icon(imageVector = Icons.Default.BlurOn, contentDescription = null) },
        headlineContent = {
            Text(text = stringResource(id = R.string.feature_grayscale_screenFilter_effects))
        },
        supportingContent = {
            Column {
                Text(text = stringResource(id = R.string.feature_grayscale_screenFilter_effects_description))
                OptionsRow(
                    options = mapOf(
                        R.string.feature_grayscale_screenFilter_effects_shade to ScreenFilterEffects.SHADE,
                        R.string.feature_grayscale_screenFilter_effects_blur to ScreenFilterEffects.BLUR,
                        R.string.feature_grayscale_screenFilter_effects_both to ScreenFilterEffects.SHADE_AND_BLUR,
                    ),
                    selectedOption = viewModel.screenFilterEffects.collectAsState().value,
                    onOptionSelected = { viewModel.setScreenFilterEffects(it as ScreenFilterEffects) },
                )
            }
        },
    )
}

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
        subtitleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime_description),
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