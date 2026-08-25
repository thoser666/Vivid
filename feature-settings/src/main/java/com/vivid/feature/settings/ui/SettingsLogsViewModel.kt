package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI-Zustand des Log-Screens (Kategorie „Logs & Diagnose“).
 */
data class LogsUiState(
    /** Alle Einträge innerhalb der Vorhaltezeit, neueste zuerst (ggf. gefiltert). */
    val entries: List<LogEntry> = emptyList(),
    /** Vorhaltezeit in Tagen (1–30). */
    val retentionDays: Int = LogsDefaults.DEFAULT_RETENTION_DAYS,
    /** Anzahl der markierten Abstürze in der aktuellen Vorhaltezeit. */
    val crashCount: Int = 0,
    /** true = nur Fehler/Crashes anzeigen. */
    val errorsOnly: Boolean = false,
)

/**
 * ViewModel des Log-Screens: kombiniert den Live-Puffer ([LogBuffer]) mit der
 * persistierten Tages-Historie ([LogStore]) innerhalb der konfigurierbaren
 * Vorhaltezeit, zählt markierte Abstürze ([LogEntry.isCrash]) und erlaubt das
 * Ändern der Vorhaltezeit (Rotation), das Filtern auf Fehler/Crashes sowie das
 * Leeren (Puffer + Dateien).
 */
@HiltViewModel
class SettingsLogsViewModel @Inject constructor(
    private val logBuffer: LogBuffer,
    private val logStore: LogStore,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val retention = MutableStateFlow(LogsDefaults.DEFAULT_RETENTION_DAYS)
    private val history = MutableStateFlow<List<LogEntry>>(emptyList())
    private val errorsOnly = MutableStateFlow(false)

    val uiState: StateFlow<LogsUiState> = combine(
        logBuffer.entries,
        retention,
        history,
        errorsOnly,
    ) { live, ret, hist, onlyErrors ->
        val merged = mergeEntries(hist, live)
        val filtered = if (onlyErrors) {
            merged.filter { it.isCrash || it.level >= LogLevel.ERROR }
        } else {
            merged
        }
        LogsUiState(
            entries = filtered.sortedByDescending { it.timestampMillis },
            retentionDays = ret,
            crashCount = merged.count { it.isCrash },
            errorsOnly = onlyErrors,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LogsUiState())

    init {
        viewModelScope.launch {
            val saved = settingsRepository.appSettingsFlow.first().logsRetentionDays
            val clamped = saved.coerceIn(LogsDefaults.MIN_RETENTION_DAYS, LogsDefaults.MAX_RETENTION_DAYS)
            retention.value = clamped
            logStore.prune(clamped)
            history.value = logStore.load(clamped)
        }
    }

    /** Vorhaltezeit ändern (1–30): persistieren, alte Tage löschen, Historie neu laden. */
    fun setRetentionDays(days: Int) {
        val clamped = days.coerceIn(LogsDefaults.MIN_RETENTION_DAYS, LogsDefaults.MAX_RETENTION_DAYS)
        viewModelScope.launch {
            settingsRepository.updateLogsRetentionDays(clamped)
            retention.value = clamped
            logStore.prune(clamped)
            history.value = logStore.load(clamped)
        }
    }

    /** Filter „nur Fehler & Crashes“ umschalten. */
    fun toggleErrorsOnly() {
        errorsOnly.value = !errorsOnly.value
    }

    /** Leert Puffer + persistierte Logs (alle Tage). */
    fun clearLogs() {
        logBuffer.clear()
        viewModelScope.launch {
            logStore.clear()
            history.value = emptyList()
        }
    }

    /**
     * Dedupliziert die persistierte Historie gegen den Live-Puffer (der Tree
     * schreibt in beide) über Zeitstempel+Message und gibt chronologisch
     * aufsteigend zurück.
     */
    private fun mergeEntries(history: List<LogEntry>, live: List<LogEntry>): List<LogEntry> {
        val seen = HashSet<Pair<Long, String>>(history.size + live.size)
        return (history + live).filter { seen.add(it.timestampMillis to it.message) }
    }
}

/** Konstanten für die Log-Vorhaltezeit (1–30 Tage, Default 7). */
object LogsDefaults {
    const val DEFAULT_RETENTION_DAYS = 7
    const val MIN_RETENTION_DAYS = 1
    const val MAX_RETENTION_DAYS = 30
}