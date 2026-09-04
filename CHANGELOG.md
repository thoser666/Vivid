# 📝 Changelog

Alle Änderungen an Vivid — **automatisch aus den GitHub-Releases gespiegelt**.

> ⚠️ **Nicht von Hand bearbeiten.** Der generierte Abschnitt (unten, zwischen den beiden Marker-Kommentaren) wird von [`scripts/update_changelog.sh`](scripts/update_changelog.sh) gepflegt — Workflow: [`.github/workflows/automation-changelog.yml`](.github/workflows/automation-changelog.yml), Trigger: `release: published` + manuell.

- 🔗 Aktuelle Releases: <https://github.com/thoser666/Vivid/releases>
- 📦 Release-Kanäle & Versionsstrategie (nightly → alpha → beta → stable): [RELEASE.md](RELEASE.md)
- 🎯 Feature-Tracking: [PARITY.md](PARITY.md)

<!-- CHANGELOG-START -->
## 🌙 **Nightly** 0.5.11-nightly.161 — 2026-09-04

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260904-103135)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.11-nightly.139 — 2026-09-03

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260903-104853)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.11-nightly.118 — 2026-09-02

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260902-103230)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.11-nightly.114 — 2026-09-01

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260901-110756)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.11-nightly.89 — 2026-08-30

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260830-111210)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.11-beta — 2026-08-30

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.11-beta)

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

## 🌙 **Nightly** 0.5.10-nightly.74 — 2026-08-29

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260829-121701)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.10-beta — 2026-08-29

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.10-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260828-181145...v0.5.10-beta

## 🌙 **Nightly** 0.5.9-nightly.60 — 2026-08-28

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260828-181145)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.9-nightly.27 — 2026-08-27

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260827-173043)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.9-beta — 2026-08-27

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.9-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.7-beta...v0.5.9-beta

## 🚀 **Stable** v0.5.8-beta.1 — 2026-08-26

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.8-beta.1)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.5.7-beta...v0.5.8-beta.1

## 🟡 **Beta** v0.5.7-beta — 2026-08-26

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.7-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260825-065435...v0.5.7-beta

## 🌙 **Nightly** 0.5.6-nightly.336 — 2026-08-25

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260825-065435)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.6-nightly.331 — 2026-08-24

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260824-070655)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.6-beta — 2026-08-23

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.6-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260823-064957...v0.5.6-beta

## 🌙 **Nightly** 0.5.5-nightly.324 — 2026-08-23

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260823-064957)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.5-nightly.304 — 2026-08-22

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260822-064747)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.5-nightly.297 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-113155)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.5-nightly.296 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-111303)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.5-nightly.294 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-103245)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.5-beta — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.5-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260821-092552...v0.5.5-beta

## 🌙 **Nightly** 0.5.4-nightly.293 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-092552)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.291 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-090739)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.290 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-083650)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.289 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-081905)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.288 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-080139)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.286 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-074329)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.285 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-071421)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.284 — 2026-08-21

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260821-065516)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.283 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-153126)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.4-nightly.281 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-151356)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.4-beta — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.4-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260820-143702...v0.5.4-beta

## 🌙 **Nightly** 0.5.3-nightly.280 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-143702)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.278 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-141708)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.276 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-135724)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.275 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-132815)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.274 — 2026-08-20

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260820-124644)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.267 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-160518)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.265 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-154850)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.264 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-152544)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.3-nightly.260 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-150949)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.3-beta — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.3-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260819-124541...v0.5.3-beta

## 🌙 **Nightly** 0.5.3-nightly.258 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-145315)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.257 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-124541)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.256 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-081848)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.253 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-080148)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.252 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-073928)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.248 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-072341)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.247 — 2026-08-19

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260819-065016)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.246 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-180117)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.2-nightly.243 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-174510)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.2-beta — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.2-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260818-163136...v0.5.2-beta

## 🌙 **Nightly** 0.5.1-nightly.242 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-172710)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.241 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-163136)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.239 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-161508)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.238 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-155802)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.234 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-153949)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.233 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-151822)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.232 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-134731)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.231 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-131131)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.230 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-122712)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.227 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-120313)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.1-nightly.226 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-113855)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.1-beta — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.1-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260818-095427...v0.5.1-beta

## 🌙 **Nightly** 0.5.1-nightly.223 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-111249)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.222 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-095427)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.221 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-093022)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.220 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-091742)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.219 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-090038)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.218 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-083635)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.217 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-080728)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.216 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-075100)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.215 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-073409)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.209 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-065033)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.208 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-045520)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.207 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-043642)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.206 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-042155)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.203 — 2026-08-18

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260818-040545)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.202 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-180654)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.200 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-130412)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.199 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-124507)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.198 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-121331)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.196 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-085620)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🟡 **Beta** v0.5.0-beta — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.0-beta)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260817-075152...v0.5.0-beta

## 🌙 **Nightly** 0.5.0-nightly.195 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-083933)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.194 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-075152)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.193 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-071636)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.192 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-070102)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.191 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-051526)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.190 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-045901)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.189 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-040617)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.5.0-nightly.188 — 2026-08-17

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260817-035010)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.5.0-alpha — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.0-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260816-100431...v0.5.0-alpha

