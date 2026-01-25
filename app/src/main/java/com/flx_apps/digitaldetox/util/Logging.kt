package com.flx_apps.digitaldetox.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long, val priority: Int, val tag: String?, val message: String
) {
    val formattedDate: String
        get() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    override fun toString(): String {
        return "$timestamp|$priority|${tag ?: ""}|$message"
    }

    companion object {
        fun fromString(line: String): LogEntry? {
            val parts = line.split("|", limit = 4)
            if (parts.size == 4) {
                return LogEntry(
                    timestamp = parts[0].toLongOrNull() ?: 0,
                    priority = parts[1].toIntOrNull() ?: 0,
                    tag = parts[2].ifEmpty { null },
                    message = parts[3]
                )
            }
            return null
        }
    }
}

object InMemoryLogStore {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logFile: File? = null
    private var isInitialized = false

    fun init(file: File) {
        logFile = file
        isInitialized = true
        if (file.exists()) {
            scope.launch {
                val loadedLogs = try {
                    file.readLines().mapNotNull { LogEntry.fromString(it) }
                } catch (e: Exception) {
                    emptyList()
                }
                _logs.value = loadedLogs
            }
        }
    }

    fun addLog(priority: Int, tag: String?, message: String) {
        // Only add logs if initialized (debug builds)
        if (!isInitialized) return

        val entry = LogEntry(System.currentTimeMillis(), priority, tag, message)
        _logs.update {
            (it + entry).takeLast(1000)
        }
        scope.launch {
            try {
                logFile?.appendText("$entry\n")
                // Keep file size in check (optional, but good practice)
                if ((logFile?.length() ?: 0) > 1024 * 1024) { // 1MB
                    val lines = logFile?.readLines()?.takeLast(1000) ?: emptyList()
                    logFile?.writeText(lines.joinToString("\n") + "\n")
                }
            } catch (e: Exception) {
                // Ignore file errors during logging
            }
        }
    }

    fun clear() {
        if (!isInitialized) return

        _logs.value = emptyList()
        scope.launch {
            logFile?.delete()
            logFile?.createNewFile()
        }
    }
}

class CachingDebugTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        InMemoryLogStore.addLog(priority, tag, message)
    }
}
