package com.flx_apps.digitaldetox.ui.screens.feature

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.MainActivity
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.repository.ApplicationInfoRepository
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.appsSummary
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.websitesSummary
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.widgets.SimpleListTile
import com.flx_apps.digitaldetox.websites.Websites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tile that opens the app exceptions screen
 * @param titleText A custom title text for the tile (a default is provided)
 * @param subtitleRes A custom subtitle for the tile (a default is provided). It takes the number of
 *   selected apps as its single format argument. Once websites matter to the feature, a second
 *   line says which ones it covers.
 */
@Composable
fun OpenAppExceptionsTile(
    featureViewModel: FeatureViewModel = viewModel(),
    navViewModel: NavViewModel = viewModel(viewModelStoreOwner = LocalContext.current as MainActivity),
    titleText: String = stringResource(id = R.string.feature_settings_exceptions),
    @StringRes subtitleRes: Int? = null,
) {
    val feature = featureViewModel.feature as SupportsAppExceptionsFeature
    val appCount = rememberSelectedAppCount(feature)
    val apps = subtitleRes?.let { stringResource(id = it, appCount) }
        ?: appsSummary(feature.appExceptionListType, appCount)
    val websites = if (Websites.matterTo(feature)) {
        val entries = Websites.websitesOf(feature.appExceptions)
        val collections = entries.count { Websites.collectionOf(it) != null }
        websitesSummary(feature.websiteListType, entries.size - collections, collections)
    } else null
    SimpleListTile(
        titleText = titleText,
        subtitleText = listOfNotNull(apps, websites).joinToString("\n"),
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Manage App Scope",
                modifier = Modifier.size(24.dp)
            )
        }, onClick = {
        navViewModel.openRoute(NavigationRoutes.AppExceptions(featureId = featureViewModel.feature.id))
    }, allowClickWhenLocked = true,
        leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_app_exceptions)
    )
}

/**
 * How many apps of [feature]'s selection count, which is what the selection screen counts as
 * well: the apps that are really there. Querying the package manager takes a moment, so the
 * stored apps stand in until the real number arrives.
 */
@Composable
fun rememberSelectedAppCount(feature: SupportsAppExceptionsFeature): Int {
    val apps = Websites.appsOf(feature.appExceptions)
    return produceState(initialValue = apps.size, apps) {
        value = withContext(Dispatchers.IO) { ApplicationInfoRepository.countInstalled(apps) }
    }.value
}
