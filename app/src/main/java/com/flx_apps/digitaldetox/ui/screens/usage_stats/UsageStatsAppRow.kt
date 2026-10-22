package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.rule_groups.colorRes
import com.flx_apps.digitaldetox.ui.theme.brandTone
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.util.formatDistance
import com.flx_apps.digitaldetox.ui.widgets.AppIcon
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AppUsageRow(
    app: AppUsageStat,
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
        UsageItemIcon(item = app, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                app.groups.forEach { GroupChip(it) }
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
                    R.string.usageStats_scrollIntensity_detail,
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
                    if (app.isWebsite) {
                        pluralStringResource(
                            R.plurals.usageStats_visits, app.launchCount, app.launchCount
                        )
                    } else {
                        stringResource(R.string.usageStats_launches, app.launchCount)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * An app's icon, or for a website a globe of the same size, since a site has no icon DetoxDroid
 * could show without fetching one.
 */
@Composable
fun UsageItemIcon(item: AppUsageStat, size: Dp) {
    if (!item.isWebsite) {
        AppIcon(packageName = item.packageName, size = size, contentDescription = item.label)
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

/** A group that covers the app, in the color of its strongest step. */
@Composable
fun GroupChip(badge: GroupBadge) {
    val tone = brandTone(badge.step.colorRes)
    Surface(shape = RoundedCornerShape(8.dp), color = tone.container) {
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelSmall,
            color = tone.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
