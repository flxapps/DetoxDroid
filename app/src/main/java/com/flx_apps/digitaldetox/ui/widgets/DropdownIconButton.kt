package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.material.DropdownMenu
import androidx.compose.material.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A dropdown menu that is opened by clicking on an icon button.
 * @param icon The icon button that opens the dropdown menu.
 * @param items The items of the dropdown menu.
 * @param onItemSelected A callback that is called when the user selects an item.
 */
@Composable
fun DropdownIconButton(
    icon: @Composable () -> Unit, items: List<String>, onItemSelected: (Any) -> Unit
) {
    val expanded = remember {
        MutableStateFlow(false)
    }
    DropdownMenu(
        expanded = expanded.collectAsState().value,
        onDismissRequest = { expanded.value = false }) {
        items.forEach { item ->
            DropdownMenuItem(text = { Text(text = item) }, onClick = {
                onItemSelected(item)
                expanded.value = false
            })
        }
    }

    IconButton(onClick = { expanded.value = true }) {
        icon()
    }
}