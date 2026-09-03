# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.5.x   | :white_check_mark: |
| 0.4.x   | :white_check_mark: |
| < 0.4   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in Vivid, please report it responsibly.

**Do not open a public issue.** Instead, please email [vivid.security@proton.me](mailto:vivid.security@proton.me) with:

- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### What to expect

- **Acknowledgment** within 48 hours
- **Initial assessment** within 1 week
- **Fix or mitigation** for confirmed vulnerabilities will be prioritized
- You will be credited in the release notes (unless you prefer to remain anonymous)

### Scope

Vivid is an open-source IRL streaming app. Security issues particularly relevant to:

- **Stream keys and OAuth tokens** — must never be logged or exposed
- **Network communications** — RTMPS/TLS enforcement
- **Chat bot credentials** — bot OAuth tokens, LLM API keys
- **Remote control** — LAN-based web remote (no external exposure intended)
- **Location data** — GPS coordinates used for the text/info widget
- **Error reporting** — Sentry telemetry and opt-out behavior
- **Log data** — In-App-Log redaction and retention

### Out of scope

- Social engineering attacks
- Issues requiring physical access to the user's device
- Vulnerabilities in third-party dependencies (report these upstream)

---

## 🔒 Security Measures (Stand 2026-09-02)

### Sentry Error Reporting — Härtung

| Maßnahme | Status | Detail |
|----------|--------|--------|
| **Manual Init** | ✅ | Auto-Init im Manifest deaktiviert; `SentryAndroid.init()` in `VividApplication` |
| **sendDefaultPii=false** | ✅ | Keine IP-Adresse, kein Gerätename wird erhoben |
| **beforeSend Opt-out** | ✅ | `applySentryOptOut()` verwirft alle Events wenn Nutzer das Toggle in Settings ausschaltet; live gelesen pro Event |
| **FOSS-Build deaktiviert Sentry** | ✅ | `BuildConfig.FOSS_BUILD=true` → kein Sentry-Init, kein Tracking, kein Telemetry (F-Droid-Konformität) |
| **Screenshot-Attach deaktiviert** | ✅ | `attachScreenshot=false` — keine Bildschirmfotos in Crash-Reports |
| **Mapping-Check in CI** | ✅ | `check_sentry_optout_mapping.sh` beweist per R8-Mapping, dass die Opt-out-Logik im Release-Build enthalten ist (Pre-Push-Gate + Fastlane-CI) |

### Log-Redactor (In-App-Logs)

Der `LogRedactor` schwärzt sensible Werte **bevor** sie in den `LogBuffer` gelangen. Betroffene Muster:

| Muster | Beispiel | Ergebnis |
|--------|----------|----------|
| `key=value` | `stream_key=abc123` | `stream_key=***` |
| `password=…` | `password=hunter2` | `password=***` |
| `token=…` | `token=xyz789` | `token=***` |
| `Bearer <token>` | `Bearer eyJhbG...` | `Bearer ***` |
| `user:pass@host` | `rtmp://user:secret@host` | `rtmp://***@host` |
| Lange Hex-Werte (≥32) | `deadbeef…` (32+ Zeichen) | `***` |

**Anwendung:** Jeder `Timber.d()`/`Timber.e()`-Aufruf wird automatisch durch `LogBufferTree` vor dem Speichern geschwärzt. Der `/logs`-Endpoint der Web-Remote-Control liefert ausschließlich geschwärzte Einträge.

### CI/CD Security Scanning

| Tool | Status | Scope |
|------|--------|-------|
| **CodeQL** | ✅ aktiv | Kotlin/Java-Codebasis, `security-extended` + `security-and-quality`, wöchentlich + pro Push |
| **Dependabot** | ✅ aktiv | Gradle-Dependencies + GitHub Actions, gruppiert nach Kategorie |
| **DeepSource** | ✅ aktiv | Statische Kotlin-Analyse (advisory, Major/Critical blockierend) |
| **Secret-Guard** | ✅ im Pre-Push | Prüft auf ungeschützte Keystores und Klartext-Secrets |
| **SHA-Pinning** | ✅ | Alle GitHub-Actions auf immutable SHAs gepinnt (Snyk-Setup ausgenommen: offizielles `snyk/actions/setup@v1.0.0`, da Snyk die Legacy-Gradle-Images abgekündigt hat) |

