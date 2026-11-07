package com.vivid.feature.streaming

import com.vivid.core.data.CameraMode
import com.vivid.core.data.AppSettings
import com.vivid.core.data.AppOrientation
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.resolveEncoderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One saved-settings snapshot and preparation path for UI, web remote, and chat starts. */
@Singleton
class StreamStartCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val engine: StreamingEngine,
    private val launcher: StreamingServiceLauncher,
    internal var encoderCapabilities: EncoderCapabilities,
) {
    private val startMutex = Mutex()
    private val _configIssues = MutableStateFlow<List<StreamConfigIssue>>(emptyList())
    val configIssues: StateFlow<List<StreamConfigIssue>> = _configIssues.asStateFlow()

    suspend fun checkSettings() {
        _configIssues.value = validate(settingsRepository.appSettingsFlow.first())
    }

    suspend fun start(onPrepared: () -> Unit = {}) = startMutex.withLock {
        val state = engine.streamingState.value
        if (state is StreamingState.Preparing || state is StreamingState.Streaming) return@withLock
        val settings = settingsRepository.appSettingsFlow.first()
        val issues = validate(settings)
        _configIssues.value = issues
        if (issues.any { it.severity == ConfigIssueSeverity.ERROR }) return@withLock

        val urls = buildList {
            buildStreamUrl(settings.streamUrl, settings.streamKey, settings.streamUseTls)?.let(::add)
            buildStreamUrl(settings.secondaryStreamUrl, settings.secondaryStreamKey, settings.secondaryStreamUseTls)?.let(::add)
        }
        if (urls.isEmpty()) {
            _configIssues.value = issues + StreamConfigIssue(ConfigIssueSeverity.ERROR, R.string.stream_error_no_url)
            return@withLock
        }
        val resolved = if (settings.encoderAutoFallback && settings.cameraMode == CameraMode.STANDARD) {
            resolveEncoderConfig(settings.videoCodecPreference, settings.encoderPreset, orientedEncoderCapabilities(encoderCapabilities, settings.appOrientation == AppOrientation.PORTRAIT), settings.videoFps ?: settings.encoderPreset.fps)
        } else {
            ResolvedEncoderConfig(settings.videoCodecPreference, settings.encoderPreset, fallbackApplied = false, fps = settings.videoFps ?: settings.encoderPreset.fps)
        }
        engine.configureCameraMode(if (settings.videoFps == 30) CameraMode.STANDARD else settings.cameraMode)
        engine.configureStreamVideo(settings)
        engine.configureEncoder(resolved, settings.encoderAutoFallback)
        engine.configureAdaptiveBitrate(settings.adaptiveBitrateEnabled)
        onPrepared()
        launcher.startStreaming(urls)
    }

    private fun validate(settings: AppSettings): List<StreamConfigIssue> = StreamConfigValidator.validate(
        streamUrl = settings.streamUrl,
        streamKey = settings.streamKey,
        streamUseTls = settings.streamUseTls,
        secondaryStreamUrl = settings.secondaryStreamUrl,
        secondaryStreamKey = settings.secondaryStreamKey,
        secondaryStreamUseTls = settings.secondaryStreamUseTls,
    )
}
