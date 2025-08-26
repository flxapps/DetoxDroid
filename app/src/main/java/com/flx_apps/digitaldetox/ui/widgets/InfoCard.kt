package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A card that displays a text with an info icon. It is basically just a wrapper for [Card] and
 * reduces some boilerplate, as this widget is used quite often in the app.
 */
@Composable
fun InfoCard(infoText: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Icon(
                tint = MaterialTheme.colorScheme.primary,
                imageVector = Icons.Default.Info,
                contentDescription = "Feature Description",
                modifier = Modifier.size(24.dp)
            )
            Text(
                infoText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}