### Snyk-CLI-Migration

Der Workflow `.github/workflows/security-snyk.yml` verwendet seit September 2026 nicht mehr die abgekündigte `snyk/actions/gradle-jdk17`-Docker-Action. Stattdessen werden JDK 17, `snyk/actions/setup@v1.0.0` und die Snyk-CLI direkt verwendet. Die CLI erhält gültige Verzeichnisnamen (`build,.gradle`) statt eines nicht unterstützten Glob-Musters. Test und Monitor haben jeweils ein 20-Minuten-Timeout; SARIF wird nur hochgeladen, wenn die CLI tatsächlich eine Datei erzeugt. Der Offline-Guard `scripts/test_snyk_workflow.sh` prüft diese Vorgaben.

### Workflow-Härtung (Code-Scanning-Alerts)

Die Code-Scanning-/Scorecard-Fundstellen zu GitHub-Workflows sind seit September 2026 im Quelltext behoben:

- **Least-Privilege-Permissions:** Workflows mit Schreibrechten definieren `permissions: {}` auf Top-Level (alles verweigert) und vergeben die benötigten Rechte (`contents: write`, `security-events: write`, `pull-requests: write`) nur in dem Job, der sie braucht.
- **Keine PR-Titel-Interpolation:** Der Dependabot-Auto-Merge-Workflow übergibt den PR-Titel als Step-Environment (`PR_TITLE: ${{ github.event.pull_request.title }}`) und verarbeitet ihn ausschließlich als quoted Shell-Variable — kein direktes `${{ }}`-Interpolieren in Shell-Strings (Script-Injection-Risiko).
- **Regressions-Schutz:** `scripts/test_workflow_security.sh` (Teil des Pre-Push-Gates) prüft beide Vorgaben offline und verhindert das Wiedereinchecken unsicherer Muster.

**Scorecard-Alert-Abgleich (03.09.2026):** Nach dem Hardenings-Push wurden die 9 TokenPermissions-Alerts und der kritische PR-Titel-Injection-Alert durch die frische Scorecard-Analyse automatisch geschlossen. Zusätzlich behoben: `security-scorecard.yml` triggerte auf `master` statt auf dem Default-Branch `develop` (Alerts konnten nie refreshen) und `snyk/actions/setup` ist jetzt SHA-gepinnt (`9adf32b`, verifiziert). Die drei Kotlin-Compiler-Fehlalarme (`tmp0_other_with_cast`, `$stable`-Shadowing) wurden als False Positive dismissiert — dokumentiert im Alert-Kommentar.

Verbleibende Scorecard-Hinweise (Repository-Einstellungen bzw. bewusst versionierte Artefakte, kein Anwendungscode):

