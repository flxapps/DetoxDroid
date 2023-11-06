package com.flx_apps.digitaldetox.ui.screens.feature.pause_button

import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.PauseButtonFeature
import com.flx_apps.digitaldetox.system_integration.PauseInteractionService
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.KeyEventUtil

/**
 * The settings section for the pause button feature.
 */
@Composable
fun PauseButtonFeatureSettingsSection(
    viewModel: PauseButtonFeatureSettingsViewModel = viewModel()
) {
    // determine whether and which dialog should be shown
    when (viewModel.showPauseDurationNumberPickerDialog.collectAsState().value) {
        PauseButtonFeatureSettingsViewModelDialog.PAUSE_DURATION -> PauseDurationDialog()
        PauseButtonFeatureSettingsViewModelDialog.TIME_BETWEEN_PAUSES_DURATION -> TimeBetweenPausesDialog()
        PauseButtonFeatureSettingsViewModelDialog.PICK_HARDWARE_KEY -> PickHardwareKeyDialog()
        else -> {} // No dialog is shown
    }
    PauseDurationTile()
    MinimumTimeBetweenPausesTile()
    PauseFromAssistantTile()
    PauseFromHardwareButtonTile()
}

/**
 * A tile to launch the Android Assistant settings. DetoxDroid can be used as Assistant in order to
 * launch pauses.
 * @see PauseInteractionService
 */
@Composable
private fun PauseFromAssistantTile(viewModel: PauseButtonFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(leadingIcon = Icons.Default.Accessibility,
        titleText = stringResource(id = R.string.feature_pause_fromAssistant),
        subtitleText = stringResource(
            id = R.string.feature_pause_fromAssistant_description
        ),
        onClick = { viewModel.callAndroidAssistantSettings() })
}

/**
 * @see PauseButtonFeature.hardwareKey
 */
@Composable
private fun PauseFromHardwareButtonTile(viewModel: PauseButtonFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(leadingIcon = Icons.Default.AppShortcut,
        titleText = stringResource(id = R.string.feature_pause_fromHardwareButton),
        subtitleText = stringResource(
            id = R.string.feature_pause_fromHardwareButton_description
        ),
        trailing = viewModel.hardwareKey.collectAsState().value.takeIf {
            it != KeyEvent.KEYCODE_UNKNOWN
        }?.let {
            { Text(text = KeyEventUtil.keyCodeToShortString(it)) }
        } ?: {},
        onClick = { viewModel.showHardwareKeyDialog() })
}

/**
 * @see PauseButtonFeature.pauseDuration
 */
@Composable
private fun PauseDurationTile(viewModel: PauseButtonFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(leadingIcon = Icons.Default.AccessTime,
        titleText = stringResource(id = R.string.feature_pause_duration),
        subtitleText = stringResource(
            id = R.string.feature_pause_duration_description
        ),
        trailing = {
            Text(
                stringResource(
                    id = R.string.time__minutes, viewModel.pauseDuration.collectAsState().value
                )
            )
        },
        onClick = {
            viewModel.setVisibilityOfDialog(
                PauseButtonFeatureSettingsViewModelDialog.PAUSE_DURATION
            )
        })
}

/**
 * @see PauseButtonFeature.timeBetweenPausesDuration
 */
@Composable
private fun MinimumTimeBetweenPausesTile(viewModel: PauseButtonFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        leadingIcon = Icons.Default.Timelapse,
        titleText = stringResource(id = R.string.feature_pause_minimumTimeBetween),
        subtitleText = stringResource(
            id = R.string.feature_pause_minimumTimeBetween_description
        ),
        trailing = {
            Text(
                stringResource(
                    id = R.string.time__minutes,
                    viewModel.timeBetweenPausesDuration.collectAsState().value
                )
            )
        },
        onClick = {
            viewModel.setVisibilityOfDialog(
                PauseButtonFeatureSettingsViewModelDialog.TIME_BETWEEN_PAUSES_DURATION
            )
        },
    )
}

/**
 * The dialog for selecting the pause duration.
 */
@Composable
fun PauseDurationDialog(
    viewModel: PauseButtonFeatureSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    NumberPickerDialog(
        titleText = stringResource(id = R.string.feature_pause_duration),
        initialValue = viewModel.pauseDuration.collectAsState().value,
        onValueSelected = {
            viewModel.setPauseDuration(it)
        },
        onDismissRequest = {
            viewModel.setVisibilityOfDialog(PauseButtonFeatureSettingsViewModelDialog.NONE)
        },
        label = {
            context.getString(R.string.time__minutes, it)
        },
        range = 1..15
    )
}

/**
 * The dialog for selecting the time between pauses.
 */
@Composable
fun TimeBetweenPausesDialog(
    viewModel: PauseButtonFeatureSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    NumberPickerDialog(
        titleText = stringResource(id = R.string.feature_pause_minimumTimeBetween),
        initialValue = viewModel.timeBetweenPausesDuration.collectAsState().value,
        onValueSelected = {
            viewModel.setTimeBetweenPausesDuration(it)
        },
        onDismissRequest = {
            viewModel.setVisibilityOfDialog(PauseButtonFeatureSettingsViewModelDialog.NONE)
        },
        label = {
            context.getString(R.string.time__minutes, it)
        },
        range = 0..120
    )
}

@Composable
fun PickHardwareKeyDialog(
    viewModel: PauseButtonFeatureSettingsViewModel = viewModel()
) {
    val selectedKey = viewModel.newHardwareKeySelection.collectAsState().value
    val dialogText = selectedKey.takeIf { it != KeyEvent.KEYCODE_UNKNOWN }?.let {
        stringResource(
            id = R.string.feature_pause_fromHardwareButton_selected,
            KeyEventUtil.keyCodeToShortString(it)
        )
    } ?: stringResource(id = R.string.feature_pause_fromHardwareButton_noButtonPressed)
    AlertDialog(onDismissRequest = {
        viewModel.setVisibilityOfDialog(
            PauseButtonFeatureSettingsViewModelDialog.NONE
        )
    },
        title = { Text(text = stringResource(id = R.string.feature_pause_fromHardwareButton_press)) },
        text = { Text(text = dialogText) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.hideHardwareKeyDialog(selectedKey)
            }) {
                Text(text = stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.hideHardwareKeyDialog(KeyEvent.KEYCODE_UNKNOWN)
            }) {
                Text(text = stringResource(id = R.string.action_cancel))
            }
        })
}