package com.flx_apps.digitaldetox.ui.theme

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40
)

/** Cards come out a little rounder than Material's defaults, closer to the grouped lists. */
private val DetoxDroidShapes = Shapes(medium = RoundedCornerShape(16.dp))

/**
 * Whether the app is in its dark theme: the user's choice, or the system's when they have not made
 * one.
 */
@Composable
fun isAppInDarkTheme(): Boolean {
    val mode by AppearanceSettings.themeMode.collectAsState()
    return when (mode) {
        AppearanceSettings.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceSettings.ThemeMode.LIGHT -> false
        AppearanceSettings.ThemeMode.DARK -> true
    }
}

/**
 * The app's theme. Activities draw edge to edge (see `enableEdgeToEdge`), and the screens' app bars
 * reach under the status bar; the system bar icons follow [darkTheme] rather than the system's
 * night mode, since the two differ once the user picks a theme ([AppearanceSettings]).
 * @param syncSystemBars whether to set the system bar styles of the hosting activity. Screens that
 * style their bars themselves pass false.
 */
@Composable
fun DetoxDroidTheme(
    darkTheme: Boolean = isAppInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    syncSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    if (syncSystemBars) SystemBarsFollowTheme(darkTheme)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DetoxDroidShapes,
        content = content
    )
}

/**
 * Re-applies the edge-to-edge setup of the hosting activity, if any, with the theme's own notion
 * of dark, so status bar icons stay readable on a dark app in a light system and the other way
 * round. The scrims are the defaults of `enableEdgeToEdge`, which keeps them private.
 */
@Composable
private fun SystemBarsFollowTheme(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme }
        )
    }
}

private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
