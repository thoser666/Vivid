package com.vivid.irlbroadcaster

import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions

/**
 * Erwartete Netzwerk-Betriebszustände — vollständig qualifizierte Exception-Typen,
 * die **keinen Produktdefekt** beschreiben, sondern die Umgebung: Laptop aus,
 * WLAN gewechselt, Zielhost offline. Vivid behandelt sie gefangen und
 * fehlertolerant (Zustand wird zurückgesetzt, kein Throw an den Aufrufer), der
 * Nutzer sieht sie im In-App-Log. Für Sentry sind sie reine Triage-Last.
 *
 * Jeder Eintrag ist durch ein eigenes, bereits attribuiertes Sentry-Issue
 * belegt — die Liste wächst nur mit einem echten Issue als Beleg:
 *
 *  - `java.net.NoRouteToHostException`               — #267 (VIVID-3P, 04.10.2026)
 *  - `java.net.ConnectException`                     — #217 (`/192.168.1.100:4455`)
 *  - `java.net.SocketTimeoutException`               — #219, #224, #244
 *  - `java.net.UnknownHostException`                 — #220 (`"srt"`-Hostname)
 *  - `io.ktor.client.plugins.ConnectTimeoutException` — #248 (`ws://…:4455`)
 *
 * **Bewusst NICHT enthalten** (jede dieser Klassen ist eine Falle):
 *
 *  - `java.net.BindException` (#228, VIVID-37/3E) ist selbst ein `SocketException`
 *    und damit strukturell Geschwister von `ConnectException`/`NoRouteToHostException`.
 *    Ein Basisklassen-Abgleich auf `SocketException` würde den **echten Start-Crash**
 *    EADDRINUSE verschlucken (Registry: `REMOTE-EADDRINUSE-STARTUP`). Deshalb wird
 *    ausschließlich der **exakte** Typ verglichen, nie die Oberklasse.
 *  - `ClosedReadChannelException` (#242): Der Server hat mitten im Protokoll
 *    geschlossen — das kann ein Serverfehler sein und bleibt deshalb sichtbar.
 *  - `JobCancellationException` (#218): Verschluckt echte Coroutine-Bugs, weil fast
 *    jede abgebrochene Koroutine damit endet.
 *
 * Diese Klassen stehen in keinem Kanon-Crash, sind aber auch kein gemeldeter
 * Betriebszustand — sie laufen im Zweifel **durch** (fail-safe, s.u.).
 */
internal val EXPECTED_TRANSPORT_EXCEPTIONS: Set<String> = setOf(
    "java.net.NoRouteToHostException",
    "java.net.ConnectException",
    "java.net.SocketTimeoutException",
    "java.net.UnknownHostException",
    "io.ktor.client.plugins.ConnectTimeoutException",
)

/** Entscheidung über ein einzelnes Sentry-Event. */
internal enum class SentryEventDisposition {
    /** Das Event wird unverändert weitergereicht (Default jeder Unsicherheit). */
    KEEP,

    /** Reiner erwarteter Betriebszustand — nicht an Sentry, kein Issue im Watchdog. */
    DROP_EXPECTED_TRANSPORT,
}

/**
 * Die Typnamen der Cause-Kette, von außen nach innen. Die Kette endet an einer
 * zyklischen `cause`-Verkettung (malicious/versehentlich selbstbezügliche
 * Throwables) und ist deshalb tiefenbegrenzt.
 *
 * Bewusst über [Throwable] und **nicht** über `SentryEvent.getExceptions()`:
 * auf dem `beforeSend`-Pfad ist `exceptions` noch `null` — die Kette entsteht erst
 * bei der Serialisierung. Empirisch belegt (Sentry 8.54, `setThrowable` auf einem
 * frischen `SentryEvent`): `getExceptions() == null`, `getThrowable()` dagegen
 * trägt die volle Kette. Ein Filter auf `exceptions` wäre damit **stumm
 * wirkungslos** — er hätte das Issue-Rauschen scheinbar beseitigt, ohne ein
 * einziges Event zu filtern.
 */
internal fun throwableTypeChain(throwable: Throwable?, maxDepth: Int = 16): List<String> {
    val chain = mutableListOf<String>()
    var current = throwable
    while (current != null && chain.size < maxDepth) {
        chain += current.javaClass.name
        val next = current.cause
        // Selbstbezügliche Kette (cause == self) würde sonst endlos laufen.
        if (next === current) break
        current = next
    }
    return chain
}

