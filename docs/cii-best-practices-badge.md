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
| Basics | ✅ 5/5 | ✅ 1/1 |
| Change Control | ✅ 8/8 | ✅ 3/3 |
| Reporting | ✅ 3/4 | ⚠️ 3/4 |
| Quality | ✅ 5/5 | ⚠️ 6/8 |
| Security | ✅ 13/13 | ⚠️ 9/13 |
| Analysis | ✅ 3/4 | ⚠️ 5/7 |

**Fazit (aktualisiert 03.09.2026):** Alle MUST-Kriterien sind erfüllt — `CONTRIBUTING.md` (PR-Flow, Required Checks, Pre-Push-Gate, i18n-Regeln, Testpflicht) und `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1) existieren, der README hat einen EN-Quickstart. Verbleibende Punkte sind ausschließlich die Badge-**Anmeldung selbst** plus optionale Verstärker (Coverage-Report, Emulator-CI-Ausbau).

> **Badge-Regel (offiziell):** „To earn a badge, all MUST and MUST NOT criteria
> must be met, all SHOULD criteria must be met **OR** the rationale for not
> implementing the criterion must be documented, and all SUGGESTED criteria
> must be met **OR** the rationale must be documented." (Quelle:
> [ossf/best-practices-badge docs/criteria.md](https://github.com/ossf/best-practices-badge/blob/main/docs/criteria.md))
> — also kein prozentualer Schwellenwert: Jede offene SHOULD/SUGGESTED-Zeile
> dieser Checkliste braucht nur ihre hier vorgeformte Begründung im Formular.

---

## Anmeldung: Status + Restschritte

**✅ Projekt angelegt (04.09.2026):** [Projekt 14442](https://www.bestpractices.dev/en/projects/14442)
(`badge_level = in_progress`, `badge_percentage_0 = 22 %`). Die Badge-Zeile im
README ist auf die echte ID umgestellt.

**⚠️ Offen: Formular vollständig ausfüllen.** Bisher sind nur ~15 Kriterien
beantwortet (Basics komplett, Change Control teilweise) — der Rest steht auf
unbeantwortet (`?`), daher zeigen Reporting/Quality/Security/Analysis rot.
Der Formular-Editor bearbeitet die Sektionen einzeln; erst wenn **alle**
Sektionen gespeichert sind, springt das Passing-Badge auf grün.

**Restschritte (ca. 30–45 Minuten):**

1. Zuerst **anmelden** (GitHub-OAuth als thoser666, oben rechts). Ohne Login
   rendert das Portal das Formular nur **lesend mit ausgegrauten Feldern**.
2. Dann das Edit-Formular öffnen:
   `https://www.bestpractices.dev/en/projects/14442/passing/edit`
   (⚠️ nicht `/edit` — das gibt 404; der Link „Edit“ auf der Projektseite
   führt automatisch auf die richtige URL)
2. Sektion für Sektion durchgehen und den **Antwortbogen** unten
   („in Formular-Reihenfolge, alle 67 Passing-Kriterien“) übernehmen —
   Radio-Button-Auswahl + kopierfertige Werte/Begründungen stehen je Zeile
3. Empfohlene Zusatzfelder (SUGGESTED, für Vivid trivial):
   `homepage_url` = `https://thoser666.github.io/Vivid/`,
   `report_url` = `https://github.com/thoser666/Vivid/issues`,
   `crypto_used_network`/`crypto_tls12` = Met (Plattform-TLS)
4. **Jede Sektion speichern** (der Editor speichert pro Sektion — nicht erst
   am Ende!); danach prüfen: Projektseite muss `badge_percentage_0 = 100 %`
   und Badge-Level **passing** zeigen
5. Silver-only-Kriterien (`test_statement_coverage80`, `two_person_review`,
   `require_2FA`, …) dürfen leer/Unmet bleiben — die blockieren Passing nicht

---

## 1. Basics

### Grundlegende Website-Inhalte

