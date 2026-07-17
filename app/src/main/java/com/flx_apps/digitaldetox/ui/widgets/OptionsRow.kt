package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Displays a set of mutually exclusive options as [FilterChip]s in a [ChipFlowRow]. The selected
 * chip is rendered in full primary color with a check mark — the default secondaryContainer is too
 * close to card backgrounds to read at a glance.
 * @param options A map of text resources to option values
 * @param selectedOption The currently selected option
 * @param onOptionSelected A callback that is called when an option is selected
 */
@Composable
fun OptionsRow(
    options: Map<Int, Any>,
    selectedOption: Any,
    onOptionSelected: (Any) -> Unit,
) {
    ChipFlowRow {
        options.forEach { (textRes, option) ->
            val selected = option == selectedOption
            FilterChip(
                selected = selected,
                onClick = { onOptionSelected(option) },
                label = { Text(text = stringResource(id = textRes)) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
