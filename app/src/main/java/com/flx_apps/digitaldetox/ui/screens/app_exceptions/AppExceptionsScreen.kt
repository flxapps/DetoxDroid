package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    var listTab by rememberSaveable { mutableStateOf(AppListTab.APPS) }
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
                // the filters are about apps: system or not, and the app's category
                onOpenFilters = if (listTab == AppListTab.APPS) {
                    { appExceptionsViewModel.setShowListSettingsSheet(true) }
                } else null,
                filtersEnabled = !settingsLocked,
                filtersContentDescription = stringResource(id = R.string.action_filter),
            )
        }) {
            if (appExceptionsViewModel.showListSettingsSheet.collectAsState().value) {
                AppExceptionsListSettingsSheet()
            }
            Column(modifier = Modifier.padding(paddingValues = it)) {
                SettingsLockBannerIfNeeded(featureId = featureId)
                InstalledAppsList(
                    settingsLocked = settingsLocked,
                    listTab = listTab,
                    onListTabSelected = { listTab = it }
                )
            }
        }
    }
}

/** The two halves of an app list. */
enum class AppListTab(@StringRes val label: Int, val icon: ImageVector) {
    APPS(R.string.appList_tab_apps, Icons.Default.Apps),
    WEBSITES(R.string.appList_tab_websites, Icons.Default.Language),
}

