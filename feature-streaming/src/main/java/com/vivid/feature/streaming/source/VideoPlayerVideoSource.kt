package com.vivid.feature.streaming.source

import android.content.Context
import android.net.Uri
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType

/**
 * Video-Datei als echte Videoquelle (S3 des Moblin-Buckets „Screen Capture +
 * Video-Player als Videoquelle“).
 *
 * Die Quelle kapselt einen RootEncoder-[MultiFromFile] (MediaExtractor/MediaCodec-
 * Wiedergabe der Datei, komplett von der Library verwaltet) und ergänzt den
 * Datei-Flow, den die App vor dem Start durchlaufen muss:
 *
 * 1. [setVideo] erhält die [Uri] der gewählten Datei (z. B. per Storage-Access-
 *    Framework aus dem Streaming-Screen)
 * 2. [start] bereitet die Encoder vor (Audio + Video aus der Datei) — ohne
 *    gesetzte Datei liefert RootEncoder eine IOException, daher wird vorher geprüft
 * 3. Die Engine streamt danach über [startStream] auf die einzelnen Ziele
 *
 * [start]/[stop] erfüllen das [VideoSource]-Interface (Quelle aktivieren/deaktivieren);
 * die eigentliche URL-Streaming-Steuerung bleibt bei der Engine (wie bei Kamera
 * und Screen-Capture).
 */
class VideoPlayerVideoSource(
    private val context: Context,
    private val player: MultiFromFile,
) : VideoSource {

    override val kind: VideoSourceKind = VideoSourceKind.VIDEO_PLAYER

    /** true, solange die Video-Player-Quelle aktiv streamt. */
    override val isActive: Boolean
        get() = player.isStreaming

    /** true, wenn eine Video-Datei gesetzt wurde (prepare bereit). */
    var isVideoSet: Boolean = false
        private set

    /** Die zuletzt gesetzte Video-Datei (null, solange keine gewählt wurde). */
    var videoUri: Uri? = null
        private set

    /**
     * Setzt die abzuspielende Video-Datei (Content-Uri, z. B. aus dem SAF-Picker).
     *
     * @return true, wenn die Datei gesetzt und die Encoder vorbereitet werden konnten.
     */
    fun setVideo(uri: Uri): Boolean {
        return try {
            val videoOk = player.prepareVideo(context, uri)
            val audioOk = player.prepareAudio(context, uri)
            if (videoOk && audioOk) {
                videoUri = uri
                isVideoSet = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Aktiviert die Quelle.
     *
     * Ohne gesetzte Datei wird nicht gestartet (RootEncoder würde sonst mit
     * „Source not set“ werfen). Der Stream selbst läuft über [startStream].
     *
     * @return true, wenn die Quelle startbereit ist.
     */
    override fun start(): Boolean {
        if (!isVideoSet) return false
        return true
    }

    /** Deaktiviert die Quelle: stoppt alle Ziele und den Decoder. */
    override fun stop(): Boolean {
        player.stopStream()
        return true
    }

    /** Startet das Stream-Ziel [index] auf die übergebene [url]. */
    fun startStream(index: Int, url: String) {
        player.startStream(MultiType.RTMP, index, url)
    }

    /** Stoppt das Stream-Ziel [index]. */
    fun stopStream(index: Int) {
        player.stopStream(MultiType.RTMP, index)
    }
}