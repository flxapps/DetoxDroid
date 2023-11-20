package com.flx_apps.digitaldetox.ui.screens.feature.do_not_disturb

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

@Composable
fun DoNotDisturbFeatureSettingsSection(
    viewModel: DoNotDisturbFeatureSettingsViewModel = viewModel()
) {
    OpenScheduleTile()
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_doNotDisturb_systemSettings),
        subtitleText = stringResource(id = R.string.feature_doNotDisturb_systemSettings_description),
        trailing = {
            Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null)
        },
        onClick = { viewModel.openDoNotDisturbSystemSettings() },
        leadingIcon = Icons.Default.Settings
    )
}