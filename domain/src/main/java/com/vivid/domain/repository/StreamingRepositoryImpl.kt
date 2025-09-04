package com.vivid.domain.repository

import com.vivid.data.model.StreamingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Stellt sicher, dass es nur eine Instanz des Repositories gibt
class StreamingRepositoryImpl @Inject constructor() : StreamingRepository {

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    override val streamingState = _streamingState.asStateFlow()

    override suspend fun startStream() {
        // Nur starten, wenn der Stream inaktiv ist
        if (_streamingState.value is StreamingState.Idle) {
            _streamingState.value = StreamingState.Connecting
            println("STREAM REPO: Verbinde...")
            delay(2000) // Simuliere Verbindungsaufbau

            // In der Realität: Prüfe hier, ob die Verbindung erfolgreich war
            val success = true
            if (success) {
                _streamingState.value = StreamingState.Streaming
                println("STREAM REPO: Streaming aktiv.")
            } else {
                _streamingState.value = StreamingState.Error("Verbindung fehlgeschlagen")
                println("STREAM REPO: Fehler.")
            }
        }
    }

    override suspend fun stopStream() {
        // Nur stoppen, wenn der Stream läuft oder beim Verbinden ist
        if (_streamingState.value is StreamingState.Streaming || _streamingState.value is StreamingState.Connecting) {
            _streamingState.value = StreamingState.Disconnecting
            println("STREAM REPO: Trenne Verbindung...")
            delay(1000) // Simuliere das Trennen

            _streamingState.value = StreamingState.Idle
            println("STREAM REPO: Inaktiv.")
        }
    }
}