package com.vivid.feature.streaming.source

import android.view.Surface
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import com.vivid.feature.streaming.source.uvc.UvcConnectionState
import com.vivid.feature.streaming.source.uvc.UvcConnectionManager
import timber.log.Timber

/**
 * Externe USB/UVC-Kamera als echte Videoquelle (#305, Phasen C–E).
 *
 * Kapselt einen RootEncoder-[MultiStream] (ein Encoder, ein UVC-Adapter,
 * Mikrofon-Audio, ein RTMP-Client pro Ziel) und ergänzt die Gerätebindung:
 *
 * 1. Der Nutzer verbindet ein Gerät im Picker ([UvcConnectionManager.connect]);
 *    die Modi stammen vom geöffneten Gerät, die Auswahl wird revalidiert.
 * 2. [startIdlePreview] zeigt echte Frames vor dem Go-Live (derselbe
 *    GL-/Encoder-Pfad, kein zweiter UVC-Zugriff).
 * 3. [start] bereitet die Encoder mit der Modusgröße vor (Encoder folgt dem
 *    Capture, nicht umgekehrt) — ohne bereites Gerät wird nicht präpariert.
 * 4. Die Engine streamt danach über [startStream] auf die einzelnen Ziele;
 *    MP4 läuft über denselben Record-Controller wie die Kamera.
 *
 * [start]/[stop] erfüllen das [VideoSource]-Interface; die URL-Steuerung bleibt
 * bei der Engine (wie bei Kamera/Screen-Capture/Video-Player).
 */
