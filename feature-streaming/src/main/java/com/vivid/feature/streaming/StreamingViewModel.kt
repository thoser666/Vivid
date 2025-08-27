package com.vivid.feature.streaming

import com.pedro.library.view.OpenGlView
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamingEngine: StreamingEngine
) : ViewModel() {

    // Diese Zustände bleiben:
    val isStreaming = streamingEngine.isStreaming
    val streamingError = streamingEngine.streamingError

    // Diese Funktion wird von der UI aufgerufen, um alles zu verbinden
    fun initialize(openGlView: OpenGlView) { // <-- Change the parameter type here
        streamingEngine.initializeCamera(openGlView) // <-- Now this should work
    }

    fun switchCamera() {
        streamingEngine.switchCamera()
    }

    fun startStream(rtmpUrl: String) {
        streamingEngine.startStreaming(rtmpUrl)
    }

    fun stopStream() {
        streamingEngine.stopStreaming()
    }

    // ViewModel freigeben
    override fun onCleared() {
        super.onCleared()
        streamingEngine.release()
    }
}