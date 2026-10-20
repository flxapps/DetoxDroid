package com.flx_apps.digitaldetox.ui.screens.usage_stats

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DailyAppUsage
import com.flx_apps.digitaldetox.data.DailyWebsiteUsage
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository.Companion.HISTORY_RETENTION_MONTHS
import com.flx_apps.digitaldetox.data.repository.UsageSource
import com.flx_apps.digitaldetox.features.BreakDoomScrollingFeature
import com.flx_apps.digitaldetox.features.DisableAppsFeature
import com.flx_apps.digitaldetox.features.GrayscaleAppsFeature
import com.flx_apps.digitaldetox.features.UsageStatsTracker
import com.flx_apps.digitaldetox.system_integration.TodaySummary
import com.flx_apps.digitaldetox.system_integration.UsageStatsProvider
import com.flx_apps.digitaldetox.system_integration.screenTimeMs
import com.flx_apps.digitaldetox.util.DistancePerspective
import com.flx_apps.digitaldetox.util.UsageAccessUtil
import com.flx_apps.digitaldetox.util.appCategory
import com.flx_apps.digitaldetox.util.appLabel
import com.flx_apps.digitaldetox.util.startOfDayMillis
import com.flx_apps.digitaldetox.websites.InstalledBrowsers
import com.flx_apps.digitaldetox.websites.WebsiteCollections
import com.flx_apps.digitaldetox.websites.Websites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    application: Application,
    private val repository: UsageStatsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UsageStatsUiState())
    val uiState: StateFlow<UsageStatsUiState> = _uiState

    /** Daily screen time of a single app over the last [APP_TREND_DAYS] days (for the sparkline). */
    private val _appTrend = MutableStateFlow<List<Pair<LocalDate, Long>>>(emptyList())
    val appTrend: StateFlow<List<Pair<LocalDate, Long>>> = _appTrend

    private var lastSnapshotElapsedMs = 0L

    /**
     * What the user has left out of the numbers, read once per refresh so that every aggregate in
     * one snapshot is built from the same answer.
     */
    private var exclusions = UsageExclusions(application)

    /**
     * Which instrument this snapshot is built from. Read once per refresh so one snapshot cannot
     * mix the two, and re-read every time because the grant can be given while the screen is open.
     */
    private var source: UsageSource = UsageSource.OS

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val browsers = InstalledBrowsers.find(getApplication()).readable.map { it.label }
            _uiState.update { it.copy(readableBrowsers = browsers) }
        }
    }

    /** The name of [categoryId], or "Other" for one the system has no name for. */
    private fun categoryLabel(categoryId: Int): String {
        val context = getApplication<Application>()
        return ApplicationInfo.getCategoryTitle(context, categoryId)?.toString()
            ?: context.getString(R.string.appCategories_other)
    }

    fun setTimeFrame(timeFrame: TimeFrame) {
        _uiState.update { it.copy(timeFrame = timeFrame, selectedDay = null) }
        refresh()
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _uiState.update {
            it.copy(
                timeFrame = TimeFrame.CUSTOM,
                customStart = minOf(start, end),
                customEnd = maxOf(start, end),
                selectedDay = null
            )
        }
        refresh()
    }

    /** Focuses top apps, categories and hour charts on [date]; null clears the focus again. */
    fun selectDay(date: LocalDate?) {
        _uiState.update { it.copy(selectedDay = date) }
        refresh()
    }

    /** Sets the opt-in hourly rate for the life-cost reframing (0 disables it). */
    fun setHourlyRate(rate: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageStatsSettings.hourlyRate = rate
            _uiState.update { it.copy(hourlyRate = rate) }
        }
    }

    /** Leaves [packageName] out of the numbers, or counts it again. */
    fun setAppExcluded(packageName: String, excluded: Boolean) {
        UsageStatsSettings.excludedApps = if (excluded) {
            UsageStatsSettings.excludedApps + packageName
        } else {
            UsageStatsSettings.excludedApps - packageName
        }
        refresh()
    }

    /** Leaves a whole [ApplicationInfo.category] out of the numbers, or counts it again. */
    fun setCategoryExcluded(categoryId: Int, excluded: Boolean) {
        UsageStatsSettings.excludedCategories = if (excluded) {
            UsageStatsSettings.excludedCategories + categoryId
        } else {
            UsageStatsSettings.excludedCategories - categoryId
        }
        refresh()
    }

    /**
     * Starts or stops counting the time on websites. Stopping deletes what was counted: off means
     * no list of sites is kept on the phone at all.
     */
    fun setCountWebsites(count: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            UsageStatsSettings.countWebsites = count
            if (!count) repository.deleteWebsiteUsage()
            refresh()
        }
    }

    /**
     * Reloads the screen. [force] additionally snapshots today's live counters into Room even if
     * a snapshot was taken recently (used by pull-to-refresh and the initial load).
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                withContext(Dispatchers.IO) {
                    maybeSnapshotToday(force)
                    refreshHistoryMetadata()
                    load()
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun maybeSnapshotToday(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSnapshotElapsedMs < SNAPSHOT_THROTTLE_MS) return
        try {
            repository.snapshotToday()
            lastSnapshotElapsedMs = now
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh today's usage snapshot")
        }
    }

    private suspend fun refreshHistoryMetadata() {
        exclusions = UsageExclusions(getApplication())
        source = if (UsageAccessUtil.hasUsageAccess(getApplication())) {
            UsageSource.OS
        } else {
            UsageSource.SELF_MEASURED
        }
        val retentionStart = LocalDate.now().minusMonths(HISTORY_RETENTION_MONTHS)
        val historyDays = repository.getHistoricalDayCount(retentionStart, LocalDate.now(), source)
        val earliest = repository.getEarliestHistoryDate(source)
        _uiState.update {
            it.copy(
                availableHistoryDays = historyDays,
                earliestHistoryDate = earliest,
                hourlyRate = UsageStatsSettings.hourlyRate,
                isSelfMeasured = source == UsageSource.SELF_MEASURED,
                countsWebsites = UsageStatsSettings.countWebsites
            )
        }
    }

    private suspend fun load() {
        val state = _uiState.value
        val today = LocalDate.now()
        when (state.timeFrame) {
            TimeFrame.TODAY -> loadToday(today)
            TimeFrame.CUSTOM -> loadRange(state.customStart, state.customEnd, computeDeltas = false)
            else -> loadRange(
                start = today.minusDays(state.timeFrame.days - 1L),
                end = today,
                computeDeltas = true
            )
        }
    }

    private suspend fun loadToday(today: LocalDate) {
        val zone = ZoneId.systemDefault()
        val startMs = today.startOfDayMillis(zone)
        val nowMs = System.currentTimeMillis()

        val readsOs = source == UsageSource.OS
        val events = if (readsOs) {
            UsageStatsProvider.queryEventCounts(startMs, nowMs) { !exclusions.excludes(it) }
        } else {
            UsageStatsProvider.EventCounts.EMPTY
        }
        val sessionCounts = UsageStatsProvider.groupSessionCounts(events.sessions)

        val allApps = if (readsOs) {
            UsageStatsProvider.queryForPeriod(startMs, nowMs).mapValues { (pkg, stat) ->
                AggregatedAppStats(
                    packageName = pkg,
                    totalTimeMs = stat.screenTimeMs,
                    launchCount = events.launchCounts[pkg] ?: 0,
                    sessionCount = sessionCounts[pkg] ?: 0,
                    scrollCount = UsageStatsTracker.scrollEventCounter.countFor(pkg),
                    scrollDistancePx = UsageStatsTracker.scrollDistanceCounter.countFor(pkg).toLong()
                )
            }
        } else {
            selfMeasuredToday()
        }
        val perApp = allApps.filterKeys { !exclusions.excludes(it) }
        val total = perApp.values.sumOf { it.totalTimeMs }

        val yesterdayStartMs =
            today.minusDays(1).startOfDayMillis(zone)
        val yesterday = today.minusDays(1)
        val yesterdayTotal = if (readsOs) {
            UsageStatsProvider.queryForPeriod(yesterdayStartMs, startMs)
                .filterKeys { !exclusions.excludes(it) }.values.sumOf { it.screenTimeMs }
        } else {
            repository.getHistorical(yesterday, yesterday, source)
                .filterNot { exclusions.excludes(it.packageName) }.sumOf { it.totalTimeMs }
        }
        val yesterdayUnlocks = if (readsOs) {
            UsageStatsProvider.queryEventCounts(yesterdayStartMs, startMs).unlockCount
        } else 0

        // break/block counts come from Room (refreshed by the snapshot above): unlike the live
        // in-memory counters, the persisted counts survive process restarts
        val todayRows = repository.getHistorical(today, today)
        val detoxImpact = buildDetoxImpact(
            breakCount = todayRows.sumOf { it.breakCount },
            blockCount = todayRows.sumOf { it.blockCount },
            grayscaleTimeMs = GrayscaleAppsFeature.currentGrayscaleTimeMs(),
            colorScreenTimeRemainingMs = TodaySummary.colorBudget()?.leftMs
        )

        _uiState.update {
            it.copy(
                totalScreenTime = total,
                totalDelta = if (yesterdayTotal > 0) total - yesterdayTotal else null,
                topApps = buildTopApps(perApp),
                byDay = listOf(DailyUsage(today, total)),
                hourBuckets = events.hourBuckets,
                unlockCount = events.unlockCount,
                unlockDelta = if (yesterdayUnlocks > 0) events.unlockCount - yesterdayUnlocks else null,
                unlockHourBuckets = events.unlockHourBuckets,
                categoryDistribution = buildCategoryDistribution(perApp),
                weekdayAverages = FloatArray(7),
                detoxImpact = detoxImpact,
                effectiveDays = 1,
                scrollDistanceMeters = pixelsToMeters(perApp.values.sumOf { it.scrollDistancePx }),
                excludedApps = buildExcludedApps(allApps),
                categories = buildCategoryFilters(allApps),
                topWebsites = if (UsageStatsSettings.countWebsites) {
                    buildTopWebsites(
                        UsageStatsTracker.websiteTimeRecorder.snapshot().mapValues { (_, time) ->
                            time.foregroundMs to time.launchCount
                        }
                    )
                } else emptyList()
            )
        }
    }

    private suspend fun loadRange(start: LocalDate, end: LocalDate, computeDeltas: Boolean) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)

        val readsOs = source == UsageSource.OS
        val historical = repository.getHistorical(start, end, source)
        val allRows =
            if (readsOs) withBackfilledRecentDays(historical, start, end) else historical
        val rows = allRows.filterNot { exclusions.excludes(it.packageName) }

        // per-day totals, zero-filled so the time axis has no silent gaps
        val totalsByDate = rows.groupBy { it.date }
            .mapValues { (_, dayRows) -> dayRows.sumOf { it.totalTimeMs } }
        val byDay = (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            DailyUsage(date, totalsByDate[date] ?: 0L)
        }
        val total = byDay.sumOf { it.totalTimeMs }

        val selectedDay = _uiState.value.selectedDay
        val focusRows = if (selectedDay != null) rows.filter { it.date == selectedDay } else rows
        val allFocusRows =
            if (selectedDay != null) allRows.filter { it.date == selectedDay } else allRows
        val allPerApp = aggregateByPackage(allFocusRows, emptyMap(), emptyMap())
        val focusStart = selectedDay ?: start
        val focusEnd = selectedDay ?: end

        val eventsStartMs = focusStart.startOfDayMillis(zone)
        val eventsEndMs = if (focusEnd >= today) {
            System.currentTimeMillis()
        } else {
            focusEnd.plusDays(1).startOfDayMillis(zone)
        }
        val events = if (readsOs) {
            UsageStatsProvider.queryEventCounts(eventsStartMs, eventsEndMs) {
                !exclusions.excludes(it)
            }
        } else {
            UsageStatsProvider.EventCounts.EMPTY
        }
        val sessionCounts = UsageStatsProvider.groupSessionCounts(events.sessions)

        val perApp = aggregateByPackage(focusRows, events.launchCounts, sessionCounts)

        // Unlocks and hour histograms: for ranges beyond the OS event-log retention (and for
        // selected days that may lie beyond it), prefer the persisted daily device stats. Short
        // ranges keep using the live event log, which also covers days DetoxDroid wasn't running.
        // The persisted buckets are device-level with no per-app breakdown, so for those ranges the
        // "when" chart still counts apps the user left out of everything else.
        val deviceRows = when {
            !readsOs -> emptyList()
            selectedDay != null -> repository.getDeviceStats(selectedDay, selectedDay)
            days > EVENT_LOG_RELIABLE_DAYS -> repository.getDeviceStats(start, end)
            else -> emptyList()
        }
        val unlocksFromHistory = deviceRows.isNotEmpty()
        val unlockCount: Int
        val unlockHourBuckets: IntArray
        val launchHourBuckets: IntArray
        if (unlocksFromHistory) {
            unlockCount = deviceRows.sumOf { it.unlockCount }
            unlockHourBuckets = sumHourBuckets(deviceRows.map { it.unlockHourBuckets })
            launchHourBuckets = sumHourBuckets(deviceRows.map { it.launchHourBuckets })
        } else {
            unlockCount = events.unlockCount
            unlockHourBuckets = events.unlockHourBuckets
            launchHourBuckets = events.hourBuckets
        }

        var totalDelta: Long? = null
        var unlockDelta: Int? = null
        if (computeDeltas && selectedDay == null) {
            val deltas = computePreviousPeriodDeltas(
                start, days, total, unlockCount, unlocksFromHistory, zone
            )
            totalDelta = deltas.first
            unlockDelta = deltas.second
        }

        val detoxImpact = buildDetoxImpact(
            breakCount = focusRows.sumOf { it.breakCount },
            blockCount = focusRows.sumOf { it.blockCount },
            grayscaleTimeMs = repository.getTotalGrayscaleTimeInRange(focusStart, focusEnd),
            colorScreenTimeRemainingMs = null
        )

        _uiState.update {
            it.copy(
                totalScreenTime = total,
                totalDelta = totalDelta,
                topApps = buildTopApps(perApp),
                byDay = byDay,
                hourBuckets = launchHourBuckets,
                unlockCount = unlockCount,
                unlockDelta = unlockDelta,
                unlockHourBuckets = unlockHourBuckets,
                categoryDistribution = buildCategoryDistribution(perApp),
                weekdayAverages = weekdayAverages(byDay),
                detoxImpact = detoxImpact,
                effectiveDays = days,
                scrollDistanceMeters = pixelsToMeters(perApp.values.sumOf { it.scrollDistancePx }),
                excludedApps = buildExcludedApps(allPerApp),
                categories = buildCategoryFilters(allPerApp),
                topWebsites = if (UsageStatsSettings.countWebsites) {
                    buildTopWebsites(
                        repository.getWebsiteUsage(focusStart, focusEnd).groupBy { it.site }
                            .mapValues { (_, days) ->
                                days.sumOf(DailyWebsiteUsage::timeMs) to
                                        days.sumOf(DailyWebsiteUsage::visitCount)
                            }
                    )
                } else emptyList()
            )
        }
    }

    /**
     * The sites worth a row, from their time and visits ([perSite]), the way [buildTopApps] picks
     * the apps.
     */
    private fun buildTopWebsites(perSite: Map<String, Pair<Long, Int>>): List<AppUsageStat> {
        loadListedCollections()
        return perSite.filterValues { (timeMs, _) -> timeMs >= MIN_APP_FOREGROUND_TIME_MS }
            .map { (site, numbers) ->
                val (timeMs, visits) = numbers
                AppUsageStat(
                    packageName = Websites.entryOfSite(site),
                    label = site,
                    totalTimeMs = timeMs,
                    launchCount = visits,
                    sessionCount = visits,
                    scrollEventCount = 0,
                    scrollIntensityScore = 0f,
                    scrollsPerMinute = 0f,
                    scrollDistanceMeters = 0.0,
                    detoxBadges = websiteBadgesFor(site)
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    /**
     * Reads the collections the features list, so a site one of them holds gets its badge on the
     * first load. Blocking, but each file only once per process.
     */
    private fun loadListedCollections() {
        listOf(GrayscaleAppsFeature, DisableAppsFeature, BreakDoomScrollingFeature)
            .flatMap { feature -> feature.appExceptions.mapNotNull(Websites::collectionOf) }
            .toSet()
            .forEach { WebsiteCollections.load(getApplication(), it) }
    }

    /** What each feature does on [site] as a website, like [detoxBadgesFor] for an app. */
    private fun websiteBadgesFor(site: String): List<DetoxBadge> = buildList {
        if (GrayscaleAppsFeature.isActivated && Websites.covers(GrayscaleAppsFeature, site)) {
            add(DetoxBadge.GRAYSCALE)
        }
        if (DisableAppsFeature.isActivated && Websites.covers(DisableAppsFeature, site)) {
            add(DetoxBadge.DISABLED)
        }
        if (BreakDoomScrollingFeature.isActivated &&
            Websites.covers(BreakDoomScrollingFeature, site)
        ) {
            add(DetoxBadge.DOOM_SCROLL)
        }
    }

    private fun sumHourBuckets(buckets: List<IntArray>): IntArray {
        return IntArray(24) { hour -> buckets.sumOf { it.getOrElse(hour) { 0 } } }
    }

    /**
     * Room only has rows for days DetoxDroid was tracking, while the OS keeps roughly a week of
     * per-day usage buckets. Backfills range days missing from Room with the OS data so recent
     * history isn't shown as empty (backfilled days carry screen time only, no counters).
     */
    private fun withBackfilledRecentDays(
        rows: List<DailyAppUsage>, start: LocalDate, end: LocalDate
    ): List<DailyAppUsage> {
        val daysWithData = rows.mapTo(mutableSetOf()) { it.date }
        val backfilled = rows.toMutableList()
        for ((date, stats) in UsageStatsProvider.queryDailyUsage(BACKFILL_DAYS)) {
            if (date < start || date > end || date in daysWithData) continue
            stats.mapTo(backfilled) { (pkg, stat) ->
                DailyAppUsage(
                    rowId = DailyAppUsage.createRowId(date, pkg),
                    date = date,
                    packageName = pkg,
                    totalTimeMs = stat.screenTimeMs,
                    sessionCount = 0,
                    launchCount = 0,
                    scrollCount = 0,
                    breakCount = 0,
                    blockCount = 0
                )
            }
        }
        return backfilled
    }

    private fun aggregateByPackage(
        rows: List<DailyAppUsage>,
        eventLaunchCounts: Map<String, Int>,
        eventSessionCounts: Map<String, Int>
    ): Map<String, AggregatedAppStats> {
        return rows.groupBy { it.packageName }.mapValues { (pkg, pkgRows) ->
            // Both sources can only undercount: the OS event log is truncated to its retention
            // window (about a week), and Room misses days DetoxDroid wasn't tracking (e.g.
            // backfilled days). Taking the max of the two therefore gives the best estimate.
            AggregatedAppStats(
                packageName = pkg,
                totalTimeMs = pkgRows.sumOf { it.totalTimeMs },
                launchCount = maxOf(pkgRows.sumOf { it.launchCount }, eventLaunchCounts[pkg] ?: 0),
                sessionCount = maxOf(pkgRows.sumOf { it.sessionCount }, eventSessionCounts[pkg] ?: 0),
                scrollCount = pkgRows.sumOf { it.scrollCount },
                scrollDistancePx = pkgRows.sumOf { it.scrollDistancePx.toLong() }
            )
        }
    }

    /**
     * Screen-time and unlock deltas vs. the period of equal length directly before [start].
     * Short frames use the OS stats; longer frames use Room history, and only when it covers at
     * least half of the previous period (otherwise the comparison would be wildly misleading).
     * For long frames, unlock deltas additionally require that the current unlocks came from the
     * persisted device stats ([currentUnlocksFromHistory]) so both sides measure the same thing.
     */
    private suspend fun computePreviousPeriodDeltas(
        start: LocalDate,
        days: Int,
        currentTotal: Long,
        currentUnlocks: Int,
        currentUnlocksFromHistory: Boolean,
        zone: ZoneId
    ): Pair<Long?, Int?> {
        val prevStart = start.minusDays(days.toLong())
        val prevEnd = start.minusDays(1)
        val prevStartMs = prevStart.startOfDayMillis(zone)
        val prevEndMs = start.startOfDayMillis(zone)
        val minCoverageDays = (days + 1) / 2

        if (days > EVENT_LOG_RELIABLE_DAYS || source == UsageSource.SELF_MEASURED) {
            val totalDelta = if (repository.getHistoricalDayCount(prevStart, prevEnd, source) >= minCoverageDays) {
                currentTotal - previousRoomTotal(prevStart, prevEnd)
            } else null
            val unlockDelta = if (
                currentUnlocksFromHistory &&
                repository.getDeviceStatsDayCount(prevStart, prevEnd) >= minCoverageDays
            ) {
                currentUnlocks - repository.getTotalUnlocksInRange(prevStart, prevEnd)
            } else null
            return totalDelta to unlockDelta
        }

        val prevTotal = UsageStatsProvider.queryForPeriod(prevStartMs, prevEndMs)
            .filterKeys { !exclusions.excludes(it) }.values.sumOf { it.screenTimeMs }
        val prevUnlocks = UsageStatsProvider.queryEventCounts(prevStartMs, prevEndMs).unlockCount
        return (if (prevTotal > 0) currentTotal - prevTotal else null) to
            (if (prevUnlocks > 0) currentUnlocks - prevUnlocks else null)
    }

    /**
     * Screen time of the previous period out of Room. With nothing left out this is one SQL sum;
     * with exclusions it has to walk the per-app rows, which is why the cheap path is kept.
     */
    private suspend fun previousRoomTotal(start: LocalDate, end: LocalDate): Long {
        if (exclusions.isEmpty) return repository.getTotalScreenTimeInRange(start, end, source)
        return repository.getHistorical(start, end, source)
            .filterNot { exclusions.excludes(it.packageName) }.sumOf { it.totalTimeMs }
    }

    /**
     * Today's self-measured figures, taken live so the screen does not wait for the next
     * debounced write to Room.
     */
    private fun selfMeasuredToday(): Map<String, AggregatedAppStats> {
        val times = UsageStatsTracker.foregroundTimeRecorder.snapshot()
        return times.mapValues { (pkg, time) ->
            AggregatedAppStats(
                packageName = pkg,
                totalTimeMs = time.foregroundMs,
                launchCount = time.launchCount,
                sessionCount = time.launchCount,
                scrollCount = UsageStatsTracker.scrollEventCounter.countFor(pkg),
                scrollDistancePx = UsageStatsTracker.scrollDistanceCounter.countFor(pkg).toLong()
            )
        }
    }

    /** Converts odometer pixels to meters using the screen's physical DPI. */
    private fun pixelsToMeters(pixels: Long): Double = DistancePerspective.pixelsToMeters(
        pixels, getApplication<Application>().resources.displayMetrics
    )

    /** Average screen time per weekday (Mon..Sun), ignoring days without any recorded usage. */
    private fun weekdayAverages(byDay: List<DailyUsage>): FloatArray {
        val sums = LongArray(7)
        val counts = IntArray(7)
        for (day in byDay) {
            if (day.totalTimeMs <= 0L) continue
            val dayIndex = day.date.dayOfWeek.value - 1
            sums[dayIndex] += day.totalTimeMs
            counts[dayIndex]++
        }
        return FloatArray(7) { if (counts[it] > 0) sums[it].toFloat() / counts[it] else 0f }
    }

    private fun buildTopApps(perApp: Map<String, AggregatedAppStats>): List<AppUsageStat> {
        return perApp.values
            .filter { it.totalTimeMs >= MIN_APP_FOREGROUND_TIME_MS }
            .sortedByDescending { it.totalTimeMs }
            .map { toAppUsageStat(it) }
    }

    /**
     * The apps the user named to be left out, whether or not they were used in the period. Built
     * from the stored selection so that one with no usage in the selected period can still be
     * brought back. Apps left out through their category are not in here: the category is where
     * they are brought back.
     */
    private fun buildExcludedApps(allApps: Map<String, AggregatedAppStats>): List<AppUsageStat> {
        return exclusions.apps
            .map { pkg ->
                toAppUsageStat(
                    allApps[pkg] ?: AggregatedAppStats(pkg, 0L, 0, 0, 0, 0L)
                )
            }
            .sortedWith(compareByDescending<AppUsageStat> { it.totalTimeMs }.thenBy { it.label })
    }

    /**
     * Every category with usage in the period, so the sheet can offer the whole of it at once.
     * Categories that only the user's own exclusions would name are in here as well, because a
     * category whose apps are all left out still has to be reachable to undo that.
     */
    private fun buildCategoryFilters(
        allApps: Map<String, AggregatedAppStats>
    ): List<UsageCategoryFilter> {
        val context = getApplication<Application>()
        val timeByCategory = mutableMapOf<Int, Long>()
        for ((pkg, stat) in allApps) {
            timeByCategory.merge(context.appCategory(pkg), stat.totalTimeMs, Long::plus)
        }
        exclusions.categories.forEach { timeByCategory.putIfAbsent(it, 0L) }
        return timeByCategory.entries
            .map { (categoryId, totalTimeMs) ->
                UsageCategoryFilter(
                    categoryId = categoryId,
                    label = categoryLabel(categoryId),
                    totalTimeMs = totalTimeMs,
                    isExcluded = categoryId in exclusions.categories
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    private fun toAppUsageStat(stat: AggregatedAppStats): AppUsageStat {
        return AppUsageStat(
            packageName = stat.packageName,
            label = getApplication<Application>().appLabel(stat.packageName),
            totalTimeMs = stat.totalTimeMs,
            launchCount = stat.launchCount,
            sessionCount = stat.sessionCount,
            scrollEventCount = stat.scrollCount,
            scrollIntensityScore = scrollIntensityScore(stat.scrollCount, stat.totalTimeMs),
            scrollsPerMinute = scrollsPerMinute(stat.scrollCount, stat.totalTimeMs),
            scrollDistanceMeters = pixelsToMeters(stat.scrollDistancePx),
            detoxBadges = detoxBadgesFor(stat.packageName)
        )
    }

    private fun detoxBadgesFor(packageName: String): List<DetoxBadge> = buildList {
        if (GrayscaleAppsFeature.isActivated && GrayscaleAppsFeature.appliesTo(packageName)) {
            add(DetoxBadge.GRAYSCALE)
        }
        if (DisableAppsFeature.isActivated && DisableAppsFeature.disableableApps.contains(packageName)) {
            add(DetoxBadge.DISABLED)
        }
        if (BreakDoomScrollingFeature.isActivated && BreakDoomScrollingFeature.appliesTo(packageName)) {
            add(DetoxBadge.DOOM_SCROLL)
        }
    }

    private fun buildDetoxImpact(
        breakCount: Int?,
        blockCount: Int?,
        grayscaleTimeMs: Long?,
        colorScreenTimeRemainingMs: Long?
    ): DetoxImpactData? {
        val doomScrollingActive = BreakDoomScrollingFeature.isActivated
        val disableAppsActive = DisableAppsFeature.isActivated
        val grayscaleActive = GrayscaleAppsFeature.isActivated

        if (!grayscaleActive && !doomScrollingActive && !disableAppsActive) return null

        return DetoxImpactData(
            colorScreenTimeRemainingMs = colorScreenTimeRemainingMs,
            grayscaleTimeMs = if (grayscaleActive) grayscaleTimeMs else null,
            doomScrollBreakCount = if (doomScrollingActive) breakCount else null,
            appsBlockedCount = if (disableAppsActive) blockCount else null
        )
    }

    private fun buildCategoryDistribution(
        perApp: Map<String, AggregatedAppStats>
    ): Map<String, Long> {
        val context = getApplication<Application>()
        val byCategory = mutableMapOf<String, Long>()
        for ((pkg, stat) in perApp) {
            byCategory.merge(categoryLabel(context.appCategory(pkg)), stat.totalTimeMs, Long::plus)
        }
        return byCategory.entries.sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    /** Loads the sparkline of [packageName], an app's package or a website's entry. */
    fun loadAppTrend(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val end = LocalDate.now()
            val start = end.minusDays(APP_TREND_DAYS - 1L)
            val byDate = if (Websites.isWebsite(packageName)) {
                val site = Websites.displayName(getApplication(), packageName)
                repository.getSiteHistorical(site, start, end)
                    .associate { it.date to it.timeMs }
            } else {
                repository.getAppHistorical(packageName, start, end, source)
                    .associate { it.date to it.totalTimeMs }
            }
            _appTrend.value = (0 until APP_TREND_DAYS).map { offset ->
                val date = start.plusDays(offset.toLong())
                date to (byDate[date] ?: 0L)
            }
        }
    }

    companion object {
        /** Apps with less than one minute of foreground time are hidden from the top-apps list. */
        private const val MIN_APP_FOREGROUND_TIME_MS = 60_000L

        /** Number of days (inclusive of today) shown in the per-app trend sparkline. */
        private const val APP_TREND_DAYS = 7

        /** How many recent days are backfilled from OS data when Room has no rows for them. */
        private const val BACKFILL_DAYS = 7

        /**
         * Ranges up to this many days can rely on the OS usage stats / event log for
         * previous-period comparisons; anything longer needs the Room history.
         */
        private const val EVENT_LOG_RELIABLE_DAYS = 7

        /** Reloads triggered by selection changes reuse a snapshot younger than this. */
        private const val SNAPSHOT_THROTTLE_MS = 30_000L

        /**
         * Composite scroll-intensity metric: scroll count weighted by scroll rate,
         * `scrollCount * sqrt(scrollsPerMinute)`. Volume dominates, so brief bursts
         * can't outrank sustained doom-scrolling, but sqrt gives bursty sessions
         * more weight than ln. Foreground time is clamped to a minimum of 0.1 minute
         * so sub-second sessions don't inflate the rate.
         */
        internal fun scrollIntensityScore(scrollCount: Int, totalTimeMs: Long): Float {
            if (scrollCount <= 0 || totalTimeMs <= 0) return 0f
            return scrollCount * sqrt(scrollsPerMinute(scrollCount, totalTimeMs))
        }

        /** Scrolls per minute, clamped to a 0.1-min floor for sub-second sessions. */
        internal fun scrollsPerMinute(scrollCount: Int, totalTimeMs: Long): Float {
            if (scrollCount <= 0 || totalTimeMs <= 0) return 0f
            val minutes = (totalTimeMs / 60000f).coerceAtLeast(0.1f)
            return scrollCount / minutes
        }
    }
}
