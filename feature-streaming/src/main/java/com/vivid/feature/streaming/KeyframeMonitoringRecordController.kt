package com.vivid.feature.streaming

import android.media.MediaCodec
import com.pedro.library.base.recording.RecordController
import java.nio.ByteBuffer

/** Camera2Base passes every encoded frame through this hook, including while not recording. */
internal class KeyframeMonitoringRecordController(
    private val inner: RecordController,
    private val onVideoFrame: (MediaCodec.BufferInfo) -> Unit,
) : RecordController by inner {
    override fun recordVideo(videoBuffer: ByteBuffer, videoInfo: MediaCodec.BufferInfo) {
        if (videoInfo.size > 0 && videoInfo.flags and (MediaCodec.BUFFER_FLAG_CODEC_CONFIG or MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
            onVideoFrame(videoInfo)
        }
        inner.recordVideo(videoBuffer, videoInfo)
    }
}
