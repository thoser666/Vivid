package com.vivid.feature.chat.twitch

import app.cash.turbine.test
import com.vivid.feature.chat.irc.IrcMessageParser
import com.vivid.feature.chat.model.ChatConnectionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchChatClientTest {

    private val parser = IrcMessageParser()

    private fun connection(written: MutableList<String>, lines: Flow<String>): IrcConnection =
        mockk {
            coEvery { connect() } just Runs
            every { write(any()) } answers { written.add(firstArg<String>()) }
            every { incoming } returns lines
            every { close() } just Runs
        }

    private fun factory(vararg connections: IrcConnection): IrcConnectionFactory =
        mockk {
            every { create() } returnsMany connections.toList()
        }

    @Test
    fun `start sends anonymous handshake and join`() = runTest {
        val written = mutableListOf<String>()
        val client = TwitchChatClient(
            scope = this,
            connectionFactory = factory(connection(written, flow { awaitCancellation() })),
            parser = parser,
        )
        client.start("channel")
        advanceUntilIdle()

        assertTrue(written.contains("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership"))
        assertTrue(written.contains("PASS SCHMOOPIIE"))
        assertTrue(written.any { it.startsWith("NICK justinfan") })
        assertTrue(written.contains("JOIN #channel"))
        assertEquals(ChatConnectionState.Connected("channel"), client.state.value)

        client.stop()
    }

    @Test
    fun `answers ping with pong`() = runTest {
        val written = mutableListOf<String>()
        val lines = flow {
            emit("PING :tmi.twitch.tv")
            awaitCancellation()
        }
        val client = TwitchChatClient(
            scope = this,
            connectionFactory = factory(connection(written, lines)),
            parser = parser,
        )
        client.start("channel")
        advanceUntilIdle()

        assertTrue(written.contains("PONG :tmi.twitch.tv"))
        client.stop()
    }

    @Test
    fun `privmsg produces chat message with parsed fields`() = runTest {
        val written = mutableListOf<String>()
        val line = "@badge-info=;badges=moderator/1;color=#FF0000;display-name=PogChamp;emotes=25:0-4;" +
            "id=abc123;mod=1;room-id=123;subscriber=0;tmi-sent-ts=1500000000000;turbo=0;user-id=456;" +
            "user-type=mod :pogchamp!pogchamp@pogchamp.tmi.twitch.tv PRIVMSG #channel :HeyGuys Kappa"
        val lines = flow {
            emit(line)
            awaitCancellation()
        }
        val client = TwitchChatClient(
            scope = this,
            connectionFactory = factory(connection(written, lines)),
            parser = parser,
        )

        client.messages.test {
            client.start("channel")
            val message = awaitItem()
            assertEquals("abc123", message.id)
            assertEquals("channel", message.channel)
            assertEquals("456", message.userId)
            assertEquals("pogchamp", message.userLogin)
            assertEquals("PogChamp", message.displayName)
            assertEquals("#FF0000", message.color)
            assertEquals("HeyGuys Kappa", message.text)
            assertEquals(listOf("moderator/1"), message.badges)
            assertEquals("25:0-4", message.emotesTag)
            assertEquals(1500000000000L, message.timestamp)
            assertTrue(message.isModerator)
            assertEquals(false, message.isSubscriber)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reconnects after the connection drops`() = runTest {
        val firstWritten = mutableListOf<String>()
        val secondWritten = mutableListOf<String>()
        val first = connection(firstWritten, flowOf("PING :tmi.twitch.tv"))
        val second = connection(secondWritten, flow { awaitCancellation() })
        val connectionFactory = factory(first, second)
        val client = TwitchChatClient(
            scope = this,
            connectionFactory = connectionFactory,
            parser = parser,
        )
        client.start("channel")
        advanceUntilIdle()

        verify(exactly = 2) { connectionFactory.create() }
        assertTrue(firstWritten.contains("PONG :tmi.twitch.tv"))
        assertTrue(secondWritten.contains("JOIN #channel"))
        assertEquals(ChatConnectionState.Connected("channel"), client.state.value)

        client.stop()
    }

    @Test
    fun `stop cancels pending reconnection`() = runTest {
        val written = mutableListOf<String>()
        val connectionFactory = factory(connection(written, flowOf()))
        val client = TwitchChatClient(
            scope = this,
            connectionFactory = connectionFactory,
            parser = parser,
        )
        client.start("channel")
        runCurrent()
        assertTrue(written.contains("JOIN #channel"))

        client.stop()
        advanceUntilIdle()

        verify(exactly = 1) { connectionFactory.create() }
        assertEquals(ChatConnectionState.Disconnected, client.state.value)
    }
}
