package com.vivid.feature.streaming

import androidx.annotation.StringRes

/** Schweregrad eines Befundes aus dem Stream-Selbst-Check. */
enum class ConfigIssueSeverity {
    /** Blockiert den Go-Live (z. B. fehlende URL). */
    ERROR,

    /** Warnung — Stream startet, aber der Hinweis sollte sichtbar sein (z. B. fehlender Key). */
    WARNING,
}

/**
 * Ein einzelner Befund des Stream-Selbst-Checks.
 *
 * Die Meldung ist eine String-Ressource (i18n): [messageRes] ist die
 * Ressourcen-ID, [formatArgs] liefert die Platzhalter-Argumente (z. B. das
 * nicht unterstützte Protokoll). [prefixRes] markiert Befunde des optionalen
 * sekundären Ziels („Zweites Ziel: …“) und wird von der UI vorangestellt.
 */
data class StreamConfigIssue(
    val severity: ConfigIssueSeverity,
    @StringRes val messageRes: Int,
    val formatArgs: List<String> = emptyList(),
    @StringRes val prefixRes: Int = 0,
)

/**
 * Validiert die Stream-Konfiguration VOR dem Go-Live.
 *
 * Reine Funktion ohne Abhängigkeiten, damit sie trivial unit-testbar ist.
 * Die Meldungen liegen als String-Ressourcen vor (Deutsch = Default,
 * Englisch in `values-en`) — sie werden direkt im StreamingScreen angezeigt.
 */
object StreamConfigValidator {

    private val SUPPORTED_SCHEMES = listOf("rtmp", "rtmps", "srt")

    /**
     * Prüft das primäre ([streamUrl]/[streamKey]) und optionale sekundäre
     * ([secondaryStreamUrl]/[secondaryStreamKey]) Stream-Ziel und liefert alle
     * gefundenen Befunde.
     *
     * - Leere URL → Fehler („Keine Stream-URL konfiguriert").
     * - Nicht unterstütztes Protokoll (weder rtmp/rtmps noch srt) → Fehler.
     * - URL ohne Host (z. B. `rtmp://` oder nur ein Pfad) → Fehler.
     * - Fehlender Stream-Key bei RTMP/RTMPS → Warnung (manche Plattformen
     *   liefern den Key bereits in der URL).
     * - `streamUseTls = true` bei einer `srt://`-URL → Warnung (TLS betrifft nur RTMP).
     *
     * Das sekundäre Ziel ist optional: Eine leere sekundäre URL wird ignoriert
     * (Multi-Streaming deaktiviert), sobald sie aber gesetzt ist, gelten dieselben
     * Checks — die Befunde sind mit [R.string.stream_secondary_label] gekennzeichnet.
     */
    fun validate(
        streamUrl: String,
        streamKey: String,
        streamUseTls: Boolean,
        secondaryStreamUrl: String = "",
        secondaryStreamKey: String = "",
        secondaryStreamUseTls: Boolean = false,
    ): List<StreamConfigIssue> {
        val issues = validateTarget(
            url = streamUrl,
            key = streamKey,
            useTls = streamUseTls,
            required = true,
            prefixRes = 0,
        ).toMutableList()
        issues += validateTarget(
            url = secondaryStreamUrl,
            key = secondaryStreamKey,
            useTls = secondaryStreamUseTls,
            required = false,
            prefixRes = R.string.stream_secondary_label,
        )
        return issues
    }

    /**
     * Validiert ein einzelnes Stream-Ziel. [required] = true nur für das primäre
     * Ziel (leere URL = harter Fehler); das optionale sekundäre Ziel wird bei
     * leerer URL still ignoriert.
     */
    private fun validateTarget(
        url: String,
        key: String,
        useTls: Boolean,
        required: Boolean,
        @StringRes prefixRes: Int,
    ): List<StreamConfigIssue> {
        val issues = mutableListOf<StreamConfigIssue>()
        val trimmedUrl = url.trim()

        if (trimmedUrl.isEmpty()) {
            if (required) {
                issues += StreamConfigIssue(
                    ConfigIssueSeverity.ERROR,
                    R.string.stream_error_no_url,
                    prefixRes = prefixRes,
                )
            }
            return issues
        }

        val scheme = trimmedUrl.substringBefore("://").lowercase()
        if (scheme !in SUPPORTED_SCHEMES) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.ERROR,
                R.string.stream_error_bad_scheme,
                formatArgs = listOf(scheme),
                prefixRes = prefixRes,
            )
        }

        val host = hostOf(trimmedUrl)
        if (host.isNullOrBlank()) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.ERROR,
                R.string.stream_error_no_host,
                prefixRes = prefixRes,
            )
        }

        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty() && scheme.startsWith("rtmp")) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.WARNING,
                R.string.stream_error_no_key,
                prefixRes = prefixRes,
            )
        }

        if (useTls && scheme == "srt") {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.WARNING,
                R.string.stream_error_srt_tls,
                prefixRes = prefixRes,
            )
        }

        return issues
    }

    /** Extrahiert den Host aus einer Stream-URL (vor Port/Pfad/Query). */
    private fun hostOf(url: String): String? {
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = "")
        val authority = withoutScheme.substringBefore("/").substringBefore("?")
        val host = authority.substringBefore(":").trim()
        return host.ifEmpty { null }
    }
}
