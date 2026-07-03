package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryCardContent(
    totalMs: Long,
    deltaMs: Long?,
    timeFrame: TimeFrame,
    effectiveDays: Int,
    detoxImpact: DetoxImpactData?
) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.usage_stats_screen_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.animation.AnimatedContent(
                    targetState = totalMs.milliseconds.toHrMinString(context),
                    transitionSpec = {
                        androidx.compose.animation.slideInVertically { height -> height } togetherWith
                            androidx.compose.animation.slideOutVertically { height -> -height }
                    },
                    label = "screenTime"
                ) { animatedTime ->
                    Text(
                        text = animatedTime,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (timeFrame != TimeFrame.TODAY) {
                val days = effectiveDays.coerceAtLeast(1)
                val dailyAvg = totalMs / days
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dailyAvg.milliseconds.toHrMinString(context),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.usage_stats_daily_average_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        if (deltaMs != null) {
            val isUp = deltaMs >= 0
            val color = if (isUp) MaterialTheme.colorScheme.error else colorResource(id = R.color.green)
            val sign = if (isUp) "+" else ""
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$sign${deltaMs.milliseconds.toHrMinString(context)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (timeFrame == TimeFrame.TODAY) R.string.usage_stats_vs_yesterday
                        else R.string.usage_stats_vs_previous_period
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (detoxImpact != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.usage_stats_detox_impact),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    detoxImpact.grayscaleTimeMs?.let {
                        DetoxMetricChip(
                            value = it.milliseconds.toHrMinString(context),
                            label = stringResource(R.string.usage_stats_grayscale_time),
                            color = colorResource(id = R.color.purple)
                        )
                    }
                    detoxImpact.colorScreenTimeRemainingMs?.let {
                        DetoxMetricChip(
                            value = it.milliseconds.toHrMinString(context),
                            label = stringResource(R.string.usage_stats_color_remaining),
                            color = colorResource(id = R.color.green)
                        )
                    }
                    detoxImpact.doomScrollBreakCount?.let {
                        DetoxMetricChip(
                            value = it.toString(),
                            label = stringResource(R.string.usage_stats_scroll_breaks),
                            color = colorResource(id = R.color.orange)
                        )
                    }
                    detoxImpact.appsBlockedCount?.let {
                        DetoxMetricChip(
                            value = it.toString(),
                            label = stringResource(R.string.usage_stats_blocks),
                            color = colorResource(id = R.color.pink)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetoxMetricChip(label: String, value: String, color: Color) {
    val bgAlpha = if (isSystemInDarkTheme()) 0.25f else 0.15f
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = bgAlpha)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}
