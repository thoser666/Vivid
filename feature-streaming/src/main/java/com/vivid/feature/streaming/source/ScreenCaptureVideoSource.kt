package com.vivid.feature.streaming.source

import android.app.Activity
import android.content.Intent
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiType

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
 * [start]/[stop] erfüllen das [VideoSource]-Interface (Quelle aktivieren/deaktivieren);
 * die eigentliche URL-Streaming-Steuerung bleibt bei der Engine (wie bei der Kamera).
 */
class ScreenCaptureVideoSource(
    private val display: MultiDisplay,
) : VideoSource {

    override val kind: VideoSourceKind = VideoSourceKind.SCREEN_CAPTURE

    /** true, solange die Screen-Capture-Quelle aktiv streamt. */
    override val isActive: Boolean
        get() = display.isStreaming

    /** true, wenn der MediaProjection-Consent bereits erteilt wurde. */
    var isConsentGranted: Boolean = false
        private set

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
        return true
    }

    /**
     * Aktiviert die Quelle: bereitet Audio- und Video-Encoder vor.
     *
     * Ohne erteilten Consent wird nicht präpariert (RootEncoder würde sonst mit
     * „You need send intent data before startRecord or startStream“ werfen).
     *
     * @return true, wenn beide Encoder bereit sind.
     */
    override fun start(): Boolean {
        if (!isConsentGranted) return false
        return display.prepareAudio() && display.prepareVideo()
    }

    /** Deaktiviert die Quelle: stoppt alle Ziele und den Screen-Share-Encoder. */
    override fun stop(): Boolean {
        display.stopStream()
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
