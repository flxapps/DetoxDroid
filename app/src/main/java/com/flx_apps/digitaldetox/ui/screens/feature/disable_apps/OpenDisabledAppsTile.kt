package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import androidx.compose.runtime.Composable
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.ManageAppExceptionsScreen
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile

/**
 * Represents the list tile that opens the [ManageAppExceptionsScreen] for the [DisableAppsFeature].
 */
@Composable
fun ManageDisabledAppsListTile() {
    OpenAppExceptionsTile(subtitleRes = R.string.feature_disableApps_manage_defined)
}