class UsbVideoSource(
    private val stream: MultiStream,
    private val connectionManager: UvcConnectionManager,
) : VideoSource {

    internal val glInterface get() = stream.getGlInterface()

    /** Der gekapselte Encoder (für Record-Controller und Keyframe-Monitoring). */
    internal val multiStream: MultiStream get() = stream

    var videoBitrate: Int = DEFAULT_VIDEO_BITRATE

    override val kind: VideoSourceKind = VideoSourceKind.USB_UVC

    /** true, solange die USB-Quelle streamt oder aufnimmt. */
    override val isActive: Boolean
        get() = stream.isStreaming || stream.isRecording

    val isStreaming: Boolean get() = stream.isStreaming
    val isRecording: Boolean get() = stream.isRecording

    /** Aktueller UVC-Verbindungszustand (für UI und Engine-Guards). */
    val connectionState get() = connectionManager.connectionState

    /**
     * Aktiviert die Quelle: beendet eine laufende Idle-Vorschau (prepare
     * verbietet sie) und bereitet Audio- und Video-Encoder vor.
     *
     * Ohne bereites Gerät (Berechtigung/Öffnen/Modus fehlt) wird nicht
     * präpariert — die Engine routet das über `failStream`, statt einen
     * Encoder ohne Frames zu starten.
     *
     * @return true, wenn beide Encoder bereit sind.
     */
    override fun start(): Boolean {
        // Stream während laufender Standalone-Aufnahme: Die Encoder gehören
        // bereits der Aufnahme — kein Re-Prepare (StreamBase verbietet es bei
        // laufendem Recording), sondern Weiternutzung des laufenden Captures.
        if (stream.isRecording || stream.isStreaming) return currentMode() != null
        val mode = currentMode() ?: return false
        return try {
            if (stream.isOnPreview) stream.stopPreview()
            // Dieselben Audio-Defaults wie der Kamera-Pfad
            // (`Camera2Base.prepareAudio()` = 64 kbit/s, 32 kHz, Stereo).
            val audioReady = stream.prepareAudio(
                DEFAULT_AUDIO_SAMPLE_RATE,
                DEFAULT_AUDIO_STEREO,
                DEFAULT_AUDIO_BITRATE,
            )
            // Encoder folgt dem Capture: Modusgröße, 6 Mbit/s-Standard wie die
            // Kamera (Legacy-Pfad), 2-s-Keyframe-Intervall, keine Rotation
            // (UVC liefert nativ Landscape).
            val videoReady = stream.prepareVideo(
                mode.width,
                mode.height,
                videoBitrate,
                mode.fps,
                KEYFRAME_INTERVAL_SECONDS,
                0,
            )
            audioReady && videoReady
        } catch (e: Exception) {
            Timber.e(e, "UsbVideoSource: Encoder-Vorbereitung verweigert")
            false
        }
    }

    /** Deaktiviert die Quelle: stoppt alle Ziele und den Shared-Encoder. */
    override fun stop(): Boolean {
        runCatching { stream.stopStream() }
        return true
    }

    /** Startet das Stream-Ziel [index] auf die übergebene [url]. */
    fun startStream(index: Int, url: String) {
        stream.startStream(MultiType.RTMP, index, url)
    }

    /** Stoppt das Stream-Ziel [index] (andere Ziele laufen weiter). */
    fun stopStream(index: Int) {
        stream.stopStream(MultiType.RTMP, index)
    }

    /**
     * Startet die Idle-Vorschau mit echten Frames (vor dem Go-Live, ohne
     * Encoder-Streaming). Nutzt dieselbe GL-Pipeline wie Stream/Recording.
     */
    fun startIdlePreview(surface: Surface, width: Int, height: Int): Boolean {
        val mode = currentMode() ?: return false
        return try {
            if (!stream.isOnPreview) {
                if (!prepareForPreview(mode)) return false
                stream.startPreview(surface, width, height)
            } else {
                stream.getGlInterface().attachPreview(surface)
                stream.getGlInterface().setPreviewResolution(width, height)
            }
            stream.getGlInterface().setPreviewRotation(0)
            stream.getGlInterface().setAspectRatioMode(com.pedro.encoder.utils.gl.AspectRatioMode.Adjust)
            true
        } catch (e: Exception) {
            Timber.e(e, "UsbVideoSource: Idle-Vorschau fehlgeschlagen")
            runCatching { stream.stopPreview() }
            false
        }
    }

    /** Beendet die Idle-Vorschau (Stream/Recording laufen ungestört weiter). */
    fun stopIdlePreview() {
        if (stream.isOnPreview && !stream.isStreaming && !stream.isRecording) {
            runCatching { stream.stopPreview() }
        }
    }

    /** Löst die UI-Vorschau von der GL-Pipeline (Stream/Recording laufen weiter). */
    fun deAttachPreview() {
        runCatching { glInterface?.deAttachPreview() }
    }

    private fun prepareForPreview(mode: com.vivid.feature.streaming.source.uvc.UvcCaptureMode): Boolean =
        try {
            stream.prepareVideo(
                mode.width,
                mode.height,
                videoBitrate,
                mode.fps,
                KEYFRAME_INTERVAL_SECONDS,
                0,
            )
        } catch (e: Exception) {
            Timber.e(e, "UsbVideoSource: Vorschau-Vorbereitung verweigert")
            false
        }

    private fun currentMode(): com.vivid.feature.streaming.source.uvc.UvcCaptureMode? =
        when (val state = connectionManager.connectionState.value) {
            is UvcConnectionState.Ready -> state.mode
            is UvcConnectionState.Capturing -> state.mode
            is UvcConnectionState.Starting -> state.mode
            else -> null
        }


    companion object {
        /** Standard-Bitrate (bit/s) wie der Kamera-Legacy-Pfad (6 Mbit/s). */
        const val DEFAULT_VIDEO_BITRATE = 6_000 * 1_000

        /** Keyframe-Intervall (s) wie der Kamera-Pfad. */
        const val KEYFRAME_INTERVAL_SECONDS = 2

        /** Audio-Defaults wie `Camera2Base.prepareAudio()`. */
        const val DEFAULT_AUDIO_SAMPLE_RATE = 32000
        const val DEFAULT_AUDIO_STEREO = true
        const val DEFAULT_AUDIO_BITRATE = 64 * 1024
    }
}
