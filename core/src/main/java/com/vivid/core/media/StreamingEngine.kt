package com.vivid.core.media

import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingEngine @Inject constructor() {

    private var outputStreams = mutableListOf<String>()
    private var inputPlayer: ExoPlayer? = null

    fun addOutputStream(rtmpUrl: String) {
        outputStreams.add(rtmpUrl)
    }

    fun startStreaming() {
        // RTMP streaming logic hier
    }

    fun stopStreaming() {
        outputStreams.clear()
        inputPlayer?.stop()
    }
}