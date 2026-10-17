package com.vivid.feature.streaming.source.uvc

import com.pedro.library.base.recording.RecordController
import com.pedro.library.multiple.MultiStream
import com.vivid.feature.streaming.ReplayRecorder
import java.io.File

/** Installed before preparation, retaining codec formats when recording begins during RTMP. */
internal class UvcTrackRecordController(private val inner: RecordController) : RecordController by inner {
    var includeAudio = true
    override fun startRecord(path: String, listener: RecordController.Listener?, tracks: RecordController.RecordTracks) {
        inner.startRecord(path, listener, if (includeAudio) tracks else RecordController.RecordTracks.VIDEO)
    }
}

internal class UvcReplayRecorder(
    private val stream: MultiStream,
    private val tracks: UvcTrackRecordController,
    private val includeAudio: Boolean,
    private val onFailure: () -> Unit,
) : ReplayRecorder {
    override fun start(file: File): Boolean {
        tracks.includeAudio = includeAudio
        return try {
            file.parentFile?.mkdirs()
            stream.startRecord(file.absolutePath, null, object : RecordController.Listener {
                override fun onStatusChange(status: RecordController.Status) = Unit
                override fun onError(e: Exception?) { onFailure() }
            })
            true
        } catch (_: Exception) { false }
    }
    override fun stop() { stream.stopRecord() }
}
