package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel des Log-Screens (Kategorie „Logs & Diagnose“).
 *
 * Liest den app-weiten [LogBuffer] (derselbe, in den der Timber-Tree in
 * `VividApplication` schreibt) und erlaubt das Leeren des Puffers.
 * Kopieren/Teilen passiert im Screen (braucht Context/Clipboard).
 */
@HiltViewModel
class SettingsLogsViewModel @Inject constructor(
    private val logBuffer: LogBuffer,
) : ViewModel() {

    val logs: StateFlow<List<LogEntry>> = logBuffer.entries

    fun clearLogs() {
        logBuffer.clear()
    }
}