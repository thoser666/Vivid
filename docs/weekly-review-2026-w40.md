# Wochenrückblick KW 40 (24.09.–30.09.2026)

> Kompakte Übersicht für das Team-Wiki — Quellen: GitHub-PR/Release-API, [PARITY.md](../PARITY.md), CI-Runs. Alle Hashes auf develop.

## Auf einen Blick

- **23 gemergte PRs** — darunter 2 Community-Beiträge (smka), 8 Dependabot-Bumps, 13 Automatik-/Doku-PRs; **0 offene PRs** zum Wochenende
- **Releases:** v0.5.18-beta (24.09.), v0.5.19-beta (25.09.), Nightlies 26.–29.09.
- **7 Incidents** bearbeitet, **8 Verträge** neu oder gehärtet, CI standlos 6/6 grün

## Merges

### Community (smka, Fork)

| PR | Beitrag | Würdigung |
|----|---------|-----------|
| #213 | In-App-Sprachauswahl + russische Lokalisierung (28.09.) | [CONTRIBUTORS.md](../CONTRIBUTORS.md) `umgesetzt` + Dank-Kommentar am PR |
| #230 | Kamera-Vorschau wiederhergestellt, seitenverhältnistreu (29.09., Maintainer-Pass 2d524fa2) | dito |

### Infrastruktur & Dependencies (30.09., Stapel)

| PR | Inhalt | Anmerkung |
|----|--------|-----------|
| #235 | Gradle-Wrapper 9.7.1 → 9.8.0 | volles Gate kompiliert/testet mit neuem Wrapper |
| #236–#238 | codeql-action 4.38.2 (init/analyze/upload-sarif) | **Familie gemeinsam** gemergt — alle 4 Pin-Stellen auf derselben SHA (Same-Release-Verbot) |
| #231 | androidx: coreKtx 1.19.1, navigation 2.10.2 | Patch-Bumps |
| #232 | testing: mockito-core 5.24.0, mockito-kotlin 6.4.0 | Minor |
| #233 | roborazzi 1.75.0 | Minor |
| #234 | sentry-gradle 6.23.0 | nach `@dependabot rebase` (CONFLICTING gegen #232/#233) |
| #239 | ruby/setup-ruby 1.327.0 (28.09.) | Actions-Bump |

### Doku & Automatik

#214 (Issue-Template: Blank-Issues deaktiviert, Discussions/Security-Links) · CHANGELOG-Mirror (#201/#205/#208/#211/#212/#229/#241/#243) · F-Droid-Repo-Updates (#202/#209/#240)

## Incidents & Lehren

1. **#230-Merge: PARITY-Orphan-Guard rot** — Squash-Merges machen Contributor-Branch-Hashes zu Orphans; 4 Zeilen konsolidiert (`c7b8bf33`).
2. **Fork-PR-Workflows rot** (Release Drafter „not accessible", Snyk leerer Token) — Fork-Guards in beiden Workflows mit Pflicht-Klammerung (`97c4f5bf`).
3. **OBS CLEARTEXT (#226) abgeschlossen** — OkHttp→Ktor-CIO-WebSockets (`bf5b7333`), JUnit/Coroutines-Hang-Forensik → koroutinenfreie E2E-Tests.
4. **CONTRIBUTORS.md hinter Merges zurück** (#213 `offen`, #230 fehlte) — fiel erst durch Nutzer-Rückfrage auf → Pflege (`625da60`) + **Reminder-Automatisierung** (`e8fe709`) + öffentlicher Dank am PR (`9360627`). Smoke-Lehre: `gh --jq` druckt Array-Literale als EINE JSON-Zeile (Regression C10); OAuth-Tokens können Issues nicht löschen (Test-Artefakte #245/#246 dokumentiert geschlossen).
5. **GitHub-SARIF-Multi-Run-Enforcement** (Ende-September-Stichtag) — Snyk schreibt 11 Projekt-Runs in eine Datei, Upload abgelehnt (Run 36667123590) → Kategorie-Fixup je Run (`dea457b3`), alle Befunde bleiben in Code Scanning sichtbar.
6. **PyPI-Drift 2× am selben Tag** — platformdirs 4.12.2 (`1eb60d6`) und charset-normalizer 3.5.2 (`65a365c`) trafen Regenerierung und CI-Lauf in einer 16-Minuten-Lücke → roter CI auf intaktem Commit → **Semantik-Trennung** (`d27f289a`): Drift advisory im Push-Kontext, `PIP_DRIFT_STRICT=1` fail-closed nur für die Bot-Nach-Verifikation; Integrität (Formalprüfung + Deploy-Hashes) bleibt strikt.
7. **K1-Vertragsbruch vorprogrammiert** — CodeQL-Familien-Bump verletzte die wörtliche v4.38.1-SHA-Erwartung → K1 dynamisiert: Same-Release-Check über alle Workflows (`c38fb13`), künftige Familien-Bumps brechen den Vertrag nicht mehr.

## Verträge (neu / geändert)

| Vertrag | Ort | Stand |
|---------|-----|-------|
| CONTRIBUTORS-Guard (Struktur: 1 Tabelle, `#Ref`, offen/umgesetzt) | `check_contributors.sh` (neu 27.09., `f390acb2`) | Pre-Push + CI |
| Contributors-Reminder (State-Fenster + pending + Credit-Marker + BACKFILL_SINCE) | `contributors_reminder.sh` | 30 Verträge offline |
| Snyk-SARIF-Kategorie-Fixup (je Run `automationDetails.id`, vor dem Upload) | `test_snyk_workflow.sh` | Vertragsprüfung inkl. Reihenfolge |
| K1 Same-Release über alle codeql-action-Pins (dynamisch) | `test_codeql_kotlin.sh` | K2–K6 unverändert |
| Drift-Semantik zweistufig (advisory push / STRICT bot) | `test_pip_drift_semantics.sh` (7 Verträge) | Sandbox-Stub, netzunabhängig |
| Fork-Guard-Klammerung (Push-Events dürfen nicht mitskippen) | `test_workflow_security.sh` | release-drafter + snyk |
| i18n-Guard auf 4 Sprachen + stream_url_hint-Inhalt | `check_i18n.sh` | mit #213 erweitert |
| VIVID-37 EADDRINUSE-Range 5000..5172 / Attribution ab 5182 | `CrashAdvisoryRegistry` | `74a5e217` (28.09.) |

## Ausblick / offene Punkte

- **CodeQL-Bundle-Blockade:** Bundle 2.27.0 lehnt Kotlin 2.4.20 weiter ab — Wächter (`check_codeql_blockade.sh`) beobachtet, 4.38.2 bringt noch kein entblockendes Bundle.
- **Drift-Bot** fängt Closure-Drift wöchentlich (`automation-pypi-drift`, Di 06:30 UTC) und verifiziert fail-closed; Push-CI ist dadurch racy-resistent.
- **Reminder-Regelkreis** (Ankündigung → pflegen → Dank-Edit) ist für künftige Fork-PRs aktiv; Backfill für #213/#230 live abgeschlossen.
- Nächster Beta-Meilenstein: Roadmap-Reservierung v0.6.0-beta (Streaming-Erweiterungs-Bucket, Guard aktiv).
