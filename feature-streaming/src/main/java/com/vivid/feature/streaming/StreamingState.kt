package com.vivid.feature.streaming

sealed class StreamingState {
    object Idle : StreamingState()
    object Preparing : StreamingState()
    object Streaming : StreamingState()
    data class Failed(val reason: String?) : StreamingState()
}