/**
 * Displays the list of installed apps so the user can select apps for the current scope mode, and
 * on its second half the websites (see [websiteSections]).
 *
 * Selected apps float to the top in their own section (same pattern as the minimal-launcher
 * widget configurator), so the feature's effective scope is visible at a glance. Because rows
 * keep their key across sections, toggling one animates it to its new place.
 *
 * The scope section is rendered as the first [LazyColumn] item, so it visually belongs to the list
 * and scrolls away with the app items. The switch between apps and websites sticks to the top, so
 * the other half is never more than a tap away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsList(
    settingsLocked: Boolean,
    listTab: AppListTab,
    onListTabSelected: (AppListTab) -> Unit,
    appExceptionsViewModel: AppExceptionsViewModel = viewModel()
) {
    val appExceptions = appExceptionsViewModel.appExceptionItems.collectAsState().value
    val sites = appExceptionsViewModel.sites.collectAsState().value
    val collections = appExceptionsViewModel.collections.collectAsState().value
    val browsers = appExceptionsViewModel.browsers.collectAsState().value
    val context = LocalContext.current
    val advancedListed = collections.any { it.collection.advanced && it.checked }
    var advancedExpanded by rememberSaveable { mutableStateOf(advancedListed) }
    // a collection in the list is never out of sight, also when "Copy from…" brought it in
    LaunchedEffect(advancedListed) { if (advancedListed) advancedExpanded = true }
    LazyColumn {
        item(key = "listType") {
            AppExceptionsListTypeSection(settingsLocked = settingsLocked)
        }
        stickyHeader(key = "listTab") {
            AppListTabSelector(selected = listTab, onSelected = onListTabSelected)
        }
        when (listTab) {
            AppListTab.APPS -> appSections(appExceptions, settingsLocked)
            AppListTab.WEBSITES -> websiteSections(
                sites = sites,
                collections = collections,
                browsers = browsers,
                query = appExceptionsViewModel.query.value,
                collectionTitle = { context.getString(it.title) },
                settingsLocked = settingsLocked,
                advancedExpanded = advancedExpanded,
                onToggleAdvanced = { advancedExpanded = !advancedExpanded },
                onAddWebsite = appExceptionsViewModel::addWebsite,
                onToggleWebsite = appExceptionsViewModel::toggleWebsite,
                copyButton = { CopyExceptionsFromButton(settingsLocked = settingsLocked) },
            )
        }
    }
}

/**
 * Apps or websites, as two segments. The segments sit on the screen's background, so the list
 * scrolling under the stuck header does not show through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListTabSelector(selected: AppListTab, onSelected: (AppListTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AppListTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = tab == selected,
                    onClick = { onSelected(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppListTab.entries.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = tab == selected) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                            )
                        }
                    }
                ) {
                    Text(stringResource(id = tab.label), maxLines = 1)
                }
            }
        }
    }
}

/** The app half of the list: the selected apps, then all the others. */
private fun LazyListScope.appSections(
    appExceptions: List<AppExceptionItem>?, settingsLocked: Boolean
) {
    if (appExceptions == null) {
        item(key = "loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
        return
    }
    val (selected, available) = appExceptions.partition { it.isException }
    item(key = "selectedHeader") {
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
        item(key = "selectedEmpty") {
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
    item(key = "availableHeader") {
        AppListSectionHeader(stringResource(id = R.string.appList_section_available))
    }
    if (available.isEmpty()) {
        item(key = "availableEmpty") {
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

/**
 * "Copy from…" next to the "Selected apps" header: takes over another feature's app selection.
 * Only rendered when at least one other feature actually has apps selected.
 */
@Composable
private fun CopyExceptionsFromButton(
    settingsLocked: Boolean, viewModel: AppExceptionsViewModel = viewModel()
) {
    val copySources = viewModel.copySources.collectAsState().value
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
                                text = if (source.websiteCount > 0) {
                                    stringResource(
                                        id = R.string.feature_settings_exceptions_copyFrom_appAndWebsiteCount,
                                        source.appCount, source.websiteCount
                                    )
                                } else {
                                    stringResource(
                                        id = R.string.feature_settings_exceptions_copyFrom_appCount,
                                        source.appCount
                                    )
                                },
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
    val hasWebsites = viewModel.selectedWebsiteCount.collectAsState().value > 0
    val listTypeMap = mapOf(
        R.string.feature_settings_exceptions_listType_notList to AppExceptionListType.NOT_LIST,
        R.string.feature_settings_exceptions_listType_onlyList to AppExceptionListType.ONLY_LIST
    ).filter { (viewModel.feature as SupportsAppExceptionsFeature).listTypes.contains(it.value) }
    val supportsMultipleListTypes = listTypeMap.size > 1
    val cardIcon: ImageVector =
        if (supportsMultipleListTypes) Icons.Default.Settings else Icons.Default.Info

    var pendingListType by remember { mutableStateOf<AppExceptionListType?>(null) }

    IconCard(
        icon = cardIcon,
        contentDescription = null
    ) {
        Text(
            text = stringResource(id = R.string.feature_settings_exceptions_listType),
            style = MaterialTheme.typography.titleSmall
        )
        if (supportsMultipleListTypes) {
            OptionsRow(
                options = listTypeMap, selectedOption = selectedListType, onOptionSelected = {
                    if (settingsLocked) return@OptionsRow
                    val newType = it as AppExceptionListType
                    if (newType == selectedListType) return@OptionsRow
                    // a non-empty selection changes meaning with the type: the apps the feature
                    // was limited to become the apps it spares. Ask before carrying it over.
                    if (toggledItemsSize > 0) {
                        pendingListType = newType
                    } else {
                        viewModel.setExceptionListType(newType)
                    }
                })
        }
        Text(
            text = stringResource(
                id = exceptionsSummary(selectedListType, hasWebsites), toggledItemsSize
            ), modifier = Modifier.padding(top = 6.dp)
        )
    }

    pendingListType?.let { newType ->
        SwitchListTypeDialog(
            newType = newType,
            selectedCount = toggledItemsSize,
            hasWebsites = hasWebsites,
            onKeep = {
                viewModel.setExceptionListType(newType)
                pendingListType = null
            },
            onClear = {
                viewModel.setExceptionListType(newType, clearSelection = true)
                pendingListType = null
            },
            onDismiss = { pendingListType = null })
    }
}

/**
 * Says what switching the list type does to the apps already selected, and offers to switch with
 * them or with an empty list.
 */
@Composable
private fun SwitchListTypeDialog(
    newType: AppExceptionListType,
    selectedCount: Int,
    hasWebsites: Boolean,
    onKeep: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    id = if (hasWebsites) {
                        R.string.feature_settings_exceptions_switchListType_title_websites
                    } else {
                        R.string.feature_settings_exceptions_switchListType_title
                    }
                )
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        id = when {
                            newType == AppExceptionListType.NOT_LIST && hasWebsites ->
                                R.string.feature_settings_exceptions_switchListType_toNotList_websites

                            newType == AppExceptionListType.NOT_LIST ->
                                R.string.feature_settings_exceptions_switchListType_toNotList

                            hasWebsites ->
                                R.string.feature_settings_exceptions_switchListType_toOnlyList_websites

                            else -> R.string.feature_settings_exceptions_switchListType_toOnlyList
                        }, selectedCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    R.string.feature_settings_exceptions_switchListType_keep to onKeep,
                    R.string.feature_settings_exceptions_switchListType_clear to onClear
                ).forEach { (labelRes, onClick) ->
                    Text(
                        text = stringResource(id = labelRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel))
            }
        }
    )
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

/**
 * The line that says what a list covers: "Active only in 3 selected apps." Once websites are in
 * it, the line names them too, with one number for both, as the list counts each entry once.
 */
@StringRes
fun exceptionsSummary(listType: AppExceptionListType, hasWebsites: Boolean): Int = when {
    listType == AppExceptionListType.NOT_LIST && hasWebsites ->
        R.string.feature_settings_exceptions_notListed_websites

    listType == AppExceptionListType.NOT_LIST -> R.string.feature_settings_exceptions_notListed
    hasWebsites -> R.string.feature_settings_exceptions_onlyListed_websites
    else -> R.string.feature_settings_exceptions_onlyListed
}
