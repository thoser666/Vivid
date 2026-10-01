# Release Notes: Vivid v0.5.20-beta

**Release Date:** 2026-10-01
**Version:** 0.5.20-beta (versionCode 5202, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest“ · Obtainium · eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** Zwei weitere durch Sentry identifizierte Produktions-Crashes sind seit dem letzten Schnitt behoben — der Go-Live-Crash der Screen-Capture-Quelle auf Android 14+ (VIVID-39) und die Log-Viewer-NPE durch korrumpierte Tagesdateien (VIVID-3A/3B) — plus eine Eingabe-Härtung beim RTMP-Ziel (VIVID-3D). Alle vier Sentry-Attributionen (#216/#220/#221/#225) sind abgeschlossen; dieser Schnitt schließt die zugehörigen CrashAdvisory-Ranges.

## 🎉 Highlights

### 💥 Go-Live-Crash mit Screen-Capture auf Android 14+ behoben (VIVID-39, #221)
- Die Screen-Capture-Quelle (S2) streamt über `MediaProjection.createVirtualDisplay()` — Android 14+ verlangt dafür einen laufenden Foreground-Service vom Typ `mediaProjection`, der StreamingService meldete aber nur `microphone|camera`. Beim Go-Live mit Bildschirm-Stream starb der Prozess als `SecurityException: Media projections` („Unable to start service“, fatal, 4 Events / 3 Nutzer).
- **Dreistufige Abhilfe:** Manifest-Permission `FOREGROUND_SERVICE_MEDIA_PROJECTION` + `mediaProjection` im `foregroundServiceType`; gezielte Anmeldung des Typs nur bei aktiver Screen-Capture-Quelle (`requiresMediaProjectionFgs`); Defense-in-Depth fängt die `SecurityException` in StreamingService und `ScreenCaptureVideoSource` — der Go-Live zeigt jetzt eine Fehlermeldung statt zu crashen, selbst wenn eine Stufe versagt.
- **CrashAdvisory-Eintrag** `MEDIA-PROJECTION-FGS-TYPE` (versionCode 5074–5192): Betroffene Nutzer sehen die 💥-Advisory mit dem Workaround (andere Videoquelle / Update) — ab 5202 ist der Crash behoben und die Range geschlossen. Neuer Manifest-Guard C3 verhindert die Rückkehr der Crash-Klasse (Selbsttest F8/F9).

### 💥 Log-Viewer-NPE durch korrumpierte Log-Datei behoben (VIVID-3A/3B, #225/#216)
- `LogStore` deserialisiert die JSON-Lines-Tagesdateien per Gson-Reflexion **ohne Kotlin-Konstruktor** — fehlende Keys, `"level": null` oder unbekannte Enum-Namen wurden still auf null-Felder gemappt; der erste Zugriff crashte (`Enum.name()`, fatal).
- **Fix:** `parseLine` stellt die Invarianten am Systemrand wieder her — verletzende Zeilen werden **übersprungen** statt weitergereicht („ein defekter Log blockiert die App nie“); Reconstruction über den Konstruktor garantiert null-freie Entries für Logs-Screen, Filter und `GET /logs`. 4 Regressionstests reproduzieren exakt den Produktions-Crash.
- **CrashAdvisory-Eintrag** `LOG-ENTRY-NPE-GSON-DESERIALIZE` (versionCode 5074–5192): ab 5202 behoben.

### 🛡️ RTMP-Ziel-Eingabe härtet Protokoll-Token im Host-Slot ab (VIVID-3D, #220)
- Ein `srt://…` im RTMP(S)-Ziel-Feld wurde von RootEncoder als **Literal-Hostname „srt“** geparst und scheiterte kryptisch am DNS (`UnknownHostException`). Der `StreamConfigValidator` blockt jetzt Hosts, die exakt einem Protokoll-Token entsprechen, als ERROR **vor** dem Engine-Start — mit klarer Feld-Meldung in de/en/fr/ru. Legitime Hosts wie `srt.example.com` passieren unverändert.

## 🔒 Wartung

- **Attributionen abgeschlossen:** Alle vier offenen Sentry-Issues (VIVID-3A/3B/3D/39) sind nach dem Triage-Verfahren aus docs/sentry-issues.md §5 attribuiert (#216/#220/#221/#225); die Schließungs-Mechanik wurde präzisiert (fix-release-Tag wirkt nur Sentry-seitig, GitHub-Issues schließen manuell im Release-Zug).
- **Issue-Hausmuster:** Neue Issues werden beim Anlegen dem Maintainer zugewiesen (`--assignee thoser666`, docs/sentry-issues.md §5).
- WHIP-Gerätesmoke-Vorbereitung (debug-only Einstiegspunkt + JVM-testbarer Runner) liegt bereit; der Gerätedurchlauf (MediaMTX, Android-Gerät) folgt separat.

## 📥 Installation & Update

| Kanal | Wie |
|---|---|
| **GitHub Releases** | Neuestes Release („Latest“) — `app-standard-release.apk` oder `app-foss-release.apk` |
| **Obtainium** | Zieht das Latest-Release automatisch |
| **Eigenes F-Droid-Repo** | Wöchentlich aus den Stable-Releases gebaut (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren). Der Cut ist ab v0.5.14 direkt installierbar (versionCode 5202 > 5192) und schließt alle drei oben genannten CrashAdvisory-Ranges.
- **FOSS-Hinweis:** Alle Fixes sind flavor-unabhängig und wirken identisch in beiden Builds.

## 🔗 Referenzen

- Sentry-Ops: [docs/sentry-stats.md](sentry-stats.md) · Triage-Verfahren: [docs/sentry-issues.md](sentry-issues.md) §5 · Alerts→Issues: [docs/sentry-alerts.md](sentry-alerts.md)
- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
