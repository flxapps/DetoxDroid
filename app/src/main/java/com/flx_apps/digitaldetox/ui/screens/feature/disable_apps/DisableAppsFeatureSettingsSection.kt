package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
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
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup

/**
 * The settings of the lock: how a locked app is kept out of reach. Which apps, when and after how
 * much use is set on the groups.
 */
@Composable
fun DisableAppsFeatureSettingsSection() {
    SettingsGroup {
        OperationModeTile()
    }
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
                    R.string.feature_disableApps_operationMode_suspend to DisableAppsMode.SUSPEND,
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
                        if (modeSuccessfullyChanged) return@OptionsRow
                        // the mode is not available yet, so point at what it is waiting for
                        val route = when (selectedMode) {
                            DisableAppsMode.DEACTIVATE -> NavigationRoutes.PermissionsRequired(
                                GrantPermissionsCommand(
                                    command = context.getString(R.string.rootCommand_grantDeviceAdminPermission),
                                    supportsShizuku = false
                                ),
                            )

                            DisableAppsMode.SUSPEND -> NavigationRoutes.ShizukuSetup
                            DisableAppsMode.BLOCK -> return@OptionsRow
                        }
                        featureViewModel.showSnackbar(
                            message = context.getString(
                                if (selectedMode == DisableAppsMode.SUSPEND) {
                                    R.string.feature_disableApps_operationMode_suspend_needsShizuku
                                } else {
                                    R.string.action_requestPermissions
                                }
                            ),
                            duration = SnackbarDuration.Short,
                            actionLabel = context.getString(R.string.action_go),
                            onResult = { snackbarResult ->
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    navViewModel.openRoute(route)
                                }
                            })
                    },
                )
            }
        },
    )
}
