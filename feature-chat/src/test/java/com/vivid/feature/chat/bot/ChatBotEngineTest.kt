package com.vivid.feature.chat.bot

import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.LlmConfig
import com.vivid.feature.chat.ai.LlmException
import com.vivid.feature.chat.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
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
    ): ChatMessage = ChatMessage(
        id = "id-${text.hashCode()}",
        channel = "channel",
        userId = "1",
        userLogin = login,
        displayName = displayName ?: login,
        color = null,
        text = text,
        badges = emptyList(),
        emotesTag = "",
        timestamp = System.currentTimeMillis(),
        isModerator = false,
        isSubscriber = false,
    )

    private fun config(
        login: String = "vividbot",
        mentionsOnly: Boolean = true,
        replyCooldownMillis: Long = 8_000,
        maxRepliesPerMinute: Int = 10,
        systemPrompt: String = "Du bist ein freundlicher Bot.",
    ): ChatBotConfig = ChatBotConfig(
        channel = "channel",
        login = login,
        oauthToken = "token",
        systemPrompt = systemPrompt,
        mentionsOnly = mentionsOnly,
        replyCooldownMillis = replyCooldownMillis,
        maxRepliesPerMinute = maxRepliesPerMinute,
        llm = LlmConfig(baseUrl = "https://llm.example", apiKey = "key", model = "model"),
    )

    private fun engine(): ChatBotEngine = ChatBotEngine(llmClient = llm)

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
}
