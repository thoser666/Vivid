package com.vivid.feature.streaming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.scene.AutoSceneSwitcher
import com.vivid.feature.streaming.scene.SceneController
import com.vivid.feature.streaming.source.VideoSourceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class StreamingViewModel @Inject constructor(
    val streamingEngine: StreamingEngine,
    private val settingsRepository: SettingsRepository,
    private val streamingServiceLauncher: StreamingServiceLauncher,
    private val sceneRepository: SceneRepository,
    private val sceneController: SceneController,
    private val autoSceneSwitcher: AutoSceneSwitcher,
) : ViewModel() {

    private val _configIssues = MutableStateFlow<List<StreamConfigIssue>>(emptyList())

    // --- Szenen (Basic Scenes) + Auto-Scene-Switcher ---

    /** Alle gespeicherten Szenen (Anlage-Reihenfolge). */
    val scenes: Flow<List<StreamScene>> = sceneRepository.scenesFlow

    /** ID der zuletzt angewendeten Szene (null = keine aktiv). */
    val activeSceneId: Flow<String?> = sceneRepository.activeSceneIdFlow

    /** Auto-Scene-Switcher: an/aus + Intervall (Sekunden). */
    val autoSwitchEnabled: StateFlow<Boolean> = autoSceneSwitcher.enabled
    val autoSwitchIntervalSeconds: StateFlow<Long> = autoSceneSwitcher.intervalSeconds

    /**
     * Befunde des Stream-Selbst-Checks (werden vor dem Go-Live im UI angezeigt).
     * Die Meldungen sind String-Ressourcen (i18n) — die UI löst sie per
     * `stringResource` auf, daher gibt es keinen vorkomponierten String mehr.
     */
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

            // Harte Fehler blockieren den Go-Live (die UI zeigt sie als Banner).
            if (issues.any { it.severity == ConfigIssueSeverity.ERROR }) return@launch

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
                // Defensiv: validate() liefert für leere primäre URL bereits den
                // Fehler; falls er trotzdem fehlt, als Befund nachtragen.
                _configIssues.value = issues + StreamConfigIssue(
                    ConfigIssueSeverity.ERROR,
                    R.string.stream_error_no_url,
                )
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

    // --- Szenen-Aktionen ---

    /**
     * Speichert die aktuelle Konfiguration (Videoquelle, Widget, Stream-Ziel)
     * als neue Szene. Ein leerer Name ist ein No-op (die UI erzwingt einen
     * Namen über den lokalisierten Standardwert).
     */
    fun saveScene(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val settings = settingsRepository.appSettingsFlow.first()
            val scene = StreamScene(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                videoSource = streamingEngine.activeSourceKind.value.toSceneVideoSource(),
                widgetEnabled = settings.widgetEnabled,
                widgetShowTime = settings.widgetShowTime,
                widgetShowLocation = settings.widgetShowLocation,
                widgetShowSpeed = settings.widgetShowSpeed,
                widgetShowAltitude = settings.widgetShowAltitude,
                widgetTemplate = settings.widgetTemplate,
                streamUrl = settings.streamUrl,
                streamKey = settings.streamKey,
                streamUseTls = settings.streamUseTls,
            )
            sceneRepository.saveScene(scene)
        }
    }

    /** Wendet eine Szene an (Stream-Ziel, Widget, Videoquelle). */
    fun applyScene(scene: StreamScene) {
        viewModelScope.launch { sceneController.applyScene(scene) }
    }

    /** Löscht eine Szene (inkl. aktiver Markierung, falls sie aktiv war). */
    fun deleteScene(sceneId: String) {
        viewModelScope.launch { sceneRepository.deleteScene(sceneId) }
    }

    /** Auto-Wechsel an/aus. */
    fun setAutoSwitchEnabled(enabled: Boolean) {
        autoSceneSwitcher.setEnabled(enabled)
    }

    /** Auto-Wechsel-Intervall (Sekunden, geclampt auf das Minimum). */
    fun setAutoSwitchIntervalSeconds(seconds: Long) {
        autoSceneSwitcher.setIntervalSeconds(seconds)
    }
}

/** Engine-Quelle → Szenen-Quelle (das Domain-Modul kennt die Engine nicht). */
private fun VideoSourceKind.toSceneVideoSource(): SceneVideoSource = when (this) {
    VideoSourceKind.CAMERA -> SceneVideoSource.CAMERA
    VideoSourceKind.SCREEN_CAPTURE -> SceneVideoSource.SCREEN_CAPTURE
    // S3 (Video-Player) ist nicht implementiert — Fallback auf Kamera.
    VideoSourceKind.VIDEO_PLAYER -> SceneVideoSource.CAMERA
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
 * aktiviert TLS selbst (verifiziert an RootEncoder 2.7.5: `tlsEnabled` wird
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
