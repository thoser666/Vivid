package com.vivid.feature.streaming.source

import android.content.Context
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import java.io.File

/**
 * Gespeicherte Replay-Aufnahme (MP4) als Videoquelle (letzter offener Punkt der
 * PARITY-Row „Replays“: „Replay als Szenen-Quelle“).
 *
 * Die Quelle kapselt einen RootEncoder-[MultiFromFile] — derselbe Encoder-Typ wie
 * die Video-Player-Quelle ([VideoPlayerVideoSource]), aber zwei entscheidende
 * Unterschiede:
 *
 * 1. Die Datei stammt aus dem app-internen Replay-Verzeichnis (`filesDir/replays`)
 *    und nicht aus dem SAF — deshalb reicht ein Dateipfad (kein Content-Uri).
 * 2. RootEncoders `setLoopMode(true)` startet die Wiedergabe automatisch neu, wenn
 *    die Datei endet — eine Szene „Replay“ streamt also ohne Nutzer-Eingriff
 *    weiter (Szenen-Eigenschaft: Dauerquelle).
 *
 * [start]/[stop] erfüllen das [VideoSource]-Interface (Quelle aktivieren/deaktivieren);
 * die eigentliche URL-Streaming-Steuerung bleibt bei der Engine (wie bei Kamera,
 * Screen-Capture und Video-Player).
 */
class ReplayVideoSource(
    private val context: Context,
    private val player: MultiFromFile,
) : VideoSource {

    override val kind: VideoSourceKind = VideoSourceKind.REPLAY

    /** true, solange die Replay-Quelle aktiv streamt. */
    override val isActive: Boolean
        get() = player.isStreaming

    /** true, wenn eine Replay-Datei gesetzt wurde (prepare bereit). */
    var isVideoSet: Boolean = false
        private set

    /** Die zuletzt gesetzte Replay-Datei (null, solange keine gewählt wurde). */
    var replayFile: File? = null
        private set

    /**
     * Setzt die abzuspielende Replay-Datei (app-interner Pfad) und aktiviert die
     * Loop-Wiedergabe.
     *
     * @return true, wenn die Datei gesetzt und die Encoder vorbereitet werden konnten.
     */
    fun setReplay(file: File): Boolean {
        return try {
            player.setLoopMode(true)
            val videoOk = player.prepareVideo(file.absolutePath)
            val audioOk = player.prepareAudio(file.absolutePath)
            if (videoOk && audioOk) {
                replayFile = file
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
     * Ohne gesetzte Replay-Datei wird nicht gestartet (RootEncoder würde sonst mit
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
