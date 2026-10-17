package com.vivid.feature.streaming.source.uvc

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.library.multiple.MultiStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt den UVC-Encoder ([MultiStream]) für #305.
 *
 * Analog zu [com.vivid.feature.streaming.CameraFactory] (MultiCamera2), damit
 * die Engine die Instanz über eine mockbare Abstraktion erzeugt und die
 * ConnectChecker (pro Stream-Ziel) injizieren kann.
 *
 * Warum [MultiStream] statt `MultiCamera2`: `MultiCamera2` erweitert
 * `Camera2Base` und kann keine eigene `VideoSource` aufnehmen; `MultiStream`
 * (verifiziert an RootEncoder 2.7.5) akzeptiert im Konstruktor eine eigene
 * Video- ([VividUvcVideoSource]) und Audio-Quelle ([MicrophoneSource] — kein
 * USB-Audio-Mixing) und fächert *einen* encodierten Ausgang auf unabhängige
 * RTMP-Clients auf (ein Checker pro Ziel). Es wird nie eine zweite
 * `GenericStream`-Instanz geöffnet (kein doppelter UVC-Zugriff).
 */
interface UvcStreamFactory {
    fun create(connectCheckers: List<ConnectChecker>): MultiStream
}

/** Die echte Implementierung für die App (RootEncoder Multi-Stream über UVC). */
class RtmpUvcStreamFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uvcVideoSource: VividUvcVideoSource,
) : UvcStreamFactory {
    override fun create(connectCheckers: List<ConnectChecker>): MultiStream {
        // Nicht genutzte Protokolle mit leeren Arrays deaktiviert (wie bei
        // MultiCamera2/MultiDisplay); mindestens ein RTMP-Checker ist Pflicht
        // (sonst wirft MultiStream IllegalArgumentException).
        return MultiStream(
            context,
            connectCheckers.toTypedArray(), // rtmp
            emptyArray(), // rtsp
            emptyArray(), // srt
            emptyArray(), // udp
            uvcVideoSource,
            MicrophoneSource(), // Mikrofon bleibt aktiv, kein USB-Audio-Mixing
        )
    }
}
