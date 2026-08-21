package com.vivid.feature.chat.bot

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.twitch.TwitchEventSubClient
import com.vivid.feature.chat.twitch.TwitchEventSubConfig
import com.vivid.feature.chat.twitch.TwitchModerationClient
import com.vivid.feature.chat.twitch.TwitchSendChatClient
import com.vivid.feature.chat.twitch.TwitchWhisperClient
import com.vivid.feature.chat.twitch.TwitchWhisperConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Verbindet den KI-Chat-Bot mit dem Stream-Lifecycle: verbindet sich beim
 * Streamstart vollautomatisch, fährt bei Streamende sauber herunter und
 * stoppt sofort, wenn der Nutzer den Bot in den Einstellungen deaktiviert.
 */
@Singleton
class ChatBotController @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val chatReader: TwitchChatEventSubReader,
    private val sendChatClient: TwitchSendChatClient,
    private val whisperClient: TwitchWhisperClient,
    private val moderationClient: TwitchModerationClient,
    private val eventSubClient: TwitchEventSubClient,
    private val engine: ChatBotEngine,
    private val chatTts: ChatTtsController,
    private val settingsRepository: SettingsRepository,
) {
    @Volatile
    private var streaming = false

    /** Zeitstempel des Stream-Starts (für den `!uptime`-Befehl). */
    @Volatile
    private var streamStartedAtMillis = 0L
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
        streamStartedAtMillis = System.currentTimeMillis()
        scope.launch { startBot(settingsRepository.appSettingsFlow.first()) }
    }

    fun onStreamStopped() {
        streaming = false
        streamStartedAtMillis = 0L
        stopBot()
    }

    private suspend fun startBot(settings: AppSettings) {
        val config = ChatBotConfig.fromSettings(settings)
        if (!config.isReady) {
            stopBot()
            return
        }
        // Kanal-Nachrichten (EventSub channel.chat.message) + private Whispers
        // (EventSub user.whisper.message) → Engine. Gesendet wird über die
        // Helix-API (POST /helix/chat/messages) statt IRC-PRIVMSG.
        val eventSubConfig = TwitchEventSubConfig(
            botLogin = config.login,
            oauthToken = config.oauthToken,
            clientId = config.twitchClientId,
            channel = config.channel,
        )
        engine.start(
            messages = merge(chatReader.messages, eventSubClient.whispers),
            config = config,
            sender = object : ChatSender {
                override suspend fun send(text: String) {
                    sendChatClient.send(eventSubConfig, text)
                }

                override suspend fun sendWhisper(toLogin: String, text: String) {
                    whisperClient.whisper(
                        TwitchWhisperConfig(
                            botLogin = config.login,
                            oauthToken = config.oauthToken,
                            clientId = config.twitchClientId,
                        ),
                        toLogin,
                        text,
                    )
                }
            },
            scope = scope,
            streamStartedAtMillis = streamStartedAtMillis,
            // Owner-Moderation (!ban/!timeout/!delete): echte Helix-Aufrufe
            // mit denselben Bot-Zugangsdaten wie das Senden.
            moderation = object : ChatModeration {
                override suspend fun ban(userLogin: String): String =
                    moderationClient.ban(eventSubConfig, userLogin)

                override suspend fun timeout(userLogin: String, durationMinutes: Int?): String =
                    moderationClient.timeout(eventSubConfig, userLogin, durationMinutes)

                override suspend fun deleteRecent(count: Int?, recentMessageIds: List<String>): String =
                    moderationClient.deleteRecent(eventSubConfig, count, recentMessageIds)
            },
        )
        // Chat-TTS (der !tts-Befehl): liest den gleichen Nachrichten-Flow vor —
        // andere Bots (Ignore-Liste) werden nicht vorgelesen.
        chatTts.start(chatReader.messages, config.login, config.ignoreBots)
        // Chat lesen (EventSub) + Whisper-Empfang: Streamer kann dem Bot
        // privat Befehle schicken.
        chatReader.start(eventSubConfig)
        eventSubClient.start(eventSubConfig)
    }

    private fun stopBot() {
        engine.stop()
        chatTts.stop()
        chatReader.stop()
        eventSubClient.stop()
    }
}
