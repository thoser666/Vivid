package com.vivid.feature.chat.bot

import com.vivid.feature.chat.model.ChatMessage
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatTtsControllerTest {

    private val speaker = mockk<ChatTtsSpeaker>(relaxed = true)

    private val messages = MutableSharedFlow<ChatMessage>(
        replay = 16,
        extraBufferCapacity = 16,
    )

    private fun chatMessage(
        text: String,
        login: String = "viewer1",
        displayName: String = "Viewer1",
    ): ChatMessage = ChatMessage(
        id = "id-${text.hashCode()}",
        channel = "channel",
        userId = "1",
        userLogin = login,
        displayName = displayName,
        color = null,
        text = text,
        badges = emptyList(),
        emotesTag = "",
        timestamp = System.currentTimeMillis(),
        isModerator = false,
        isSubscriber = false,
    )

    private fun controller(scope: CoroutineScope): ChatTtsController =
        ChatTtsController(scope = scope, speaker = speaker)

    @Test
    fun `toggle flips the enabled state and returns the new value`() = runTest {
        val controller = controller(this)
        assertFalse(controller.enabled.value)

        assertTrue(controller.toggle())
        assertTrue(controller.enabled.value)

        assertFalse(controller.toggle())
        assertFalse(controller.enabled.value)
    }

    @Test
    fun `setEnabled updates the state`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        assertTrue(controller.enabled.value)
        controller.setEnabled(false)
        assertFalse(controller.enabled.value)
    }

    @Test
    fun `start speaks viewer messages when enabled`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot")

        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        verify { speaker.speak("Viewer1: hallo zusammen") }
        controller.stop()
    }

    @Test
    fun `start skips the bots own messages`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot")

        messages.emit(chatMessage("TTS ist jetzt AN 🔊 — Chat wird vorgelesen.", login = "vividbot"))
        advanceUntilIdle()

        verify(exactly = 0) { speaker.speak(any()) }
        controller.stop()
    }

    @Test
    fun `start skips command messages`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot")

        messages.emit(chatMessage("!tts"))
        messages.emit(chatMessage("@vividbot !help"))
        advanceUntilIdle()

        verify(exactly = 0) { speaker.speak(any()) }
        controller.stop()
    }

    @Test
    fun `start skips messages from bots on the ignore list`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot", ignoreBots = setOf("rivuletbot"))

        messages.emit(chatMessage("Der Stream startet gleich!", login = "rivuletbot", displayName = "RivuletBot"))
        messages.emit(chatMessage("hallo zusammen", login = "viewer1", displayName = "Viewer1"))
        advanceUntilIdle()

        // Der andere Bot wird nicht vorgelesen, normale Viewer schon.
        verify(exactly = 0) { speaker.speak("RivuletBot: Der Stream startet gleich!") }
        verify { speaker.speak("Viewer1: hallo zusammen") }
        controller.stop()
    }

    @Test
    fun `start does not speak while tts is disabled`() = runTest {
        val controller = controller(this)
        controller.start(messages, ownLogin = "vividbot") // enabled = false

        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        verify(exactly = 0) { speaker.speak(any()) }
        controller.stop()
    }

    @Test
    fun `enabling tts afterwards starts speaking`() = runTest {
        val controller = controller(this)
        controller.start(messages, ownLogin = "vividbot")

        messages.emit(chatMessage("vorher"))
        advanceUntilIdle() // wird noch bei deaktiviertem TTS verarbeitet → nicht vorgelesen
        controller.setEnabled(true)
        messages.emit(chatMessage("nachher"))
        advanceUntilIdle()

        verify(exactly = 0) { speaker.speak("Viewer1: vorher") }
        verify { speaker.speak("Viewer1: nachher") }
        controller.stop()
    }

    @Test
    fun `spoken text is capped at the maximum length`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot")

        messages.emit(chatMessage("x".repeat(300)))
        advanceUntilIdle()

        val spoken = slot<String>()
        verify { speaker.speak(capture(spoken)) }
        assertEquals(ChatTtsController.MAX_SPOKEN_CHARS + "Viewer1: ".length, spoken.captured.length)
        controller.stop()
    }

    @Test
    fun `stop cancels the subscription`() = runTest {
        val controller = controller(this)
        controller.setEnabled(true)
        controller.start(messages, ownLogin = "vividbot")
        controller.stop()

        messages.emit(chatMessage("hallo zusammen"))
        advanceUntilIdle()

        verify(exactly = 0) { speaker.speak(any()) }
    }
}
