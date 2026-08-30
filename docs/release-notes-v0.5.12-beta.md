# Vivid v0.5.12-beta — F-Droid Archiv

**Veröffentlicht:** 30. August 2026

## 🗄️ F-Droid-Archiv-Repository

Das eigene F-Droid-Repository (`https://thoser666.github.io/Vivid/fdroid/repo`) hat jetzt ein **separates Archiv-Repository** — gelöst, warum der F-Droid-Client bisher *„Diese Paketquelle scheint kein Archiv zu haben“* anzeigte.

### Was geändert wurde
- **Zwei getrennte Repos:** `repo/` (die letzten 5 Release-Versionen, schnell für Updates) + `archive/` (**alle älteren Versionen**, persistent für Downgrades/Fallback)
- **`archive_older: 5`** — `fdroid update` verschiebt automatisch die 5 neuesten in `repo/` und alle älteren ins `archive/`
- **`archive_url` / `archive_name`** — der F-Droid-Client erkennt und zeigt das Archiv jetzt korrekt (Einstellungen ➜ Repository ➜ Archiv)
- **Persistenz:** Das `archive/`-Verzeichnis wird im git-Repo (unter `docs/fdroid/archive`) gespeichert und bei jedem Lauf wiederhergestellt — ältere Versionen gehen nicht verloren

### Nutzer-Vorteile
- **Downgrades:** Zurück zu älteren Versionen, falls ein neues Release Probleme macht
- **Fallback:** Auch sehr alte Versionen bleiben installierbar (z.B. für alte Android-Geräte)
- **Keine Warnung mehr:** Der Client zeigt das Archiv sauber an

## 📋 Technische Details
- **Workflow:** `.github/workflows/deploy-fdroid.yml` — lädt jetzt **alle** semver-Release-APKs (statt nur 5), überspringt Debug-Builds, verschmilzt das persistente Archiv
- **Config:** `fdroid/config.yml` — `archive_older`, `archive_url`, `archive_name`, `archive_description`, `archive_icon`
- **Doku:** `RELEASE.md` → Archivierungs-Strategie aktualisiert

## 📊 Statistik
- **APK-Größe:** ~7.7 MB pro Version (alle alten Versionen im Archiv ≈ 150 MB, im git-Repo handhabbar)

## 🔗 Links
- F-Droid Repo: https://thoser666.github.io/Vivid/fdroid/repo
- F-Droid Archiv: https://thoser666.github.io/Vivid/fdroid/archive
- QR-Code: https://thoser666.github.io/Vivid/fdroid/qr.svg
