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
    private val streamingServiceLauncher: StreamingServiceLauncher,
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _configIssues = MutableStateFlow<List<StreamConfigIssue>>(emptyList())

    /** Befunde des Stream-Selbst-Checks (werden vor dem Go-Live im UI angezeigt). */
    val configIssues: StateFlow<List<StreamConfigIssue>> = _configIssues.asStateFlow()

    init {
        runConfigCheck()
    }

    /** Führt den Selbst-Check mit den aktuell gespeicherten Einstellungen aus. */
    fun runConfigCheck() {
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            _configIssues.value = StreamConfigValidator.validate(
                streamUrl = settings.streamUrl,
                streamKey = settings.streamKey,
                streamUseTls = settings.streamUseTls,
                secondaryStreamUrl = settings.secondaryStreamUrl,
                secondaryStreamKey = settings.secondaryStreamKey,
                secondaryStreamUseTls = settings.secondaryStreamUseTls,
            )
        }
    }

    /**
     * Liest die gespeicherten Stream-Einstellungen, validiert sie per Selbst-Check
     * und startet den Stream nur, wenn keine Fehler vorliegen.
     */
    fun startStream() {
        viewModelScope.launch {
            _errorMessage.value = null
            val settings = settingsRepository.appSettingsFlow.first()

            val issues = StreamConfigValidator.validate(
                streamUrl = settings.streamUrl,
                streamKey = settings.streamKey,
                streamUseTls = settings.streamUseTls,
                secondaryStreamUrl = settings.secondaryStreamUrl,
                secondaryStreamKey = settings.secondaryStreamKey,
                secondaryStreamUseTls = settings.secondaryStreamUseTls,
            )
            _configIssues.value = issues

            val errors = issues.filter { it.severity == ConfigIssueSeverity.ERROR }
            if (errors.isNotEmpty()) {
                _errorMessage.value = errors.joinToString("\n") { it.message }
                return@launch
            }

            val urls = buildList {
                buildStreamUrl(settings.streamUrl, settings.streamKey, settings.streamUseTls)?.let { add(it) }
                // Optionales zweites Ziel (Multi-Streaming).
                buildStreamUrl(
                    settings.secondaryStreamUrl,
                    settings.secondaryStreamKey,
                    settings.secondaryStreamUseTls,
                )?.let { add(it) }
            }
            if (urls.isEmpty()) {
                _errorMessage.value = "Keine Stream-URL konfiguriert. Bitte in den Einstellungen hinterlegen."
                return@launch
            }
            // Der Stream läuft im Foreground-Service weiter, wenn die App in den
            // Hintergrund geht (Prozess-Priorität + WakeLock). Der Service ruft
            // seinerseits streamingEngine.startStream(urls) auf.
            streamingServiceLauncher.startStreaming(urls)
        }
    }

    fun stopStream() {
        streamingServiceLauncher.stopStreaming()
    }
}

/**
 * Baut die vollständige RTMP-URL aus URL + Stream-Key.
 *
 * Der Key wird nur angehängt, wenn er nicht bereits das letzte Pfadsegment der
 * URL ist (manche Plattformen liefern die komplette URL inkl. Key). Eine leere
 * URL ergibt `null`, damit der Aufrufer einen Fehler anzeigen kann.
 *
 * Bei [useTls] = true wird `rtmp://` automatisch auf `rtmps://` umgeschrieben
 * (RTMP über TLS). Die RootEncoder-Library erkennt `rtmps://` am Scheme und
 * aktiviert TLS selbst (verifiziert an RootEncoder 2.6.4: `tlsEnabled` wird
 * gesetzt, wenn das Scheme auf `s` endet; Default-Port ist dann 443 statt 1935).
 * Ein explizit gesetzter Standard-RTMP-Port (1935) wird deshalb auf 443
 * umgeschrieben — ein eigener TLS-Port (z. B. 8443) bleibt erhalten.
 * URLs, die bereits ein sicheres Scheme haben (`rtmps`/`rtmpt`/`rtmpts`/`srt`),
 * bleiben unverändert.
 */
internal fun buildStreamUrl(streamUrl: String, streamKey: String, useTls: Boolean = false): String? {
    var url = streamUrl.trim()
    if (url.isEmpty()) return null
    if (useTls && url.startsWith("rtmp://")) {
        url = "rtmps://" + url.removePrefix("rtmp://")
        // RootEncoder nutzt bei TLS standardmäßig Port 443. Einen expliziten
        // Standard-RTMP-Port (1935) auf 443 umschreiben, sonst würde TLS auf
        // dem falschen Port versuchen. Eigene Ports (z. B. 8443) bleiben.
        url = normalizeTlsPort(url)
    }
    val key = streamKey.trim()
    if (key.isEmpty() || url.endsWith(key) || url.endsWith("/$key")) return url
    val separator = if (url.endsWith("/")) "" else "/"
    return url + separator + key
}

/**
 * Schreibt einen expliziten Standard-RTMP-Port `:1935` direkt nach dem Host
 * auf `:443` um (für rtmps). Eigene Ports (z. B. `:8443`) bleiben unverändert.
 * Reine String-Logik ohne Regex, damit die Escapes nicht ins Rutschen kommen.
 */
private fun normalizeTlsPort(url: String): String {
    val afterScheme = url.substringAfter("rtmps://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return url
    val authority = afterScheme.substringBefore("/").substringBefore("?")
    if (!authority.endsWith(":1935")) return url
    val host = authority.removeSuffix(":1935")
    val rest = afterScheme.removePrefix(authority)
    return "rtmps://$host:443$rest"
}
