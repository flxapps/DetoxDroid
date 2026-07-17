package com.flx_apps.digitaldetox.util

import android.content.Context
import com.flx_apps.digitaldetox.R
import kotlin.time.Duration

/** "2 h 5 min" / "5 min" — the standard duration format for stats values. */
fun Duration.toHrMinString(context: Context): String {
    val hours = this.inWholeHours
    val minutes = this.inWholeMinutes % 60
    return if (hours > 0) {
        context.getString(R.string.duration_hours_minutes, hours, minutes)
    } else {
        context.getString(R.string.duration_minutes, minutes)
    }
}

/** Like [toHrMinString] but with second-level resolution for very short durations. */
fun Duration.toShortDurationString(context: Context): String {
    val totalSeconds = inWholeSeconds
    return when {
        totalSeconds >= 3600 -> {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            context.getString(R.string.duration_hours_minutes, hours, minutes)
        }
        totalSeconds >= 60 -> context.getString(R.string.duration_minutes, totalSeconds / 60)
        totalSeconds > 0 -> "${totalSeconds}s"
        else -> "<1s"
    }
}

/** "2h" / "45m" — single-unit format for cramped spots like chart axis labels. */
fun formatDurationMsShort(context: Context, ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    return if (hours > 0) {
        context.getString(R.string.duration_hours_short, hours)
    } else {
        context.getString(R.string.duration_minutes_short, totalMinutes)
    }
}

/** "2h05m" / "2h" / "45 min" — compact but exact format for chart tooltips. */
fun formatDurationMsCompact(context: Context, ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val remainingMinutes = totalMinutes % 60
    return if (hours > 0 && remainingMinutes > 0) {
        context.getString(R.string.duration_hours_compact, hours, remainingMinutes)
    } else if (hours > 0) {
        context.getString(R.string.duration_hours_short, hours)
    } else {
        context.getString(R.string.duration_minutes, totalMinutes)
    }
}
