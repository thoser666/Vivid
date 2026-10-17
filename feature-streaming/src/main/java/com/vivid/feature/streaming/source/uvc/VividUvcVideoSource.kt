package com.vivid.feature.streaming.source.uvc

import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vivid-eigener UVC-Adapter auf RootEncoders `VideoSource` (#305, Phase C).
 *
 * Warum kein Upstream-`CameraUvcSource` (RootEncoder `extra-sources:2.7.5`,
 * verifiziert am Tag): Die Beispielklasse wählt bei `onAttach` *jedes* neu
 * angeschlossene Gerät automatisch aus (keine Gerätewahl), ruft `startPreview()`
 * VOR `addSurface()` auf, ignoriert `create(width, height, fps, rotation)`
 * faktisch (gibt immer true zurück), meldet `running = true` bereits beim
 * Erzeugen des Helpers (kein Bereitschaftssignal) und lässt Detach-/Fehler-
 * Callbacks leer. Keine dieser Eigenschaften erfüllt Geräte-/Moduswahl,
 * Fehlerabbildung oder Lebenszyklus-Anforderungen — daher dieser Adapter über
 * dieselbe Helper-API (`com.herohan.uvcapp`, UVCAndroid 1.0.13), gesteuert vom
 * [UvcConnectionManager] (genau ein Owner).
 *
 * Vertrag:
 * - `create` bestätigt nur, wenn ein Gerät bereit ist (Ready/Capturing);
 *   die Encoder-Größe folgt dem ausgehandelten UVC-Modus (siehe
 *   [UsbVideoSource]), nicht umgekehrt.
 * - `start` hängt die GL-Surface ein; `Capturing` meldet der Manager erst nach
 *   gestarteter Vorschau. `isRunning` ist false, solange kein Capture läuft.
 * - `stop`/`release` sind idempotent und geben nur eigene Surfaces frei.
 */
@Singleton
class VividUvcVideoSource @Inject constructor(
    private val connectionManager: UvcConnectionManager,
) : VideoSource() {

    private var surface: Surface? = null
    private var running = false
    private val lock = Any()

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        val mode = when (val state = connectionManager.connectionState.value) {
            is UvcConnectionState.Ready -> state.mode
            is UvcConnectionState.Capturing -> state.mode
            is UvcConnectionState.Starting -> state.mode
            else -> return false
        }
        return width == mode.width && height == mode.height && fps == mode.fps && rotation == 0
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        synchronized(lock) {
            if (surface != null) return
            this.surfaceTexture = surfaceTexture
            val owned = Surface(surfaceTexture)
            val attached = connectionManager.attachCaptureSurface(owned)
            if (!attached) {
                runCatching { owned.release() }
                this.surfaceTexture = null
                Timber.w("VividUvcVideoSource: Capture-Surface abgelehnt (kein bereites Gerät)")
                return
            }
            surface = owned
            running = true
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!running) {
                surface = null
                return
            }
            running = false
            surface?.let { owned ->
                connectionManager.detachCaptureSurface(owned)
                runCatching { owned.release() }
            }
            surface = null
            surfaceTexture = null
        }
    }

    override fun release() {
        stop()
    }

    override fun isRunning(): Boolean =
        synchronized(lock) { running && connectionManager.connectionState.value is UvcConnectionState.Capturing }

    override fun getOrientationConfig() =
        OrientationConfig(forced = OrientationForced.LANDSCAPE)
}
