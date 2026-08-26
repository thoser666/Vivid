# Release Notes: Vivid v0.5.8-beta.1

**Release Date:** 2026-08-26
**Version:** 0.5.8-beta.1 (versionCode 5081)
**Channel:** Beta (GitHub Releases)

## 🎉 Highlights

### 📦 Eigener F-Droid-Repo-Server
- **Eigener Repository-Server** auf GitHub Pages: `https://thoser666.github.io/Vivid/fdroid/repo`
- **QR-Code** für schnellen F-Droid-Scan: `https://thoser666.github.io/Vivid/fdroid/qr.svg`
- **5 Versionen** im Haupt-Repo (letzte Releases: Alpha + Beta + Nightly)
- **FOSS-Build-Flavor** (`com.vivid.foss`) für F-Droid Hauptrepo ohne Sentry/Tracking
- **Metadata-YAML** statt Python-Hack (`metadata/com.vivid.yml`)
- Automatisierter CI-Workflow: `deploy-fdroid.yml` (baut APKs, signiert Repo-Index)

### 🏷️ Workflow-Umbenennung
Klarere Bezeichnungen für bessere Übersicht:
| Vorher | Nachher | Zweck |
|--------|---------|-------|
| `android.yml` | `android-ci.yml` | Reines CI (Build/Lint/Tests) |
| `android_fastlane.yml` | `release-pipeline.yml` | Nightly/Stable/Play |
| `codeql.yml` | `security-codeql.yml` | CodeQL Security |
| `scorecard.yml` | `security-scorecard.yml` | OpenSSF Scorecard |
| `pages.yml` | `deploy-pages.yml` | GitHub Pages |
| `fdroid-repo.yml` | `deploy-fdroid.yml` | F-Droid Repository |

### 🔧 Build-Fixes
- **bundleStandardPlayRelease** nach foss-Flavor-Einführung (Variablen-Auflösung korrigiert)
- **AAB-Pfad** korrigiert: `app-standard-playRelease.aab` statt `app-standard-play-release.aab`
- **F-Droid Checkout** auf develop-Branch bei Release-Trigger korrigiert

## 📊 Metriken

| Metrik | Wert |
|--------|------|
| **F-Droid Repo** | ✅ Live mit 5 Versionen |
| **QR-Code** | ✅ Samsung-Kamera-kompatibel |
| **CI-Selbsttests** | ✅ publish_play dry_run grün |
| **Pre-Push-Gate** | ✅ Alle 10+ Checks grün |

## 📦 Installationshinweise

### Über F-Droid (Empfohlen)
1. F-Droid öffnen → Einstellungen → Repositories
2. QR-Code scannen: https://thoser666.github.io/Vivid/fdroid/qr.svg
3. Oder manuell: `https://thoser666.github.io/Vivid/fdroid/repo`

### Über GitHub Releases
- APK-Download: https://github.com/thoser666/Vivid/releases/tag/v0.5.8-beta.1
- Obtainium: Pre-Release-Channel aktivieren

### Für Entwickler
- `deploy-fdroid.yml` läuft bei jedem Tag automatisch
- F-Droid-Metadaten in `metadata/com.vivid.yml`
- Repository-Signatur: `vivid-fdroid` (eigener Keystore)

## 🔜 Nächste Schritte

1. **Play-Upload vorbereiten**: Screenshots, Content Rating, Data Safety
2. **Kotlin 2.4.20 Update**: Warten auf stabile Version (September)
3. **Companion-App**: Remote-Steuerung für Settings/Widgets
4. **Multi-Plattform-Chat**: Kick, YouTube, SOOP Integration

---

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.7-beta...v0.5.8-beta.1

**Installation**: https://github.com/thoser666/Vivid/releases/tag/v0.5.8-beta.1

**Documentation**: https://thoser666.github.io/Vivid/

**F-Droid**: https://thoser666.github.io/Vivid/fdroid/repo
