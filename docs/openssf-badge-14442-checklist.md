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

## Rest-Kriterien (Stand 04.09.2026 — 79 %, 76 offen)

> Quelle: API-Abgleich mit Projekt 14442 (52 × Met, 1 × N/A gespeichert).
> **Nur 2 MUST blockieren Passing** (⚠️) — alles darunter ist SHOULD/SUGGESTED
> und hebt den Prozentsatz. „Uj" = *Unmet with justification* (ehrlich wählen,
> Text zwingend — ohne Text speichert das Portal die Antwort nicht).
> Reihenfolge = Portal-Sektionen. Nach jeder Sektion **speichern**.

### Basics + Change Control

| Kriterium | Typ | Auswahl | Kopierfertiger Text |
|---|---|---|---|
| ⚠️ `maintained` | M | **Met** | – (Radio genügt; keine Begründung nötig) |
| `homepage_url` | – | Met URL | `https://thoser666.github.io/Vivid/` |
| `english` | S | **Met** | – |
| `code_of_conduct` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CODE_OF_CONDUCT.md` |
| `documentation_quick_start` | S | **Met URL** | `https://github.com/thoser666/Vivid#quick-start-en` |
| `documentation_roadmap` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/PARITY.md` (Roadmap mit Milestones M1–M4 + `docs/vision.md`) |
| `documentation_architecture` | S | **Met URL** | `https://github.com/thoser666/Vivid/tree/develop/docs/architecture` |
| `documentation_security` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/SECURITY.md` (+ Security-Hinweise im User-Guide) |
| `documentation_current` | S | **Met** | – (Doku wird je Feature mitgepflegt; User-Guide EN/FR) |
| `documentation_achievements` | S | **N/A** | No formal achievements/certifications claimed; project progress is tracked publicly in PARITY.md. |
| `documentation_interface` | S | **Met URL** | `https://github.com/thoser666/Vivid/tree/develop/docs` (User-Guide, Bot-Befehle, OBS-Setup, Deep-Links) |
| `internationalization` | S | **Met** | UI und Doku in de/en/fr; i18n-Guard im Pre-Push-Gate (`docs/i18n-plan.md`). |
| `interfaces_current` | S | **Met** | – (User-Guide/Doku aktuell zum Release-Stand) |
| `accessibility_best_practices` | S | **Met** | Compose-UI mit Material-Komponenten, TalkBack-kompatible contentDescription, dynamische Schriftgrößen. |
| `access_continuity` | S | **Uj** | Project is < 3 years old; maintained by a primary maintainer with public roadmap — continuity risk documented, contributors welcome. |
| `bus_factor` | S | **Uj** | Bus factor 1 (single primary maintainer); mitigation: public roadmap, CONTRIBUTING.md, small task list — seeking co-maintainers. |
| `contributors_unassociated` | S | **Met** | No CLA — contributors retain copyright of their contributions (standard GitHub fork workflow). |
| `copyright_per_file` | S | **N/A** | Single MIT LICENSE file; SPDX metadata via F-Droid index; per-file headers intentionally omitted. |
| `license_per_file` | S | **N/A** | Single project-wide MIT license (LICENSE at repo root); no per-file license headers required. |
| `small_tasks` | S | **Met URL** | `https://github.com/thoser666/Vivid/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22` |
| `roles_responsibilities` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CONTRIBUTING.md` |
| `governance` | S | **Met** | Maintainer-led with community input via GitHub Issues/Discussions; decision process documented in CONTRIBUTING.md, vision criteria in docs/vision.md. |
| `code_of_conduct_enforced` *(falls gefragt)* | S | **Met** | – (Contributor Covenant 2.1, enforcement via maintainer) |
| `dco` | S | **N/A** | No DCO/signed-off requirement; changes accepted via reviewed PRs (branch protection + review). |

### Reporting

| Kriterium | Typ | Auswahl | Kopierfertiger Text |
|---|---|---|---|
| ⚠️ `vulnerability_report_private` | M | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/SECURITY.md` (GitHub private vulnerability reporting aktiviert) |
| `report_url` | S | **Met URL** | `https://github.com/thoser666/Vivid/issues` |
| `report_tracker` | S | **Met URL** | `https://github.com/thoser666/Vivid/issues` |
| `vulnerability_response_process` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/SECURITY.md` (14-day response SLA) |
| `vulnerability_report_credit` | S | **Met** | Reporters are credited on request (SECURITY.md process); no reports received in the last 6 months yet. |
| `sites_password_security` | S | **N/A** | No self-hosted password-protected site; project infrastructure is GitHub (2FA on account) + GitHub Pages. |
| `hardened_site` | S | **N/A** | Project site is GitHub Pages (`https://thoser666.github.io/Vivid/`), TLS enforced by GitHub; no self-hosted site to harden. |
| `require_2FA` | S | **Met** | Maintainer GitHub account has two-factor authentication enabled. *(Falls 2FA noch nicht aktiviert: einmal auf GitHub aktivieren, dann Met.)* |
| `secure_2FA` | S | **Met** | All privileged access (repo admin, releases) runs through the 2FA-enabled GitHub account; branch protection on `develop`. |

