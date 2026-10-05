package com.vivid.feature.streaming

import com.pedro.library.base.Camera2Base

/**
 * Camera2Base keeps audioInitialized=true after stopRecord, although its AAC codec
 * and microphone are stopped. A following VIDEO_ONLY recording must not restart
 * that released audio encoder. The next prepareAudio re-enables normal audio.
 */
internal fun prepareStandaloneRecordingAudio(camera: Camera2Base, includeAudio: Boolean): Boolean {
    if (includeAudio) return camera.prepareAudio()
    check(!camera.isStreaming && !camera.isRecording)
    Camera2Base::class.java.getDeclaredField("audioInitialized").apply { isAccessible = true }
        .setBoolean(camera, false)
    return true
}
