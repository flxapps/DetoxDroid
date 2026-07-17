package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow

/** Longer time frames only become selectable once enough history has accumulated. */
const val MIN_DAYS_FOR_30D_FRAME = 23
const val MIN_DAYS_FOR_90D_FRAME = 60
const val MIN_DAYS_FOR_HISTORY_HINT = 30

@Composable
fun TimeFrameSelector(
    selectedTimeFrame: TimeFrame,
    availableHistoryDays: Int,
    isPremiumUnlocked: Boolean,
    onTimeFrameSelected: (TimeFrame) -> Unit,
    onLockedFrameClicked: (TimeFrame) -> Unit,
    onCustomClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleFrames = TimeFrame.values().filter { frame ->
        when (frame) {
            TimeFrame.LAST_30_DAYS -> availableHistoryDays >= MIN_DAYS_FOR_30D_FRAME
            TimeFrame.LAST_90_DAYS -> availableHistoryDays >= MIN_DAYS_FOR_90D_FRAME
            else -> true
        }
    }
    ChipFlowRow(modifier = modifier.padding(vertical = 4.dp)) {
        for (frame in visibleFrames) {
            val locked = frame.isPremium && !isPremiumUnlocked
            FilterChip(
                selected = !locked && selectedTimeFrame == frame,
                onClick = {
                    when {
                        locked -> onLockedFrameClicked(frame)
                        frame == TimeFrame.CUSTOM -> onCustomClicked()
                        else -> onTimeFrameSelected(frame)
                    }
                },
                label = { Text(stringResource(frame.labelRes)) },
                leadingIcon = when {
                    locked -> {
                        {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.premium_chip_lockedLabel),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    frame == TimeFrame.CUSTOM -> {
                        {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    else -> null
                }
            )
        }
    }
}
