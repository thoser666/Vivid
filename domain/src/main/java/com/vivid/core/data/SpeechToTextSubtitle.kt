package com.vivid.core.data

/**
 * Fehlerzustände der Untertitel-Erkennung (domain-neutral; die UI mappt auf
 * lokalisierte Strings). Die Konstanten der Android-SpeechRecognizer-Fehler
 * sind bewusst als Literale dokumentiert, damit domain android-frei bleibt.
 */
enum class SubtitleError {
    /** Kein Erkennungsdienst auf dem Gerät (`SpeechRecognizer.isRecognitionAvailable` = false). */
    UNAVAILABLE,

    /** ERROR_RECOGNIZER_BUSY (8) / ERROR_SERVICE_BUSY — transient, Restart mit Backoff. */
    BUSY,

    /** ERROR_NETWORK (2) / ERROR_NETWORK_TIMEOUT (1) / ERROR_SERVER (4). */
    NETWORK,

    /** ERROR_AUDIO (3) — z. B. Mikrofon belegt. */
    AUDIO,

    /** ERROR_INSUFFICIENT_PERMISSIONS (9) — RECORD_AUDIO fehlt. */
    PERMISSION,

    /** ERROR_SPEECH_TIMEOUT (6) / ERROR_NO_MATCH (7) — nichts Verständliches gehört. */
    SPEECH_TIMEOUT,

    /** ERROR_CLIENT (5) oder unbekannter Code. */
    GENERIC,
}

/** Anzeige-Zustand des Untertitel-Overlays (bereits gerollt; UI rendert nur). */
data class SubtitleState(
    /** Master-Toggle aus den Settings. */
    val enabled: Boolean = false,
    /** Erkennungsdienst verfügbar? false → Overlay zeigt den UNAVAILABLE-Hinweis. */
    val available: Boolean = true,
    /** Erkennung läuft aktiv (zwischen startListening und Fehler/Stop). */
    val listening: Boolean = false,
    /** Finalisierte Zeilen (älteste zuerst, max. Zeilenlimit der Maschine). */
    val lines: List<String> = emptyList(),
    /** Laufender Teilsatz (wird beim Finalisieren zur Zeile). */
    val partial: String = "",
    /** Letzter Fehler — null solange alles gesund ist. */
    val error: SubtitleError? = null,
) {
    /** Sichtbare Zeilen: finalisierte + laufender Teilsatz. */
    val displayLines: List<String> get() = if (partial.isBlank()) lines else lines + partial
}

/** Ergebnis einer Fehler-Transition: neuer Zustand + Restart-Entscheidung. */
data class SubtitleErrorTransition(
    val state: SubtitleState,
    /** true → Engine nach [SubtitleStateMachine.restartDelayMs] neu starten. */
    val restart: Boolean,
)

/**
 * Reine Untertitel-Logik (PARITY-Zeile 143 „Untertitel (Speech-to-Text)"): hält die
 * Zeilen-Rolling-Transitionen, entscheidet über Restart-Würdigkeit der Plattform-Fehler
 * und berechnet den exponentiellen Backoff (2 s, 4 s, 8 s … Cap 30 s, Reset nach
 * erfolgreichem Satz). Ohne Android- und Coroutines-Abhängigkeit (Domain-Modul ist
 * absichtlich frei davon) — der ViewModel in feature-widgets besitzt die Maschine,
 * spiegelt die Transitionen in einen StateFlow und führt die Engine-Aufrufe
 * (start/stop/delay) aus, die diese Klasse nur entscheidet.
 *
 * Fehlerbehandlung (Capability-aware Fallback):
 * - Kein Erkennungsdienst → [SubtitleError.UNAVAILABLE], kein Startversuch.
 * - Transiente Fehler (BUSY, NETWORK, SPEECH_TIMEOUT) → Restart mit Backoff.
 * - Permanente Fehler (AUDIO, PERMISSION, CLIENT) → Overlay mit Hinweis, kein Restart.
 */
