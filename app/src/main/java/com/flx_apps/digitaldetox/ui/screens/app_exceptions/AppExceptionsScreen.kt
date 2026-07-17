package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.feature_types.AppExceptionListType
import com.flx_apps.digitaldetox.feature_types.FeatureId
import com.flx_apps.digitaldetox.feature_types.SupportsAppExceptionsFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.PasswordLockGate
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.SettingsLockBannerIfNeeded
import com.flx_apps.digitaldetox.ui.widgets.AppBarBackButton
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.OptionsRow
import com.flx_apps.digitaldetox.ui.widgets.apps.AppFilterSheet
import com.flx_apps.digitaldetox.ui.widgets.apps.AppListSectionHeader
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
                searchContentDescription = stringResource(id = R.string.action_search),
                closeSearchContentDescription = stringResource(id = R.string.action_search_close),
                onOpenFilters = { appExceptionsViewModel.setShowListSettingsSheet(true) },
                filtersEnabled = !settingsLocked,
                filtersContentDescription = stringResource(id = R.string.action_filter),
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
 * Selected apps float to the top in their own section (same pattern as the minimal-launcher
 * widget configurator), so the feature's effective scope is visible at a glance. Because rows
 * keep their key across sections, toggling one animates it to its new place.
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
        } else {
            val (selected, available) = appExceptions.partition { it.isException }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppListSectionHeader(stringResource(id = R.string.appList_section_selected))
                    CopyExceptionsFromButton(settingsLocked = settingsLocked)
                }
            }
            if (selected.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.appList_selected_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(selected, key = { item -> item.appInfo.packageName }) { appException ->
                    AppExceptionListItem(appException, modifier = Modifier.animateItem())
                }
            }
            item {
                AppListSectionHeader(stringResource(id = R.string.appList_section_available))
            }
            if (available.isEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.feature_settings_exceptions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(available, key = { item -> item.appInfo.packageName }) { appException ->
                    AppExceptionListItem(appException, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * "Copy from…" next to the "Selected apps" header: takes over another feature's app selection.
 * Only rendered when at least one other feature actually has apps selected.
 */
@Composable
private fun CopyExceptionsFromButton(
    settingsLocked: Boolean, viewModel: AppExceptionsViewModel = viewModel()
) {
    val copySources = remember { viewModel.copySources() }
    if (copySources.isEmpty()) return
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        enabled = !settingsLocked,
        modifier = Modifier
            .padding(end = 8.dp)
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(id = R.string.feature_settings_exceptions_copyFrom),
            style = MaterialTheme.typography.labelMedium
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(id = R.string.feature_settings_exceptions_copyFrom_dialogTitle)) },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.feature_settings_exceptions_copyFrom_dialogHint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    copySources.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.copyExceptionsFrom(source.featureId)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(id = source.titleRes))
                            Text(
                                text = stringResource(
                                    id = R.string.feature_settings_exceptions_copyFrom_appCount,
                                    source.appCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
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
        contentDescription = null
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
    item: AppExceptionItem,
    modifier: Modifier = Modifier,
    appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    val settingsLocked = LocalSettingsLocked.current
    AppSelectionListItem(
        packageName = item.appInfo.packageName,
        appName = item.appInfo.appName,
        appCategory = item.appInfo.appCategory,
        isSystemApp = item.appInfo.isSystemApp,
        checked = item.isException,
        modifier = modifier,
        enabled = !settingsLocked,
        onCheckedChange = {
            if (settingsLocked) return@AppSelectionListItem
            appExceptionsViewModel.toggleAppException(item.appInfo.packageName)
        })
}

/**
 * The [AppExceptionsListSettingsSheet] provides methods to filter the list by system/user apps and
 * by the app category. The sheet itself is the shared [AppFilterSheet].
 */
@Composable
fun AppExceptionsListSettingsSheet(
    viewModel: AppExceptionsViewModel = viewModel()
) {
    AppFilterSheet(
        showSystemApps = viewModel.showSystemApps.collectAsState().value,
        showUserApps = viewModel.showUserApps.collectAsState().value,
        categories = viewModel.selectedAppCategories.collectAsState().value,
        onToggleSystemApps = { viewModel.toggleShowSystemApps() },
        onToggleUserApps = { viewModel.toggleShowUserApps() },
        onToggleCategory = { viewModel.toggleAppCategory(it) },
        onDismiss = { viewModel.setShowListSettingsSheet(false) },
    )
}
