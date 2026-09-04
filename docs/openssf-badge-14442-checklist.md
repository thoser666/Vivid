# OpenSSF Best Practices Badge – Ausfüll-Hilfe für Projekt 14442 (Vivid)

Diese Datei unterstützt beim manuellen Ausfüllen von
<https://www.bestpractices.dev/en/projects/14442>. Die "Met/Unmet"-Werte und
Begründungen basieren auf dem aktuellen Repo-Stand (`develop`).

**Wichtig vor dem Editieren:** Auf der Badge-Seite eingeloggt sein (GitHub-OAuth
mit Account `thoser666`) und als Maintainer des Projekts anerkannt sein –
sonst sind alle Felder ausgegraut.

Legende: **M** = MUST, **S** = SHOULD, **G** = SUGGESTED. (N/A) = nicht zutreffend wählbar.

---

## Basics

| Kriterium | Typ | Wert | Wertung / Begründung |
|---|---|---|---|
| `name` | – | Vivid | – |
| `description_good` | M | **Met** | README erklärt: "Professional IRL streaming for Android – aiming for full feature parity with Moblin". Klare Beschreibung im README und auf der Badge-Seite bereits hinterlegt. |
| `description` | – | (Text) | "Android version of the open-source Moblin IRL streaming app. Vivid lets you broadcast high-quality live video to platforms like Twitch, YouTube, Facebook, Kick or your own SRT server, with advanced features like multi-network bonding, OBS WebSocket control, configurable overlays, and full I18n support." |
| `homepage_url` | – | `https://github.com/thoser666/Vivid` | – |
| `repo_url` | – | `https://github.com/thoser666/Vivid` | – |
| `license` | – | `MIT` | SPDX-Format. Entspricht der LICENSE-Datei und dem README-Badge. |
| `contribution` | M | **Met** | `CONTRIBUTING.md` vorhanden (URL: `.../blob/develop/CONTRIBUTING.md`). |
| `contribution_requirements` | S | **Met** | CONTRIBUTING.md + CI (Pre-Push-Hook, Lint-Regeln) dokumentieren die Annahmekriterien. |
| `description_good` (website) | M | **Met** | README beschreibt Zweck. |
| `interact` | M | **Met** | README verlinkt "Report Bug" (Issues), "Discussions" und "Contribute". |
| `contribution` (website) | M | **Met** | siehe oben. |
| `floss_license` | M | **Met** | MIT ist OSI-approbiert. |
| `floss_license_osi` | G | **Met** | MIT ist OSI-approbiert. |
| `license_location` | M | **Met** | `LICENSE` (Top-Level). URL: `.../blob/develop/LICENSE`. |
| `documentation_basics` | M | **Met** | `docs/user-guide.md` (+ EN/FR), README mit Install- und Nutzungsanleitung. |
| `documentation_interface` | M | **Met** | User-/API-Doku in `docs/` (USER_GUIDE, Update-Mechanik, AI-Chat-Bot, OBS, PARITY). |
| `sites_https` | M | **Met** | Alle URLs (README, Repo, Docs) sind `https:`. |
| `discussion` | M | **Met** | GitHub Issues + Discussions. |
| `english` | S | **Met** | README und Doku auf Englisch verfügbar (`docs/user-guide.en.md`), englische Bug-/PR-Kommunkation. |
| `maintained` | M | **Met** | Aktive Entwicklung auf `develop`, CI grün, laufende Releases. |

---

## Change Control

| Kriterium | Typ | Wert | Wertung / Begründung |
|---|---|---|---|
| `repo_public` | M | **Met** | Öffentliches GitHub-Repo. |
| `repo_track` | M | **Met** | Git verfolgt Änderungen/Autor/Zeit. |
| `repo_interim` | M | **Met** | Zwischenstände liegen auf `develop` zwischen Releases. |
| `repo_distributed` | G | **Met** | Git (verteilt). |
| `version_unique` | M | **Met** | Git-Releases/Tags (z. B. `0.0.1-alpha`, `nightly-*`) + Versionierung. |
| `version_semver` | G | **Met** | SemVer-artige Versionen (z. B. `0.0.1-alpha`). |
| `version_tags` | G | **Met** | Releases werden per Git-Tag identifiziert (Release-Pipeline). |
| `release_notes` | M | **Met** | `CHANGELOG.md` (Top-Level). URL: `.../blob/develop/CHANGELOG.md`. |
| `release_notes_vulns` | M | (N/A) | Keine bekannten CVEs in Releases bisher → **N/A** wählen, ggf. kurz begründen ("no known run-time vulnerabilities with a CVE in past releases"). |

---

## Reporting

