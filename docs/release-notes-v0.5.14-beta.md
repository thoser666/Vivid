# Vivid v0.5.14-beta

**Veröffentlicht:** 9. September 2026

Fünfte Patch-Beta nach dem M1-Abschluss: Diese Version liefert fünf Nutzer-Features aus den offenen Moblin-Gap-Punkten — **Replay-Aufnahme mit Audio-Konfiguration und Thumbnails, Hype-Train-Anzeige, Text-Widget-Template-Editor mit Geocoding-Variablen und Replay als Szenen-Quelle** — plus ein Fix gegen einen seltener Crash in der Replay-Bibliothek.

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, 4K/HEVC, SRTLA-Bonding, adaptive Bitrate etc.) reserviert. Auch diese Ausbaustufe kommt deshalb als Patch-Beta in der laufenden `0.5.x`-Linie – nicht als Feature-Beta `v0.6.0-beta`. Siehe [RELEASE.md](../RELEASE.md) → Roadmap → Nummerierung.

## ✨ Neue Features

### Replays (PARITY-Row „Replays" — vollständig ✅)
- **Replay-Aufnahme als MP4 parallel zum Stream** (Record-to-Disk) mit Aufbewahrungsgrenze
- **Audio-Konfiguration**: Aufnahme mit Ton oder nur Bild (`ReplayAudioMode`), umschaltbar in den Streaming-Einstellungen
- **Thumbnail-Vorschauen** in der Replay-Bibliothek (automatisch aus einem Frame der Aufnahme erzeugt)
- **Replay-Bibliothek**: Liste, Inline-Wiedergabe, Löschen (einzeln/alles) und Teilen über das System-Share-Sheet
- **Replay als Szenen-Quelle**: eine Aufnahme kann als Stream-Videoquelle in **Dauerschleife** gestreamt werden („Als Stream-Quelle verwenden" in der Bibliothek); `StreamScene` speichert die Replay-Datei dauerhaft — Szene anwenden, und das Replay läuft als Quelle

### Twitch Hype Train (EventSub)
- **Hype-Train-Banner im Chat-Overlay**: Level + Fortschritt, live aktualisiert (begin/progress/end über `channel.hype_train.*`)
- Umschaltbar in den Chat-Overlay-Einstellungen; Test-Auslösung per `!testalert hype`

### Text-Widget-Variablen (komplett)
- **Template-Editor in den Einstellungen** (Overlays & Widgets): mehrzeiliges Eingabefeld mit Variable-Chips und Live-Vorschau
- **Neue Geocoding-Variablen** `{road}`, `{city}`, `{country}`: Reverse-Geocoding über den Android-Geocoder mit TTL- und Distanz-Cache (10 min / 500 m) — Geocoding läuft nur, wenn das Template die Variablen auch nutzt

### Release- & Infra-Prozess (sichtbar für Tester)
- **fdroidserver-Requirements-Closure** vollständig SHA-256-hash-gepinnt (Scorecard PinnedDependencies, inkl. transitive Abhängigkeiten)
- **Doku-Guards**: Bot-Befehls-Katalog als Single Source of Truth (In-App-Hilfe, Handbücher DE/EN/FR und Wiki generieren daraus), Wiki-Sync generiert mehrsprachige Wiki-Seiten
- **CodeQL-Kotlin-Wächter**: wöchentlicher Status-Check, sobald CodeQL Kotlin 2.4.20 GA unterstützt, wird per Issue-Comment an das Re-Upgrade erinnert
- **Security-Loop-Guard** in der Release-Pipeline (Keystore-Härtung, Signatur-Checks, Reproduzierbarkeits-Vergleich, Sentry-Opt-out-Nachweis)

## 🐛 Bugfixes
- **Replay-Bibliothek**: `MediaMetadataRetriever` (Thumbnail-Erzeugung) wurde per `use {}` als `AutoCloseable` behandelt — das existiert erst ab API 29 (minSdk 24) und konnte auf älteren Geräten crashen. Behoben per `try/finally` mit `release()`
- Kotlin-Compile-Warnung in der Hype-Train-End-Anzeige behoben (PluralsCandidate-Lint in de/en)
- Fehlender Konstruktor-Parameter in Replay-Bibliothek-Tests (folgte dem Audio/Thumbnail-Umbau)

## 📊 Statistik
- **Commits seit v0.5.13-beta:** 36
- **Neue Tests:** ReplayVideoSource (12), SceneController/StreamingViewModel/ReplayLibrary erweitert, Robolectric-UI-Tests für Bibliothek + Bestätigungsdialog; Geocoder-/Cache-/VM-Tests für die Geocoding-Variablen; Hype-Train-Reader/ViewModel-Tests
- **PARITY-Status:** „Replays" **vollständig ✅**, „Text-Widget-Variablen" **vollständig ✅**, Twitch-Hype-Train ergänzt (Twitch-Integration weiter ausgebaut)
