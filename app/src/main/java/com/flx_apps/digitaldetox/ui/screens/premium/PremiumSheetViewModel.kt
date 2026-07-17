package com.flx_apps.digitaldetox.ui.screens.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository
import com.flx_apps.digitaldetox.data.repository.UsageStatsRepository.Companion.HISTORY_RETENTION_MONTHS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/**
 * What DetoxDroid has already done for this user, summed over the retained history. Shown on the
 * support sheet: a tip request lands very differently next to "stepped in 47 times for you" than
 * next to a generic feature list.
 */
data class PremiumImpactStats(
    /** Doom-scroll breaks + app blocks — the moments DetoxDroid actively stepped in. */
    val interventionCount: Int,
    val grayscaleTimeMs: Long,
    val daysTracked: Int
) {
    val isEmpty: Boolean
        get() = interventionCount == 0 && grayscaleTimeMs == 0L && daysTracked == 0
}

@HiltViewModel
class PremiumSheetViewModel @Inject constructor(
    private val repository: UsageStatsRepository
) : ViewModel() {
    /** Null while loading; the sheet simply renders without the impact card until it arrives. */
    private val _impactStats = MutableStateFlow<PremiumImpactStats?>(null)
    val impactStats: StateFlow<PremiumImpactStats?> = _impactStats

    fun loadImpactStats() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val end = LocalDate.now()
                val start = end.minusMonths(HISTORY_RETENTION_MONTHS)
                val rows = repository.getHistorical(start, end)
                PremiumImpactStats(
                    interventionCount = rows.sumOf { it.breakCount + it.blockCount },
                    grayscaleTimeMs = repository.getTotalGrayscaleTimeInRange(start, end),
                    daysTracked = repository.getHistoricalDayCount(start, end)
                )
            }.onSuccess { _impactStats.value = it }
                .onFailure { Timber.w(it, "Could not load impact stats for the support sheet") }
        }
    }
}
