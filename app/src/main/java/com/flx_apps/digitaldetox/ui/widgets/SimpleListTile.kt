package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
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
        trailingContent = if (effectivelyEnabled) trailing else {
            { Box(modifier = Modifier.inert()) { trailing() } }
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

/**
 * Keeps the trailing control of a locked or disabled tile from reacting, since the control itself
 * has no idea: no press, no key and no accessibility service gets through, and screen readers lose
 * its state along with its action. Of the touches only the presses are taken, as a switch or
 * checkbox reacts to nothing else, while the moves have to stay untouched for the list around the
 * tile to scroll when a drag starts on it.
 */
private fun Modifier.inert(): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { if (it.changedToDownIgnoreConsumed()) it.consume() }
            }
        }
    }
    .focusProperties { canFocus = false }
    .clearAndSetSemantics {}