/**
 * Pure Entscheidung, ob ein Sentry-Event den erwarteten Netzwerk-Betriebszustand
 * beschreibt. Drei unabhängige Sicherheitsnetze, jedes für sich ausreichend, um
 * **keinen echten Befund** zu verlieren:
 *
 *  1. **FATAL ist tabu, und ein unbekanntes Level ebenso.** Nur ein unbehandelter
 *     Prozess-Crash ist FATAL. Der vom Sentry-Android-SDK beim Init gepflanzte
 *     `SentryTimberTree` mappt Timber `ERROR` auf `SentryLevel.ERROR`
 *     (bytecode-verifiziert: Priorität 6 → ERROR, 7/ASSERT → FATAL). Damit sind
 *     gefangene `Timber.e`-Fehler per Konstruktion nie FATAL — die Grenze trennt
 *     echte Crashes von Betriebszuständen. Fehlt das Level (`null`), ist nicht
 *     entscheidbar, ob es FATAL ist — dann bleibt das Event ebenfalls stehen.
 *     Filterbar ist damit nur, was sich **positiv** als Nicht-FATAL ausweist.
 *  2. **Nur die komplette Kette.** Gefiltert wird nur, wenn **jedes** Glied der
 *     Cause-Kette ein erlaubter Transportzustand ist. Ein einziger unbekannter
 *     Typ in der Kette lässt das Event durch. Ein echter Bug, der sich als
 *     Transportfehler tarnt, behält seinen falschen Anteil in der Kette.
 *  3. **Ohne Throwable bleibt alles.** Events ohne Throwable (Log-Events, eigene
 *     `Sentry.captureMessage`-Aufrufe) kennt diese Policy nicht und bleiben.
 *
 * **Bewusst nicht als Kriterium verwendet:** `Mechanism.isHandled()`. Der
 * `SentryTimberTree` ruft `captureEvent` **ohne** ein `Mechanism` auf; für
 * `captureEvent` gilt Sentry-Konvention `handled=false`. Eine Regel
 * „unbehandelt → behalten" würde damit **jedes** Timber-Event behalten und den
 * Filter wirkungslos machen. Ebenso untauglich ist `getExceptions()` (siehe
 * [throwableTypeChain]).
 *
 * **Bekannte Evidenzlücke:** Die tatsächliche Cause-Kette des #267-Events war am
 * konkreten Event nicht lesbar (lokaler Token ohne `project:read`), und Ktor
 * hüllt Connect-Fehler je nach Pfad in eigene Wrapper-Typen. Trifft die Kette einen
 * Wrapper, der nicht in [EXPECTED_TRANSPORT_EXCEPTIONS] steht, bleibt das Event
 * stehen — der Watchdog meldet es weiter. Das ist die gewollte Richtung: **zu
 * wenig filtern** (Rauschen bleibt) ist reparierbar, **zu viel filtern** (ein
 * echter Befund verschwindet) nicht.
 */
internal fun classifySentryEvent(
    level: SentryLevel?,
    throwable: Throwable?,
): SentryEventDisposition {
    // FATAL ist tabu, und ein **unbekanntes** Level ebenso: Ohne gesetztes Level
    // laesst sich nicht ausschliessen, dass es doch ein FATAL-Crash ist. Nur ein
    // explizit gesetztes Nicht-FATAL-Level berechtigt zum Filtern — fehlende
    // Information ist kein Freibrief.
    if (level == null || level == SentryLevel.FATAL) return SentryEventDisposition.KEEP
    val types = throwableTypeChain(throwable)
    if (types.isEmpty()) return SentryEventDisposition.KEEP
    return if (types.all { it in EXPECTED_TRANSPORT_EXCEPTIONS }) {
        SentryEventDisposition.DROP_EXPECTED_TRANSPORT
    } else {
        SentryEventDisposition.KEEP
    }
}

/**
 * Die `beforeSend`-Verkabelung für [VividApplication]: zuerst der bestehende
 * Opt-out, dann der Betriebszustands-Filter.
 *
 * Bewusst **komponiert** über [sentryBeforeSendCallback] statt den Opt-out
 * inline nachzubauen: Der Opt-out ist datenschutzrelevant, und
 * `check_sentry_optout_mapping.sh` weist ihn per R8-Mapping nach (C3–C6) — der
 * Nachweis trägt nur, solange genau diese Fabrik den Aufrufpfad bildet. Würde
 * [VividApplication] sie direkt durch einen eigenen Callback ersetzen, hätte sie
 * im Release-Build keinen Aufrufer mehr und R8 entfernte sie samt ihrem Nachweis.
 * Als aufgerufene Funktion bleibt sie dagegen referenziert und wird in den
 * Sentry-Aufrufpfad inlined.
 *
 * Die Reihenfolge der beiden Filter ist ansonsten beliebig, solange beide
 * erhalten bleiben — ein deaktivierter Opt-out verwirft **jedes** Event.
 * Der [io.sentry.Hint] wird durchgereicht, nicht verworfen.
 *
 * Hinweis zum Nachweisumfang: R8 weist damit die **Opt-out**-Logik nach, nicht
 * den Transport-Filter als eigenes Symbol. Das ist eine bewusste Grenze dieses
 * Guards (er ist ein Datenschutz-Nachweis, kein Triage-Nachweis) und in
 * docs/sentry-issues.md §5 Punkt 7 dokumentiert.
 */
internal fun sentryBeforeSendWithTransportFilter(
    isEnabled: () -> Boolean,
): SentryOptions.BeforeSendCallback {
    val optOutCallback = sentryBeforeSendCallback(isEnabled)
    return SentryOptions.BeforeSendCallback { event, hint ->
        optOutCallback.execute(event, hint)?.takeIf {
            classifySentryEvent(it.level, it.throwable) == SentryEventDisposition.KEEP
        }
    }
}

/** Nur für Tests/Doku: die Typkette eines Events als lesbare Liste. */
internal fun SentryEvent.exceptionTypeNames(): List<String> = throwableTypeChain(throwable)