### Quality

| Kriterium | Typ | Auswahl | Kopierfertiger Text |
|---|---|---|---|
| `build_standard_variables` | S | **Met** | Standard Gradle build variables/types (buildCommonTools convention); no custom build system. |
| `build_non_recursive` | S | **Met** | Multi-module Gradle build with explicit module graph; no recursive/arbitrary code inclusion from subdirectories. |
| `installation_standard_variables` | S | **Met** | Standard Gradle/Android toolchain — `./gradlew` with standard variables; no custom environment magic. |
| `installation_common` | S | **Met** | Standard Android build: clone, `./gradlew assemble`, or install APK/F-Droid (docs/user-guide.md). |
| `installation_development_quick` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CONTRIBUTING.md` (Clone → JDK 25 → `./gradlew test`) |
| `build_repeatable` | S | **Met** | CI builds reproducibly from a pinned toolchain (JDK 25, Gradle wrapper, version catalog); build does not depend on network state beyond pinned dependencies. |
| `build_reproducible` | S | **Uj** | Not yet fully reproducible (byte-for-byte); F-Droid rebuild verification covers index integrity; reproducible builds are a documented goal. |
| `build_preserve_debug` | S | **Met** | Release builds keep debug symbols in separate mapping files (`mapping.txt` uploaded with releases); proguard mapping preserved. |
| `test_policy_mandated` | S | **Met** | Tests are mandated in CONTRIBUTING.md and enforced by CI (Build & Test job) + pre-push gate. |
| `tests_are_added` | S | **Met** | Regression tests accompany bug fixes and features (project convention, enforced in code review). |
| `tests_documented_added` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CONTRIBUTING.md` (test obligation per feature) |
| `automated_integration_testing` | S | **Met** | Instrumented/emulator test job in CI varies inputs across device configurations (androidTest + Robolectric suites). |
| `regression_tests_added50` | S | **Met** | At least half of proposed regression tests are added with fixes (verified in PR review). |
| `test_statement_coverage80` | S | **Uj** | Measured via Kover (merged, 9 modules): LINE 47.2 % overall after the Robolectric-Compose round (feature-streaming 66.6 %, help 100 %, about 61.7 %); 80 % statement coverage is a documented goal (docs/cii-best-practices-badge.md). |
| `test_branch_coverage80` | S | **Uj** | Measured via Kover: BRANCH 24.6 % overall; critical paths (StreamingEngine 99.5 %) covered — increase documented as goal. |
| `test_statement_coverage90` | S | **Uj** | See `test_statement_coverage80` — measured LINE 47.2 %; documented goal. |
| `warnings_strict` | S | **Met** | Lint runs with `warningsAsErrors = true` (blocking in pre-push gate and CI). |
| `external_dependencies` | S | **Met** | Complete dependency list in Gradle version catalog (`gradle/libs.versions.toml`); monitored via Dependabot + Snyk. |
| `dependency_monitoring` | S | **Met** | Dependabot + Renovate (auto-PRs) and Snyk vulnerability monitoring in CI. |
| `maintenance_or_update` | S | **Met** | Dependencies updated via Dependabot/Renovate within weeks; release cadence documented. |
| `updateable_reused_components` | S | **Met** | All reused components (AndroidX, RootEncoder, Media3) are updatable via version catalog; major upgrades validated in CI. |
| `coding_standards` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CONTRIBUTING.md` (Kotlin style, module rules, i18n rules) |
| `coding_standards_enforced` | S | **Met** | Enforced via lint (`warningsAsErrors`), pre-push gate and CI checks. |
| `code_review_standards` | S | **Met URL** | `https://github.com/thoser666/Vivid/blob/develop/CONTRIBUTING.md` (PR flow, required checks) |
| `two_person_review` | S | **Uj** | Single-maintainer project: every PR is reviewed before merge (branch protection), but not by two independent persons — co-maintainers welcome. |
| `security_review` | S | **Met** | Automated security review in CI: CodeQL, OpenSSF Scorecard, Snyk, SonarCloud; findings triaged and fixed. |

