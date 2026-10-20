package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.premium.PremiumManager
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.review.AppReviewController
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.widgets.apps.AppListTab
import com.flx_apps.digitaldetox.ui.widgets.apps.AppListTabSelector
import com.flx_apps.digitaldetox.util.NavigationUtil
import com.flx_apps.digitaldetox.util.RestrictedSettingsUtil
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How many apps or websites the most-used list shows before "Show more". */
internal const val TOP_ITEMS_PREVIEW_COUNT = 10

private enum class TopAppsSort(val labelRes: Int) {
    SCREEN_TIME(R.string.usageStats_sort_screenTime),
    OPENS(R.string.usageStats_sort_opens),
    SCROLL_INTENSITY(R.string.usageStats_sort_scrollIntensity)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    showExclusions: Boolean = false,
    openApp: String? = null,
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
    viewModel: UsageStatsViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.collectAsState().value
    val isPremiumUnlocked by PremiumManager.isPremiumUnlocked.collectAsState()
    val selectedApp = remember { mutableStateOf<AppUsageStat?>(null) }
    val showCustomPicker = remember { mutableStateOf(false) }
    val exclusionsShown = remember { mutableStateOf(showExclusions) }
    var appToOpen by remember { mutableStateOf(openApp) }
    var listTab by rememberSaveable { mutableStateOf(AppListTab.APPS) }
    var sortMode by remember { mutableStateOf(TopAppsSort.SCREEN_TIME) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortedApps = remember(state.topApps, sortMode) {
        when (sortMode) {
            TopAppsSort.SCREEN_TIME -> state.topApps.sortedByDescending { it.totalTimeMs }
            TopAppsSort.OPENS -> state.topApps.sortedByDescending { it.launchCount }
            TopAppsSort.SCROLL_INTENSITY -> state.topApps.sortedByDescending { it.scrollIntensityScore }
        }
    }
    // websites have no scrolling of their own to sort by, so that order keeps them by time
    val sortedWebsites = remember(state.topWebsites, sortMode) {
        if (sortMode == TopAppsSort.OPENS) state.topWebsites.sortedByDescending { it.launchCount }
        else state.topWebsites.sortedByDescending { it.totalTimeMs }
    }

    LaunchedEffect(Unit) { viewModel.refresh(force = true) }
    // a link from the widget names an app, whose details open once the numbers have it
    LaunchedEffect(state.topApps) {
        val packageName = appToOpen ?: return@LaunchedEffect
        state.topApps.find { it.packageName == packageName }?.let {
            selectedApp.value = it
            appToOpen = null
        }
    }

    // Looking at a week+ of usage stats is the moment the app's value is most visible — a good,
    // frequency-capped time to ask for a store review (no-op in the FOSS build).
    val activity = LocalActivity.current
    LaunchedEffect(state.availableHistoryDays) {
        if (state.availableHistoryDays >= 7 && activity != null) {
            AppReviewController.maybeAskForReview(activity)
        }
    }

    val context = LocalContext.current

    if (showCustomPicker.value) {
        UsageStatsDateRangePicker(
            onDismiss = { showCustomPicker.value = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showCustomPicker.value = false
            },
            earliestAvailableDate = state.earliestHistoryDate
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usageStats_title)) },
                navigationIcon = {
                    IconButton(onClick = { navViewModel.onBackPress() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { exclusionsShown.value = true }) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.usageStats_exclusions_title)
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(force = true) }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TimeFrameSelector(
                        selectedTimeFrame = state.timeFrame,
                        availableHistoryDays = state.availableHistoryDays,
                        isPremiumUnlocked = isPremiumUnlocked,
                        onTimeFrameSelected = { viewModel.setTimeFrame(it) },
                        onLockedFrameClicked = {
                            PremiumSheetController.showForLockedFeature(R.string.premium_feature_usageHistory)
                        },
                        onCustomClicked = { showCustomPicker.value = true }
                    )
                    if (state.timeFrame == TimeFrame.CUSTOM) {
                        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        Text(
                            text = "${state.customStart.format(fmt)} – ${state.customEnd.format(fmt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                if (state.isSelfMeasured) {
                    item {
                        ElevatedCard(shape = CARD_SHAPE, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.usageStats_selfMeasured),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { NavigationUtil.openUsageAccessSettings(context) },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.action_grantPermission))
                                }
                            }
                        }
                    }
                }

                if (state.availableHistoryDays < MIN_DAYS_FOR_HISTORY_HINT) {
                    item {
                        ElevatedCard(
                            shape = CARD_SHAPE,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.usageStats_improvesOverTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    TrendsCarousel(
                        state = state,
                        onDaySelected = { viewModel.selectDay(it) },
                        onHourlyRateChanged = { viewModel.setHourlyRate(it) }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppListTabSelector(
                            selected = listTab,
                            onSelected = { listTab = it },
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.usageStats_sort),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false }
                            ) {
                                val forWebsites = listTab == AppListTab.WEBSITES
                                val modes = TopAppsSort.entries.filterNot {
                                    forWebsites && it == TopAppsSort.SCROLL_INTENSITY
                                }
                                for (mode in modes) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (forWebsites && mode == TopAppsSort.OPENS) {
                                                        R.string.usageStats_sort_visits
                                                    } else mode.labelRes
                                                )
                                            )
                                        },
                                        onClick = {
                                            sortMode = mode
                                            sortExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (listTab == AppListTab.WEBSITES) {
                    item {
                        WebsiteUsageCard(
                            state = state,
                            websites = sortedWebsites,
                            onOpen = { selectedApp.value = it },
                            onCountWebsites = { viewModel.setCountWebsites(it) }
                        )
                    }
                }

                if (listTab == AppListTab.APPS && state.topApps.isNotEmpty()) {
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        val displayedApps =
                            if (expanded) sortedApps else sortedApps.take(TOP_ITEMS_PREVIEW_COUNT)
                        StatsCard {
                            displayedApps.forEachIndexed { index, app ->
                                AppUsageRow(
                                    app = app,
                                    onClick = { selectedApp.value = app },
                                    showScrollInfo = sortMode == TopAppsSort.SCROLL_INTENSITY
                                )
                                if (index < displayedApps.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                            if (state.topApps.size > TOP_ITEMS_PREVIEW_COUNT) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (expanded) stringResource(R.string.usageStats_showLess)
                                        else stringResource(R.string.usageStats_showMore)
                                    )
                                }
                            }
                        }
                    }
                }

                if (listTab == AppListTab.APPS && state.topApps.isEmpty()) {
                    item {
                        // An empty period is not a missing permission: a user who has granted usage
                        // access and simply has no data yet should not be sent to a settings page
                        // that already says yes.
                        val hasUsageAccess =
                            remember { UsageAccessUtil.hasUsageAccess(context) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(
                                    if (hasUsageAccess) R.string.usageStats_noData
                                    else R.string.usageStats_usageAccessRequired
                                ), style = MaterialTheme.typography.bodyLarge
                            )
                            if (!hasUsageAccess) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { NavigationUtil.openUsageAccessSettings(context) }) {
                                    Text(stringResource(R.string.action_grantPermission))
                                }
                                if (RestrictedSettingsUtil.mayBlockSpecialAccess(context)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.usageStats_usageAccess_restrictedSettingsHint),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(onClick = { NavigationUtil.openAppInfoSettings(context) }) {
                                        Text(stringResource(R.string.action_openAppInfo))
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        selectedApp.value?.let { app ->
            LaunchedEffect(app.packageName) { viewModel.loadAppTrend(app.packageName) }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedApp.value = null },
                sheetState = sheetState
            ) {
                AppDetailContent(
                    app = app,
                    trendData = viewModel.appTrend.collectAsState().value,
                    onFeatureChanged = { viewModel.refresh() }
                )
            }
        }

        if (exclusionsShown.value) {
            val exclusionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { exclusionsShown.value = false },
                sheetState = exclusionsSheetState
            ) {
                UsageStatsExclusionsSheet(
                    state = state,
                    onAppExcluded = { packageName, excluded ->
                        viewModel.setAppExcluded(packageName, excluded)
                    },
                    onCategoryExcluded = { categoryId, excluded ->
                        viewModel.setCategoryExcluded(categoryId, excluded)
                    }
                )
            }
        }

        // Keep the selected app or website in sync when the lists are rebuilt after a feature
        // change.
        LaunchedEffect(state.topApps, state.topWebsites) {
            selectedApp.value?.let { old ->
                val items = if (old.isWebsite) state.topWebsites else state.topApps
                selectedApp.value = items.find { it.packageName == old.packageName }
            }
        }
    }
}
