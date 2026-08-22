package com.vivid.irlbroadcaster

import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.StreamControl
import com.vivid.core.repository.StreamingRepository
import com.vivid.feature.chat.bot.ChatStreamControl
import com.vivid.feature.chat.bot.ChatStreamStatus
import com.vivid.feature.chat.bot.DiagnosticCheck
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
    private val streamControl: StreamControl,
    private val streamingEngine: StreamingEngine,
    private val streamingRepository: StreamingRepository,
    private val settingsRepository: SettingsRepository,
) : ChatStreamControl {

    override suspend fun start() {
        streamControl.start()
    }

    override fun stop() {
        streamControl.stop()
    }

    override fun toggleTorch(): Boolean = streamingEngine.toggleTorch()

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
        val viewerLlmReady = settings.chatBotApiBaseUrl.isNotBlank() &&
            settings.chatBotApiKey.isNotBlank() &&
            settings.chatBotModel.isNotBlank()
        val ownerLlmReady = settings.chatBotOwnerLlmBaseUrl.isNotBlank() &&
            settings.chatBotOwnerLlmApiKey.isNotBlank() &&
            settings.chatBotOwnerLlmModel.isNotBlank()
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
            // Event-Alerts (Follow/Sub/Raid) im Chat-Overlay: brauchen den Kanal
            // (Subscription-Condition), Bot-Login + Token (Auth) und die
            // Client-ID (Helix-API). Die Scopes lassen sich aus den Settings
            // nicht verifizieren (Twitch gibt keine Scope-Liste im Token
            // zurück) — der Bot muss Moderator sein (moderator:read:followers)
            // und der Token channel:read:subscriptions besitzen; fehlende
            // Rechte lassen nur den jeweiligen Alert-Typ ausfallen (best-effort,
            // Chat läuft weiter).
            DiagnosticCheck(
                "Event-Alerts konfiguriert",
                settings.chatChannel.isNotBlank() &&
                    settings.chatBotLogin.isNotBlank() &&
                    settings.chatBotOauthToken.isNotBlank() &&
                    settings.chatBotTwitchClientId.isNotBlank(),
                when {
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
                },
            ),
            // Privater Antwortweg (!diag/!ask-Antworten an den Owner): braucht
            // Client-ID + Bot-Token, wenn der Toggle an ist. Toggle aus = bewusst
            // öffentliche Antworten → kein offener Punkt.
            DiagnosticCheck(
                "Whisper (privater Antwortweg)",
                !settings.chatBotOwnerWhisperReplies ||
                    (settings.chatBotTwitchClientId.isNotBlank() && settings.chatBotOauthToken.isNotBlank()),
                when {
                    !settings.chatBotOwnerWhisperReplies -> "deaktiviert (Toggle aus) → öffentlich"
                    settings.chatBotTwitchClientId.isBlank() && settings.chatBotOauthToken.isBlank() ->
                        "Client-ID + Bot-Token fehlen"
                    settings.chatBotTwitchClientId.isBlank() -> "Twitch-App-Client-ID fehlt"
                    settings.chatBotOauthToken.isBlank() -> "Bot-Token fehlt"
                    else -> "Client-ID + Token gesetzt (Scope user:manage:whispers nötig)"
                },
            ),
            DiagnosticCheck("Viewer-LLM (Endpunkt/Key/Modell)", viewerLlmReady),
            DiagnosticCheck("Owner-LLM (Endpunkt/Key/Modell)", ownerLlmReady),
            // KI-Quelle der Owner-Befehle (!diag/!ask): eigene Owner-KI oder
            // Viewer-KI als Fallback — offen nur, wenn gar keine KI
            // konfiguriert ist (dann antworten die Befehle deterministisch).
            DiagnosticCheck(
                "Owner-KI-Quelle",
                ownerLlmReady || viewerLlmReady,
                when {
                    ownerLlmReady -> "eigene Owner-KI (exklusiv)"
                    viewerLlmReady -> "Viewer-KI (Fallback)"
                    else -> "keine KI konfiguriert → deterministisch (Checkliste/Hinweis)"
                },
            ),
        )
        return StreamDiagnostics(status = status, obsConnected = obsConnected, checks = checks)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatStreamControlModule {

    /** Konkrete Bindung gewinnt über die @BindsOptionalOf-Deklaration in feature-chat. */
    @Binds
    @Singleton
    abstract fun bindChatStreamControl(
        impl: AppChatStreamControl,
    ): ChatStreamControl
}
