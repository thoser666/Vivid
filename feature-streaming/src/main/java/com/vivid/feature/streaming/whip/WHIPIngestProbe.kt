package com.vivid.feature.streaming.whip

import android.content.Context
import org.webrtc.PeerConnectionFactory
import timber.log.Timber

/**
 * P0-Spike-Probe (Analysedokument docs/whip-spike.md §5): Der einzige
 * `org.webrtc`-Importpunkt im Spike — beweist Compile-Link und Runtime-Load
 * der 49-MB-AAR (dlopen der libjingle-`.so` + Java-Bridge). Kein Produktivpfad;
 * die Media-Bridge (WHIPStreamRoute) ist P1. Der WHIP-HTTP-Contract
 * (POST/PATCH/DELETE) ist bereits durch die JVM-E2E-Tests in `core`
 * (WHIPClientTest, Ktor-Testserver) vollständig abgedeckt — hier wird bewusst
 * kein zweiter HTTP-Pfad gebaut.
 */
object WHIPIngestProbe {

    /**
     * PeerConnectionFactory-Initialisierung ohne Kamera/Audio-Pfad. Wirft
     * bewusst nicht — Ergebnis `false` + Log bei Fehler (fehlertoleranter
     * Spike-Vertrag). Aufruf nur aus einem Spike-Debug-Einstiegspunkt
     * (kein Produktivpfad in P0).
     */
    fun sdkSmoke(appContext: Context): Boolean = runCatching {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val ok = factory != null
        runCatching { factory?.dispose() }
        ok
    }.onFailure {
        Timber.e(it, "WHIP P0 SDK-Smoke fehlgeschlagen")
    }.getOrDefault(false)
}
