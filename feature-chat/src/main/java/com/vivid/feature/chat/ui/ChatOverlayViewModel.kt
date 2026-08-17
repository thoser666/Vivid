package com.vivid.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
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
    )

    private val _uiState = MutableStateFlow(ChatOverlayUiState())
    val uiState: StateFlow<ChatOverlayUiState> = _uiState.asStateFlow()

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
                    chatReader.start(
                        TwitchEventSubConfig(
                            botLogin = settings.chatBotLogin,
                            oauthToken = settings.chatBotOauthToken,
                            clientId = settings.chatBotTwitchClientId,
                            channel = channel,
                        ),
                    )
                } else {
                    chatReader.stop()
                }
                _uiState.update {
                    it.copy(
                        enabled = enabled,
                        channel = channel,
                        configured = configured,
                        messages = if (contextChanged) emptyList() else it.messages,
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

    override fun onCleared() {
        chatReader.stop()
        super.onCleared()
    }
}
