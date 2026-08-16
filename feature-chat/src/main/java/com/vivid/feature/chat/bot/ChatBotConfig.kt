package com.vivid.feature.chat.bot

import com.vivid.core.data.AppSettings
import com.vivid.feature.chat.ai.LlmConfig

/** Fertig aufbereitete Bot-Konfiguration aus den App-Settings. */
data class ChatBotConfig(
    val channel: String,
    val login: String,
    val oauthToken: String,
    val systemPrompt: String,
    val mentionsOnly: Boolean,
    val replyCooldownMillis: Long,
    val maxRepliesPerMinute: Int,
    val maxReplyLength: Int = 500,
    val historySize: Int = 20,
    val llm: LlmConfig,
) {
    val isReady: Boolean
        get() = channel.isNotBlank() && login.isNotBlank() && oauthToken.isNotBlank() && llm.isConfigured

    companion object {
        fun fromSettings(settings: AppSettings): ChatBotConfig =
            ChatBotConfig(
                channel = settings.chatChannel.trim().lowercase(),
                login = settings.chatBotLogin.trim().lowercase(),
                oauthToken = settings.chatBotOauthToken.trim().removePrefix("oauth:"),
                systemPrompt = settings.chatBotSystemPrompt,
                mentionsOnly = settings.chatBotMentionsOnly,
                replyCooldownMillis = settings.chatBotReplyCooldownSeconds * 1000,
                maxRepliesPerMinute = settings.chatBotMaxRepliesPerMinute,
                llm = LlmConfig(
                    baseUrl = settings.chatBotApiBaseUrl,
                    apiKey = settings.chatBotApiKey,
                    model = settings.chatBotModel,
                ),
            )
    }
}
