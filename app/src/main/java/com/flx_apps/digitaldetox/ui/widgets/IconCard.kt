package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Generic card with a leading icon and arbitrary content.
 * @param icon Leading icon shown in the card.
 * @param contentDescription Content description for accessibility.
 * @param modifier Modifier for the outer card.
 * @param content Composable content shown to the right of the icon.
 */
@Composable
fun IconCard(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Icon(
                tint = MaterialTheme.colorScheme.primary,
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                content()
            }
        }
    }
}

/**
 * Convenience wrapper around [IconCard] for plain informational text blocks.
 */
@Composable
fun InfoCard(infoText: String) {
    IconCard(
        icon = Icons.Default.Info,
        contentDescription = "Info"
    ) {
        Text(
            infoText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
