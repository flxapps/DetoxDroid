import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Displays a set of options as [FilterChip]s in a [FlowRow].
 * @param options A map of text resources to option values
 * @param selectedOption The currently selected option
 * @param onOptionSelected A callback that is called when an option is selected
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
fun OptionsRow(
    options: Map<Int, Any>,
    selectedOption: Any,
    onOptionSelected: (Any) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (textRes, option) ->
            FilterChip(
                selected = option == selectedOption,
                onClick = { onOptionSelected(option) },
            ) {
                Text(text = stringResource(id = textRes))
            }
        }
    }
}