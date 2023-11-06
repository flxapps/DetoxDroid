package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.time.Duration.Companion.minutes

/**
 * A tile for the grayscale apps feature settings screen.
 */
@Composable
fun GrayscaleAppsFeatureSettingsSection(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()
) {
    OpenAppExceptionsTile()
    OpenScheduleTile()
    ExtraDimTile()
    IgnoreFullScreenAppsTile()
    AllowedDailyColorScreenTimeTile()
}

/**
 * The UI element for toggling the extra dim setting.
 */
@Composable
fun ExtraDimTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_extraDim),
        subtitleText = stringResource(id = R.string.feature_grayscale_extraDim_description_description),
        trailing = {
            Checkbox(
                checked = viewModel.extraDimActivated.collectAsState().value,
                onCheckedChange = {
                    viewModel.toggleExtraDim()
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
    val showAllowedDailyColorScreenTimeDialog =
        viewModel.showAllowedDailyColorScreenTimeDialog.collectAsState().value
    val allowedDailyColorScreenTime =
        viewModel.allowedDailyColorScreenTime.collectAsState().value.toInt()
    if (showAllowedDailyColorScreenTimeDialog) {
        NumberPickerDialog(titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
            initialValue = allowedDailyColorScreenTime,
            onValueSelected = { viewModel.setAllowedDailyColorScreenTime(it.toLong()) },
            onDismissRequest = { viewModel.setShowAllowedDailyColorScreenTimeDialog(false) },
            range = 0..180 step 15,
            label = { it.minutes.toHrMinString() })
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
        subtitleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime_description),
        trailing = {
            Text(stringResource(id = R.string.time__minutes, allowedDailyColorScreenTime))
        },
        onClick = {
            viewModel.setShowAllowedDailyColorScreenTimeDialog(true)
        },
        leadingIcon = Icons.Default.ColorLens
    )
}