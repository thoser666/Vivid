package com.vivid.irlbroadcaster

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.log.LogStore
import com.vivid.core.remote.StreamControl
import com.vivid.core.repository.StreamingRepository
import com.vivid.feature.chat.bot.ChatStreamControl
import com.vivid.feature.chat.bot.ChatStreamStatus
import com.vivid.feature.chat.bot.DiagnosticCheck
import com.vivid.feature.chat.bot.FixAction
import com.vivid.feature.chat.bot.StreamDiagnostics
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-seitige Implementierung der Owner-Steuerung ([ChatStreamControl]):
 * verbindet den Chat-Bot mit der echten Streaming-Engine. Start/Stopp
 * delegieren an die vorhandene [StreamControl]-Implementierung (liest die
 * gespeicherten Stream-Einstellungen und startet den Engine-Stream);
 * die Diagnose sammelt deterministisch Stream-Status (inkl. Fehlerursache),
 * OBS-Verbindung und Konfigurations-Checks für die Owner-Befehle
 * `!start` / `!stop` / `!diag` / `!ask`.
 */
@Singleton
class AppChatStreamControl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val streamControl: StreamControl,
    private val streamingEngine: StreamingEngine,
    private val streamingRepository: StreamingRepository,
    private val settingsRepository: SettingsRepository,
    private val logStore: LogStore,
) : ChatStreamControl {

    override suspend fun start() {
        streamControl.start()
    }

    override fun stop() {
        streamControl.stop()
    }

    override fun toggleTorch(): Boolean = streamingEngine.toggleTorch()

    override fun setVideoFilter(filterName: String?): List<String> {
        val filters = com.vivid.feature.streaming.VideoFilter.entries
        if (filterName.isNullOrBlank()) {
            streamingEngine.nextVideoFilter()
        } else {
            val match = filters.find { it.name.equals(filterName, ignoreCase = true) }
            if (match != null) {
                streamingEngine.setVideoFilter(match)
            }
        }
        return filters.map { it.name }
    }

    override fun toggleLowLightBoost(): Boolean = streamingEngine.toggleLowLightBoost()

    override fun setLutPreset(presetIndex: Int): Boolean {
        val preset = when (presetIndex) {
            1 -> com.vivid.feature.streaming.LutPreset.WARM
            2 -> com.vivid.feature.streaming.LutPreset.COOL
            else -> com.vivid.feature.streaming.LutPreset.NONE
        }
        return streamingEngine.setLutPreset(preset)
    }

    override fun setColorSpace(spaceIndex: Int): Boolean {
        val space = when (spaceIndex) {
            1 -> com.vivid.feature.streaming.ColorSpace.DISPLAY_P3
            2 -> com.vivid.feature.streaming.ColorSpace.APPLE_LOG
            else -> com.vivid.feature.streaming.ColorSpace.SRGB
        }
        return streamingEngine.setColorSpace(space)
    }

    override fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    override suspend fun fix(): List<FixAction> {
        val actions = mutableListOf<FixAction>()
        val engineState = streamingEngine.streamingState.value

        // Stream läuft nicht, aber Settings sind vorhanden → Neustart
        if (engineState is StreamingState.Idle) {
            val settings = settingsRepository.appSettingsFlow.first()
            if (settings.streamUrl.isNotBlank() && settings.streamKey.isNotBlank()) {
                try {
                    streamControl.start()
                    actions.add(FixAction("Stream-Neustart", true, "Stream wird (neu) gestartet"))
                } catch (e: Exception) {
                    actions.add(FixAction("Stream-Neustart", false, e.message ?: "Unbekannter Fehler"))
                }
            }
        }

        // OBS-Status prüfen (kein auto-Reconnect möglich, nur Hinweis)
        val obsConnected = streamingRepository.isConnectedToObs.value
        if (!obsConnected) {
            actions.add(FixAction(
                "OBS-Verbindung",
                false,
                "OBS nicht verbunden — manueller Reconnect nötig (Settings → OBS-Remote)",
            ))
        }

        return actions
    }

    override suspend fun diagnostics(): StreamDiagnostics {
        val engineState = streamingEngine.streamingState.value
        val status = when (engineState) {
            is StreamingState.Idle -> ChatStreamStatus.Idle
            is StreamingState.Preparing -> ChatStreamStatus.Preparing
            is StreamingState.Streaming -> ChatStreamStatus.Streaming
            is StreamingState.Failed -> ChatStreamStatus.Failed(engineState.reason)
        }
        val settings = settingsRepository.appSettingsFlow.first()
        val obsConnected = streamingRepository.isConnectedToObs.value
        val checks = listOf(
            DiagnosticCheck("Stream-URL (primär)", settings.streamUrl.isNotBlank()),
            DiagnosticCheck("Stream-Key (primär)", settings.streamKey.isNotBlank()),
            DiagnosticCheck(
                "Multi-Streaming (zweites Ziel)",
                settings.secondaryStreamUrl.isBlank() ||
                    (settings.secondaryStreamUrl.isNotBlank() && settings.secondaryStreamKey.isNotBlank()),
            ),
            DiagnosticCheck("OBS verbunden", obsConnected),
            DiagnosticCheck("Twitch-Chat-Kanal", settings.chatChannel.isNotBlank()),
            DiagnosticCheck(
                "Bot-Login + OAuth-Token",
                settings.chatBotLogin.isNotBlank() && settings.chatBotOauthToken.isNotBlank(),
            ),
            alertsCheck(settings),
            whisperCheck(settings),
            DiagnosticCheck("Viewer-LLM (Endpunkt/Key/Modell)", viewerLlmReady(settings)),
            DiagnosticCheck("Owner-LLM (Endpunkt/Key/Modell)", ownerLlmReady(settings)),
            ownerKiSourceCheck(settings),
            crashSummaryCheck(settings),
        )
        return StreamDiagnostics(status = status, obsConnected = obsConnected, checks = checks)
    }

    // ── Diagnose-Hilfsfunktionen (halten die Methode [diagnostics] klein) ──

    private fun viewerLlmReady(settings: AppSettings): Boolean =
        settings.chatBotApiBaseUrl.isNotBlank() &&
            settings.chatBotApiKey.isNotBlank() &&
            settings.chatBotModel.isNotBlank()

    private fun ownerLlmReady(settings: AppSettings): Boolean =
        settings.chatBotOwnerLlmBaseUrl.isNotBlank() &&
            settings.chatBotOwnerLlmApiKey.isNotBlank() &&
            settings.chatBotOwnerLlmModel.isNotBlank()

    /**
     * Event-Alerts (Follow/Sub/Raid) im Chat-Overlay: brauchen den Kanal
     * (Subscription-Condition), Bot-Login + Token (Auth) und die
     * Client-ID (Helix-API). Die Scopes lassen sich aus den Settings
     * nicht verifizieren (Twitch gibt keine Scope-Liste im Token
     * zurück) — der Bot muss Moderator sein (moderator:read:followers)
     * und der Token channel:read:subscriptions besitzen; fehlende
     * Rechte lassen nur den jeweiligen Alert-Typ ausfallen (best-effort,
     * Chat läuft weiter).
     */
    private fun alertsCheck(settings: AppSettings): DiagnosticCheck {
        val ok = settings.chatChannel.isNotBlank() &&
            settings.chatBotLogin.isNotBlank() &&
            settings.chatBotOauthToken.isNotBlank() &&
            settings.chatBotTwitchClientId.isNotBlank()
        val detail = when {
            settings.chatChannel.isBlank() && settings.chatBotLogin.isBlank() &&
                settings.chatBotOauthToken.isBlank() && settings.chatBotTwitchClientId.isBlank() ->
                "Kanal, Bot-Login, Bot-Token und Client-ID fehlen"
            settings.chatChannel.isBlank() -> "Chat-Kanal fehlt"
            settings.chatBotLogin.isBlank() -> "Bot-Login fehlt"
            settings.chatBotOauthToken.isBlank() -> "Bot-Token fehlt"
            settings.chatBotTwitchClientId.isBlank() -> "Twitch-App-Client-ID fehlt"
            else ->
                "Kanal/Bot/Client-ID gesetzt — Bot muss Moderator sein (Scope " +
                    "moderator:read:followers) und der Token channel:read:subscriptions " +
                    "besitzen (für Sub-Alerts)"
        }
        return DiagnosticCheck("Event-Alerts konfiguriert", ok, detail)
    }

    /**
     * Privater Antwortweg (!diag/!ask-Antworten an den Owner): braucht
     * Client-ID + Bot-Token, wenn der Toggle an ist. Toggle aus = bewusst
     * öffentliche Antworten → kein offener Punkt.
     */
    private fun whisperCheck(settings: AppSettings): DiagnosticCheck {
        val ok = !settings.chatBotOwnerWhisperReplies ||
            (settings.chatBotTwitchClientId.isNotBlank() && settings.chatBotOauthToken.isNotBlank())
        val detail = when {
            !settings.chatBotOwnerWhisperReplies -> "deaktiviert (Toggle aus) → öffentlich"
            settings.chatBotTwitchClientId.isBlank() && settings.chatBotOauthToken.isBlank() ->
                "Client-ID + Bot-Token fehlen"
            settings.chatBotTwitchClientId.isBlank() -> "Twitch-App-Client-ID fehlt"
            settings.chatBotOauthToken.isBlank() -> "Bot-Token fehlt"
            else -> "Client-ID + Token gesetzt (Scope user:manage:whispers nötig)"
        }
        return DiagnosticCheck("Whisper (privater Antwortweg)", ok, detail)
    }

    /**
     * KI-Quelle der Owner-Befehle (!diag/!ask): eigene Owner-KI oder
     * Viewer-KI als Fallback — offen nur, wenn gar keine KI
     * konfiguriert ist (dann antworten die Befehle deterministisch).
     */
    private fun ownerKiSourceCheck(settings: AppSettings): DiagnosticCheck {
        val ownerReady = ownerLlmReady(settings)
        val viewerReady = viewerLlmReady(settings)
        val detail = when {
            ownerReady -> "eigene Owner-KI (exklusiv)"
            viewerReady -> "Viewer-KI (Fallback)"
            else -> "keine KI konfiguriert → deterministisch (Checkliste/Hinweis)"
        }
        return DiagnosticCheck("Owner-KI-Quelle", ownerReady || viewerReady, detail)
    }

    /**
     * Crash-Zusammenfassung aus dem tagesbasierten LogStore (gleiche
     * Vorhaltezeit wie der Log-Screen): ok = keine markierten Abstürze
     * im Fenster; sonst zählt die Diagnose die 💥-Einträge und verweist
     * auf den Log-Screen zur Auswertung.
     */
    private fun crashSummaryCheck(settings: AppSettings): DiagnosticCheck {
        // Gleiche Vorhaltezeit wie der Log-Screen (1–30 Tage, Default 7);
        // bewusst lokal geklemmt statt Import aus dem Settings-UI.
        val retentionDays = settings.logsRetentionDays.coerceIn(1, 30)
        val crashCount = logStore.load(retentionDays).count { it.isCrash }
        val detail = if (crashCount == 0) {
            "keine Crashes in den letzten $retentionDays Tagen"
        } else {
            "$crashCount Crash/Crashes in den letzten $retentionDays Tagen — " +
                "Auswertung im Log-Screen (Logs & Diagnose)"
        }
        return DiagnosticCheck("Crash-Zusammenfassung", crashCount == 0, detail)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ChatStreamControlModule {

    /** Konkrete Bindung gewinnt über die @BindsOptionalOf-Deklaration in feature-chat. */
    @Binds
    @Singleton
    fun bindChatStreamControl(
        impl: AppChatStreamControl,
    ): ChatStreamControl
}