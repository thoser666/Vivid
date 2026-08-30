# Vivid v0.5.11-beta

**Veröffentlicht:** 30. August 2026

## ✨ Neue Features

### Chat-Anzeige-Details (komplett)
- **Reply-Indikator:** ↩ + Username der Eltern-Nachricht wird vor dem Username angezeigt
- **Bits-Anzeige:** ⭐ + Anzahl der Bits wird am Ende der Nachricht angezeigt
- **/me-Styling:** Kursive, graue Schreibweise für `/me`-Aktionen
- **Gelöschte Nachrichten:** EventSub `channel.chat.message_delete` mit Toggle zum Ausblenden/Ausgrauen (Bot muss Moderator sein)
- **Chat-Layout-Einstellungen:** Breite (100–400 dp), Höhe (100–600 dp), Hintergrund-Transparenz (0–100%), Schriftgröße (8–20 sp), Zeitstempel-Anzeige — alles als Slider/Switch in den Overlays-Settings
- **Chat-Layout-Farben:** Username-, Text- und Hintergrund-Farbe wählbar (Hex-Format #RRGGBB)
- **Chat-Overlay-Positionierung:** 4 Ecken wählbar (Oben links/rechts, Unten links/rechts) als FilterChips

### CI/CD
- **Snyk JAVA_HOME-Fix:** JDK-25-Setup aus Snyk-Workflow entfernt (Snyk bringt eigenes JDK 17 mit)
- **SARIF-Upload-Fix:** `--all-projects` entfernt (GitHub Code Scanning akzeptiert nur einen Run pro Upload)

## 🐛 Bugfixes
- PARITY-Log-Hashes korrigiert (orphaned Commits durch Amend-Zyklen)
- Lint `NonObservableLocale` in ChatOverlay behoben (jetzt `LocalLocale.current.platformLocale`)
- I18n-Fix für #RRGGBB Hint-Strings (in alle 3 Sprachen externalisiert)

## 📊 Statistik
- **Commits seit v0.5.10-beta:** 14
- **Geänderte Dateien:** 20+
- **Neue Unit-Tests:** 8 (ChatOverlayViewModel, SettingsViewModel)
- **PARITY-Status:** Chat-Anzeige-Details ✅ komplett (inkl. Layout)

## 🔗 Links
- [GitHub Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.11-beta)
- [Release-Notes (DE)](docs/release-notes-v0.5.11-beta.md)
