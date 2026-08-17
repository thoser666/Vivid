package com.vivid.feature.chat.bot

import com.vivid.core.data.AppSettings
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatBotConfigTest {

    @Test
    fun `fromSettings maps the bot basics and limits`() {
        val config = ChatBotConfig.fromSettings(
            AppSettings(
                chatChannel = "  MeinKanal ",
                chatBotLogin = " VividBot ",
                chatBotOauthToken = "oauth:tok123",
                chatBotMode = ChatBotMode.COMMAND,
                chatBotReplyCooldownSeconds = 5,
                chatBotPerViewerCooldownSeconds = 90,
                chatBotPerViewerMaxReplies = 4,
                chatBotMaxRepliesPerHour = 50,
                chatBotMentionsOnly = false,
                chatBotMaxRepliesPerMinute = 20,
            ),
        )

        // Normalisierung (trim + lowercase + oauth:-Präfix entfernen).
        assertEquals("meinkanal", config.channel)
        assertEquals("vividbot", config.login)
        assertEquals("tok123", config.oauthToken)
        // Limits: Sekunden → Millis, Caps direkt.
        assertEquals(5_000L, config.replyCooldownMillis)
        assertEquals(90_000L, config.perViewerCooldownMillis)
        assertEquals(4, config.perViewerMaxReplies)
        assertEquals(50, config.maxRepliesPerHour)
    }

    @Test
    fun `fromSettings normalizes the ignore list and maps scope and prefix`() {
        val config = ChatBotConfig.fromSettings(
            AppSettings(
                chatBotIgnoreBots = " @RivuletBot , otherbot,  ",
                chatBotCommandScope = ChatBotCommandScope.PREFIX,
                chatBotCommandPrefix = "  v  ",
            ),
        )

        assertEquals(setOf("rivuletbot", "otherbot"), config.ignoreBots)
        assertEquals(ChatBotCommandScope.PREFIX, config.commandScope)
        assertEquals("v", config.commandPrefix)
    }

    @Test
    fun `command mode is ready without an llm key`() {
        val config = ChatBotConfig.fromSettings(
            AppSettings(
                chatChannel = "kanal",
                chatBotLogin = "vividbot",
                chatBotOauthToken = "token",
                chatBotMode = ChatBotMode.COMMAND,
                chatBotApiKey = "",
            ),
        )

        assertTrue(config.isReady)
    }

    @Test
    fun `autonomous mode needs a configured llm`() {
        val withKey = ChatBotConfig.fromSettings(
            AppSettings(
                chatChannel = "kanal",
                chatBotLogin = "vividbot",
                chatBotOauthToken = "token",
                chatBotMode = ChatBotMode.AUTONOMOUS,
                chatBotApiKey = "sk-123",
            ),
        )
        val withoutKey = ChatBotConfig.fromSettings(
            AppSettings(
                chatChannel = "kanal",
                chatBotLogin = "vividbot",
                chatBotOauthToken = "token",
                chatBotMode = ChatBotMode.AUTONOMOUS,
                chatBotApiKey = "",
            ),
        )

        assertTrue(withKey.isReady)
        assertFalse(withoutKey.isReady)
    }

    @Test
    fun `isReady requires channel login and token`() {
        val config = ChatBotConfig.fromSettings(AppSettings(chatBotMode = ChatBotMode.COMMAND))

        assertFalse(config.isReady)
    }

    // --- Owner-Zugriff (nur der Streamer) ---

    @Test
    fun `fromSettings normalizes the owner logins and maps the owner llm`() {
        val config = ChatBotConfig.fromSettings(
            AppSettings(
                chatBotOwnerLogins = " @Streamer2 , zweitkonto,  ",
                chatBotOwnerLlmBaseUrl = "https://owner.example",
                chatBotOwnerLlmApiKey = "owner-key",
                chatBotOwnerLlmModel = "claude-4",
            ),
        )

        assertEquals(setOf("streamer2", "zweitkonto"), config.ownerLogins)
        assertEquals("https://owner.example", config.ownerLlm.baseUrl)
        assertEquals("owner-key", config.ownerLlm.apiKey)
        assertEquals("claude-4", config.ownerLlm.model)
        assertTrue(config.isOwnerLlmReady)
        // Privater Antwortweg: Standard an, Client-ID leer.
        assertEquals(true, config.ownerWhisperReplies)
        assertEquals("", config.twitchClientId)
    }

    @Test
    fun `fromSettings maps the private whisper path settings`() {
        val config = ChatBotConfig.fromSettings(
            AppSettings(
                chatBotOwnerWhisperReplies = false,
                chatBotTwitchClientId = " client-abc ",
            ),
        )

        assertFalse(config.ownerWhisperReplies)
        assertEquals("client-abc", config.twitchClientId)
    }

    @Test
    fun `owner llm is not ready when not configured`() {
        val config = ChatBotConfig.fromSettings(AppSettings())

        assertFalse(config.isOwnerLlmReady)
    }

    @Test
    fun `isOwner accepts the broadcaster and allow-listed logins`() {
        val config = ChatBotConfig.fromSettings(AppSettings(chatBotOwnerLogins = "streamer2"))

        // Broadcaster-Badge ist immer Owner.
        assertTrue(config.isOwner("streamer1", isBroadcaster = true))
        // Allow-List (case-insensitiv, mit/ohne '@').
        assertTrue(config.isOwner("STREAMER2", isBroadcaster = false))
        assertTrue(config.isOwner("@streamer2", isBroadcaster = false))
        // Jeder andere ist kein Owner.
        assertFalse(config.isOwner("viewer1", isBroadcaster = false))
    }
}
