package com.vivid.core.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Testet die Schwärzung sensibler Werte (Stream-Keys, Tokens, Passwörter) —
 * die Konvention „nie ins Log“ wird damit technisch abgesichert.
 */
class LogRedactorTest {

    @Test
    fun `stream key als key-Parameter wird geschwaerzt`() {
        val redacted = LogRedactor.redact("Connecting to rtmp://live.twitch.tv with key=1234567890abcdef1234567890abcdef")
        assertFalse(redacted.contains("1234567890abcdef1234567890abcdef"))
        assertEquals("Connecting to rtmp://live.twitch.tv with key=***", redacted)
    }

    @Test
    fun `stream_key mit Unterstrich wird geschwaerzt`() {
        val redacted = LogRedactor.redact("stream_key=deadbeefdeadbeefdeadbeefdeadbeef")
        assertFalse(redacted.contains("deadbeef"))
        assertEquals("stream_key=***", redacted)
    }

    @Test
    fun `password und token Werte werden geschwaerzt`() {
        assertEquals("password=***", LogRedactor.redact("password=hunter2"))
        assertEquals("token=***", LogRedactor.redact("token=abc123"))
        assertEquals("oauth: ***", LogRedactor.redact("oauth: xyz789"))
    }

    @Test
    fun `bearer token wird geschwaerzt`() {
        val redacted = LogRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abcdefghijklmnopqrstuvwxyz")
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz"))
        assertTrue(redacted.contains("Bearer ***"))
    }

    @Test
    fun `URL mit eingebetteten Zugangsdaten wird geschwaerzt`() {
        val redacted = LogRedactor.redact("connect to rtmp://user:supersecret@live.example.com/app")
        assertFalse(redacted.contains("supersecret"))
        assertTrue(redacted.contains("rtmp://***@live.example.com/app"))
    }

    @Test
    fun `langer Hex-Stream-Key wird geschwaerzt`() {
        val key = "0123456789abcdef0123456789abcdef0123456789abcdef"
        val redacted = LogRedactor.redact("Stream key: $key")
        assertFalse(redacted.contains(key))
    }

    @Test
    fun `normaler Text bleibt unveraendert`() {
        val message = "WebSocket connected to host:8080, stream started, 5 viewers"
        assertEquals(message, LogRedactor.redact(message))
    }

    @Test
    fun `kurze Woerter werden nicht geschwaerzt`() {
        // „key“ als Wortbestandteil (z. B. „keyboard“) darf nicht kaputtgehen.
        assertEquals("keyboard input ok", LogRedactor.redact("keyboard input ok"))
        assertNotEquals("***board input ok", LogRedactor.redact("keyboard input ok"))
    }
}