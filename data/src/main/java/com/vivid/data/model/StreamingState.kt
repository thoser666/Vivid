package com.vivid.data.model

sealed interface StreamingState {
    object Idle : StreamingState         // Der Stream ist inaktiv
    object Connecting : StreamingState   // Der Stream baut gerade eine Verbindung auf
    object Streaming : StreamingState    // Der Stream ist aktiv
    object Disconnecting : StreamingState// Der Stream wird gerade beendet
    data class Error(val message: String) : StreamingState // Ein Fehler ist aufgetreten
}