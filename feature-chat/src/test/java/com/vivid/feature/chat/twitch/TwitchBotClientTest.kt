package com.vivid.feature.chat.twitch

import app.cash.turbine.test
import com.vivid.feature.chat.irc.IrcMessageParser
import com.vivid.feature.chat.model.ChatConnectionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchBotClientTest {

    private val parser = IrcMessageParser()

    private fun connection(written: MutableList<String>, lines: Flow<String>): IrcConnection =
        mockk {
            coEvery { connect() } just Runs
            every { write(any()) } answers { written.add(firstArg<String>()) }
            every { incoming } returns lines
            every { close() } just Runs
        }

    private fun factory(connection: IrcConnection): IrcConnectionFactory =
        mockk {
            every { create() } returns connection
        }

    @Test
    fun `connects with oauth handshake and joins the channel`() = runTest {
        val written = mutableListOf<String>()
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, flow { awaitCancellation() })),
            parser = parser,
        )

        client.connect("Channel", "VividBot", "mytoken123")
        advanceUntilIdle()

        assertTrue(written.contains("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership"))
        assertTrue(written.contains("PASS oauth:mytoken123"))
        assertTrue(written.contains("NICK vividbot"))
        assertTrue(written.contains("JOIN #channel"))
        assertEquals(ChatConnectionState.Connected("channel"), client.state.value)

        client.disconnect()
    }

    @Test
    fun `answers ping with pong`() = runTest {
        val written = mutableListOf<String>()
        val lines = flow {
            emit("PING :tmi.twitch.tv")
            awaitCancellation()
        }
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, lines)),
            parser = parser,
        )

        client.connect("channel", "vividbot", "token")
        advanceUntilIdle()

        assertTrue(written.contains("PONG :tmi.twitch.tv"))
        client.disconnect()
    }

    @Test
    fun `sendMessage writes a privmsg to the channel`() = runTest {
        val written = mutableListOf<String>()
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, flow { awaitCancellation() })),
            parser = parser,
        )

        client.connect("channel", "vividbot", "token")
        advanceUntilIdle()
        client.sendMessage("Hallo zusammen!")
        advanceUntilIdle()

        assertTrue(written.contains("PRIVMSG #channel :Hallo zusammen!"))
        client.disconnect()
    }

    @Test
    fun `sanitizes line breaks and trims sent messages`() = runTest {
        val written = mutableListOf<String>()
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, flow { awaitCancellation() })),
            parser = parser,
        )

        client.connect("channel", "vividbot", "token")
        advanceUntilIdle()
        client.sendMessage("  zeile eins\nzeile zwei  ")
        advanceUntilIdle()

        assertTrue(written.contains("PRIVMSG #channel :zeile eins zeile zwei"))
        client.disconnect()
    }

    @Test
    fun `privmsg produces a chat message`() = runTest {
        val written = mutableListOf<String>()
        val line = "@badge-info=;badges=moderator/1;color=#FF0000;display-name=PogChamp;" +
            "id=abc123;mod=1;room-id=123;subscriber=0;tmi-sent-ts=1500000000000;turbo=0;user-id=456;" +
            "user-type=mod :pogchamp!pogchamp@pogchamp.tmi.twitch.tv PRIVMSG #channel :HeyGuys"
        val lines = flow {
            emit(line)
            awaitCancellation()
        }
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, lines)),
            parser = parser,
        )

        client.messages.test {
            client.connect("channel", "vividbot", "token")
            val message = awaitItem()
            assertEquals("abc123", message.id)
            assertEquals("pogchamp", message.userLogin)
            assertEquals("HeyGuys", message.text)
            assertEquals(false, message.isBroadcaster)
            client.disconnect()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `privmsg marks the channel owner via the broadcaster badge`() = runTest {
        val written = mutableListOf<String>()
        val line = "@badge-info=;badges=broadcaster/1;display-name=StreamerOne;id=def;mod=0;" +
            "room-id=123;subscriber=0;tmi-sent-ts=1500000000000;turbo=0;user-id=999;" +
            "user-type= :streamerone!streamerone@streamerone.tmi.twitch.tv PRIVMSG #channel :Moin"
        val lines = flow {
            emit(line)
            awaitCancellation()
        }
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, lines)),
            parser = parser,
        )

        client.messages.test {
            client.connect("channel", "vividbot", "token")
            val message = awaitItem()
            assertEquals("streamerone", message.userLogin)
            assertTrue(message.isBroadcaster)
            client.disconnect()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect stops the connection`() = runTest {
        val written = mutableListOf<String>()
        val client = TwitchBotClient(
            scope = this,
            connectionFactory = factory(connection(written, flow { awaitCancellation() })),
            parser = parser,
        )

        client.connect("channel", "vividbot", "token")
        advanceUntilIdle()
        assertTrue(written.contains("JOIN #channel"))

        client.disconnect()
        advanceUntilIdle()

        assertEquals(ChatConnectionState.Disconnected, client.state.value)
    }
}
