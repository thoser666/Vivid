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
}
