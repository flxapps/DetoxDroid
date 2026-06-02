package com.flx_apps.digitaldetox.ui.screens.feature

import androidx.compose.runtime.compositionLocalOf

/**
 * Composition local that provides the locked state for the current feature's settings.
 * When true, settings should be displayed in a read-only/disabled state.
 */
val LocalSettingsLocked = compositionLocalOf { false }
