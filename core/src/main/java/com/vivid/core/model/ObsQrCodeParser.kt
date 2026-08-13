package com.vivid.core.model

import java.net.URLDecoder

/**
 * Parser für OBS-WebSocket-QR-Code-Inhalte.
 *
 * OBS Studio (obs-websocket-Plugin) kodiert die Verbindungsdaten als QR-Code.
 * Die Formate (aus dem obs-websocket-Quellcode, `src/forms/ConnectInfo.cpp`):
 *
 *  - **aktuell (5.x):** `obsws://<host>:<port>/<password>` — das Passwort ist
 *    URL-percent-encoded; ohne Auth entfällt der Pfad (`obsws://<host>:<port>`)
 *  - **älter (4.x):** `obswebsocket://<host>:<port>` bzw.
 *    `obswebsocket|[host]:[port]|[password]`
 *
 * Der Parser akzeptiert bewusst alle drei Varianten und liefert ein
 * [ObsQrCodeParseResult] mit entweder den extrahierten Daten oder einer
 * verständlichen Fehlermeldung (für die UI).
 */
object ObsQrCodeParser {

    private val OBSWS_URI = Regex("""^obsws://([^:/?#]+):(\d{1,5})(?:/(.*))?$""", RegexOption.IGNORE_CASE)
    private val OBSWS_LEGACY_URI = Regex("""^obswebsocket://([^:/?#]+):(\d{1,5})(?:/(.*))?$""", RegexOption.IGNORE_CASE)
    private val OBSWS_LEGACY_PIPE = Regex("""^obswebsocket\|\[?([^\]|:]+)\]?:\[?(\d{1,5})\]?(?:\|\[?(.*?)\]?)?$""", RegexOption.IGNORE_CASE)

    /**
     * Parst den QR-Code-Inhalt [qrText] in OBS-Verbindungsdaten.
     *
     * @return [ObsQrCodeParseResult.Success] mit [ObsQrCodeData] oder
     *         [ObsQrCodeParseResult.Error] mit einer nutzerfreundlichen Meldung.
     */
    fun parse(qrText: String): ObsQrCodeParseResult {
        val trimmed = qrText.trim()
        if (trimmed.isEmpty()) {
            return ObsQrCodeParseResult.Error("QR-Code ist leer")
        }

        OBSWS_URI.matchEntire(trimmed)?.let { m ->
            return success(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        OBSWS_LEGACY_URI.matchEntire(trimmed)?.let { m ->
            return success(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
        OBSWS_LEGACY_PIPE.matchEntire(trimmed)?.let { m ->
            return success(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }

        return ObsQrCodeParseResult.Error(
            "Kein gültiger OBS-WebSocket-QR-Code. Erwartet: obsws://host:port/passwort " +
                "(oder obswebsocket://host:port).",
        )
    }

    private fun success(host: String, port: String, passwordEncoded: String?): ObsQrCodeParseResult {
        val password = passwordEncoded
            ?.takeIf { it.isNotBlank() }
            ?.let { decodePercent(it) }
            .orEmpty()
        return ObsQrCodeParseResult.Success(
            ObsQrCodeData(
                host = host,
                port = port.toInt(),
                password = password,
            ),
        )
    }

    private fun decodePercent(value: String): String {
        return try {
            URLDecoder.decode(value, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            // Nicht-percent-codierbarer Inhalt (z. B. ungültige Escape-Sequenz) —
            // dann den Rohwert übernehmen statt zu crashen.
            value
        }
    }
}

/** Ergebnis des QR-Code-Parsings: entweder Daten oder eine Fehlermeldung. */
sealed interface ObsQrCodeParseResult {
    data class Success(val data: ObsQrCodeData) : ObsQrCodeParseResult
    data class Error(val message: String) : ObsQrCodeParseResult
}
