package com.vivid.feature.streaming

import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteStreamStatus
import com.vivid.core.remote.StreamControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Mappt den Engine-Status auf den Remote-Status (pur, für Tests). */
internal fun mapToRemoteStatus(state: StreamingState): RemoteStreamStatus = when (state) {
    is StreamingState.Idle -> RemoteStreamStatus.IDLE
    is StreamingState.Preparing -> RemoteStreamStatus.PREPARING
    is StreamingState.Streaming -> RemoteStreamStatus.STREAMING
    is StreamingState.Failed -> RemoteStreamStatus.FAILED
}

/**
 * Verdrahtet die Web-Remote-Control mit der [StreamingEngine]:
 * liest die gespeicherten Stream-Einstellungen (URL/Key) und mappt den
 * Engine-Status auf [RemoteStreamStatus].
 *
 * Der [scope] ist injizierbar, damit Tests einen Test-Dispatcher verwenden können.
 */
@Singleton
class StreamingEngineStreamControl @Inject constructor(
    private val engine: StreamingEngine,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) : StreamControl {

    override val status: StateFlow<RemoteStreamStatus> = engine.streamingState
        .map { mapToRemoteStatus(it) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = RemoteStreamStatus.IDLE,
        )

    override suspend fun start() {
        val settings = settingsRepository.appSettingsFlow.first()
        val url = buildStreamUrl(settings.streamUrl, settings.streamKey, settings.streamUseTls)
        if (url != null) {
            engine.startStream(url)
        }
    }

    override fun stop() {
        engine.stopStream()
    }
}
