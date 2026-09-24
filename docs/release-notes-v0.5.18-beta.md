# Release Notes: Vivid v0.5.18-beta

**Release Date:** 2026-09-24
**Version:** 0.5.18-beta (versionCode 5182, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest“ · Obtainium · eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** Seit `v0.5.17-beta` (22.09.) sind 35 Commits zusammengekommen — Anlass ist der **erste real identifizierte Start-Crash** aus dem Galaxy-S23-Bericht: Ein belegter Port 8080 warf beim Start der Web-Remote-Control synchron (`EADDRINUSE`) und tötete auf Versionen vor der v0.5.16-Härtung den Prozess. Mit diesem Cut ist die Ursache dreifach entschärft (graceful Behandlung + Kill-Switch, automatische Port-Fallback-Kette, Bind-Verifikations-Loop) — und die Crash-Diagnose bekommt mit dem **Sentry Error-Replay** ein Werkzeug für die Fälle, die nur im Feld passieren.

## 🎉 Highlights

### 🩹 Web-Remote: Start-Crash durch belegten Port 8080 behoben (EADDRINUSE + Kill-Switch)
- Belegter Port 8080 wirft in `RemoteControlServer.start()` **keine Exception mehr**: Die `BindException` wird abgefangen (graceful: kein Server statt Prozess-Absturz); der Probe-Port bleibt als Test-API bestehen.
- Neues Setting **remoteControlEnabled** (Kill-Switch, Default an): Autostart-Gate in `VividApplication` + Toggle unter „Remote & Datenschutz“ (DE/EN/FR inkl. Robolectric-Tests). Wer die Remote-Control bewusst nicht nutzt, kann sie damit vollständig abschalten.
- `CrashAdvisoryRegistry` mit dem **ersten echten Eintrag** `REMOTE-EADDRINUSE-STARTUP` (versionCode 5000–5144, Workaround: Upgrade oder Kill-Switch) — die rote 💥-Advisory-Zeile im Log-Viewer verweist jetzt auf einen realen, versionsgebundenen Fall statt auf die anfangs leere Registry.

### 🔌 Automatische Port-Fallback-Kette (8080 → 8081 → … → ephemeral)
- Ein belegter Port ist keine Endstation mehr: Die neue `PortFallbackPolicy` wählt **deterministisch den ersten freien Kandidaten** der Kette 8080 → 8081 → 8082 → 8083 → ephemeral und startet den Server dort; der Ausweichport erscheint reaktiv in den Settings (`RemoteControlInfo.port`) mit erklärendem Hinweis (DE/EN/FR).
- **Zwei Härtungen gegen die Probe-Bind-Lücke:** Der Probe-Bind wurde auf NIO umgestellt (Ktor CIO 3.5.2 bindet denselben Stack; `java.net.ServerSocket` gewann auf Windows kontra NIO-Channels und lieferte falsch-grüne Probes), und nach jedem Engine-Start verifiziert eine **Bind-Verifikations-Loop**, dass die Engine den Port wirklich gebunden hat (der Probe-Bind muss jetzt fehlschlagen, sonst rückt das nächste Kettenglied nach; der ephemeralen Kernel-Wahl sind 3 Versuche vergönnt).
- Endet die Kette erfolglos: fehlertolerant **kein Server statt Crash** — der EADDRINUSE-Vertrag gilt für alle Fehlerpfade. Die Tests laufen gegen IANA-unassignierte Cold-Range-Ports (24000–32000) statt ephemeraler Kernel-Ports, damit CI-Runs flake-frei bleiben.

### 🎥 Sentry Error-Replay: Session Replay nur bei Fehlern
- Session Replay dient ausschließlich der **Crash-Diagnose**: `onErrorSampleRate 1.0`, `sessionSampleRate 0.0` — ohne Fehler wird nichts aufgezeichnet oder hochgeladen; zusammen mit dem Startup Safe Mode und der Crash-Advisory die dritte Diagnose-Stufe.
- Der bestehende **Opt-out-Toggle** steuert das Replay auf drei Ebenen mit (Rates, Buffering-Start/Stop, separater `beforeSendReplay`-Callback), weil `sendDefaultPii`/`beforeSend` Replay-Envelopes nicht filtern; die Maskierung bleibt vollständig auf SDK-Default (Texte/Bilder geschwärzt).
- FOSS-Build und Startup-Safe-Mode initialisieren Sentry weiterhin gar nicht — das Error-Replay betrifft nur den Standard-Build.

## 🔒 Sicherheit & Wartung

- **Wöchentlicher Sentry-Ops-Review-Workflow** (`automation-sentry-ops.yml`, Mo 06:30 UTC): Sentry-Stats-Guard mit Lese-Token + Health-Probe als sanktionierter Cron-Nutzer; Issue-Automation bei WARN/FEHLER mit Dedup + Auto-Close (ersetzt den monatlichen Stats-Review). Interne Ops-Doku als Kapitel „🛰️ Sentry-Ops“ in [RELEASE.md](../RELEASE.md).
- fdroidserver-Closure automatisch gegen PyPI-Drift nachgeführt (etabliertes Verfahren).
- Selbsttests im Pre-Push-Gate erweitert: Ops-Workflow-Test W1–W9 (31 Checks), PortFallbackPolicy-Suite + Integrationstests mit echten Sockets (3× wiederholt stabil), Bind-Loop-Regression gegen die Probe-Bind-Lücke.

## 📥 Installation & Update

| Kanal | Wie |
|---|---|
| **GitHub Releases** | Neuestes Release („Latest“) — `app-standard-release.apk` oder `app-foss-release.apk` |
| **Obtainium** | Zieht das Latest-Release automatisch |
| **Eigenes F-Droid-Repo** | Wöchentlich aus den Stable-Releases gebaut (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren).
- **FOSS-Hinweis:** Der FOSS-Build enthält keinen Sentry — EADDRINUSE-Fix, Kill-Switch und Port-Fallback-Kette funktionieren dort identisch (die Remote-Control ist flavor-unabhängig); das Error-Replay betrifft nur den Standard-Build.

## 🔗 Referenzen

- Sentry-Ops: [docs/sentry-stats.md](sentry-stats.md)
- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