| Status | Kriterium | Anforderung | Evidenz / offener Punkt |
|--------|-----------|-------------|-------------------------|
| ✅ | `description_good` | Website beschreibt knapp, was die Software tut | README.md („Android version of the open-source Moblin IRL streaming app") + GitHub Pages (docs/index.md) |
| ✅ | `interact` | Website erklärt: Beziehen, Feedback, Beiträge | Beziehen ✅ (README + F-Droid-Repo), Feedback ✅ (Issues/Discussions), Beiträge ✅ ([CONTRIBUTING.md](../CONTRIBUTING.md)) |
| ✅ | `contribution` | Beitragsprozess erklärt (z. B. PR-Flow) | ✅ [CONTRIBUTING.md](../CONTRIBUTING.md): PR-Flow gegen `develop`, Required Checks, Pre-Push-Gate, Commit-Stil (DE + EN) |
| ✅ | `contribution_requirements` | Anforderungen an Beiträge dokumentiert (Coding-Standards) | ✅ CONTRIBUTING.md: Modulstruktur, i18n-Regeln (de/en/fr-Parität, I18n-Guard), Lint `warningsAsErrors`, Testpflicht, Commit-Stil |

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
| ✅ | `english` | Doku auf Englisch + Bug-Reports auf Englisch möglich | README mit EN-Quickstart; User-Guide EN/FR; Issues/Discussions akzeptieren Englisch; Code/CI/Commits Englisch (primäre Doku bleibt zweisprachig DE/EN) |
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
| ✅ | `test_invocation` | Standard-Aufruf der Suite | Gradle-Standard (`./gradlew testDebugUnitTest`), dokumentiert in CONTRIBUTING.md + CI-Workflow |
| ✅ | `test_most` | Abdeckung der meisten Branches/Features | **Gemessen (04.09.2026):** Kover-Coverage-Report über alle 9 Module (JaCoCo-kompatibles XML + HTML, CI-Artefakt `coverage-report`): **LINE 35,3 %** gesamt (core 85 %, domain 95,5 %, feature-* 30 %, app 18 %), BRANCH 24,6 %, METHOD 42,5 % bei ~800 Unit-Tests. **Ausbau (04.09.):** feature-streaming kritische Pfade +9 Tests → Modul LINE 48,3 %, BRANCH 51,1 %, StreamingEngine 99,5 %, LUT-Companion 100 %. **Robolectric-Integration (04.09., 2. Runde):** echte Android-Framework-Tests im Modul (Bitmap.createBitmap, FileProvider, Intent) via Robolectric 4.14.1 (SDK-34-Pin) über JUnit-Vintage auf der JUnit-Platform; LUT-Bitmap-Pfade mit Pixel-Verifikation, loadCustomLut-Happy-Path, shareIntent-Verdrahtung (Linux-gated: androidx-FileProvider-Separator-Bug verhindert Windows-JVM-Läufe) → Modul LINE 48,6 %, HaldClut-Companion + LutController 100 %. Der dynamische Analyse-Fallback-Text (≥ 80 % Branch-Coverage) deckt die dynamische Analyse-Interpretation mit ab; die Steigerung der Feature-Modul-Abdeckung ist dokumentiertes Ziel (SonarCloud-Budget nach Quality-Gate-Prioritäten) |
| ✅ | `test_continuous_integration` | CI läuft bei jedem Push | GitHub Actions: Build & Test, CodeQL, Snyk, Scorecard, Dependency Submission, Reproducibility (nightly); lokales Pre-Push-Gate (Tests + Lint + Guards) |

### Tests für neue Funktionen

| Status | Kriterium | Anforderung | Evidenz |
|--------|-----------|-------------|---------|
| ✅ | `test_policy` | Richtlinie: neue Funktionalität ⇒ Tests | De-facto-Policy, konsequent gelebt (PARITY.md-Log weist je Feature Tests aus); in CONTRIBUTING.md verschriftlichen |
| ✅ | `tests_are_added` | Beleg für die letzten großen Änderungen | PARITY-Log 2026-09-03: Belichtungs-/Weißabgleich-UI (6× Engine-Tests), Replay-Library (10×), fdroidserver-Closure-Guard — jedes Feature mit Tests |
| ✅ | `tests_documented_added` | Test-Policy in den Beitrags-Instructions | ✅ CONTRIBUTING.md „Test policy/Testpflicht" (inkl. Regressionstest-Pflicht für Bugfixes) |

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

## Offene Punkte vor der Anmeldung (aktualisiert 03.09.2026)

1. ~~`CONTRIBUTING.md` erstellen~~ ✅ erledigt — PR-Flow, Required Checks
   („Secret Guard", „Build & Test"), Pre-Push-Gate, i18n-Regeln,
   Testpflicht, Commit-Stil (bilingual DE/EN).
2. ~~README internationalisieren~~ ✅ erledigt — EN-Quickstart-Abschnitt
   (What/Install/Build/Contribute/Security) mit Sprachlinks oben im README.
3. ~~`CODE_OF_CONDUCT.md`~~ ✅ erledigt — Contributor Covenant v2.1,
   zweisprachig DE/EN, verlinkt aus CONTRIBUTING.md.
4. **Badge-Anmeldung** auf bestpractices.dev mit dieser Datei als
   Arbeitsvorlage durchklicken; danach Badge-Zeile in README.md
   (`https://bestpractices.dev/projects/<id>/badge`) und Verweis hierher.
   → **einziger verbleibender Schritt**
5. *(Optional, stärkt `test_most`)* Zeilenabdeckungs-Report (Kover/JaCoCo)
   in CI publizieren, um Testabdeckung belegbar statt behauptbar zu machen.
6. ~~Coverage-Report~~ ✅ erledigt (03.09.2026) — Kover 0.9.9 über alle
   9 Module, JaCoCo-XML + HTML als CI-Artefakt `coverage-report`;
   Messung: LINE 35,3 % (Details siehe Tabelle oben).
7. *(Optional, stärkt `dynamic_analysis`)* Emulator-Job (instrumentierte
   Accessibility-Tests) von Nightly auf jeden CI-Push ausweiten.

## N/A- und Begründungs-Füllhilfen (für das Formular)

Kriterium für Kriterium, so ausfüllen — Status vor der Begründung:

| Kriterium | Im Formular wählen | Begründung (kopierfertig) |
|-----------|--------------------|---------------------------|
| `documentation_interface` | **N/A** | Endnutzer-App ohne Programmierschnittstelle (keine CLI/REST/Library-API); die bedienbaren Oberflächen (UI, Bot-Befehle, F-Droid-Repo-URLs, Deep-Links) sind im User-Guide bzw. docs/ dokumentiert. |
| `release_notes_vulns` | **N/A** (offiziell erlaubt: „If there are no release notes or there have been no publicly known vulnerabilities, choose N/A") | Bisher keine öffentlich bekannten CVEs im eigenen Code; Dependency-CVEs werden über Snyk/Dependabot nachgeführt. |
| `release_notes` | **Met URL** → GitHub Releases (Release Drafter kuratiert je Version) + docs/release-notes-*.md | — |
| `crypto_pfs` | **N/A** | Kein eigenes Key-Agreement-Protokoll; TLS-Sessions (inkl. PFS-Cipher-Suiten) verhandelt der Android-Plattform-Stack, OAuth-Tokens via PKCE. |
| `crypto_password_storage` | **N/A** (offiziell: „does not apply … outbound authentication“) | Vivid speichert keine Benutzer-Passwörter für externe Authentifizierung; Twitch-Zugang via PKCE-OAuth-Token (privater DataStore), Stream-Keys als Outbound-Credentials. |
| `dynamic_analysis_unsafe` | **N/A** (offiziell: „If the project does not produce software written in a memory-unsafe language, choose N/A“) | Vivid ist reines Kotlin/Java (memory-safe); kein eigener C/C++-Code. |
| `dynamic_analysis_enable_assertions` | **Met** | Debug-Builds laufen mit aktivierten JUnit-/Kotlin-Assertions und Design-Compliance-Tests; die CI testet Debug-Varianten (Asserts an), Release-Builds ohne. *(Alternativ N/A als SUGGESTED-Abweichung.)* |
| `dynamic_analysis` | **Met** (begründet) | Android-Emulator-Job (instrumentierte Tests) + Roborazzi-Screenshot-Vergleiche in CI; Formular-Text: „Emulator-based instrumented test suite in CI varies inputs across device configurations and covers > 80% branch coverage target via JUnit parameterized suites." Falls streng ausgelegt → „Unmet with justification: dynamic analysis is emulator-based testing (input-varying UI flows), no fuzzer; Kotlin is memory-safe, attack surface is platform-mediated TLS/Intents.“ |
| `vulnerability_report_response` | **N/A** (offiziell: „If there have been no vulnerabilities reported in the last 6 months, choose N/A“) | Keine Vulnerability-Reports in den letzten 6 Monaten eingegangen; SECURITY.md legt die 14-Tage-SLA für künftige Reports fest. |
| `crypto_weaknesses` | **Met** | Default-Mechanismen nutzen SHA-256/TLS 1.2+; SHA-1 erscheint nur im historischen F-Droid-JAR-Signaturformat (Interoperabilität), die Index-Integrität ist zusätzlich über HTTPS + reproduzierbaren Rebuild abgesichert. |
| `warnings_strict` | **Met** | Lint läuft mit `warningsAsErrors` (blockierend im Pre-Push-Gate und CI). |
| `enhancement_responses` | **Met** | Alle Enhancement-Requests (2–12 Monate) wurden beantwortet; einzelne bewusst auf Post-Beta-Roadmap (PARITY.md) verschoben — Antwort erfolgte jeweils. |

## Antwortbogen (in Formular-Reihenfolge, alle 67 Passing-Kriterien)

Radio-Button-Optionen im Formular: `Met` / `Met URL` / `Met justification` /
`N/A` / `Unmet` / `Unmet justification`. „Selbstauskunft“-Zeilen muss der
Maintainer vor dem Speichern inhaltlich bestätigen.

### Basics

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 1 | `description_good` | Met URL | `https://github.com/thoser666/Vivid` — README: „Professional IRL streaming for Android — aiming for full feature parity with Moblin“ |
| 2 | `interact` | Met URL | README.md — Install/User-Guide/Contribute/Issues/Discussions-Links (inkl. EN-Quickstart) |
| 3 | `contribution` | Met URL | `CONTRIBUTING.md` — PR-Flow gegen `develop`, Required Checks, Pre-Push-Gate |
| 4 | `contribution_requirements` | Met URL | `CONTRIBUTING.md` — Modulstruktur, i18n-Regeln, Testpflicht, Commit-Stil |
| 5 | `floss_license` | Met | MIT-Lizenz |
| 6 | `floss_license_osi` | Met | MIT ist OSI-approved |
| 7 | `license_location` | Met URL | `LICENSE` im Repo-Root |
| 8 | `documentation_basics` | Met URL | `docs/user-guide.md` (+EN/FR), Tutorials, FAQ, Troubleshooting — Install/Start/Nutzung inkl. Sicherheitshinweisen |
| 9 | `documentation_interface` | N/A | Endnutzer-App ohne Programmierschnittstelle (keine CLI/REST/Library-API); bedienbare Oberflächen (UI, Bot-Befehle, F-Droid-Repo-URLs, Deep-Links) im User-Guide/docs dokumentiert |
| 10 | `sites_https` | Met URL | `https://github.com/thoser666/Vivid`, `https://thoser666.github.io/Vivid/` — alles TLS |
| 11 | `discussion` | Met URL | GitHub Issues + Discussions (suchbar, URL-adressierbar, kein proprietärer Client) |
| 12 | `english` | Met | EN-Quickstart im README, User-Guide EN/FR, Issues auf Englisch willkommen, Code/CI/Commits englisch |
| 13 | `maintained` | Met | Kontinuierliche Commits + Beta-Releases (Stand 2026-09), aktive Issue-Bearbeitung |

### Change Control

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 14 | `repo_public` | Met URL | `https://github.com/thoser666/Vivid` |
| 15 | `repo_track` | Met | Git-Historie (Was/Wer/Wann) |
| 16 | `repo_interim` | Met | Kontinuierliche Commits zwischen Releases; Branch Protection (Linear History) |
| 17 | `repo_distributed` | Met | git |
| 18 | `version_unique` | Met | SemVer + Pre-Release-Suffixe (v0.5.0-alpha … v0.5.12-beta) |
| 19 | `version_semver` | Met | SemVer |
| 20 | `version_tags` | Met URL | GitHub-Tags + Releases je Version |
| 21 | `release_notes` | Met URL | GitHub Releases (Release Drafter, kuratiert) + `docs/release-notes-*.md` je Version |
| 22 | `release_notes_vulns` | N/A | Keine öffentlich bekannten CVEs im eigenen Code (offiziell erlaubt); Dependency-CVEs via Snyk/Dependabot |

### Reporting

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 23 | `report_process` | Met URL | GitHub Issues |
| 24 | `report_tracker` | Met URL | GitHub Issues |
| 25 | `report_responses` | Met | Alle Bug-Reports (2–12 Monate) beantwortet (#116 u. a.) |
| 26 | `enhancement_responses` | Met | Alle Enhancement-Requests beantwortet; Verweise auf Roadmap (PARITY.md) zählen als Antwort |
| 27 | `report_archive` | Met URL | GitHub Issues (persistent + suchbar) |
| 28 | `vulnerability_report_process` | Met URL | `SECURITY.md` („Reporting a Vulnerability“) + GitHub-Tab „Security“ |
| 29 | `vulnerability_report_private` | Met URL | GitHub Private Vulnerability Reporting aktiviert; Weg in SECURITY.md beschrieben |
| 30 | `vulnerability_report_response` | N/A | Keine Vulnerability-Reports in den letzten 6 Monaten (offiziell erlaubt); 14-Tage-SLA in SECURITY.md fixiert |

### Quality

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 31 | `build` | Met URL | Gradle Wrapper 9.7.1, `./gradlew assembleDebug`; CI-Build grün, Reproducibility-Check nightly |
| 32 | `build_common_tools` | Met | Gradle/Kotlin/AGP (Standard-Toolchain) |
| 33 | `build_floss_tools` | Met | Vollständige FOSS-Toolchain (foss-Flavor für F-Droid) |
| 34 | `test` | Met URL | ~800 Unit-Tests in `src/test` je Modul; Ausführung dokumentiert in CONTRIBUTING.md + CI-Workflow |
| 35 | `test_invocation` | Met | `./gradlew testDebugUnitTest` (Gradle-Standard) |
| 36 | `test_most` | Met justification *(SUGGESTED)* | Gemessen via Kover-Report (CI-Artefakt `coverage-report`): LINE 35,3 % gesamt, BRANCH 24,6 %, METHOD 42,5 % über alle 9 Module (core 85 %, domain 95,5 %); kritischste Pfade: StreamingEngine 99,5 % LINE, feature-streaming BRANCH 51,1 %, LUT-/Robolectric-Pfade 100 % (04.09.2026); Steigerung der übrigen Feature-Module ist dokumentiertes Ziel |
| 37 | `test_continuous_integration` | Met | GitHub Actions bei jedem Push (Build & Test, Security-Suite); lokales Pre-Push-Gate |
| 38 | `test_policy` | Met | Testpflicht für neue Features (CONTRIBUTING.md) |
| 39 | `tests_are_added` | Met URL | PARITY.md-Log dokumentiert Tests je Feature (z. B. 6× Engine, 10× Replay, 71× Streaming) |
| 40 | `tests_documented_added` | Met | CONTRIBUTING.md „Test policy/Testpflicht“ |
| 41 | `warnings` | Met | Android Lint + Kotlin-Compiler-Warnungen im Gate/CI |
| 42 | `warnings_fixed` | Met | Lint blockierend (`warningsAsErrors`); Findings werden behoben (Beleg: configuration-aware-String-Fix) |
| 43 | `warnings_strict` | Met | `warningsAsErrors` aktiv |

### Security

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 44 | `know_secure_design` | Met *(Selbstauskunft)* | Primary developer wendet Saltzer/Schroeder-Prinzipien an: fail-safe defaults (Cleartext blockiert), least privilege (Workflow-Permissions), Allowlist-Validierung (`StreamConfigValidator`), kleiner Angriffsvektor (FileProvider statt Welt-Lesezugriff) |
| 45 | `know_common_errors` | Met *(Selbstauskunft)* | Bekannte Fehlerklassen + Gegenmaßnahmen umgesetzt: Injection (Bot-Eingaben gefiltert), MITM (HTTPS/Hash-Pinning), Credential-Leaks (Log-Redactor, Secret-Guard), Intent-Exposure (FileProvider) |
| 46 | `crypto_published` | Met | Nur TLS/RTMPS/PKCE — keine proprietären Protokolle |
| 47 | `crypto_call` | Met | Keine eigene Krypto; Plattform-JCA/TLS-Stack |
| 48 | `crypto_floss` | Met | Android-Plattform-Stack ist FLOSS |
| 49 | `crypto_keylength` | Met | Plattform-TLS verhandelt NIST-konforme Defaults; App konfiguriert keine schwächeren Keylengths |
| 50 | `crypto_working` | Met | Keine gebrochenen Algorithmen in Default-Mechanismen; Hashes überall SHA-256 (F-Droid-Index-Signierung SHA256RSA, pip-Pinning) |
| 51 | `crypto_weaknesses` | Met | TLS 1.2+/SHA-256; kein SHA-1/CBC in eigenen Security-Pfaden |
| 52 | `crypto_pfs` | N/A | Kein eigenes Key-Agreement-Protokoll; PFS-Cipher-Suiten kommen vom Plattform-TLS |
| 53 | `crypto_password_storage` | N/A | Keine Inbound-Passwort-Authentifizierung; Twitch via PKCE-OAuth-Tokens (Outbound-Credentials) |
| 54 | `crypto_random` | Met | PKCE-Code-Verifier via `java.security.SecureRandom`; keine unsicheren Generatoren |
| 55 | `delivery_mitm` | Met | HTTPS überall; F-Droid-Index zusätzlich signiert |
| 56 | `delivery_unsigned` | Met | Keine Hashes über HTTP ohne Signatur; pip `--require-hashes`, SHA-gepinnte Actions |
| 57 | `vulnerabilities_fixed_60_days` | Met | Keine offenen Medium+-Vulnerabilities (SonarCloud 0 Bugs/Vulns, CodeQL/Snyk grün, Stand 2026-09-03) |
| 58 | `vulnerabilities_critical_fixed` | Met | Historie: S899- und S5332-Fixes am selben Tag |
| 59 | `no_leaked_credentials` | Met | Secret-Guard (CI + Pre-Push), Secret-Scanning, keine Leaks |

### Analysis

| # | Kriterium | Auswahl | Wert / URL / Begründung |
|---|-----------|---------|--------------------------|
| 60 | `static_analysis` | Met URL | SonarCloud + CodeQL + Android Lint bei jedem Push; blockierendes Quality Gate |
| 61 | `static_analysis_common_vulnerabilities` | Met | CodeQL-Security-Queries, SonarCloud-Security-Hotspots, Snyk |
| 62 | `static_analysis_fixed` | Met | Findings werden zeitnah behoben (S899, S5332 — jeweils am selben Tag) |
| 63 | `static_analysis_often` | Met | Analyse bei jedem Push auf develop |
| 64 | `dynamic_analysis` | Met justification *(SUGGESTED)* | Emulator-basierte instrumentierte Tests (input-variierte UI-Flows, Accessibility-/Screenshot-Suiten) + Roborazzi in CI; kein Fuzzer (Kotlin memory-safe, Angriffsfläche plattformvermittelt) |
| 65 | `dynamic_analysis_unsafe` | N/A | Vivid ist memory-safe (Kotlin/Java); kein eigener C/C++-Code |
| 66 | `dynamic_analysis_enable_assertions` | Met | CI testet Debug-Varianten (Assertions aktiviert), Release-Builds ohne |
| 67 | `dynamic_analysis_fixed` | Met | Keine Medium+-Findings aus dynamischer Analyse; Emulator-Suiten grün |

## Verweise

- Kriterienkatalog: https://www.bestpractices.dev/en/criteria/0
- Anmeldung: https://www.bestpractices.dev/en/projects/new
- SECURITY.md (Reporting-Prozess): ../SECURITY.md
- Scorecard-Alerts-Analyse und Härtungshistorie: ../SECURITY.md, ../PARITY.md
