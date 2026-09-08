package com.vivid.feature.streaming

import android.content.Context
import android.os.Build
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.pedro.library.base.Camera2Base
import com.pedro.library.base.recording.RecordController
import com.pedro.library.util.AndroidMuxerRecordController
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Zustände der lokalen Replay-Aufnahme. */
sealed interface ReplayState {
    data object Idle : ReplayState
    data class Recording(val file: File) : ReplayState
}

/** Kleine Abstraktion über den Media-Muxer der Streaming-Kamera. */
interface ReplayRecorder {
    fun start(file: File): Boolean
    fun stop()
}

/** RootEncoder-Adapter für MP4-Aufnahmen parallel zum Stream. */
class RootEncoderReplayRecorder(
    private val camera: Camera2Base,
) : ReplayRecorder {
    override fun start(file: File): Boolean = runCatching {
        file.parentFile?.mkdirs()
        camera.startRecord(file.absolutePath)
        true
    }.getOrDefault(false)

    override fun stop() {
        camera.stopRecord()
    }
}

/**
 * Audio-Konfiguration der MP4-Aufnahme.
 *
 * RootEncoder wählt in `Camera2Base.startRecord` selbst die Tracks
 * (`ALL` wenn Audio initialisiert ist, sonst `VIDEO`). Dieser Recorder
 * injiziert über `setRecordController` einen Wrapper, der die Track-Auswahl
 * auf [com.vivid.core.data.ReplayAudioMode.VIDEO_ONLY] erzwingt, indem er
 * den vom Framework übergebenen `RecordTracks`-Wert überschreibt — der
 * Muxer schreibt dann keine Audiospur.
 *
 * `Camera2Base` hält seinen Controller in einem geschützten Feld und bietet
 * nur `setRecordController` öffentlich an (kein Getter). Da der Controller
 * im Konstruktor auf [AndroidMuxerRecordController] initialisiert wird und
 * die App ihn nie austauscht, wird hier jeweils ein frischer Default-
 * Controller gesetzt — `setRecordController` übernimmt dabei selbst die
 * bestehenden Codec-Informationen. Bei `includeAudio=true` wird ein
 * unverwrappter Controller gesetzt, damit ein vorheriger VIDEO_ONLY-Lauf
 * die Audiospur nicht weiter unterdrückt.
 */
class TrackControlledReplayRecorder(
    private val camera: Camera2Base,
    private val includeAudio: Boolean,
) : ReplayRecorder {
    override fun start(file: File): Boolean = runCatching {
        file.parentFile?.mkdirs()
        if (includeAudio) {
            camera.setRecordController(AndroidMuxerRecordController())
        } else {
            camera.setRecordController(
                TrackFilteringRecordController(AndroidMuxerRecordController()),
            )
        }
        camera.startRecord(file.absolutePath)
        true
    }.getOrDefault(false)

    override fun stop() {
        camera.stopRecord()
    }
}

/**
 * Delegierender [RecordController], der beim Start die Audiospur unterdrückt:
 * `startRecord(path, listener, tracks)` wird mit [RecordController.RecordTracks.VIDEO]
 * statt des vom Framework gewählten Wertes weitergeleitet. Alle anderen Aufrufe
 * gehen unverändert an den inneren Controller (Standard: AndroidMuxerRecordController).
 */
class TrackFilteringRecordController(
    private val inner: RecordController,
) : RecordController by inner {
    override fun startRecord(
        path: String,
        listener: RecordController.Listener?,
        tracks: RecordController.RecordTracks,
    ) {
        inner.startRecord(path, listener, RecordController.RecordTracks.VIDEO)
    }
}

/**
 * Verwaltet lokale Replay-Dateien. Es werden nur die neuesten [maxFiles]
 * Aufnahmen behalten; die Dateien bleiben app-intern und werden nicht exportiert.
 */
