package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import OptionsRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureScreenSnackbarStateProvider
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.theme.labelVerySmall
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A tile for the grayscale apps feature settings screen.
 */
@Composable
fun GrayscaleAppsFeatureSettingsSection(
    viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()
) {
    AppExceptionListTypeTile()
    OpenAppExceptionsTile()
    OpenScheduleTile()
    ExtraDimTile()
    IgnoreFullScreenAppsTile()
    AllowedDailyColorScreenTimeTile()
}

@Composable
fun AppExceptionListTypeTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    val listTypeMap = mapOf(
        R.string.feature_settings_exceptions_listType_notList to AppExceptionListType.NOT_LIST,
        R.string.feature_settings_exceptions_listType_onlyList to AppExceptionListType.ONLY_LIST
    )
    val selectedOption = viewModel.appExceptionListType.collectAsState().value

    androidx.compose.material3.ListItem(
        headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_listType)) },
        supportingContent = {
            Column {
                Text(text = stringResource(id = R.string.feature_settings_exceptions_listType_description))
                OptionsRow(
                    options = listTypeMap,
                    selectedOption = selectedOption,
                    onOptionSelected = { viewModel.setAppExceptionListType(it as AppExceptionListType) }
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_app_exceptions),
                contentDescription = null
            )
        }
    )
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
            Checkbox(checked = viewModel.extraDimActivated.collectAsState().value,
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
    val usedUpScreenTime = GrayscaleAppsFeature.usedUpScreenTime.milliseconds.inWholeMinutes.toInt()
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
        subtitleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime_description),
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.time__minutes, allowedDailyColorScreenTime))
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(
                        id = R.string.time__minutes, usedUpScreenTime
                    ) + "\n" + stringResource(
                        id = R.string.time__minutes_used
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.labelVerySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        onClick = {
            if (!viewModel.setShowAllowedDailyColorScreenTimeDialog(true)) {
                // the user has not given the permission to access usage stats, so we show a snackbar
                // to request the permission
                coroutineScope.launch {
                    val result = FeatureScreenSnackbarStateProvider.snackbarState.showSnackbar(
                        context.getString(R.string.action_requestPermissions),
                        context.getString(R.string.action_go)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        NavigationUtil.openUsageAccessSettings(context)
                    }
                }
            }
        },
        leadingIcon = Icons.Default.ColorLens
    )
}