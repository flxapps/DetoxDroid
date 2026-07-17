package com.flx_apps.digitaldetox.ui.screens.usage_stats

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.NumberPickerDialog
import com.flx_apps.digitaldetox.util.DistancePerspective
import java.text.NumberFormat
import java.util.Locale

/**
 * The "In Perspective" carousel page: reframes the period's abstract numbers as something
 * tangible — physical scroll distance with a landmark comparison, and (opt-in) what the screen
 * time is worth at the user's own hourly rate. Deliberately phrased as neutral observations,
 * not accusations.
 */
@Composable
fun PerspectiveCardContent(
    totalScreenTimeMs: Long,
    scrollDistanceMeters: Double,
    hourlyRate: Int,
    onHourlyRateChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    var showRatePicker by remember { mutableStateOf(false) }

    if (showRatePicker) {
        val offLabel = stringResource(R.string.usage_stats_time_value_off)
        NumberPickerDialog(
            titleText = stringResource(R.string.usage_stats_time_value_setup),
            initialValue = hourlyRate,
            range = 0..500 step 5,
            label = { rate ->
                if (rate == 0) offLabel
                else context.getString(R.string.usage_stats_rate_per_hour, formatMoney(rate.toDouble()))
            },
            onValueSelected = onHourlyRateChanged,
            onDismissRequest = { showRatePicker = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        if (scrollDistanceMeters >= MIN_ODOMETER_METERS) {
            PerspectiveTile(
                icon = Icons.Default.Straighten,
                tint = colorResource(id = R.color.blue),
                onClick = null,
                trailingIcon = null
            ) {
                Text(
                    text = formatDistance(context, scrollDistanceMeters),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.usage_stats_odometer_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                comparisonText(context, scrollDistanceMeters)?.let { comparison ->
                    Text(
                        text = comparison,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colorResource(id = R.color.blue)
                    )
                }
            }
        }

        if (hourlyRate > 0) {
            PerspectiveTile(
                icon = Icons.Default.Savings,
                tint = colorResource(id = R.color.green),
                onClick = { showRatePicker = true },
                trailingIcon = Icons.Default.Edit
            ) {
                Text(
                    text = formatMoney(totalScreenTimeMs / MS_PER_HOUR * hourlyRate),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.usage_stats_time_value_label,
                        context.getString(
                            R.string.usage_stats_rate_per_hour, formatMoney(hourlyRate.toDouble())
                        )
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            PerspectiveTile(
                icon = Icons.Default.Savings,
                tint = colorResource(id = R.color.green),
                onClick = { showRatePicker = true },
                trailingIcon = Icons.Default.ChevronRight
            ) {
                Text(
                    text = stringResource(R.string.usage_stats_time_value_setup),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.usage_stats_time_value_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.usage_stats_perspective_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

/** A tinted stat tile with a round icon badge, used by the perspective card. */
@Composable
private fun PerspectiveTile(
    icon: ImageVector,
    tint: Color,
    onClick: (() -> Unit)?,
    trailingIcon: ImageVector?,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = tint.copy(alpha = if (isSystemInDarkTheme()) 0.22f else 0.12f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) { content() }
            if (trailingIcon != null) {
                Icon(
                    trailingIcon,
                    contentDescription = stringResource(R.string.usage_stats_time_value_setup),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Whether the perspective page has anything to show for this state. */
fun hasPerspectiveContent(state: UsageStatsUiState): Boolean =
    state.scrollDistanceMeters >= MIN_ODOMETER_METERS || state.totalScreenTime > 0

private const val MIN_ODOMETER_METERS = 1.0
private const val MS_PER_HOUR = 3_600_000.0

private fun comparisonText(context: Context, meters: Double): String? {
    return when (val comparison = DistancePerspective.comparisonFor(meters)) {
        is DistancePerspective.Comparison.Landmark -> context.getString(
            R.string.usage_stats_odometer_comparison,
            formatMultiplier(comparison.multiplier),
            context.getString(comparison.nameRes)
        )
        is DistancePerspective.Comparison.Floors ->
            context.getString(R.string.usage_stats_odometer_floors, comparison.floors)
        null -> null
    }
}

fun formatDistance(context: Context, meters: Double): String {
    return if (meters >= 1000) {
        context.getString(R.string.distance_kilometers, meters / 1000)
    } else {
        context.getString(R.string.distance_meters, meters.toInt())
    }
}

private fun formatMultiplier(multiplier: Double): String {
    return if (multiplier >= 10) {
        String.format(Locale.getDefault(), "%.0f", multiplier)
    } else {
        String.format(Locale.getDefault(), "%.1f", multiplier)
    }
}

private fun formatMoney(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.getDefault())
        .apply { maximumFractionDigits = 0 }
        .format(amount)
}
