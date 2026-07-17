package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chargemap.compose.numberpicker.NumberPicker
import com.flx_apps.digitaldetox.R

/**
 * A dialog that displays a number picker. It is basically just a wrapper for [AlertDialog] and
 * reduces some boilerplate. The number picker is provided by the [NumberPicker] library.
 * @param titleText The title of the dialog.
 * @param initialValue The initial value of the number picker.
 * @param range The range of the number picker.
 * @param label A function that converts the number picker value to a string.
 * @param onValueSelected A callback that is called when the user selects a value.
 * @param onDismissRequest A callback that is called when the user dismisses the dialog.
 * @see NumberPicker
 * @see AlertDialog
 */
@Composable
fun NumberPickerDialog(
    titleText: String = "Number Picker",
    initialValue: Int,
    range: Iterable<Int> = 0..100,
    label: (Int) -> String = { it.toString() },
    onValueSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    // remembered so a recomposition of the caller (or a config change) cannot reset the value
    // the user has already scrolled to
    var pickerValue by rememberSaveable { mutableIntStateOf(initialValue) }
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(text = titleText)
    }, confirmButton = {
        TextButton(onClick = {
            onValueSelected(pickerValue)
            onDismissRequest()
        }) {
            Text(text = stringResource(id = R.string.action_save))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(id = R.string.action_cancel))
        }
    }, text = {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            NumberPicker(value = pickerValue, onValueChange = {
                pickerValue = it
            }, range = range, label = label, dividersColor = MaterialTheme.colorScheme.onSurface,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
        }
    })
}
