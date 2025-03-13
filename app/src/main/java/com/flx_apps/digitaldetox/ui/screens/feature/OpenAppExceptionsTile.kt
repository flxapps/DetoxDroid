package com.flx_apps.digitaldetox.ui.screens.feature

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile

/**
 * A tile that opens the app exceptions screen
 * @param titleText A custom title text for the tile (a default is provided)
 * @param subtitleText A custom subtitle text for the tile (a default is provided)
 */
@Composable
fun OpenAppExceptionsTile(
    featureViewModel: FeatureViewModel = viewModel(),
    navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalContext.current as MainActivity),
    titleText: String = stringResource(id = R.string.feature_settings_exceptions),
    subtitleText: String? = null,
) {
    val feature = featureViewModel.feature as SupportsAppExceptionsFeature
    SimpleListTile(
        titleText = titleText, subtitleText = subtitleText ?: stringResource(
        id = if (feature.appExceptionListType == AppExceptionListType.NOT_LIST) R.string.feature_settings_exceptions__notListed
        else R.string.feature_settings_exceptions__onlyListed, feature.appExceptions.size
    ), trailing = {
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = "Manage Exceptions",
            modifier = Modifier.size(24.dp)
        )
    }, onClick = {
        navViewModel.openRoute(NavigationRoutes.AppExceptions(featureId = featureViewModel.feature.id))
    }, leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_app_exceptions)
    )
}