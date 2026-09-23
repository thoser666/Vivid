package com.vivid.core.startup

/**
 * Reine Entscheidung des Sentry Session Replay als **Error-Replay**:
 *
 * Vivid zeichnet keine Sessions auf — das Replay dient ausschließlich der
 * Crash-Diagnose. Dafür konfiguriert [plan] die Sentry-Optionen so, dass
 *
 *  - Fehler-Replays aktiv sind ([errorSampleRate] = 1.0): Bei jedem gemeldeten
 *    Fehler werden die letzten ~30 Sekunden vor dem Fehler (lokal gepuffert,
 *    Maskierung aktiv) mitgeliefert.
 *  - Dauerhafte Session-Replays deaktiviert sind ([sessionSampleRate] = 0.0):
 *    Ohne Fehler wird nichts geschnitten und nichts hochgeladen.
 *
 * Der Opt-out-Toggle („Fehlerberichte senden (Sentry)“) gilt auch für das
 * Replay — auf zwei Ebenen:
 *  1. **Konfiguration**: [plan] setzt bei ausgeschaltetem Reporting beide
 *     Rates auf 0 und verlangt kein Buffering — es wird gar nichts geschnitten.
 *  2. **Laufzeit**: [runtimeAction] gibt für jeden Settings-Stand die Aktion
 *     zurück, die die App an der Replay-API ausführt (Buffering starten bzw.
 *     stoppen). Sie ist die Rückfallebene gegen SDK-Rückfälle, bei denen das
 *     Buffering unabhängig von den Rates liefe ( beforeSend filtert Replays
 *     nicht — sie sind eigene Envelopes).
 *
 * FOSS-Build und Startup-Safe-Mode initialisieren Sentry gar nicht — Replay
 * ist damit dort strukturell ausgeschlossen (kein eigener Pfad nötig).
 */
object SentryReplayPolicy {

    /**
     * Error-Replay-Rate: 1.0 = jedes gemeldete Fehler-Event bekommt sein
     * Retro-Replay (der Puffer ist lokal, Kosten nur bei tatsächlichem Fehler).
     */
    const val ERROR_SAMPLE_RATE: Double = 1.0

    /**
     * Session-Replay-Rate: 0.0 = nie dauerhaft mitschneiden (Privacy by
     * default; Quota-schonend).
     */
    const val SESSION_SAMPLE_RATE: Double = 0.0

    /**
     * Der Replay-Konfigurationsstand für einen Sentry-Opt-out-Stand.
     *
     * @param errorSampleRate Wert für `SentryReplayOptions.onErrorSampleRate`.
     * @param sessionSampleRate Wert für `SentryReplayOptions.sessionSampleRate`.
     * @param startBuffering true = die App startet das Puffering aktiv an der
     *   Replay-API (`Sentry.replay().startBuffering()`); false = das Buffering
     *   bleibt/stirbt gestoppt.
     */
    data class Plan(
        val errorSampleRate: Double,
        val sessionSampleRate: Double,
        val startBuffering: Boolean,
    )

    /** Konfiguration für [sentryEnabled] (Error-Replay an/aus). */
    fun plan(sentryEnabled: Boolean): Plan =
        if (sentryEnabled) {
            Plan(ERROR_SAMPLE_RATE, SESSION_SAMPLE_RATE, startBuffering = true)
        } else {
            Plan(0.0, 0.0, startBuffering = false)
        }

    /**
     * Laufzeit-Aktion für den Opt-out-Stand [sentryEnabled]: REPLAY_START
     * (Pufferung starten) bei „an“, REPLAY_STOP (Pufferung stoppen) bei
     * „aus“ und NONE, wenn der Stand sich gegenüber [previousEnabled] nicht
     * geändert hat (kein Flackern beim Settings-Recollect).
     */
    enum class RuntimeAction { REPLAY_START, REPLAY_STOP, NONE }

    fun runtimeAction(sentryEnabled: Boolean, previousEnabled: Boolean?): RuntimeAction =
        when {
            previousEnabled == null -> if (sentryEnabled) RuntimeAction.REPLAY_START else RuntimeAction.REPLAY_STOP
            sentryEnabled == previousEnabled -> RuntimeAction.NONE
            sentryEnabled -> RuntimeAction.REPLAY_START
            else -> RuntimeAction.REPLAY_STOP
        }
}
