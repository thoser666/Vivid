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
        // existiert seit v0.5.0-alpha (versionCode-Basis 5000). Range nach
        // Forensik zu Sentry VIVID-37 (#228) korrigiert: Die v0.5.16-beta-
        // Haertung (99d14bd5: Probe + CoroutineExceptionHandler) fing nur die
        // synchrone Probe, nicht das Probe->Bind-Rennen des asynchronen
        // Engine-Binds - 5162/5172 crashten weiter (fatal-Events bis 26.09.),
        // erst die Bind-Verifikation 65c93832 schloss die Luecke (v0.5.18-beta,
        // 5182). 0.5.15-beta (5152) wurde nie ausgeliefert (kein GitHub-Release,
        // keine APKs im F-Droid-Repo) - die Range bleibt ab 5000 lueckenlos.
        // Kill-Switch: Remote-Control in den Einstellungen aus.
        KnownCrashCandidate(
            id = "REMOTE-EADDRINUSE-STARTUP",
            description =
                "Absturz beim Start: Der Port der Web-Remote-Control (8080) ist belegt " +
                    "(EADDRINUSE) - z. B. durch eine andere App oder eine zweite Vivid-Instanz.",
            minVersionCode = 5000,
            maxVersionCode = 5172,
            workaround =
                "Auf Build >= 5182 (v0.5.18-beta) aktualisieren - dort wird ein belegter Port " +
                    "zuverlaessig (inkl. Bind-Rennen) abgefangen; alternativ Web-Remote-Control " +
                    "in den Einstellungen (Remote & Datenschutz) ausschalten.",
        ),
        // Issue #215 / Sentry VIVID-36 (fatal, 20 Events / 10 Nutzer,
        // 30.08.-07.09.2026): Doppeltes Scroll-Nesting im Kamera-Settings-
        // Screen - SettingsCameraScreen legte seit e636d1f1 einen eigenen
        // ungebundenen verticalScroll in den bereits scrollbaren
        // SettingsSectionScaffold; der innere Scrollable wurde mit unendlicher
        // Maximalhoehe gemessen (Compose-IllegalStateException). Still
        // entschaerft in 009972ad (Entfernung des inneren Scrolls als Beifang
        // der Robolectric-Coverage-Runde, ab v0.5.13-beta / 5132) - deshalb
        // blieb der Crash unattribuiert und Issue #164 wurde als "nicht
        // reproduzierbar" geschlossen. Keine Kill-Switch-Flaeche (der Screen
        // selbst ist optional), Workaround: Upgrade auf >= 5132.
        KnownCrashCandidate(
            id = "CAM-FOCUS-INFINITE-SCROLL",
            description =
                "Absturz beim Oeffnen der Kamera-Steuerung in den Einstellungen " +
                    "(v0.5.10-beta..v0.5.12-beta): Doppeltes Scroll-Nesting im " +
                    "Screen fuehrte zu einer unendlichen Hoehen-Messung " +
                    "(Compose-Layout-Crash).",
            minVersionCode = 5102,
            maxVersionCode = 5122,
            workaround =
                "Auf Build >= 5132 (v0.5.13-beta) aktualisieren - dort ist der " +
                    "doppelte Scroll entfernt. Uebergangsweise die Kamera-Steuerung " +
                    "nicht oeffnen.",
        ),
        // Sentry VIVID-3A/3B (Issues #225/#216; fatal, 3 Events / 3 Nutzer,
        // 09.09.-25.09.2026): NPE `Enum.name()` auf null in LogEntry.format()
        // bzw. beim errorsOnly-Levelvergleich. Ursache: LogStore.load
        // deserialisiert die JSON-Lines-Tagesdateien per Gson-Reflexion OHNE
        // Kotlin-Konstruktor - Zeilen mit fehlendem/unbekanntem level erzeugen
        // LogEntry-Instanzen mit level=null; der erste Zugriff (format(),
        // Levelvergleich) crashte. Fix: parseLine stellt die Invarianten wieder
        // her und ueberspringt verletzte Zeilen. Keine Kill-Switch-Flaeche
        // (Crash nur beim Oeffnen des Log-Viewers/der Crash-Diagnose);
        // beim naechsten Release-Schnitt die konkrete Build-Nummer im
        // workaround nachtragen.
        KnownCrashCandidate(
            id = "LOG-ENTRY-NPE-GSON-DESERIALIZE",
            description =
                "Absturz beim Oeffnen von Logs & Diagnose (oder der Crash-Diagnose): " +
                    "eine beschaedigte persistierte Log-Zeile (fehlender/unbekannter " +
                    "Level) erzeugte einen Log-Eintrag ohne Level (NPE beim Formatieren).",
            minVersionCode = 5074,
            maxVersionCode = 5194,
            workaround =
                "Auf den naechsten Release aktualisieren - dort ueberspringt der " +
                    "Log-Store beschaedigte Zeilen beim Laden.",
        ),
        // Sentry VIVID-39 (Issue #221; fatal, 4 Events / 3 Nutzer, 08.09.-
        // 21.09.2026): "Unable to start service ... StreamingService ...:
        // SecurityException: Media projections". Android 14+ verlangt fuer
        // MediaProjection.createVirtualDisplay() einen laufenden FGS vom Typ
        // mediaProjection (Manifest-Permission + startForeground-Typ); der
        // StreamingService meldete nur microphone|camera an, die Screen-
        // Capture-Quelle (S2, seit v0.5.7-beta) crashte den Prozess beim
        // Go-Live auf Android-14+-Geraeten. Fix: FOREGROUND_SERVICE_
        // MEDIA_PROJECTION + mediaProjection-Typ bei aktiver Screen-Capture-
        // Quelle + service-seitiges Catch. Kill-Switch: Screen-Capture-Quelle
        // meiden (Kamera/Video-Player/Replay verwenden).
        KnownCrashCandidate(
            id = "MEDIA-PROJECTION-FGS-TYPE",
            description =
                "Absturz beim Go-Live mit Screen-Capture-Quelle auf Android 14+: " +
                    "der Streaming-Service meldet den FGS-Typ mediaProjection nicht " +
                    "(Sicherheitsausnahme, der Prozess bricht beim Bildschirm-Stream ab).",
            minVersionCode = 5074,
            maxVersionCode = 5194,
            workaround =
                "Auf den naechsten Release aktualisieren - dort meldet der " +
                    "Streaming-Service den mediaProjection-FGS-Typ automatisch. " +
                    "Vorlaeufig eine andere Videoquelle verwenden (Kamera, " +
                    "Video-Player oder Replay) statt Screen-Capture.",
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