class ReplayStorage(
    val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {
    init {
        require(maxFiles > 0) { "maxFiles must be positive" }
    }

    fun nextFile(nowMillis: Long = System.currentTimeMillis()): File {
        directory.mkdirs()
        val stamp = FILE_FORMAT.format(Date(nowMillis))
        var file = File(directory, "replay-$stamp.mp4")
        var suffix = 1
        while (file.exists()) {
            file = File(directory, "replay-$stamp-$suffix.mp4")
            suffix++
        }
        return file
    }

    fun prune() {
        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        }.orEmpty().sortedByDescending { it.lastModified() }
        files.drop(maxFiles).forEach { stale ->
            if (!stale.delete() && stale.exists()) stale.deleteOnExit()
        }
    }

    fun list(): List<File> = directory.listFiles { file ->
        file.isFile && file.extension.equals("mp4", ignoreCase = true)
    }.orEmpty().sortedByDescending { it.lastModified() }

    companion object {
        const val DEFAULT_MAX_FILES = 5
        private val FILE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}

/**
 * Koordiniert Aufnahmebeginn/-ende und räumt alte Replays nach jeder Aufnahme auf.
 * Die Klasse enthält keine Android-Media-Logik und ist deshalb vollständig per
 * Unit-Test prüfbar.
 */
class ReplayController(
    private val storage: ReplayStorage,
    private val recorder: ReplayRecorder,
) {
    private val _state = MutableStateFlow<ReplayState>(ReplayState.Idle)
    val state: StateFlow<ReplayState> = _state.asStateFlow()

    fun start(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (_state.value is ReplayState.Recording) return false
        val file = storage.nextFile(nowMillis)
        if (!recorder.start(file)) {
            if (!file.delete() && file.exists()) file.deleteOnExit()
            return false
        }
        _state.value = ReplayState.Recording(file)
        return true
    }

    fun stop(): File? {
        val recording = _state.value as? ReplayState.Recording ?: return null
        runCatching { recorder.stop() }
        _state.value = ReplayState.Idle
        storage.prune()
        return recording.file.takeIf { it.exists() }
    }

    fun prune() = storage.prune()
}

/** Erzeugt den app-internen Replay-Speicher für die Streaming-Engine. */
fun replayStorage(context: Context): ReplayStorage =
    ReplayStorage(File(context.filesDir, "replays"))

/**
 * Erzeugt und lädt Thumbnail-Vorschauen für die Replay-Bibliothek.
 *
 * Thumbs liegen als JPEG neben den MP4s (gleicher Basisname, Endung `.jpg`);
 * fehlende Thumbs werden beim Laden einmalig aus einem Frame nahe 1 s
 * extrahiert (API 27+: `getScaledFrameAtTime`, darunter `getFrameAtTime`)
 * und auf maximal [MAX_WIDTH_PX]px Breite skaliert.
 */
class MediaMetadataReplayThumbnailStore @Inject constructor() : ReplayThumbnailStore {

    override fun thumbnailFileFor(replay: File): File =
        File(replay.parentFile, replay.nameWithoutExtension + THUMB_SUFFIX)

    override fun loadOrCreate(replay: File): File? {
        val thumb = thumbnailFileFor(replay)
        if (thumb.isFile && thumb.length() > 0) return thumb
        if (!replay.isFile || replay.length() == 0L) return null

        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(replay.absolutePath)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        SNAPSHOT_TIME_US,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        MAX_WIDTH_PX,
                        MAX_HEIGHT_PX,
                    )
                } else {
                    retriever.getFrameAtTime(SNAPSHOT_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                bitmap ?: return@use null
                FileOutputStream(thumb).use { out ->
                    bitmap.compress(CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()
                thumb
            }
        }.getOrNull()
    }

    override fun deleteThumbnail(replay: File) {
        thumbnailFileFor(replay).takeIf { it.isFile }?.delete()
    }

    companion object {
        const val THUMB_SUFFIX = ".jpg"
        const val SNAPSHOT_TIME_US = 1_000_000L
        const val MAX_WIDTH_PX = 320
        const val MAX_HEIGHT_PX = 180
        const val JPEG_QUALITY = 80
    }
}

/** Abstraktion über den Thumbnail-Speicher (unit-testbar ohne Media-Framework). */
interface ReplayThumbnailStore {
    /** Pfad des Thumbnails für das Replay (ohne es zu erzeugen). */
    fun thumbnailFileFor(replay: File): File

    /** Lädt das Thumbnail oder erzeugt es einmalig aus dem Video. */
    fun loadOrCreate(replay: File): File?

    /** Löscht das Thumbnail des Replays (falls vorhanden). */
    fun deleteThumbnail(replay: File)
}
