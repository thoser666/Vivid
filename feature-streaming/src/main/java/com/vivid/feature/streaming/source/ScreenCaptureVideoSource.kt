package com.vivid.feature.streaming.source

import android.app.Activity
import android.content.Intent
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiType
import timber.log.Timber

/**
 * Screen-Capture als echte Videoquelle (S2 des Moblin-Buckets „Screen Capture +
 * Video-Player als Videoquelle“).
 *
 * Die Quelle kapselt einen RootEncoder-[MultiDisplay] (MediaProjection +
 * VirtualDisplay + ImageReader, komplett von der Library verwaltet) und ergänzt
 * den [MediaProjection]-Consent-Flow, den die App vor dem Start durchlaufen muss:
 *
 * 1. [createConsentIntent] liefert den Consent-Intent (`createScreenCaptureIntent`)
 * 2. Die App startet ihn per Activity-Result; das Ergebnis wird über
 *    [onConsentResult] an die Quelle übergeben (`setIntentResult`)
 * 3. [start] bereitet die Encoder vor (Audio + Video) — ohne erteilten Consent
 *    liefert RootEncoder einen RuntimeException, daher wird vorher geprüft
 * 4. Die Engine streamt danach über [startStream] auf die einzelnen Ziele
 *
 * **Der Consent gilt pro Stream-Start (#326)**: Ab Android 14 ist das
 * `resultData` aus dem System-Dialog ein einmalig verwendbares Token — der
 * `MediaProjection` darf nur daraus erzeugt werden, ein zweiter Start mit
 * denselben Daten wirft `SecurityException` („Media projections require a
 * foreground service of type …“ / Reuse-Verbot). Deshalb ist der Consent nach
 * [start] und nach [stop] verbraucht: [requiresFreshConsent] meldet das, die
 * Engine und die UI holen vor jedem Start einen neuen Dialog ein.
 *
 * [start]/[stop] erfüllen das [VideoSource]-Interface (Quelle aktivieren/deaktivieren);
 * die eigentliche URL-Streaming-Steuerung bleibt bei der Engine (wie bei der Kamera).
 */
class ScreenCaptureVideoSource(
    private val display: MultiDisplay,
) : VideoSource {

    internal val glInterface get() = display.glInterface as? com.pedro.library.view.GlStreamInterface

    override val kind: VideoSourceKind = VideoSourceKind.SCREEN_CAPTURE

    /** true, solange die Screen-Capture-Quelle aktiv streamt. */
    override val isActive: Boolean
        get() = display.isStreaming

    /** true, wenn der MediaProjection-Consent bereits erteilt wurde. */
    var isConsentGranted: Boolean = false
        private set

    /**
     * true, sobald der aktuelle Consent benutzt wurde (Start oder Stop).
     *
     * Android 14+ verbietet die Wiederverwendung des `resultData`-Tokens; nach
     * einem Start/Stop ist der Consent damit wertlos (#326).
     */
    private var consentConsumed = false

    /**
     * true, wenn vor dem nächsten [start] ein neuer Consent-Dialog nötig ist.
     *
     * Gilt ohne erteilten Consent und nach jedem verbrauchten (Start/Stop).
     */
    fun requiresFreshConsent(): Boolean = !isConsentGranted || consentConsumed

    /** Liefert den MediaProjection-Consent-Intent (System-Dialog „Bildschirm übertragen“). */
    fun createConsentIntent(): Intent = display.sendIntent()

    /**
     * Übergibt das Ergebnis des Consent-Dialogs an die Quelle.
     *
     * @return true, wenn der Nutzer zugestimmt hat (RESULT_OK + Daten vorhanden).
     */
    fun onConsentResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) return false
        display.setIntentResult(resultCode, data)
        isConsentGranted = true
        // Frisches Token → wieder genau einen Start lang gültig (#326).
        consentConsumed = false
        return true
    }

    /**
     * Aktiviert die Quelle: bereitet Audio- und Video-Encoder vor.
     *
     * Ohne erteilten — oder mit verbrauchtem — Consent wird nicht präpariert
     * (RootEncoder würde sonst mit „You need send intent data before
     * startRecord or startStream“ werfen, bzw. das einmalige Token ablehnen).
     *
     * Eine [SecurityException] (z. B. fehlender FGS-Typ mediaProjection auf
     * Android 14+, VIVID-39/#221) wird gefangen und als `false` gemeldet — die
     * Engine routet das über `failStream` in den Failed-Zustand statt den
     * Prozess zu crashen.
     *
     * @return true, wenn beide Encoder bereit sind.
     */
    override fun start(): Boolean {
        if (requiresFreshConsent()) return false
        return try {
            display.prepareAudio() && display.prepareVideo()
        } catch (e: SecurityException) {
            Timber.e(e, "ScreenCaptureVideoSource: preparation denied (SecurityException)")
            false
        } finally {
            // Ab hier ist das Token verbraucht — auch wenn die Vorbereitung
            // scheitert, ist ein neuer Dialog nötig (#326).
            consentConsumed = true
        }
    }

    /**
     * Deaktiviert die Quelle: stoppt alle Ziele und den Screen-Share-Encoder.
     *
     * Der MediaProjection wird dabei freigegeben, der Consent ist verbraucht:
     * der nächste Start muss einen neuen Dialog durchlaufen (#326).
     */
    override fun stop(): Boolean {
        display.stopStream()
        consentConsumed = true
        return true
    }

    /** Startet das Stream-Ziel [index] auf die übergebene [url]. */
    fun startStream(index: Int, url: String) {
        display.startStream(MultiType.RTMP, index, url)
    }

    /** Stoppt das Stream-Ziel [index]. */
    fun stopStream(index: Int) {
        display.stopStream(MultiType.RTMP, index)
    }
}