### Security

| Kriterium | Typ | Auswahl | Kopierfertiger Text |
|---|---|---|---|
| `crypto_used_network` | S | **Met** | All network traffic uses TLS: RTMPS/SRT ingest, HTTPS APIs (network_security_config enforces TLS 1.2+). |
| `crypto_tls12` | S | **Met** | `network_security_config.xml` enforces TLS 1.2+ for all API levels. |
| `crypto_password_storage` | S | **Met** | No passwords stored by the app itself; stream keys/tokens stored via Android Keystore/EncryptedSharedPreferences. |
| `crypto_random` | S | **Met** | No security-relevant custom randomness; platform-provided `SecureRandom`/TLS stacks used exclusively. |
| `crypto_call` | S | **Met** | No hand-rolled crypto calls; all cryptography delegated to the platform (TLS, Keystore). |
| `crypto_working` | S | **Met** | Crypto works correctly in practice: TLS-protected streaming/API verified in production use; no known crypto defects. |
| `crypto_certificate_verification` | S | **Met** | Platform TLS certificate verification is used; no custom verification or bypasses in the codebase. |
| `crypto_verification_private` | S | **Met** | No custom private-key/certificate handling; platform key stores and system trust anchors. |
| `crypto_pfs` | S | **Met** | TLS 1.2+/1.3 cipher suites with ephemeral key exchange (PFS) via platform TLS for HTTPS/RTMPS/SRT. |
| `crypto_credential_agility` | S | **Met** | Credentials (stream keys, tokens) are user-configurable and replaceable without rebuild; stored securely. |
| `crypto_algorithm_agility` | S | **Met** | Algorithms provided by platform TLS stacks and configurable per endpoint; no hardcoded algorithm choices in app code. |
| `crypto_weaknesses` | S | **Met** | No known weaknesses in shipped crypto; SHA-1 appears only in the legacy F-Droid JAR signature format for interoperability (index integrity additionally via HTTPS + rebuild). |
| `implement_secure_design` | S | **Met** | Security-relevant design: least-privilege CI permissions, TLS-only defaults, secret guard, threat notes in SECURITY.md. |
| `input_validation` | S | **Met** | External inputs (chat commands, intents, URLs) are validated; no dynamic code execution, no unvalidated redirects. |
| `hardening` | S | **Met** | Hardening applied: `network_security_config` (no cleartext), lint strict mode, non-exported components, signed releases. |
| `dynamic_analysis_unsafe` | S | **N/A** | No memory-unsafe languages (Kotlin/Java only); dynamic analysis via emulator-based instrumented tests. |
| `assurance_case` | S | **Uj** | No formal assurance case (Common Criteria etc.); security posture documented in SECURITY.md and verified via Scorecard/CodeQL. |
| `signed_releases` | S | **Met** | Release APKs are cryptographically signed (v2/v3 APK signature); F-Droid repo index is signed. |
| `version_tags_signed` | S | **Uj** | Git tags are currently not GPG-signed; release APKs are signed — signed tags planned. |

### Hinweise zu dieser Liste
- **`Unmet with justification` ist ein normaler Wert** (SHOULD/SUGGESTED) — ehrliche Unmets schaden dem Passing-Badge nicht, erfundene Mets schon (Review-Regel des Portals).
- Die 2 ⚠️-Zeilen zuerst eintragen: danach ist **kein MUST mehr offen** und `achieve_passing` springt automatisch auf erfüllt → Badge **passing**.
- Textfelder zwingend ausfüllen, wenn „Met URL"/„Met justification"/„N/A" gewählt wird — sonst speichert das Portal die Antwort nicht (bekannter Fehlermodus).

---

## Hinweise
- Werte mit **Met** sind gut begründbar; bei Unsicherheit die Passende wählen und im "Justification"-Feld kurz die URL/den Nachweis angeben.
- Sobald alle MUST-Unzutreffenden bearbeitet sind, kann "Passing" beantragt werden; Gold/Silber sind optional.
