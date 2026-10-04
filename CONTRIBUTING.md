# Contributing to Vivid / Mitwirken an Vivid

> **English summary below — [zum deutschen Teil](#-beiträge-auf-deutsch)**

Thanks for your interest in contributing! 🎉

**TL;DR (EN):**

- **Bugs & ideas:** open a [GitHub Issue](../../issues) (English or German is fine).
- **Security vulnerabilities:** do **not** open a public issue — follow [SECURITY.md](SECURITY.md) (private reporting).
- **Code changes:** fork → feature branch → pull request against **`develop`**. CI must be green; add tests for new functionality; user-facing strings in de/en/fr.
- **Run before pushing:** `bash scripts/pre-push.sh` (same checks as CI).

---

## 🇬🇧 Contributing (English)

### Ways to contribute

| Type | Where | Notes |
|------|-------|-------|
| Bug reports | [GitHub Issues](../../issues) | Use the issue search first; include device/Android version and repro steps |
| Feature ideas | [GitHub Issues](../../issues) or [Discussions](../../discussions) | Check [PARITY.md](PARITY.md) — Moblin-parity features are tracked there |
| Questions | [Discussions](../../discussions) | Also see [docs/faq/](docs/faq/common-issues.md) and [docs/troubleshooting/](docs/troubleshooting/) |
| Documentation | Pull request | Docs live in `docs/` (German primary, English and French guides for user docs) |
| Code | Pull request | See the workflow below |

### Pull request workflow

1. **Fork & branch** — create a feature branch from `develop`
   (`git checkout -b feat/my-feature origin/develop`).
2. **Implement** — follow the existing conventions:
   - **Kotlin + Compose**, module-per-feature (`feature-*`), core logic in `core/`, data in `data/`/`domain/`.
   - **Tests are mandatory** for new functionality (unit tests in `src/test`, same module — see "Test policy" below).
   - **No hardcoded UI strings** — all user-facing text goes into `values/strings.xml` **plus complete `values-en/` and `values-fr/` translations**. The i18n guard (`scripts/check_i18n.sh`) blocks `Text("…")`-style literals in UI modules. Bot replies are deliberately kept in the streamer's language and are exempt (see `docs/i18n-plan.md` §4).
3. **Run the pre-push gate locally** (see below) — it runs the same checks as CI.
4. **Open the pull request against `develop`** — the [pull request template](.github/pull_request_template.md) is pre-filled; describe *what* and *why*; link related issues. Bug reports and feature ideas use the templates in [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/).
5. **Required checks must pass**; a maintainer reviews and merges (squash keeps history linear).

### Required checks (branch protection on `develop`)

Every PR must pass these checks before it can be merged:

| Check | Workflow | What it does |
|-------|----------|--------------|
| **Secret Guard** | `android-ci.yml` | Blocks leaked credentials (keystore paths, tokens, keys) |
| **Build & Test** | `android-ci.yml` | Gradle wrapper validation, unit tests (all modules), standard-flavor tests, Android Lint (`warningsAsErrors`), release build |

Additionally, every push triggers the security suite (CodeQL, Snyk, OpenSSF Scorecard, SonarCloud) — findings there are treated as release blockers, not suggestions. Security suppressions (Snyk ignores, dismissed alerts, NOSONAR) must be
documented in the [suppressions register](docs/security-suppressions.md) with a reason and review date — the pre-push gate enforces this.

### Pre-push gate (run CI locally)

```bash
bash scripts/pre-push.sh                 # tests + lint + all guards
bash scripts/pre-push.sh --dry-run       # show what would run
PRE_PUSH_SKIP_LINT=1 bash scripts/pre-push.sh   # skip lint (faster iteration)
PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh     # additionally build release (R8/ProGuard)
```

The gate runs: unit tests for all modules, Lint (`warningsAsErrors`), secret guard, i18n guard, markdown anchor check, PARITY log check, workflow security tests, pip pinning check, release-safety checks. Install it as a Git hook once with:

```bash
bash scripts/install-git-hooks.sh
```

If you must bypass it for a single push: `git push --no-verify` (CI will still gate the merge).

### Test policy

- **New functionality ships with tests.** Unit tests live in the same module (`src/test/java/…`); UI/logic covered via plain JUnit + coroutines-test + MockK (no Robolectric needed).
- CI runs `./gradlew testDebugUnitTest` (all modules) plus `:app:testStandardDebugUnitTest` (design-compliance tests).
- Bug fixes: add a regression test that fails without the fix.
- Test coverage is expected to grow with every feature — the [PARITY.md](PARITY.md) log documents tests per feature as evidence.

### Third-party credit (CONTRIBUTORS.md)

External contributors (people outside the core maintainer team) are credited in
[CONTRIBUTORS.md](CONTRIBUTORS.md) — one table, columns
`Person | Beitrag | Referenz | Status`:

- `Beitrag` = what was implemented (feature, fix, localization).
- `Referenz` = the GitHub issue/PR number (`#123`).
- `Status` = `offen` (announced / not merged yet) or `umgesetzt` (merged).

A maintainer adds the row when accepting the contribution (author may add it in
their own PR — the pull request template has a checkbox for it). `offen` flips to
`umgesetzt` once the work is merged. Structure is
enforced by `scripts/check_contributors.sh` (pre-push gate + CI) — malformed
edits break the build.

**Fork-PR reminder + public credit (automatic).** Merging a pull request from
a fork does not update CONTRIBUTORS.md by itself. On every push to develop,
the `Automation / Contributors Reminder` workflow (scripts/contributors_reminder.sh)
lists fork-PR merges since the last reminder and opens — or comments on — an
auto-issue whenever the credit row is missing or still `offen` after the merge,
including forks deleted after the merge. Additionally, every credited fork PR
receives a **public thank-you comment** on the PR itself: if the credit row is
already `umgesetzt`, the bot thanks the author and links CONTRIBUTORS.md; if
the row is missing or still `offen`, it announces that the credit is being
tracked up. Once the row lands, the next run **edits that announcement into
the thank-you** (closed loop via the `contributors-state: … pending:…`
window marker) — the author sees the credit arrive without any maintainer
action. One comment per PR (idempotency marker), history stays clean, the
bookkeeping stays maintainer work.

### Commit style

Conventional Commits, English, present tense:

```
feat(chat): add deleted-message rendering
fix(streaming): handle File.delete() results in replay cleanup
ci(security): pin pip installs to SHA-256-verified artifacts
docs(security): add CII badge readiness checklist
```

Scopes in use: `chat`, `streaming`, `widgets`, `settings`, `app`, `ci`, `security`, `build`, `docs`, `test`.

### Reporting security issues

Please **do not** open public issues for security vulnerabilities. Follow the private process in [SECURITY.md](SECURITY.md) (GitHub Private Vulnerability Reporting) — initial response within 14 days.

---

## 🇩🇪 Beiträge (auf Deutsch)

### So kannst du mitwirken

| Art | Wo | Hinweise |
|-----|-----|---------|
| Fehler melden | [GitHub Issues](../../issues) | Zuerst Issues-Suche; Geräte-/Android-Version und Repro-Schritte angeben |
| Feature-Wünsche | [GitHub Issues](../../issues) / [Discussions](../../discussions) | Moblin-Parität wird in [PARITY.md](PARITY.md) getrackt |
| Fragen | [Discussions](../../discussions) | Siehe auch [FAQ](docs/faq/common-issues.md) und [Troubleshooting](docs/troubleshooting/) |
| Doku | Pull Request | Doku lebt in `docs/` (Deutsch primär; User-Guide zusätzlich EN/FR) |
| Code | Pull Request | Siehe Workflow unten |

### Ein offenes Issue übernehmen

Issues ohne Assignee sind **frei** — nimm dir eins, das dich interessiert:

```bash
gh issue edit <nr> --add-assignee <dein-name>   # Issue sichern
```

Sobald du anfängst, gehört das Issue dir; bitte im PR darauf beziehen und es
mit abschließen. Es ist ausdrücklich **erlaubt**, ein fremd übernommenes
Issue wieder freizugeben (`--remove-assignee`), wenn du es nicht mehr
machst — lieber offen und unzugewiesen als still liegen gelassen. Es gibt
keinen Anspruch auf ein Issue ohne Arbeit.

Neu angelegte Issues sind bewusst unzugewiesen (seit 03.10.2026, Hausregel in
[docs/sentry-issues.md](docs/sentry-issues.md) §5); sie werden erst dann
zugewiesen, wenn mit der Umsetzung begonnen wird.

### Pull-Request-Workflow

1. **Fork & Branch** — Feature-Branch von `develop` anlegen
   (`git checkout -b feat/mein-feature origin/develop`).
2. **Implementieren** — bestehende Konventionen beachten:
   - **Kotlin + Compose**, Modul je Feature (`feature-*`), Kernlogik in `core/`, Daten in `data/`/`domain/`.
   - **Testpflicht:** neue Funktionalität kommt mit Unit-Tests (gleiches Modul, `src/test`).
   - **Keine hartkodierten UI-Strings** — alle Texte in `values/strings.xml` **plus vollständige Übersetzungen in `values-en/` und `values-fr/`**. Der I18n-Guard (`scripts/check_i18n.sh`) blockt `Text("…")`-Literale in UI-Modulen. Bot-Antworten bleiben bewusst in der Streamer-Sprache und sind ausgenommen (`docs/i18n-plan.md` §4).
3. **Pre-Push-Gate lokal ausführen** (siehe unten) — dieselben Checks wie die CI.
4. **Pull Request gegen `develop` öffnen** — die [Pull-Request-Vorlage](.github/pull_request_template.md) ist vorbefüllt; Was und Warum beschreiben; verwandte Issues verlinken. Fehler- und Feature-Wünsche nutzen die Vorlagen in [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/).
5. **Required Checks müssen grün sein**; ein Maintainer reviewed und merged (Squash hält die History linear).

### Required Checks (Branch Protection auf `develop`)

Jeder PR muss diese Checks bestehen:

| Check | Workflow | Inhalt |
|-------|----------|--------|
| **Secret Guard** | `android-ci.yml` | Blockt geleakte Credentials (Keystore-Pfade, Tokens, Keys) |
| **Build & Test** | `android-ci.yml` | Gradle-Wrapper-Validierung, Unit-Tests (alle Module), Standard-Flavor-Tests, Android Lint (`warningsAsErrors`), Release-Build |

Zusätzlich löst jeder Push die Security-Suite aus (CodeQL, Snyk, OpenSSF Scorecard, SonarCloud) — Findings dort sind Release-Blocker, keine Vorschläge.

#### Bot-PRs und die Check-Suppression

GitHub unterdrückt `pull_request`-Workflows auf PRs, deren Branch mit `GITHUB_TOKEN` gepusht wurde (Rekursions-Schutz) — die Required Checks würden auf Bot-PRs nie laufen. Deshalb pushen alle Automatiken (Changelog-Spiegel, F-Droid-Repo-Update, PyPI-Drift-Wächter) ihre Branches über das **User-Credential** `AUTOMATION_TOKEN` (Fallback `GITHUB_TOKEN` mit `::warning::`-Hinweis im Run-Log). Regressionstest: `scripts/test_bot_pr_credentials.sh` (Teil des Pre-Push-Gates).

**Erkenntnisse vom 06.09.2026 (drei Fehlversuch-Klassen, alle dokumentiert in der Praxis verifiziert):**

1. **Classic-PAT statt Fine-grained PAT.** Der Fine-grained PAT scheiterte in drei Stufen despite korrekter UI-Konfiguration: ohne Repository-Access auf Vivid → Push-403; ohne `Pull requests: RW` → PR-Create-403; **mit beidem weiterhin 403** beim PR-Create (`Resource not accessible by personal access token`, Runs 34021546368/34022706363/34023536729 — Push via Contents:RW funktionierte, der REST-PR-Create nicht). Ein **Classic-PAT mit `repo`-Scope** (https://github.com/settings/tokens/new) funktioniert in allen Stufen. Bei Token-Problemen also: erst prüfen, **welcher** Token im Secret steckt (Permission-Edits gelten nur für den editierten Token!), dann ggf. Classic-PAT verwenden.
2. **REST statt `gh pr create`.** `gh pr create` nutzt die GraphQL-Mutation `createPullRequest`, die von Fine-grained PATs auch mit korrekter Permission häufig abgelehnt wird. Alle vier Bot-Workflows erstellen PRs deshalb per REST: `gh api repos/${{ github.repository }}/pulls -f title=… -f head=… -f base=… -F body=…` (deploy-fdroid.yml, automation-changelog.yml, automation-pypi-drift.yml, Changelog-Mirror in release-pipeline.yml). Guard im Selbsttest (T7/T8).
3. **Vollautomatik-Kette (verifiziert):** Branch-Push per PAT → REST-PR-Create → Pflicht-Checks (Build & Test, Secret Guard) laufen automatisch → Merge per `--admin` (Review-Pflicht umgangen). Belege: PRs #146–#150 an einem Tag. Der PyPI-Drift-Wächter vollendet die Kette vollautomatisch: Nach grünen Pflicht-Checks merged er den PR selbst (REST-Squash mit Admin-Override — `enforce_admins` ist `false`, die 1-Review-Pflicht kann ein Bot-PR nie selbst erfüllen; fail-soft: roter Check oder Timeout lässt den PR offen). Regressionstest: W12 in `scripts/test_pypi_drift_workflow.sh`. Auch die drei Changelog-Mirror-Workflows (automation-changelog.yml, release-pipeline.yml, distribution-stable.yml) wickeln ihre Bot-PRs inzwischen vollautomatisch ab: Nach dem REST-PR-Create pollen sie die Pflicht-Checks und mergen den PR selbst (REST-Squash mit Admin-Override; fail-soft — roter Check oder 30-min-Timeout lässt den PR offen). Regressionstest: T13 in `scripts/test_bot_pr_credentials.sh`.
4. **Orphan-Rollback:** Scheitert der PR-Create (Permission, Netz), bleibt der bereits gepushte Bot-Branch sonst als Orphan zurück (Vorfall: 3 Orphan-Branches aus den 403-Runs, jeweils manuell per API geräumt). Alle vier Bot-Workflows löschen den Branch deshalb automatisch (`gh api -X DELETE …/git/refs/heads/$BRANCH`), bevor sie mit `::error::` abbrechen. Guard: Selbsttest T9.
5. **Rebase-Härtung (Changelog-Mirror):** Läuft zwischen Mirror-Start und PR-Create ein Merge auf develop (v. a. ein anderer Changelog-Bot-PR), wird der neue PR `CONFLICTING` — beide ändern dieselben CHANGELOG-Zeilen (Vorfall #149, gelöst durch: schließen → Mirror neu triggern → frischer PR). Beide Mirror-Workflows (automation-changelog.yml, release-pipeline.yml) holen nach dem Commit `origin/develop` frisch, prüfen per `git merge-base --is-ancestor HEAD~1 origin/develop` und rebasen bei Bedarf. Bei einem Rebase-Konflikt wird der Commit verworfen und der CHANGELOG **auf dem neuen develop neu generiert** (`scripts/update_changelog.sh` ist idempotent und liest Releases frisch von der API) — ein Konflikt-PR kann so nicht mehr entstehen. Guard: Selbsttest T10.

Historischer Hand-Fix, falls beide Automatik-Ebenen fallen: leeren Trigger-Commit auf den Bot-PR-Branch pushen (feuert `synchronize` → volle Check-Suite).

### Pre-Push-Gate (CI lokal ausführen)

```bash
bash scripts/pre-push.sh                 # Tests + Lint + alle Guards
bash scripts/pre-push.sh --dry-run       # zeigt nur, was laufen würde
PRE_PUSH_SKIP_LINT=1 bash scripts/pre-push.sh   # Lint überspringen (schneller iterieren)
PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh     # zusätzlich Release-Build (R8/ProGuard)
```

Das Gate läuft: Unit-Tests aller Module, Lint (`warningsAsErrors`), Secret-Guard, I18n-Guard, Markdown-Anker-Check, PARITY-Log-Check, Workflow-Security-Tests, pip-Pinning-Check, Release-Safety-Checks. Einmalig als Git-Hook installieren:

```bash
bash scripts/install-git-hooks.sh
```

Für einen einzelnen Push umgehen: `git push --no-verify` (die CI gated weiterhin den Merge).

### Testpflicht

- **Neue Funktionalität kommt mit Tests.** Unit-Tests im gleichen Modul (`src/test/java/…`); plain JUnit + coroutines-test + MockK (kein Robolectric nötig).
- Die CI läuft `./gradlew testDebugUnitTest` (alle Module) plus `:app:testStandardDebugUnitTest` (Design-Compliance-Tests).
- Bug-Fixes: Regressionstest ergänzen, der ohne den Fix fehlschlägt.
- Die [PARITY.md](PARITY.md)-Log-Tabelle dokumentiert je Feature die Tests als Beleg.

### Doku-Guards (Handbuch & Wiki aktuell halten)

Die Dokumentation kann nicht mehr unbemerkt veralten — drei Guards erzwingen das:

- **Bot-Befehls-Guard** (`scripts/check_bot_commands_doc.sh`, Teil des Pre-Push-Gates + CI): Die Quick-Reference in [docs/user-guide.md](docs/user-guide.md) (DE) ist die gepflegte Referenz — **jeder neue Bot-Befehl** aus `BotCommandProcessor.dispatch()` muss in **allen drei Sprachen** (DE/EN/FR) dokumentiert sein, sonst ist die CI rot. Wichtig: auch die *Wer?*-Spalte korrekt pflegen (Owner vs. Owner+Mod vs. Alle — steht in `docs/ai-chat-bot.md` bzw. im Code).
- **Bot-Befehls-Katalog** (`BotCommandsCatalog` in feature-chat): Der In-App-Hilfe-Screen rendert seine Befehlszeilen **direkt aus dem Katalog**, der auch den `!help`-Antworttext erzeugt — kein dritter Hardcode mehr. `BotCommandsCatalogTest` beweist per `handle()`, dass jeder Katalog-Name von der dispatch()-Tabelle akzeptiert wird; ein Robolectric-Test verlangt für jeden Eintrag eine lokalisierte Beschreibung (`help_cmd_*`) in allen Sprachen. **Neuer Befehl = Katalog-Eintrag + dispatch-Zeile + `help_cmd_*`-Strings, mehr nicht.**
- **Wiki-Sync** (`.github/workflows/automation-wiki-sync.yml`): Die GitHub-Wiki-Seiten (Home mit DE-Quick-Reference, `User-Guide-EN`, `User-Guide-FR` als vollständige Mirrors) sind **nicht handgepflegt** — sie werden aus den drei Handbüchern generiert (`scripts/sync_wiki.sh`) und bei jeder Änderung an Handbuch oder Bot-Code automatisch neu gepusht. Wiki-Edits werden beim nächsten Sync überschrieben; Doku-Änderungen gehören immer ins Repo (PR gegen `develop`).
- **Security-Loop-Guard** (`scripts/check_security_loop.sh`, Teil des Pre-Push-Gates + CI + Release-Pipeline): Die release-grade Sicherheitsregeln der Pipeline (Keystore-Härtung ohne `KEYSTORE_BASE64`, APK-/AAB-Signatur-Checks gegen den Release-Key, Reproduzierbarkeits-Hash-Vergleich, Sentry-Opt-out-Mapping-Nachweis in beiden Kanälen, `::warning::` bei fehlendem `AUTOMATION_TOKEN`) müssen **strukturell vorhanden bleiben** — entfernt jemand eine dieser Regeln, ist der Guard rot. Der Fixture-Selbsttest (`scripts/test_security_loop.sh`, 8 Fälle) beweist das; im Release-Job läuft der Guard zusätzlich am echten R8-Mapping.
- **EventSub-JSON-Härtung** (`scripts/check_eventsub_json_hardening.sh`, Teil des Pre-Push-Gates + CI): Twitch erweitert EventSub-Payloads ohne Vorankündigung (zuletzt `shared_chat`/`source_*` auf `channel.chat.message`). Jede `Json { … }`-Instanziierung im feature-chat-Produktionscode muss deshalb **`ignoreUnknownKeys = true`** setzen — sonst crasht der Parser bei künftigen Feldern und Chat-Nachrichten fallen still aus Overlay/Bot. Bewusste Opt-outs werden als `@Suppress("EventSubJsonIgnoreUnknownKeys")` an der Datei markiert und im Guard-Log als `SUPPRESSED` ausgewiesen. Fixture-Selbsttest: `scripts/test_eventsub_json_hardening.sh` (9 Fälle); Contract-Tests mit realen Shared-Chat-Payloads: `TwitchChatEventSubReaderTest` (`contract *`).
- **gh-CLI-Flag-Guard** (`scripts/check_gh_cli_flags.sh`, Teil des Pre-Push-Gates + CI): Jede `gh`-Flag-Verwendung in den Workflows wird gegen die Hilfe der lokalen gh-CLI validiert (Subcommand-Auflösung inklusive, gecacht). Ein Tippfehler wie `gh release list --exclude-prereleases` (richtig: `--exclude-pre-releases`) bricht sonst erst den CI-Job; der Guard fängt ihn vor dem Push. Fixture-Selbsttest: `scripts/test_gh_cli_flags.sh` (8 Fälle). Bewusste Ausnahmen: `# gh-flag-exempt: <grund>` am Zeilenende.

### Keine Zeilenfenster in Guards (Meta-Guard)

`scripts/test_no_line_windows.sh` (Pre-Push-Gate + CI) verbietet **Zeilen- und Kontextfenster** in allen Guard- und Testskripten: `grep -A N` / `grep -B N`, `head - N` als Assertion, `sed -n 'N,Mp'`, `awk 'NR==N'`.

**Warum.** Ein Struktur- oder Semantik-Fakt, der als Zeilenabstand ausgedrückt wird, bricht genau dann, wenn jemand an etwas völlig anderem arbeitet — eine Kommentarzeile, eine Leerzeile, ein neuer Key. Diese Klasse hat das Repo dreimal getroffen:

| Vorfall | Muster | Wirkung |
|---|---|---|
| #249 | `grep -A35` auf den Emulator-Gate-Block | Ein längerer Kommentar schob den Block aus dem Fenster; der Check wurde rot, obwohl sich am Getesteten nichts geändert hatte. |
| #258 | `head -30 \| grep 'permissions: {}'` | `permissions: {}` rutschte auf Zeile 32, der Workflow war korrekt. Ein Kommentar in derselben Datei dokumentierte schon, dass das `-A9`-Fenster einmal von Hand breiter gestellt werden musste. |
| #258 | `head -5` auf die `set -euo pipefail`-Zeile in `pre-push.sh` | Gleiche Klasse, gleiche Ursache. |

Ohne den Meta-Guard wandern die Prüfungen still zurück auf Fenster, und der nächste Kommentar lässt sie erneut rot werden. **Die Regel lautet daher: YAML-Struktur wird geparst, Semantik über Anker verglichen — nie über Abstand.**

**Zulässig sind bewusst nur:**

1. `head -1` — „erster Treffer" bzw. „erste Zeilennummer" als Datenbegrenzung.
2. Zeilennummern-Vergleiche `… | head -1 | xargs test {} -lt $(…)` — prüfen eine *Reihenfolge*, keinen Abstand; Einfügungen dazwischen ändern die Aussage nicht.
3. Einträge in der Allowlist des Guards. Dort **ist** der Abstand die Aussage (z. B. „der Retry-Wrapper muss unmittelbar über dem Task stehen"). Jeder Eintrag trägt eine Begründung und wird auf Existenz geprüft, damit die Allowlist nicht verwaisen kann.

**Für Workflow-Strukturfragen** gibt es `scripts/lib_workflow_yaml.sh` (`source`n): `wf_job_permissions`, `wf_job_if`, `wf_step_uses`, `wf_step_with`, `wf_step_index`, `wf_step_if`, `wf_step_run`. Die Assertions sind bewusst **exakt** — der komplette Permission-Satz, nicht „enthält `issues: write`“. Bei einem Least-Privilege-Guard ist eine zusätzlich vergebene Berechtigung genau das, was auffallen soll; ein `grep -Fq` auf eine Einzelberechtigung lässt sie durch. Unbekannter Pfad, Job oder Key liefert `-`, damit ein Tippfehler nicht wie ein bestandener Check aussieht.

**Und derselbe Helper kann auch vorhanden sein und trotzdem nichts liefern (#259).** Die Extraktion lief über `print()` in der Standard-Kodierung der Konsole — unter Windows cp1252. Ein Workflow-Wert mit `≠`, `—` oder `ü` (z. B. der Kommentar `RC≠0` in einem `run`-Snippet) brach daraufhin mit `UnicodeEncodeError` ab, der Fehler landete im `|| echo "-"` und sah damit wie „nicht gefunden“ aus. Die Library nagelt stdout deshalb fest auf UTF-8 fest. Wichtig allgemein: die Sentinel-Semantik („`-` = unbekannt“) ist nur dann sicher, wenn der Fehlerweg *nicht* denselben Wert liefert wie der Normalfall — sonst ist ein Tippfehler und ein kaputter Interpreter nicht mehr unterscheidbar. Geprüft wird das in `test_workflow_security.sh` **verhaltensbasiert** (ein echter Probe-Workflow mit `≠` muss einen echten Wert liefern), nicht per grep auf die reconfigure-Zeile.

**Verhaltenstests statt Zeilenvergleiche.** Für Logik, die in einem `run:`-Block steht, genügt kein grep auf eine Zeile: `wf_step_run` liefert das Snippet, der Guard führt es mit gestubbtem `git`/`gh` aus und prüft das Ergebnis. Das trifft die Untergrenze der Stable-Tag-Auswahl (#259, `D16.5`–`D16.8`) — dort ist entscheidend, *welcher* Tag gewählt wird, nicht dass eine bestimmte Zeile vorhanden ist.
**Ein Stub muss das Werkzeug emulieren, nicht nur antworten (#262).** Beim Verhaltenstest des
Verify-Jobs (#262) war der `gh`-Stub zu ehrlich: Er gab das rohe JSON von `release list` zurück
und ignorierte dabei `--jq`. Das Snippet griff folglich auf das **komplette JSON-Array** als Tag
zu (`TAG='[{"tagName":…}]'`), und der Fehlerfall „kein Nightly im Raster“ blieb unentdeckt — ein
Array-String ist weder leer noch `null`, also lief jeder Negativzweig ins Leere. Geprüft wurde
damit nicht die Auswahl-Logik der Pipeline, sondern die Treue des Stubs. Ein Stub muss das
Filter-Verhalten des Originals nachbilden: bei `gh` heißt das, `--jq` selbst auswerten
(`printf '%s\n' "$DATA" | jq -r "$filter"`), sonst prüft der Guard die Auswertung, die es gar nicht gibt.

**Drei Fallen beim Negieren — alle drei hat die Mutationsprobe aufgedeckt.** Ein Check, der nur im
Positivfall stimmt, beweist nichts. Deshalb wird jede neue Assertion einmal gegen eine Mutation
geprüft (der erwartete Fehlschlag muss eintreten) und das Ergebnis nach dem Restore gegen eine
Negativkontrolle (reiner Kommentarumbau muss grün bleiben). Drei dabei gefundene Muster:

- **Ein Check ist EIN Kommando.** `check "…" test X && test Y` zerlegt die Shell in **zwei**
  Aufrufe: `check` sieht nur `test X`, meldet PASS und verwirft den Status von `test Y` über das
  `&&`. Im Skript von #262 stand genau so eine Bedingung und blieb grün, obwohl das `if` entfernt
  worden war. Beide Hälften gehören in ein `bash -c '[ … ] && [ … ]'`.
- **Negationen prüfen gegen `-`, nicht gegen `''`.** Die `wf_*`-Helfer liefern bei „nicht
  gefunden“ per Hauskonvention den Sentinel `-`. `test "$(wf_step_if …)" = ''` ist deshalb
  **immer wahr** — der Check bleibt grün, obwohl die Bedingung entfernt wurde.
- **Kommentare, die Verhalten zitieren, kippen `check_absent`.** Ein Guard, der prüft „diese
  Fehlermeldung ist weg“, und dabei `grep -q 'Version/Revision mismatch'` verwendet, wird rot,
  sobald ein Kommentar den alten Wortlaut **erklärend** nennt — dieselbe Klasse wie das
  Issue-Regel-Beispiel in `AGENTS.md` (#260). Guards prüfen deshalb die konkrete **Codeform**
  (`grep -q 'echo "::error::…'`), nicht einen Text, den auch Prosa zitieren darf.

**Restore niemals über `git checkout --`.** Das stellt aus dem **Index** wieder her und verwirft
dabei alles, was nicht gestaged ist — bei einem Mutationslauf über mehrere Dateien kostet das
den gesamten Arbeitsstand. Gesichert wird vor dem Lauf in ein Verzeichnis außerhalb des
Baums (`D:/temp/bak*/` unter Windows, `/tmp` überlebt nur innerhalb **eines** Tool-Aufrufs) und
danach Datei für Datei zurückgespielt. Ein abschließender `cmp` je Datei beweist, dass keine
Mutation überlebt hat.

**Ein nicht existierender Helper sieht aus wie ein bestandener Check.** `$(wf_step_if …)` in einer `[[ -z … ]]`-Prüfung ist bei einem Tippfehler im Funktionsnamen *immer* wahr: Bash meldet `command not found` auf stderr, die Command-Substitution liefert trotzdem leer, der Check bleibt dauerhaft grün. Deshalb vergleicht `test_workflow_security.sh` **jeden in Guards benutzten `wf_*`-Namen gegen die Definition in der Library** — und meldet umgekehrt definierte Helfer, die niemand benutzt (tote Zusicherung, nur ein Hinweis).

### Gate-Skripte nie aus dem Ziel-Tag-Baum beziehen (#250)

Der Stable-Publish testet den **Ziel-Tag**, führt aber die Workflow-Datei aus `develop` aus. Das sind zwei verschiedene Bäume — und der Job hat lange beides vermischt:

```
Checkout code            → Arbeitsbaum = develop (fetch-depth: 0)
Determine target …       → TAG = v0.5.15-beta
Checkout target tag      → Arbeitsbaum = v0.5.15-beta
Run instrumented tests…  → bash scripts/emulator_gate_retry.sh   ← Datei fehlt im Tag
```

Vorfall 28.09.2026 (Run `36402544290`): `bash: scripts/emulator_gate_retry.sh: No such file or directory` → **exit 127**. Der Wrapper war erst drei Stunden vor dem letzten grünen Lauf (`ddc9d777`, 25.09. 06:53) entstanden, also nach `v0.5.15-beta`. Es war kein Runner-Image-Drift, sondern eine deterministische Eigenschaft des Tags — und damit **blockiert ein einziger alter Tag den Stable-Kanal**, sobald die Auswahl bei ihm landet.

**Die Regel: Produktstand aus dem Ziel-Tag, Pipeline-Stand aus `develop`.** Gate-Skripte werden im Schritt `Stage CI gate scripts (from develop, not target tag)` **vor** dem `git checkout` in `$RUNNER_TEMP` gespiegelt und von dort aufgerufen (`$CI_SCRIPTS_DIR/…`). Fehlt ein Skript im develop-Baum, bricht der Job sofort mit `::error::` ab statt drei Schritte später mit exit 127.

Ein Skript, das seinen Repo-Root aus `$0` ableitet (`cd "$(dirname "$0")/.."`), verliert dabei die Orientierung. `check_sentry_resolve.sh` löst das über `cd "${VIVID_REPO_ROOT:-$(dirname "$0")/..}"` — der Fallback erhält jeden bisherigen Aufruf, der Workflow setzt die Variable explizit. Festgeschrieben in `test_emulator_matrix.sh` (T17.1–T17.11) und `test_sentry_resolve.sh` (R13).

**Gegenprobe zur Fehlerklasse:** Positions-Checks auf Workflow-Dateien ankerten besser auf `$CI_SCRIPTS_DIR/<skript>` als auf den bloßen Skriptnamen. Der alte Anker `scripts/emulator_gate_retry.sh` matchte sowohl den echten Aufruf als auch einen Pfad in einem Kommentar — und ist in `vivid-ci-scripts/…` gar nicht mehr enthalten. Ein Kommentarpfad verschiebt einen Namens-Substring-Anker und kippt die Reihenfolge-Aussage (das ist in dieser Sitzung einmal passiert).

### Danksagung Dritter (CONTRIBUTORS.md)

Dritte (Personen außerhalb des Kern-Teams) werden in
[CONTRIBUTORS.md](CONTRIBUTORS.md) gewürdigt — eine Tabelle, Spalten
`Person | Beitrag | Referenz | Status`:

- `Beitrag` = was umgesetzt wurde (Feature, Fix, Lokalisierung).
- `Referenz` = GitHub-Issue/-PR-Nummer (`#123`).
- `Status` = `offen` (angekündigt / noch nicht gemergt) oder `umgesetzt`
  (gemergt).

Beim Annehmen eines Beitrags ergänzt ein Maintainer die Zeile (der Autor darf es
im eigenen PR selbst tun); `offen` → `umgesetzt`, sobald die Arbeit gemergt ist.
Die Struktur erzwingt `scripts/check_contributors.sh` (Pre-Push-Gate + CI) —
Formatfehler brechen den Build ab.

**Fork-PR-Erinnerung + öffentlicher Dank (automatisch).** Das Mergen eines
Fork-PRs aktualisiert CONTRIBUTORS.md nicht von selbst. Bei jedem Push auf
develop listet der Workflow `Automation / Contributors Reminder`
(scripts/contributors_reminder.sh) die Fork-PR-Merges seit dem letzten
Reminder und eröffnet — oder kommentiert auf — ein Auto-Issue, sobald die
Beitragszeile fehlt oder nach dem Merge noch `offen` steht, einschließlich
nach dem Merge gelöschter Forks. Zusätzlich erhält jeder gewürdigte Fork-PR
einen **öffentlichen Dank-Kommentar** am PR selbst: Ist die Zeile bereits
`umgesetzt`, dankt der Bot dem Autor mit Verweis auf CONTRIBUTORS.md; fehlt
die Zeile oder steht noch `offen`, kündigt er das Nachziehen an. Sobald die
Zeile landet, **editiert der nächste Run diese Ankündigung zum Dank**
(geschlossener Regelkreis über den State-Marker `contributors-state: …
pending:…`) — der Autor sieht die Würdigung nachziehen, ohne Maintainer-Hand.
Ein Kommentar je PR (Idempotenz-Marker), die Historie bleibt sauber, die
Pflege bleibt Maintainer-Handarbeit.

Der State-Marker liegt an **zwei** Orten, und beide werden gelesen
(`latest_state_for`): im **Body** der Auto-Issue, die beim ersten Melden
angelegt wird, und in einem **Kommentar**, der den Stand bei jedem Nachziehen
fortschreibt. Der Kommentar-Stand hat Vorrang, weil der Body nie
nachgeführt wird. Das ist kein Redundanz-, sondern Fehlerkorrektur-Bedarf:
Vor #258 wurde nur der Kommentar gelesen, wodurch der State einer frisch
eröffneten Reminder-Issue verloren ging — der nächste Run startete mit leerer
`pending`-Liste, der Merge lag plötzlich außerhalb des Fensters, und der
Regelkreis meldete „nichts zu tun", während Beiträge unversorgt blieben.
Ebenso braucht der Job **zwei** Berechtigungen: Der Dank-Kommentar geht an
einen *Pull Request*, und der Actions-Token braucht dafür `pull-requests:
write` — `issues: write` allein genügt für Issues, nicht für PR-Kommentare
(`X-Accepted-GitHub-Permissions: issues=write; pull_requests=write`). Beide
Punkte sind in `scripts/test_contributors_reminder.sh` festgeschrieben
(C8c, C15, C16).

### Commit-Stil

Conventional Commits, Englisch, Präsens — Beispiele und Scopes siehe oben (englischer Teil).

### Sicherheitsprobleme melden

Bitte **keine** öffentlichen Issues für Sicherheitslücken. Dem privaten Prozess in [SECURITY.md](SECURITY.md) folgen (GitHub Private Vulnerability Reporting) — Erstantwort innerhalb von 14 Tagen.

---

## Verhaltenskodex

Mitwirkende verpflichten sich auf den [Verhaltenskodex](CODE_OF_CONDUCT.md) (Contributor Covenant).
