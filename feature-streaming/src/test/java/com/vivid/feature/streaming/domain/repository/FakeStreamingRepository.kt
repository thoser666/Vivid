package com.vivid.feature.streaming.domain.repository

import com.vivid.data.model.StreamingState
import com.vivid.feature.streaming.data.repository.StreamingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeStreamingRepository : StreamingRepository {

    private val _state = MutableStateFlow<StreamingState>(StreamingState.Idle)
    override val streamingState = _state.asStateFlow()

    var startStreamCalled = false
    var stopStreamCalled = false

    override suspend fun startStream() {
        startStreamCalled = true
        _state.value = StreamingState.Streaming
    }

    override suspend fun stopStream() {
        stopStreamCalled = true
        _state.value = StreamingState.Idle
    }
}