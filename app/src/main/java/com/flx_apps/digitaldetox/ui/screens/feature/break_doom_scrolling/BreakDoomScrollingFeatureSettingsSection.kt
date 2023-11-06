package com.flx_apps.digitaldetox.ui.screens.feature.break_doom_scrolling

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * The UI for the break doom scrolling feature settings section.
 */
@Composable
fun BreakDoomScrollingFeatureSettingsSection(
    viewModel: BreakDoomScrollingFeatureSettingsViewModel = viewModel()
) {
    OpenAppExceptionsTile()
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
}

