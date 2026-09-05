package com.vivid.feature.chat.twitch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** UI-Zustand der Twitch-Kanalintegration. */
data class TwitchChannelUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val streamInfo: TwitchStreamInfo? = null,
    val error: String? = null,
    val lastUpdatedAtMillis: Long? = null,
)

/**
 * Bindet Twitch-Helix-Kanalinformationen an Settings und Streaming-UI.
 *
 * Viewer werden nur auf ausdrückliche Anfrage geladen; die Streaming-UI kann
 * [refresh] in einem sichtbaren Lifecycle-Effect periodisch ausführen. Dadurch
 * gibt es im Hintergrund keine Netzwerkaktivität und Tests bleiben deterministisch.
 *
 * Bevorzugt werden die verschlüsselt gespeicherten OAuth-Tokens aus
 * [TwitchTokenStore] verwendet (Fallback: die Klartext-Settings-Werte für
 * bestehende Installationen ohne gültige Session).
 */
@HiltViewModel
class TwitchChannelViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val channelClient: TwitchChannelClient,
    private val tokenStore: TwitchTokenStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TwitchChannelUiState())
    val uiState: StateFlow<TwitchChannelUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            val config = settings.channelConfig()
            if (!config.isConfigured) {
                _uiState.value = TwitchChannelUiState(
                    error = "Twitch-Kanal ist nicht vollständig konfiguriert.",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { channelClient.getStreamInfo(config) }
                .onSuccess { info ->
                    _uiState.value = TwitchChannelUiState(
                        streamInfo = info,
                        lastUpdatedAtMillis = System.currentTimeMillis(),
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Twitch-Kanalinformationen konnten nicht geladen werden.",
                    )
                }
        }
    }

    /** Setzt Titel/Kategorie und speichert die Eingaben für den nächsten Abruf. */
    fun updateChannelInfo(title: String, category: String) {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            val config = settings.channelConfig()
            if (!config.isConfigured) {
                _uiState.value = _uiState.value.copy(
                    error = "Twitch-Kanal ist nicht vollständig konfiguriert.",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(saving = true, error = null)
            runCatching { channelClient.updateChannelInfo(config, title, category) }
                .onSuccess {
                    settingsRepository.updateTwitchChannelSettings(
                        channel = settings.chatChannel,
                        oauthToken = settings.twitchChannelOauthToken,
                        title = title.trim(),
                        category = category.trim(),
                    )
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        streamInfo = _uiState.value.streamInfo?.copy(
                            title = title.trim(),
                            category = category.trim(),
                        ),
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        error = error.message ?: "Twitch-Kanalinformationen konnten nicht gesetzt werden.",
                    )
                }
        }
    }

    private suspend fun AppSettings.channelConfig(): TwitchChannelConfig {
        // Der dedizierte Broadcaster-Token ist optional; bestehende Installationen
        // können zunächst den bereits hinterlegten Bot-Token verwenden. Liegt eine
        // verschlüsselte OAuth-Session vor, gewinnt deren (ggf. erneuertes) Access-Token.
        val storedAccess = tokenStore.loadSession()?.accessToken?.takeIf { it.isNotBlank() }
        return TwitchChannelConfig(
            channel = chatChannel.trim(),
            oauthToken = storedAccess ?: twitchChannelOauthToken.ifBlank { chatBotOauthToken },
            clientId = chatBotTwitchClientId,
        )
    }
}
