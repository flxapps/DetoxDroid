package com.flx_apps.digitaldetox.ui.screens.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.InfoCard

/**
 * A singleton that provides the snackbar host state for the feature screen and its children.
 */
object FeatureScreenSnackbarStateProvider {
    val snackbarState: SnackbarHostState by lazy { SnackbarHostState() }
}

/**
 * The screen that shows the details of a feature. It contains a top app bar with a back button and
 * a switch to toggle the active state of the feature. The content of the screen is provided by the
 * feature itself.
 * @param featureId The id of the feature to show.
 * @see [Feature.settingsContent]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreen(
    featureId: FeatureId,
    featureViewModel: FeatureViewModel = FeatureViewModel.withFeatureId(featureId),
) { // back pressed dispatcher is used to handle back button presses
    val feature = featureViewModel.feature
    val snackbarHostState = FeatureScreenSnackbarStateProvider.snackbarState
    Scaffold(snackbarHost = {
        SnackbarHost(hostState = snackbarHostState)
    }, topBar = {
        LargeTopAppBar(navigationIcon = { AppBarBackButton() }, title = {
            Column {
                Text(stringResource(id = feature.texts.title))
                Text(
                    stringResource(id = feature.texts.subtitle),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }, actions = {
            FeatureActivationSwitch()
        })
    }) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                FeatureScreenContent(feature)
            }
        }
    }
}

/**
 * A switch to toggle the active state of the feature. If the feature needs permissions to be
 * activated, a snackbar is shown to request the permissions and the state is not toggled.
 */
@Composable
fun FeatureActivationSwitch(
    featureViewModel: FeatureViewModel = viewModel(),
    navViewModel: NavViewModel = NavViewModel.navViewModel()
) {
    val context = LocalContext.current
    Switch(modifier = Modifier.padding(end = 8.dp),
        checked = featureViewModel.featureIsActive.collectAsState().value,
        onCheckedChange = {
            if (featureViewModel.toggleFeatureActive() == null) {
                featureViewModel.showSnackbar(message = context.getString(R.string.action_requestPermissions),
                    actionLabel = context.getString(R.string.action_go),
                    onResult = { snackbarResult ->
                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                            (featureViewModel.feature as NeedsPermissionsFeature).requestPermissions(
                                context, navViewModel
                            )
                        }
                    })
            }
        })
}

/**
 * The content of the feature screen. It contains a description of the feature and the settings
 * content provided by the feature.
 * @see [Feature.texts]
 * @see [Feature.settingsContent]
 */
@Composable
fun FeatureScreenContent(feature: Feature) {
    InfoCard(infoText = stringResource(id = feature.texts.description))
    feature.settingsContent()
}

