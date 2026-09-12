package com.flx_apps.digitaldetox.ui.widgets

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Something to take out of the app character by character, like a passphrase or an address: set
 * apart in monospace on a tonal box, selectable, with a copy button beside it.
 *
 * @param copyLabel What the copy button does, e.g. "Copy address"; also the clip's label.
 * @param copiedMessage Confirms the copy below Android 13; from then on the system does that.
 * @param sensitive Keeps the copied text out of the system's clipboard preview and suggestions.
 */
@Composable
fun CopyableText(
    text: String,
    copyLabel: String,
    copiedMessage: String,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp)
        ) {
            // the weight sits on a box of its own: the selection container puts its layout inside
            // a context menu box, so a weight handed to it never reaches the row, and a long value
            // like an address pushes the copy button out
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            IconButton(onClick = {
                val clip = ClipData.newPlainText(copyLabel, text)
                if (sensitive) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = copyLabel)
            }
        }
    }
}
