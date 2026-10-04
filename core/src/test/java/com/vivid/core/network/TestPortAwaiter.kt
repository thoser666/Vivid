package com.vivid.core.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wartet, bis ein Port wirklich Verbindungen annimmt.
 *
 * **Warum das nötig ist (#264):** Die E2E-Tests reservieren ihren Port mit
 * `ServerSocket(0).use { it.localPort }` — der Port ist damit *bekannt*, aber
 * garantiert noch nicht *belegt*. `EmbeddedServer.start(wait = false)` kehrt
 * zurück, sobald der Start angestoßen ist, nicht sobald der Bind durch ist
 * (CIO bindet asynchron). Ein direkt anschließender `connect()` läuft dann ins
 * Leere, der Client fängt den Fehlschlag und setzt `isConnected` auf `false` —
 * der Test schlägt an einer Stelle fehl, die nichts mit dem eigentlich
 * geprüften Verhalten zu tun hat.
 *
 * Der Test-Worker läuft auf ausgelasteten CI-Runnern; genau dort ist das
 * Fenster am größten. Deshalb wird hier nicht auf eine feste Wartezeit
 * gesetzt, sondern gepollt: sobald der Port erreichbar ist, geht es weiter.
 */
internal fun awaitPortListening(
    port: Int,
    host: String = "127.0.0.1",
    timeoutMs: Long = 5_000,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (canConnect(host, port)) return true
        Thread.sleep(10)
    }
    return canConnect(host, port)
}

/** Ein Verbindungsversuch; `true`, sobald der Port einen Socket annimmt. */
private fun canConnect(host: String, port: Int): Boolean = try {
    Socket().use { it.connect(InetSocketAddress(host, port), 250) }
    true
} catch (e: IOException) {
    false
}

/**
 * Verbindet sich **einmalig** auf [port] — fuer Tests, die unmittelbar nach dem
 * Server-Start pruefen wollen, ob der Port schon lauscht. Bewusst ohne Polling:
 * ein wartender Aufruf wuerde genau das wegpruefen, was der Test belegen soll.
 */
internal fun canConnectNow(port: Int, host: String = "127.0.0.1"): Boolean = canConnect(host, port)