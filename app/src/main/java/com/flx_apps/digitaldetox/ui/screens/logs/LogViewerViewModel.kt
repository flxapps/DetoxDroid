package com.flx_apps.digitaldetox.ui.screens.logs

import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flx_apps.digitaldetox.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Filters the [DebugLog] for the log viewer. The filtering runs off the main thread, since a busy
 * log changes many times a second and holds thousands of entries.
 */
class LogViewerViewModel : ViewModel() {
    private val _filter = MutableStateFlow(LogFilter())
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    /** Null until the first pass over the log is done. */
    val state: StateFlow<LogViewerState?> = combine(DebugLog.entries, _filter) { entries, filter ->
        buildLogViewerState(entries, filter, Process.myPid())
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLevel(level: LogLevelFilter) = _filter.update { it.copy(level = level) }

    fun setTag(tag: String?) = _filter.update { it.copy(tag = tag) }

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }

    fun resetFilter() = _filter.update { LogFilter() }

    fun clearLog() = DebugLog.clear()
}
