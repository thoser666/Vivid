package com.vivid.feature.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    val streamingEngine: StreamingEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Liest die gespeicherten Stream-Einstellungen und startet den Stream
     * mit der konfigurierten URL (inkl. Stream-Key).
     */
    fun startStream() {
        viewModelScope.launch {
            _errorMessage.value = null
            val settings = settingsRepository.appSettingsFlow.first()
            val url = buildStreamUrl(settings.streamUrl, settings.streamKey)
            if (url == null) {
                _errorMessage.value = "Keine Stream-URL konfiguriert. Bitte in den Einstellungen hinterlegen."
                return@launch
            }
            streamingEngine.startStream(url)
        }
    }

    fun stopStream() {
        streamingEngine.stopStream()
    }
}

/**
 * Baut die vollständige RTMP-URL aus URL + Stream-Key.
 *
 * Der Key wird nur angehängt, wenn er nicht bereits das letzte Pfadsegment der
 * URL ist (manche Plattformen liefern die komplette URL inkl. Key). Eine leere
 * URL ergibt `null`, damit der Aufrufer einen Fehler anzeigen kann.
 */
internal fun buildStreamUrl(streamUrl: String, streamKey: String): String? {
    val url = streamUrl.trim()
    if (url.isEmpty()) return null
    val key = streamKey.trim()
    if (key.isEmpty() || url.endsWith(key) || url.endsWith("/$key")) return url
    val separator = if (url.endsWith("/")) "" else "/"
    return url + separator + key
}
