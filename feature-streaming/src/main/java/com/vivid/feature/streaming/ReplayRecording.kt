package com.vivid.feature.streaming

import android.content.Context
import com.pedro.library.base.Camera2Base
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
 * Verwaltet lokale Replay-Dateien. Es werden nur die neuesten [maxFiles]
 * Aufnahmen behalten; die Dateien bleiben app-intern und werden nicht exportiert.
 */
class ReplayStorage(
    private val directory: File,
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
        files.drop(maxFiles).forEach { it.delete() }
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
            file.delete()
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
