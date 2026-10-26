# Release Notes: Vivid v0.5.23-beta

**Release Date:** 2026-10-25
**Version:** 0.5.23-beta (versionCode 5232, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest" → Obtainium → eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.5.22-beta` wurde **nie geschnitten** — `VERSION` sprang in PR [#284](https://github.com/thoser666/Vivid/pull/284) direkt auf `0.5.23-beta`. Diese Notes decken deshalb **alles seit `v0.5.21-beta` (12.10.2026)** ab, nicht nur die Differenz zu einer 0.5.22. `v0.6.0` bleibt weiterhin dem offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, 4K/HEVC, Game-Controller, Streamer-Browser) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** 82 Commits seit dem letzten Beta-Schnitt, darunter **drei behobene Sentry-Crash-Pfade** (#320, #326, #327 — bislang nur in zwei Nightlies gefahren), **zwei neue Privacy-Funktionen** (Anonymisierung P2/P2b) und eine **neue Videoquelle** (UVC). Reine Doku-/CI-/Guard-Arbeit rechtfertigt keinen Cut; diese Punkte tun es.

## 🎉 Highlights

### 🕵️ Anonymisierung P2: Gesichtserkennung + Ganzbild-Verpixelung (neu)
- **Gesichtserkennung (Route A)** läuft auf der **RootEncoder-Hardware** statt über TFLite/BlazeFace: Slot-Merge mehrerer Erkennungen, Persistenz der Einstellung, Umschaltung erst nach einem sauberen Zustandswechsel (`06e761d4`).
- **Ganzbild-Verpixelung** (`uFullFrame`/`uPixelGrid`) als zweite, bewusst grobe Stufe für Standort-Schutz — Shader-Uniform plus Persistenz und UI-Schalter (`2b4a4e21`).
- Beide Stufen sind **unabhängig kombinierbar** und überleben App-Neustarts; Strings in de/en/fr/ru. Hardware-verifiziert auf POCO X7 Pro.

### 🎥 UVC-Videoquelle für externe USB-Kameras (neu, #305)
- Neue Quelle **UVC** über Pedros `MultiDisplay`-Bibliothek: in `VideoSourceKind`/`VideoSourceRegistry` registriert, in der Engine für Quellen-Wechsel, GL-Surface und Stop-Behandlung verdrahtet, im DI-Modul gebunden (`ae4699e7`).
- Damit ist die Kamera-Quellen-Trio Camera/Screen/Video um eine vierte Option erweitert; Regressionstest `UvcVideoSourceTest` folgt dem Muster der Screen-Capture-Tests.

### 💥 Streaming-Start: Consent pro Start, Startfehler werden sichtbar (#326, #327)
- **#326:** Ab Android 14 ist das `resultData` der MediaProjection ein **einmaliges Token**. `ScreenCaptureVideoSource` markiert es nach `start()`/`stop()` als verbraucht (`consentConsumed`, `requiresFreshConsent()`); ein neuer `onConsentResult`-Pfad macht es genau einen Start lang wieder gültig. Der Go-Live-Flow im Streaming-Screen holt den Consent daher **vor jedem Start neu** ein — auch nach fehlgeschlagener Vorbereitung, weil das Token dann ebenfalls verbrannt ist.
- **#327:** `StreamingService.startAsForeground()` meldet per Boolean, ob der Foreground-Zustand steht. Schlägt er fehl, wird **gar nicht erst gestartet** — vorher lief `startStream` nach bereits aufgeräumtem Service weiter und AMS beendete den Prozess mit „Unable to start service". Der neue Catch umfasst SecurityException **und** RootEncoder-RuntimeExceptions und schreibt über `reportStreamStartFailure()` einen sichtbaren **Failed**-Zustand statt eines stillen Idle.
- Die eigenen Fehlerursachen der Engine sind jetzt **String-Ressourcen** (`StreamingState.Failed.reasonRes`, vier neue `streaming_error_*`-Keys in de/en/fr/ru); die Rohdiagnose der RTMP-Library bleibt in `reason` und steht nachrangig in Banner, Notification und `!diag`.

### 🛡️ OBS/Sentry: R8-Keep-Rules für Gson (#320 / VIVID-3V)
- ProGuard-/R8-Keep-Rules für die OBS-**Security-Klassen** und das **Request-Interface**: R8 entfernte bisher die einzige Gson-`InstanceCreator`-Anbindung, woraufhin `handleHello` mit `JsonIOException: Abstract classes can't be instantiated` abstürzte (`1c3a33ca`).
- Der Guard ist strukturell verriegelt: `scripts/check_issue_320_proguard.sh` läuft seit `b1c8a0a1` **im Pre-Push-Gate** statt nur zu existieren — ein verlorenes `-keep` fällt damit sofort auf, nicht erst beim nächsten Release-Build.
- Belegt per R8-Mapping-Nachweis (`scripts/check_sentry_optout_mapping.sh`, C1–C6, beide Release-Kanäle).

## 🌍 Internationalisierung
- **Codekommentare und alle Logs** sind Englisch — Logs werden bewusst **nicht** lokalisiert (i18n-Plan §4) und sind damit technische Sprache; der Guard `check_i18n.sh` lehnt deutsche `Timber`-Strings in `src/main` ab (Wortliste + Umlaute, Selbsttest 8/8).
- Die **letzten hartkodierten UI-Fehler** sind Ressourcen geworden: API-Login/-Registrierung (`api_error_unknown`), QR-Parser (`qr_error_empty`/`qr_error_invalid`) und die komplette **Twitch-Fehlerkette** der Settings/Streaming-Oberfläche (13 neue Keys) — jeweils de/en/fr/ru. Rohdetails fremder Netzwerk-Bibliotheken bleiben bewusst unübersetzt.

## 🔒 Wartung
- **Guava 33.7.2-jre** gepinnt — Snyk-High **CVE-2026-102554** (`65ab7f5f`).
- **Kotlin-Gruppe (4 Updates)**, **Gradle 9.8.1**, `asm-util` 9.11, `roborazzi` 1.76.
- **CI-Härtung:** CodeQL-Action-Familie gemeinsam auf v4.38.3 inkl. Dependabot-Gruppe und Familien-Selbsttest; Kotlin-Pin entfällt (Extractor im CodeQL-Bundle); Snyk-Policy-Guard mit Python-Execution-Probe und Fail-Loud-verriegelten Publish-Snippets; Dependabot-Auto-Merge liest die **tatsächliche** Fehlerursache statt zu raten.
- Test-Coverage-Runde obs-control (5. Robolectric-Compose-Runde); fdroidserver-Requirements-Regeneration (PyPI-Drift).

## ⚠️ Bekannte offene Fehler (keine Regressionen)
- [#325](https://github.com/thoser666/Vivid/issues/325) `CameraOpenException: Camera no detected` — offen, unbehoben.
- [#324](https://github.com/thoser666/Vivid/issues/324) `IllegalStateException: Session has been closed` — offen, unbehoben.
- Noch **nicht** in diesem Cut: der experimentelle **60-FPS-Kameramodus** ([PR #322](https://github.com/thoser666/Vivid/pull/322)) — offener Konflikt mit `develop`, opt-in, daher kein Grund, den Cut aufzuschieben.

## 📥 Installation & Update

| Kanal | Bezug |
|-------|-------|
| **GitHub Releases** | „Latest" (diese Version) — FOSS-/Standard-APK + `SHA256SUMS.txt` |
| **Obtainium** | zieht automatisch das neueste GitHub-Release |
| **Eigenes F-Droid-Repo** | wöchentlicher Build aus den Stable-Releases (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren). Direkt von `v0.5.21-beta` installierbar (versionCode 5232 > 5212).
- **FOSS-Hinweis:** Alle Änderungen sind flavor-unabhängig und wirken identisch in beiden Builds.

## 🔗 Referenzen

- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Fixes & Features: Issues [#305](https://github.com/thoser666/Vivid/issues/305) · [#320](https://github.com/thoser666/Vivid/issues/320) · [#326](https://github.com/thoser666/Vivid/issues/326) · [#327](https://github.com/thoser666/Vivid/issues/327), Versions-Hebung [#284](https://github.com/thoser666/Vivid/pull/284)
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
