package com.flx_apps.digitaldetox.ui.theme

import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource

/**
 * One of DetoxDroid's six colors as a tint: a faint container and the color itself on it. Each
 * color stands for one thing wherever the app shows it (purple for gray, pink for a lock, orange
 * for doomscrolling, yellow for a wait or a pause, blue for the brake, green for running), so a
 * tint is a meaning, not decoration.
 *
 * In the dark theme the color is lifted a little, since a mid-tone on a dark card is hard to read.
 * In the light theme it is pulled towards black instead, the brighter the color the further, so
 * that yellow text reads on its own faint container as well as purple does.
 */
class BrandTone(val container: Color, val content: Color)

@Composable
fun brandTone(@ColorRes colorRes: Int): BrandTone {
    val color = colorResource(id = colorRes)
    val dark = isAppInDarkTheme()
    return BrandTone(
        container = color.copy(alpha = if (dark) 0.28f else 0.16f),
        content = if (dark) lerp(color, Color.White, 0.35f) else lerp(color, Color.Black, darkeningOf(color)),
    )
}

private fun darkeningOf(color: Color): Float {
    val luminance = color.luminance()
    return when {
        luminance > 0.5f -> 0.45f
        luminance > 0.25f -> 0.3f
        else -> 0.15f
    }
}
