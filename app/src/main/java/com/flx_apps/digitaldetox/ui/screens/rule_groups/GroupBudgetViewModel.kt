package com.flx_apps.digitaldetox.ui.screens.rule_groups

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import com.flx_apps.digitaldetox.rules.BudgetPeriod
import com.flx_apps.digitaldetox.rules.GroupId
import com.flx_apps.digitaldetox.rules.RuleGroup
import com.flx_apps.digitaldetox.rules.RuleGroups
import com.flx_apps.digitaldetox.ui.screens.usage_stats.UsageStatsSettings
import com.flx_apps.digitaldetox.websites.Websites
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.olshevski.navigation.reimagined.hilt.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The time a group's apps were used on one of the last days, for the budget to be set against. */
data class DayUsage(val date: LocalDate, val ms: Long)

/**
 * Edits a group's budget. The amount and the period are a draft until [save]: the chart below
 * them is meant to be read before deciding, and a budget that changed with every tap of the
 * stepper would lock apps while the user is still thinking.
 */
@HiltViewModel
class GroupBudgetViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val repository: UsageStatsRepository,
) : AndroidViewModel(application) {
    companion object {
        /** How many days the chart shows. */
        const val DAYS = 7

        /** The step of the minus and plus buttons, in minutes. */
        const val STEP_MINUTES = 5

        /** The budgets on offer as chips, in minutes. */
        val Presets = listOf(15, 30, 60)

        @Composable
        fun withGroupId(groupId: GroupId): GroupBudgetViewModel =
            hiltViewModel(defaultArguments = bundleOf("groupId" to groupId))
    }

    val groupId: GroupId = savedStateHandle["groupId"]!!

    val group: RuleGroup? get() = RuleGroups.get(groupId)

    private val _period = MutableStateFlow(group?.budgetPeriod ?: BudgetPeriod.DAY)
    val period: StateFlow<BudgetPeriod> = _period

    /** The draft in minutes; 0 stands for no limit. */
    private val _minutes = MutableStateFlow(
        group?.takeIf { it.hasBudget }?.let { TimeUnit.MILLISECONDS.toMinutes(it.budgetMs).toInt() } ?: 0
    )
    val minutes: StateFlow<Int> = _minutes

    /** The last [DAYS] days, oldest first; null while they are read. */
    private val _lastDays = MutableStateFlow<List<DayUsage>?>(null)
    val lastDays: StateFlow<List<DayUsage>?> = _lastDays

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _lastDays.value = runCatching { readLastDays() }
                .onFailure { Timber.w(it, "Could not read the group's last days") }
                .getOrDefault(emptyList())
        }
    }

    /**
     * The group's apps' time per day over the last week, from the usage records DetoxDroid keeps,
     * plus the time on its websites where that is counted.
     */
    private suspend fun readLastDays(): List<DayUsage> {
        val group = group ?: return emptyList()
        val today = LocalDate.now()
        val start = today.minusDays((DAYS - 1).toLong())
        val apps = Websites.appsOf(group.appExceptions)
        val byDay = HashMap<LocalDate, Long>()
        repository.getHistorical(start, today)
            .filter { it.packageName in apps }
            .forEach { byDay.merge(it.date, it.totalTimeMs, Long::plus) }
        if (UsageStatsSettings.countWebsites && Websites.websitesOf(group.appExceptions).isNotEmpty()) {
            repository.getWebsiteUsage(start, today)
                .filter { Websites.covers(group, it.site) }
                .forEach { byDay.merge(it.date, it.timeMs, Long::plus) }
        }
        return (0 until DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayUsage(date, byDay[date] ?: 0L)
        }
    }

    fun setPeriod(period: BudgetPeriod) {
        _period.value = period
    }

    fun setMinutes(minutes: Int) {
        _minutes.value = minutes.coerceIn(0, 24 * 60)
    }

    fun step(direction: Int) {
        val current = _minutes.value
        val next = if (current == 0 && direction > 0) STEP_MINUTES else current + direction * STEP_MINUTES
        setMinutes(next.coerceAtLeast(0))
    }

    /**
     * Writes the draft to the group. A gray step that sat at the end of the budget stays at the
     * end: "in color for the budget, then gray" is a shape, not a number of minutes.
     */
    fun save() {
        val group = group ?: return
        val grayAtEnd = group.grays && !group.graysFromStart && group.grayAfterMs >= group.budgetMs
        group.budgetMs = if (_minutes.value > 0) {
            TimeUnit.MINUTES.toMillis(_minutes.value.toLong())
        } else {
            RuleGroup.NO_BUDGET
        }
        group.budgetPeriod = _period.value
        if (grayAtEnd && group.hasBudget) group.grayAfterMs = group.budgetMs
        RuleGroups.onChanged()
    }
}
