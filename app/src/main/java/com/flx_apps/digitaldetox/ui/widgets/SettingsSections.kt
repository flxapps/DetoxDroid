package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R

/** A small colored heading that groups the settings below it. */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * Folds settings that rarely need touching away under one row, so a settings screen leads with
 * the ones that matter. The row names what it holds in [summary]. It starts out open when
 * [initiallyExpanded], which call sites use for a setting that is no longer at its default: a
 * setting someone changed should never be out of sight.
 *
 * Opening it only reveals settings, so it stays usable while the settings are locked; the
 * settings inside lock themselves.
 */
@Composable
fun AdvancedSettings(
    summary: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "advancedSettingsChevron"
    )
    ListItem(
        headlineContent = { Text(text = stringResource(id = R.string.feature_settings_advanced)) },
        supportingContent = { Text(text = summary) },
        leadingContent = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = null) },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation)
            )
        },
        modifier = Modifier.clickable { expanded = !expanded }
    )
    AnimatedVisibility(visible = expanded) {
        Column { content() }
    }
}
