# 🚀 Release-Notes v0.5.4-beta

| | |
|---|---|
| **Version** | `0.5.4-beta` (versionCode `5042`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.4-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Dark Mode Stufe 2 — Design-Modus + Akzentfarben

**Die größte sichtbare Änderung seit v0.5.3-beta — die App wird endlich im eigenen Stil einstellbar:**

### 1. 🌙 Dark Mode Stufe 2 (UI-Farbschemata fertig)

Stufe 1 (folgt dem System) war in v0.5.3-beta — jetzt kommt der volle **User-Toggle**:

- **Design-Modus:** `System` / `Hell` / `Dunkel` / `AMOLED` — AMOLED nutzt reine Schwarz-Flächen für OLED-Displays
- **6 kuratierte Akzentfarben:** Vivid-Grün (Standard, exakt die Stufe-1-Palette), plus 5 weitere Material-3-Paletten
- **Neue Settings-Kategorie „Darstellung“:** Modus-SegmentedButton + Farb-Swatches mit Auswahl-Ring
- **Wirkt sofort:** Die Theme-Änderung wird live angewendet — **kein App-Neustart nötig**

### 2. 📍 Höhenmeter im Text-/Info-Widget

Das Info-Widget (Zeit/GPS/Geschwindigkeit) zeigt jetzt auch die **Höhenmeter** an — neu in Moblin 33.12.0, jetzt in Vivid übernommen.

### 3. 🌍 Dritte Sprache: Französisch (komplett)

Die I18n-Externalisierung ist abgeschlossen — **alle UI-Strings liegen in Modul-Ressourcen**, und **Französisch ist als dritte vollständige Sprache dazugekommen** (Deutsch als Default, Englisch, Französisch):

- Alle fünf Module (app, core, feature-settings, feature-obs-control, feature-streaming) mit vollständigem `values-fr`
- Auch Chat-Overlay- und Widget-Strings externalisiert (feature-chat, feature-widgets neu dabei)
- CI-Gate prüft die Vollständigkeit aller drei Sprachen bei jedem Push

### 4. ⚙️ Custom-Plattform-Vorlage „Benutzerdefiniert“

In den Stream-Einstellungen gibt es jetzt eine **vierte Vorlage „Benutzerdefiniert“** — sie leert die Ingest-URL und lässt den TLS-Toggle offen, damit beliebige **RTMP/SRT-Ziele** (z. B. **Owncast**) eingetragen werden können. Dazu ein Hinweistext unter dem Stream-URL-Feld.

---

## ✨ Was sonst noch in diesem Build steckt

### 5. CI-Härtung & Qualität

- **PARITY-Log-Guard:** Neuer Selbsttest `test_parity_log.sh` — jeder Aktualisierungslog-Eintrag in PARITY.md muss einen gültigen Commit-Hash tragen statt `—`; alle 40 historischen Einträge wurden rückwirkend per Git-Historie rekonstruiert (mit Zuordnungs-Doku)
- **Roadmap-Reservierungs-Guard:** `release_beta` lehnt `v0.6.0-beta` aktiv ab, solange das Streaming-Bucket (RIST/WHIP/RTMP) offen ist — strukturell abgesichert statt nur dokumentiert
- **Dependency-Bumps:** Gradle Wrapper 9.7.0, kotlinx-coroutines, androidx activity-compose, actions/checkout 7.0.1

---

## 🧪 Was Tester validieren sollten

1. **Dark Mode Stufe 2:** Einstellungen → Darstellung → Design-Modus auf „Dunkel“ und „AMOLED“ stellen (wirkt sofort ohne Neustart); Akzentfarbe wechseln → Farb-Swatches reagieren live.
2. **Höhenmeter:** Widget aktivieren mit GPS → die Höhe wird angezeigt (m, mit Anstieg/Abstieg).
3. **Französisch:** Gerät auf Französisch stellen → komplette App (Settings, Streaming, OBS, About) erscheint auf Französisch; kein englischer/deutscher Misch-Mix.
4. **Custom-Plattform:** Stream-Einstellungen → Vorlage „Benutzerdefiniert“ → eigene RTMP/SRT-URL eintragen (z. B. Owncast) → Go-Live-Selbst-Check akzeptiert die URL.
5. **Regression:** Streaming, OBS-Steuerung, Chat-Overlay + Bot, Menüstruktur wie gewohnt — der Umbau betrifft Theme, Widget, Vorlagen und Strings.

## 🔧 Technisch (für Entwickler)

- `app/src/main/java/com/vivid/irlbroadcaster/ui/theme/Theme.kt`: `ThemeMode`/`AccentColor`-Enums, `accentPalettes` (6 Akzente, M3-TonalSpot), `VividAmoledColorScheme`, `VividTheme(darkTheme, amoled, accent)`; Persistenz via `SettingsRepository` (`theme_mode`/`theme_accent`), live angewendet in `MainActivity` — `VividThemeTest`, `ThemeModeTest`, `AccentColorTest` u. a.
- `feature-widgets`: Höhenmeter (`WidgetFormatters`), Tests erweitert
- I18n: `scripts/check_i18n.sh` prüft jetzt **6 Module** in **3 Sprachen** (values ↔ values-en ↔ values-fr); `scripts/test_check_i18n.sh` (5 Fixtures); Plan: [docs/i18n-plan.md](../docs/i18n-plan.md)
- CI: `scripts/check_parity_log.sh` + `scripts/test_parity_log.sh`, `scripts/test_roadmap_reservation.sh` in Pre-Push-Gate und android.yml
