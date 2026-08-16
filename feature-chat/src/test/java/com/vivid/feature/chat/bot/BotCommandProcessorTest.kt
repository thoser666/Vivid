package com.vivid.feature.chat.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BotCommandProcessorTest {

    private fun processor(nowMillis: Long = 1_000_000L) = BotCommandProcessor().apply { now = { nowMillis } }

    @Test
    fun `help lists the available commands`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            processor().handle("!help", null),
        )
    }

    @Test
    fun `commands is an alias for help`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            processor().handle("!commands", null),
        )
    }

    @Test
    fun `commands are case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            processor().handle("!HELP", null),
        )
    }

    @Test
    fun `command can appear in the middle of a message`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            processor().handle("@vividbot !help bitte", null),
        )
    }

    @Test
    fun `uptime formats the running time`() {
        // now() = 10_000_000, Start vor 1h 1m 1s (= 3_661_000 ms).
        val startedAt = 10_000_000L - 3_661_000L
        assertEquals(
            BotCommandProcessor.Result.Reply("Der Stream läuft seit 1h 1m 1s."),
            processor(nowMillis = 10_000_000L).handle("!uptime", startedAt),
        )
    }

    @Test
    fun `uptime reports when no stream is running`() {
        assertEquals(
            BotCommandProcessor.Result.Reply("Gerade läuft kein Stream."),
            processor().handle("!uptime", null),
        )
    }

    @Test
    fun `uptime reports when the stream start time is zero`() {
        assertEquals(
            BotCommandProcessor.Result.Reply("Gerade läuft kein Stream."),
            processor().handle("!uptime", 0L),
        )
    }

    @Test
    fun `bot returns the bot info`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.BOT_INFO_TEXT),
            processor().handle("!bot", null),
        )
    }

    @Test
    fun `unknown commands are reported as unknown`() {
        assertEquals(
            BotCommandProcessor.Result.Unknown("xyz"),
            processor().handle("!xyz", null),
        )
    }

    @Test
    fun `messages without an exclamation token are none`() {
        assertEquals(
            BotCommandProcessor.Result.None,
            processor().handle("hallo zusammen", null),
        )
    }

    @Test
    fun `a lone exclamation mark is treated as no command`() {
        assertEquals(
            BotCommandProcessor.Result.None,
            processor().handle("schau mal !", null),
        )
    }

    @Test
    fun `first exclamation token wins`() {
        // Nur das erste !-Token wird interpretiert — deterministisch und spamsicher.
        assertEquals(
            BotCommandProcessor.Result.Unknown("song"),
            processor().handle("!song !help", null),
        )
    }
}
