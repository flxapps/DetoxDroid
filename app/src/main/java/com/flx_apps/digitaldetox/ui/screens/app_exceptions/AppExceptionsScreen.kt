package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.res.pluralStringResource
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
 * The switch between apps and websites sticks to the top, so the other half is never more than a
 * tap away. Under it each half has its own scope card, as apps and websites each have a list type
 * of their own; the card scrolls away with the list.
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
    val uncoveredUnreadable =
        appExceptionsViewModel.uncoveredUnreadableBrowsers.collectAsState().value
    val websitesInBrowsers = WebsitesInBrowsers(
        readable = browsers?.readable.orEmpty().mapTo(mutableSetOf()) { it.packageName },
        unreadable = browsers?.unreadable.orEmpty().mapTo(mutableSetOf()) { it.packageName },
        appListType = appExceptionsViewModel.exceptionListType.collectAsState().value,
        websiteListType = appExceptionsViewModel.websiteListType.collectAsState().value,
        websiteCount = appExceptionsViewModel.selectedWebsiteCount.collectAsState().value,
    )
    val context = LocalContext.current
    val advancedListed = collections.any { it.collection.advanced && it.checked }
    var advancedExpanded by rememberSaveable { mutableStateOf(advancedListed) }
    // a collection in the list is never out of sight, also when "Copy from…" brought it in
    LaunchedEffect(advancedListed) { if (advancedListed) advancedExpanded = true }
    LazyColumn {
        stickyHeader(key = "listTab") {
            AppListTabSelector(selected = listTab, onSelected = onListTabSelected)
        }
        when (listTab) {
            AppListTab.APPS -> {
                item(key = "appListType") { AppsListTypeCard(settingsLocked = settingsLocked) }
                appSections(appExceptions, settingsLocked, websitesInBrowsers)
            }

            AppListTab.WEBSITES -> {
                item(key = "websiteListType") {
                    WebsitesListTypeCard(
                        settingsLocked = settingsLocked,
                        onShowApps = { onListTabSelected(AppListTab.APPS) }
                    )
                }
                websiteSections(
                    sites = sites,
                    collections = collections,
                    browsers = browsers,
                    // with every website covered, a browser DetoxDroid cannot read is a way around
                    // it, unless the browser itself is covered as an app
                    uncoveredBrowsers = if (
                        websitesInBrowsers.websiteListType == AppExceptionListType.NOT_LIST
                    ) uncoveredUnreadable else emptyList(),
                    onShowApps = { onListTabSelected(AppListTab.APPS) },
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
    appExceptions: List<AppExceptionItem>?,
    settingsLocked: Boolean,
    websitesInBrowsers: WebsitesInBrowsers,
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
            AppExceptionListItem(
                appException,
                supportingText = websitesInBrowsers.lineFor(appException),
                modifier = Modifier.animateItem()
            )
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
            AppExceptionListItem(
                appException,
                supportingText = websitesInBrowsers.lineFor(appException),
                modifier = Modifier.animateItem()
            )
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
 * The scope card of the app half: which apps the feature covers. Features with both list types
 * offer the choice (settings icon); Disable Apps only explains its fixed scope (info icon).
 */
@Composable
private fun AppsListTypeCard(
    settingsLocked: Boolean, viewModel: AppExceptionsViewModel = viewModel()
) {
    val listType = viewModel.exceptionListType.collectAsState().value
    val count = viewModel.selectedAppCount.collectAsState().value
    val allowed = (viewModel.feature as SupportsAppExceptionsFeature).listTypes
    ListTypeCard(
        options = mapOf(
            R.string.feature_settings_exceptions_listType_notList to AppExceptionListType.NOT_LIST,
            R.string.feature_settings_exceptions_listType_onlyList to AppExceptionListType.ONLY_LIST
        ).filterValues { it in allowed },
        selected = listType,
        selectedCount = count,
        summary = appsSummary(listType, count),
        switchTexts = SwitchListTypeTexts(
            title = R.string.feature_settings_exceptions_switchListType_title,
            toNotList = R.string.feature_settings_exceptions_switchListType_toNotList,
            toOnlyList = R.string.feature_settings_exceptions_switchListType_toOnlyList,
        ),
        settingsLocked = settingsLocked,
        onSelect = viewModel::setExceptionListType,
    )
}

/**
 * The scope card of the website half: which websites the feature covers. While only the selected
 * ones count, it also names the browsers the app half covers, where every website counts anyway,
 * with a way over to them.
 */
@Composable
private fun WebsitesListTypeCard(
    settingsLocked: Boolean,
    onShowApps: () -> Unit,
    viewModel: AppExceptionsViewModel = viewModel()
) {
    val listType = viewModel.websiteListType.collectAsState().value
    val count = viewModel.selectedWebsiteCount.collectAsState().value
    val coveredBrowsers = viewModel.browsersCoveredAsApps.collectAsState().value
    ListTypeCard(
        options = mapOf(
            R.string.feature_settings_exceptions_listType_notList_websites to
                    AppExceptionListType.NOT_LIST,
            R.string.feature_settings_exceptions_listType_onlyList_websites to
                    AppExceptionListType.ONLY_LIST
        ).filterValues { it in viewModel.allowedWebsiteListTypes },
        selected = listType,
        selectedCount = count,
        summary = websitesSummary(listType, count),
        switchTexts = SwitchListTypeTexts(
            title = R.string.feature_settings_exceptions_switchListType_title_websites,
            toNotList = R.string.feature_settings_exceptions_switchListType_toNotList_websites,
            toOnlyList = R.string.feature_settings_exceptions_switchListType_toOnlyList_websites,
        ),
        settingsLocked = settingsLocked,
        onSelect = viewModel::setWebsiteListType,
    ) {
        if (listType == AppExceptionListType.ONLY_LIST && coveredBrowsers.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.websites_coveredBrowsers,
                    count = coveredBrowsers.size,
                    joinedNames(coveredBrowsers)
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(
                onClick = onShowApps,
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(text = stringResource(id = R.string.websites_showApps))
            }
        }
    }
}

/** What [SwitchListTypeDialog] says, for the half of the list it is about. */
private data class SwitchListTypeTexts(
    @StringRes val title: Int,
    @StringRes val toNotList: Int,
    @StringRes val toOnlyList: Int,
)

/**
 * The scope card of one half of the list: a title, the choice of list type where [options] holds
 * more than one, the [summary] of what is covered, and [extra] content below it.
 */
@Composable
private fun ListTypeCard(
    options: Map<Int, AppExceptionListType>,
    selected: AppExceptionListType,
    selectedCount: Int,
    summary: String,
    switchTexts: SwitchListTypeTexts,
    settingsLocked: Boolean,
    onSelect: (AppExceptionListType, Boolean) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val choosable = options.size > 1
    var pendingListType by remember { mutableStateOf<AppExceptionListType?>(null) }

    IconCard(icon = if (choosable) Icons.Default.Settings else Icons.Default.Info) {
        Text(
            text = stringResource(id = R.string.feature_settings_exceptions_listType),
            style = MaterialTheme.typography.titleSmall
        )
        if (choosable) {
            OptionsRow(options = options, selectedOption = selected, onOptionSelected = {
                if (settingsLocked) return@OptionsRow
                val newType = it as AppExceptionListType
                if (newType == selected) return@OptionsRow
                // a non-empty selection changes meaning with the type: the entries the feature
                // was limited to become the ones it spares. Ask before carrying it over.
                if (selectedCount > 0) {
                    pendingListType = newType
                } else {
                    onSelect(newType, false)
                }
            })
        }
        Text(text = summary, modifier = Modifier.padding(top = 6.dp))
        extra()
    }

    pendingListType?.let { newType ->
        SwitchListTypeDialog(
            newType = newType,
            selectedCount = selectedCount,
            texts = switchTexts,
            onKeep = {
                onSelect(newType, false)
                pendingListType = null
            },
            onClear = {
                onSelect(newType, true)
                pendingListType = null
            },
            onDismiss = { pendingListType = null })
    }
}

/**
 * Says what switching the list type does to the entries already selected, and offers to switch
 * with them or with an empty list.
 */
@Composable
private fun SwitchListTypeDialog(
    newType: AppExceptionListType,
    selectedCount: Int,
    texts: SwitchListTypeTexts,
    onKeep: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = texts.title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        id = if (newType == AppExceptionListType.NOT_LIST) texts.toNotList
                        else texts.toOnlyList,
                        selectedCount
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
 * What the app half says about the websites inside a browser, see [lineFor]. On this half a
 * browser is an app; the websites it shows go by the other half, and by the browser where that
 * only counts the websites it lists.
 */
private data class WebsitesInBrowsers(
    val readable: Set<String>,
    val unreadable: Set<String>,
    val appListType: AppExceptionListType,
    val websiteListType: AppExceptionListType,
    val websiteCount: Int,
)

/**
 * The line under a browser on the app half that says on which of its websites the feature is
 * active, once websites matter to the feature at all. It follows the checkbox: a browser the
 * feature covers as an app has every website covered, unless the website half spares some.
 */
@Composable
private fun WebsitesInBrowsers.lineFor(item: AppExceptionItem): String? {
    val packageName = item.appInfo.packageName
    val sparesWebsites = websiteListType == AppExceptionListType.NOT_LIST
    if (websiteCount == 0 && !sparesWebsites) return null
    if (packageName in unreadable) {
        return stringResource(id = R.string.appList_browser_websites_unreadable)
    }
    if (packageName !in readable) return null
    val coveredAsApp = (appListType == AppExceptionListType.ONLY_LIST) == item.isException
    return when {
        sparesWebsites && websiteCount > 0 -> pluralStringResource(
            id = R.plurals.appList_browser_websites_allExcept, count = websiteCount, websiteCount
        )

        sparesWebsites || coveredAsApp -> stringResource(id = R.string.appList_browser_websites_all)
        else -> pluralStringResource(
            id = R.plurals.appList_browser_websites_only, count = websiteCount, websiteCount
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
    supportingText: String? = null,
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
        supportingText = supportingText,
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
 * The line that says which apps a list covers, out of [count] selected: "Active only in 3
 * selected apps."
 */
@Composable
fun appsSummary(listType: AppExceptionListType, count: Int): String = when {
    listType == AppExceptionListType.ONLY_LIST -> pluralStringResource(
        id = R.plurals.feature_settings_exceptions_onlyListed, count = count, count
    )

    count == 0 -> stringResource(id = R.string.feature_settings_exceptions_allApps)
    else -> pluralStringResource(
        id = R.plurals.feature_settings_exceptions_notListed, count = count, count
    )
}

/**
 * The line that says which websites a list covers, out of [count] listed: "Active only on 2
 * selected websites." A collection counts as one, as it does in the list.
 */
@Composable
fun websitesSummary(listType: AppExceptionListType, count: Int): String = when {
    listType == AppExceptionListType.ONLY_LIST -> pluralStringResource(
        id = R.plurals.feature_settings_exceptions_onlyListed_websites, count = count, count
    )

    count == 0 -> stringResource(id = R.string.feature_settings_exceptions_allWebsites)
    else -> pluralStringResource(
        id = R.plurals.feature_settings_exceptions_notListed_websites, count = count, count
    )
}
