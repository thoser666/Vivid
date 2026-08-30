package com.vivid.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.ChatOverlayPosition
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.model.ChatBadge
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.emotes.EmoteSource
import com.vivid.feature.chat.emotes.ThirdPartyEmote
import com.vivid.feature.chat.emotes.ThirdPartyEmoteService
import com.vivid.feature.chat.twitch.TwitchBadgeClient
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.twitch.TwitchEventSubConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
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
    private val emoteService: ThirdPartyEmoteService,
) : ViewModel() {

    companion object {
        /** Maximale Anzahl an Nachrichten, die im Overlay vorgehalten werden. */
        const val MAX_MESSAGES = 50

        /** Maximale Anzahl gleichzeitig angezeigter Event-Alerts. */
        const val MAX_ALERTS = 3

        /** Anzeigedauer eines Event-Alerts, danach verschwindet er. */
        const val ALERT_TTL_MS = 10_000L
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
        /** Third-Party-Emotes (BTTV/FFZ/7TV) als Name→URL-Map. */
        val thirdPartyEmotes: Map<String, String> = emptyMap(),
        /**
         * Event-Alerts (Follow/Sub/Raid) aus dem EventSub-WebSocket — max.
         * [MAX_ALERTS] gleichzeitig, jeder verschwindet nach [ALERT_TTL_MS]
         * automatisch wieder.
         */
        val alerts: List<ChatAlert> = emptyList(),
        /** IDs gelöschter Nachrichten (vom EventSub Topic `channel.chat.message_delete`). */
        val deletedMessageIds: Set<String> = emptySet(),
        /** Gelöschte Nachrichten ausblenden statt ausgrauen. */
        val hideDeleted: Boolean = true,
        // Chat-Layout-Einstellungen
        val overlayWidthDp: Int = 240,
        val overlayHeightDp: Int = 300,
        val overlayBackgroundAlpha: Float = 0.5f,
        val overlayFontSizeSp: Int = 12,
        val overlayShowTimestamp: Boolean = true,
        // Chat-Overlay-Farben
        val overlayUsernameColorHex: String = "#B39DDB",
        val overlayTextColorHex: String = "#FFFFFF",
        val overlayBackgroundColorHex: String = "#000000",
        // Chat-Overlay-Position
        val overlayPosition: ChatOverlayPosition = ChatOverlayPosition.TOP_END,
        // Fade-In-Animation für neue Nachrichten
        val overlayAnimateNewMessages: Boolean = true,
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
                    loadThirdPartyEmotes(config.channel)
                } else {
                    // Stopp/fehlende Konfiguration: laufende Badge-Loads
                    // invalidieren, damit ihre Antworten nicht mehr ankommen.
                    badgeLoadChannel = null
                    chatReader.stop()
                }

                // Third-Party-Emote-Quellen aus den Settings synchronisieren.
                val activeSources = mutableSetOf<EmoteSource>()
                if (settings.emotesBttvEnabled) activeSources.add(EmoteSource.BTTV)
                if (settings.emotesFfzEnabled) activeSources.add(EmoteSource.FFZ)
                if (settings.emotes7tvEnabled) activeSources.add(EmoteSource.SEVENTV)
                val previousSources = emoteService.activeSources.value
                emoteService.setActiveSources(activeSources)
                // Bei Quellen-Änderung Cache leeren und neu laden.
                if (activeSources != previousSources && enabled && channel.isNotBlank() && configured) {
                    emoteService.invalidateCache(channel)
                    loadThirdPartyEmotes(channel)
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
                        // Alerts gehören zum vorherigen Kanal — bei Wechsel/Stopp
                        // sofort entfernen (TTL-Entfernung läuft sonst weiter).
                        alerts = if (contextChanged) emptyList() else it.alerts,
                        // Gelöschte Nachrichten: bei Kanalwechsel zurücksetzen
                        deletedMessageIds = if (contextChanged) emptySet() else it.deletedMessageIds,
                        hideDeleted = settings.chatOverlayHideDeleted,
                        // Chat-Layout-Einstellungen
                        overlayWidthDp = settings.chatOverlayWidthDp,
                        overlayHeightDp = settings.chatOverlayHeightDp,
                        overlayBackgroundAlpha = settings.chatOverlayBackgroundAlpha,
                        overlayFontSizeSp = settings.chatOverlayFontSizeSp,
                        overlayShowTimestamp = settings.chatOverlayShowTimestamp,
                        overlayUsernameColorHex = settings.chatOverlayUsernameColorHex,
                        overlayTextColorHex = settings.chatOverlayTextColorHex,
                        overlayBackgroundColorHex = settings.chatOverlayBackgroundColorHex,
                        overlayPosition = settings.chatOverlayPosition,
                        overlayAnimateNewMessages = settings.chatOverlayAnimateNewMessages,
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
        // Event-Alerts aufnehmen (begrenzt) und nach Ablauf der TTL wieder
        // entfernen. Entfernt wird per ID-Gleichheit — die IDs der Alerts sind
        // eindeutig, ein TTL-Timer löscht also nie fremde Alerts mit.
        viewModelScope.launch {
            chatReader.alerts.collect { alert ->
                _uiState.update { state ->
                    state.copy(alerts = (state.alerts + alert).takeLast(MAX_ALERTS))
                }
                viewModelScope.launch {
                    delay(ALERT_TTL_MS)
                    _uiState.update { state -> state.copy(alerts = state.alerts - alert) }
                }
            }
        }
        // Verbindungsstatus durchreichen.
        viewModelScope.launch {
            chatReader.state.collect { connection ->
                _uiState.update { it.copy(connection = connection) }
            }
        }
        // Gelöschte Nachrichten (EventSub: channel.chat.message_delete)
        // sammeln und im UI-State markieren.
        viewModelScope.launch {
            chatReader.deletedMessageIds.collect { deletedId ->
                _uiState.update { state ->
                    state.copy(deletedMessageIds = state.deletedMessageIds + deletedId)
                }
            }
        }
    }

    /**
     * Trigger-API: löst über den Reader einen synthetischen Test-Alert aus
     * (ohne Netzwerk) — zum Verifizieren des Overlay-Renderings vor dem
     * Go-Live bzw. in Tests.
     */
    fun triggerTestAlert(type: ChatAlertType) {
        chatReader.triggerTestAlert(type)
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

    /**
     * Lädt Third-Party-Emotes (BTTV/FFZ/7TV) für den Kanal.
     * Fehler sind unkritisch: das Overlay zeigt dann nur Twitch-Emotes.
     */
    private fun loadThirdPartyEmotes(channelId: String) {
        viewModelScope.launch {
            emoteService.loadEmotes(channelId)
            // Emotes als Name→URL-Map im UI-State speichern
            emoteService.emotes.collect { emoteMap ->
                val channelEmotes = emoteMap[channelId] ?: emptyList()
                val emoteNameToUrl = channelEmotes.associate { it.name to it.url }
                _uiState.update { it.copy(thirdPartyEmotes = emoteNameToUrl) }
            }
        }
    }

    override fun onCleared() {
        chatReader.stop()
    }
}
