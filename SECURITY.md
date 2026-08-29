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

## 🔒 Security Measures (Stand 2026-08-29)

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
| **SHA-Pinning** | ✅ | Alle GitHub-Actions auf immutable SHAs gepinnt |

### F-Droid / FOSS-Build

Der `foss`-Flavor (`com.vivid.foss`) ist vollständig frei von proprietärem Tracking:

- ✅ **Kein Sentry** — kein Error-Reporting, kein Telemetry
- ✅ **Keine Analytics** — keine Nutzungsstatistiken
- ✅ **Open-Source-only Dependencies** — alle Abhängigkeiten sind OSI-zertifiziert
- ✅ **Eigener Repo-Server** — `https://thoser666.github.io/Vivid/fdroid/repo` (letzte 5 Versionen)
