package com.vivid.irlbroadcaster

import androidx.annotation.StringRes
import com.vivid.R
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.source.VideoSourceKind

/**
 * Konstanten und pure Helfer für den Streaming-Foreground-Service.
 *
 * Bewusst OHNE weitere Android-Imports (nur die Annotation + die R-Klasse,
 * eine reine Konstanten-Klasse), damit diese Logik in reinen JVM-Unit-Tests
 * getestet werden kann. Die Notification-Texte sind String-Ressourcen (i18n) —
 * der Service löst sie per `getString(...)` auf.
 */
object StreamingServiceSupport {

    const val CHANNEL_ID = "vivid_streaming"
    @StringRes
    val CHANNEL_NAME_RES: Int = R.string.notif_channel_name
    const val NOTIFICATION_ID = 1001

    const val ACTION_START_STREAM = "com.vivid.action.START_STREAM"
    const val ACTION_STOP_STREAM = "com.vivid.action.STOP_STREAM"

    /** Stream-Ziele (RTMP-URLs) als ArrayList<String> — Multi-Streaming. */
    const val EXTRA_STREAM_URLS = "com.vivid.extra.STREAM_URLS"

    /** Titel-Ressource der persistenten Notification, abhängig vom Engine-Status. */
    @StringRes
    fun notificationTitleRes(state: StreamingState): Int = when (state) {
        is StreamingState.Streaming -> R.string.notif_title_live
        is StreamingState.Failed -> R.string.notif_title_failed
        else -> R.string.notif_title_preparing
    }

    /** Text-Ressource der persistenten Notification, abhängig vom Engine-Status. */
    @StringRes
    fun notificationTextRes(state: StreamingState): Int = when (state) {
        is StreamingState.Streaming -> R.string.notif_text_streaming
        is StreamingState.Failed -> R.string.notif_text_failed
        else -> R.string.notif_text_preparing
    }

    /** True, wenn bei diesem Status die Stop-Aktion angezeigt werden soll. */
    fun showStopAction(state: StreamingState): Boolean =
        state is StreamingState.Streaming || state is StreamingState.Failed

    /**
     * True, wenn der Streaming-Service den FGS-Typ `mediaProjection` anmelden muss.
     *
     * Android 14+ verlangt für jeden `MediaProjection.createVirtualDisplay()`-
     * Aufruf einen bereits laufenden Foreground-Service vom Typ `mediaProjection`
     * — fehlt er, wirft der Aufruf eine SecurityException und der Service stirbt
     * beim Start als `Unable to start service` (Sentry VIVID-39, Issue #221). Nur
     * die Screen-Capture-Quelle (S2) streamt über genau diesen Pfad; Kamera,
     * Video-Player und Replay brauchen den Typ nicht.
     */
    fun requiresMediaProjectionFgs(sourceKind: VideoSourceKind): Boolean =
        sourceKind == VideoSourceKind.SCREEN_CAPTURE
}