## 🌙 **Nightly** 0.4.2-nightly.185 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-103640)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.184 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-102131)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.183 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-100431)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.182 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-094922)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.181 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-093133)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.180 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-085704)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.179 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-084115)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.178 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-075140)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.176 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-071906)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.175 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-064747)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.174 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-052512)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.171 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-050927)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.170 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-045341)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.169 — 2026-08-16

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260816-043816)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.168 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-123632)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.167 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-122007)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.165 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-1203)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.2-nightly.163 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-1148)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.4.2-alpha — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.4.2-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260815-0915...v0.4.2-alpha

## 🌙 **Nightly** 0.4.2-nightly.161 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-1015)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.160 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0959)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.159 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0915)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.158 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0734)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.156 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0717)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.155 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0701)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.153 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0645)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.152 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0324)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.4.1-nightly.151 — 2026-08-15

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260815-0246)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.4.1-alpha — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.4.1-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/v0.4.0-alpha...v0.4.1-alpha

## 🌙 **Nightly** 0.4.0-nightly.149 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1505)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.4.0-alpha — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.4.0-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260814-1310...v0.4.0-alpha

## 🌙 **Nightly** 0.3.0-nightly.147 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1310)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.146 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1244)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.144 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1150)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.143 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1134)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.141 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1119)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.140 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1057)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.139 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-1023)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.138 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0943)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.137 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0912)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.136 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0848)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.135 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0827)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.134 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0811)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.133 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0755)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.132 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0732)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.131 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0619)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.130 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0605)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.129 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0548)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.128 — 2026-08-14

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260814-0532)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.127 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-1630)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.126 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-1618)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.125 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-1219)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.124 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-1140)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.123 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-1039)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.122 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-0928)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.121 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-0844)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.120 — 2026-08-13

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260813-0733)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.119 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1334)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.118 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1317)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.117 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1250)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.115 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1217)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.3.0-nightly.114 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1112)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.3.0-alpha — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.3.0-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260812-1015...v0.3.0-alpha

## 🌙 **Nightly** 0.2.0-nightly.112 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1043)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.111 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1030)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.110 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-1015)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.109 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0952)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.108 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0820)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.107 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0751)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.106 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0606)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.105 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0528)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.104 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0430)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.103 — 2026-08-12

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260812-0411)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.102 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1255)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.101 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1243)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.100 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1225)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.99 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1203)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.98 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1152)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.97 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.93 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0428)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.91 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0410)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.88 — 2026-08-11

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0351)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.87 — 2026-08-10

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260810-1614)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.86 — 2026-08-10

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260810-1602)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🌙 **Nightly** 0.2.0-nightly.85 — 2026-08-10

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/nightly-20260810-1540)

Nightly feature build — installable via Obtainium (enable pre-releases).

**Artefakte:** `app-release.apk` · `mapping.txt` · `output-metadata.json`

## 🧪 **Alpha** v0.2.0-alpha — 2026-08-10

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/v0.2.0-alpha)

**Full Changelog**: https://github.com/thoser666/Vivid/compare/nightly-20260810-1048...v0.2.0-alpha

## 🧪 **Alpha** 0.0.1-alpha — 2025-08-29

[GitHub-Release](https://github.com/thoser666/Vivid/releases/tag/0.0.1-alpha)

- Camera is displayed Button to switch the camera
- Button to start the stream (should display errors)
- Errors are transmitted via sentry.io

## What's Changed

- Thoser666 patch 1 by @thoser666 in https://github.com/thoser666/Vivid/pull/1
- Create SECURITY.md by @thoser666 in https://github.com/thoser666/Vivid/pull/2
- Create dependabot.yml by @thoser666 in https://github.com/thoser666/Vivid/pull/3
- adding sentry.io by @thoser666 in https://github.com/thoser666/Vivid/pull/9
- style: format code with Ktlint by @deepsource-autofix[bot] in https://github.com/thoser666/Vivid/pull/10
- Bump kotlin from 2.0.21 to 2.2.0 by @dependabot[bot] in https://github.com/thoser666/Vivid/pull/5
- Create android.yml by @thoser666 in https://github.com/thoser666/Vivid/pull/13
- Bump androidx.activity:activity-compose from 1.8.0 to 1.10.1 by @dependabot[bot] in https://github.com/thoser666/Vivid/pull/4
- Bump androidx.lifecycle:lifecycle-runtime-ktx from 2.6.1 to 2.9.2 by @dependabot[bot] in https://github.com/thoser666/Vivid/pull/8
- Bump androidx.test.espresso:espresso-core from 3.5.1 to 3.7.0 by @dependabot[bot] in https://github.com/thoser666/Vivid/pull/7
- Bump androidx.core:core-ktx from 1.10.1 to 1.16.0 by @dependabot[bot] in https://github.com/thoser666/Vivid/pull/6
- Create codeql.yml by @thoser666 in https://github.com/thoser666/Vivid/pull/16
- coreFunctions-AndUI by @thoser666 in https://github.com/thoser666/Vivid/pull/12
<!-- CHANGELOG-END -->
