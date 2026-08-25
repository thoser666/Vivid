package com.vivid.core.log

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Testet den Ringpuffer: Kapazität, Reihenfolge, Leeren und die StateFlow-
 * Veröffentlichung für die UI.
 */
class LogBufferTest {

    private fun entry(message: String = "msg") = LogEntry(
        timestampMillis = 0L,
        level = LogLevel.INFO,
        tag = "Test",
        message = message,
    )

    @Test
    fun `add haengt hinten an und veroeffentlicht ueber den Flow`() = runTest {
        val buffer = LogBuffer(capacity = 3)
        buffer.add(entry("a"))
        buffer.add(entry("b"))

        assertEquals(listOf("a", "b"), buffer.entries.first().map { it.message })
        assertEquals(listOf("a", "b"), buffer.snapshot().map { it.message })
    }

    @Test
    fun `Ringpuffer verwirft die aeltesten Eintraege ueber der Kapazitaet`() {
        val buffer = LogBuffer(capacity = 3)
        (1..5).forEach { buffer.add(entry("m$it")) }

        assertEquals(listOf("m3", "m4", "m5"), buffer.snapshot().map { it.message })
        assertEquals(3, buffer.snapshot().size)
    }

    @Test
    fun `clear leert den Puffer und den Flow`() = runTest {
        val buffer = LogBuffer(capacity = 3)
        buffer.add(entry("a"))
        buffer.clear()

        assertTrue(buffer.snapshot().isEmpty())
        assertTrue(buffer.entries.first().isEmpty())
    }

    @Test
    fun `Standardkapazitaet betraegt 500`() {
        val buffer = LogBuffer()
        (1..600).forEach { buffer.add(entry("m$it")) }

        assertEquals(500, buffer.snapshot().size)
        assertEquals("m101", buffer.snapshot().first().message)
        assertEquals("m600", buffer.snapshot().last().message)
    }
}