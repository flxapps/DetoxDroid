package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.screens.app_exceptions.joinedNames

/**
 * The website half of the most-used list. While the time on websites is not counted it says what
 * counting would mean and offers it; once it is, the sites come in the rows the apps use, and
 * open the same details.
 *
 * @param websites The sites to show, in the order the list is sorted in.
 * @param onCountWebsites Starts (true) or stops (false) the counting.
 */
@Composable
fun WebsiteUsageCard(
    state: UsageStatsUiState,
    websites: List<AppUsageStat>,
    onOpen: (AppUsageStat) -> Unit,
    onCountWebsites: (Boolean) -> Unit,
) {
    if (!state.countsWebsites) {
        state.readableBrowsers?.let {
            CountWebsitesInvitation(browsers = it, onCount = { onCountWebsites(true) })
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    var confirmTurnOff by remember { mutableStateOf(false) }
    StatsCard {
        if (websites.isEmpty()) {
            Text(
                text = stringResource(R.string.usageStats_websites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            val shown = if (expanded) websites else websites.take(TOP_ITEMS_PREVIEW_COUNT)
            shown.forEachIndexed { index, site ->
                AppUsageRow(app = site, onClick = { onOpen(site) })
                if (index < shown.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            if (websites.size > TOP_ITEMS_PREVIEW_COUNT) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (expanded) R.string.usageStats_showLess
                            else R.string.usageStats_showMore
                        )
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp))
        // the sites' time is already in the browsers' rows on the app half, so the total above
        // does not grow with it
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.usageStats_websites_partOf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { confirmTurnOff = true }) {
                Text(stringResource(R.string.usageStats_websites_turnOff))
            }
        }
    }
    if (confirmTurnOff) {
        AlertDialog(
            onDismissRequest = { confirmTurnOff = false },
            title = { Text(stringResource(R.string.usageStats_websites_turnOff_title)) },
            text = { Text(stringResource(R.string.usageStats_websites_turnOff_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTurnOff = false
                    onCountWebsites(false)
                }) { Text(stringResource(R.string.usageStats_websites_turnOff)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTurnOff = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * What counting the time on websites means, in which of the phone's [browsers], and the button
 * that starts it. Without a browser DetoxDroid can read there is nothing to start.
 */
@Composable
private fun CountWebsitesInvitation(browsers: List<String>, onCount: () -> Unit) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.usageStats_websites_title),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = if (browsers.isEmpty()) {
                stringResource(R.string.websites_browsers_none)
            } else {
                stringResource(R.string.usageStats_websites_optIn, joinedNames(browsers))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (browsers.isNotEmpty()) {
            FilledTonalButton(onClick = onCount, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.usageStats_websites_optIn_action))
            }
        }
    }
}
