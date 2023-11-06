package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import ManageAppExceptionsScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile

/**
 * Represents the list tile that opens the [ManageAppExceptionsScreen] for the [DisableAppsFeature].
 */
@Composable
fun ManageDisabledAppsListTile() {
    OpenAppExceptionsTile(
        titleText = stringResource(id = R.string.feature_disableApps_manage),
        subtitleText = stringResource(
            id = R.string.feature_disableApps_manage__defined, DisableAppsFeature.appExceptions.size
        ),
    )
}