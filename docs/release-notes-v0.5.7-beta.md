# Release Notes: Vivid v0.5.7-beta

**Release Date:** 2026-08-28
**Version:** 0.5.7-beta (versionCode 5072)
**Channel:** Beta (GitHub Releases)

## 🎉 Highlights

### 🔒 Security & CI-Härtung (Hauptfokus)

#### CodeQL Security-Scan
- **CodeQL GitHub Actions Workflow** aktiviert (`.github/workflows/security-codeql.yml`)
- SHA-Pinning aller Drittanbieter-Actions für Supply-Chain-Security
- SARIF-Upload in GitHub Security-Tab
- **Alle Alerts behoben**: 0 offene Errors/Warnings, 10 False Positives dokumentiert

#### OpenSSF Scorecard
- **Automatische Security-Analyse** nach Open-Source-Best-Practices
- SHA-Pinning: `ossf/scorecard-action@55891bbd...` (v2.4.4)
- Weekly Ausführung auf master + manuell
- SARIF-Upload in GitHub Security-Tab

#### Stale Issue Management
- **Automatisches Schließen** inaktiver Issues nach 30+7 Tagen
- Exempt-Labels für wichtige Issues (bug, security, pinned, etc.)
- Tägliche Ausführung um 06:00 UTC

#### CodeQL Alert-Bereinigung
- **False Positive**: `implicit-pendingintents` (explizite Intents + FLAG_IMMUTABLE)
- **False Positive**: `field-masks-super-field` (Kotlin interne $stable-Felder)
- **False Positives**: `local-variable-is-never-read` (Kotlin-Compiler-Artefakte)
- **Mitigated**: `allowBackup=true` (bewusste Entscheidung für Settings-Wiederherstellung)
- **Präventiv**: `override val message` in 4 Exception-Klassen

### 🎬 Neue Features

#### Streaming-Quellen
- **S3 Video-Player** als echte Videoquelle (MultiFromFile + SAF-Datei-Picker)
- **S2 Screen-Capture** mit MediaProjection-Consent und Source-Toggle
- **Basic Scenes** + Auto-Scene-Switcher für Stream-Konfigurationen

#### Logging & Diagnose
- **Tägliche Log-Rotation** mit konfigurierbarer Vorhaltezeit (1-30 Tage)
- **Crash-Markierung** in Logs mit rotem Hintergrund
- **In-App Logs** (Logs & Diagnose) mit Timber-Trees und Redaktions-Filter
- **GET /logs?days=N** Endpunkt für Web-Remote-Control (token-geschützt)
- **Crash-Zusammenfassung** in `!diag` Befehl

#### Moderation
- **Profanity-Filter** für `!ask` mit Kategorien, Custom/Excluded Words und UI

### 🔧 CI-Verbesserungen

- **Sentry Mapping-Check** gehärtet (weich vs. streng, 13 Selbsttests)
- **Fastlane-Selbsttests** gegen SIGPIPE-Race gehärtet
- **DeepSource Kotlin-Analyse** bereinigt (KT-W1042, KT-R1006, KT-R1000)

## 📊 Metriken

| Metrik | Wert |
|--------|------|
| **CodeQL Status** | ✅ Sauber (0 offene Alerts) |
| **False Positives** | 10 (dokumentiert) |
| **Fixed Alerts** | 6 |
| **Preventive Fixes** | 2 |
| **Neue Features** | 4 |
| **CI-Härtung** | 3 Maßnahmen |

## 📦 Installationshinweise

### Für Endbenutzer
- Download der APK unter https://github.com/thoser666/Vivid/releases/tag/v0.5.7-beta
- Installation über Obtainium (Pre-Release-Channel aktivieren)

### Für Entwickler
- CodeQL-Scan läuft automatisch bei Push/PR
- Scorecard-Analyse wöchentlich auf master
- Stale-Issues werden automatisch verwaltet

## 🔜 Nächste Schritte

1. **Play-Upload vorbereiten**: Screenshots, Content Rating, Data Safety
2. **Kotlin 2.4.20 Update**: Warten auf stabile Version (September)
3. **Companion-App**: Remote-Steuerung für Settings/Widgets
4. **Multi-Plattform-Chat**: Kick, YouTube, SOOP Integration

---

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.6-beta...v0.5.7-beta

**Installation**: https://github.com/thoser666/Vivid/releases/tag/v0.5.7-beta

**Documentation**: https://thoser666.github.io/Vivid/

**Security**: https://thoser666.github.io/Vivid/privacy/
