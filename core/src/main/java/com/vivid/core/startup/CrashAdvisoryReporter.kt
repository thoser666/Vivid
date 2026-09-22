package com.vivid.core.startup

import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel

/**
 * Meldet eine [CrashAdvisory] deutlich im In-App-Log ([LogBuffer]):
 * WARN-Stufe, CRASH-Markierung ([LogEntry.isCrash]) — der Eintrag erscheint
 * damit als rote Zeile mit 💥 im Log-Viewer.
 *
 * Bewusst **nur** Ringpuffer, keine [com.vivid.core.log.LogStore]-Persistenz:
 * Die Advisory feuert bei jedem Start der betroffenen Version erneut — eine
 * Persistenz würde die Tagesdateien mit Wiederholungszeilen fluten. Wer sie
 * verpasst, sieht sie beim nächsten Start wieder.
 *
 * Bewusst **nicht** über Timber: Der Report braucht `isCrash = true`, was der
 * normale Log-Weg des Trees nicht ausdrückt, und bleibt von der Tree-
 * Konfiguration unabhängig (direkt nach dem Planting im `Application.onCreate`).
 */
class CrashAdvisoryReporter(private val buffer: LogBuffer) {

    /** Start-Prüfung: melden, falls [versionCode] einen bekannten Kandidaten trifft. */
    fun reportIfAny(
        versionCode: Int,
        candidates: List<KnownCrashCandidate> = CrashAdvisoryRegistry.KNOWN,
    ) {
        CrashAdvisoryRegistry.evaluate(versionCode, candidates)?.let(::report)
    }

    /** Schreibt die Meldung einmalig in den Puffer (idempotent pro Aufruf). */
    fun report(advisory: CrashAdvisory) {
        buffer.add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.WARN,
                tag = TAG,
                message = advisory.reportMessage(),
                isCrash = true,
            ),
        )
    }

    companion object {
        const val TAG = "CrashAdvisory"
    }
}
