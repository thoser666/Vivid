package com.vivid.feature.streaming

import android.os.Handler
import android.os.Looper

/**
 * Kamera-Instanz, die RootEncoders Hardware-Gesichtserkennung bedient
 * (`Camera2Base.enableFaceDetection` bzw. `Camera2ApiManager` für den
 * Idle-Preview-Pfad).
 *
 * Android-frei gehalten, damit der Lebenszyklus ohne RootEncoder auf der JVM
 * testbar ist; die Engine liefert die Adapter.
 */
internal interface FaceDetectable {

    /**
     * Aktiviert die Erkennung und liefert pro Capture-Frame die Boxen.
     *
     * @return false, wenn die Kamera **noch nicht offen** ist (RootEncoder
     * braucht das gebaute Input-Surface) oder der Gerätetest keinen Face-Modus
     * bereitstellt — dann ist ein späterer Versuch nötig bzw. die Capability
     * endgültig nicht erfüllt.
     */
    fun enableFaceDetection(onFaces: (FaceFrame) -> Unit): Boolean

    /** Deaktiviert die Erkennung wieder (auch wenn sie nie lief). */
    fun disableFaceDetection()
}

/**
 * P2 — Lebenszyklus der Hardware-Gesichtserkennung (Skizze §5, Risiko #1).
 *
 * `enableFaceDetection` liefert erst nach dem Öffnen der Kamera einen Erfolg
 * (RootEncoder braucht das gebaute Input-Surface und die
 * `AVAIL_FACE_DETECT_MODES`-Caches des Kameraschlüssels). Weil der Aufruf an
 * mehreren, teils asynchron erreichten Stellen passiert (Idle-Preview nach
 * `openCameraId`, Stream nach `startStream`), versucht der Producer das
 * Einschalten bis zu [maxAttempts] Mal mit [retryDelayMs] Abstand nach — die
 * Versuche eines abgelösten Ziels werden über eine Generation verworfen,
 * sodass ein später Nachholen nie ein altes Ziel wiederbelebt.
 *
 * Erst mit [setDesired] auf `true` wird tatsächlich eingeschaltet; das Ziel
 * darf aber schon vorher angehängt sein (Quell- und Toggle-Wechsel laufen
 * dann ohne Re-Attach).
 *
 * Das Ergebnis der Capability-Prüfung wird als [available] gemeldet
 * (`null` = noch nie versucht): Emulatoren und Rückkameras ohne
 * `STATISTICS_FACE_DETECT_MODE` melden hier `false`, die manuellen Zonen
 * bleiben davon unberührt (Skizze §8).
 */
internal class FaceDetectionProducer(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit = ::scheduleFaceRetry,
    private val cancelScheduled: () -> Unit = ::cancelFaceRetry,
    private val onAvailableChanged: (Boolean?) -> Unit = {},
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {

    private var target: FaceDetectable? = null
    private var onFaces: ((FaceFrame) -> Unit)? = null
    private var attempts = 0
    private var generation = 0
    private var desired = false

    /**
     * Ergebnis des letzten Einschaltversuchs: `null` noch nie versucht,
     * `true` Erkennung lief (oder lief), `false` Capability nicht erfüllt.
     */
    var available: Boolean? = null
        private set

    /** Ziel angehängt (Kamera-Instanz referenziert, unabhängig vom Wunsch). */
    val isAttached: Boolean get() = target != null

    /** Ob aktuell eingeschaltet (Wunsch **und** letzter Versuch erfolgreich). */
    val isActive: Boolean get() = desired && available == true

    /**
     * Setzt den Soll-Zustand (Master- **und** P2-Toggle der Engine). Ohne
     * Ziel wird nur der Wunsch gemerkt; mit Ziel wird sofort ein-/geschaltet.
     */
    fun setDesired(enabled: Boolean) {
        if (desired == enabled) return
        desired = enabled
        if (enabled) {
            if (target != null) {
                attempts = 0
                tryEnable()
            }
        } else {
            target?.let { runCatching { it.disableFaceDetection() } }
        }
    }

    /**
     * Hängt ein Ziel an. Ein vorheriges Ziel wird zuerst abgeschaltet
     * (Quell- bzw. Kamerawechsel); eingeschaltet wird nur bei [setDesired].
     */
    fun attach(newTarget: FaceDetectable, onFaces: (FaceFrame) -> Unit) {
        detach()
        target = newTarget
        this.onFaces = onFaces
        attempts = 0
        if (desired) tryEnable()
    }

    /** Trennt das Ziel, bricht laufende Nachversuche ab und meldet `null`. */
    fun detach() {
        generation += 1
        cancelScheduled()
        target?.let { runCatching { it.disableFaceDetection() } }
        target = null
        onFaces = null
        attempts = 0
        setAvailable(null)
    }

    private fun tryEnable() {
        if (!desired) return
        val current = target ?: return
        val callback = onFaces ?: return
        val expected = generation
        val enabled = runCatching { current.enableFaceDetection(callback) }.getOrDefault(false)
        if (expected != generation) return
        setAvailable(enabled)
        if (enabled) {
            attempts = 0
            return
        }
        attempts += 1
        if (attempts >= maxAttempts) return
        schedule(retryDelayMs) {
            if (expected == generation) tryEnable()
        }
    }

    private fun setAvailable(value: Boolean?) {
        if (available == value) return
        available = value
        onAvailableChanged(value)
    }

    private companion object {
        /** Nachversuche, bevor die Capability als nicht erfüllt gilt. */
        const val MAX_ATTEMPTS = 10

        /** Abstand der Nachversuche (Kamera-Öffnung braucht typ. einen Frame). */
        const val RETRY_DELAY_MS = 200L
    }
}

/**
 * Nachversuche auf dem Haupt-Handler — **lazily** erzeugt und mit
 * `runCatching` belegt: die Streaming-Engine-Tests laufen auf der reinen JVM
 * ohne Looper, und ein fehlender Handler darf den ersten Versuch (der im
 * Erfolgsfall eh keinen Retry braucht) nicht abbrechen.
 */
private var faceRetryHandler: Handler? = null

private fun faceRetryHandlerOrNull(): Handler? = runCatching {
    faceRetryHandler ?: Handler(Looper.getMainLooper()).also { faceRetryHandler = it }
}.getOrNull()

internal fun scheduleFaceRetry(delayMs: Long, action: () -> Unit) {
    val handler = faceRetryHandlerOrNull() ?: return
    runCatching { handler.postDelayed({ runCatching(action) }, delayMs) }
}

internal fun cancelFaceRetry() {
    runCatching { faceRetryHandlerOrNull()?.removeCallbacksAndMessages(null) }
}
