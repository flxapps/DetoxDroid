package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DoomScrollingSensitivity
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * The UI for the break doom scrolling feature settings section.
 */
@Composable
fun BreakDoomScrollingFeatureSettingsSection(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    OpenAppExceptionsTile(subtitleText = stringResource(
        id = if (BreakDoomScrollingFeature.appExceptionListType == AppExceptionListType.NOT_LIST) {
            R.string.feature_doomScrolling_exceptions_summary_notList
        } else {
            R.string.feature_doomScrolling_exceptions_summary_onlyList
        },
        BreakDoomScrollingFeature.appExceptions.size
    ))
    OpenScheduleTile()

    val timeUntilWarning = viewModel.timeUntilWarning.collectAsState().value
    if (viewModel.showTimeUntilWarningNumberPickerDialog.collectAsState().value) {
        val context = LocalContext.current
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_doomScrolling_timeUntilWarning),
            initialValue = timeUntilWarning,
            onValueSelected = {
                viewModel.setTimeUntilWarning(it)
            },
            onDismissRequest = {
                viewModel.setTimeUntilNumberPickerDialogVisible(false)
            },
            label = {
                context.getString(R.string.time__minutes, it)
            },
            range = 1..60
        )
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_doomScrolling_timeUntilWarning),
        subtitleText = stringResource(id = R.string.feature_doomScrolling_timeUntilWarning_description),
        trailing = {
            Text(
                text = stringResource(
                    id = R.string.time__minutes, viewModel.timeUntilWarning.collectAsState().value
                )
            )
        },
        onClick = {
            viewModel.setTimeUntilNumberPickerDialogVisible(true)
        },
        leadingIcon = Icons.Default.Timelapse
    )

    CooldownTimeTile()
    DetectionSensitivityTile()
}

/**
 * Lets the user choose how long an app (or the feed that caused the incident) stays locked after
 * a doom-scrolling break.
 */
@Composable
fun CooldownTimeTile(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    val cooldownTime = viewModel.cooldownTime.collectAsState().value
    if (viewModel.showCooldownTimeNumberPickerDialog.collectAsState().value) {
        val context = LocalContext.current
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime),
            initialValue = cooldownTime,
            onValueSelected = {
                viewModel.setCooldownTime(it)
            },
            onDismissRequest = {
                viewModel.setCooldownTimeNumberPickerDialogVisible(false)
            },
            label = {
                context.getString(R.string.time__minutes, it)
            },
            range = 1..60
        )
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime),
        subtitleText = stringResource(id = R.string.feature_doomScrolling_cooldownTime_description),
        trailing = {
            Text(text = stringResource(id = R.string.time__minutes, cooldownTime))
        },
        onClick = {
            viewModel.setCooldownTimeNumberPickerDialogVisible(true)
        },
        leadingIcon = Icons.Default.Lock
    )
}

/**
 * Lets the user choose how easily the scroll-intensity trigger fires (see
 * [DoomScrollingSensitivity]).
 */
@Composable
fun DetectionSensitivityTile(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    ListItem(
        leadingContent = {
            Icon(imageVector = Icons.Default.Tune, contentDescription = null)
        },
        headlineContent = { Text(text = stringResource(id = R.string.feature_doomScrolling_sensitivity)) },
        supportingContent = {
            Column {
                Text(text = stringResource(id = R.string.feature_doomScrolling_sensitivity_description))
                OptionsRow(
                    options = mapOf(
                        R.string.feature_doomScrolling_sensitivity_relaxed to DoomScrollingSensitivity.RELAXED,
                        R.string.feature_doomScrolling_sensitivity_balanced to DoomScrollingSensitivity.BALANCED,
                        R.string.feature_doomScrolling_sensitivity_strict to DoomScrollingSensitivity.STRICT,
                    ),
                    selectedOption = viewModel.detectionSensitivity.collectAsState().value,
                    onOptionSelected = {
                        viewModel.setDetectionSensitivity(it as DoomScrollingSensitivity)
                    },
                )
            }
        },
    )
}
