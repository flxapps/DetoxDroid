package com.flx_apps.digitaldetox.ui.screens.feature.disable_apps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.DisableAppsMode
import com.flx_apps.digitaldetox.rules.Step
import com.flx_apps.digitaldetox.system_integration.AppSuspender
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.feature.FeatureViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.permissions_required.GrantPermissionsCommand
import com.flx_apps.digitaldetox.ui.screens.rule_groups.colorRes
import com.flx_apps.digitaldetox.ui.screens.shizuku_setup.ShizukuSetupGoal
import com.flx_apps.digitaldetox.ui.theme.brandTone
import com.flx_apps.digitaldetox.ui.widgets.CardOption
import com.flx_apps.digitaldetox.ui.widgets.OptionCards
import com.flx_apps.digitaldetox.ui.widgets.SectionHeader
import com.flx_apps.digitaldetox.util.observeAsState

/**
 * The settings of the lock: what it does to an app once it takes hold. Which apps, when and for
 * how long is set on the groups, and the wait has nothing to set here.
 */
@Composable
fun DisableAppsFeatureSettingsSection() {
    SectionHeader(title = stringResource(id = R.string.feature_disableApps_operationMode))
    Spacer(modifier = Modifier.height(8.dp))
    OperationModeCards()
}

/**
 * The three ways of keeping a locked app away, one card each. A mode that still needs something
 * says so on its card, and picking it leads to that setup instead of quietly doing nothing.
 */
@Composable
fun OperationModeCards(
    featureViewModel: FeatureViewModel = viewModel(),
    disableAppsFeatureSettingsViewModel: DisableAppsFeatureSettingsViewModel = viewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
) {
    val context = LocalContext.current
    // the Shizuku setup comes back to this screen, so what the modes need is re-read on resume
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val canSuspend = remember(lifecycleState) { AppSuspender.isAvailable }
    val canDeactivate = remember(lifecycleState) { DetoxDroidDeviceAdminReceiver.isGranted(context) }
    val options = listOf(
        CardOption(
            value = DisableAppsMode.BLOCK,
            icon = Icons.Default.Block,
            title = stringResource(id = R.string.feature_disableApps_operationMode_block),
            description = stringResource(id = R.string.feature_disableApps_operationMode_block_description),
        ),
        CardOption(
            value = DisableAppsMode.SUSPEND,
            icon = Icons.Default.AcUnit,
            title = stringResource(id = R.string.feature_disableApps_operationMode_suspend),
            description = stringResource(id = R.string.feature_disableApps_operationMode_suspend_description),
            note = stringResource(id = R.string.feature_disableApps_operationMode_suspend_requirement)
                .takeUnless { canSuspend },
        ),
        CardOption(
            value = DisableAppsMode.DEACTIVATE,
            icon = Icons.Default.VisibilityOff,
            title = stringResource(id = R.string.feature_disableApps_operationMode_deactivate),
            description = stringResource(id = R.string.feature_disableApps_operationMode_deactivate_description),
            note = stringResource(id = R.string.feature_disableApps_operationMode_deactivate_requirement)
                .takeUnless { canDeactivate },
        ),
    )
    OptionCards(
        options = options,
        selected = disableAppsFeatureSettingsViewModel.operationMode.collectAsState().value,
        tone = brandTone(Step.LOCK.colorRes),
        onSelect = { mode ->
            if (disableAppsFeatureSettingsViewModel.changeOperationMode(mode)) return@OptionCards
            // the mode is not available yet, so point at what it is waiting for
            val route = when (mode) {
                DisableAppsMode.DEACTIVATE -> NavigationRoutes.PermissionsRequired(
                    GrantPermissionsCommand(
                        command = context.getString(R.string.rootCommand_grantDeviceAdminPermission),
                        supportsShizuku = false
                    ),
                )

                DisableAppsMode.SUSPEND -> NavigationRoutes.ShizukuSetup(ShizukuSetupGoal.SUSPEND)
                DisableAppsMode.BLOCK -> return@OptionCards
            }
            featureViewModel.showSnackbar(
                message = context.getString(
                    if (mode == DisableAppsMode.SUSPEND) {
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
