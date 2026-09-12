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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R

private val GroupMargin = 16.dp
private val GroupGap = 2.dp
private val GroupOuterCorner = 20.dp
private val GroupInnerCorner = 4.dp

/**
 * Sets related rows apart as one rounded block, each row on a segment of its own: round where the
 * block begins and ends, barely rounded in between, with a hairline gap between the rows. Every
 * direct child is a row; children that take up no space, like dialogs or a tile that has nothing
 * to show, are skipped, and a group without any rows takes no space either.
 *
 * The segments are the rows' background, so inside a group `surface` is transparent: list items
 * and anything else painted in it show the segment instead of covering it.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val segmentColor = MaterialTheme.colorScheme.surfaceContainer
    // the rows' vertical extents, from the last layout pass
    var segments by remember { mutableStateOf(emptyList<IntRange>()) }
    Layout(
        content = {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent),
                content = content
            )
        },
        modifier = modifier.drawWithContent {
            val margin = GroupMargin.toPx()
            val outer = CornerRadius(GroupOuterCorner.toPx())
            val inner = CornerRadius(GroupInnerCorner.toPx())
            val outline = Path()
            segments.forEachIndexed { index, rows ->
                val top = if (index == 0) outer else inner
                val bottom = if (index == segments.lastIndex) outer else inner
                outline.addRoundRect(
                    RoundRect(
                        left = margin,
                        top = rows.first.toFloat(),
                        right = size.width - margin,
                        bottom = rows.last.toFloat(),
                        topLeftCornerRadius = top,
                        topRightCornerRadius = top,
                        bottomRightCornerRadius = bottom,
                        bottomLeftCornerRadius = bottom
                    )
                )
            }
            drawPath(outline, segmentColor)
            // keeps the rows' ripples inside the rounded corners
            clipPath(outline) { this@drawWithContent.drawContent() }
        }
    ) { measurables, constraints ->
        val margin = GroupMargin.roundToPx()
        val rowConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth - margin * 2).coerceAtLeast(0)
            } else {
                Constraints.Infinity
            },
            minHeight = 0
        )
        val placeables = measurables.map { it.measure(rowConstraints) }
        val rows = placeables.filter { it.height > 0 }
        if (rows.isEmpty()) {
            segments = emptyList()
            return@Layout layout(0, 0) { placeables.forEach { it.place(0, 0) } }
        }
        val gap = GroupGap.roundToPx()
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            rows.maxOf { it.width } + margin * 2
        }
        layout(width, margin + rows.sumOf { it.height } + gap * (rows.size - 1)) {
            val bounds = mutableListOf<IntRange>()
            var y = margin / 2
            placeables.forEach { placeable ->
                placeable.place(margin, y)
                if (placeable.height > 0) {
                    bounds += y..y + placeable.height
                    y += placeable.height + gap
                }
            }
            segments = bounds
        }
    }
}

/**
 * A small colored heading over the [SettingsGroup] below it, lined up with the icons of its rows.
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp)
    )
}

/**
 * Folds settings that rarely need touching away under one row, so a settings screen leads with
 * the ones that matter. The row names what it holds in [summary], and forms a [SettingsGroup] of
 * its own with them. It starts out open when [initiallyExpanded], which call sites use for a
 * setting that is no longer at its default: a setting someone changed should never be out of
 * sight.
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
    SettingsGroup {
        ListItem(
            headlineContent = {
                Text(text = stringResource(id = R.string.feature_settings_advanced))
            },
            supportingContent = { Text(text = summary) },
            leadingContent = {
                Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = null)
            },
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
}
