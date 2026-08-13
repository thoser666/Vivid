package com.vivid.feature.streaming

/** Schweregrad eines Befundes aus dem Stream-Selbst-Check. */
enum class ConfigIssueSeverity {
    /** Blockiert den Go-Live (z. B. fehlende URL). */
    ERROR,

    /** Warnung — Stream startet, aber der Hinweis sollte sichtbar sein (z. B. fehlender Key). */
    WARNING,
}

/** Ein einzelner Befund des Stream-Selbst-Checks mit klarer, anzeigbarer Meldung. */
data class StreamConfigIssue(
    val severity: ConfigIssueSeverity,
    val message: String,
)

/**
 * Validiert die Stream-Konfiguration VOR dem Go-Live.
 *
 * Reine Funktion ohne Abhängigkeiten, damit sie trivial unit-testbar ist.
 * Die Meldungen sind bewusst auf Deutsch und erklären, was zu tun ist —
 * sie werden direkt im StreamingScreen angezeigt.
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
     * Checks — die Befunde sind mit „Zweites Ziel:" gekennzeichnet.
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
            label = "",
        ).toMutableList()
        issues += validateTarget(
            url = secondaryStreamUrl,
            key = secondaryStreamKey,
            useTls = secondaryStreamUseTls,
            required = false,
            label = "Zweites Ziel: ",
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
        label: String,
    ): List<StreamConfigIssue> {
        val issues = mutableListOf<StreamConfigIssue>()
        val trimmedUrl = url.trim()

        if (trimmedUrl.isEmpty()) {
            if (required) {
                issues += StreamConfigIssue(
                    ConfigIssueSeverity.ERROR,
                    "Keine Stream-URL konfiguriert. Bitte in den Einstellungen hinterlegen.",
                )
            }
            return issues
        }

        val scheme = trimmedUrl.substringBefore("://").lowercase()
        if (scheme !in SUPPORTED_SCHEMES) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.ERROR,
                "${label}Nicht unterstütztes Protokoll \"$scheme\". Erlaubt sind rtmp, rtmps und srt.",
            )
        }

        val host = hostOf(trimmedUrl)
        if (host.isNullOrBlank()) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.ERROR,
                "${label}Die Stream-URL enthält keinen gültigen Server-Host (z. B. rtmp://live.twitch.tv/app).",
            )
        }

        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty() && scheme.startsWith("rtmp")) {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.WARNING,
                "${label}Kein Stream-Key hinterlegt. Twitch, YouTube und Kick verlangen einen Key.",
            )
        }

        if (useTls && scheme == "srt") {
            issues += StreamConfigIssue(
                ConfigIssueSeverity.WARNING,
                "${label}TLS ist bei einer srt://-URL nicht anwendbar — die URL wird unverschlüsselt verwendet.",
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
