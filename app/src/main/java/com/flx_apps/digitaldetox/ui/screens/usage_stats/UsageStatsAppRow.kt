package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.util.toHrMinString
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppUsageRow(
    app: AppUsageStat,
    pm: android.content.pm.PackageManager,
    onClick: () -> Unit,
    showScrollInfo: Boolean = false
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (app.detoxBadges.isNotEmpty()) {
                    for (badge in app.detoxBadges) {
                        BadgeChip(badge)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                app.totalTimeMs.milliseconds.toHrMinString(context),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (showScrollInfo && app.scrollEventCount > 0) {
                val scrollInfo = stringResource(
                    R.string.usage_stats_scroll_intensity_detail,
                    app.scrollEventCount,
                    app.scrollsPerMinute
                )
                Text(
                    if (app.scrollDistanceMeters >= 1.0) {
                        "$scrollInfo · ${formatDistance(context, app.scrollDistanceMeters)}"
                    } else {
                        scrollInfo
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!showScrollInfo && app.launchCount > 0) {
                Text(
                    stringResource(R.string.usage_stats_launches, app.launchCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BadgeChip(badge: DetoxBadge) {
    val bgAlpha = if (isSystemInDarkTheme()) 0.25f else 0.15f
    val (label, color) = when (badge) {
        DetoxBadge.GRAYSCALE -> stringResource(R.string.usage_stats_badge_grayscale) to colorResource(
            id = R.color.purple
        )
        DetoxBadge.DISABLED -> stringResource(R.string.usage_stats_badge_disabled) to colorResource(
            id = R.color.pink
        )
        DetoxBadge.DOOM_SCROLL -> stringResource(R.string.usage_stats_badge_doom_scroll) to colorResource(
            id = R.color.orange
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = bgAlpha)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
