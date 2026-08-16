package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings // Importiert die vollständige Klasse
import com.vivid.core.data.ChatBotMode
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteControlServer
import com.vivid.core.remote.RemoteControlTokenStore
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand des Update-Indikators auf dem Settings-Screen (für den Obtainium-Test). */
data class SettingsUpdateState(
    val checking: Boolean = false,
    val result: UpdateCheckResult? = null,
)

/** Zugangsdaten der Web-Remote-Control für die Anzeige in den Settings. */
data class RemoteControlInfo(
    val port: Int = RemoteControlServer.DEFAULT_PORT,
    val token: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker,
    private val remoteControlTokenStore: RemoteControlTokenStore,
    private val remoteControlServer: RemoteControlServer,
) : ViewModel() {

    // Der StateFlow verwendet jetzt die vollständige AppSettings-Klasse.
    private val _uiState = MutableStateFlow(AppSettings())
    val uiState = _uiState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<Unit>()
    val saveEvent = _saveEvent.asSharedFlow()

    private val _updateState = MutableStateFlow(SettingsUpdateState())
    val updateState = _updateState.asStateFlow()

    private val _remoteControl = MutableStateFlow(RemoteControlInfo())
    val remoteControl = _remoteControl.asStateFlow()

    /** Version, für die der letzte Check lief — verhindert Mehrfach-Checks pro Screen-Öffnung. */
    private var lastCheckedVersion: String? = null

    /**
     * Prüft einmalig gegen die GitHub-Releases, ob ein neueres Build existiert.
     * Aufgerufen vom Settings-Screen (LaunchedEffect); das Ergebnis zeigt die UI
     * als „Update verfügbar“-Badge — direkt im Einstieg für den Obtainium-Update-Test.
     */
    fun checkForUpdates(installedVersionName: String) {
        if (installedVersionName.isBlank() || lastCheckedVersion == installedVersionName || _updateState.value.checking) {
            return
        }
        lastCheckedVersion = installedVersionName
        viewModelScope.launch {
            _updateState.value = SettingsUpdateState(checking = true)
            _updateState.value = SettingsUpdateState(checking = false, result = updateChecker.check(installedVersionName))
        }
    }

    init {
        viewModelScope.launch {
            // Sammelt die Daten vom neuen, kombinierten Flow im Repository.
            settingsRepository.appSettingsFlow.collect { settings ->
                _uiState.value = settings
            }
        }
        viewModelScope.launch {
            // Token der Web-Remote-Control laden (wird bei Bedarf erzeugt) —
            // damit der Nutzer die LAN-URL im Browser aufrufen kann.
            val token = remoteControlTokenStore.getOrCreateToken()
            _remoteControl.value = RemoteControlInfo(port = RemoteControlServer.DEFAULT_PORT, token = token)
        }
    }

    // Diese Funktionen aktualisieren den State. Sie funktionieren dank .copy() perfekt.
    fun onStreamUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(streamUrl = newUrl) }
    fun onStreamKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(streamKey = newKey) }
    fun onStreamUseTlsChange(newUseTls: Boolean) { _uiState.value = _uiState.value.copy(streamUseTls = newUseTls) }

    // Zweites (optionales) Stream-Ziel für Multi-Streaming.
    fun onSecondaryStreamUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(secondaryStreamUrl = newUrl) }
    fun onSecondaryStreamKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(secondaryStreamKey = newKey) }
    fun onSecondaryStreamUseTlsChange(newUseTls: Boolean) { _uiState.value = _uiState.value.copy(secondaryStreamUseTls = newUseTls) }

    /**
     * Übernimmt eine Plattform-Vorlage: setzt die Ingest-URL und aktiviert
     * automatisch die sichere Verbindung (rtmps://).
     */
    fun applyPlatformPreset(platform: StreamPlatform) {
        _uiState.value = _uiState.value.copy(
            streamUrl = platform.ingestUrl,
            streamUseTls = true,
        )
    }
    fun onObsHostChange(newHost: String) { _uiState.value = _uiState.value.copy(obsHost = newHost) }
    fun onObsPortChange(newPort: String) { _uiState.value = _uiState.value.copy(obsPort = newPort) }
    fun onObsPasswordChange(newPassword: String) { _uiState.value = _uiState.value.copy(obsPassword = newPassword) }
    fun onObsUseTlsChange(newUseTls: Boolean) { _uiState.value = _uiState.value.copy(obsUseTls = newUseTls) }

    // Chat-Overlay-Einstellungen.
    fun onChatChannelChange(newChannel: String) { _uiState.value = _uiState.value.copy(chatChannel = newChannel) }
    fun onChatOverlayEnabledChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(chatOverlayEnabled = newEnabled) }

    // Chat-Bot-Einstellungen.
    fun onChatBotEnabledChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(chatBotEnabled = newEnabled) }
    fun onChatBotModeChange(newMode: ChatBotMode) { _uiState.value = _uiState.value.copy(chatBotMode = newMode) }
    fun onChatBotLoginChange(newLogin: String) { _uiState.value = _uiState.value.copy(chatBotLogin = newLogin) }
    fun onChatBotOauthTokenChange(newToken: String) { _uiState.value = _uiState.value.copy(chatBotOauthToken = newToken) }
    fun onChatBotApiBaseUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(chatBotApiBaseUrl = newUrl) }
    fun onChatBotApiKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(chatBotApiKey = newKey) }
    fun onChatBotModelChange(newModel: String) { _uiState.value = _uiState.value.copy(chatBotModel = newModel) }
    fun onChatBotSystemPromptChange(newPrompt: String) { _uiState.value = _uiState.value.copy(chatBotSystemPrompt = newPrompt) }
    // Numerische Felder: Roh-Text aus dem Eingabefeld, unlesbar → 0 (Cooldown aus / unbegrenzt).
    fun onChatBotReplyCooldownSecondsChange(raw: String) {
        _uiState.value = _uiState.value.copy(chatBotReplyCooldownSeconds = raw.toLongOrNull() ?: 0L)
    }
    fun onChatBotMentionsOnlyChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(chatBotMentionsOnly = newEnabled) }
    fun onChatBotMaxRepliesPerMinuteChange(raw: String) {
        _uiState.value = _uiState.value.copy(chatBotMaxRepliesPerMinute = raw.toIntOrNull() ?: 0)
    }

    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value
            // Speichere beide Einstellungs-Typen.
            settingsRepository.updateStreamSettings(
                url = currentSettings.streamUrl,
                key = currentSettings.streamKey,
                useTls = currentSettings.streamUseTls,
            )
            settingsRepository.updateSecondaryStreamSettings(
                url = currentSettings.secondaryStreamUrl,
                key = currentSettings.secondaryStreamKey,
                useTls = currentSettings.secondaryStreamUseTls,
            )
            settingsRepository.updateObsSettings(
                host = currentSettings.obsHost,
                port = currentSettings.obsPort,
                password = currentSettings.obsPassword,
                useTls = currentSettings.obsUseTls,
            )
            settingsRepository.updateChatSettings(
                channel = currentSettings.chatChannel,
                overlayEnabled = currentSettings.chatOverlayEnabled,
            )
            settingsRepository.updateChatBotSettings(
                enabled = currentSettings.chatBotEnabled,
                apiBaseUrl = currentSettings.chatBotApiBaseUrl,
                apiKey = currentSettings.chatBotApiKey,
                model = currentSettings.chatBotModel,
                systemPrompt = currentSettings.chatBotSystemPrompt,
                replyCooldownSeconds = currentSettings.chatBotReplyCooldownSeconds,
                mentionsOnly = currentSettings.chatBotMentionsOnly,
                maxRepliesPerMinute = currentSettings.chatBotMaxRepliesPerMinute,
                mode = currentSettings.chatBotMode,
                login = currentSettings.chatBotLogin,
                oauthToken = currentSettings.chatBotOauthToken,
            )
            _saveEvent.emit(Unit)
        }
    }

    /**
     * Startet den Web-Remote-Control-Server neu.
     *
     * Ab Android 17 (targetSdk 37) braucht die Remote-Control die
     * `ACCESS_LOCAL_NETWORK`-Runtime-Berechtigung. Wird sie erst nach dem
     * App-Start erteilt, muss der Server neu starten, damit der lauschende
     * Socket die neue Berechtigung übernimmt.
     */
    fun restartRemoteControlServer() {
        viewModelScope.launch {
            runCatching {
                remoteControlServer.stop()
                remoteControlServer.start()
            }
        }
    }
}
