package com.flx_apps.digitaldetox.ui.screens.feature.delayed_loading

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DelayedAppLoadingFeature
import com.flx_apps.digitaldetox.features.DelayedAppLoadingFeatureId
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

@Composable
fun DelayedAppLoadingSettingsSection() {
    ManageDelayedAppsListTile()
    DelaySecondsTile()
    OpenScheduleTile()
}

@Composable
fun ManageDelayedAppsListTile(
    navViewModel: NavViewModel = viewModel()
) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_delayedLoading_manage_apps),
        leading = {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null
            )
        },
        onClick = {
            navViewModel.navigate(NavigationRoutes.AppExceptions(featureId = DelayedAppLoadingFeatureId))
        }
    )
}

@Composable
fun DelaySecondsTile() {
    val context = LocalContext.current
    var dialogVisible by remember { mutableStateOf(false) }
    var currentDelay by remember { mutableStateOf(DelayedAppLoadingFeature.delaySeconds) }

    if (dialogVisible) {
        NumberPickerDialog(
            titleText = stringResource(id = R.string.feature_delayedLoading_delay_duration),
            label = { context.getString(R.string.time_seconds, it) },
            initialValue = currentDelay,
            minValue = 3,
            maxValue = 60,
            onValueSelected = {
                currentDelay = it
                DelayedAppLoadingFeature.delaySeconds = it
                dialogVisible = false
            },
            onDismissRequest = {
                dialogVisible = false
            }
        )
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_delayedLoading_delay_duration),
        subtitleText = context.getString(R.string.time_seconds, currentDelay),
        leading = {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null
            )
        },
        onClick = {
            dialogVisible = true
        }
    )
}
