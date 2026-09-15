# Release Notes: Vivid v0.5.15-beta

**Release Date:** 2026-09-14
**Version:** 0.5.15-beta (versionCode 5154)
**Channel:** Beta (GitHub Releases)

## 🎉 Highlights

### 🔒 Sicherheit & Privatsphäre
- **Backups: OAuth-Tokens ausgeräumt** (CodeQL-High `backup-enabled`, #471): `android:allowBackup="false"` — DataStore-/Keystore-basierte Tokens wandern nicht mehr in Android-Cloud-/Gerätebackups. Die zuvor wirkungslosen leeren Template-Rules (`backup_rules.xml`/`data_extraction_rules.xml`) sind entfernt.
- **Geocoder async (API ≥ 33)** (#474/#476): Der deprecated Blocking-Call `Geocoder.getFromLocation` läuft auf API ≥ 33 jetzt über die moderne async-`GeocodeListener`-API; auf Geräten unter Android 13 (minSdk 24) bleibt der dokumentierte Sync-Fallback ohne Deprecation-Warnung. Gleiche Semantik, kein Regressionsrisiko.
- **Dead-Code-Fixes** (#472/#473): `ReplayRecording.stop()` und `PlacenamesCache.isFresh()` aufgeräumt (`!is`-Guard + Smart-Cast, unread `val` entfernt).
- **0 offene CodeQL-Alerts**: Alle Findings sind bearbeitet — 3 per Code-Fix `fixed` (#471/#473/#474), 14 als `won't fix`/`false positive` dokumentiert. Neues **Suppressions-Register** (`docs/security-suppressions.md`) mit Guard im Pre-Push-Gate und monatlichem Review-Workflow hält den Zustand fest.
- **Manifest-Security-Guard**: Neuer Selbsttest-abgesicherter Guard blockiert Regressionen bei `allowBackup` (nach #471).

### 📺 Streaming (Vorgriff auf das v0.6.0-Bucket)
- **Encoder-Presets 4K/60fps**: Neue Qualitätsstufen inkl. **HEVC-Fallback-Kette** für schmale Bitraten.
- **Adaptive Bitrate (AIMD)**: Dynamische Bitrate-Anpassung (Abbau nach Sättigungssignalen, Förderband-Aufbau) reduziert Peer-denied/Abbrüche auf mobilen Netzen; **Upload-Statistik je Stream-Ziel** (gemessene kbps in Echtzeit). Standardmäßig aus, per Einstellung aktivierbar.

### 🚀 Distribution & CI
- **v0.5.14 Stable-Vorbereitung** abgeschlossen (VERSION 0.5.14, F-Droid-Metadaten) — Stable-Verteilung über den wöchentlichen Lauf.
- **cosign keyless-Signatur** der `SHA256SUMS.txt` im Stable-Workflow.
- F-Droid-Supply-Chain: Hash-pinnende Closure-Pflege, Kotlin-Extractor-Pin für CodeQL-Trace-Builds, Snyk-Policy-Ignore für rubyzip (dokumentiert).
- Pre-Push-Gate **fail-closed** verdrahtet (alle Guards + Selbsttests laufen auch lokal grün).

## 📊 Metriken

| Metrik | Wert |
|--------|------|
| **CodeQL-Alerts** | ✅ 0 offen (17 dokumentierte Dismissals im Register) |
| **Register-Guard-Selbsttest** | ✅ 12/12 (inkl. G7 live/offline) |
| **Unit-Tests** | ✅ feature-widgets (Resolver 6× sdk34 async, Cache 8), ReplayRecording (10) |
| **Pre-Push-Gate** | ✅ komplett grün (inkl. Manifest-Security-Guard + Selbsttest) |
| **Nightly** | Nachlauf: Security-Paket ab 14.09. drin |

## 📦 Installationshinweise

### Über GitHub Releases
- APK-Download: https://github.com/thoser666/Vivid/releases/tag/v0.5.15-beta
- Obtainium: Pre-Release-Channel aktivieren

### Über F-Droid (eigener Repo-Server)
- QR-Code: https://thoser666.github.io/Vivid/fdroid/qr.svg
- Oder manuell: `https://thoser666.github.io/Vivid/fdroid/repo`

## 🔜 Nächste Schritte

1. **Streaming-Bucket v0.6.0** abschließen: RIST/WHIP/RTMP-Erweiterungen
2. **Play Upload**: Screenshots, Content Rating, Data Safety
3. **Stable-Verteilung v0.5.14** über den wöchentlichen Lauf

---

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.13-beta...v0.5.15-beta

**Installation**: https://github.com/thoser666/Vivid/releases/tag/v0.5.15-beta

**Documentation**: https://thoser666.github.io/Vivid/

**F-Droid**: https://thoser666.github.io/Vivid/fdroid/repo