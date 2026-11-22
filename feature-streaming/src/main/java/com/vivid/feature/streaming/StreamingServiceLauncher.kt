package com.vivid.feature.streaming

/**
 * Startet/stoppt den Streaming-Foreground-Service, damit der Stream weiterläuft,
 * wenn die App im Hintergrund ist (Prozess-Priorität + WakeLock).
 *
 * Als Interface, damit das [StreamingViewModel] ohne Android-Kontext unit-testbar
 * ist — die echte Implementierung lebt im App-Modul.
 */
interface StreamingServiceLauncher {

    /** Startet den Foreground-Service, der die übergebenen [urls] streamt. */
    fun startStreaming(urls: List<String>)

    /** Promote capture before consuming a scene's one-use MediaProjection consent. */
    fun startSceneProjection(sourceId: String, resultCode: Int, data: android.content.Intent): Boolean = false

    /** Stoppt den Foreground-Service (und damit den Stream). */
    fun stopStreaming()
}
