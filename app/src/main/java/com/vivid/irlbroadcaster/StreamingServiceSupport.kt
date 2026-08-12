package com.vivid.irlbroadcaster

import com.vivid.feature.streaming.StreamingState

/**
 * Konstanten und pure Helfer für den Streaming-Foreground-Service.
 *
 * Bewusst OHNE Android-Imports, damit diese Logik in reinen JVM-Unit-Tests
 * getestet werden kann.
 */
object StreamingServiceSupport {

    const val CHANNEL_ID = "vivid_streaming"
    const val CHANNEL_NAME = "Live-Stream"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START_STREAM = "com.vivid.action.START_STREAM"
    const val ACTION_STOP_STREAM = "com.vivid.action.STOP_STREAM"

    const val EXTRA_STREAM_URL = "com.vivid.extra.STREAM_URL"

    /** Titel der persistenten Notification, abhängig vom Engine-Status. */
    fun notificationTitle(state: StreamingState): String = when (state) {
        is StreamingState.Streaming -> "Vivid sendet live"
        is StreamingState.Failed -> "Stream fehlgeschlagen"
        else -> "Stream wird vorbereitet …"
    }

    /** Text der persistenten Notification, abhängig vom Engine-Status. */
    fun notificationText(state: StreamingState): String = when (state) {
        is StreamingState.Streaming ->
            "Der Livestream läuft im Hintergrund weiter — tippe zum Beenden."
        is StreamingState.Failed ->
            "Die Verbindung ist fehlgeschlagen (${state.reason}) — tippe zum Beenden."
        else ->
            "Der Stream läuft im Hintergrund weiter — tippe zum Beenden."
    }

    /** True, wenn bei diesem Status die Stop-Aktion angezeigt werden soll. */
    fun showStopAction(state: StreamingState): Boolean =
        state is StreamingState.Streaming || state is StreamingState.Failed
}
