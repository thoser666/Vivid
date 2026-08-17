package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.LlmConfig
import com.vivid.feature.chat.ai.LlmException
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.ai.LlmMessage
import com.vivid.feature.chat.media.ChatMediaPlayer
import com.vivid.feature.chat.twitch.TwitchWhisperException
import java.util.Optional
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatBotEngineTest {

    private val llm = mockk<LlmClient>()
    private val sender = mockk<ChatSender>()

    private val messages = MutableSharedFlow<ChatMessage>(
        replay = 16,
        extraBufferCapacity = 16,
    )

    private fun chatMessage(
        text: String,
        login: String = "viewer1",
        displayName: String? = null,
        isModerator: Boolean = false,
        isBroadcaster: Boolean = false,
        isWhisper: Boolean = false,
    ): ChatMessage = ChatMessage(
        id = "id-${text.hashCode()}",
        channel = "channel",
        // userId ist plattformneutral und muss pro User eindeutig sein —
        // die Per-Viewer-Limits der Engine keyen darauf.
        userId = "user-$login",
        userLogin = login,
        displayName = displayName ?: login,
        color = null,
        text = text,
        badges = emptyList(),
        emotesTag = "",
        timestamp = System.currentTimeMillis(),
        isModerator = isModerator,
        isSubscriber = false,
        isBroadcaster = isBroadcaster,
        isWhisper = isWhisper,
    )

    private fun config(
        login: String = "vividbot",
        mentionsOnly: Boolean = true,
        replyCooldownMillis: Long = 8_000,
        maxRepliesPerMinute: Int = 10,
        systemPrompt: String = "Du bist ein freundlicher Bot.",
        mode: ChatBotMode = ChatBotMode.AUTONOMOUS,
        apiKey: String = "key",
        commandScope: ChatBotCommandScope = ChatBotCommandScope.ALL,
        commandPrefix: String = "",
        ignoreBots: Set<String> = emptySet(),
        perViewerCooldownMillis: Long = 0L,
        perViewerMaxReplies: Int = 0,
        maxRepliesPerHour: Int = 0,
        ownerLogins: Set<String> = emptySet(),
        ownerLlmBaseUrl: String = "",
        ownerLlmApiKey: String = "",
        ownerLlmModel: String = "",
        // Standard aus, damit die bestehenden Owner-Tests den öffentlichen
        // Weg (sender.send) prüfen; Whisper-Tests aktivieren ihn explizit.
        ownerWhisperReplies: Boolean = false,
    ): ChatBotConfig = ChatBotConfig(
        channel = "channel",
        login = login,
        oauthToken = "token",
        systemPrompt = systemPrompt,
        mentionsOnly = mentionsOnly,
        replyCooldownMillis = replyCooldownMillis,
        maxRepliesPerMinute = maxRepliesPerMinute,
        mode = mode,
        commandScope = commandScope,
        commandPrefix = commandPrefix,
        ignoreBots = ignoreBots,
        perViewerCooldownMillis = perViewerCooldownMillis,
        perViewerMaxReplies = perViewerMaxReplies,
        maxRepliesPerHour = maxRepliesPerHour,
        ownerLogins = ownerLogins,
        ownerLlm = LlmConfig(
            baseUrl = ownerLlmBaseUrl,
            apiKey = ownerLlmApiKey,
            model = ownerLlmModel,
        ),
        ownerWhisperReplies = ownerWhisperReplies,
        llm = LlmConfig(baseUrl = "https://llm.example", apiKey = apiKey, model = "model"),
    )

    private fun engine(
        media: ChatMediaPlayer = mockk(),
        streamControl: ChatStreamControl? = null,
    ): ChatBotEngine = ChatBotEngine(
        llmClient = llm,
        commandProcessor = BotCommandProcessor(),
        chatTts = mockk(),
        media = media,
        chatStreamControl = Optional.ofNullable(streamControl),
    )

    private fun streamControl(status: ChatStreamStatus = ChatStreamStatus.Idle): ChatStreamControl =
        mockk {
            coEvery { start() } just Runs
            every { stop() } just Runs
            coEvery { diagnostics() } returns StreamDiagnostics(
                status = status,
                obsConnected = true,
                checks = listOf(DiagnosticCheck("Stream-URL (primär)", ok = true)),
            )
        }

    @Test
    fun `replies when mentioned in mentions-only mode`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("@vividbot hallo"))
        advanceUntilIdle()

        coVerify { llm.complete(any(), any()) }
        coVerify { sender.send("Antwort!") }
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `ignores messages without mention in mentions-only mode`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `replies without mention when mentions-only is disabled`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(mentionsOnly = false), sender, this)
        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        coVerify { sender.send("Antwort!") }
        engine.stop()
    }

    @Test
    fun `ignores its own messages`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("hallo", login = "vividbot"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    // --- Koexistenz: Ignore-Liste für andere Bots (z. B. Rivulet) ---

    @Test
    fun `ignores commands from bots on the ignore list`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(ignoreBots = setOf("rivuletbot")), sender, this)
        messages.emit(chatMessage("!help", login = "rivuletbot", displayName = "RivuletBot"))
        messages.emit(chatMessage("!pause", login = "rivuletbot", displayName = "RivuletBot"))
        advanceUntilIdle()

        // Weder Befehle noch LLM-Aufrufe für den ignorierten Bot.
        coVerify(exactly = 0) { sender.send(any()) }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `does not feed ignored bot messages to the llm in autonomous mode`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"

        engine.start(
            messages,
            config(mentionsOnly = false, ignoreBots = setOf("rivuletbot")),
            sender,
            this,
        )
        messages.emit(chatMessage("Der Stream startet gleich!", login = "rivuletbot", displayName = "RivuletBot"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `answers commands from other viewers even with an ignore list`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(ignoreBots = setOf("rivuletbot")), sender, this)
        messages.emit(chatMessage("!help", login = "viewer1", displayName = "Viewer1"))
        advanceUntilIdle()

        coVerify { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    // --- Koexistenz: Befehlsscope MENTION/PREFIX in der Engine ---

    @Test
    fun `mention scope answers only addressed commands`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(commandScope = ChatBotCommandScope.MENTION), sender, this)
        messages.emit(chatMessage("!help")) // gehört dem anderen Bot
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `prefix scope answers only prefixed commands`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(commandScope = ChatBotCommandScope.PREFIX, commandPrefix = "v"),
            sender,
            this,
        )
        messages.emit(chatMessage("!uptime")) // generisch → anderer Bot
        messages.emit(chatMessage("!v!help"))
        advanceUntilIdle()

        coVerify(exactly = 0) { sender.send("Gerade läuft kein Stream.") }
        coVerify(exactly = 1) {
            sender.send("Verfügbare Befehle: !v!help · !v!uptime · !v!tts · !v!song · !v!next · !v!pause · !v!bot")
        }
        engine.stop()
    }

    // --- Begrenzungen: Per-Viewer-Cooldown, Per-Viewer-Cap, Stunden-Budget ---

    @Test
    fun `per viewer cooldown blocks repeat replies from the same viewer`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0, // nur das Per-Viewer-Limit testen
                perViewerCooldownMillis = 60_000,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help")) // viewer1
        advanceUntilIdle()
        currentTime += 5_000 // innerhalb des 60s-Fensters
        messages.emit(chatMessage("@vividbot !uptime")) // viewer1, geblockt
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }
        coVerify(exactly = 0) { sender.send("Gerade läuft kein Stream.") }
        engine.stop()
    }

    @Test
    fun `per viewer cooldown allows a reply after the window passes`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerCooldownMillis = 60_000,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()
        currentTime += 61_000
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()

        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `per viewer cooldown is independent per viewer`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerCooldownMillis = 60_000,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help", login = "viewer1"))
        advanceUntilIdle()
        currentTime += 5_000
        messages.emit(chatMessage("@vividbot !help", login = "viewer2"))
        advanceUntilIdle()

        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `moderators bypass the per viewer cooldown`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerCooldownMillis = 60_000,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help", login = "mod1"))
        advanceUntilIdle()
        currentTime += 5_000
        // Gleicher Absender, aber Moderator → antwortet trotz Cooldown.
        messages.emit(chatMessage("@vividbot !uptime", login = "mod1", isModerator = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }
        coVerify(exactly = 1) { sender.send("Gerade läuft kein Stream.") }
        engine.stop()
    }

    @Test
    fun `per viewer cap limits replies per viewer per stream`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerCooldownMillis = 0,
                perViewerMaxReplies = 2,
            ),
            sender,
            this,
        )
        repeat(3) {
            messages.emit(chatMessage("@vividbot !help"))
            advanceUntilIdle()
            currentTime += 5_000 // kein Cooldown → nur der Cap zählt
        }

        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `per viewer cap resets when the engine restarts`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerMaxReplies = 1,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()
        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }

        // Neustart (z. B. Stream-Ende/-Start) setzt den Zähler zurück.
        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                perViewerMaxReplies = 1,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()
        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `hourly budget stops replies once the hour cap is reached`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                maxRepliesPerHour = 2,
            ),
            sender,
            this,
        )
        repeat(3) {
            messages.emit(chatMessage("@vividbot !help", login = "viewer$it"))
            advanceUntilIdle()
        }

        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `hourly budget resets after an hour`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                maxRepliesPerHour = 1,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help", login = "viewer1"))
        advanceUntilIdle()
        messages.emit(chatMessage("@vividbot !help", login = "viewer2"))
        advanceUntilIdle()
        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }

        currentTime += 3_601_000 // über eine Stunde später
        messages.emit(chatMessage("@vividbot !help", login = "viewer3"))
        advanceUntilIdle()
        coVerify(exactly = 2) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `per viewer cooldown also blocks the autonomous llm path`() = runTest {
        val engine = engine()
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { llm.complete(any(), any()) } returns "KI-Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(mentionsOnly = false, perViewerCooldownMillis = 60_000, replyCooldownMillis = 0),
            sender,
            this,
        )
        messages.emit(chatMessage("hallo bot")) // viewer1 → LLM antwortet
        advanceUntilIdle()
        currentTime += 5_000 // noch im 60s-Fenster
        messages.emit(chatMessage("und noch eine Frage")) // viewer1 → geblockt
        advanceUntilIdle()

        coVerify(exactly = 1) { llm.complete(any(), any()) }
        coVerify(exactly = 1) { sender.send("KI-Antwort!") }
        engine.stop()
    }

    @Test
    fun `per viewer cooldown blocks media commands from the same viewer`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.nowPlaying() } returns "Song"
        every { media.pause() } just Runs
        val engine = engine(media)
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(perViewerCooldownMillis = 60_000, replyCooldownMillis = 0),
            sender,
            this,
        )
        messages.emit(chatMessage("!song")) // viewer1
        advanceUntilIdle()
        currentTime += 5_000
        messages.emit(chatMessage("!pause")) // viewer1 → geblockt, keine Aktion
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.send("Aktuell läuft: Song") }
        coVerify(exactly = 0) { media.pause() }
        engine.stop()
    }

    // --- Live-Verbrauch (Settings-Screen: Kosten-Budget beobachten) ---

    @Test
    fun `usage exposes hourly count, budget, total and top viewers`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(
                commandScope = ChatBotCommandScope.MENTION,
                replyCooldownMillis = 0,
                maxRepliesPerHour = 3,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help", login = "viewer1", displayName = "ViewerEins"))
        messages.emit(chatMessage("@vividbot !help", login = "viewer2", displayName = "ViewerZwei"))
        messages.emit(chatMessage("@vividbot !help", login = "viewer1", displayName = "ViewerEins"))
        advanceUntilIdle()

        val usage = engine.usage.value
        assertEquals(3, usage.repliesThisHour)
        assertEquals(3, usage.hourlyBudget)
        assertEquals(3, usage.totalRepliesThisStream)
        assertEquals(listOf("ViewerEins", "ViewerZwei"), usage.topViewers.map { it.displayName })
        assertEquals(2, usage.topViewers.first().replies)
        engine.stop()
    }

    @Test
    fun `usage reports an unset budget as zero`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(commandScope = ChatBotCommandScope.MENTION, replyCooldownMillis = 0),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()

        assertEquals(1, engine.usage.value.repliesThisHour)
        assertEquals(0, engine.usage.value.hourlyBudget)
        engine.stop()
    }

    @Test
    fun `usage resets when the engine restarts`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(commandScope = ChatBotCommandScope.MENTION, replyCooldownMillis = 0),
            sender,
            this,
        )
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()
        assertEquals(1, engine.usage.value.totalRepliesThisStream)

        engine.start(
            messages,
            config(commandScope = ChatBotCommandScope.MENTION, replyCooldownMillis = 0),
            sender,
            this,
        )
        assertEquals(0, engine.usage.value.totalRepliesThisStream)
        engine.stop()
    }

    @Test
    fun `respects the cooldown between replies`() = runTest {
        val engine = engine()
        var currentTime = 1_000_000L // Basis hoch genug, dass die erste Antwort nicht am Cooldown hängt
        engine.now = { currentTime }
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("@vividbot eins"))
        currentTime = 1_004_000 // innerhalb des 8s-Cooldowns
        messages.emit(chatMessage("@vividbot zwei"))
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.send(any()) }
        engine.stop()
    }

    @Test
    fun `respects the rate limit per minute`() = runTest {
        val engine = engine()
        var currentTime = 0L
        engine.now = { currentTime }
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(replyCooldownMillis = 0, maxRepliesPerMinute = 2), sender, this)
        messages.emit(chatMessage("@vividbot eins"))
        currentTime = 1_000
        messages.emit(chatMessage("@vividbot zwei"))
        currentTime = 2_000
        messages.emit(chatMessage("@vividbot drei"))
        advanceUntilIdle()

        coVerify(exactly = 2) { sender.send(any()) }
        engine.stop()
    }

    @Test
    fun `trims long replies to the configured maximum length`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "x".repeat(1000)
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("@vividbot hallo"))
        advanceUntilIdle()

        val sent = slot<String>()
        coVerify { sender.send(capture(sent)) }
        assertTrue(sent.captured.length <= 500)
        engine.stop()
    }

    @Test
    fun `does not send anything when the llm fails`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } throws LlmException("kaputt")

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("@vividbot hallo"))
        advanceUntilIdle()

        coVerify(exactly = 0) { sender.send(any()) }
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `does not start when the configuration is incomplete`() = runTest {
        val engine = engine()
        val broken = config().copy(oauthToken = "")

        engine.start(messages, broken, sender, this)
        messages.emit(chatMessage("@vividbot hallo"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        assertEquals(ChatBotState.Disabled, engine.state.value)
        engine.stop()
    }

    // --- Betriebsmodus-Switch: COMMAND („Bot wie Moblin“) ---

    @Test
    fun `command mode answers commands deterministically without the llm`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(mode = ChatBotMode.COMMAND), sender, this)
        messages.emit(chatMessage("!help"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        coVerify { sender.send(BotCommandProcessor.HELP_TEXT) }
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `command mode ignores non-command messages`() = runTest {
        val engine = engine()

        engine.start(messages, config(mode = ChatBotMode.COMMAND), sender, this)
        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        coVerify(exactly = 0) { sender.send(any()) }
        engine.stop()
    }

    @Test
    fun `command mode hints on unknown commands`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(mode = ChatBotMode.COMMAND), sender, this)
        messages.emit(chatMessage("!xyz"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        coVerify { sender.send("Unbekannter Befehl „xyz“ — Tipp: !help") }
        engine.stop()
    }

    @Test
    fun `command mode starts even without an llm key`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(mode = ChatBotMode.COMMAND, apiKey = ""), sender, this)
        messages.emit(chatMessage("!bot"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        coVerify { sender.send(BotCommandProcessor.BOT_INFO_TEXT) }
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `command mode answers uptime from the stream start time`() = runTest {
        var currentTime = 10_000_000L
        val engine = ChatBotEngine(
            llmClient = llm,
            commandProcessor = BotCommandProcessor().apply { now = { currentTime } },
            chatTts = mockk(),
            media = mockk(),
            chatStreamControl = Optional.empty(),
        )
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(mode = ChatBotMode.COMMAND, replyCooldownMillis = 0),
            sender,
            this,
            streamStartedAtMillis = currentTime - 3_661_000L, // vor 1h 1m 1s
        )
        messages.emit(chatMessage("!uptime"))
        advanceUntilIdle()

        coVerify { sender.send("Der Stream läuft seit 1h 1m 1s.") }
        engine.stop()
    }

    // --- Betriebsmodus-Switch: AUTONOMOUS („KI entscheidet selbst“) ---

    @Test
    fun `autonomous mode answers commands deterministically and lets the llm decide on other messages`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(mode = ChatBotMode.AUTONOMOUS, mentionsOnly = false, replyCooldownMillis = 0),
            sender,
            this,
        )
        messages.emit(chatMessage("!help"))
        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        coVerify(exactly = 1) { llm.complete(any(), any()) }
        coVerify { sender.send(BotCommandProcessor.HELP_TEXT) }
        coVerify { sender.send("Antwort!") }
        engine.stop()
    }

    @Test
    fun `autonomous mode lets the ai stay silent via the no-reply marker`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns ChatBotEngine.NO_REPLY_MARKER

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("@vividbot hallo"))
        advanceUntilIdle()

        coVerify(exactly = 1) { llm.complete(any(), any()) }
        coVerify(exactly = 0) { sender.send(any()) }
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `autonomous mode passes the autonomy guidance in the system prompt`() = runTest {
        val engine = engine()
        coEvery { llm.complete(any(), any()) } returns "Antwort!"
        coEvery { sender.send(any()) } just Runs
        val messagesArg = slot<List<LlmMessage>>()

        engine.start(messages, config(mentionsOnly = false), sender, this)
        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        coVerify { llm.complete(any(), capture(messagesArg)) }
        val system = messagesArg.captured.first { it.role == LlmMessage.ROLE_SYSTEM }
        assertTrue(system.content.contains("autonomer Chat-Bot"))
        assertTrue(system.content.contains(ChatBotEngine.NO_REPLY_MARKER))
        engine.stop()
    }

    // --- !tts: Chat-Text-to-Speech ---

    @Test
    fun `tts command toggles the chat tts and confirms in chat`() = runTest {
        val chatTts = mockk<ChatTtsController>()
        every { chatTts.toggle() } returns true
        val engine = ChatBotEngine(
            llmClient = llm,
            commandProcessor = BotCommandProcessor(),
            chatTts = chatTts,
            media = mockk(),
            chatStreamControl = Optional.empty(),
        )
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!tts"))
        advanceUntilIdle()

        coVerify(exactly = 1) { chatTts.toggle() }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        assertTrue(sent.captured.contains("AN"))
        engine.stop()
    }

    @Test
    fun `tts command confirms when toggling off`() = runTest {
        val chatTts = mockk<ChatTtsController>()
        every { chatTts.toggle() } returns false
        val engine = ChatBotEngine(
            llmClient = llm,
            commandProcessor = BotCommandProcessor(),
            chatTts = chatTts,
            media = mockk(),
            chatStreamControl = Optional.empty(),
        )
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!tts"))
        advanceUntilIdle()

        coVerify(exactly = 1) { chatTts.toggle() }
        assertTrue(sent.captured.contains("AUS"))
        engine.stop()
    }

    // --- Media-Player-Steuerung (!song/!next/!pause/!play/!prev) ---

    @Test
    fun `song command replies with the current track`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.nowPlaying() } returns "Blue Monday – New Order"
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!song"))
        advanceUntilIdle()

        coVerify(exactly = 0) { llm.complete(any(), any()) }
        assertEquals("Aktuell läuft: Blue Monday – New Order", sent.captured)
        engine.stop()
    }

    @Test
    fun `song command reports when nothing is playing`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.nowPlaying() } returns null
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!song"))
        advanceUntilIdle()

        assertEquals("Gerade läuft kein Song.", sent.captured)
        engine.stop()
    }

    @Test
    fun `song command reports missing notification access`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns false
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!song"))
        advanceUntilIdle()

        assertEquals(ChatBotEngine.MEDIA_NO_ACCESS_TEXT, sent.captured)
        verify(exactly = 0) { media.nowPlaying() }
        engine.stop()
    }

    @Test
    fun `next command skips and confirms`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.skipToNext() } just Runs
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!next"))
        advanceUntilIdle()

        coVerify(exactly = 1) { media.skipToNext() }
        assertEquals(ChatBotEngine.MEDIA_NEXT_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `pause command pauses and confirms`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.pause() } just Runs
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!pause"))
        advanceUntilIdle()

        coVerify(exactly = 1) { media.pause() }
        assertEquals(ChatBotEngine.MEDIA_PAUSE_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `play command resumes and confirms`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.play() } just Runs
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!play"))
        advanceUntilIdle()

        coVerify(exactly = 1) { media.play() }
        assertEquals(ChatBotEngine.MEDIA_PLAY_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `prev command goes back and confirms`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns true
        every { media.skipToPrevious() } just Runs
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!prev"))
        advanceUntilIdle()

        coVerify(exactly = 1) { media.skipToPrevious() }
        assertEquals(ChatBotEngine.MEDIA_PREVIOUS_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `media actions report missing notification access without acting`() = runTest {
        val media = mockk<ChatMediaPlayer>()
        every { media.hasAccess() } returns false
        val engine = engine(media)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!pause"))
        advanceUntilIdle()

        assertEquals(ChatBotEngine.MEDIA_NO_ACCESS_TEXT, sent.captured)
        verify(exactly = 0) { media.pause() }
        engine.stop()
    }

    // --- Owner-Steuerung (!start / !stop / !diag / !ask — nur der Streamer) ---

    @Test
    fun `owner start command from a viewer is rejected without acting`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!start")) // viewer1
        advanceUntilIdle()

        assertEquals(ChatBotEngine.OWNER_ONLY_TEXT, sent.captured)
        coVerify(exactly = 0) { control.start() }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `owner start command from the allow list starts the stream`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!start", login = "streamer2", displayName = "StreamerZwei"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.start() }
        assertEquals(ChatBotEngine.STREAM_START_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `broadcaster badge allows owner commands`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!stop", login = "streamer1", isBroadcaster = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.stop() }
        assertEquals(ChatBotEngine.STREAM_STOP_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `owner commands work in command mode without the llm`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(mode = ChatBotMode.COMMAND, ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!start", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.start() }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        assertEquals(ChatBotEngine.STREAM_START_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `owner commands bypass the reply cooldown`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        var currentTime = 1_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), replyCooldownMillis = 8_000),
            sender,
            this,
        )
        // Viewer-Antwort registriert einen Reply (Start des Cooldown-Fensters).
        messages.emit(chatMessage("!help"))
        advanceUntilIdle()
        currentTime += 1_000 // noch innerhalb des 8s-Cooldowns
        messages.emit(chatMessage("!start", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.start() }
        engine.stop()
    }

    @Test
    fun `owner commands are still protected by the rate limit`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        var currentTime = 10_000_000L
        engine.now = { currentTime }
        coEvery { sender.send(any()) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), replyCooldownMillis = 0, maxRepliesPerMinute = 1),
            sender,
            this,
        )
        messages.emit(chatMessage("!help")) // verbraucht das Rate-Limit-Fenster
        advanceUntilIdle()
        messages.emit(chatMessage("!start", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { control.start() }
        coVerify(exactly = 1) { sender.send(BotCommandProcessor.HELP_TEXT) }
        engine.stop()
    }

    @Test
    fun `owner diagnose without owner llm sends the deterministic summary`() = runTest {
        val control = mockk<ChatStreamControl> {
            coEvery { start() } just Runs
            every { stop() } just Runs
            coEvery { diagnostics() } returns StreamDiagnostics(
                status = ChatStreamStatus.Streaming,
                obsConnected = false,
                checks = listOf(
                    DiagnosticCheck("Stream-URL (primär)", ok = true),
                    DiagnosticCheck("OBS verbunden", ok = false),
                ),
            )
        }
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!diag", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.diagnostics() }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        assertTrue(sent.captured.contains("Stream:"))
        assertTrue(sent.captured.contains("OBS: ⚠️"))
        assertTrue(sent.captured.contains("Offene Punkte"))
        assertTrue(sent.captured.contains("OBS verbunden"))
        engine.stop()
    }

    @Test
    fun `owner diagnose with owner llm routes the fact sheet to the owner llm`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        val messagesArg = slot<List<LlmMessage>>()
        val ownerLlm = LlmConfig(baseUrl = "https://owner.example", apiKey = "owner-key", model = "claude-4")
        coEvery { llm.complete(eq(ownerLlm), capture(messagesArg)) } returns "Empfehlung: Alles gut ✅"
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(
            messages,
            config(
                ownerLogins = setOf("streamer2"),
                ownerLlmBaseUrl = ownerLlm.baseUrl,
                ownerLlmApiKey = ownerLlm.apiKey,
                ownerLlmModel = ownerLlm.model,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("!diag", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.diagnostics() }
        val user = messagesArg.captured.first { it.role == LlmMessage.ROLE_USER }
        assertTrue(user.content.contains("stream_status=idle"))
        assertTrue(user.content.contains("check:Stream-URL (primär)=ok"))
        assertEquals("Empfehlung: Alles gut ✅", sent.captured)
        engine.stop()
    }

    @Test
    fun `owner diagnose falls back to the summary when the owner llm fails`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { llm.complete(any(), any()) } throws LlmException("kaputt")
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(
            messages,
            config(
                ownerLogins = setOf("streamer2"),
                ownerLlmBaseUrl = "https://owner.example",
                ownerLlmApiKey = "key",
                ownerLlmModel = "model",
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("!diag", login = "streamer2"))
        advanceUntilIdle()

        assertTrue(sent.captured.contains("Stream:"))
        assertTrue(sent.captured.contains("Owner-KI fehlgeschlagen"))
        assertEquals(ChatBotState.Idle, engine.state.value)
        engine.stop()
    }

    @Test
    fun `owner ask without owner llm shows the configuration hint`() = runTest {
        val engine = engine()
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!ask warum stockt der Stream?", login = "streamer2"))
        advanceUntilIdle()

        assertEquals(ChatBotEngine.OWNER_LLM_NOT_CONFIGURED_TEXT, sent.captured)
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `owner ask without text asks for a question`() = runTest {
        val engine = engine()
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!ask", login = "streamer2"))
        advanceUntilIdle()

        assertEquals(ChatBotEngine.OWNER_ASK_EMPTY_TEXT, sent.captured)
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `owner ask routes the question with stream context to the owner llm`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        val messagesArg = slot<List<LlmMessage>>()
        val ownerLlm = LlmConfig(baseUrl = "https://owner.example", apiKey = "owner-key", model = "claude-4")
        coEvery { llm.complete(eq(ownerLlm), capture(messagesArg)) } returns "Antwort für den Streamer"
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(
            messages,
            config(
                ownerLogins = setOf("streamer2"),
                ownerLlmBaseUrl = ownerLlm.baseUrl,
                ownerLlmApiKey = ownerLlm.apiKey,
                ownerLlmModel = ownerLlm.model,
            ),
            sender,
            this,
        )
        messages.emit(chatMessage("!ask stelle die Verbindung zu Twitch her", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.diagnostics() }
        val user = messagesArg.captured.first { it.role == LlmMessage.ROLE_USER }
        assertTrue(user.content.contains("Aktueller Stream-Zustand:"))
        assertTrue(user.content.contains("stelle die Verbindung zu Twitch her"))
        assertEquals("Antwort für den Streamer", sent.captured)
        engine.stop()
    }

    // --- Privater Antwortweg: Owner-Antworten per Twitch-Whisper statt PRIVMSG ---

    @Test
    fun `owner start reply is whispered instead of sent to the channel`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        coEvery { sender.sendWhisper(any(), any()) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), ownerWhisperReplies = true),
            sender,
            this,
        )
        messages.emit(chatMessage("!start", login = "streamer2", displayName = "StreamerZwei"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.start() }
        coVerify(exactly = 1) { sender.sendWhisper("streamer2", ChatBotEngine.STREAM_START_TEXT) }
        coVerify(exactly = 0) { sender.send(any()) }
        engine.stop()
    }

    @Test
    fun `owner diagnose summary is whispered when private replies are enabled`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.sendWhisper(any(), capture(sent)) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), ownerWhisperReplies = true),
            sender,
            this,
        )
        messages.emit(chatMessage("!diag", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { sender.send(any()) }
        assertTrue(sent.captured.contains("Stream:"))
        engine.stop()
    }

    @Test
    fun `owner replies fall back to the public channel when the whisper fails`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.sendWhisper(any(), any()) } throws TwitchWhisperException("Empfänger blockt Whispers")
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), ownerWhisperReplies = true),
            sender,
            this,
        )
        messages.emit(chatMessage("!stop", login = "streamer2"))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.stop() }
        coVerify(exactly = 1) { sender.sendWhisper(any(), any()) }
        assertEquals(ChatBotEngine.STREAM_STOP_TEXT, sent.captured)
        engine.stop()
    }

    @Test
    fun `non-owner hint stays public even with whisper enabled`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val sent = slot<String>()
        coEvery { sender.send(capture(sent)) } just Runs

        engine.start(
            messages,
            config(ownerLogins = setOf("streamer2"), ownerWhisperReplies = true),
            sender,
            this,
        )
        messages.emit(chatMessage("!start")) // viewer1
        advanceUntilIdle()

        assertEquals(ChatBotEngine.OWNER_ONLY_TEXT, sent.captured)
        coVerify(exactly = 0) { sender.sendWhisper(any(), any()) }
        coVerify(exactly = 0) { control.start() }
        engine.stop()
    }

    // --- Whisper-Empfang (EventSub): Streamer schickt dem Bot private Befehle ---

    @Test
    fun `whisper from the allow list starts the stream and replies privately`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        coEvery { sender.sendWhisper(any(), any()) } just Runs
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!start", login = "streamer2", isWhisper = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { control.start() }
        coVerify(exactly = 1) { sender.sendWhisper("streamer2", ChatBotEngine.STREAM_START_TEXT) }
        coVerify(exactly = 0) { sender.send(any()) }
        engine.stop()
    }

    @Test
    fun `whisper from the channel owner counts as broadcaster and answers privately`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        val whispered = slot<String>()
        coEvery { sender.sendWhisper(any(), capture(whispered)) } just Runs

        engine.start(messages, config(), sender, this)
        messages.emit(
            chatMessage(
                "!diag",
                login = "channel",
                isBroadcaster = true, // vom EventSub-Client gesetzt (Login == Kanal)
                isWhisper = true,
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { control.diagnostics() }
        coVerify(exactly = 0) { sender.send(any()) }
        assertTrue(whispered.captured.contains("Stream:"))
        engine.stop()
    }

    @Test
    fun `whisper from a non-owner gets a private hint and no action`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        coEvery { sender.sendWhisper(any(), any()) } just Runs
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!start", login = "viewer1", isWhisper = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { sender.sendWhisper("viewer1", ChatBotEngine.OWNER_ONLY_TEXT) }
        coVerify(exactly = 0) { sender.send(any()) }
        coVerify(exactly = 0) { control.start() }
        engine.stop()
    }

    @Test
    fun `viewer commands in whispers are ignored`() = runTest {
        val engine = engine()
        coEvery { sender.send(any()) } just Runs
        coEvery { sender.sendWhisper(any(), any()) } just Runs
        coEvery { llm.complete(any(), any()) } returns "Antwort!"

        engine.start(messages, config(), sender, this)
        messages.emit(chatMessage("!help", login = "viewer1", isWhisper = true))
        messages.emit(chatMessage("was ist los?", login = "viewer1", isWhisper = true))
        advanceUntilIdle()

        coVerify(exactly = 0) { sender.send(any()) }
        coVerify(exactly = 0) { sender.sendWhisper(any(), any()) }
        coVerify(exactly = 0) { llm.complete(any(), any()) }
        engine.stop()
    }

    @Test
    fun `whisper reply failure never falls back to the public channel`() = runTest {
        val control = streamControl()
        val engine = engine(streamControl = control)
        coEvery { sender.sendWhisper(any(), any()) } throws TwitchWhisperException("Empfänger blockt Whispers")
        coEvery { sender.send(any()) } just Runs

        engine.start(messages, config(ownerLogins = setOf("streamer2")), sender, this)
        messages.emit(chatMessage("!stop", login = "streamer2", isWhisper = true))
        advanceUntilIdle()

        // Die Aktion wird ausgeführt, aber die Bestätigung bleibt privat —
        // eine private Anfrage wird nie öffentlich beantwortet.
        coVerify(exactly = 1) { control.stop() }
        coVerify(exactly = 1) { sender.sendWhisper(any(), any()) }
        coVerify(exactly = 0) { sender.send(any()) }
        engine.stop()
    }
}
