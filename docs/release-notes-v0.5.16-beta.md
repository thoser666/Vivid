# Release Notes: Vivid v0.5.16-beta

**Release Date:** 2026-09-19
**Version:** 0.5.16-beta (versionCode 5162)
**Channel:** Beta (GitHub Releases)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Hinweis zu v0.5.15-beta:** Der gleichnamige Tag (14.09.) wurde nie als GitHub-Release veröffentlicht — dieser Cut enthält dessen vollständigen Inhalt plus die Folgearbeiten. Nutzer der Nightly-Builds haben die Änderungen bereits erhalten.

## 🎉 Highlights

### 🎙 Untertitel (Speech-to-Text) als Stream-Overlay
- Live-Untertitel über dem Mikrofon-Audio via Android `SpeechRecognizer`, gerendert als Overlay im Stream (PARITY-Zeile 143 ✅).
- Capability-aware: Ohne Erkennungsdienst auf dem Gerät greift ein klarer Fallback (Hinweis statt stiller Fehlfunktion); Umschaltung per Settings-Toggle.
- Robolectric-getestet inkl. Shadow-`SpeechRecognizer` (Happy-Path, Busy-/Fehlerpfade, Capability-Fallback).

### 📱 UX-Audit vollständig abgeschlossen (5/5 Punkte)
- **Edge-to-Edge** (`enableEdgeToEdge`, targetSdk 37) inkl. Inset-Behandlung des Streaming-Screens und Overlay-Schicht; App-Start gegen Port-Konflikte des Remote-Control-Servers gehärtet (deterministische Port-Probe).
- **Accessibility:** Semantics/stateDescription für EV-Slider, Encoder-Chips und Toggles der Streaming-Steuerungen.
- **Per-App-Sprache** (Android 13+): DE/EN/FR über die Systemeinstellung App-Info → Sprache wählbar (`android:localeConfig`); die App benötigt dafür keinen eigenen In-App-Switch. Dokumentiert in allen drei Handbuch-Sprachen und im Wiki.
- **Adaptive Layouts** (Window Size Classes): Streaming- und Settings-Screens nutzen Tablets, Foldables und Querformat.
- **Theme-Cleanup:** Material-3-Baseline-Typografie statt Scaffold-Overrides (kleinerer, wartbarer Theme-Code).

### 🧩 Text-Widget erweitert
- Neue Variablen `{timer}`, `{distance}`, `{gforce}` (PARITY-Zeile 129 ✅ — letzte offene 🚧-Zeile der Overlays-Kategorie). Damit stehen neben Zeit/Position auch {road}/{city}/{country} (Geocoding) und die Sensor-Variablen für Template-Ausgaben bereit.

### 🤖 Release- & CI-Automatisierung (nicht user-facing, aber release-würdig)
- **Auto-Merge aller Bot-PRs:** Alle fünf Bot-Workflows (Changelog-Mirror ×3, F-Droid, PyPI-Drift-Wächter) poll nach dem PR-Create die Pflicht-Checks und mergen vollautomatisch (REST-Squash, fail-soft). Live bewiesen an PRs #180/#181.
- **PyPI-Drift-Wächter:** wöchentliche Erkennung von Upstream-Drift in der SHA-256-gepinnten fdroidserver-Closure mit automatischem Regenerierungs-PR (End-to-End im echten Drift-Fall validiert).
- CI-Diagnose-Härtung: stumme Gate-Fehler im Selbsttest werden mit voller Ausgabe sichtbar gemacht.

## 📊 Metriken

| Metrik | Wert |
|--------|------|
| **PARITY-Stand** | 39 ✅ / 2 🚧 / 26 📋 von 67 Features |
| **UX-Audit** | ✅ 5/5 Punkte abgeschlossen |
| **CI** | ✅ 7/7 Lanes grün (CI, CodeQL, Snyk, Scorecard, Release Drafter, Release Pipeline, Dependency Submission) |
| **CodeQL-Alerts** | ✅ 0 offen (Suppressions-Register mit Prüffristen) |
| **Bot-PR-Kette** | ✅ vollautomatisch (Credential → REST-PR → Pflicht-Checks → Auto-Merge, Guard T1–T13) |

## 📦 Installationshinweise

### Über GitHub Releases
- APK-Download: https://github.com/thoser666/Vivid/releases/tag/v0.5.16-beta
- Obtainium: Pre-Release-Channel aktivieren

### Über F-Droid (eigener Repo-Server)
- QR-Code: https://thoser666.github.io/Vivid/fdroid/qr.svg
- Oder manuell: `https://thoser666.github.io/Vivid/fdroid/repo`

## 🔜 Nächste Schritte

1. **Streaming-Bucket v0.6.0** umsetzen: RIST, WHIP, RTMP-Pull/Ingest, SRTLA, Game-Controller, Deep-Linking, Streamer-Browser, Landscape
2. **Play Upload**: Screenshots, Content Rating, Data Safety
3. **Stable-Verteilung** (v0.5.14/v0.5.15-Linie) über den wöchentlichen Lauf — dessen „FOSS-APK not found"-Fehler muss vorher behoben werden

---

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.15-beta...v0.5.16-beta

**Installation**: https://github.com/thoser666/Vivid/releases/tag/v0.5.16-beta

**Documentation**: https://thoser666.github.io/Vivid/

**F-Droid**: https://thoser666.github.io/Vivid/fdroid/repo
