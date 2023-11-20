package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chargemap.compose.numberpicker.NumberPicker
import com.flx_apps.digitaldetox.R
import kotlinx.coroutines.flow.MutableStateFlow

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
    val numberPickerValue = MutableStateFlow(initialValue)
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(text = titleText)
    }, confirmButton = {
        Text(text = stringResource(id = R.string.action_save), modifier = Modifier.clickable {
            onValueSelected(numberPickerValue.value)
            onDismissRequest()
        })
    }, dismissButton = {
        Text(
            text = stringResource(id = R.string.action_cancel),
            modifier = Modifier.clickable { onDismissRequest() })
    }, text = {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            NumberPicker(value = numberPickerValue.collectAsState().value, onValueChange = {
                numberPickerValue.value = it
            }, range = range, label = label)
        }
    })
}