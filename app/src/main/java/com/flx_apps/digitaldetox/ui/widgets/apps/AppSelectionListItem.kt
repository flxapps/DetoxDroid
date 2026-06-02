package com.flx_apps.digitaldetox.ui.widgets.apps

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppSelectionListItem(
    packageName: String,
    appName: String,
    appCategory: String,
    isSystemApp: Boolean,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rowClickTogglesSelection: Boolean = true,
    highlighted: Boolean = false,
    showAppIcon: Boolean = true,
    showTags: Boolean = true,
    supportingText: String? = null,
    headlineTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    extraLeadingContent: (@Composable RowScope.() -> Unit)? = null,
    extraTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val appIcon by produceState<Bitmap?>(null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                packageManager.getApplicationIcon(packageName).toBitmap(128, 128)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    ListItem(
        modifier = modifier.then(
            if (rowClickTogglesSelection) {
                Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
            } else {
                Modifier
            }
        ),
        colors = ListItemDefaults.colors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                Color.Transparent
            }
        ),
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appName,
                    modifier = Modifier.fillMaxWidth(if (headlineTrailingContent != null) 0.85f else 1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                headlineTrailingContent?.invoke(this)
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showTags) {
                    Row {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                            Text(
                                text = if (isSystemApp) "System" else "User",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (appCategory.isNotBlank()) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = appCategory,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                extraTrailingContent?.invoke(this)
                Checkbox(
                    enabled = enabled,
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                extraLeadingContent?.invoke(this)
                if (showAppIcon) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon!!.asImageBitmap(),
                            contentDescription = "App Icon",
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "App Icon Placeholder",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else if (extraLeadingContent == null) {
                    Icon(
                        imageVector = Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "App Icon Placeholder",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    )
}
