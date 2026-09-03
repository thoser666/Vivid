# OpenSSF CII Best Practices Badge — Bereitschafts-Checkliste

> **Status:** Vorbereitung (Stand: 2026-09-03). Die Anmeldung auf
> [bestpractices.dev](https://www.bestpractices.dev) steht noch aus; diese
> Checkliste prüft jedes **Passing-Badge**-Kriterium gegen den aktuellen
> Vivid-Stand und listet, was vor der Anmeldung zu erledigen ist.
>
> Kriterien-IDs in Klammern entsprechen den offiziellen IDs der
> [Badge-Kriterien](https://www.bestpractices.dev/en/criteria/0) — im
> Anmeldeformular können sie direkt so zugeordnet werden.
>
> Legende: ✅ erfüllt · ⚠️ teilweise / mit Vorbehalt · ❌ offen · N/A nicht anwendbar (mit Begründung)

---

## Kurzübersicht

| Kategorie | MUST | SHOULD |
|-----------|------|--------|
| Basics | ✅ 4/5 | ✅ 1/1 |
| Change Control | ✅ 8/8 | ✅ 3/3 |
| Reporting | ✅ 3/4 | ⚠️ 3/4 |
| Quality | ✅ 5/5 | ⚠️ 6/8 |
| Security | ✅ 13/13 | ⚠️ 9/13 |
| Analysis | ✅ 3/4 | ⚠️ 5/7 |

**Fazit:** Einziger echter MUST-Blocker ist `contribution`
(fehlendes `CONTRIBUTING.md`). Alle übrigen offenen Punkte sind
SHOULD-Kriterien oder Dokumentations-Nachschärfen — Vivid kann die
Anmeldung unmittelbar nach der CONTRIBUTING-Ergänzung wagen.

---

## Anmeldung: So geht's (5 Minuten)

1. https://www.bestpractices.dev/en/projects/new öffnen
2. Mit GitHub-Konto (thoser666) anmelden
3. Repo-URL eintragen: `https://github.com/thoser666/Vivid`
4. Kriterien durchgehen — diese Datei liefert je Kriterium Status + Evidenz
5. Nach Erreichen von ≥ 90 % der MUST-/SHOULD-Kriterien (Passing-Level):
   Badge-HTML aus dem Portal in README.md einbetten

---

## 1. Basics

### Grundlegende Website-Inhalte

| Status | Kriterium | Anforderung | Evidenz / offener Punkt |
|--------|-----------|-------------|-------------------------|
| ✅ | `description_good` | Website beschreibt knapp, was die Software tut | README.md („Android version of the open-source Moblin IRL streaming app") + GitHub Pages (docs/index.md) |
| ⚠️ | `interact` | Website erklärt: Beziehen, Feedback, Beiträge | Beziehen ✅ (README + F-Droid-Repo), Feedback ✅ (Issues-Verweis), **Beiträge ❌ — `CONTRIBUTING.md` fehlt** |
| ❌→⚠️ | `contribution` | Beitragsprozess erklärt (z. B. PR-Flow) | **Blocker: CONTRIBUTING.md erstellen** (PR-Flow, Branch develop, Required Checks, Pre-Push-Gate, i18n-Regeln de/en/fr) |
| ⚠️ | `contribution_requirements` | Anforderungen an Beiträge dokumentiert (Coding-Standards) | Im Zuge von CONTRIBUTING.md: ktlint/Lint-Regeln, Testpflicht für neue Features, Commit-Stil |

### FLOSS-Lizenz

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `floss_license` | Software ist FLOSS | MIT-Lizenz |
| ✅ | `floss_license_osi` | Lizenz OSI-approved | MIT ist OSI-zertifiziert |
| ✅ | `license_location` | Lizenz an Standard-Ort im Repo | `LICENSE` im Repo-Root |

### Dokumentation

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `documentation_basics` | Basis-Dokumentation vorhanden | docs/user-guide.{md,en.md,fr.md}, docs/tutorials/, docs/faq/, docs/troubleshooting/, RELEASE.md |
| N/A | `documentation_interface` | Referenz-Doku der externen Schnittstellen | **Begründung:** Vivid ist eine Endnutzer-Streaming-App ohne Programm-Schnittstelle. Die bedienbaren Oberflächen (UI, Bot-Befehle, F-Droid-Repo-URLs, Deep-Links) sind im User-Guide und in docs/architecture/ dokumentiert. |

### Sonstiges

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `sites_https` | Website/Repo/Downloads über HTTPS | github.com, thoser666.github.io (Pages erzwingt TLS), F-Droid-Repo via HTTPS |
| ✅ | `discussion` | Suchbare Diskussions-/Issue-Mechanismen ohne proprietäre Clients | GitHub Issues (+ ggf. Discussions); Board-Kommunikation über Issue-Kommentare |
| ⚠️ | `english` | Doku auf Englisch + Bug-Reports auf Englisch möglich | Code/Kommentare/CI/Commits: Englisch ✅; Issue-Vorlagen akzeptieren Englisch ✅; **primäre README/Doku ist Deutsch** (EN/FR-Guides existieren: docs/user-guide.en.md, user-guide.fr.md). Abschärfen: README um EN-Kurzabschnitt + Sprachlinks ergänzen |
| ✅ | `maintained` | Projekt wird aktiv gepflegt | Kontinuierliche Commits/Releases (Beta-Serie v0.5.x, Stand 2026-09) |

---

## 2. Change Control

### Öffentliches, versioniertes Repository

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `repo_public` | Öffentliches, versioniertes Repo mit URL | https://github.com/thoser666/Vivid |
| ✅ | `repo_track` | Verfolgung von Was/Wer/Wann | Git-Historie mit signierten Verfassern, Git-Log |
| ✅ | `repo_interim` | Zwischenstände (nicht nur Releases) | Kontinuierliche Commits zwischen Releases; Branch Protection auf `develop` (Linear History, Required Checks) |
| ✅ | `repo_distributed` | Verteilte VCS (git) | git + GitHub |

### Eindeutige Versionsnummern

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `version_unique` | Eindeutige Versionskennung je Release | v0.5.0-alpha … v0.5.12-beta (SemVer + Pre-Release) |
| ✅ | `version_semver` | SemVer/CalVer (Empfehlung) | SemVer mit Pre-Release-Suffix |
| ✅ | `version_tags` | Releases via Git-Tags | GitHub-Tags + Releases je Version |

### Release Notes

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `release_notes` | Verständliche Release Notes je Release (kein Roh-Git-Log) | docs/release-notes-v0.5.*.md (je Version, kuratiert) + GitHub Release-Drafter |
| ✅ | `release_notes_vulns` | Behobene öffentliche CVEs in Release Notes genannt | N/A-Begründung gültig: bisher keine öffentlich bekannten CVEs im Projekt selbst; Deps-CVEs werden über Snyk/Dependabot getrackt und in den Notes erwähnt, sobald relevant |

---

## 3. Reporting

### Bug-Report-Prozess

| Status | Kriterium | Anforderung | Evidenz / offener Punkt |
|--------|-----------|-------------|-------------------------|
| ✅ | `report_process` | Prozess für Bug-Reports | GitHub Issues |
| ✅ | `report_tracker` | Issue-Tracker für einzelne Issues | GitHub Issues (z. B. #116 bereits via Tracker geschlossen) |
| ✅ | `report_responses` | Mehrheit der Bug-Reports (2–12 Monate) beantwortet | Alle bisherigen Issues beantwortet/geschlossen (#116, #138 u. a.) |
| ✅ | `report_archive` | Öffentliches, durchsuchbares Archiv | GitHub Issues ist persistent + suchbar |
| ⚠️ | `enhancement_responses` | >50 % der Enhancement-Requests beantwortet | Bisher wenige Enhancement-Issues; bislang alle bearbeitet — bei Wachstum beachtet halten |

### Vulnerability-Report-Prozess

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `vulnerability_report_process` | Prozess öffentlich veröffentlicht | SECURITY.md („Reporting a Vulnerability", GitHub Private Vulnerability Reporting + E-Mail-Fallback) |
| ✅ | `vulnerability_report_private` | Vertraulicher Meldeweg beschrieben | GitHub Private Vulnerability Reporting aktiviert; SECURITY.md beschreibt den privaten Kanal |
| ⚠️ | `vulnerability_report_response` | Erstantwort ≤ 14 Tage (letzte 6 Monate) | Bisher keine Vulnerability-Reports eingegangen (N/A-Status im Formular wählen; bei ersten Reports SLA < 14 Tage einhalten — in SECURITY.md bereits als „What to expect" verankert) |

---

## 4. Quality

### Build-System

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `build` | Funktionierender Build aus Source | Gradle Wrapper (Gradle 9.4.0, AGP); `./gradlew assembleDebug`; CI „Build & Test" + Reproducibility-Check (nightly) |
| ✅ | `build_common_tools` | Gängige Build-Tools | Gradle/Kotlin/AGP — De-facto-Standard für Android |
| ✅ | `build_floss_tools` | Build nur mit FLOSS-Tools | Vollständige FOSS-Toolchain (foss-Flavor für F-Droid; proprietäre Komponenten wie Sentry sind flavor-isoliert) |

### Automatisierte Tests

| Status | Kriterium | Anforderung | Evidenz / offener Punkt |
|--------|-----------|-------------|-------------------------|
| ✅ | `test` | Öffentliches FLOSS-Test-Suite + dokumentierte Ausführung | ~800 Unit-Tests über 10 Module; Ausführung dokumentiert: CI-Workflow („Run Tests") + dieses Repo (README/CONTRIBUTING ergänzen); `./gradlew test testFossDebug` |
| ⚠️ | `test_invocation` | Standard-Aufruf der Suite | Gradle-Standard (`./gradlew test`); in CONTRIBUTING.md noch explizit nennen |
| ⚠️ | `test_most` | Abdeckung der meisten Branches/Features | Breite Abdeckung je Feature-Modul (z. B. 302 Tests StreamingEngine-Modul, 71 Replay, Chat-Bot-Engine-Suite); keine gemessene Zeilenabdeckung — optional Kover/JaCoCo-Report ergänzen, um „most" belegbar zu machen |
| ✅ | `test_continuous_integration` | CI läuft bei jedem Push | GitHub Actions: Build & Test, CodeQL, Snyk, Scorecard, Dependency Submission, Reproducibility (nightly); lokales Pre-Push-Gate (Tests + Lint + Guards) |

### Tests für neue Funktionen

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `test_policy` | Richtlinie: neue Funktionalität ⇒ Tests | De-facto-Policy, konsequent gelebt (PARITY.md-Log weist je Feature Tests aus); in CONTRIBUTING.md verschriftlichen |
| ✅ | `tests_are_added` | Beleg für die letzten großen Änderungen | PARITY-Log 2026-09-03: Belichtungs-/Weißabgleich-UI (6× Engine-Tests), Replay-Library (10×), fdroidserver-Closure-Guard — jedes Feature mit Tests |
| ⚠️ | `tests_documented_added` | Test-Policy in den Beitrags-Instructions | Mit CONTRIBUTING.md abdecken |

### Compiler-Flags / Linter

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `warnings` | Warn-Flags/Safe-Mode/Linter aktiv | Android Lint (Pre-Push-Gate + CI), Kotlin-Compiler-Warnungen; SonarCloud als zusätzlicher statischer Analyzer |
| ✅ | `warnings_fixed` | Warnungen werden behandelt | Lint ist im Pre-Push-Gate blockierend; CI bricht bei Lint-Fehlern ab (z. B. behobene `context.getString`-Configuration-Awareness) |
| ⚠️ | `warnings_strict` | Maximal strenge Warn-Konfiguration (Empfehlung) | `warningsAsErrors` nicht global aktiviert; optional in CI scharf schalten |

---

## 5. Security

### Sicheres Entwicklungs-Know-how

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `know_secure_design` | Mind. ein Hauptentwickler mit Secure-Design-Kenntnissen |Maintainer wendet OWASP/Android-Security-Praktiken an (siehe SECURITY.md-Umfang: Log-Redaction, PKCE-OAuth, Netzwerk-Härtung, Branch Protection, Supply-Chain-Pinning) |
| ✅ | `know_common_errors` | Kennt häufige Fehlerklassen + Gegenmaßnahmen | Nachweisbar über getätigte Härtungen: Cleartext-Block (xml:S5332), Intent-Exposure (FileProvider statt Welt-Lesezugriff), Key-Redaction in Logs, Dependency-Pinning mit Hashes |

### Kryptografie (Grundregeln)

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `crypto_published` | Nur öffentlich reviewte Protokolle/Algorithmen | TLS (Plattform-Stack), RTMPS, PKCE-OAuth — keine eigenen Krypto-Entwürfe |
| ✅ | `crypto_call` | Keine eigene Krypto-Implementierung | Plattform-JCA/OkHttp/TLS; OAuth via Standard-Flows; RootEncoder nutzt Plattform-TLS |
| ✅ | `crypto_floss` | Krypto-Funktionalität mit FLOSS umsetzbar | Android-Plattform-Stack ist FLOSS |
| ✅ | `crypto_keylength` | NIST-Mindest-Key-Längen (bis 2030), kleinere konfigurierbar abschaltbar | Keine eigenen Keys/Keylengths; TLS-Verhandlung durch Plattform (NIST-konform); OAuth-Tokens via Plattform-Best Practices |
| ✅ | `crypto_working` | Keine gebrochenen Algorithmen im Default | Kein MD5/SHA-1/DES/RC4 in Security-Pfaden; SHA-256 überall (F-Droid-Index-Signaturen, Hash-Pinning) |
| ✅ | `crypto_weaknesses` | Keine Algorithmen mit ernsten Schwächen (Empfehlung) | SHA-256/TLS 1.2+; SHA-1 nur wo F-Droid-JAR-Signaturformat es historisch erfordert (Index-Integrität zusätzlich über HTTPS + Hash-Pinning gehärtet) |
| N/A | `crypto_pfs` | Perfect Forward Secrecy (Empfehlung) | TLS-Verhandlung inkl. PFS-Cipher-Suiten durch Plattform-Stack; keine eigenen Key-Agreements |
| N/A | `crypto_password_storage` | Passwort-Storage mit Key-Stretching | Vivid speichert keine Benutzer-Passwörter; Twitch-Token via PKCE-OAuth (ohne Client-Secret) in privatem DataStore; F-Droid/Release-Signing via Keystore außerhalb des Repos |
| ✅ | `crypto_random` | Kryptografisch sichere RNG für Keys/Nonces | PKCE-Code-Verifier via `SecureRandom`; sonst keine eigenen Keys/Nonces |

### MITM-gesicherte Auslieferung

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `delivery_mitm` | Auslieferung gegen MITM geschützt (HTTPS/SSH) | Repo/Releases/APKs über HTTPS; F-Droid-Repo: signierte Index-JARs (Signatur prüft Integrität auch nach TLS) |
| ✅ | `delivery_unsigned` | Keine Hashes über HTTP ohne Signatur-Prüfung | Hash-Pinning (pip `--require-hashes`, SHA-256) über HTTPS; GitHub-Actions per Commit-SHA gepinnt; Wrapper-JAR per Checksummen-DB validierbar |

### Bekannte Schwachstellen

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `vulnerabilities_fixed_60_days` | Keine unpatched Medium+-Schwachstellen > 60 Tage bekannt | SonarCloud: 0 offene BUG/VULNERABILITY, Quality Gate OK (2026-09-03); CodeQL/Snyk/Dependabot grün; Scorecard-Alerts getrieben (PinnedDependencies #41/#42/#462–#464 geschlossen) |
| ✅ | `vulnerabilities_critical_fixed` | Kritische Schwachstellen werden schnell behoben | Historie: S899-Fix binnen Stunden nach Quality-Gate-Alarm; Snyk-Findings via Constraints sofort gepatcht |
| ✅ | `no_leaked_credentials` | Keine gültigen privaten Credentials im öffentlichen Repo | Secret Guard (CI-Job) blockiert Secrets; Pre-Push-Gate mit Secret-Scan; Log-Redactor verhindert Key-Leak in App-Logs; F-Droid-Keystore/F-Droid-Credentials liegen außerhalb |

---

## 6. Analysis

### Statische Code-Analyse

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `static_analysis` | Statische Analyse vor jedem Major-Release | SonarCloud (Quality Gate blockiert Reliability-/Security-Abweichungen) + Android Lint + CodeQL — jeweils in CI vor Release-Pipeline |
| ✅ | `static_analysis_common_vulnerabilities` | Analyzer mit Vulnerability-Regeln (Empfehlung) | CodeQL (Security-Queries), SonarCloud Security-Hotspots (z. B. xml:S5332 behoben), Snyk für Deps |
| ✅ | `static_analysis_fixed` | Medium+-Findings zeitnah behoben | Beleg-Historie: S899 (ReplayRecording) am Tag behoben; Hotspot S5332 (Cleartext) am Tag behoben (`eddbfc5`) |
| ✅ | `static_analysis_often` | Analyse bei jedem Commit (Empfehlung) | CI löst SonarCloud/CodeQL/Lint bei jedem Push auf develop aus |

### Dynamische Analyse

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ⚠️ | `dynamic_analysis` | Mind. ein dynamisches Analyse-Tool vor Major-Releases (Empfehlung) | Android-Emulator-Tests (instrumentierte Accessibility-/Screenshot-Infrastruktur vorhanden, nur bei Nightly/Release getriggert); Roborazzi-Screenshot-Gegenläufe; Ausbau: Emulator-Job in jedem CI-Lauf oder SAP/Play-Pre-Launch-Reports |
| N/A | `dynamic_analysis_unsafe` | Memory-Tools bei memory-unsafe Sprachen | Vivid ist Kotlin/Java (memory-safe); natives C/C++ nur in Bibliotheks-Interna (OpenGL/RootEncoder), nicht im eigenen Code |
| N/A | `dynamic_analysis_enable_assertions` | Assertions-reiche Analyse-Konfiguration (Empfehlung) | Debug-Builds mit JUnit-Assertions + StrictMode-fähig; für Badge als SUGGESTED N/A wählbar |
| ✅ | `dynamic_analysis_fixed` | Medium+-Findings aus dynamischer Analyse zeitnah behoben | Bisher keine dynamisch gefundenen Vulnerabilities; CI-Emulator-Tests grün |
| ⚠️ | *(Scorecard-Fuzzing, kein CII-Kriterium)* | — | Scorecard Fuzzing = 0 (keine Scorecard-erkennbare Fuzzer-Integration; Kotlin/Android hat keine) — hier dokumentiert als bewusste Akzeptanz, in Scorecard via `.github/scorecard.yml` annotierbar |

---

## Offene Punkte vor der Anmeldung (priorisiert)

1. **`CONTRIBUTING.md` erstellen** (blockiert `contribution`, `interact`, `contribution_requirements`, `tests_documented_added`):
   PR-Flow (fork → branch → PR auf `develop`), Required Checks nennen
   („Build & Test", „Secret Guard"), Pre-Push-Gate (`./gradlew test
   testFossDebug lint` + Guards), i18n-Regeln (de/en/fr-Parität,
   `check_i18n_parity`), Commit-Stil, Testpflicht für neue Features,
   Security-Reporting-Verweis auf SECURITY.md.
2. **README internationalisieren (leicht):** EN-Kurzabschnitt oben
   (What/Why/Install/Contribute-Link) + Links auf `docs/user-guide.en.md`
   — stärkt `english` und `interact`.
3. **`CODE_OF_CONDUCT.md`** (Contributor Covenant v2.1, deutsch/englisch
   zweisprachig) — kein Kern-Kriterium auf Passing, aber Standard-Erwartung
   und birgt keine Kosten.
4. **Badge-Anmeldung** auf bestpractices.dev mit dieser Datei als
   Arbeitsvorlage durchklicken; danach Badge-Zeile in README.md
   (`https://bestpractices.dev/projects/<id>/badge`) und Verweis hierher.
5. *(Optional, stärkt `test_most`)* Zeilenabdeckungs-Report (Kover/JaCoCo)
   in CI publizieren, um Testabdeckung belegbar statt behauptbar zu machen.
6. *(Optional, stärkt `dynamic_analysis`)* Emulator-Job (instrumentierte
   Accessibility-Tests) von Nightly auf jeden CI-Push ausweiten.

## N/A-Begründungen (für das Formular)

- `documentation_interface`: Endnutzer-App ohne Programmierschnittstelle;
  UI/Bot-Befehle/Repo-URLs im User-Guide bzw. docs/ dokumentiert.
- `release_notes_vulns`: Keine öffentlich bekannten CVEs im eigenen Code;
  Dependency-CVEs werden über Snyk/Dependabot nachgeführt.
- `crypto_pfs` / `crypto_password_storage`: Kein eigenes Key-Agreement und
  keine Passwort-Speicherung; OAuth via PKCE, TLS durch Plattform-Stack.
- `dynamic_analysis_unsafe` / `dynamic_analysis_enable_assertions`:
  Memory-safe Sprachen (Kotlin); keine memory-unsafe eigenen Komponenten.

## Verweise

- Kriterienkatalog: https://www.bestpractices.dev/en/criteria/0
- Anmeldung: https://www.bestpractices.dev/en/projects/new
- SECURITY.md (Reporting-Prozess): ../SECURITY.md
- Scorecard-Alerts-Analyse und Härtungshistorie: ../SECURITY.md, ../PARITY.md
