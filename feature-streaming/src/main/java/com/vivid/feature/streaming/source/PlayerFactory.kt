package com.vivid.feature.streaming.source

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.multiple.MultiFromFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt die Video-Datei-Encoder-Instanz ([MultiFromFile]) für S3.
 *
 * Analog zu [com.vivid.feature.streaming.CameraFactory] (MultiCamera2) und
 * [DisplayFactory] (MultiDisplay), damit die [com.vivid.feature.streaming.StreamingEngine]
 * die Instanz über eine mockbare Abstraktion erzeugt und die ConnectChecker
 * (pro Stream-Ziel) injizieren kann — die Checker aktualisieren den Ziel-Status
 * der Engine.
 */
interface PlayerFactory {
    fun create(connectCheckers: List<ConnectChecker>): MultiFromFile
}

/** Die echte Implementierung für die App (RootEncoder Video-Datei-Wiedergabe). */
class RtmpPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayerFactory {
    override fun create(connectCheckers: List<ConnectChecker>): MultiFromFile {
        // MultiFromFile(context, videoDecoderInterface, audioDecoderInterface,
        // rtmp, rtsp, srt, udp) — analog zu MultiCamera2/MultiDisplay werden nicht
        // genutzte Protokolle mit leeren Arrays deaktiviert. Die Decoder-Interfaces
        // sind reine Finished-Callbacks (leer = Library-Default-Verhalten).
        return MultiFromFile(
            context,
            VideoDecoderInterface { },
            AudioDecoderInterface { },
            connectCheckers.toTypedArray(), // rtmp
            emptyArray(), // rtsp
            emptyArray(), // srt
            emptyArray(), // udp
        )
    }
}