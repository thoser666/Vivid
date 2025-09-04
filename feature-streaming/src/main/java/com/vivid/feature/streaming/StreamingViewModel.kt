package com.vivid.feature.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor() : ViewModel() {

    // Privater, veränderlicher StateFlow, der den aktuellen Streaming-Zustand hält.
    private val _isStreaming = MutableStateFlow(false)
    // Öffentlicher, unveränderlicher StateFlow, den die UI beobachten kann.
    val isStreaming = _isStreaming.asStateFlow()

    /**
     * Schaltet den Streaming-Zustand um.
     * Hier wird später die eigentliche Logik zum Verbinden/Trennen des Streams eingefügt.
     */
    fun toggleStreaming() {
        viewModelScope.launch {
            if (_isStreaming.value) {
                // TODO: Logik zum Stoppen des Streams hier einfügen
                // (z.B. Verbindung zum Server trennen)
                _isStreaming.value = false
                println("STREAMING: Stopped")
            } else {
                // TODO: Logik zum Starten des Streams hier einfügen
                // (z.B. Verbindung zum RTMP/SRT-Server aufbauen)
                _isStreaming.value = true
                println("STREAMING: Started")
            }
        }
    }
}