package com.vivid.feature.streaming.ui // Lege sie neben dem Screen ab

import androidx.lifecycle.ViewModel
import com.vivid.feature.streaming.StreamingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    // Hilt injiziert hier automatisch die Singleton-Instanz der StreamingEngine
    val streamingEngine: StreamingEngine,
) : ViewModel() {

    // Wenn das ViewModel zerstört wird (z.B. weil der Benutzer wegnavigiert),
    // geben wir die Ressourcen der Engine frei.
    override fun onCleared() {
        super.onCleared()
        streamingEngine.release()
    }
}