- **BinaryArtifacts (#34–#40):** F-Droid-Index-JARs (`docs/fdroid/{repo,archive}`) und der Gradle-Wrapper sind erforderliche, absichtlich versionierte Artefakte.
- **BranchProtection (#23): ✅ behoben (03.09.2026)** — Branch-Protection auf `develop` erweitert: Required Reviews (1, `dismiss_stale_reviews`, `require_last_push_approval`), Strict Required Checks (`Build & Test`, `Secret Guard`), Required Linear History, Required Conversation Resolution, Force-Push/Deletion verboten. Admins behalten den Bypass (Solo-Maintainer; sonst Deadlock, da PR-Autoren nicht sich selbst approven können).
- **CodeReview (#44): 🚧 teilweise** — Required Reviews sind jetzt aktiv (siehe BranchProtection), aber Scorecard verlangt zusätzlich Branch-Protection auf der Default-Branch plus all-*-/OOO-Reviewer-Konfiguration; der Rest ist Solo-Repo-bedingt offen.
- **Fuzzing (#43), CIIBestPractices (#45):** Prozess-/Einrichtungsthemen, keine Quellcode-Änderungen.
- **Bot-Pushes → PRs:** Direkte `git push` auf `develop` durch Workflows sind durch die Protection unzulässig; `automation-changelog.yml`, `release-pipeline.yml` (Changelog-Schritt) und `deploy-fdroid.yml` erstellen jetzt Branch + PR, gemergt wird manuell (GITHUB_TOKEN-PRs können sich nicht selbst mergen; Solo-Betreuer mergt mit Admin-Bypass).
- **PinnedDependencies (#41–#42, #464): ✅ behoben (03.09.2026)** — `pip install` in deploy-pages/deploy-fdroid ist jetzt SHA-256-verifiziert (`--require-hashes`): markdown `3.10.3` (Wheel-Hash verifiziert) und fdroidserver `2.4.5` mit **kompletter transitiver Closure** in `.github/requirements/fdroidserver-requirements.txt` (67 Pakete, generiert für ubuntu-latest/CPython 3.12; Versionsauswahl respektiert die Constraints der Eltern-Pakete, z. B. `ruamel.yaml<0.17.22`). Der Generator `scripts/gen_fdroid_requirements.py` bewertet Environment-Marker (Linux/CPython 3.12 — schließt Windows/macOS- und 3.6-Backport-Pakete aus), ergänzt manuell dokumentierte Kanten für alte sdists ohne PyPI-Metadaten (`clint → args`) und führt eine Closure-Selbstprüfung aus (fail-loud bei unvollständiger Abdeckung). Der Guard `scripts/test_pip_pinning.sh` (Teil des Pre-Push-Gates) erzwingt die Hash-Pflicht, die Verwendung der Closure-Datei, deren formale Gültigkeit und **Reproduzierbarkeit**: `gen_fdroid_requirements.py --check` vergleicht die committete Datei byte-identisch gegen eine frisch generierte Closure und blockiert das Pre-Push-Gate bei Drift (z. B. wenn sich PyPI-Metadaten ändern oder `MANUAL_DEPS` erweitert wird → neu generieren und committen). Die Closure ist in CI live validiert (Run 33745855828: `pip install --require-hashes` inkl. `clint → args` → `Successfully installed … fdroidserver-2.4.5 …`).

### Transitive Dependency-Härtung

`settings.gradle.kts` erzwingt für bekannte transitive Snyk-Fundstellen sichere Patchstände: Netty `4.1.137.Final`, Commons Lang `3.18.0` und Bouncy Castle `1.85`. Die Constraints gelten für alle Konfigurationen, einschließlich Android-Test-/Tooling-Abhängigkeiten. `scripts/test_dependency_security_constraints.sh` schützt die zentrale Konfiguration gegen versehentliches Entfernen.

### Netzwerk-Sicherheit (Cleartext blockiert)

`app/src/main/res/xml/network_security_config.xml` setzt `cleartextTrafficPermitted="false"` für den Base-Config — Cleartext-HTTP ist damit auf **allen** Android-Versionen explizit blockiert (auf API < 28 war er implizit erlaubt; das Manifest-Attribut `android:networkSecurityConfig` verweist darauf). Der RTMP/RTMPS-Stream läuft über RootEncoders eigene Sockets und ist davon nicht betroffen; alle HTTP-APIs (Twitch, Sentry, GitHub, Emote-CDNs) nutzen ausschließlich HTTPS. Hintergrund: SonarCloud-Security-Hotspot `xml:S5332` („usesCleartextTraffic implicitly enabled for older Android versions").

### F-Droid / FOSS-Build

Der `foss`-Flavor (`com.vivid.foss`) ist vollständig frei von proprietärem Tracking:

- ✅ **Kein Sentry** — kein Error-Reporting, kein Telemetry
- ✅ **Keine Analytics** — keine Nutzungsstatistiken
- ✅ **Open-Source-only Dependencies** — alle Abhängigkeiten sind OSI-zertifiziert
- ✅ **Eigener Repo-Server** — `https://thoser666.github.io/Vivid/fdroid/repo` (letzte 5 Versionen) plus Archiv `https://thoser666.github.io/Vivid/fdroid/archive` (alle älteren Versionen)