class SubtitleStateMachine(
    /** Maximal sichtbare finalisierte Zeilen. */
    val maxLines: Int = DEFAULT_MAX_LINES,
) {
    /** Anzahl Fehler seit dem letzten erfolgreichen Satz (Backoff-Exponent). */
    var errorStreak: Int = 0
        private set

    /** Aktivieren: ohne Erkennungsdienst → UNAVAILABLE-Hinweis statt Startversuch. */
    fun onEnabled(current: SubtitleState, engineAvailable: Boolean): SubtitleState =
        current.copy(enabled = true, available = engineAvailable, listening = false, error = null)

    /** Deaktivieren/Reset: vollständiger Leerzustand. */
    fun reset(): SubtitleState {
        errorStreak = 0
        return SubtitleState()
    }

    /** Engine meldet aktives Hören. */
    fun onListening(current: SubtitleState): SubtitleState = current.copy(listening = true)

    /** Zwischenergebnis übernehmen. */
    fun onPartial(current: SubtitleState, text: String): SubtitleState =
        current.copy(partial = text.take(MAX_LINE_LENGTH))

    /** Endgültigen Satz übernehmen: als Zeile anhängen, Teilsatz verwerfen, Backoff zurücksetzen. */
    fun onFinal(current: SubtitleState, text: String): SubtitleState {
        val trimmed = text.trim()
        errorStreak = 0
        if (trimmed.isEmpty()) return current.copy(partial = "")
        val nextLines = (current.lines + trimmed.take(MAX_LINE_LENGTH)).takeLast(maxLines)
        return current.copy(lines = nextLines, partial = "", error = null)
    }

    /** Plattform-Fehler verarbeiten: neuer Zustand + Restart-Entscheidung. */
    fun onError(current: SubtitleState, code: Int): SubtitleErrorTransition {
        val error = mapError(code)
        errorStreak++
        val next = current.copy(listening = false, error = error, partial = "")
        return SubtitleErrorTransition(state = next, restart = isRetryable(error))
    }

    /** Verzögerung bis zum nächsten Restart nach [onError] (exponentiell, Cap 30 s). */
    fun restartDelayMs(): Long {
        val backoff = RETRY_BASE_MS shl (errorStreak - 1).coerceIn(0, 4)
        return backoff.coerceAtMost(RETRY_CAP_MS)
    }

    private fun mapError(code: Int): SubtitleError = when (code) {
        ERROR_NETWORK_TIMEOUT, ERROR_NETWORK, ERROR_SERVER -> SubtitleError.NETWORK
        ERROR_AUDIO -> SubtitleError.AUDIO
        ERROR_CLIENT -> SubtitleError.GENERIC
        ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH -> SubtitleError.SPEECH_TIMEOUT
        ERROR_RECOGNIZER_BUSY -> SubtitleError.BUSY
        ERROR_INSUFFICIENT_PERMISSIONS -> SubtitleError.PERMISSION
        else -> SubtitleError.GENERIC
    }

    private fun isRetryable(error: SubtitleError): Boolean = when (error) {
        SubtitleError.BUSY, SubtitleError.NETWORK, SubtitleError.SPEECH_TIMEOUT -> true
        SubtitleError.AUDIO, SubtitleError.PERMISSION, SubtitleError.GENERIC, SubtitleError.UNAVAILABLE -> false
    }

    companion object {
        const val DEFAULT_MAX_LINES = 2

        /** Zeilen-Länge begrenzen — Erkennungsergebnisse sind kurz, aber nicht immer. */
        const val MAX_LINE_LENGTH = 120

        private const val RETRY_BASE_MS = 2_000L
        private const val RETRY_CAP_MS = 30_000L

        // SpeechRecognizer ERROR_*-Konstanten (android.speech, hier als Literale).
        private const val ERROR_NETWORK_TIMEOUT = 1
        private const val ERROR_NETWORK = 2
        private const val ERROR_AUDIO = 3
        private const val ERROR_SERVER = 4
        private const val ERROR_CLIENT = 5
        private const val ERROR_SPEECH_TIMEOUT = 6
        private const val ERROR_NO_MATCH = 7
        private const val ERROR_RECOGNIZER_BUSY = 8
        private const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    }
}

/**
 * Abstraktion über die Plattform-Spracherkennung (Android SpeechRecognizer).
 * Implementierung in feature-widgets (`AndroidSpeechToTextEngine`); Tests stubben
 * dieses Interface. Rein Kotlin — keine Android-Importe im Domain-Modul.
 */
interface SpeechToTextEngine {
    /** true, wenn ein Erkennungsdienst vorhanden ist. */
    val isAvailable: Boolean

    fun setListener(listener: Listener?)

    /** Startet (oder startet neu) die Erkennung. Fehler kommen asynchron über [Listener]. */
    fun start()

    fun stop()

    /** Callbacks der Plattform-Erkennung, bereits auf Untertitel-Ereignisse reduziert. */
    interface Listener {
        /** Erkennung bereit / Mikrofon offen. */
        fun onListening() {}

        /** Zwischenergebnis (kann sich beliebig oft ändern). */
        fun onPartial(text: String) {}

        /** Endgültiger Satz. */
        fun onFinal(text: String) {}

        /** Plattform-Fehlercode (SpeechRecognizer ERROR_*-Literale). */
        fun onError(code: Int) {}
    }
}
