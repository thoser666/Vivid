# 🚀 Release-Notes v0.5.3-beta

| | |
|---|---|
| **Version** | `0.5.3-beta` (versionCode `5032`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.3-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Dark Mode folgt dem System + Emotes im Chat-Overlay

**Zwei Nutzer-Features seit v0.5.2-beta:**

### 1. 🌙 Dark Mode (UI-Farbschemata, Stufe 1)

Die App hat jetzt ein echtes **Vivid-Branding-Theme** statt der Template-Standardfarben:

- **Vivid-Grün-Palette** (Material 3, Seed `#3DDC84` — identisch zur Akzentfarbe in den Projekt-SVGs) ersetzt Purple80/Pink80
- **Dark Mode folgt dem System:** Wechselt das Gerät auf dunkles Design, wird Vivid automatisch dunkel (vorher war die App *immer* hell, egal was das System sagte)
- Stufe 2 (eigener Umschalter System/Hell/Dunkel/AMOLED + Akzentfarbe) ist bereits als offener Punkt im Tracker notiert

### 2. 😀 Inline-Twitch-Emotes im Chat-Overlay

Der Twitch-Chat im Overlay rendert Emotes jetzt **inline neben dem Text** als Bilder vom Twitch-CDN:

- Strukturierte Emote-Fragment-Parsing direkt aus den EventSub-Message-Fragments (`InlineEmote`-Modell, `parseFromEmotesTag`)
- Rendering über **Coil** mit 25 MB Disk-Cache (CDN-Bilder werden lokal gecacht, keine Wiederholungsladezeiten)
- Emotes erscheinen skaliert zur Textgröße im `FlowRow`-Layout des Overlays — Chat sieht damit aus wie in Twitch selbst

## ✨ Was sonst noch in diesem Build steckt

### 3. CI-Härtung: Markdown-Anker-Check + GitHub-Golden-Test

- **`check_markdown_anchors.sh`** validiert bei jedem Push alle internen Markdown-Links (Anker + Datei-Existenz, auch für Bilder/SVGs/toml) mit dem **deterministischen GitHub-Anker-Algorithmus** — empirisch gegen die gerenderten GitHub-Seiten abgesichert (inkl. Sonderfälle wie Emoji-Variation-Selectors, Umlaute, em-dash)
- **Golden-Test** (`github_anchors_golden.tsv`, 14 Header→Anker-Paare) beweist offline, dass die Berechnung GitHub-exakt ist — der Check ist jetzt ohne Netzwerk vollständig abgesichert
- Der erste Lauf deckte **13 tote Anker + 5 kaputte `../scripts/`-Links** in RELEASE.md/PARITY.md/README/docs auf — alle behoben

### 4. Roadmap & Doku

- README hat einen neuen **Roadmap-Abschnitt** mit Ankern zu den Checklisten (Beta-Build, Play-P0–P2) und den Post-Beta-Buckets — **bidirektional** verlinkt (README ↔ RELEASE.md)
- Zwei neue Roadmap-Buckets in PARITY.md: **Multi-Plattform-Chat (Kick/YouTube/SOOP)** und **Color-Spaces + 3D-LUTs**
- **I18n-Externalisierungs-Plan** (`docs/i18n-plan.md`) — die In-Progress-Arbeit ist jetzt sichtbar in der Roadmap

---

## 🧪 Was Tester validieren sollten

1. **Dark Mode:** Gerät auf „Dunkel“ stellen → App öffnen → Theme ist dunkel mit Vivid-Grün als Akzent; zurück auf „Hell“ → App wieder hell. (Falls die App offen ist, einmal neu öffnen.)
2. **Inline-Emotes:** Chat-Overlay mit einem Twitch-Kanal mit Emotes im Chat starten → Emotes erscheinen als Bilder inline im Text; beim zweiten Öffnen laden sie sofort (Cache).
3. **Regression:** Streaming, OBS-Steuerung, Settings-Menüstruktur und Chat-Bot wie gewohnt — der Umbau betrifft nur Theme + Overlay-Rendering.

## 🔧 Technisch (für Entwickler)

- `app/src/main/java/com/vivid/irlbroadcaster/ui/theme/Theme.kt`: Vivid-Palette (Light/Dark) + `isSystemInDarkTheme()`, Paletten `internal` testbar; `VividThemeTest` (5 Fälle)
- `feature-chat`: `InlineEmote` + `parseFromEmotesTag`, EventSub-Fragment-Parsing in `TwitchChatEventSubReader`, Coil-Integration (`coil-compose`, 25 MB Disk-Cache in `VividApplication`), `ChatOverlay` mit `FlowRow`-Segment-Rendering (Text + inline `AsyncImage`); 8 Parser-Tests + 7 Overlay-Segment-Tests
- CI: `scripts/check_markdown_anchors.sh` + `scripts/test_github_anchors.sh` (Golden-Test, CRLF-resistent) in Pre-Push-Gate und android.yml
