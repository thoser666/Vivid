package com.vivid.core.startup

/**
 * Reine Entscheidung der Crash-Schleifen-Erkennung (Startup-Safe-Mode):
 *
 * Vivid zählt bei jedem Startversuch einen Zähler hoch und setzt ihn zurück,
 * sobald die App die UI tatsächlich erreicht ([HEALTHY_RESET]). Erreicht der
 * Zähler [SAFE_MODE_THRESHOLD], startet die App statt der vollen UI in die
 * Diagnose-Activity (`CrashDiagnosticsActivity`) — sie zeigt den letzten
 * persistierten Crash aus dem In-App-Log an (Anzeigen/Kopieren/Teilen),
 * ohne Hilt, DataStore oder Remote-Server zu brauchen. Damit bleibt die
 * Diagnose unabhängig von jeder möglichen Crash-Ursache lauffähig.
 *
 * Auf dem Datenträger liegt nur der Zähler (`startup_attempts`, eine Zahl);
 * die gesamte Logik hier ist pure und vollständig testbar.
 */
object CrashLoopPolicy {

    /**
     * Anzahl gescheiterter Starts in Folge, ab der der Safe-Mode greift.
     * **Zwei**: Ein einzelner Crash kann viele Ursachen haben (OOM durchs
     * System, Kill beim Kamera-Zugriff …) — erst der zweite Erfolglos-Start
     * hintereinander ist eine belastbare Crash-Schleife.
     */
    const val SAFE_MODE_THRESHOLD = 2

    /**
     * Zählerstand, der als „gesund“ gilt: Die UI war erreicht, die
     * Crash-Schleifen-Verdachtschaft ist vollständig zurückgesetzt.
     */
    const val HEALTHY_RESET = 0

    /** Entscheidung eines Startversuchs. */
    enum class Decision {
        /** Normaler Start: volle UI (MainActivity-Komposition). */
        NORMAL,

        /** Safe-Mode: Diagnose-Activity statt voller UI. */
        SAFE_MODE,
    }

    /**
     * Entscheidung für einen Startversuch mit dem aktuellen Zählerstand
     * (vor dem Inkrement — [onStartupAttempt] beschreibt die Reihenfolge).
     *
     * Semantik: Der Zähler zählt die **gescheiterten** Starts in Folge. Sind
     * bereits [SAFE_MODE_THRESHOLD] gescheitert, greift der Safe-Mode für
     * diesen Start (Start Nr. 3 nach zwei Crashes). `>=` statt `==`, damit
     * ein überlauffreundlicher Zählerstand den Safe-Mode nie verlässt.
     */
    fun decide(attemptsBeforeIncrement: Int): Decision =
        if (attemptsBeforeIncrement >= SAFE_MODE_THRESHOLD) Decision.SAFE_MODE
        else Decision.NORMAL

    /**
     * Zähler-Transition für einen Startversuch: `attempts + 1` — der Aufrufer
     * persistiert das Ergebnis sofort (crash-resistent: Stirbt die App später
     * in diesem Versuch, bleibt der erhöhte Stand erhalten).
     */
    fun onStartupAttempt(attemptsBeforeIncrement: Int): Int = attemptsBeforeIncrement + 1

    /**
     * Zähler-Transition, wenn die UI erreicht wurde (MainActivity `RESUMED`):
     * vollständiger Reset auf [HEALTHY_RESET].
     */
    fun onUiReached(): Int = HEALTHY_RESET
}
