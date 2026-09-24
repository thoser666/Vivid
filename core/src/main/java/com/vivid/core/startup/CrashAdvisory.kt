package com.vivid.core.startup

/**
 * Ein bekannter, versionsgebundener Crash-Kandidat.
 *
 * Einträge beschreiben einen geschlossenen versionCode-Bereich ([minVersionCode]
 * bis [maxVersionCode], beide inklusiv), in dem eine veröffentlichte Vivid-
 * Version einen bekannten Absturz trägt. Sie werden **pro Release gepflegt**:
 * Fliest der Fix in Build N, verschiebt sich [maxVersionCode] auf N-1 bzw. der
 * Eintrag wird entfernt, sobald keine unterstützte Version mehr in der Range
 * liegt.
 *
 * Die Registry ist bewusst **leer startend** — ein Eintrag wird erst gesetzt,
 * wenn ein Crash real identifiziert ist (z. B. über Sentry/In-App-Log), nie
 * spekulativ.
 */
data class KnownCrashCandidate(
    /** Stabile Kurz-ID für Logs/Suche, z. B. `S23-STARTUP-BIND`. */
    val id: String,

    /** Menschliche Beschreibung des Absturzes (erscheint im In-App-Log). */
    val description: String,

    /** Erster (inklusiver) versionCode, der den Crash trägt. */
    val minVersionCode: Int,

    /** Letzter (inklusiver) versionCode, der den Crash trägt. */
    val maxVersionCode: Int,

    /** Optionaler Handlungshinweis für den Nutzer (z. B. „auf Build ≥ N aktualisieren"). */
    val workaround: String? = null,
)

/**
 * Ergebnis der Start-Prüfung: Ein [KnownCrashCandidate] trifft auf die
 * installierte Version zu und soll beim App-Start gemeldet werden.
 */
data class CrashAdvisory(
    val candidate: KnownCrashCandidate,
    val installedVersionCode: Int,
) {
    /** Mehrzeilige, deutliche Meldung für das In-App-Log (WARN). */
    fun reportMessage(): String = buildString {
        append("⚠ Crash-Advisory [").append(candidate.id).append("]: ")
        append(candidate.description)
        append(" (installierter versionCode ").append(installedVersionCode)
        append(" in betroffenem Bereich ")
        append(candidate.minVersionCode).append("..").append(candidate.maxVersionCode)
        append(")")
        candidate.workaround?.let { append(" — ").append(it) }
    }
}

/**
 * Start-Entscheidung (pure, ohne Android): Liefert den ersten bekannten
 * Crash-Kandidaten, dessen versionCode-Bereich die installierte Version
 * einschließt — oder `null`, wenn diese Version sauber ist.
 *
 * Bei überlappenden Kandidaten gewinnt der **erste** in der Liste (die
 * Registry ist handgepflegt und klein; Reihenfolge = Priorität).
 */
object CrashAdvisoryRegistry {

    /**
     * Bekannte Crash-Kandidaten der aktuellen Release-Generation. **Leer**
     * bedeutet: keine identifizierten versionengebundenen Crashes. Erster
     * Eintrag entsteht, sobald ein realer Crash (Sentry, In-App-Log des
     * Nutzers) einer Version zugeordnet ist — der auskommentierte Bauplan
     * zeigt das Format.
     */
    val KNOWN: List<KnownCrashCandidate> = listOf(
        // Real identifizierter Widget-Render-Crash (Sentry-Befund 24.09.2026):
        // PatternSyntaxException in Androids ICU-Regex-Engine beim <clinit> von
        // TextInfoWidgetViewModel (unmaskiertes schließendes '}') →
        // ExceptionInInitializerError über die Hilt-ViewModel-Fabrik → Crash beim
        // Komponieren des Text-Info-Widgets im Streaming-Overlay (TextInfoWidget
        // wird in DefaultStreamingOverlay bedingungslos komponiert). Feature
        // {road}/{city}/{country} seit 036c69c4 → in jedem Release ab v0.5.14
        // (5144). Getroffen: Geräte mit strenger ICU-Engine; JDK-Tests sahen den
        // Fehler nie (JVM-Regex akzeptiert bare '}'). Kill-Switch: Text-Widget in
        // den Einstellungen deaktivieren. Fix (Klammern maskiert) + statischer
        // ICU-Regex-Guard (check_icu_regex_braces.sh) im selben Commit.
        KnownCrashCandidate(
            id = "TEXT-INFO-WIDGET-REGEX-ICU",
            description =
                "Absturz beim Start/Streaming-Screen: Die Android-ICU-Regex-Engine " +
                    "rejectet ein internes Pattern des Text-Info-Widgets (unmaskierte " +
                    "Klammer) — ViewModel-Konstruktion stirbt beim Rendern des Overlays.",
            minVersionCode = 5144,
            maxVersionCode = 5182,
            workaround =
                "Auf Build >= 5192 aktualisieren — dort ist das Pattern maskiert. " +
                    "Vorläufig: Text-Info-Widget in den Einstellungen (Widgets) " +
                    "deaktivieren, dann Streaming ohne Overlay nutzen.",
        ),
        // Erster real identifizierter Start-Crash (Nutzerbericht S23, In-App-Log):
        // BindException EADDRINUSE in RemoteControlServer.start(), wenn Port
        // 8080 bereits belegt ist (zweite App/Instanz). Remote-Autostart
        // existiert seit v0.5.0-alpha (versionCode-Basis 5000); die Haertung
        // (Graceful-Skip + Log) landete in v0.5.16-beta (5162) — davor crashte
        // der Prozess. Kill-Switch: Remote-Control in den Einstellungen aus.
        KnownCrashCandidate(
            id = "REMOTE-EADDRINUSE-STARTUP",
            description =
                "Absturz beim Start: Der Port der Web-Remote-Control (8080) ist belegt " +
                    "(EADDRINUSE) - z. B. durch eine andere App oder eine zweite Vivid-Instanz.",
            minVersionCode = 5000,
            maxVersionCode = 5144,
            workaround =
                "Auf Build >= 5162 (v0.5.16-beta) aktualisieren - dort wird ein belegter Port " +
                    "abgefangen; alternativ Web-Remote-Control in den Einstellungen (Remote & " +
                    "Datenschutz) ausschalten.",
        ),
        // Bauplan fuer weitere Eintraege (keine spekulativen Eintraege):
        // KnownCrashCandidate(
        //     id = "EXAMPLE-STARTUP-CRASH",
        //     description = "Absturz beim Start auf <Geraetestyp>, Ursache <kurz>",
        //     minVersionCode = 5080,
        //     maxVersionCode = 5090,
        //     workaround = "Auf Build >= 5091 aktualisieren",
        // ),
    )

    /** Reine Entscheidung: trifft [versionCode] einen Kandidaten? */
    fun evaluate(
        versionCode: Int,
        candidates: List<KnownCrashCandidate> = KNOWN,
    ): CrashAdvisory? = candidates
        .firstOrNull { versionCode in it.minVersionCode..it.maxVersionCode }
        ?.let { CrashAdvisory(it, versionCode) }
}
