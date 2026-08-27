package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotCommandScope
import com.vivid.feature.chat.model.ChatAlertType
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
    fun `tts returns the toggle result`() {
        assertEquals(
            BotCommandProcessor.Result.ToggleTts,
            processor().handle("!tts", null),
        )
    }

    @Test
    fun `tts is case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.ToggleTts,
            processor().handle("!TTS", null),
        )
    }

    @Test
    fun `tts works inside a message`() {
        assertEquals(
            BotCommandProcessor.Result.ToggleTts,
            processor().handle("@vividbot bitte !tts", null),
        )
    }

    @Test
    fun `song returns the now-playing result`() {
        assertEquals(
            BotCommandProcessor.Result.MediaNowPlaying,
            processor().handle("!song", null),
        )
    }

    @Test
    fun `nowplaying and np are aliases for song`() {
        assertEquals(
            BotCommandProcessor.Result.MediaNowPlaying,
            processor().handle("!nowplaying", null),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaNowPlaying,
            processor().handle("!np", null),
        )
    }

    @Test
    fun `next and skip advance to the next track`() {
        assertEquals(
            BotCommandProcessor.Result.MediaNext,
            processor().handle("!next", null),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaNext,
            processor().handle("!skip", null),
        )
    }

    @Test
    fun `pause and play control playback`() {
        assertEquals(
            BotCommandProcessor.Result.MediaPause,
            processor().handle("!pause", null),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaPlay,
            processor().handle("!play", null),
        )
    }

    @Test
    fun `prev and previous go back to the previous track`() {
        assertEquals(
            BotCommandProcessor.Result.MediaPrevious,
            processor().handle("!prev", null),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaPrevious,
            processor().handle("!previous", null),
        )
    }

    @Test
    fun `media commands are case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.MediaPause,
            processor().handle("!PAUSE", null),
        )
    }

    // --- Owner-Befehle (!start / !stop / !diag / !ask) ---

    @Test
    fun `start and go-live return the owner start result`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerStart,
            processor().handle("!start", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerStart,
            processor().handle("!go-live", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerStart,
            processor().handle("!GO_LIVE", null),
        )
    }

    @Test
    fun `stop and end return the owner stop result`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerStop,
            processor().handle("!stop", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerStop,
            processor().handle("!end", null),
        )
    }

    @Test
    fun `diag and status return the owner diagnose result`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerDiagnose,
            processor().handle("!diag", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerDiagnose,
            processor().handle("!status", null),
        )
    }

    @Test
    fun `ask carries the question text`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerAsk("stelle die Verbindung zu Twitch her"),
            processor().handle("!ask stelle die Verbindung zu Twitch her", null),
        )
    }

    @Test
    fun `ask without a question carries an empty text`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerAsk(""),
            processor().handle("!ask", null),
        )
    }

    @Test
    fun `fix returns owner fix result`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerFix,
            processor().handle("!fix", null),
        )
    }

    @Test
    fun `owner commands work inside a message and with the prefix scope`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerStart,
            processor().handle("@vividbot !start bitte", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerStop,
            processor().handle("!v!stop", null, ChatBotCommandScope.PREFIX, "v"),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerAsk("was ist los?"),
            processor().handle("!v!ask was ist los?", null, ChatBotCommandScope.PREFIX, "v"),
        )
    }

    // --- !torch: Taschenlampe umschalten (Owner-only, Gate in der Engine) ---

    @Test
    fun `torch maps to OwnerTorch`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerTorch,
            processor().handle("!torch", null),
        )
    }

    @Test
    fun `torch aliases lantern and flashlight`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerTorch,
            processor().handle("!lantern", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerTorch,
            processor().handle("!flashlight", null),
        )
    }

    @Test
    fun `torch is case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerTorch,
            processor().handle("!TORCH", null),
        )
    }

    @Test
    fun `torch works with prefix scope`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerTorch,
            processor().handle("!v!torch", null, ChatBotCommandScope.PREFIX, "v"),
        )
    }

    // --- Moderation (!ban / !timeout / !delete — Owner-only, Gate in der Engine) ---

    @Test
    fun `ban carries the target user`() {
        assertEquals(
            BotCommandProcessor.Result.Ban("troll1"),
            processor().handle("!ban troll1", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Ban("troll1"),
            processor().handle("!ban @troll1", null),
        )
    }

    @Test
    fun `ban without a user carries an empty login`() {
        assertEquals(
            BotCommandProcessor.Result.Ban(""),
            processor().handle("!ban", null),
        )
    }

    @Test
    fun `timeout carries the user and an optional duration`() {
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", null),
            processor().handle("!timeout mod1", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", 10),
            processor().handle("!timeout mod1 10", null),
        )
    }

    @Test
    fun `timeout accepts duration suffixes case-insensitively`() {
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", 10),
            processor().handle("!timeout mod1 10min", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", 10),
            processor().handle("!timeout mod1 10Minute", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", 10),
            processor().handle("!timeout mod1 10m", null),
        )
    }

    @Test
    fun `timeout rejects a non-numeric or non-positive duration`() {
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", null),
            processor().handle("!timeout mod1 später", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", null),
            processor().handle("!timeout mod1 0", null),
        )
    }

    @Test
    fun `delete carries an optional count`() {
        assertEquals(
            BotCommandProcessor.Result.Delete(null),
            processor().handle("!delete", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Delete(5),
            processor().handle("!delete 5", null),
        )
    }

    @Test
    fun `moderation commands work inside a message and with the prefix scope`() {
        assertEquals(
            BotCommandProcessor.Result.Ban("troll1"),
            processor().handle("@vividbot !ban troll1 bitte", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Timeout("mod1", 10),
            processor().handle("!v!timeout mod1 10", null, ChatBotCommandScope.PREFIX, "v"),
        )
        assertEquals(
            BotCommandProcessor.Result.Delete(3),
            processor().handle("!v!delete 3", null, ChatBotCommandScope.PREFIX, "v"),
        )
    }

    // --- Test-Alert (!testalert — Owner-only, Gate in der Engine) ---

    @Test
    fun `testalert parses the alert type`() {
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.FOLLOW),
            processor().handle("!testalert follow", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.SUBSCRIBE),
            processor().handle("!testalert sub", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.RAID),
            processor().handle("!testalert raid", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.GIFT_SUB),
            processor().handle("!testalert gift", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.RESUB),
            processor().handle("!testalert resub", null),
        )
    }

    @Test
    fun `testalert accepts aliases and is case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.FOLLOW),
            processor().handle("!testalert Follower", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.SUBSCRIBE),
            processor().handle("!test-alert subscribe", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.RAID),
            processor().handle("!alert RAID", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.GIFT_SUB),
            processor().handle("!testalert giftsub", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.GIFT_SUB),
            processor().handle("!test-alert Gift-Sub", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.RESUB),
            processor().handle("!alert resubscribe", null),
        )
    }

    @Test
    fun `testalert without a valid type carries null`() {
        assertEquals(
            BotCommandProcessor.Result.TestAlert(null),
            processor().handle("!testalert", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(null),
            processor().handle("!testalert quatsch", null),
        )
    }

    @Test
    fun `testalert works inside a message and with the prefix scope`() {
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.FOLLOW),
            processor().handle("@vividbot !testalert follow bitte", null),
        )
        assertEquals(
            BotCommandProcessor.Result.TestAlert(ChatAlertType.RAID),
            processor().handle("!v!testalert raid", null, ChatBotCommandScope.PREFIX, "v"),
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
            BotCommandProcessor.Result.Unknown("xyz"),
            processor().handle("!xyz !help", null),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaNext,
            processor().handle("!next !song", null),
        )
    }

    // --- Koexistenz: Befehlsscope (ALL / MENTION / PREFIX) ---

    @Test
    fun `mention scope ignores commands without an address`() {
        val p = processor()
        assertEquals(
            BotCommandProcessor.Result.None,
            p.handle("!help", null, ChatBotCommandScope.MENTION, "", "vividbot"),
        )
        assertEquals(
            BotCommandProcessor.Result.None,
            p.handle("!pause", null, ChatBotCommandScope.MENTION, "", "vividbot"),
        )
    }

    @Test
    fun `mention scope answers commands addressed to the bot`() {
        val p = processor()
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            p.handle("@vividbot !help", null, ChatBotCommandScope.MENTION, "", "vividbot"),
        )
        // Auch ohne @ und mit nachgestelltem Komma/Doppelpunkt.
        assertEquals(
            BotCommandProcessor.Result.MediaPause,
            p.handle("vividbot: !pause", null, ChatBotCommandScope.MENTION, "", "vividbot"),
        )
    }

    @Test
    fun `mention scope is case-insensitive on the bot login`() {
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            processor().handle("@VividBot !help", null, ChatBotCommandScope.MENTION, "", "vividbot"),
        )
    }

    @Test
    fun `prefix scope answers only prefixed commands`() {
        val p = processor()
        assertEquals(
            BotCommandProcessor.Result.Reply(
                "Verfügbare Befehle: !v!help · !v!uptime · !v!tts · !v!song · !v!next · !v!pause · !v!bot · !v!testalert · !v!torch · !v!filter · !v!boost",
            ),
            p.handle("!v!help", null, ChatBotCommandScope.PREFIX, "v"),
        )
        assertEquals(
            BotCommandProcessor.Result.ToggleTts,
            p.handle("!v!tts", null, ChatBotCommandScope.PREFIX, "v"),
        )
        assertEquals(
            BotCommandProcessor.Result.MediaNext,
            p.handle("!v!next", null, ChatBotCommandScope.PREFIX, "v"),
        )
    }

    @Test
    fun `prefix scope ignores generic commands of the other bot`() {
        val p = processor()
        // Generische !-Befehle gehören dem anderen Bot → None, nicht Unknown.
        assertEquals(
            BotCommandProcessor.Result.None,
            p.handle("!help", null, ChatBotCommandScope.PREFIX, "v"),
        )
        assertEquals(
            BotCommandProcessor.Result.None,
            p.handle("@rivuletbot !uptime", null, ChatBotCommandScope.PREFIX, "v"),
        )
    }

    @Test
    fun `prefix scope works with multi-character prefixes and inside a message`() {
        val p = processor()
        assertEquals(
            BotCommandProcessor.Result.MediaPause,
            p.handle("hey @vividbot !vivid!pause", null, ChatBotCommandScope.PREFIX, "vivid"),
        )
    }

    @Test
    fun `prefix scope without a configured prefix answers nothing`() {
        assertEquals(
            BotCommandProcessor.Result.None,
            processor().handle("!v!help", null, ChatBotCommandScope.PREFIX, ""),
        )
    }

    @Test
    fun `prefix scope is case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.MediaNext,
            processor().handle("!V!next", null, ChatBotCommandScope.PREFIX, "V"),
        )
    }

    @Test
    fun `all scope is the default and keeps answering every command`() {
        val p = processor()
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            p.handle("!help", null),
        )
        assertEquals(
            BotCommandProcessor.Result.Reply(BotCommandProcessor.HELP_TEXT),
            p.handle("!help", null, ChatBotCommandScope.ALL, "v", "vividbot"),
        )
    }

    // --- !filter (Video-Effekte) ---

    @Test
    fun `filter without args returns Filter with null name`() {
        assertEquals(
            BotCommandProcessor.Result.Filter(null),
            processor().handle("!filter", null),
        )
    }

    @Test
    fun `filter with name returns Filter with name`() {
        assertEquals(
            BotCommandProcessor.Result.Filter("SEPIA"),
            processor().handle("!filter SEPIA", null),
        )
    }

    @Test
    fun `fx alias works like filter`() {
        assertEquals(
            BotCommandProcessor.Result.Filter(null),
            processor().handle("!fx", null),
        )
    }

    @Test
    fun `filter is case-insensitive in dispatch`() {
        assertEquals(
            BotCommandProcessor.Result.Filter("grayscale"),
            processor().handle("!filter grayscale", null),
        )
    }

    @Test
    fun `filter in middle of message`() {
        assertEquals(
            BotCommandProcessor.Result.Filter("noise"),
            processor().handle("@vividbot !filter noise please", null),
        )
    }

    // --- !boost: Low-Light-Boost (Owner-only, Gate in der Engine) ---

    @Test
    fun `boost maps to OwnerBoost`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerBoost,
            processor().handle("!boost", null),
        )
    }

    @Test
    fun `boost aliases lowlight and low-light`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerBoost,
            processor().handle("!lowlight", null),
        )
        assertEquals(
            BotCommandProcessor.Result.OwnerBoost,
            processor().handle("!low-light", null),
        )
    }

    @Test
    fun `boost is case-insensitive`() {
        assertEquals(
            BotCommandProcessor.Result.OwnerBoost,
            processor().handle("!BOOST", null),
        )
    }
}
