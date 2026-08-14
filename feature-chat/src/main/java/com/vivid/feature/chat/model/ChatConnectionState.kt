package com.vivid.feature.chat.model

sealed interface ChatConnectionState {
    data object Disconnected : ChatConnectionState
    data object Connecting : ChatConnectionState
    data class Connected(val channel: String) : ChatConnectionState
}
