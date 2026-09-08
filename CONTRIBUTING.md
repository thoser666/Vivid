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
4. **Open the pull request against `develop`** — describe *what* and *why*; link related issues.
5. **Required checks must pass**; a maintainer reviews and merges (squash keeps history linear).

### Required checks (branch protection on `develop`)

Every PR must pass these checks before it can be merged:

| Check | Workflow | What it does |
|-------|----------|--------------|
| **Secret Guard** | `android-ci.yml` | Blocks leaked credentials (keystore paths, tokens, keys) |
| **Build & Test** | `android-ci.yml` | Gradle wrapper validation, unit tests (all modules), standard-flavor tests, Android Lint (`warningsAsErrors`), release build |

Additionally, every push triggers the security suite (CodeQL, Snyk, OpenSSF Scorecard, SonarCloud) — findings there are treated as release blockers, not suggestions.

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

### Pull-Request-Workflow

1. **Fork & Branch** — Feature-Branch von `develop` anlegen
   (`git checkout -b feat/mein-feature origin/develop`).
2. **Implementieren** — bestehende Konventionen beachten:
   - **Kotlin + Compose**, Modul je Feature (`feature-*`), Kernlogik in `core/`, Daten in `data/`/`domain/`.
   - **Testpflicht:** neue Funktionalität kommt mit Unit-Tests (gleiches Modul, `src/test`).
   - **Keine hartkodierten UI-Strings** — alle Texte in `values/strings.xml` **plus vollständige Übersetzungen in `values-en/` und `values-fr/`**. Der I18n-Guard (`scripts/check_i18n.sh`) blockt `Text("…")`-Literale in UI-Modulen. Bot-Antworten bleiben bewusst in der Streamer-Sprache und sind ausgenommen (`docs/i18n-plan.md` §4).
3. **Pre-Push-Gate lokal ausführen** (siehe unten) — dieselben Checks wie die CI.
4. **Pull Request gegen `develop` öffnen** — Was und Warum beschreiben; verwandte Issues verlinken.
5. **Required Checks müssen grün sein**; ein Maintainer reviewed und merged (Squash hält die History linear).

### Required Checks (Branch Protection auf `develop`)

Jeder PR muss diese Checks bestehen:

| Check | Workflow | Inhalt |
|-------|----------|--------|
| **Secret Guard** | `android-ci.yml` | Blockt geleakte Credentials (Keystore-Pfade, Tokens, Keys) |
| **Build & Test** | `android-ci.yml` | Gradle-Wrapper-Validierung, Unit-Tests (alle Module), Standard-Flavor-Tests, Android Lint (`warningsAsErrors`), Release-Build |

Zusätzlich löst jeder Push die Security-Suite aus (CodeQL, Snyk, OpenSSF Scorecard, SonarCloud) — Findings dort sind Release-Blocker, keine Vorschläge.

#### Bot-PRs und die Check-Suppression

GitHub unterdrückt `pull_request`-Workflows auf PRs, deren Branch mit `GITHUB_TOKEN` gepusht wurde (Rekursions-Schutz) — die Required Checks würden auf Bot-PRs nie laufen. Deshalb pushen alle Automatiken (Changelog-Spiegel, F-Droid-Repo-Update) ihre Branches über das **User-Credential** `AUTOMATION_TOKEN` (Fallback `GITHUB_TOKEN` mit `::warning::`-Hinweis im Run-Log). Regressionstest: `scripts/test_bot_pr_credentials.sh` (Teil des Pre-Push-Gates).

**Erkenntnisse vom 06.09.2026 (drei Fehlversuch-Klassen, alle dokumentiert in der Praxis verifiziert):**

1. **Classic-PAT statt Fine-grained PAT.** Der Fine-grained PAT scheiterte in drei Stufen despite korrekter UI-Konfiguration: ohne Repository-Access auf Vivid → Push-403; ohne `Pull requests: RW` → PR-Create-403; **mit beidem weiterhin 403** beim PR-Create (`Resource not accessible by personal access token`, Runs 34021546368/34022706363/34023536729 — Push via Contents:RW funktionierte, der REST-PR-Create nicht). Ein **Classic-PAT mit `repo`-Scope** (https://github.com/settings/tokens/new) funktioniert in allen Stufen. Bei Token-Problemen also: erst prüfen, **welcher** Token im Secret steckt (Permission-Edits gelten nur für den editierten Token!), dann ggf. Classic-PAT verwenden.
2. **REST statt `gh pr create`.** `gh pr create` nutzt die GraphQL-Mutation `createPullRequest`, die von Fine-grained PATs auch mit korrekter Permission häufig abgelehnt wird. Alle drei Bot-Workflows erstellen PRs deshalb per REST: `gh api repos/${{ github.repository }}/pulls -f title=… -f head=… -f base=… -F body=…` (deplekt-fdroid.yml, automation-changelog.yml, Changelog-Mirror in release-pipeline.yml). Guard im Selbsttest (T7/T8).
3. **Vollautomatik-Kette (verifiziert):** Branch-Push per PAT → REST-PR-Create → Pflicht-Checks (Build & Test, Secret Guard) laufen automatisch → Merge per `--admin` (Review-Pflicht umgangen). Belege: PRs #146–#150 an einem Tag.
4. **Orphan-Rollback:** Scheitert der PR-Create (Permission, Netz), bleibt der bereits gepushte Bot-Branch sonst als Orphan zurück (Vorfall: 3 Orphan-Branches aus den 403-Runs, jeweils manuell per API geräumt). Alle drei Bot-Workflows löschen den Branch deshalb automatisch (`gh api -X DELETE …/git/refs/heads/$BRANCH`), bevor sie mit `::error::` abbrechen. Guard: Selbsttest T9.
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

### Commit-Stil

Conventional Commits, Englisch, Präsens — Beispiele und Scopes siehe oben (englischer Teil).

### Sicherheitsprobleme melden

Bitte **keine** öffentlichen Issues für Sicherheitslücken. Dem privaten Prozess in [SECURITY.md](SECURITY.md) folgen (GitHub Private Vulnerability Reporting) — Erstantwort innerhalb von 14 Tagen.

---

## Verhaltenskodex

Mitwirkende verpflichten sich auf den [Verhaltenskodex](CODE_OF_CONDUCT.md) (Contributor Covenant).
