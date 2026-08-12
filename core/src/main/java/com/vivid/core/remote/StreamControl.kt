package com.vivid.core.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstrahiert die Steuerung des laufenden Streams für die Web-Remote-Control.
 *
 * Die Implementierung liegt im `feature-streaming`-Modul (`StreamingEngine`),
 * damit `core` nicht von der Engine abhängt.
 */
interface StreamControl {
    /** Aktueller Stream-Status (IDLE / PREPARING / STREAMING / FAILED). */
    val status: StateFlow<RemoteStreamStatus>

    /** Startet den Stream mit den gespeicherten Einstellungen. */
    suspend fun start()

    /** Stoppt den Stream. */
    fun stop()
}
