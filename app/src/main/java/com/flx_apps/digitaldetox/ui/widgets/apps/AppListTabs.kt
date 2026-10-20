package com.flx_apps.digitaldetox.ui.widgets.apps

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R

/** The two halves of everything that lists apps and websites side by side. */
enum class AppListTab(@StringRes val label: Int, val icon: ImageVector) {
    APPS(R.string.appList_tab_apps, Icons.Default.Apps),
    WEBSITES(R.string.appList_tab_websites, Icons.Default.Language),
}

/**
 * Apps or websites, as two segments, the same switch wherever the app shows both. A segment can
 * carry a count of what is on its half ([counts]), so whoever looks at one half sees that the
 * other has something too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListTabSelector(
    selected: AppListTab,
    onSelected: (AppListTab) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<AppListTab, Int> = emptyMap(),
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        AppListTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, AppListTab.entries.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = tab == selected) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(id = tab.label), maxLines = 1)
                    val count = counts[tab] ?: 0
                    if (count > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 6.dp)
                        ) { Text(text = count.toString()) }
                    }
                }
            }
        }
    }
}
