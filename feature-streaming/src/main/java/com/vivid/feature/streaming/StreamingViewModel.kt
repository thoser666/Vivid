package com.vivid.feature.streaming

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    // Hilt weiß, wie man einen StreamingEngine erstellt (dank @Inject constructor)
    // und wird ihn hier automatisch für uns bereitstellen.
    val streamingEngine: StreamingEngine,
) : ViewModel() {
    // Das ViewModel ist im Moment nur ein Halter für die StreamingEngine.
    // Hier könnte später mehr Logik hinzukommen, z.B. das Laden von User-Daten.
}
