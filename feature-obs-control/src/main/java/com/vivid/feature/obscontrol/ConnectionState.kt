package com.vivid.feature.obscontrol

import androidx.annotation.StringRes

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()

    /**
     * Fehlerzustand. [messageRes] (i18n) hat Vorrang; [message] trägt
     * technische Details (z. B. eine Exception-Message aus dem Repository).
     */
    data class Error(
        val message: String = "",
        @StringRes val messageRes: Int = 0,
    ) : ConnectionState()
}
