package com.vivid.feature.chat.session

import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.twitch.EventSubSocketFactory
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.twitch.TwitchEventSubConfig
import com.vivid.feature.chat.twitch.TwitchWhisperClient
import io.mockk.mockk
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * P0-Verträge der Multi-Plattform-Skizze (docs/architecture/multi-platform-chat.md):
 * plattformneutrales Modell (Default TWITCH = Verhaltensneutralität),
 * ChatSendResult-Mapping, typsichere Twitch-Config und der Interface-Dispatch
 * des Twitch-Readers. Flows/Reconnect/Alerts frieren die Bestands-Tests ein
 * (Reader-/Engine-/VM-Suiten); hier nur die neuen Verträge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionP0Test {

    @Test
    fun `chat message defaults to twitch platform`() {
        val message = ChatMessage(
            id = "m1",
            channel = "kanal",
            userId = "u1",
            userLogin = "user1",
            displayName = "User1",
            color = "#ff0000",
            text = "hi",
            badges = emptyList(),
            emotesTag = "",
            timestamp = 0L,
            isModerator = false,
            isSubscriber = false,
        )
        assertSame(ChatPlatform.TWITCH, message.platform)
    }

    @Test
    fun `platform enum ids are stable wire values`() {
        assertEquals("twitch", ChatPlatform.TWITCH.id)
        assertEquals("youtube", ChatPlatform.YOUTUBE.id)
        assertEquals("kick", ChatPlatform.KICK.id)
    }

    @Test
    fun `send result maps helix fields`() {
        assertEquals(ChatSendResult.Sent, ChatSendResult.from(isSent = true, dropReason = null))
        assertEquals(
            ChatSendResult.Dropped("Slow-Mode"),
            ChatSendResult.from(isSent = false, dropReason = "Slow-Mode"),
        )
        assertEquals(
            ChatSendResult.Failed("401 Scope fehlt"),
            ChatSendResult.from(isSent = false, dropReason = null, error = "401 Scope fehlt"),
        )
    }

    @Test
    fun `twitch session config derives platform and channel`() {
        val twitch = TwitchEventSubConfig(
            botLogin = "vividbot",
            oauthToken = "tok",
            clientId = "cid",
            channel = "Kanal",
        )
        val config = ChatSessionConfig.Twitch(twitch)
        assertSame(ChatPlatform.TWITCH, config.platform)
        assertEquals("Kanal", config.channel)
        assertEquals(twitch, config.twitch)
    }

    @Test
    fun `session config is sealed to twitch only in p0`() {
        // Sealed-Exhaustivität: der Adapter-Dispatch (`as? Twitch ?: throw`)
        // ist in P0 totsicher — keine Fremd-Variante kann von außen entstehen
        // (anonyme/externe Unterklassen scheitern an der Sealed-Grenze).
        assertEquals(listOf("Twitch"), ChatSessionConfig::class.sealedSubclasses.map { it.simpleName })
    }

    @Test
    fun `twitch reader dispatches interface start to the legacy overload`() = runTest {
        // Nicht konfigurierte Twitch-Config: der Interface-Dispatch delegiert
        // an die Bestands-Überladung, die (Verhalten unverändert) still
        // abbricht — kein Socket, kein Crash, Zustand bleibt Disconnected.
        val reader = TwitchChatEventSubReader(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            socketFactory = mockk<EventSubSocketFactory>(relaxed = true),
            whisperClient = mockk<TwitchWhisperClient>(relaxed = true),
            http = mockk<HttpClient>(relaxed = true),
        )
        val unconfigured = TwitchEventSubConfig(
            botLogin = "",
            oauthToken = "",
            clientId = "",
            channel = "kanal",
        )
        reader.start(ChatSessionConfig.Twitch(unconfigured))
        assertEquals(ChatConnectionStateDisconnected, reader.state.value)
        reader.stop()
    }

    private companion object {
        val ChatConnectionStateDisconnected =
            com.vivid.feature.chat.model.ChatConnectionState.Disconnected
    }
}
