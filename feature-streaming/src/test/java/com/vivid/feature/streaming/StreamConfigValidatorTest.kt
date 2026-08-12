package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamConfigValidatorTest {

    @Test
    fun `blank url produces a single error`() {
        val issues = StreamConfigValidator.validate("   ", "key", streamUseTls = false)

        assertEquals(1, issues.size)
        val issue = issues[0]
        assertEquals(ConfigIssueSeverity.ERROR, issue.severity)
        assertTrue(issue.message.contains("Keine Stream-URL konfiguriert"))
    }

    @Test
    fun `blank url returns early without further checks`() {
        // Auch mit leerem Key und TLS darf nur der URL-Fehler erscheinen.
        assertEquals(1, StreamConfigValidator.validate("", "", streamUseTls = true).size)
    }

    @Test
    fun `unsupported scheme produces an error`() {
        val issues = StreamConfigValidator.validate("http://live.example/app", "key", streamUseTls = false)

        assertTrue(issues.any { it.severity == ConfigIssueSeverity.ERROR && it.message.contains("http") })
    }

    @Test
    fun `url without host produces an error`() {
        val issues = StreamConfigValidator.validate("rtmp:///app", "key", streamUseTls = false)

        assertTrue(issues.any { it.severity == ConfigIssueSeverity.ERROR && it.message.contains("Server-Host") })
    }

    @Test
    fun `valid rtmp url with key passes`() {
        val issues = StreamConfigValidator.validate(
            "rtmp://live.twitch.tv/app",
            "key-1",
            streamUseTls = false,
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `rtmps url with tls passes`() {
        val issues = StreamConfigValidator.validate(
            "rtmps://live.twitch.tv/app",
            "key-1",
            streamUseTls = true,
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `srt url passes with tls warning`() {
        val issues = StreamConfigValidator.validate(
            "srt://live.example:9000?streamid=key-1",
            "key-1",
            streamUseTls = true,
        )

        assertTrue(issues.none { it.severity == ConfigIssueSeverity.ERROR })
        assertTrue(issues.any { it.severity == ConfigIssueSeverity.WARNING && it.message.contains("TLS") })
    }

    @Test
    fun `missing stream key on rtmp is a warning not an error`() {
        val issues = StreamConfigValidator.validate(
            "rtmp://live.twitch.tv/app",
            "",
            streamUseTls = false,
        )

        assertEquals(1, issues.size)
        assertEquals(ConfigIssueSeverity.WARNING, issues[0].severity)
        assertTrue(issues[0].message.contains("Stream-Key"))
    }

    @Test
    fun `host extraction works with port and path`() {
        val issues = StreamConfigValidator.validate(
            "rtmp://localhost:1935/live",
            "key-1",
            streamUseTls = false,
        )

        assertTrue(issues.isEmpty())
    }
}
