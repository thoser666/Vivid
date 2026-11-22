package com.vivid.feature.streaming

import androidx.annotation.StringRes

sealed class StreamingState {
    object Idle : StreamingState()
    object Preparing : StreamingState()
    object Streaming : StreamingState()

    /**
     * @param reason Rohdiagnose von außen (z. B. `onConnectionFailed` der
     *   RTMP-Library) — bleibt unübersetzt, weil der Text vom Server/Encoder
     *   kommt und keine App-Ausgabe ist.
     * @param reasonRes Lokalisierte Ursache aus der App (res/values/strings.xml
     *   und die Sprachvarianten davon, i18n-Pflicht für eigene Ausgaben); hat in
     *   UI und Notification Vorrang vor [reason].
     */
    data class Failed(
        val reason: String? = null,
        @StringRes val reasonRes: Int? = null,
    ) : StreamingState()
}
