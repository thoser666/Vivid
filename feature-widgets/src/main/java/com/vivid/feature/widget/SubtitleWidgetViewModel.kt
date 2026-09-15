package com.vivid.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.SubtitleState
import com.vivid.core.data.SubtitleStateMachine
import com.vivid.core.data.SpeechToTextEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Verdrahtet das Untertitel-Overlay (PARITY-Zeile 143): Settings-Toggle →
 * [SubtitleStateMachine] → [SpeechToTextEngine]. Die Maschine liefert die
 * reine Entscheidungslogik (Rolling Lines, Fehler-Taxonomie, Backoff), die
 * Engine die Plattform-Spracherkennung — dieser ViewModel ist die Brücke und
 * spiegelt die Transitionen in einen [StateFlow] für die UI.
 */
@HiltViewModel
class SubtitleWidgetViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val engine: SpeechToTextEngine,
) : ViewModel() {

    private val machine = SubtitleStateMachine()

    private val _state = MutableStateFlow(SubtitleState())
    val state: StateFlow<SubtitleState> = _state.asStateFlow()

    /** Aktiver Restart-Job (Backoff-Warteschlange); abgebrochen bei Reset. */
    private var restartJob: Job? = null

    private val engineListener = object : SpeechToTextEngine.Listener {
        override fun onListening() {
            update { machine.onListening(it) }
        }

        override fun onPartial(text: String) {
            update { machine.onPartial(it, text) }
        }

        override fun onFinal(text: String) {
            update { machine.onFinal(it, text) }
            // Nach einem finalen Satz die Erkennung sofort neu anstoßen (der
            // Plattform-Recognizer beendet die Sitzung nach jedem Ergebnis).
            engine.start()
        }

        override fun onError(code: Int) {
            val next = machine.onError(_state.value, code)
            _state.value = next.state
            if (next.restart) scheduleRestart() else engine.stop()
        }
    }

    init {
        engine.setListener(engineListener)
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                setEnabled(settings.subtitlesEnabled)
            }
        }
    }

    /** Toggle-Anwendung: startet/stoppt die Engine je nach Capability-Fallback. */
    private fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            restartJob?.cancel()
            restartJob = null
            engine.stop()
            _state.value = machine.reset()
            return
        }
        val available = engine.isAvailable
        update { machine.onEnabled(it, available) }
        if (available) {
            engine.start()
        }
    }

    /** Restart nach Fehler mit dem von der Maschine berechneten Backoff. */
    private fun scheduleRestart() {
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(machine.restartDelayMs())
            engine.start()
        }
    }

    /** Transition anwenden und in den UI-Flow spiegeln. */
    private fun update(transform: (SubtitleState) -> SubtitleState): SubtitleState {
        val next = transform(_state.value)
        _state.value = next
        return next
    }

    /** Test-Hook: onCleared ist protected; Tests geben die Engine ueber diesen Weg frei. */
    internal fun onClearedForTest() = onCleared()

    override fun onCleared() {
        restartJob?.cancel()
        engine.setListener(null)
        engine.stop()
    }
}
