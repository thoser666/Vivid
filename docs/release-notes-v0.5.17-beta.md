# Release Notes: Vivid v0.5.17-beta

**Release Date:** 2026-09-22
**Version:** 0.5.17-beta (versionCode 5172, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest“ · Obtainium · eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** Seit `v0.5.16-beta` (20.09.) sind 58 Commits zusammengekommen — darunter der **Startup Safe Mode**, der Absturz-Schleifen endlich sichtbar macht. Genau dieser Transport ist der Grund für den Schnitt: Crashes wie der gemeldete Galaxy-S23-Start-Crash liegen bislang in **jedem** veröffentlichten Build (GitHub, Obtainium, F-Droid-Repos) brach, ohne Diagnosemöglichkeit. Ab dieser Version startet Vivid nach zwei gescheiterten Versuchen in einen robusten Diagnose-Screen.

## 🎉 Highlights

### 🛡 Startup Safe Mode: Crash-Schleifen werden sichtbar
- Vivid zählt Startversuche (fehlerfrei, dateibasiert). Nach **zwei gescheiterten Starts in Folge** startet der dritte Versuch nicht mehr blind, sondern in die **Crash-Diagnose** (`CrashDiagnosticsActivity`): Sie zeigt den **letzten persistierten Crash** mit vollständigem Stacktrace aus dem In-App-Log.
- Die Diagnose ist bewusst **ohne Hilt/Compose/DataStore/Sentry/Remote-Server** gebaut — sie läuft auch dann, wenn genau diese Komponenten die Crash-Ursache sind.
- Buttons: **Kopieren**, **Teilen** (System-Share-Sheet mit Versionszeile) und **„Vollständigen Start versuchen“** (löst den Safe-Mode-Modus; der Zähler-Reset passiert erst, wenn die volle UI erreicht wird).
- Funktioniert in **beiden Builds** (Standard und FOSS) — kein Sentry nötig.
- Betroffene (Sentry-Reporting aktiv + Standard-Build) sehen den Crash zusätzlich symbolisiert im Sentry-Dashboard; mit dem Safe Mode bekommen jetzt alle Nutzer einen lokalen Diagnoseweg.

### ⚠️ Crash-Advisory: bekannte, versionsgebundene Crash-Kandidaten
- Beim Start prüft Vivid gegen eine Registry bekannter, an versionCode-Grenzen gebundener Crash-Kandidaten und meldet Treffer deutlich (rote 💥-Zeile im Log-Viewer) inklusive Workaround-Hinweis.
- Die Registry startet bewusst leer — Einträge landen erst nach real identifizierten Crashes (Sentry/In-App-Log), nie spekulativ.

### 💬 Multi-Plattform-Chat: YouTube liest mit (P0 + P1 der Architektur-Skizze)
- **P0 (Grundgerüst):** `ChatPlatform` (Twitch/YouTube/Kick), `ChatMessage.platform` und die `ChatReader`/`ChatSender`-Interfaces — verhaltensneutral, alle Bestandstests grün.
- **P1 (YouTube-Adapter):** Lesen des YouTube-Live-Chats **anonym** über die innertube-Polling-API (Bootstrap über die Kanal-Live-Seite, drift-tolerante Continuation-Extraktion, Backoff bei 429/5xx) — kein OAuth, keine Quota. Senden folgt in P4.
- **Merged Overlay:** Twitch- und YouTube-Nachrichten laufen in dasselbe Chat-Overlay; YouTube-Nachrichten tragen ein rotes ▶-Plattform-Badge (Kick K grün vorbereitet, Twitch bleibt badge-frei).
- Aktivierung: **Einstellungen → Overlays** → YouTube-Kanal-ID + Toggle. Architektur-Skizze: [docs/architecture/multi-platform-chat.md](architecture/multi-platform-chat.md).

### 🤝 Twitch Shared-Chat: Anzeige + Härtung
- Läuft eine Shared-Chat-Session („Stream Together“), zeigt das Overlay einen dezenten Hinweis (Gast: Host-Login · Host: Teilnehmer-Anzahl) — per EventSub `channel.shared_chat.begin/update/end`, kein Scope nötig.
- **Owner-Gate gehärtet:** Broadcaster-Badges von Ko-Streamern in Shared-Chat-Sessions lösen **keine** Owner-Befehle mehr aus (`!start`/`!stop`/`!diag`/…) — der Badge zählt nur im konfigurierten Kanal.
- **Ingestion gegen Modell-Drift gehärtet:** Contract-Tests mit realen Shared-Chat-Payloads (inkl. `source_*`-Feldern und undokumentierten Zukunftsfeldern) + CI-Guard, der `ignoreUnknownKeys = true` für alle Chat-Json-Instanzen erzwingt.

### 🔔 Event-Alerts verifiziert
- Render-Tests über alle 6 Twitch-Alert-Typen (Subs, Gifts inkl. anonymer Gifts, Raids, Hype-Train inkl. Ende); Scope-Voraussetzungen sind jetzt sichtbar in den Settings dokumentiert (`moderator:read:followers`, `channel:read:subscriptions` …).
- Kleiner Render-Bugfix: Suffix-Fragmente der Alert-Texte verloren ihr führendes Leerzeichen.

## 🔒 Sicherheit & Wartung

- **Bouncy Castle (Dependabot critical + high):** Pin auf 1.85 jetzt auch auf dem Root-Buildscript-Classpath — die verwundbaren 1.80.2/1.81-Nodes sind aus dem Dependency-Graph.
- **cosign v3-Bundle-Migration** im Stable-Verteiler: Signaturprüfung jetzt per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt` (Sigstore-Bundle ersetzt `.sig`/`.crt`).
- **Stable-Verteiler komplett funktionsfähig:** Beide Flavor-APKs + Prüfsummen + cosign-Bundle als Pflicht-Assets (Completeness-Regel, idempotenter Repair-Pfad).
- **Auto-Merge für alle Bot-PRs** (Changelog-Mirror, F-Droid, PyPI-Drift): nach grünen Pflicht-Checks mergen die PRs vollautomatisch.
- Dependency-Bumps: AGP 9.4.1, Robolectric 4.17, Networking- und Media-Gruppen, fdroidserver-Closure (automatisch gegen PyPI-Drift nachgeführt).
- Teststabilität: flaky `ObsControlViewModelTest` dauerhaft behoben (MockK-Agent-Race, JDK 25/JEP 451).

## 📥 Installation & Update

| Kanal | Wie |
|---|---|
| **GitHub Releases** | Neuestes Release („Latest“) — `app-standard-release.apk` oder `app-foss-release.apk` |
| **Obtainium** | Zieht das Latest-Release automatisch |
| **Eigenes F-Droid-Repo** | Wöchentlich aus den Stable-Releases gebaut (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren).
- **FOSS-Hinweis:** Der FOSS-Build enthält keinen Sentry — der neue Startup Safe Mode funktioniert dort genauso (lokale Diagnose).

## 🔗 Referenzen

- Architektur-Skizze Multi-Plattform-Chat: [docs/architecture/multi-platform-chat.md](architecture/multi-platform-chat.md)
- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
