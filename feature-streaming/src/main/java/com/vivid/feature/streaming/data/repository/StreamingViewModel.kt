package com.vivid.feature.streaming.data.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.data.model.StreamingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    private val streamingRepository: StreamingRepository
) : ViewModel() {

    // Das ViewModel gibt den Zustand vom Repository direkt an die UI weiter
    val streamingState: StateFlow<StreamingState> = streamingRepository.streamingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = StreamingState.Idle
        )

    fun toggleStreaming() {
        viewModelScope.launch {
            when (streamingState.value) {
                is StreamingState.Streaming -> streamingRepository.stopStream()
                is StreamingState.Idle -> streamingRepository.startStream()
                else -> {
                    // Mache nichts, wenn gerade verbunden oder getrennt wird
                }
            }
        }
    }
}