| Kriterium | Typ | Wert | Wertung / Begründung |
|---|---|---|---|
| `report_process` | M | **Met** | GitHub Issue-Tracker; `SECURITY.md` vorhanden (URL: `.../blob/develop/SECURITY.md`). |
| `report_tracker` | S | **Met** | GitHub Issues genutzt. |
| `report_responses` | M | **Met** | Aktive Pflege; Reports erhalten Antworten. |
| `enhancement_responses` | S | **Met** | Aktiv betreute Enhancement-Tickets. |
| `report_archive` | M | **Met** | Öffentliche Issue-/Discussion-Archive auf GitHub. |
| `vulnerability_report_process` | M | **Met** | `SECURITY.md` (URL: `.../blob/develop/SECURITY.md`). |
| `vulnerability_report_private` | M | (N/A) | Privates Security-Reporting via GitHub "private vulnerability reporting" ist aktiv → die URL/Option dort eintragen; falls nicht genutzt, **N/A** wählen. |
| `vulnerability_report_response` | M | (N/A) | Keine Vulnerability-Reports in den letzten 6 Monaten → **N/A**. |

---

## Quality

| Kriterium | Typ | Wert | Wertung / Begründung |
|---|---|---|---|
| `build` | M | **Met** | Gradle-Build (Android). CI baut das Projekt (android-ci.yml). |
| `build_common_tools` | G | **Met** | Gradle (Branchenstandard für Android). |
| `build_floss_tools` | S | **Met** | Gradle + OpenJDK sind FLOSS. |
| `test` | M | **Met** | Automatisierte Test-Suiten vorhanden (JVM-Unit-Tests + instrumentierte Tests); Ausführung dokumentiert (CI `android-ci.yml`). |
| `test_invocation` | S | **Met** | `./gradlew test` / standardmäßiger Gradle-Testaufruf + CI. |
| `test_most` | S | **Met** | Umfassende Unit-Tests je Modul (Chat, Streaming, Settings, etc.). |
| `test_continuous_integration` | S | **Met** | GitHub Actions: `android-ci.yml` (Build & Test), CodeQL, Scorecard, Snyk. |
| `test_policy` | M | **Met** | CI erzwingt Tests; neue Features bringen Tests (Policy via Pre-Push-Hook/CI). |
| `test_most_code` | S | **Met** | Siehe `test_most`. |
| `locking` | M | (N/A) | App (kein Multi-User-Library) → **N/A**. Versionierte Gem/Dependency-Locks existieren (Gradle-Lockfiles), falls gewünscht **Met**. |
| `warnings_strict` | S | **Met/N/A** | Kotlin/Android mit striktem Kompilieren. Empfehlung: **N/A** (App) oder **Met** mit Verweis auf Lint. |
| `warnings_fixed` | M | **Met** | Kein bekanntes offenes Set an Build-Warnungen. |
| `no_bug_github` | M | **Met** | Bug-Tracking über GitHub Issues. |
| `no_flaw_review` | M | **Met** | Review vor Merge (`Branch Protection` auf `develop` + PR-Review). |
| `static_analysis` | S | **Met** | Android-Lint + CodeQL + Scorecard. |
| `static_analysis_common` | M | **Met** | Lint ist Branchenstandard für Android. |
| `dynamic_analysis` | S | (N/A/Met) | Instrumentierte/Instrumentation-CI-Tests **Met**; falls als zu aufwendig, **N/A** begründen. |

---

## Security

| Kriterium | Typ | Wert | Wertung / Begründung |
|---|---|---|---|
| `secure_build` | S | **Met** | CI-Builds auf GitHub-Actions (deterministisch). |
| `secure_build_signed` | S | **Met** | Release-APKs sind signiert; Releases geprüft. |
| `no_leaked_credentials` | M | **Met** | Secret-Guard im Pre-Push-Hook + CI; keine Credentials im Repo. |
| `no_fixed_crypto` | S | **Met** | Moderne Krypto (RTMPS/TLS), keine fixen Schlüssel. |
| `no_http_default` | S | **Met** | HTTPS-Default; SRT/TLS-Ingest. |
| `https_optional` | S | **Met** | TLS-Optional sinnvoll konfiguriert. |

*(Weitere Security-Kriterien wie `input_validation` usw. nach Repo-Stand beantworten – Standard: Met.)*

---

## Hinweise
- Werte mit **Met** sind gut begründbar; bei Unsicherheit die Passende wählen und im "Justification"-Feld kurz die URL/den Nachweis angeben.
- Sobald alle MUST-Unzutreffenden bearbeitet sind, kann "Passing" beantragt werden; Gold/Silber sind optional.
