# 🚀 Release-Notes v0.5.2-beta

| | |
|---|---|
| **Version** | `0.5.2-beta` (versionCode `5022`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.2-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Neue Settings-Menüstruktur (Kategorie-Übersicht wie Moblin)

Der monolithische Settings-Screen (eine lange Scroll-Liste mit über 12 Sektionen) ist Geschichte. Die Einstellungen haben jetzt eine **Kategorie-Übersicht mit fünf Kacheln** — dasselbe Muster, das auch Moblin nutzt (gruppierte Einstellungen mit Unteransichten):

```
⚙️ Einstellungen
├─ 📡 Streaming & OBS        → Stream-URL/-Key, Plattform-Vorlagen, Multi-Streaming, OBS
├─ 🎨 Overlays & Widgets     → Twitch-Chat-Overlay, Text-/Info-Widget (Zeit/GPS/Geschwindigkeit)
├─ 🤖 Chat-Bot & KI          → Betriebsmodus, Bot-Konto/LLM, Limits, Presets, Owner-Zugriff, Media-Befehle
├─ 🖥️ Remote & Datenschutz   → Web-Remote-Control (LAN/Token), Sentry-Fehlerberichte
└─ ℹ️ Über & Updates         → Version, Update-Badge, Release-Info
```

**Vorteile für Tester:** Statt 500 Scroll-Pixel bis zum Chat-Bot sind es jetzt **2 Taps** (Kachel → Feld) — jede Kategorie hat ihre eigene Ansicht mit Zurück-Pfeil und „Speichern“-Button. Die Übersicht zeigt zusätzlich Version + Update-Status auf einen Blick.

## ✨ Was sonst neu ist

### 1. README: Hero-Screenshot + Galerie

Die GitHub-README zeigt jetzt ein **Hero-Mockup** (`docs/hero.svg`, Live-Stream-Screen im Phone-Frame) direkt unter den Badges und eine **Screenshot-Galerie** — der erste Eindruck beim Öffnen des Repos ist jetzt das Produkt, nicht Text.

### 2. Play-Vorbereitung: P0-Infrastruktur steht (konto-unabhängig)

- **Upload-Keystore neu erzeugt** und liegt **außerhalb des Repos** (`I:\gpg-keys\Google_Play_upload`) — strukturell nicht committbar
- **Die vier `UPLOAD_*`-Secrets sind gesetzt** und im CI-Dry-Run end-to-end bewiesen (AAB signiert, Signatur gegen den Upload-Key verifiziert, kein Upload)
- **publish_play-Dry-Run grün** — die Pipeline funktioniert; es fehlen nur noch die Console-Schritte nach Kontofreigabe (`PLAY_JSON_KEY_*`)

### 3. CI-Härtung: README↔Play-Konsistenz-Guard

Der Metadaten-Check (`check_play_metadata.sh`) prüft jetzt zusätzlich, dass **jede README-Bildreferenz existiert** und **jeder Play-Screenshot in der README-Galerie** referenziert ist — README und Play-Store-Metadaten können nie wieder auseinanderlaufen. Dabei wurden zwei Lane-Bugs gefixt (cwd-unabhängige Pfadauflösung für fastlane).

### 4. Echte Screenshots statt Platzhalter

Die Play-Screenshots (Live-Stream + Settings) sind echte App-Aufnahmen per Screengrab-Lane — der Settings-Screenshot zeigt die neue Kategorie-Übersicht. Das Play-Metadaten-Gate ist grün.

---

## 🧪 Was Tester validieren sollten

1. **Menüstruktur:** Settings öffnen → Kacheln sehen → in jede Kategorie wechseln → Feld ändern → Speichern → zurück → Werte sind übernommen (App-Neustart testen)
2. **Chat-Bot & KI:** Kategorie öffnen — alle Bot-Felder (Konto/LLM, Limits, Presets, Owner, Whisper) müssen erreichbar sein
3. **Remote & Datenschutz:** Web-Remote-Token sichtbar, Sentry-Toggle vorhanden
4. **Über & Updates:** Version + Update-Badge korrekt, „Über Vivid & Updates“-Button funktioniert
5. **Regression:** Streaming, OBS-Steuerung und Overlay wie gehabt — der Umbau hat nur die Settings-Navigation verändert

## 🔧 Technisch (für Entwickler)

- `SettingsScreen.kt`: 776 → ~190 Zeilen (nur noch Übersicht, zentrale testbare `SettingsCategories`-Definition)
- 5 neue Sub-Screens + gemeinsames `SettingsSectionScaffold` (Top-Bar/Zurück/Speichern)
- 5 neue Navigations-Routen (`settings_streaming/overlays/chatbot/remote/about`)
- Neuer Unit-Test `SettingsCategoriesTest` (Struktur + Routen-Vollständigkeit) — läuft im CI
- Play-Vorbereitung: `prepare_play_secrets.sh --set` (UPLOAD_*), Keystore-Backup-Checkliste, publish_play-Dry-Run
