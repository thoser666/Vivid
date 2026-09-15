package com.vivid.feature.obscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.core.repository.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class ObsControlViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // OBS-Steuerung (PARITY Row 71): eigener Blackout-Übergang, damit "Screen black"
    // nie eine Nutzer-Szene überschreibt.
    companion object {
        const val BLACKOUT_SCENE = "Vivid Blackout"
        const val BLACKOUT_INPUT = "Vivid-Blackout"
        const val SYNC_STEP_NS = 50_000_000L
    }

    private val _uiState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val uiState: StateFlow<ConnectionState> = _uiState.asStateFlow()

    private val _blackoutActive = MutableStateFlow(false)
    val blackoutActive: StateFlow<Boolean> = _blackoutActive.asStateFlow()

    private val _blackoutPreparing = MutableStateFlow(false)
    val blackoutPreparing: StateFlow<Boolean> = _blackoutPreparing.asStateFlow()

    private val _restoreScene = MutableStateFlow<String?>(null)

    // Gespeichertes Scheme aus den Einstellungen, damit das Quick-Connect-Panel
    // nicht still auf ws:// zurücksetzt, wenn wss:// konfiguriert wurde.
    private val _savedUseTls = MutableStateFlow(false)
    val savedUseTls: StateFlow<Boolean> = _savedUseTls.asStateFlow()

    val inputs = streamingRepository.obsInputs
    val muteStates = streamingRepository.obsMuteStates
    val audioLevels = streamingRepository.obsAudioLevels
    val syncOffsets = streamingRepository.obsSyncOffsets
    val scenes = streamingRepository.obsScenes
    val currentProgramScene = streamingRepository.obsCurrentProgramScene
    val snapshot = streamingRepository.obsSnapshot

    init {
        streamingRepository.isConnectedToObs
            .onEach { isConnected ->
                _uiState.value = if (isConnected) ConnectionState.Connected else ConnectionState.Disconnected
                if (isConnected) {
                    refreshAfterConnect()
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.appSettingsFlow
            .map { it.obsUseTls }
            .onEach { _savedUseTls.value = it }
            .launchIn(viewModelScope)

        // Blackout-Warteschleife: sobald die Blackout-Szene in der Szenenliste
        // auftaucht (Antwort auf GetSceneList nach createScene), wird der Input
        // angelegt und die Szene gesetzt — danach aktiv. Kein harter Timeout,
        // ein erneutes Tippen stoppt die Vorbereitung.
        scenes
            .onEach { list ->
                if (list.contains(BLACKOUT_SCENE) && _preparingBlackout) {
                    _blackoutInputCreated = true
                    _preparingBlackout = false
                    _blackoutPreparing.value = false
                    streamingRepository.obsCreateBlackoutInput(BLACKOUT_SCENE, BLACKOUT_INPUT)
                    streamingRepository.obsSetProgramScene(BLACKOUT_SCENE)
                    _blackoutActive.value = true
                }
            }
            .launchIn(viewModelScope)
    }

    private fun refreshAfterConnect() {
        streamingRepository.obsRefreshInputs()
        streamingRepository.obsRefreshScenes()
        streamingRepository.obsRefreshProgramScene()
    }

    fun connect(password: String, ip: String, port: String, useTls: Boolean = false) {
        val portNumber = port.toIntOrNull()
        if (portNumber == null) {
            _uiState.value = ConnectionState.Error(messageRes = R.string.obs_invalid_port_message)
            return
        }

        _uiState.value = ConnectionState.Connecting
        try {
            streamingRepository.connectToObs(password, ip, portNumber, useTls)
        } catch (e: Exception) {
            _uiState.value = ConnectionState.Error(
                message = e.message ?: "",
                messageRes = if (e.message.isNullOrBlank()) R.string.obs_connect_failed_message else 0,
            )
        }
    }

    fun disconnect() {
        streamingRepository.disconnectFromObs()
    }

    fun toggleMute(inputName: String) {
        streamingRepository.obsToggleMute(inputName)
    }

    fun refreshSyncOffset(inputName: String) {
        streamingRepository.obsRefreshSyncOffset(inputName)
    }

    /** Verschiebt den Sync-Offset eines Inputs in 50-ms-Schritten (clamped >= 0). */
    fun adjustSyncOffset(inputName: String, direction: Int) {
        val current = syncOffsets.value[inputName] ?: 0L
        val next = (current + direction * SYNC_STEP_NS).coerceAtLeast(0L)
        streamingRepository.obsSetSyncOffset(inputName, next)
    }

    /** Snapshot der aktuellen Programm-Szene als PNG. */
    fun takeScreenshot() {
        val source = currentProgramScene.value ?: return
        streamingRepository.obsTakeScreenshot(source)
    }

    /** Blendet auf die Blackout-Szene um bzw. zurück zur vorherigen Szene. */
    fun toggleBlackout() {
        if (_preparingBlackout) return
        if (_blackoutActive.value) {
            _blackoutActive.value = false
            _blackoutPreparing.value = false
            _restoreScene.value?.takeIf { it.isNotBlank() }?.let { streamingRepository.obsSetProgramScene(it) }
            _restoreScene.value = null
            return
        }

        _restoreScene.value = currentProgramScene.value
        if (!scenes.value.contains(BLACKOUT_SCENE)) {
            _preparingBlackout = true
            _blackoutInputCreated = false
            _blackoutPreparing.value = true
            streamingRepository.obsCreateScene(BLACKOUT_SCENE)
            return
        }

        _blackoutInputCreated = true
        streamingRepository.obsCreateBlackoutInput(BLACKOUT_SCENE, BLACKOUT_INPUT)
        streamingRepository.obsSetProgramScene(BLACKOUT_SCENE)
        _blackoutActive.value = true
    }

    // Blackout-Merkzustand im Screen-Thread des ViewModels; die Slots halten die
    // Vorbereitung pro Verbindung synchron, solange der scenes-Flow noch läuft.
    private var _preparingBlackout = false
    private var _blackoutInputCreated = false
}