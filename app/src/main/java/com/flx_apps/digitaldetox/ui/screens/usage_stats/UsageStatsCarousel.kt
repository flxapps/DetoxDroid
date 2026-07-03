package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.util.formatDurationMsShort
import java.time.LocalDate

/**
 * Fixed height of the whole carousel card content (header + chart). Keeping it constant across
 * pages — instead of sizing each chart individually — ensures all cards line up regardless of
 * whether their header has a subtitle.
 */
val CAROUSEL_CARD_HEIGHT = 280.dp
val CARD_SHAPE = RoundedCornerShape(16.dp)
val CARD_INNER_PADDING = 12.dp

private enum class TrendsPage(val titleRes: Int?) {
    SUMMARY(titleRes = null),
    PERSPECTIVE(R.string.usage_stats_perspective),
    BY_DAY(R.string.usage_stats_by_day),
    TIME_OF_DAY(R.string.usage_stats_time_of_day),
    WEEKDAY(R.string.usage_stats_weekday_averages),
    UNLOCKS(R.string.usage_stats_screen_unlocks),
    CATEGORIES(R.string.usage_stats_app_categories),
    SCROLL_INTENSITY(R.string.usage_stats_scroll_intensity)
}

@Composable
fun TrendsCarousel(
    state: UsageStatsUiState,
    onDaySelected: (LocalDate?) -> Unit,
    onHourlyRateChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val showWeekday = state.timeFrame == TimeFrame.LAST_30_DAYS ||
        state.timeFrame == TimeFrame.LAST_90_DAYS ||
        state.timeFrame == TimeFrame.CUSTOM
    val pages = buildList {
        add(TrendsPage.SUMMARY)
        if (hasPerspectiveContent(state)) add(TrendsPage.PERSPECTIVE)
        if (state.timeFrame != TimeFrame.TODAY && state.byDay.size > 1) add(TrendsPage.BY_DAY)
        if (state.hourBuckets.sum() > 0) add(TrendsPage.TIME_OF_DAY)
        if (showWeekday) add(TrendsPage.WEEKDAY)
        if (state.unlockCount > 0) add(TrendsPage.UNLOCKS)
        if (state.categoryDistribution.isNotEmpty()) add(TrendsPage.CATEGORIES)
        if (state.topApps.any { it.scrollIntensityScore > 0f }) add(TrendsPage.SCROLL_INTENSITY)
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 8.dp,
        ) { pageIndex ->
            val page = pages[pageIndex]
            StatsCard(modifier = Modifier.height(CAROUSEL_CARD_HEIGHT)) {
                when (page) {
                    TrendsPage.SUMMARY -> Text(
                        stringResource(state.timeFrame.labelRes),
                        style = MaterialTheme.typography.titleSmall
                    )

                    TrendsPage.BY_DAY -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.usage_stats_by_day),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (state.effectiveDays > 1) {
                            val avgMs = state.totalScreenTime / state.effectiveDays
                            Text(
                                text = stringResource(
                                    R.string.usage_stats_daily_average,
                                    formatDurationMsShort(context, avgMs)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TrendsPage.PERSPECTIVE -> Column {
                        Text(
                            stringResource(R.string.usage_stats_perspective),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.usage_stats_perspective_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TrendsPage.SCROLL_INTENSITY -> Column {
                        Text(
                            stringResource(R.string.usage_stats_scroll_intensity),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.usage_stats_scroll_intensity_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> Text(
                        stringResource(page.titleRes!!),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        TrendsPage.SUMMARY -> SummaryCardContent(
                            totalMs = state.totalScreenTime,
                            deltaMs = state.totalDelta,
                            timeFrame = state.timeFrame,
                            effectiveDays = state.effectiveDays,
                            detoxImpact = state.detoxImpact
                        )

                        TrendsPage.PERSPECTIVE -> PerspectiveCardContent(
                            totalScreenTimeMs = state.totalScreenTime,
                            scrollDistanceMeters = state.scrollDistanceMeters,
                            hourlyRate = state.hourlyRate,
                            onHourlyRateChanged = onHourlyRateChanged
                        )

                        TrendsPage.BY_DAY -> ByDayBarChart(
                            dailyUsage = state.byDay,
                            selectedDay = state.selectedDay,
                            onDaySelected = onDaySelected
                        )

                        TrendsPage.TIME_OF_DAY -> TimeOfDayBarChart(state.hourBuckets)

                        TrendsPage.WEEKDAY -> WeekdayAveragesChart(state.weekdayAverages)

                        TrendsPage.UNLOCKS -> ScreenUnlocksChart(
                            unlockCount = state.unlockCount,
                            unlockDelta = state.unlockDelta,
                            unlockHourBuckets = state.unlockHourBuckets
                        )

                        TrendsPage.CATEGORIES -> CategoryDonutChart(state.categoryDistribution)

                        TrendsPage.SCROLL_INTENSITY -> ScrollIntensityChart(state.topApps)
                    }
                }
            }
        }
        if (pages.size > 1) {
            PageDots(
                currentPage = pagerState.currentPage,
                pageCount = pages.size,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape = CARD_SHAPE,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            // padding first, so a height passed via [modifier] constrains the content area itself
            modifier = Modifier
                .padding(CARD_INNER_PADDING)
                .then(modifier),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun PageDots(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { i ->
            val selected = currentPage == i
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
