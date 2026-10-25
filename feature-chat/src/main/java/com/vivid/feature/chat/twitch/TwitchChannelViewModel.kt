package com.vivid.feature.chat.twitch

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.R
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

    /**
     * Rohdetail der Ursache (bleibt unübersetzt, i18n-plan §4) — nur gesetzt,
     * wenn die Ursache kein [errorRes] liefert.
     */
    val error: String? = null,

    /**
     * Ressourcen-ID (`@StringRes`) der lokalisierten Meldung, die die App
     * selbst formuliert (i18n-plan §4) — erst bei der Anzeige aufzulösen;
     * `0`, wenn nur ein Rohdetail vorliegt. Vorrang vor [error].
     */
    @StringRes val errorRes: Int = 0,

    /** Platzhalter-Argumente für [errorRes]. */
    val formatArgs: List<String> = emptyList(),

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
                    errorRes = R.string.twitch_channel_error_not_configured,
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
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(loading = false)
                        .withFailure(failure, R.string.twitch_channel_error_load_failed)
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
                    error = null,
                    errorRes = R.string.twitch_channel_error_not_configured,
                    formatArgs = emptyList(),
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
                .onFailure { failure ->
                    _uiState.value = _uiState.value.copy(saving = false)
                        .withFailure(failure, R.string.twitch_channel_error_save_failed)
                }
        }
    }

    /**
     * Setzt die Fehlerursache auf den UI-Zustand (i18n-plan §4): eine
     * lokalisierte Ressource hat Vorrang, ein Rohdetail der Ursache bleibt
     * unübersetzt, [fallbackRes] greift, wenn beides fehlt.
     */
    private fun TwitchChannelUiState.withFailure(
        failure: Throwable,
        @StringRes fallbackRes: Int,
    ): TwitchChannelUiState {
        val twitch = failure as? TwitchChannelException
        return when {
            twitch != null && twitch.messageRes != 0 -> copy(
                error = null,
                errorRes = twitch.messageRes,
                formatArgs = twitch.formatArgs,
            )
            !failure.message.isNullOrBlank() -> copy(
                error = failure.message,
                errorRes = 0,
                formatArgs = emptyList(),
            )
            else -> copy(error = null, errorRes = fallbackRes, formatArgs = emptyList())
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
