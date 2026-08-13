package com.vivid.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsQrCodeParserTest {

    @Test
    fun `parses current obsws uri with password`() {
        val result = ObsQrCodeParser.parse("obsws://192.168.1.50:4455/secret123")

        assertTrue(result is ObsQrCodeParseResult.Success)
        val data = (result as ObsQrCodeParseResult.Success).data
        assertEquals("192.168.1.50", data.host)
        assertEquals(4455, data.port)
        assertEquals("secret123", data.password)
    }

    @Test
    fun `parses obsws uri without password`() {
        val result = ObsQrCodeParser.parse("obsws://192.168.1.50:4455")

        assertTrue(result is ObsQrCodeParseResult.Success)
        val data = (result as ObsQrCodeParseResult.Success).data
        assertEquals("192.168.1.50", data.host)
        assertEquals(4455, data.port)
        assertEquals("", data.password)
    }

    @Test
    fun `decodes percent-encoded password`() {
        // OBS percent-encodes das Passwort (QUrl::toPercentEncoding)
        val result = ObsQrCodeParser.parse("obsws://obs.local:4455/pa%20ss%2Fw%C3%B6rt")

        assertTrue(result is ObsQrCodeParseResult.Success)
        val data = (result as ObsQrCodeParseResult.Success).data
        assertEquals("pa ss/wört", data.password)
    }

    @Test
    fun `parses legacy obswebsocket uri`() {
        val result = ObsQrCodeParser.parse("obswebsocket://192.168.1.50:4455")

        assertTrue(result is ObsQrCodeParseResult.Success)
        val data = (result as ObsQrCodeParseResult.Success).data
        assertEquals("192.168.1.50", data.host)
        assertEquals(4455, data.port)
    }

    @Test
    fun `parses legacy obswebsocket pipe format with password`() {
        val result = ObsQrCodeParser.parse("obswebsocket|[192.168.1.50]:[4455]|[pw]")

        assertTrue(result is ObsQrCodeParseResult.Success)
        val data = (result as ObsQrCodeParseResult.Success).data
        assertEquals("192.168.1.50", data.host)
        assertEquals(4455, data.port)
        assertEquals("pw", data.password)
    }

    @Test
    fun `accepts scheme case-insensitively`() {
        val result = ObsQrCodeParser.parse("OBSWS://OBS.HOME:4455/x")

        assertTrue(result is ObsQrCodeParseResult.Success)
        assertEquals("OBS.HOME", (result as ObsQrCodeParseResult.Success).data.host)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val result = ObsQrCodeParser.parse("  obsws://192.168.1.50:4455/pw  ")

        assertTrue(result is ObsQrCodeParseResult.Success)
        assertEquals("192.168.1.50", (result as ObsQrCodeParseResult.Success).data.host)
    }

    @Test
    fun `rejects empty input`() {
        val result = ObsQrCodeParser.parse("   ")

        assertTrue(result is ObsQrCodeParseResult.Error)
        assertTrue((result as ObsQrCodeParseResult.Error).message.isNotBlank())
    }

    @Test
    fun `rejects unsupported scheme`() {
        val result = ObsQrCodeParser.parse("https://example.com")

        assertTrue(result is ObsQrCodeParseResult.Error)
    }

    @Test
    fun `rejects garbage input`() {
        val result = ObsQrCodeParser.parse("das ist kein qr code")

        assertTrue(result is ObsQrCodeParseResult.Error)
    }

    @Test
    fun `rejects invalid port`() {
        val result = ObsQrCodeParser.parse("obsws://192.168.1.50:notaport/pw")

        assertTrue(result is ObsQrCodeParseResult.Error)
    }
}
