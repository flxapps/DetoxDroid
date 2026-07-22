package com.flx_apps.digitaldetox.ui.screens.feature.grayscale_apps

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.ui.screens.feature.OpenAppExceptionsTile
import com.flx_apps.digitaldetox.ui.screens.feature.OpenScheduleTile
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.theme.labelVerySmall
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.util.observeAsState
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * A tile for the grayscale apps feature settings screen.
 */
@Composable
fun GrayscaleAppsFeatureSettingsSection() {
    ShizukuWizardTile()
    OpenAppExceptionsTile(subtitleText = stringResource(
        id = if (GrayscaleAppsFeature.appExceptionListType == AppExceptionListType.NOT_LIST) {
            R.string.feature_grayscale_exceptions_summary_notList
        } else {
            R.string.feature_grayscale_exceptions_summary_onlyList
        },
        GrayscaleAppsFeature.appExceptions.size
    ))
    OpenScheduleTile()
    ExtraDimTile()
    IgnoreFullScreenAppsTile()
    AllowedDailyColorScreenTimeTile()
}

/**
 * Entry into the guided Shizuku wizard, shown only while the WRITE_SECURE_SETTINGS permission is
 * missing — so the computer-free setup path is discoverable right where the feature lives, not
 * only from onboarding. Clickable even when settings are locked: granting a permission only makes
 * the feature work, it loosens nothing.
 */
@Composable
private fun ShizukuWizardTile(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    val context = LocalContext.current
    val lifecycleState = LocalLifecycleOwner.current.lifecycle.observeAsState().value
    val hasPermission = remember(lifecycleState) {
        context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }
    if (hasPermission) return
    SimpleListTile(
        titleText = stringResource(id = R.string.noPermissions_text_shizukuWizard_go),
        subtitleText = stringResource(id = R.string.noPermissions_text_shizukuWizard),
        leadingIcon = Icons.Default.AutoFixHigh,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        allowClickWhenLocked = true,
        onClick = { navViewModel.openRoute(NavigationRoutes.ShizukuSetup) })
}

/**
 * The UI element for toggling the extra dim setting.
 */
@Composable
fun ExtraDimTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_extraDim),
        subtitleText = stringResource(id = R.string.feature_grayscale_extraDim_description_description),
        trailing = {
            Checkbox(checked = viewModel.extraDimActivated.collectAsState().value,
                onCheckedChange = {
                    viewModel.toggleExtraDim()
                })
        },
        leadingIcon = Icons.Default.BrightnessLow
    )
}

/**
 * The UI element for toggling the ignore non full screen apps setting.
 */
@Composable
fun IgnoreFullScreenAppsTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen),
        subtitleText = stringResource(id = R.string.feature_grayscale_ignoreNonFullScreen_description),
        trailing = {
            Checkbox(checked = viewModel.ignoreNonFullScreenApps.collectAsState().value,
                onCheckedChange = {
                    viewModel.toggleIgnoreNonFullScreenApps()
                })
        },
        leadingIcon = Icons.Default.Fullscreen
    )
}

/**
 * The UI element for setting the allowed daily color screen time. On click, a dialog will be shown
 * that allows the user to set the allowed daily color screen time.
 */
@Composable
fun AllowedDailyColorScreenTimeTile(viewModel: GrayscaleAppsFeatureSettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val showAllowedDailyColorScreenTimeDialog =
        viewModel.showAllowedDailyColorScreenTimeDialog.collectAsState().value
    // includes the still-running tracking session, so the display doesn't lag behind
    val usedUpScreenTime =
        GrayscaleAppsFeature.currentUsedUpScreenTime().milliseconds.inWholeMinutes.toInt()
    val allowedDailyColorScreenTime =
        viewModel.allowedDailyColorScreenTime.collectAsState().value.toInt()
    if (showAllowedDailyColorScreenTimeDialog) {
        NumberPickerDialog(titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
            initialValue = allowedDailyColorScreenTime,
            onValueSelected = { viewModel.setAllowedDailyColorScreenTime(it.toLong()) },
            onDismissRequest = { viewModel.setShowAllowedDailyColorScreenTimeDialog(false) },
            range = 0..180 step 5,
            label = { it.minutes.toHrMinString(context) })
    }

    SimpleListTile(
        titleText = stringResource(id = R.string.feature_grayscale_allowedColorScreenTime),
        subtitleText = stringResource(id = R.string.feature_disableApps_allowedDailyTime_description),
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.time__minutes, allowedDailyColorScreenTime))
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(
                        id = R.string.time__minutes, usedUpScreenTime
                    ) + "\n" + stringResource(
                        id = R.string.time__minutes_used
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.labelVerySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        onClick = { viewModel.setShowAllowedDailyColorScreenTimeDialog(true) },
        leadingIcon = Icons.Default.ColorLens
    )
}