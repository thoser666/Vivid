package com.vivid.feature.streaming.whip

import android.content.Context

/**
 * JVM-testbare Hülle um den P0-Gerätesmoke (docs/whip-spike.md §11.8): ruft die
 * injizierte Smoke-Aktion (default: [WHIPIngestProbe.sdkSmoke]) und mappt das
 * Ergebnis auf ein diagnosefähiges [WhipSmokeResult]. Bewusst schmal — kein
 * HTTP-Zweitpfad (WHIP-Contract liegt in core), keine Ktor-Abhängigkeit
 * (feature-streaming sieht den Ktor-HttpClient-Typ von core nicht).
 *
 * Diagnose-Mehrwert gegenüber rohem `sdkSmoke`: Ein Wurf des Smoke-Aufrufs
 * (z. B. `UnsatisfiedLinkError` beim dlopen der libjingle-`.so` oder
 * `NoClassDefFoundError` auf R8-gestrippte `org.webrtc`-Member) wird hier
 * abgefangen und mit Klassenname + Message im Detail gemeldet, statt den
 * Debug-Einstiegspunkt abstürzen zu lassen — genau die Laufzeitverifikation,
 * die der Gerätesmoke erbringen soll.
 */
object WHIPSmokeRunner {

    data class WhipSmokeResult(
        val ok: Boolean,
        val detail: String,
    )

    fun run(
        appContext: Context,
        smoke: (Context) -> Boolean = WHIPIngestProbe::sdkSmoke,
    ): WhipSmokeResult = try {
        if (smoke(appContext)) {
            WhipSmokeResult(
                ok = true,
                detail = "PeerConnectionFactory initialisiert und disposed — " +
                    "dlopen/RegisterNatives bestanden (Gerätesmoke C1).",
            )
        } else {
            WhipSmokeResult(
                ok = false,
                detail = "sdkSmoke lieferte false — Ursache im Logcat (Tag WHIPIngestProbe); " +
                    "häufig: dlopen der libjingle-.so fehlgeschlagen (ABI?).",
            )
        }
    } catch (t: Throwable) {
        WhipSmokeResult(
            ok = false,
            detail = "${t::class.java.name}: ${t.message ?: "(keine Message)"} — typische " +
                "Ursachen: R8 hat org.webrtc-Member gestrippt (Keep-Rules app/proguard-rules.pro) " +
                "oder ABI fehlt auf dem Gerät.",
        )
    }
}
