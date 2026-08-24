package com.vivid.feature.streaming.source

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.library.multiple.MultiDisplay
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt die Screen-Capture-Encoder-Instanz ([MultiDisplay]) für S2.
 *
 * Analog zu [com.vivid.feature.streaming.CameraFactory] (MultiCamera2), damit die
 * [com.vivid.feature.streaming.StreamingEngine] die MultiDisplay-Instanz über eine
 * mockbare Abstraktion erzeugt und die ConnectChecker (pro Stream-Ziel) injizieren
 * kann — die Checker aktualisieren den Ziel-Status der Engine.
 */
interface DisplayFactory {
    fun create(connectCheckers: List<ConnectChecker>): MultiDisplay
}

/** Die echte Implementierung für die App (RootEncoder Screen-Share). */
class RtmpDisplay2Factory @Inject constructor(
    @ApplicationContext private val context: Context,
) : DisplayFactory {
    override fun create(connectCheckers: List<ConnectChecker>): MultiDisplay {
        // MultiDisplay(context, useOpenGL = true, rtmp, rtsp, srt, udp) — analog zu
        // MultiCamera2 werden nicht genutzte Protokolle mit leeren Arrays deaktiviert.
        // Der Screen-Share-Encoder (MediaProjection + VirtualDisplay + ImageReader)
        // wird komplett von RootEncoder verwaltet; die App liefert nur den Consent
        // (sendIntent/setIntentResult) und steuert prepare/start/stop.
        return MultiDisplay(
            context,
            true,
            connectCheckers.toTypedArray(), // rtmp
            emptyArray(), // rtsp
            emptyArray(), // srt
            emptyArray(), // udp
        )
    }
}
