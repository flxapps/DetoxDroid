package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked

/**
 * A simple list tile with a title, subtitle, leading icon and trailing content. It is basically
 * just a wrapper for [androidx.compose.material3.ListItem] and reduces some boilerplate, as this
 * widget is used quite often in the app.
 *
 * @param trailing What to show at the tile's end, like the current value. It is for display only:
 *   a control put here would not know when the settings are locked, which is what [checked] is for.
 * @param checked Gives the tile a checkbox in that state, which [onCheckedChange] changes. It is
 *   disabled along with the tile, so while the settings are locked no tap, key press or
 *   accessibility service can change it, and screen readers still read it.
 * @param allowClickWhenLocked When true, the tile is still clickable even when settings are locked
 *   via [LocalSettingsLocked]. Use this for actions that are explicitly allowed while locked (e.g.
 *   the unlock action itself).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleListTile(
    titleText: String,
    subtitleText: String,
    leadingIcon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    allowClickWhenLocked: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isLocked = LocalSettingsLocked.current
    val effectivelyLocked = isLocked && !allowClickWhenLocked
    val effectivelyEnabled = enabled && !effectivelyLocked

    androidx.compose.material3.ListItem(
        headlineContent = { Text(titleText) },
        supportingContent = { Text(subtitleText) },
        trailingContent = if (checked == null) trailing else {
            {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = effectivelyEnabled,
                    // the whole tile fades while it can't be used, so the box keeps its colors
                    // instead of fading twice
                    colors = CheckboxDefaults.colors(
                        disabledCheckedColor = MaterialTheme.colorScheme.primary,
                        disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        modifier = Modifier
            .alpha(if (effectivelyEnabled) 1f else 0.5f)
            .combinedClickable(
                enabled = effectivelyEnabled, onClick = onClick, onLongClick = onLongClick
            ),
        leadingContent = if (leadingIcon != null) {
            { Icon(imageVector = leadingIcon, contentDescription = null) }
        } else null)
}
