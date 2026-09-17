package com.flx_apps.digitaldetox.ui.screens.logs

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.util.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lineTimeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
private val headerTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
private val fileTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Logcat's letters for the priorities, as the log viewer and a shared log show them. */
fun priorityLetter(priority: Int): Char = when (priority) {
    2 -> 'V'
    3 -> 'D'
    4 -> 'I'
    5 -> 'W'
    6 -> 'E'
    7 -> 'A'
    else -> '?'
}

/** An entry the way logcat prints one, continuation lines indented under the first. */
fun LogEntry.toLogcatLine(): String {
    val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(lineTimeFormat)
    return "$time ${pid.toString().padStart(5)} ${priorityLetter(priority)} ${tag ?: "-"}: " +
            message.replace("\n", "\n    ")
}

/**
 * Writes the entries the viewer shows to a text file, headed by what a bug report needs to know
 * about the app and the phone, and opens the share sheet for it.
 */
suspend fun shareDebugLog(context: Context, state: LogViewerState, filter: LogFilter) {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "debug_log").apply {
            deleteRecursively()
            mkdirs()
        }
        File(directory, "detoxdroid-log-${now.format(fileTimeFormat)}.txt").apply {
            bufferedWriter().use { out ->
                out.appendLine(
                    "DetoxDroid ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, " +
                            "${BuildConfig.VERSION_CODE})"
                )
                out.appendLine(
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                            "${Build.MANUFACTURER} ${Build.MODEL}"
                )
                out.appendLine("Saved ${now.format(headerTimeFormat)}")
                if (filter.isActive) out.appendLine("Filter: ${filter.describe()}")
                out.appendLine("${state.shown} of ${state.total} entries")
                out.appendLine()
                state.shownEntries.forEach { out.appendLine(it.toLogcatLine()) }
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.debuglog", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun LogFilter.describe(): String = listOfNotNull(
    level.takeIf { it != LogLevelFilter.ALL }?.let { "${priorityLetter(it.minPriority)} and up" },
    tag?.let { "tag $it" },
    query.trim().takeIf { it.isNotEmpty() }?.let { "text \"$it\"" }
).joinToString(", ")
