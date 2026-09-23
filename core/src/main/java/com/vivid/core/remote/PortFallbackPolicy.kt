package com.vivid.core.remote

/**
 * Reine Port-Fallback-Kette der Web-Remote-Control (ohne Android/Netz-Abhängigkeiten):
 *
 * Ist der bevorzugte Port (8080) belegt (EADDRINUSE), wird deterministisch die
 * Kette 8081 → 8082 → 8083 durchprobiert (relativ zum Preferred-Port, für
 * 8080 also genau diese). Ist auch die gesamte Kette belegt, greift als
 * letzter Ausweg ein **ephemeraler Port** — der Kernel wählt beim Bind einen
 * freien Port. Der tatsächlich gebundene Port wird über
 * [RemoteControlServer.activePort] angezeigt (Settings zeigen ihn reaktiv).
 *
 * Die Auswahl ist bewusst deterministisch (feste Kette statt Zufall), damit
 * der Port in Dokumentation/Firewall-Regeln vorhersagbar bleibt und zwei
 * Vivid-Instanzen stabil unterschiedliche Ports belegen.
 */
object PortFallbackPolicy {

    /** Feste Ausweich-Offsets nach dem bevorzugten Port (8080 → 8081/8082/8083). */
    val FALLBACK_OFFSETS: IntArray = intArrayOf(1, 2, 3)

    /** Letzter Ausweg: ephemeraler Port (Kernel wählt beim Bind einen freien). */
    const val EPHEMERAL_PORT: Int = 0

    /** true, wenn [port] der ephemeralen Auswahl entspricht. */
    fun isEphemeral(port: Int): Boolean = port == EPHEMERAL_PORT

    /** Kette als [preferred] + Offsets + ephemeral. */
    fun chain(preferred: Int): IntArray =
        intArrayOf(preferred) + FALLBACK_OFFSETS.map { preferred + it }.toIntArray() + intArrayOf(EPHEMERAL_PORT)

    /**
     * Naechster Kandidat der Kette nach [current]; hinter dem letzten festen
     * Kandidaten folgt [EPHEMERAL_PORT] (auch wenn [current] selbst bereits
     * ephemeral ist — der Aufrufer entscheidet ueber die Wiederholung).
     */
    fun nextCandidate(preferred: Int, current: Int): Int =
        chain(preferred).dropWhile { it != current }.drop(1).firstOrNull() ?: EPHEMERAL_PORT

    /**
     * Wählt den ersten Port, dessen Probe gelingt.
     *
     * [probe] wirft für einen belegten Port (z. B. `java.net.BindException`);
     * der Kandidat wird dann übersprungen. Der ephemerale Kandidat (0) wird
     * **nicht** probiert — er gilt per Definition als verfügbar.
     */
    fun selectPort(preferred: Int, probe: (Int) -> Unit): Int {
        for (candidate in chain(preferred)) {
            if (candidate == EPHEMERAL_PORT) return candidate
            try {
                probe(candidate)
                return candidate
            } catch (_: Exception) {
                // Port belegt (oder Probe fehlgeschlagen) → nächsten Kandidaten.
            }
        }
        // Unerreichbar (Kette endet mit ephemeral), defensiv für den Compiler.
        return EPHEMERAL_PORT
    }
}
