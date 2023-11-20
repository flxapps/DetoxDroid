package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A simple list tile with a title, subtitle, leading icon and trailing content. It is basically
 * just a wrapper for [androidx.compose.material3.ListItem] and reduces some boilerplate, as this
 * widget is used quite often in the app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleListTile(
    titleText: String,
    subtitleText: String,
    leadingIcon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    androidx.compose.material3.ListItem(headlineContent = {
        Text(titleText)
    }, supportingContent = {
        Text(subtitleText)
    }, trailingContent = trailing, modifier = Modifier.combinedClickable(
        onClick = onClick, onLongClick = onLongClick
    ), leadingContent = if (leadingIcon != null) {
        { Icon(imageVector = leadingIcon, contentDescription = null) }
    } else null)
}