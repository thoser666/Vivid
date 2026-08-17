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
            DiagnosticCheck(
                "Viewer-LLM (Endpunkt/Key/Modell)",
                settings.chatBotApiBaseUrl.isNotBlank() &&
                    settings.chatBotApiKey.isNotBlank() &&
                    settings.chatBotModel.isNotBlank(),
            ),
            DiagnosticCheck(
                "Owner-LLM (Endpunkt/Key/Modell)",
                settings.chatBotOwnerLlmBaseUrl.isNotBlank() &&
                    settings.chatBotOwnerLlmApiKey.isNotBlank() &&
                    settings.chatBotOwnerLlmModel.isNotBlank(),
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
