package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import OptionsRow
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * The settings section for the disable apps feature.
 */
@Composable
fun DisableAppsFeatureSettingsSection(viewModel: DisableAppsFeatureSettingsViewModel = viewModel()) {
    ManageDisabledAppsListTile()
    AllowedDailyTimeTile()
    OperationModeTile()
}

@Composable
fun AllowedDailyTimeTile(
    viewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
) {
    val allowedDailyScreenTimeInMinutes = viewModel.allowedDailyTime.collectAsState().value
    if (viewModel.dailyScreenTimePickerDialogVisible.collectAsState().value) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime),
            initialValue = allowedDailyScreenTimeInMinutes.toInt(),
            onValueSelected = {
                viewModel.setAllowedDailyScreenTime(it.toLong())
            },
            onDismissRequest = {
                viewModel.setShowDailyScreenTimePickerDialog(false)
            },
        )
    }
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime),
        subtitleText = stringResource(
            id = R.string.feature_disableApps_allowedDailyTime_description
        ),
        trailing = {
            Text(
                stringResource(
                    id = R.string.time__minutes, allowedDailyScreenTimeInMinutes
                )
            )
        },
        onClick = {
            viewModel.setShowDailyScreenTimePickerDialog(true)
        },
        leadingIcon = Icons.Default.Timelapse
    )
}

@Composable
fun OperationModeTile(
    featureViewModel: FeatureViewModel = viewModel(),
    disableAppsFeatureSettingsViewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val context = LocalContext.current
    androidx.compose.material3.ListItem(
        leadingContent = {
            Icon(imageVector = Icons.Default.Tune, contentDescription = null)
        },
        headlineContent = { Text(text = stringResource(id = R.string.feature_disableApps_operationMode)) },
        supportingContent = {
            Column {
                Text(text = stringResource(id = R.string.feature_disableApps_operationMode_description))
                val options = mapOf(
                    R.string.feature_disableApps_operationMode_block to DisableAppsMode.BLOCK,
                    R.string.feature_disableApps_operationMode_deactivate to DisableAppsMode.DEACTIVATE,
                )
                val selectedOption =
                    disableAppsFeatureSettingsViewModel.operationMode.collectAsState().value
                OptionsRow(
                    options = options,
                    selectedOption = selectedOption,
                    onOptionSelected = {
                        val selectedMode = it as DisableAppsMode
                        if (selectedMode == DisableAppsMode.DEACTIVATE && !disableAppsFeatureSettingsViewModel.changeOperationMode(
                                selectedMode
                            )
                        ) {
                            featureViewModel.showSnackbar(message = context.getString(R.string.action_requestPermissions),
                                duration = SnackbarDuration.Short,
                                actionLabel = context.getString(R.string.action_go),
                                onResult = { snackbarResult ->
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        navViewModel.openPermissionsRequiredScreen(
                                            context.getString(
                                                R.string.rootCommand_grantDeviceAdminPermission
                                            )
                                        )
                                    }
                                })
                        }
                    },
                )
            }
        },
    )
}