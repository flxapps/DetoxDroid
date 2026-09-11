package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
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
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.ui.theme.labelVerySmall
import com.flx_apps.digitaldetox.ui.widgets.AdvancedSettings
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The settings section for the disable apps feature.
 */
@Composable
fun DisableAppsFeatureSettingsSection() {
    ManageDisabledAppsListTile()
    // the daily time comes first: with none at all there is nothing to wait for, and the wait
    // tile says so
    AllowedDailyTimeTile()
    WaitBeforeOpeningTile()
    OpenScheduleTile()
    // deactivating needs a device admin grant from a computer, so blocking is what nearly everyone
    // keeps
    AdvancedSettings(
        summary = stringResource(id = R.string.feature_disableApps_operationMode),
        initiallyExpanded = DisableAppsFeature.operationMode == DisableAppsMode.DEACTIVATE
    ) {
        OperationModeTile()
    }
}

/** The waits the picker offers, in seconds; 0 turns the wait off. */
private val WaitChoices = listOf(0, 5, 10, 15, 20, 30, 45, 60)

/** The daily budgets the picker offers, in minutes, and no limit at all at the end. */
private val DailyTimeChoices = (0..180 step 5) + DisableAppsFeature.NO_DAILY_LIMIT.toInt()

/**
 * Lets the user choose how long the listed apps make them wait before they open.
 * @see DisableAppsFeature.waitBeforeOpening
 */
@Composable
fun WaitBeforeOpeningTile(
    viewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val waitSeconds = viewModel.waitBeforeOpening.collectAsState().value
    // a daily limit of 0 locks the apps right away, so there is nothing left to wait for
    val appsCanOpen = viewModel.allowedDailyTime.collectAsState().value != 0L
    val label = { seconds: Int ->
        if (seconds == 0) {
            context.getString(R.string.feature_disableApps_waitBeforeOpening_off)
        } else {
            context.getString(R.string.duration_seconds_short, seconds)
        }
    }
    if (viewModel.waitPickerDialogVisible.collectAsState().value) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_disableApps_waitBeforeOpening),
            label = label,
            initialValue = waitSeconds,
            range = WaitChoices,
            onValueSelected = { viewModel.setWaitBeforeOpening(it) },
            onDismissRequest = { viewModel.setShowWaitPickerDialog(false) },
        )
    }
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_disableApps_waitBeforeOpening),
        subtitleText = stringResource(
            id = if (appsCanOpen) {
                R.string.feature_disableApps_waitBeforeOpening_description
            } else {
                R.string.feature_disableApps_waitBeforeOpening_lockedRightAway
            }
        ),
        trailing = { Text(text = label(waitSeconds)) },
        enabled = appsCanOpen,
        onClick = { viewModel.setShowWaitPickerDialog(true) },
        leadingIcon = Icons.Default.HourglassTop
    )
}

@Composable
fun AllowedDailyTimeTile(
    viewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val allowedDailyScreenTimeInMinutes = viewModel.allowedDailyTime.collectAsState().value.toInt()
    val hasDailyLimit = allowedDailyScreenTimeInMinutes != DisableAppsFeature.NO_DAILY_LIMIT.toInt()
    // includes the still-running tracking session, so the display doesn't lag behind
    val usedUpScreenTime =
        DisableAppsFeature.currentUsedUpScreenTime().milliseconds.inWholeMinutes.toInt()
    if (viewModel.dailyScreenTimePickerDialogVisible.collectAsState().value) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime),
            label = {
                if (it == DisableAppsFeature.NO_DAILY_LIMIT.toInt()) {
                    context.getString(R.string.feature_disableApps_allowedDailyTime_noLimit)
                } else {
                    it.minutes.toHrMinString(context)
                }
            },
            // a budget that is not one of the choices (the picker used to go in single minutes)
            // starts out on the nearest one
            initialValue = DailyTimeChoices.minBy { abs(it - allowedDailyScreenTimeInMinutes) },
            range = DailyTimeChoices,
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
                Text(
                    if (hasDailyLimit) {
                        stringResource(id = R.string.time_minutes, allowedDailyScreenTimeInMinutes)
                    } else {
                        stringResource(id = R.string.feature_disableApps_allowedDailyTime_noLimit)
                    }
                )
                Text(
                    modifier = Modifier.padding(top = 8.dp), text = stringResource(
                        id = R.string.time_minutes, usedUpScreenTime
                    ) + "\n" + stringResource(
                        id = R.string.time_minutes_used
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
                val hasDailyLimit = disableAppsFeatureSettingsViewModel.allowedDailyTime
                    .collectAsState().value != DisableAppsFeature.NO_DAILY_LIMIT
                OptionsRow(
                    options = options,
                    selectedOption = selectedOption,
                    // without a daily limit the apps are never disabled, so there is no mode to pick
                    enabled = hasDailyLimit,
                    onOptionSelected = {
                        val selectedMode = it as DisableAppsMode
                        val modeSuccessfullyChanged =
                            disableAppsFeatureSettingsViewModel.changeOperationMode(selectedMode)
                        if (!modeSuccessfullyChanged && selectedMode == DisableAppsMode.DEACTIVATE) {
                            // we could not successfully change the operation mode to DEACTIVATE,
                            // so we show a snackbar that informs the user that they need to grant
                            // the device admin permission
                            featureViewModel.showSnackbar(
                                message = context.getString(R.string.action_requestPermissions),
                                duration = SnackbarDuration.Short,
                                actionLabel = context.getString(R.string.action_go),
                                onResult = { snackbarResult ->
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        navViewModel.openRoute(
                                            NavigationRoutes.PermissionsRequired(
                                                GrantPermissionsCommand(
                                                    command = context.getString(R.string.rootCommand_grantDeviceAdminPermission),
                                                    supportsShizuku = false
                                                ),
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