package com.flx_apps.digitaldetox.util

import kotlin.time.Duration

/**
 * Converts a duration to a string in the format "h min".
 */
fun Duration.toHrMinString(): String {
    val hours = this.inWholeHours
    val minutes = this.inWholeMinutes % 60
    return if (hours > 0) {
        "$hours h $minutes min"
    } else {
        "$minutes min"
    }
}