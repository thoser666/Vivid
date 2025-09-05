package com.vivid.feature.streaming.data.repository

import com.vivid.data.model.StreamingState
import kotlinx.coroutines.flow.StateFlow

interface StreamingRepository {
    /**
     * Ein Flow, der den aktuellen Zustand des Streams ausgibt.
     */
    val streamingState: StateFlow<StreamingState>

    /**
     * Startet den Streaming-Prozess.
     */
    suspend fun startStream()

    /**
     * Stoppt den Streaming-Prozess.
     */
    suspend fun stopStream()
}
