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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chargemap.compose.numberpicker.NumberPicker
import com.flx_apps.digitaldetox.R

/**
 * A dialog that picks a duration as hours and minutes, for the spans a single list of minutes
 * would make a long scroll of: two pickers side by side, each labelled with its unit.
 * @param initialMinutes The duration to start on. It is rounded to [minutesStep] first.
 * @param maxHours The largest number of hours on offer.
 * @param minutesStep The step of the minutes picker.
 * @param minMinutes The shortest duration that can be saved; the Save button waits for it.
 * @param onValueSelected Called with the picked duration in minutes.
 * @see NumberPickerDialog
 */
@Composable
fun DurationPickerDialog(
    titleText: String,
    initialMinutes: Int,
    maxHours: Int = 23,
    minutesStep: Int = 5,
    minMinutes: Int = minutesStep,
    onValueSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val rounded = (initialMinutes + minutesStep / 2) / minutesStep * minutesStep
    // remembered so a recomposition of the caller (or a config change) cannot reset what the user
    // has already scrolled to
    var hours by rememberSaveable { mutableIntStateOf((rounded / 60).coerceIn(0, maxHours)) }
    var minutes by rememberSaveable { mutableIntStateOf(rounded % 60) }
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(text = titleText)
    }, confirmButton = {
        TextButton(
            enabled = hours * 60 + minutes >= minMinutes,
            onClick = {
                onValueSelected(hours * 60 + minutes)
                onDismissRequest()
            }) {
            Text(text = stringResource(id = R.string.action_save))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(id = R.string.action_cancel))
        }
    }, text = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            NumberPicker(
                value = hours,
                onValueChange = { hours = it },
                range = 0..maxHours,
                label = { context.getString(R.string.duration_hours_short, it) },
                dividersColor = MaterialTheme.colorScheme.onSurface,
                textStyle = textStyle
            )
            NumberPicker(
                value = minutes,
                onValueChange = { minutes = it },
                range = 0 until 60 step minutesStep,
                label = { context.getString(R.string.duration_minutes, it) },
                dividersColor = MaterialTheme.colorScheme.onSurface,
                textStyle = textStyle
            )
        }
    })
}
