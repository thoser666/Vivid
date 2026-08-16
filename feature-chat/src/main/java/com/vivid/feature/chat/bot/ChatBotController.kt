package com.vivid.feature.chat.bot

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.twitch.TwitchBotClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Verbindet den KI-Chat-Bot mit dem Stream-Lifecycle: verbindet sich beim
 * Streamstart vollautomatisch, fährt bei Streamende sauber herunter und
 * stoppt sofort, wenn der Nutzer den Bot in den Einstellungen deaktiviert.
 */
@Singleton
class ChatBotController @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val botClient: TwitchBotClient,
    private val engine: ChatBotEngine,
    private val settingsRepository: SettingsRepository,
) {
    @Volatile
    private var streaming = false
    private var settingsJob: Job? = null

    init {
        // Settings-Änderungen live übernehmen: Deaktivieren stoppt sofort,
        // Ändern von Kanal/Token/LLM verbindet mit der neuen Konfiguration neu.
        settingsJob = scope.launch {
            settingsRepository.appSettingsFlow.collectLatest { settings ->
                if (streaming) {
                    if (settings.chatBotEnabled) startBot(settings) else stopBot()
                }
            }
        }
    }

    fun onStreamStarted() {
        if (streaming) return
        streaming = true
        scope.launch { startBot(settingsRepository.appSettingsFlow.first()) }
    }

    fun onStreamStopped() {
        streaming = false
        stopBot()
    }

    private suspend fun startBot(settings: AppSettings) {
        val config = ChatBotConfig.fromSettings(settings)
        if (!config.isReady) {
            stopBot()
            return
        }
        engine.start(botClient.messages, config, { text -> botClient.sendMessage(text) }, scope)
        botClient.connect(config.channel, config.login, config.oauthToken)
    }

    private fun stopBot() {
        engine.stop()
        botClient.disconnect()
    }
}
