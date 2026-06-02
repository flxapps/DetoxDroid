package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.flx_apps.digitaldetox.ui.screens.feature.LocalSettingsLocked

/**
 * A simple list tile with a title, subtitle, leading icon and trailing content. It is basically
 * just a wrapper for [androidx.compose.material3.ListItem] and reduces some boilerplate, as this
 * widget is used quite often in the app.
 *
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
    allowClickWhenLocked: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isLocked = LocalSettingsLocked.current
    val effectivelyLocked = isLocked && !allowClickWhenLocked

    androidx.compose.material3.ListItem(
        headlineContent = { Text(titleText) },
        supportingContent = { Text(subtitleText) },
        trailingContent = trailing,
        modifier = Modifier
            .alpha(if (effectivelyLocked) 0.5f else 1f)
            .blockInteractionWhenLocked(effectivelyLocked)
            .combinedClickable(
                enabled = !effectivelyLocked, onClick = onClick, onLongClick = onLongClick
            ),
        leadingContent = if (leadingIcon != null) {
            { Icon(imageVector = leadingIcon, contentDescription = null) }
        } else null)
}

private fun Modifier.blockInteractionWhenLocked(isLocked: Boolean): Modifier {
    if (!isLocked) return this
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                }
            }
        }
        .semantics { disabled() }
}