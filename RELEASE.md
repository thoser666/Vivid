# 🚀 Release Pipeline

Jeder Release durchläuft eine von vier Stufen. Welche Stufe aktiv ist, bestimmt der Stand im [PARITY.md](PARITY.md)-Feature-Tracker.

## Stufenübersicht

| Stufe | Tag-Muster | Auslöser | Zielgruppe |
|-------|-----------|----------|------------|
| `nightly` | `nightly` (rollierend) | Jeder develop-Merge | Entwickler · CI-Tester |
| `alpha` | `vX.Y.0-alpha` | Manuell via `fastlane release_alpha` | Frühe Tester (Obtainium, kein Pre-Release-Flag nötig) |
| `beta` | `vX.Y.0-beta` | Manuell via `fastlane release_beta` (TODO) | Feldtester · Hunde essen ihr eigenes Futter |
| `stable` | `vX.Y.Z` | Manuell via `fastlane release_stable` (TODO) | Play Store · F-Droid · Allgemeinverfügbarkeit |

## ⚠️ Stage Gates

### `nightly` → laufend
- Kriterium: CI grün → APK published
- Keine manuelle Prüfung

### `alpha` → ab jetzt aktiv
**Kriterien (alle müssen erfüllt sein):**
- [ ] Alle Unit-Tests grün (`bundle exec fastlane test`)
- [ ] RTMP-Streaming funktioniert (✅ in PARITY.md)
- [ ] SRT-Streaming funktioniert (✅ in PARITY.md)
- [ ] OBS-WebSocket-Steuerung getestet (✅ in PARITY.md)
- [ ] Kein Crash in 5-minütigem manuellen Test
- [ ] Auf `develop`-Branch

**Befehl:**
```sh
bundle exec fastlane release_alpha
```
→ Pusht `v0.2.0-alpha`-Tag → CI baut signiert und veröffentlicht als GitHub-Prerelease

### `beta` — 🚦 **Nächster Meilenstein**

> **🧠 Erinnerung: Sobald diese Kriterien erfüllt sind → `release_beta`-Lane schreiben und ersten Beta-Tag setzen.**
>
> **📋 Google Play: Vor dem ersten Beta-Release benötigst du:**
> - Google Play Developer Account ($25 einmalig)
> - App-Signing-Key (bereits vorhanden: `release.keystore` im CI)
> - ~~Privacy Policy~~ → **erledigt:** [PRIVACY.md](PRIVACY.md)
> - App-Icon (512×512, PNG)
> - Screenshots (mind. 2, 16:9 oder 9:16)
> - Store Listing (Kurzbeschreibung, Langbeschreibung)
> - Content Rating Questionnaire
> - Data Safety Section (welche Daten sammelt die App?)
>
> → **Vorlagen bereit:** `fastlane/metadata/android/en-US/` und `de-DE/` mit Titel, Kurz-/Langbeschreibung und Changelogs. Privacy Policy fertig ([PRIVACY.md](PRIVACY.md)). Noch ausfüllen:
>   - App-Icon (512×512, PNG) → `fastlane/metadata/android/images/icon.png`
>   - Screenshots (mind. 2, 16:9 oder 9:16) → `fastlane/metadata/android/images/phoneScreenshots/`
>   - Content Rating Questionnaire (wird in der Play Console ausgefüllt)
>   - Data Safety Section (welche Daten sammelt die App?)

**Kriterien (alle müssen erfüllt sein):**
- [ ] ≥50 % Feature-Parität (aktuell 7/33 ≈ 21 %, Ziel: ≥17 Features)
- [ ] Chat implementiert & getestet (📋 in PARITY.md)
- [ ] Mindestens ein Overlay/Widget funktioniert (📋 in PARITY.md)
- [ ] Kamera-Vorschau stabil (🚧 in PARITY.md)
- [ ] Settings persistent über App-Neustarts
- [ ] Keine bekannten Showstopper-Bugs
- [ ] ≥2 manuelle Tester haben bestätigt: „kein Crash in 15 Minuten"
- [ ] Google-Play-Unterlagen vorbereitet (s. o.)

**Befehl (TODO, noch zu implementieren):**
```sh
bundle exec fastlane release_beta
```

### `stable` — 🏁 **Endziel**

> **🧠 Erinnerung: Sobald diese Kriterien erfüllt sind → `release_stable`-Lane schreiben und v1.0.0 taggen.**

**Kriterien (alle müssen erfüllt sein):**
- [ ] ≥90 % Feature-Parität (≈30 von 33)
- [ ] Alle entwickelten Features in PARITY.md auf ✅
- [ ] Vollständige CI-Test-Suite (Unit + UI + Integration)
- [ ] Performance-Test bestanden (Streaming-Latenz <2 s, App-Start <1 s)
- [ ] Accessibility-Baseline (min. TalkBack-fähig)
- [ ] Privacy Policy live
- [ ] Play Store Listing vollständig
- [ ] F-Droid-Metadaten vorbereitet

**Befehl (TODO, noch zu implementieren):**
```sh
bundle exec fastlane release_stable
```

---

> **Pflege:** Dieses Dokument bei jeder Änderung an PARITY.md prüfen — wenn ein Gate erreicht ist, die entsprechende Lane implementieren und taggen.
