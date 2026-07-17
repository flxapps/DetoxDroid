package com.flx_apps.digitaldetox.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one way chips are laid out in this app.
 *
 * Material3 inflates every chip's layout box to the 48dp minimum touch target while the chip
 * itself is 32dp tall — in wrapping rows that phantom padding shows up as huge, uneven vertical
 * gaps (and some call sites fought it with `height(32.dp)` hacks). This row disables the
 * inflation for its children and instead applies deliberate, even spacing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowRow(
    modifier: Modifier = Modifier,
    // deliberately not a FlowRowScope receiver: that type is experimental and would force the
    // opt-in onto every call site
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
