package com.vivid.feature.streaming

import timber.log.Timber

/**
 * Hält kamera-sitzungsberührende Operationen vom Close-Path fern und fängt den
 * Session-Closed-Race ab (#324 / VIVID-3W, `CameraCaptureSessionImpl.checkNotClosed`).
 *
 * Ursache des Fatals: Die Control-Fassade ([ActiveCameraControls]) wird von
 * mehreren Threads aufgelöst — UI-Gesten auf dem Main-Thread,
 * `onCameraOpened`-Callbacks auf dem Camera-Handler-Thread. parallel dazu
 * schließt `stopIdlePreview()` dieselbe Session (`closeCamera`). Trifft ein
 * Dispatch die bereits geschlossene Session, wirft `checkNotClosed`
 * `IllegalStateException("Session has been closed; further changes are illegal.")`
 * uncaught — genau der Stack von Sentry VIVID-3W (#324: Store-Build
 * `0.5.21-beta+5212`, Pixel 8 Pro, ein einzelner Fatal am 2026-10-22).
 *
 * Zwei getrennte Netzze:
 *
 * 1. **Reihenfolge:** [dispatch] und [beginClose]/[endClose] laufen unter
 *    demselben Monitor. Ein laufender Dispatch ist garantiert fertig, bevor
 *    die Flagge `closing` steht; danach dispatchiert nichts mehr auf die
 *    schließende Kamera. Der Monitor wird bewusst NICHT über den eigentlichen
 *    `closeCamera()`-Aufruf gehalten — würde die Library im Close auf ihren
 *    Camera-Handler-Thread warten, der wiederum auf einen Dispatch trifft,
 *    der auf den Monitor wartet, entstünde ein Deadlock.
 * 2. **Fangnetz:** Ein `IllegalStateException` mit der exakten
 *    Session-Closed-Botschaft wird an der Fassade geschluckt (Timber-Log,
 *    neutraler Rückgabewert). Jede andere `IllegalStateException` wird
 *    weitergereicht — die Fassade verschluckt keine echten Bugs (fail-loud,
 *    dasselbe Prinzip wie der SentryTransportFilter, docs/sentry-issues.md §5).
 */
internal class CameraSessionGuard {

    private val monitor = Any()
    private var closing = false

    /**
     * Führt [block] gegen die aktuelle Kamera aus: gesperrt gegen [beginClose],
     * neutral mit [default], während das Schließfenster offen ist, und mit
     * Unterdrückung der Session-Closed-`IllegalStateException` (#324).
     */
    fun <T> dispatch(default: T, block: () -> T): T = synchronized(monitor) {
        if (closing) return@synchronized default
        try {
            block()
        } catch (error: IllegalStateException) {
            if (!error.isClosedSession()) throw error
            Timber.d("Session-closed race suppressed at the camera facade: %s", error.message)
            default
        }
    }

    /** Markiert den Beginn des Schließens — Dispatches bleiben bis [endClose] neutral. */
    fun beginClose() = synchronized(monitor) { closing = true }

    /** Schließt das Fenster (try/finally in der Engine), damit die Flagge nicht kleben bleibt. */
    fun endClose() = synchronized(monitor) { closing = false }

    private fun IllegalStateException.isClosedSession(): Boolean =
        message?.startsWith("Session has been closed") == true
}
