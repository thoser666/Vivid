# Vivid v0.5.12-beta

**Veröffentlicht:** 5. September 2026

## ✨ Neue Features

### Streaming
- **Belichtung & Weißabgleich:** EV-Slider + Auto-Toggles im Streaming-Screen (capability-aware, [25801af])
- **Replay-Bibliothek:** Gespeicherte MP4-Replays mit Wiedergabe im Video-Player, Löschen und Export/Teilen ([3589f44])
- **Restliche M1-Streaming-Features** (Szenen/Steuerung vervollständigt, [97d3946])

### Twitch-Integration
- **Kanal-Steuerung:** Viewer-Anzeige + Stream-Titel/Kategorie aus der App setzen ([c13612a])
- **OAuth-Grundlage:** Authorization-Code-Austausch ([0eb5670]), PKCE-State-Validierung ([47514c9]), gehärteter Token-Contract ([d7c13cd])

### Widgets & Overlays
- **Bild-Widget:** Logo/Wasserzeichen auf dem Stream mit SAF-Datei-Auswahl ([6196241])
- **Grid-Overlay:** Widget-Positionierung auf der Streaming-Vorschau ([d424046])
- **Akku-Widget:** Batterieanzeige als Overlay + `!battery` Owner-Befehl mit periodischer Low-Battery-Warnung ([fe0883c])
- **QR-Code-Widget:** Spenden-/Social-Links als Stream-Overlay
- **Chat-Overlay Fade-In:** Animation (300 ms) beim Erscheinen neuer Nachrichten ([97e004a])

### Chat
- **Chat-Polls:** Owner-verwaltete Umfragen im Chat ([14968f7])

### Quality & Testing
- **Robolectric-Compose-Coverage-Runde:** 19 neue UI-Tests; Gesamt-Coverage 35,8 % → 47,2 % (LINE) ([d1adbdd])
- **Robolectric in feature-streaming:** echte Bitmap/FileProvider-Pfade getestet ([cd41abc])
- **Kritische feature-streaming-Pfade** abgedeckt (Engine-Guards, Filter, Replay, [624887d])

### OpenSSF Best Practices Badge
- **Passing-Badge erreicht** (Projekt 14442, 100 %) + **Silver auf 98 %** — inkl. Badge-Registrierung, Kover-Coverage-Reporting in CI, Code-of-Conduct/Contribution-Guidelines und Scorecard-Härtungen

## 🔒 Security & CI
- Vollständiger OAuth-HTTP-Token-Austausch mit PKCE
- pip-Installationsaufrufe in deploy-pages/deploy-fdroid auf SHA-256-verifizierte Wheel-Artefakte gepinnt
- Snyk auf SHA-gepinnte Setup-Action umgestellt
- Network-Security-Config: Klartext-Traffic (ausnahmslos) deaktiviert
- Branch-Protection für develop (Status-Checks, Linear History)

## 📊 Statistik
- **Commits seit v0.5.11-beta:** 97
- **Neue UI-Tests:** 19 (Robolectric-Compose) + weitere Pfad-Tests
- **Coverage:** 35,8 % → 47,2 % (LINE, Kover-Merge)
- **Badges:** OpenSSF Best Practices **Passing** ✅ (Silver-Anlauf bei 98 %)

## 🔗 Links
- [GitHub Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.12-beta)

## 🗄️ F-Droid-Archiv-Repository

Das eigene F-Droid-Repository (`https://thoser666.github.io/Vivid/fdroid/repo`) hat jetzt ein **separates Archiv-Repository** ([f03e8c1]):

- **Zwei getrennte Repos:** `repo/` (die letzten 5 Release-Versionen, schnell für Updates) + `archive/` (**alle älteren Versionen**, persistent für Downgrades/Fallback)
- **`archive_older: 5`** — `fdroid update` verschiebt automatisch die 5 neuesten in `repo/` und alle älteren ins `archive/`
- **`archive_url` / `archive_name`** — der F-Droid-Client erkennt und zeigt das Archiv jetzt korrekt (Einstellungen ➜ Repository ➜ Archiv)
- **Persistenz:** Das `archive/`-Verzeichnis wird im git-Repo (unter `docs/fdroid/archive`) gespeichert und bei jedem Lauf wiederhergestellt — ältere Versionen gehen nicht verloren
- **Nutzer-Vorteile:** Downgrades zu älteren Versionen möglich, Fallback für alte Android-Geräte, keine „kein Archiv“-Warnung mehr im Client
