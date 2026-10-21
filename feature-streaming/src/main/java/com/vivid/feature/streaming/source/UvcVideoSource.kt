package com.vivid.feature.streaming.source

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiType
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** UVC USB Webcam & HDMI Capture Card Quelle (Issue #305).
 * Ergänzt das Moblin-Bucket "Screen Capture + Video-Player als Videoquelle".
 * Basiert auf Pedro's Multiple-Display Library, ähnlich wie ScreenCaptureVideoSource.
 *
 * S2: UVC-Quelle registrieren; S3: Engine-Anbindung via VideoSourceFactory.
 *
 * Siehe auch: https://github.com/pedroSG94/RootEncoder für den Encoder-Pfad.
 */
@Singleton
class UvcVideoSource @Inject constructor(
    private val display: MultiDisplay,
    private val activity: Activity,
    private val usbManager: UsbManager
) : VideoSource {

    internal val glInterface get() = display.glInterface as? com.pedro.library.view.GlStreamInterface

    override val kind: VideoSourceKind = VideoSourceKind.UVC

    /** true, solange die UVC-Quelle aktiv streamt. */
    override val isActive: Boolean
        get() = display.isStreaming

    /** true, wenn der USB-Geräte-Consent erteilt wurde. */
    var isDeviceOpen: Boolean = false
        private set

    /** Liefert den Intent zum Öffnen des USB-Geräte-Consent-Dialogs. */
    fun createConsentIntent(): Intent = display.sendIntent()

    /** Übergibt das Ergebnis des USB-Consent-Dialogs an die Quelle. */
    fun onDeviceResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) return false
        display.setIntentResult(resultCode, data)
        isDeviceOpen = true
        return true
    }

    /** Bereitet Audio- und Video-Encoder vor (nur wenn Device geöffnet). */
    override fun start(): Boolean {
        if (!isDeviceOpen) return false
        return try {
            display.prepareAudio() && display.prepareVideo()
        } catch (e: SecurityException) {
            Timber.e(e, "UvcVideoSource: Vorbereitung verweigert (SecurityException)")
            false
        }
    }

    /** Deaktiviert die UVC-Quelle: stoppt alle Ziele und den USB-Stream. */
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