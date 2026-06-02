import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.AppExceptionItem
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.AppExceptionsViewModel
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.PasswordLockGate
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.SettingsLockBannerIfNeeded
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionTopBar

/**
 * This screen allows the user to manage the app exceptions for a feature.
 *
 * It displays all installed apps and lets the user decide whether this feature should apply to all
 * apps except selected ones, or only to selected ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAppExceptionsScreen(
    featureId: FeatureId,
    appExceptionsViewModel: AppExceptionsViewModel = AppExceptionsViewModel.withFeatureId(featureId),
) {
    var showSearchBar by remember { mutableStateOf(false) }
    // Disable Apps uses a dedicated title to match the feature wording in settings.
    val titleRes = if (featureId == DisableAppsFeature.id) {
        R.string.feature_disableApps_deactivatedApps
    } else {
        R.string.feature_settings_exceptions
    }
    PasswordLockGate(featureId = featureId, showBanner = false) {
        val settingsLocked = LocalSettingsLocked.current
        Scaffold(topBar = {
            AppSelectionTopBar(
                title = stringResource(id = titleRes),
                showSearchBar = showSearchBar,
                searchQuery = appExceptionsViewModel.query.value,
                onSearchQueryChange = { query ->
                    appExceptionsViewModel.filterApps(query)
                },
                onToggleSearch = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) {
                        appExceptionsViewModel.filterApps("")
                    }
                },
                navigationIcon = { AppBarBackButton() },
                searchContentDescription = "Search",
                closeSearchContentDescription = "Close Search",
                onOpenFilters = { appExceptionsViewModel.setShowListSettingsSheet(true) },
                filtersEnabled = !settingsLocked,
                filtersContentDescription = "Open App Filters",
            )
        }) {
            if (appExceptionsViewModel.showListSettingsSheet.collectAsState().value) {
                AppExceptionsListSettingsSheet()
            }
            Column(modifier = Modifier.padding(paddingValues = it)) {
                SettingsLockBannerIfNeeded(featureId = featureId)
                InstalledAppsList(settingsLocked = settingsLocked)
            }
        }
    }
}

/**
 * Displays the list of installed apps so the user can select apps for the current scope mode.
 *
 * The scope section is rendered as the first [LazyColumn] item, so it visually belongs to the list
 * and scrolls away with the app items.
 */
@Composable
fun InstalledAppsList(
    settingsLocked: Boolean, appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    val appExceptions = appExceptionsViewModel.appExceptionItems.collectAsState().value
    LazyColumn {
        item {
            AppExceptionsListTypeSection(settingsLocked = settingsLocked)
        }
        if (appExceptions == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        } else if (appExceptions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(id = R.string.feature_settings_exceptions_empty))
                }
            }
        } else {
            items(appExceptions, key = { item -> item.appInfo.packageName }) { appException ->
                AppExceptionListItem(appException)
            }
        }
    }
}

/**
 * Scope configuration card shown above the app list.
 *
 * Features with both list types show interactive scope controls (settings icon). Features with a
 * fixed scope only show explanatory information (info icon).
 */
@Composable
fun AppExceptionsListTypeSection(
    settingsLocked: Boolean, viewModel: AppExceptionsViewModel = viewModel()
) {
    val selectedListType = viewModel.exceptionListType.collectAsState().value
    val toggledItemsSize = viewModel.toggledItemsSize.collectAsState().value
    val listTypeMap = mapOf(
        R.string.feature_settings_exceptions_listType_notList to AppExceptionListType.NOT_LIST,
        R.string.feature_settings_exceptions_listType_onlyList to AppExceptionListType.ONLY_LIST
    ).filter { (viewModel.feature as SupportsAppExceptionsFeature).listTypes.contains(it.value) }
    val supportsMultipleListTypes = listTypeMap.size > 1
    val cardIcon: ImageVector =
        if (supportsMultipleListTypes) Icons.Default.Settings else Icons.Default.Info

    IconCard(
        icon = cardIcon,
        contentDescription = if (supportsMultipleListTypes) "Feature scope settings" else "Feature scope information"
    ) {
        Text(
            text = stringResource(id = R.string.feature_settings_exceptions_listType),
            style = MaterialTheme.typography.titleSmall
        )
        if (supportsMultipleListTypes) {
            Text(
                text = stringResource(id = R.string.feature_settings_exceptions_listType_description),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            OptionsRow(
                options = listTypeMap, selectedOption = selectedListType, onOptionSelected = {
                    if (settingsLocked) return@OptionsRow
                    viewModel.setExceptionListType(it as AppExceptionListType)
                })
            Text(
                text = stringResource(
                    id = if (selectedListType == AppExceptionListType.NOT_LIST) {
                        R.string.feature_settings_exceptions_listType_description_notList
                    } else {
                        R.string.feature_settings_exceptions_listType_description_onlyList
                    }
                ), modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Text(
            text = stringResource(
                id = if (selectedListType == AppExceptionListType.NOT_LIST) {
                    R.string.feature_settings_exceptions__notListed
                } else {
                    R.string.feature_settings_exceptions__onlyListed
                }, toggledItemsSize
            ), modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * Displays an app exception item. It contains the app icon, name and a switch to toggle the
 * exception state.
 */
@Composable
fun AppExceptionListItem(
    item: AppExceptionItem, appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    val settingsLocked = LocalSettingsLocked.current
    var checkedState by remember(item.appInfo.packageName, item.isException) {
        mutableStateOf(item.isException)
    }
    AppSelectionListItem(
        packageName = item.appInfo.packageName,
        appName = item.appInfo.appName,
        appCategory = item.appInfo.appCategory,
        isSystemApp = item.appInfo.isSystemApp,
        checked = checkedState,
        enabled = !settingsLocked,
        onCheckedChange = {
            if (settingsLocked) return@AppSelectionListItem
            checkedState = appExceptionsViewModel.toggleAppException(item.appInfo.packageName)
                ?: checkedState
        })
}

/**
 * The [AppExceptionsListSettingsSheet] provides methods to filter the list by system/user apps and
 * by the app category.
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class
)
@Composable
fun AppExceptionsListSettingsSheet(
    viewModel: AppExceptionsViewModel = viewModel()
) {
    ModalBottomSheet(onDismissRequest = {
        viewModel.setShowListSettingsSheet(false)
    }, sheetState = rememberModalBottomSheetState(), containerColor = MaterialTheme.colorScheme.surface) {
        Column {
            androidx.compose.material3.ListItem(
                headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByAppType)) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = viewModel.showSystemApps.collectAsState().value,
                            onClick = { viewModel.toggleShowSystemApps() },
                            label = { Text(text = stringResource(R.string.exceptionsList_filter_systemApps)) })
                        FilterChip(
                            selected = viewModel.showUserApps.collectAsState().value,
                            onClick = { viewModel.toggleShowUserApps() },
                            label = { Text(text = stringResource(R.string.exceptionsList_filter_userApps)) })
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            androidx.compose.material3.ListItem(
                headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByCategory)) },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        viewModel.selectedAppCategories.collectAsState().value.forEach {
                            FilterChip(
                                onClick = { viewModel.toggleAppCategory(it.key) },
                                selected = it.value,
                                label = { Text(text = it.key) })
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            // FIXME there should be a better way to do this, e.g. using Modifier.navigationBarsPadding(),
            //  but I couldn't get it to work for some reason
            Box(modifier = Modifier.height(48.dp))
        }
    }
}
