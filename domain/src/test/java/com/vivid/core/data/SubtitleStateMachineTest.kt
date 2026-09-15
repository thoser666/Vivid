package com.vivid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Tests der Untertitel-Logik (PARITY-Zeile 143): Zeilen-Rolling,
 * Fehler-Taxonomie (Retryable vs. permanent), Backoff-Arithmetik.
 * Ohne Android-Framework — die Maschine ist android-frei.
 */
class SubtitleStateMachineTest {

    private val machine = SubtitleStateMachine()

    // --- onEnabled / Capability-Fallback ------------------------------------

    @Test
    fun `onEnabled with available engine marks enabled`() {
        val next = machine.onEnabled(SubtitleState(), engineAvailable = true)
        assertTrue(next.enabled)
        assertTrue(next.available)
        assertNull(next.error)
    }

    @Test
    fun `onEnabled without engine keeps enabled but unavailable`() {
        val next = machine.onEnabled(SubtitleState(), engineAvailable = false)
        assertTrue(next.enabled)
        assertFalse(next.available)
    }

    // --- Rolling lines ------------------------------------------------------

    @Test
    fun `final text becomes a line and clears the partial`() {
        var state = machine.onEnabled(SubtitleState(), true)
        state = machine.onPartial(state, "hallo welt")
        assertEquals("hallo welt", state.partial)

        state = machine.onFinal(state, "hallo welt")
        assertEquals(listOf("hallo welt"), state.lines)
        assertEquals("", state.partial)
    }

    @Test
    fun `lines roll beyond maxLines`() {
        val rolling = SubtitleStateMachine(maxLines = 2)
        var state = rolling.onEnabled(SubtitleState(), true)
        state = rolling.onFinal(state, "eins")
        state = rolling.onFinal(state, "zwei")
        state = rolling.onFinal(state, "drei")
        assertEquals(listOf("zwei", "drei"), state.lines)
    }

    @Test
    fun `blank final text only clears the partial`() {
        var state = machine.onEnabled(SubtitleState(), true)
        state = machine.onPartial(state, "hallo")
        state = machine.onFinal(state, "   ")
        assertEquals(listOf<String>(), state.lines)
        assertEquals("", state.partial)
    }

    @Test
    fun `overlong text is truncated to MAX_LINE_LENGTH`() {
        var state = machine.onEnabled(SubtitleState(), true)
        state = machine.onFinal(state, "x".repeat(200))
        assertEquals(SubtitleStateMachine.MAX_LINE_LENGTH, state.lines.single().length)
    }

    @Test
    fun `displayLines appends the partial`() {
        var state = machine.onEnabled(SubtitleState(), true)
        state = machine.onFinal(state, "eins")
        state = machine.onPartial(state, "zwei ")
        assertEquals(listOf("eins", "zwei "), state.displayLines)
    }

    // --- Fehler-Taxonomie ---------------------------------------------------

    @Test
    fun `transient errors are retryable`() {
        // ERROR_RECOGNIZER_BUSY (8), ERROR_NETWORK (2), ERROR_SPEECH_TIMEOUT (6)
        for (code in listOf(8, 2, 1, 4, 6, 7)) {
            val transition = machine.onError(SubtitleState(enabled = true), code)
            assertTrue("Code $code sollte retryable sein", transition.restart)
        }
    }

    @Test
    fun `permanent errors are not retryable`() {
        // ERROR_AUDIO (3), ERROR_INSUFFICIENT_PERMISSIONS (9), ERROR_CLIENT (5), unbekannt
        for (code in listOf(3, 9, 5, 42)) {
            val transition = machine.onError(SubtitleState(enabled = true), code)
            assertFalse("Code $code sollte permanent sein", transition.restart)
        }
    }

    @Test
    fun `error clears listening and partial`() {
        var state = machine.onEnabled(SubtitleState(), true)
        state = machine.onPartial(state, "hallo")
        val transition = machine.onError(state, 2)
        assertFalse(transition.state.listening)
        assertEquals("", transition.state.partial)
        assertEquals(SubtitleError.NETWORK, transition.state.error)
    }

    @Test
    fun `successful final resets the error streak`() {
        machine.onError(SubtitleState(enabled = true), 2)
        machine.onError(SubtitleState(enabled = true), 2)
        assertEquals(2, machine.errorStreak)
        machine.onFinal(SubtitleState(enabled = true), "gesund")
        assertEquals(0, machine.errorStreak)
    }

    // --- Backoff ------------------------------------------------------------

    @Test
    fun `backoff grows exponentially and caps at 30 seconds`() {
        // 1. Fehler: 2 s, 2.: 4 s, 3.: 8 s, 4.: 16 s, 5.: 32 s → Cap 30 s
        val codes = intArrayOf(2, 2, 2, 2, 2)
        val expected = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        for ((index, code) in codes.withIndex()) {
            machine.onError(SubtitleState(enabled = true), code)
            assertEquals("Fehler #$index", expected[index], machine.restartDelayMs())
        }
        // Weitere Fehler bleiben am Cap.
        machine.onError(SubtitleState(enabled = true), 2)
        assertEquals(30_000L, machine.restartDelayMs())
    }

    @Test
    fun `reset clears the streak`() {
        machine.onError(SubtitleState(enabled = true), 2)
        machine.reset()
        assertEquals(0, machine.errorStreak)
    }
}
