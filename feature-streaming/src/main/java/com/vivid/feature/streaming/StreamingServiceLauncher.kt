package com.vivid.feature.streaming

/**
 * Startet/stoppt den Streaming-Foreground-Service, damit der Stream weiterläuft,
 * wenn die App im Hintergrund ist (Prozess-Priorität + WakeLock).
 *
 * Als Interface, damit das [StreamingViewModel] ohne Android-Kontext unit-testbar
 * ist — die echte Implementierung lebt im App-Modul.
 */
interface StreamingServiceLauncher {

    /** Startet den Foreground-Service, der [url] streamt. */
    fun startStreaming(url: String)

    /** Stoppt den Foreground-Service (und damit den Stream). */
    fun stopStreaming()
}
