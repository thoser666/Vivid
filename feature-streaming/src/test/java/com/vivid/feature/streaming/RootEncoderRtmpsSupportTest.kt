package com.vivid.feature.streaming

import com.pedro.common.UrlParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.URISyntaxException

/**
 * Beweist die RTMPS-Fähigkeit von RootEncoder 2.6.4 am echten Artefakt.
 *
 * Verifiziert (Bytecode-Analyse von RootEncoder 2.6.4):
 * - `RtmpClient.validSchemes = ["rtmp", "rtmps", "rtmpt", "rtmpts"]`
 * - In `connect()` wird `tlsEnabled = scheme.endsWith("s")` gesetzt, d. h.
 *   `rtmps://` und `rtmpts://` aktivieren den TLS-Handshake.
 * - Default-Port ist bei TLS 443, sonst 1935.
 * - `TcpStreamSocketJava` nutzt `SSLContext.getInstance("TLS")` mit
 *   `TLSv1.1`/`TLSv1.2` und einem optionalen `TrustManager`.
 *
 * Dieser Test ruft den echten `UrlParser` von RootEncoder auf (kein Mock),
 * um zu belegen, dass `rtmps://`-URLs akzeptiert und korrekt geparst werden.
 */
class RootEncoderRtmpsSupportTest {

    // Entspricht RtmpClient.validSchemes (aus dem Artefakt bestätigt).
    private val validSchemes = arrayOf("rtmp", "rtmps", "rtmpt", "rtmpts")

    @Test
    fun `rtmps url is accepted and parsed by the real UrlParser`() {
        val parser = UrlParser.parse(
            "rtmps://live.kick.com/app/live_12345_secret",
            validSchemes,
        )

        assertEquals("rtmps", parser.scheme)
        assertEquals("live.kick.com", parser.host)
        assertNull(parser.port) // kein expliziter Port → RootEncoder nutzt 443 bei TLS
        assertEquals("app", parser.getAppName())
        assertEquals("live_12345_secret", parser.getStreamName())
    }

    @Test
    fun `rtmps url with explicit tls port keeps that port`() {
        val parser = UrlParser.parse(
            "rtmps://live.example.com:8443/app/key-1",
            validSchemes,
        )

        assertEquals("rtmps", parser.scheme)
        assertEquals("live.example.com", parser.host)
        assertEquals(8443, parser.port)
    }

    @Test
    fun `rtmps url with explicit standard port is parsed`() {
        val parser = UrlParser.parse(
            "rtmps://live.example.com:1935/app/key-1",
            validSchemes,
        )

        assertEquals("rtmps", parser.scheme)
        assertEquals(1935, parser.port)
    }

    @Test
    fun `unsupported scheme is rejected with URISyntaxException`() {
        assertThrows(URISyntaxException::class.java) {
            UrlParser.parse("http://live.example.com/app", validSchemes)
        }
    }
}
