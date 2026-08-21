package com.vivid.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.ChatBadge
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.twitch.TwitchBadgeClient
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.twitch.TwitchEventSubConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatOverlayViewModel @Inject constructor(
    private val chatReader: TwitchChatEventSubReader,
    private val settingsRepository: SettingsRepository,
    private val badgeClient: TwitchBadgeClient,
) : ViewModel() {

    companion object {
        /** Maximale Anzahl an Nachrichten, die im Overlay vorgehalten werden. */
        const val MAX_MESSAGES = 50
    }

    data class ChatOverlayUiState(
        val enabled: Boolean = false,
        val channel: String = "",
        /** Bot-Login + OAuth-Token + Client-ID gesetzt? EventSub braucht einen Token. */
        val configured: Boolean = false,
        val messages: List<ChatMessage> = emptyList(),
        val connection: ChatConnectionState = ChatConnectionState.Disconnected,
        /**
         * Twitch-Badge-Bilder je `"set_id/version_id"` (z. B. `broadcaster/1`,
         * `moderator/1`, `subscriber/6`) — vom [TwitchBadgeClient] beim Start
         * geladen; ohne Badge-Daten zeigt das Overlay nur den Usernamen.
         */
        val badges: Map<String, ChatBadge> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(ChatOverlayUiState())
    val uiState: StateFlow<ChatOverlayUiState> = _uiState.asStateFlow()

    /**
     * Kanal, für den der aktuell laufende Badge-Load gestartet wurde (oder
     * `null`, wenn kein Load aktiv ist). In-flight Badge-Loads werden anhand
     * dieses Tokens verworfen, wenn seit ihrem Start ein Kanalwechsel oder
     * ein Stopp erfolgt ist — sonst würde eine langsame Antwort des alten
     * Kanals die Badge-Map des neuen Kanals überschreiben.
     */
    private var badgeLoadChannel: String? = null

    init {
        // Reagiert auf die Chat-Einstellungen: startet/stoppt die Verbindung
        // und räumt Nachrichten bei Kanal-/Statuswechseln auf. Seit dem
        // IRC-Ausstieg liest das Overlay über EventSub `channel.chat.message`
        // und braucht deshalb die Bot-Zugangsdaten (Login + Token + Client-ID).
        viewModelScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                val enabled = settings.chatOverlayEnabled
                val channel = settings.chatChannel.trim().lowercase()
                val configured = settings.chatBotLogin.isNotBlank() &&
                    settings.chatBotOauthToken.isNotBlank() &&
                    settings.chatBotTwitchClientId.isNotBlank()
                val previous = _uiState.value
                val contextChanged = previous.enabled != enabled || previous.channel != channel

                if (enabled && channel.isNotBlank() && configured) {
                    val config = TwitchEventSubConfig(
                        botLogin = settings.chatBotLogin,
                        oauthToken = settings.chatBotOauthToken,
                        clientId = settings.chatBotTwitchClientId,
                        channel = channel,
                    )
                    chatReader.start(config)
                    loadBadges(config)
                } else {
                    // Stopp/fehlende Konfiguration: laufende Badge-Loads
                    // invalidieren, damit ihre Antworten nicht mehr ankommen.
                    badgeLoadChannel = null
                    chatReader.stop()
                }
                _uiState.update {
                    it.copy(
                        enabled = enabled,
                        channel = channel,
                        configured = configured,
                        messages = if (contextChanged) emptyList() else it.messages,
                        // Bei Kanalwechsel/Deaktivierung erst einmal leeren — die
                        // neuen Badges kommen asynchron mit dem nächsten Load.
                        badges = if (contextChanged) emptyMap() else it.badges,
                    )
                }
            }
        }
        // Neue Nachrichten des Readers in den UI-State aufnehmen (begrenzt).
        viewModelScope.launch {
            chatReader.messages.collect { message ->
                _uiState.update { state ->
                    state.copy(messages = (state.messages + message).takeLast(MAX_MESSAGES))
                }
            }
        }
        // Verbindungsstatus durchreichen.
        viewModelScope.launch {
            chatReader.state.collect { connection ->
                _uiState.update { it.copy(connection = connection) }
            }
        }
    }

    /**
     * Lädt die Twitch-Badges (global + Kanal) für das Overlay-Rendering.
     * Fehler sind unkritisch: das Overlay zeigt dann einfach keine
     * Badge-Bilder, der Chat läuft weiter.
     *
     * Stale-Guard: Der Load merkt sich den Kanal, für den er gestartet wurde.
     * Ist beim Eintreffen der Antwort ein anderer Kanal aktiv (Kanalwechsel
     * dazwischen) oder der Kanal null (Overlay gestoppt), wird die Antwort
     * verworfen — so überschreibt ein langsamer Load des alten Kanals nie die
     * Badges des neuen Kanals.
     */
    private fun loadBadges(config: TwitchEventSubConfig) {
        badgeLoadChannel = config.channel
        viewModelScope.launch {
            val badges = runCatching { badgeClient.load(config) }.getOrDefault(emptyMap())
            if (badgeLoadChannel == config.channel) {
                _uiState.update { it.copy(badges = badges) }
            }
        }
    }

    override fun onCleared() {
        chatReader.stop()
        super.onCleared()
    }
}
