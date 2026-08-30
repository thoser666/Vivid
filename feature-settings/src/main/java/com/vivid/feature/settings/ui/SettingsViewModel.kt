package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.AccentColor
import com.vivid.core.data.AppSettings // Importiert die vollständige Klasse
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.ThemeMode
import com.vivid.core.remote.RemoteControlServer
import com.vivid.core.remote.RemoteControlTokenStore
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import com.vivid.feature.chat.bot.ChatBotEngine
import com.vivid.feature.chat.bot.ChatBotUsage
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

/**
 * KI-Quelle, die die Owner-Befehle (!start/!stop/!diag/!ask) aktuell nutzen
 * (Anzeige im Settings-Screen). Spiegelt die Engine-Auswahl
 * (`ChatBotEngine.ownerLlm`): eigene Owner-KI bevorzugt, sonst Viewer-KI als
 * Fallback, sonst deterministisch (Checkliste/Hinweis).
 *
 * Label/Beschreibung sind String-Ressourcen (i18n).
 */
enum class OwnerLlmSource(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    OWNER(
        labelRes = R.string.owner_source_owner,
        descriptionRes = R.string.owner_source_owner_desc,
    ),
    VIEWER_FALLBACK(
        labelRes = R.string.owner_source_viewer_fallback,
        descriptionRes = R.string.owner_source_viewer_fallback_desc,
    ),
    DETERMINISTIC(
        labelRes = R.string.owner_source_deterministic,
        descriptionRes = R.string.owner_source_deterministic_desc,
    ),
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker,
    private val remoteControlTokenStore: RemoteControlTokenStore,
    private val remoteControlServer: RemoteControlServer,
    private val chatBotEngine: ChatBotEngine,
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

    /** Live-Zählerstand des Chat-Bots (Antworten/Std, Budget, Top-Viewer). */
    val botUsage: StateFlow<ChatBotUsage> = chatBotEngine.usage

    /**
     * Aktive KI-Quelle der Owner-Befehle, abgeleitet aus den aktuellen
     * Settings — identische Logik wie die Engine (`ChatBotEngine.ownerLlm`):
     * eigene Owner-KI (alle 3 Felder) → Viewer-KI (Fallback) → deterministisch.
     */
    val ownerLlmSource: OwnerLlmSource
        get() {
            val settings = _uiState.value
            val ownerReady = settings.chatBotOwnerLlmBaseUrl.isNotBlank() &&
                settings.chatBotOwnerLlmApiKey.isNotBlank() &&
                settings.chatBotOwnerLlmModel.isNotBlank()
            if (ownerReady) return OwnerLlmSource.OWNER
            val viewerReady = settings.chatBotApiBaseUrl.isNotBlank() &&
                settings.chatBotApiKey.isNotBlank() &&
                settings.chatBotModel.isNotBlank()
            return if (viewerReady) OwnerLlmSource.VIEWER_FALLBACK else OwnerLlmSource.DETERMINISTIC
        }

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
        // Custom: URL leeren, damit eine beliebige RTMP(S)/SRT-Ingest-URL (z. B.
        // Owncast) eingetragen werden kann — der TLS-Toggle bleibt unangetastet.
        // Vorlagen: Ingest-URL füllen + RTMPS aktivieren (buildStreamUrl-Konvertierung).
        val updated = _uiState.value.copy(streamUrl = platform.ingestUrl)
        _uiState.value = if (platform == StreamPlatform.Custom) {
            updated
        } else {
            updated.copy(streamUseTls = true)
        }
    }
    fun onObsHostChange(newHost: String) { _uiState.value = _uiState.value.copy(obsHost = newHost) }
    fun onObsPortChange(newPort: String) { _uiState.value = _uiState.value.copy(obsPort = newPort) }
    fun onObsPasswordChange(newPassword: String) { _uiState.value = _uiState.value.copy(obsPassword = newPassword) }
    fun onObsUseTlsChange(newUseTls: Boolean) { _uiState.value = _uiState.value.copy(obsUseTls = newUseTls) }

    // Chat-Overlay-Einstellungen.
    fun onChatChannelChange(newChannel: String) { _uiState.value = _uiState.value.copy(chatChannel = newChannel) }
    fun onChatOverlayEnabledChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(chatOverlayEnabled = newEnabled) }

    // Text-/Info-Widget-Einstellungen (Overlay: Uhrzeit/GPS/Geschwindigkeit).
    fun onWidgetEnabledChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(widgetEnabled = newEnabled) }
    fun onWidgetShowTimeChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(widgetShowTime = newValue) }
    fun onWidgetShowLocationChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(widgetShowLocation = newValue) }
    fun onWidgetShowSpeedChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(widgetShowSpeed = newValue) }
    fun onWidgetShowAltitudeChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(widgetShowAltitude = newValue) }

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

    // Koexistenz mit anderen Bots (z. B. Rivulet): Ignore-Liste, Befehlsscope, Präfix.
    fun onChatBotIgnoreBotsChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotIgnoreBots = newValue) }
    fun onChatBotCommandScopeChange(newScope: ChatBotCommandScope) { _uiState.value = _uiState.value.copy(chatBotCommandScope = newScope) }
    fun onChatBotCommandPrefixChange(newPrefix: String) { _uiState.value = _uiState.value.copy(chatBotCommandPrefix = newPrefix) }

    // Owner-Zugriff (nur der Streamer): Allow-List-Logins + eigene Owner-KI.
    fun onChatBotOwnerLoginsChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotOwnerLogins = newValue) }
    fun onChatBotOwnerLlmBaseUrlChange(newUrl: String) { _uiState.value = _uiState.value.copy(chatBotOwnerLlmBaseUrl = newUrl) }
    fun onChatBotOwnerLlmApiKeyChange(newKey: String) { _uiState.value = _uiState.value.copy(chatBotOwnerLlmApiKey = newKey) }
    fun onChatBotOwnerLlmModelChange(newModel: String) { _uiState.value = _uiState.value.copy(chatBotOwnerLlmModel = newModel) }
    // Privater Antwortweg: Owner-Antworten per Twitch-Whisper statt PRIVMSG.
    fun onChatBotOwnerWhisperRepliesChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(chatBotOwnerWhisperReplies = newValue) }
    fun onChatBotTwitchClientIdChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotTwitchClientId = newValue) }

    fun onChatBotProfanityEnabledChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(chatBotProfanityEnabled = newValue) }
    fun onChatBotProfanityCategoriesChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotProfanityCategories = newValue) }
    fun onChatBotProfanityCustomWordsChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotProfanityCustomWords = newValue) }
    fun onChatBotProfanityExcludedWordsChange(newValue: String) { _uiState.value = _uiState.value.copy(chatBotProfanityExcludedWords = newValue) }

    // Third-Party-Emotes: Quellen ein-/ausschalten.
    fun onEmotesBttvChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(emotesBttvEnabled = newValue) }
    fun onEmotesFfzChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(emotesFfzEnabled = newValue) }
    fun onEmotes7tvChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(emotes7tvEnabled = newValue) }

    // Gelöschte Nachrichten: ausblenden (true) oder ausgrauen (false).
    fun onChatOverlayHideDeletedChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(chatOverlayHideDeleted = newValue) }

    // Chat-Overlay-Layout-Einstellungen.
    fun onChatOverlayWidthChange(newValue: Int) { _uiState.value = _uiState.value.copy(chatOverlayWidthDp = newValue.coerceIn(100, 400)) }
    fun onChatOverlayHeightChange(newValue: Int) { _uiState.value = _uiState.value.copy(chatOverlayHeightDp = newValue.coerceIn(100, 600)) }
    fun onChatOverlayBackgroundAlphaChange(newValue: Float) { _uiState.value = _uiState.value.copy(chatOverlayBackgroundAlpha = newValue.coerceIn(0f, 1f)) }
    fun onChatOverlayFontSizeChange(newValue: Int) { _uiState.value = _uiState.value.copy(chatOverlayFontSizeSp = newValue.coerceIn(8, 20)) }
    fun onChatOverlayShowTimestampChange(newValue: Boolean) { _uiState.value = _uiState.value.copy(chatOverlayShowTimestamp = newValue) }

    // Datenschutz: Sentry-Fehlerberichte an/aus (Opt-out).
    fun onSentryEnabledChange(newEnabled: Boolean) { _uiState.value = _uiState.value.copy(sentryEnabled = newEnabled) }

    // Darstellung (Theme): Design-Modus + Akzentfarbe.
    fun onThemeModeChange(newMode: ThemeMode) { _uiState.value = _uiState.value.copy(themeMode = newMode) }
    fun onAccentColorChange(newAccent: AccentColor) { _uiState.value = _uiState.value.copy(themeAccent = newAccent) }

    // Begrenzungen: Per-Viewer-Cooldown/-Cap und Kosten-Budget (numerisch, ungültig → 0).
    // Manuelle Änderungen markieren die Auswahl als „Eigene“ (CUSTOM), damit die
    // wiederhergestellte Voreinstellung beim nächsten Start nicht die Bearbeitung überstimmt.
    fun onChatBotPerViewerCooldownSecondsChange(raw: String) {
        _uiState.value = _uiState.value.copy(
            chatBotPerViewerCooldownSeconds = raw.toLongOrNull() ?: 0L,
            chatBotLimitPreset = ChatBotLimitPreset.CUSTOM,
        )
    }
    fun onChatBotPerViewerMaxRepliesChange(raw: String) {
        _uiState.value = _uiState.value.copy(
            chatBotPerViewerMaxReplies = raw.toIntOrNull() ?: 0,
            chatBotLimitPreset = ChatBotLimitPreset.CUSTOM,
        )
    }
    fun onChatBotMaxRepliesPerHourChange(raw: String) {
        _uiState.value = _uiState.value.copy(
            chatBotMaxRepliesPerHour = raw.toIntOrNull() ?: 0,
            chatBotLimitPreset = ChatBotLimitPreset.CUSTOM,
        )
    }

    /** Schnellstart: füllt die drei Limit-Felder aus einer Voreinstellung und speichert die Wahl. */
    fun onChatBotLimitPresetChange(preset: ChatBotLimitPreset) {
        _uiState.value = _uiState.value.copy(
            chatBotPerViewerCooldownSeconds = preset.perViewerCooldownSeconds,
            chatBotPerViewerMaxReplies = preset.perViewerMaxReplies,
            chatBotMaxRepliesPerHour = preset.maxRepliesPerHour,
            chatBotLimitPreset = preset.name,
        )
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
            settingsRepository.updateWidgetSettings(
                enabled = currentSettings.widgetEnabled,
                showTime = currentSettings.widgetShowTime,
                showLocation = currentSettings.widgetShowLocation,
                showSpeed = currentSettings.widgetShowSpeed,
                showAltitude = currentSettings.widgetShowAltitude,
                template = currentSettings.widgetTemplate,
            )
            settingsRepository.updateSentryEnabled(currentSettings.sentryEnabled)
            settingsRepository.updateThemeSettings(
                themeMode = currentSettings.themeMode,
                accentColor = currentSettings.themeAccent,
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
                ignoreBots = currentSettings.chatBotIgnoreBots,
                commandScope = currentSettings.chatBotCommandScope,
                commandPrefix = currentSettings.chatBotCommandPrefix,
                perViewerCooldownSeconds = currentSettings.chatBotPerViewerCooldownSeconds,
                perViewerMaxReplies = currentSettings.chatBotPerViewerMaxReplies,
                maxRepliesPerHour = currentSettings.chatBotMaxRepliesPerHour,
                limitPreset = currentSettings.chatBotLimitPreset,
                ownerLogins = currentSettings.chatBotOwnerLogins,
                ownerLlmBaseUrl = currentSettings.chatBotOwnerLlmBaseUrl,
                ownerLlmApiKey = currentSettings.chatBotOwnerLlmApiKey,
                ownerLlmModel = currentSettings.chatBotOwnerLlmModel,
                ownerWhisperReplies = currentSettings.chatBotOwnerWhisperReplies,
                twitchClientId = currentSettings.chatBotTwitchClientId,
            )
            settingsRepository.updateEmoteSettings(
                bttvEnabled = currentSettings.emotesBttvEnabled,
                ffzEnabled = currentSettings.emotesFfzEnabled,
                sevenTvEnabled = currentSettings.emotes7tvEnabled,
            )
            settingsRepository.updateChatOverlayHideDeleted(currentSettings.chatOverlayHideDeleted)
            settingsRepository.updateChatOverlayLayout(
                widthDp = currentSettings.chatOverlayWidthDp,
                heightDp = currentSettings.chatOverlayHeightDp,
                backgroundAlpha = currentSettings.chatOverlayBackgroundAlpha,
                fontSizeSp = currentSettings.chatOverlayFontSizeSp,
                showTimestamp = currentSettings.chatOverlayShowTimestamp,
            )
            settingsRepository.updateProfanitySettings(
                profanityEnabled = currentSettings.chatBotProfanityEnabled,
                profanityCategories = currentSettings.chatBotProfanityCategories,
                profanityCustomWords = currentSettings.chatBotProfanityCustomWords,
                profanityExcludedWords = currentSettings.chatBotProfanityExcludedWords,
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
