package com.vivid.irlbroadcaster.ui.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel des In-App-Pomodoro-Timers (PARITY-Zeile 143).
 *
 * Liest die Einstellungen EINMALIG beim Start ([SettingsRepository.appSettingsFlow]
 * → `first()`), damit eine spätere Prefs-Änderung den laufenden Countdown nicht
 * zurücksetzt. Der Sekundentakt läuft in [viewModelScope]; die Phasen-Enden
 * werden über [phaseComplete] an den Screen gemeldet, der die Vibration auslöst.
 */
@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()

    private val _phaseComplete = MutableSharedFlow<PomodoroPhase>(extraBufferCapacity = 16)
    val phaseComplete: SharedFlow<PomodoroPhase> = _phaseComplete.asSharedFlow()

    private var controller: PomodoroController? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            val controller = PomodoroController(
                enabled = settings.pomodoroEnabled,
                workDurationMinutes = settings.pomodoroWorkDurationMinutes,
                breakDurationMinutes = settings.pomodoroBreakDurationMinutes,
            ).also { it.onPhaseComplete = { phase -> _phaseComplete.tryEmit(phase) } }
            this@PomodoroViewModel.controller = controller
            _uiState.value = controller.snapshot()
            runTickLoop(controller)
        }
    }

    private suspend fun runTickLoop(controller: PomodoroController) {
        while (true) {
            delay(TICK_INTERVAL_MS)
            controller.tick()
            _uiState.value = controller.snapshot()
        }
    }

    /** Start/Pause umschalten. */
    fun toggleRunning() = updateController { it.toggleRunning() }

    /** Aktuelle Phase überspringen (ohne Vibration). */
    fun skipPhase() = updateController { it.skipPhase() }

    /** Timer auf die erste Arbeitsphase zurücksetzen. */
    fun reset() = updateController { it.reset() }

    private inline fun updateController(action: (PomodoroController) -> Unit) {
        val controller = controller ?: return
        action(controller)
        _uiState.value = controller.snapshot()
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
    }
}
