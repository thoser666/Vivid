package com.vivid.core.log

/**
 * Schwärzt sensible Werte in Log-Zeilen, bevor sie in den [LogBuffer] gelangen:
 * Stream-Keys, Passwörter, OAuth-/API-Tokens und eingebettete URL-Zugangsdaten.
 *
 * Konvention im Projekt: „nie ins Log“ für Tokens/Keys (z. B. Kick-Auth). Dieser
 * Filter ist die technische Absicherung, damit auch versehentlich geloggte
 * Werte nie im In-App-Log oder Export landen.
 */
object LogRedactor {

    /** `key=wert`, `KEY: wert`, `password=wert`, `stream_key=…`, `token=…` usw. */
    private val KEY_VALUE = Regex(
        """(?i)((?:stream[_-]?key|api[_-]?key|client[_-]?secret|password|passwd|secret|token|oauth|authorization|access[_-]?token|refresh[_-]?token|key)\s*[=:]\s*)((?!bearer\b)[^\s,;&]+)""",
    )

    /** `Bearer <token>`-Header (vor KEY_VALUE, damit `Authorization: Bearer` nicht den Header frisst). */
    private val BEARER = Regex("""(?i)(bearer\s+)([A-Za-z0-9._~+/=-]+)""")

    /** `scheme://user:secret@host` — URL mit eingebetteten Zugangsdaten. */
    private val USERINFO = Regex("""([a-z][a-z0-9+.-]*://)([^@/\s]+)@""")

    /** Lange Hex-Werte (≥ 32 Zeichen) — typisch für Stream-Keys/Signaturen. */
    private val LONG_HEX = Regex("""\b[0-9a-fA-F]{32,}\b""")

    fun redact(message: String): String {
        var out = KEY_VALUE.replace(message) { match ->
            match.groupValues[1] + "***"
        }
        out = BEARER.replace(out) { match ->
            match.groupValues[1] + "***"
        }
        out = USERINFO.replace(out) { match ->
            match.groupValues[1] + "***@"
        }
        out = LONG_HEX.replace(out, "***")
        return out
    }
}