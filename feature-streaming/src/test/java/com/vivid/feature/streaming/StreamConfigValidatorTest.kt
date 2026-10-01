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
        assertEquals(R.string.stream_error_no_url, issue.messageRes)
    }

    @Test
    fun `blank url returns early without further checks`() {
        // Auch mit leerem Key und TLS darf nur der URL-Fehler erscheinen.
        assertEquals(1, StreamConfigValidator.validate("", "", streamUseTls = true).size)
    }

    @Test
    fun `unsupported scheme produces an error`() {
        val issues = StreamConfigValidator.validate("http://live.example/app", "key", streamUseTls = false)

        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.messageRes == R.string.stream_error_bad_scheme &&
                    it.formatArgs == listOf("http")
            },
        )
    }

    @Test
    fun `url without host produces an error`() {
        val issues = StreamConfigValidator.validate("rtmp:///app", "key", streamUseTls = false)

        assertTrue(issues.any { it.severity == ConfigIssueSeverity.ERROR && it.messageRes == R.string.stream_error_no_host })
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
        assertTrue(issues.any { it.severity == ConfigIssueSeverity.WARNING && it.messageRes == R.string.stream_error_srt_tls })
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
        assertEquals(R.string.stream_error_no_key, issues[0].messageRes)
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

    // --- VIVID-3D (#220): Protokoll-Token im Host-Slot ---

    @Test
    fun `rtmp url whose host is the srt token is a blocking error vivid-3d`() {
        val issues = StreamConfigValidator.validate(
            "rtmp://srt/app/key-1",
            "key-1",
            streamUseTls = false,
        )

        assertEquals(1, issues.size)
        assertEquals(ConfigIssueSeverity.ERROR, issues[0].severity)
        assertEquals(R.string.stream_error_host_is_scheme, issues[0].messageRes)
        assertEquals(listOf("srt"), issues[0].formatArgs)
    }

    @Test
    fun `rtmps url whose host is a scheme token is a blocking error`() {
        val issues = StreamConfigValidator.validate(
            "rtmps://srt/app",
            "key-1",
            streamUseTls = true,
        )

        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.messageRes == R.string.stream_error_host_is_scheme
            },
        )
    }

    @Test
    fun `scheme token host in secondary target is a labeled error`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "rtmp://srt/app",
            secondaryStreamKey = "key-2",
        )

        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.prefixRes == R.string.stream_secondary_label &&
                    it.messageRes == R.string.stream_error_host_is_scheme
            },
        )
    }

    @Test
    fun `hosts that merely contain a token or are local still pass`() {
        assertTrue(
            StreamConfigValidator.validate("rtmp://srt.example.com/live", "key-1", streamUseTls = false).isEmpty(),
        )
        assertTrue(
            StreamConfigValidator.validate("rtmp://my-srt-server.local/live", "key-1", streamUseTls = false).isEmpty(),
        )
        assertTrue(
            StreamConfigValidator.validate("rtmp://localhost:1935/live", "key-1", streamUseTls = false).isEmpty(),
        )
    }

    @Test
    fun `uppercase token host is caught case-insensitively`() {
        val issues = StreamConfigValidator.validate(
            "rtmp://SRT/app",
            "key-1",
            streamUseTls = false,
        )

        assertTrue(
            issues.any {
                it.messageRes == R.string.stream_error_host_is_scheme &&
                    it.formatArgs == listOf("SRT")
            },
        )
    }

    // --- Sekundäres Ziel (Multi-Streaming) ---

    @Test
    fun `blank secondary url is ignored (multi-streaming disabled)`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `valid secondary target passes together with the primary`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "rtmp://second.example/app",
            secondaryStreamKey = "key-2",
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `invalid secondary scheme is an error labeled as second target`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "http://second.example/app",
            secondaryStreamKey = "key-2",
        )

        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.prefixRes == R.string.stream_secondary_label &&
                    it.messageRes == R.string.stream_error_bad_scheme &&
                    it.formatArgs == listOf("http")
            },
        )
    }

    @Test
    fun `secondary url without host is an error`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "rtmp:///app",
            secondaryStreamKey = "key-2",
        )

        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.ERROR &&
                    it.prefixRes == R.string.stream_secondary_label &&
                    it.messageRes == R.string.stream_error_no_host
            },
        )
    }

    @Test
    fun `missing secondary key is a warning not an error`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "rtmp://second.example/app",
            secondaryStreamKey = "",
        )

        assertTrue(issues.none { it.severity == ConfigIssueSeverity.ERROR })
        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.WARNING &&
                    it.prefixRes == R.string.stream_secondary_label &&
                    it.messageRes == R.string.stream_error_no_key
            },
        )
    }

    @Test
    fun `secondary srt url with tls shows a labeled warning`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "key-1",
            streamUseTls = false,
            secondaryStreamUrl = "srt://second.example:9000?streamid=key-2",
            secondaryStreamKey = "key-2",
            secondaryStreamUseTls = true,
        )

        assertTrue(issues.none { it.severity == ConfigIssueSeverity.ERROR })
        assertTrue(
            issues.any {
                it.severity == ConfigIssueSeverity.WARNING &&
                    it.prefixRes == R.string.stream_secondary_label &&
                    it.messageRes == R.string.stream_error_srt_tls
            },
        )
    }

    @Test
    fun `secondary issues do not appear when secondary url is blank`() {
        val issues = StreamConfigValidator.validate(
            streamUrl = "rtmp://live.example/app",
            streamKey = "",
            streamUseTls = false,
            secondaryStreamUrl = "",
            secondaryStreamKey = "key-2",
        )

        assertEquals(1, issues.size) // nur die primäre Key-Warnung
        assertTrue(issues.none { it.prefixRes != 0 })
    }
}
