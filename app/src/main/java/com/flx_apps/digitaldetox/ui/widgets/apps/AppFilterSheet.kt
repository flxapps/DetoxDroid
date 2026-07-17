package com.flx_apps.digitaldetox.ui.widgets.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.ChipFlowRow

/**
 * Bottom sheet with the app-type and category filters for an app list. One implementation for the
 * app exception lists and the minimal-launcher widget configurator, which used to carry a copy
 * each.
 *
 * @param categories category name → whether it is currently selected. What an empty selection
 * means (nothing vs. everything) is up to the caller's filter logic — the sheet only displays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterSheet(
    showSystemApps: Boolean,
    showUserApps: Boolean,
    categories: Map<String, Boolean>,
    onToggleSystemApps: () -> Unit,
    onToggleUserApps: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByAppType)) },
                supportingContent = {
                    ChipFlowRow {
                        FilterChip(
                            selected = showSystemApps,
                            onClick = onToggleSystemApps,
                            label = { Text(stringResource(id = R.string.exceptionsList_filter_systemApps)) })
                        FilterChip(
                            selected = showUserApps,
                            onClick = onToggleUserApps,
                            label = { Text(stringResource(id = R.string.exceptionsList_filter_userApps)) })
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.feature_settings_exceptions_filterByCategory)) },
                supportingContent = {
                    if (categories.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.widget_minimalLauncher_filter_category_empty),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ChipFlowRow {
                            categories.forEach { (category, selected) ->
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleCategory(category) },
                                    label = { Text(text = category) })
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            // FIXME there should be a better way to do this, e.g. using
            //  Modifier.navigationBarsPadding(), but I couldn't get it to work for some reason
            Box(modifier = Modifier.height(48.dp))
        }
    }
}
