package com.flx_apps.digitaldetox.util

import android.content.Context
import android.os.Process
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.data.DataStoreProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * One entry of the [DebugLog]. [pid] tells which run of the app wrote it; [id] only tells entries
 * apart while the app runs and is not stored.
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val priority: Int,
    val pid: Int,
    val tag: String?,
    val message: String,
)

/**
 * How a [LogEntry] is stored: one line per entry, its fields separated by `|`. Messages keep their
 * line breaks (a stack trace is part of the message) by escaping them, so an entry never spans two
 * lines of the file.
 */
internal object LogLineFormat {
    fun encode(entry: LogEntry): String = listOf(
        entry.timestamp,
        entry.priority,
        entry.pid,
        entry.tag.orEmpty().replace('|', '/'),
        escape(entry.message)
    ).joinToString("|")

    /** Reads a line written by [encode], or null for anything else. */
    fun decode(line: String, id: Long): LogEntry? {
        val parts = line.split('|', limit = 5)
        if (parts.size != 5) return null
        return LogEntry(
            id = id,
            timestamp = parts[0].toLongOrNull() ?: return null,
            priority = parts[1].toIntOrNull() ?: return null,
            pid = parts[2].toIntOrNull() ?: return null,
            tag = parts[3].ifEmpty { null },
            message = unescape(parts[4])
        )
    }

    private fun escape(text: String) = buildString(text.length) {
        text.forEach {
            when (it) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(it)
            }
        }
    }

    private fun unescape(text: String) = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char == '\\' && i + 1 < text.length) {
                when (val escaped = text[i + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(escaped)
                }
                i += 2
            } else {
                append(char)
                i++
            }
        }
    }
}

/**
 * A record of what DetoxDroid logs, kept on the device so it can go along with a bug report. Debug
 * builds keep it from the start, release builds once the user turns it on (see [isEnabled]).
 *
 * One background thread writes the entries to a file in the order they were logged, outside the
 * app's backups. The file holds what memory holds, so an exception that ends the process is still
 * there, stack trace and all, when the app comes back.
 *
 * Only the main process keeps the log, since two processes appending to one file would garble it.
 */
object DebugLog {
    private const val CAPACITY = 5_000
    private const val FILE_NAME = "debug_log.txt"
    private const val LEGACY_FILE_NAME = "app_logs.txt"

    private var storedEnabled: Boolean by DataStoreProperty(
        booleanPreferencesKey("debugLog_enabled"), BuildConfig.DEBUG
    )

    @Volatile
    private var enabled = false

    @Volatile
    private var file: File? = null

    /** Lines in [file], which is rewritten from memory once it has grown well past [CAPACITY]. */
    private var linesInFile = 0

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** The recorded entries, oldest first. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val nextId = AtomicLong()
    private val pending = ConcurrentLinkedQueue<LogEntry>()
    private val drainScheduled = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DebugLog").apply { isDaemon = true }
    }

    /** Whether new entries are recorded. Turning it off deletes the log. */
    var isEnabled: Boolean
        get() = enabled
        set(value) {
            if (value == enabled) return
            enabled = value
            storedEnabled = value
            if (!value) clear()
        }

    /** Starts the log in the main process, with what earlier runs left in it. */
    fun init(context: Context) {
        val logFile = File(context.noBackupFilesDir, FILE_NAME)
        file = logFile
        enabled = storedEnabled
        writer.execute {
            File(context.filesDir, LEGACY_FILE_NAME).delete()
            if (!enabled) {
                logFile.delete()
                return@execute
            }
            val lines = runCatching { logFile.readLines() }.getOrDefault(emptyList())
            linesInFile = lines.size
            val loaded = lines.mapNotNull { LogLineFormat.decode(it, nextId.incrementAndGet()) }
            _entries.update { (loaded + it).takeLast(CAPACITY) }
        }
    }

    fun record(priority: Int, tag: String?, message: String) {
        if (!enabled) return
        pending += LogEntry(
            id = nextId.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            priority = priority,
            pid = Process.myPid(),
            tag = tag,
            message = message
        )
        if (drainScheduled.compareAndSet(false, true)) writer.execute(::drain)
    }

    fun clear() {
        writer.execute {
            pending.clear()
            _entries.value = emptyList()
            file?.delete()
            linesInFile = 0
        }
    }

    /** Waits a moment for everything logged so far to reach the file, for a process about to end. */
    fun flush() {
        runCatching { writer.submit(::drain).get(2, TimeUnit.SECONDS) }
    }

    private fun drain() {
        drainScheduled.set(false)
        val batch = generateSequence { pending.poll() }.toList()
        // an entry can slip in while the log is being turned off
        if (batch.isEmpty() || !enabled) return
        _entries.update { (it + batch).takeLast(CAPACITY) }
        val logFile = file ?: return
        runCatching {
            logFile.appendText(batch.joinToString(separator = "") { LogLineFormat.encode(it) + "\n" })
            linesInFile += batch.size
            if (linesInFile > CAPACITY * 3 / 2) rewrite(logFile)
        }.onFailure { android.util.Log.w("DebugLog", "Could not write the debug log", it) }
    }

    private fun rewrite(logFile: File) {
        val kept = _entries.value
        val temp = File(logFile.path + ".tmp")
        temp.bufferedWriter().use { out ->
            kept.forEach { out.append(LogLineFormat.encode(it)).append('\n') }
        }
        temp.renameTo(logFile)
        linesInFile = kept.size
    }
}

/**
 * Hands every log call to the [DebugLog], and those from [logcatPriority] up to logcat as well.
 */
class DebugLogTree(private val logcatPriority: Int) : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= logcatPriority || DebugLog.isEnabled

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= logcatPriority) super.log(priority, tag, message, t)
        DebugLog.record(priority, tag, message)
    }
}
