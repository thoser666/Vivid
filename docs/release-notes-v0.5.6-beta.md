# 🚀 Release-Notes v0.5.6-beta

| | |
|---|---|
| **Version** | `0.5.6-beta` (versionCode `5062`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.6-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Taschenlampe (Torch) + Text-Widget-Variablen + In-App-Hilfe

**Diese Beta bündelt die ersten drei user-sichtbaren „weiteren Moblin-Features“ seit v0.5.5-beta:**

### 1. 🔦 Taschenlampe (Torch) — Moblin-Parität abgeschlossen

Die Streaming-Kamera hat jetzt einen **Taschenlampen-Button** (oben rechts im Streaming-Screen), der die RootEncoder-Lantern-API steuert (`enableLantern`/`disableLantern`). Dazu gibt es den **Owner-Bot-Befehl `!torch`** (Aliasse `!lantern`/`!flashlight`) — der Streamer kann die Lampe direkt aus dem Chat/Whisper an- und ausschalten. Der Zustand lebt im `StreamingEngine`-StateFlow und übersteht das Umschalten vor dem Go-Live. **Damit schließt der PARITY-Punkt „Torch/Low-Light-Boost“ den Torch-Teil ab** (Low-Light-Boost bleibt auf der Roadmap).

### 2. 📝 Text-Widget-Variablen (Template-System)

Der Text-/Info-Widget-Screen kann jetzt **ein benutzerdefiniertes Template** verwenden statt nur der festen Einzelfelder:

```
{time} · {date} · {speed} · {altitude} · {lat} · {lon}
```

- `WidgetVariableResolver` ersetzt die Platzhalter bei jeder Sekunde/Location-Änderung live
- Setting `widgetTemplate` in den Einstellungen (wird in DataStore persistiert)
- Leeres Template → klassischer Toggle-Modus (Zeit/GPS/Geschwindigkeit/Höhe)

Damit ist der größte offene Punkt der Text-Widget-Zeile erledigt — offen bleiben Wetter, Timer/Stoppuhr, Land/Stadt, Distanz u. a. (Roadmap).

### 3. 🆘 In-App-Hilfe

Die App hat jetzt einen **Help-Screen** mit strukturierten Quick-Tips und **direkten Links auf die Bedienungsanleitungen** (deutsch/englisch/französisch), die Bot-Doku und die FAQ — erreichbar über den neuen **❓-Button** im Streaming-Screen und den **„Hilfe & Bedienungsanleitung“-Link** im About-Screen. Die Links öffnen den Browser (ACTION_VIEW). Dazu gibt es eine neue **GitHub-Pages-Landing-Page** mit Feature-Übersicht.

### 4. 🛡️ Owner-Rechtssystem + `!fix`

Das Owner-System wurde um eine **2-Stufen-Lösung** erweitert (neben `!start`/`!stop`/`!diag`/`!ask`):

- **`!fix`** — der Bot führt die Empfehlungen der Diagnose selbst aus, soweit sie deterministisch behebbar sind (z. B. fehlende Settings nachziehen), und bestätigt das Ergebnis
- Das Rechtssystem unterscheidet jetzt klar zwischen Owner (Broadcaster-Badge + Allow-List) und einfacher Verifizierung

### 5. (Intern) S1 Source-Abstraktion für Videoquellen

Als erstes Fundament des Roadmap-Buckets „Screen Capture + Video-Player als Videoquelle“ ist die **Videoquellen-Abstraktion** eingebaut: `VideoSourceKind` (CAMERA/SCREEN_CAPTURE/VIDEO_PLAYER), `VideoSource`-Interface, `VideoSourceRegistry` (aktive Quelle als StateFlow, `switchSource` in der Engine) — die Engine spricht nur noch die Abstraktion statt hart „die Kamera“. S2 (Screen-Capture via MediaProjection) und S3 (Video-Player) docken später ohne Engine-Umbau an.

---

## ✨ Was sonst noch in diesem Build steckt

### 6. Qualität & CI

- **Instrumentierte UI-Tests** für die Help-Navigation (Streaming-❓ und About-Link inkl. externer Browser-Intent) — laufen lokal auf dem Emulator, im CI als manueller `emulator-tests`-Job
- **Emulator-Test-Job:** nur noch manuell auslösbar (Hosted-Runner bieten weder KVM noch HVF — 7 Infrastruktur-Versuche, kein einziger Testfehler); dokumentiert und von der Push-Pflicht entkoppelt
- **ci: Emulator-Job-SHA-Pin** auf den Tag-Commit korrigiert
- **README-Links:** toter Anker + BOM-Fehler behoben

---

## 🧪 Was Tester validieren sollten

1. **Torch:** Streaming-Screen öffnen → 🔦-Button antippen (Lampe geht an), erneut → aus; während des Streams den Bot als Owner per `!torch` im Chat/Whisper schalten
2. **Text-Widget-Template:** Settings → Overlays & Widgets → `widgetTemplate` z. B. `{time} — {speed} km/h` setzen → Overlay rendert das Template live (Sekundenticker)
3. **In-App-Hilfe:** ❓-Button im Streaming-Screen und „Hilfe & Bedienungsanleitung“ im About → Hilfe-Screen; Klick auf die Guide-Links öffnet den Browser; Back führen zur jeweiligen Seite zurück
4. **`!fix`:** Als Owner `!diag` laufen lassen → bei behebbaren Fehlern `!fix` → der Bot meldet, was er repariert hat
5. **Regression:** Streaming starten/stoppen, Multi-Streaming, OBS-Remote, Chat-Overlay, Dark Mode, Widgets — der Umbau betrifft Kamera-/Overlay- und Bot-Pfade

## 🔧 Technisch (für Entwickler)

- Torch: `CameraControls.hasTorch/isTorchEnabled/enable/disableTorch` → `RootEncoderCameraControls` (Lantern-API); `StreamingEngine.toggleTorch()` + `torchEnabled`-StateFlow; Torch-Button lokalisiert (de/en/fr); `!torch`-Bot-Befehl (Owner-only) via `ChatStreamControl`
- Template: `WidgetVariableResolver` ({time}/{date}/{speed}/{altitude}/{lat}/{lon}), `widgetTemplate` in `AppSettings`/`SettingsRepository`, `TextInfoWidgetViewModel.resolvedTemplate` im UiState — 14 neue Tests (8 Resolver + 6 VM, feature-widgets-Suite)
- Hilfe: `HelpScreen` + `HelpLinkRow` (klickbar, `onOpenUri`), 3 Sprachversionen der Anleitung + Landing-Page, `HelpNavigationTest` mit R-Klassen (locale-robust)
- Owner: Owner-Gate (Broadcaster + Allow-List) und `!fix` mit Bestätigung; quit-Klasse in `BotCommandProcessor`
- S1: `feature-streaming/source/` (VideoSource.kt) + Registry-Injektion in `StreamingEngine` — Tests `VideoSourceRegistryTest` (5) + Engine-Tests (4)