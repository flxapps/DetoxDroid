package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import OptionsRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.theme.labelVerySmall
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import kotlin.time.Duration.Companion.milliseconds

/**
 * The settings section for the disable apps feature.
 */
@Composable
fun DisableAppsFeatureSettingsSection(viewModel: DisableAppsFeatureSettingsViewModel = viewModel()) {
    ManageDisabledAppsListTile()
    AllowedDailyTimeTile()
    OpenScheduleTile()
    OperationModeTile()
}

@Composable
fun AllowedDailyTimeTile(
    viewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val allowedDailyScreenTimeInMinutes = viewModel.allowedDailyTime.collectAsState().value
    val usedUpScreenTime = DisableAppsFeature.usedUpScreenTime.milliseconds.inWholeMinutes.toInt()
    if (viewModel.dailyScreenTimePickerDialogVisible.collectAsState().value) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime),
            label = { context.getString(R.string.time__minutes, it) },
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
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material.Text(
                    stringResource(
                        id = R.string.time__minutes, allowedDailyScreenTimeInMinutes
                    )
                )
                androidx.compose.material.Text(
                    modifier = Modifier.padding(top = 8.dp), text = stringResource(
                        id = R.string.time__minutes, usedUpScreenTime
                    ) + "\n" + stringResource(
                        id = R.string.time__minutes_used
                    ), style = MaterialTheme.typography.labelVerySmall, textAlign = TextAlign.Center
                )
            }
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
                        val modeSuccessfullyChanged =
                            disableAppsFeatureSettingsViewModel.changeOperationMode(selectedMode)
                        if (!modeSuccessfullyChanged && selectedMode == DisableAppsMode.DEACTIVATE) {
                            // we could not successfully change the operation mode to DEACTIVATE,
                            // so we show a snackbar that informs the user that they need to grant
                            // the device admin permission
                            featureViewModel.showSnackbar(message = context.getString(R.string.action_requestPermissions),
                                duration = SnackbarDuration.Short,
                                actionLabel = context.getString(R.string.action_go),
                                onResult = { snackbarResult ->
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        navViewModel.openRoute(
                                            NavigationRoutes.PermissionsRequired(
                                                context.getString(
                                                    R.string.rootCommand_grantDeviceAdminPermission
                                                )
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