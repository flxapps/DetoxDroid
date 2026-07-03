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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.util.NavigationUtil
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOP_APPS_PREVIEW_COUNT = 10

private enum class TopAppsSort(val labelRes: Int) {
    SCREEN_TIME(R.string.usage_stats_sort_screen_time),
    OPENS(R.string.usage_stats_sort_opens),
    SCROLL_INTENSITY(R.string.usage_stats_sort_scroll_intensity)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    navViewModel: NavViewModel = NavViewModel.navViewModel(),
    viewModel: UsageStatsViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.collectAsState().value
    val selectedApp = remember { mutableStateOf<AppUsageStat?>(null) }
    val showCustomPicker = remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(TopAppsSort.SCREEN_TIME) }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortedApps = remember(state.topApps, sortMode) {
        when (sortMode) {
            TopAppsSort.SCREEN_TIME -> state.topApps.sortedByDescending { it.totalTimeMs }
            TopAppsSort.OPENS -> state.topApps.sortedByDescending { it.launchCount }
            TopAppsSort.SCROLL_INTENSITY -> state.topApps.sortedByDescending { it.scrollIntensityScore }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh(force = true) }

    val context = LocalContext.current
    val pm = context.packageManager

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
                title = { Text(stringResource(R.string.usage_stats_title)) },
                navigationIcon = {
                    IconButton(onClick = { navViewModel.onBackPress() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
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
                        onTimeFrameSelected = { viewModel.setTimeFrame(it) },
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
                                    stringResource(R.string.usage_stats_improves_over_time),
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

                if (state.topApps.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(stringResource(R.string.usage_stats_top_apps))
                            Box {
                                IconButton(onClick = { sortExpanded = true }) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.usage_stats_sort),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortExpanded,
                                    onDismissRequest = { sortExpanded = false }
                                ) {
                                    for (mode in TopAppsSort.values()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(mode.labelRes)) },
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
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        val displayedApps =
                            if (expanded) sortedApps else sortedApps.take(TOP_APPS_PREVIEW_COUNT)
                        StatsCard {
                            displayedApps.forEachIndexed { index, app ->
                                AppUsageRow(
                                    app = app,
                                    pm = pm,
                                    onClick = { selectedApp.value = app },
                                    showScrollInfo = sortMode == TopAppsSort.SCROLL_INTENSITY
                                )
                                if (index < displayedApps.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                            if (state.topApps.size > TOP_APPS_PREVIEW_COUNT) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                TextButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (expanded) stringResource(R.string.usage_stats_show_less)
                                        else stringResource(R.string.usage_stats_show_more)
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.topApps.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.usage_stats_no_data),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { NavigationUtil.openUsageAccessSettings(context) }) {
                                Text(stringResource(R.string.action_grantPermission))
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

        // Keep the selected-app reference in sync when topApps is rebuilt after a feature change.
        LaunchedEffect(state.topApps) {
            selectedApp.value?.let { old ->
                selectedApp.value = state.topApps.find { it.packageName == old.packageName }
            }
        }
    }
}
