package com.vivid.feature.streacming.data.repository

import androidx.lifecycle.ViewModel
import com.vivid.feature.streaming.StreamingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    val streamingEngine: StreamingEngine,
) : ViewModel() {

    override fun onCleared() {
        super.onCleared()
        // Geben Sie die Ressourcen frei, wenn das ViewModel zerstört wird
        streamingEngine.releaseResources()
    }
}
