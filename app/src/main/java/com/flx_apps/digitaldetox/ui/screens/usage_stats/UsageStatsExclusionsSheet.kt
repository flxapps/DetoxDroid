package com.flx_apps.digitaldetox.ui.screens.usage_stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow
import com.flx_apps.digitaldetox.util.toHrMinString
import com.flx_apps.digitaldetox.ui.widgets.AppIcon
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lets the user take apps and whole categories out of the usage numbers.
 *
 * Screen time on its own says how long a window was up, not what the user was doing, and the two
 * part company for a whole class of app: a sleep tracker left running overnight measures the same
 * as a feed scrolled all night. What is left out stays listed here so it can be brought back, and
 * apps left out through their category are brought back through the category.
 */
@Composable
fun UsageStatsExclusionsSheet(
    state: UsageStatsUiState,
    onAppExcluded: (packageName: String, excluded: Boolean) -> Unit,
    onCategoryExcluded: (categoryId: Int, excluded: Boolean) -> Unit
) {
    val excludedPackages = state.excludedApps.mapTo(mutableSetOf()) { it.packageName }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.usageStats_exclusions_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.usageStats_exclusions_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (state.categories.isNotEmpty()) {
            item {
                SheetSectionLabel(stringResource(R.string.usageStats_exclusions_categories))
                ChipFlowRow(modifier = Modifier.padding(bottom = 4.dp)) {
                    for (category in state.categories) {
                        FilterChip(
                            selected = category.isExcluded,
                            onClick = {
                                onCategoryExcluded(category.categoryId, !category.isExcluded)
                            },
                            label = { Text(category.label) },
                            leadingIcon = if (category.isExcluded) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }

        if (state.excludedApps.isNotEmpty()) {
            item { SheetSectionLabel(stringResource(R.string.usageStats_exclusions_leftOut)) }
            items(state.excludedApps) { app ->
                ExclusionRow(
                    app = app,
                    isExcluded = true,
                    onToggle = { onAppExcluded(app.packageName, false) }
                )
            }
        }

        val selectable = state.topApps.filterNot { it.packageName in excludedPackages }
        if (selectable.isNotEmpty()) {
            item { SheetSectionLabel(stringResource(R.string.usageStats_exclusions_counted)) }
            items(selectable) { app ->
                ExclusionRow(
                    app = app,
                    isExcluded = false,
                    onToggle = { onAppExcluded(app.packageName, true) }
                )
            }
        }

        if (state.categories.isEmpty() && state.excludedApps.isEmpty() && selectable.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.usageStats_noData),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ExclusionRow(
    app: AppUsageStat,
    isExcluded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, size = 32.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            if (app.totalTimeMs > 0) {
                Text(
                    app.totalTimeMs.milliseconds.toHrMinString(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(checked = isExcluded, onCheckedChange = { onToggle() })
    }
}
