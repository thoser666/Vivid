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
    const val ACTION_GRANT_SCENE_PROJECTION = "com.vivid.action.GRANT_SCENE_PROJECTION"
    const val EXTRA_SCENE_SOURCE_ID = "com.vivid.extra.SCENE_SOURCE_ID"
    const val EXTRA_PROJECTION_RESULT = "com.vivid.extra.PROJECTION_RESULT"
    const val EXTRA_PROJECTION_DATA = "com.vivid.extra.PROJECTION_DATA"
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
     * Sichtbare Ursache eines Failed-Zustands.
     *
     * Lokalisierte Ressource ([StreamingState.Failed.reasonRes]) vor der
     * Rohdiagnose der RTMP-/Encoder-Library ([StreamingState.Failed.reason]) —
     * eigene Ausgaben sind i18n-Pflicht, Fremdtexte sind nicht übersetzbar.
     * Der Resolver ist injizierbar, damit die Auswahl pur testbar ist.
     */
    fun failureText(state: StreamingState, resolve: (Int) -> String): String {
        val failed = state as? StreamingState.Failed ?: return ""
        return failed.reasonRes?.let(resolve) ?: failed.reason.orEmpty()
    }

    /**
     * True, wenn der Streaming-Service den FGS-Typ `mediaProjection` anmelden muss.
     *
     * Android 14+ verlangt für jeden `MediaProjection.createVirtualDisplay()`-
     * Aufruf einen bereits laufenden Foreground-Service vom Typ `mediaProjection`
     * — fehlt er, wirft der Aufruf eine SecurityException und der Service stirbt
     * beim Start als `Unable to start service` (Sentry VIVID-39, Issue #221). Die
     * Legacy-Screen-Quelle und zusätzliche Scene-Projection-Grants benötigen
     * diesen Typ; andere Quellen allein benötigen ihn nicht.
     */
    fun requiresMediaProjectionFgs(sourceKind: VideoSourceKind, sceneProjectionActive: Boolean = false): Boolean =
        sourceKind == VideoSourceKind.SCREEN_CAPTURE || sceneProjectionActive
}
