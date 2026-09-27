# Sentry-Issues-Watchdog (Crash → GitHub-Issue automatisch)

Neue, bisher unerfasste Sentry-Crash-Reports werden automatisch als
GitHub-Issue geöffnet — mit bug_report-artiger Struktur + Sentry-Ausgabe
(Level-Zeile, Top-Frame, Permalink, Events-/Nutzer-Count, First/Last-Seen).
Der Guard ist die **Watchdog-/Failover-Schicht** zur nativen
Sentry-GitHub-Integration ([docs/sentry-alerts.md](sentry-alerts.md) §2):

- **Native Route (Echtzeit):** Sentry-Alert-Regel „Create a GitHub issue" —
  öffnet Issues sofort beim Event, serverseitig. Von außen **nicht
  verifizierbar**; de facto war bisher kein echter Crash-Report als Issue
  belegt (nur Test-Issues der Triage-Automation).
- **Dieser Watchdog (Repo-seitig):** zieht stündlich alle unresolved
  Sentry-Issues per API und erstellt für die noch nicht erfassten ein
  GitHub-Issue mit `<!-- vivid-sentry-issue: <sentry-id> -->`-Marker (Dedup).
  Läuft die native Route, findet er nichts Neues; bricht sie weg, fängt er
  die Lücke. Macht die Erstellung von außen überprüfbar.

Kein Ersatz für die Echtzeit-Route, sondern deren Sicherheitsnetz + Sicht.

## 1. Token (einmalig erstellen, 30 Tage)

User-Token mit Scope **`project:read`** (Issues-Liste):

1. <https://sentry.io/settings/account/api/auth-tokens/> → **Create New Token**
   (User Token, nicht Internal Integration)
2. Scopes **minimal**: `project:read` (+ optional `org:read`). Bewusst kein
   `project:write` — der Guard erstellt nur GitHub-Issues, er ändert nichts an
   Sentry-Issues.
3. Ablauf: 30 Tage (Sentry-Default); nach Ablauf → SKIP-Fallback des Guards.

In CI hinterlegen:

```bash
gh secret set SENTRY_ISSUES_TOKEN --body sntrys_…
```

Lokal (gitignorede `sentry.properties`, Projektstamm — **nicht** `auth.token`
überschreiben):

```properties
issues.token=sntrys_…
```

Quellen-Reihenfolge: `SENTRY_ISSUES_TOKEN` → `issues.token` → `resolve.token`
→ `stats.token` → `auth.token`. `--print-token-source` meldet nur die Quelle,
nie den Token. Für die GitHub-Seite nutzt der Guard `GITHUB_ISSUES_TOKEN`
(CI: `secrets.GITHUB_TOKEN`, reicht für issues:write); lokal einen PAT mit
`repo` setzen, sobald ein Live-Lauf gewünscht ist.

## 2. Nutzung (lokal)

```bash
bash scripts/test_sentry_issues.sh            # I1–I16, offline, kein Netz
bash scripts/check_sentry_issues.sh --dry-run # zeigt, welche Issues erstellt würden
bash scripts/check_sentry_issues.sh           # echte Erstellung (GitHub-Token nötig)
bash scripts/check_sentry_issues.sh --print-token-source
```

Verdicts:

```
OK: 2 Issue(s) neu als GitHub-Issue erstellt (Watchdog)   # erstellt
OK: 0 neue Sentry-Issues — alle unresolved Reports sind ... erfasst
OK (dry-run): 2 Issue(s) würden neu ... erstellt
SKIP: kein Sentry-Lese-Token — SENTRY_ISSUES_TOKEN setzen oder issues.token ...
SKIP: kein GitHub-Token (GITHUB_ISSUES_TOKEN) — Dedup wäre blind ...
SKIP: Token ungültig/abgelaufen ... / ohne Lesescopes (project:read) ...
FEHLER: Sentry-/GitHub-Antwort ist keine Liste — API-Feldformat geändert (exit 1)
```

Exit-Codes: `0` für OK/SKIP (bewusst kein Gate-Blocker); `1` nur bei
unverständlicher API-Antwort oder GitHub-422 (fail-closed). Der Watchdog
selbst läuft **nicht** in Pre-Push-Gate/CI (netzgebunden, schreibt Issues) —
nur sein Selbsttest (`test_sentry_issues.sh`) ist verdrahtet (Pre-Push-Gate +
android-ci.yml).

## 3. Crawling & Dedup-Vertrag

- Sentry: `GET /api/0/projects/{org}/{project}/issues/?query=is:unresolved&sort=new&limit=100`
  mit `rel="next"`-Pagination. Nur Status `unresolved` wird betrachtet
  (resolved zählt nie).
- GitHub-Bestand: `GET /repos/{owner}/{repo}/issues?state=all&per_page=100`
  (auch geschlossene Issues — dedupliziert gegen erneutes Anlegen nach
  manuellem Schließen). **Pull Requests zählen nicht** (Marker in PR-Bodies
  wird ignoriert).
- Dedup: Issue-Body enthält `<!-- vivid-sentry-issue: <sentry-id> -->`.
  Konkurrenz zweier Cron-Läufe verhindert die Workflow-`concurrency`-Gruppe.
- Erstellt wird mit Label `sentry`; die Triage-Automation
  (`automation-sentry-triage.yml`, Trigger `issued.opened`) übersetzt die
  `**Level:**`-Zeile in `severity:*` (error→high, warning→medium, …) und
  setzt `crash`/Advisory-Hinweis bei kritischen — exakt wie bei der nativen
  Route.

## 4. Im CI (stündlich)

`.github/workflows/automation-sentry-issues.yml`:

- Cron `17 * * * *` plus `workflow_dispatch`; `permissions: {}` top-level,
  Job nur `issues: write`; checkout SHA-gepinnt; `concurrency`-Gruppe
  `sentry-issues-watchdog` (kein Doppel-Anlegen).
- Fehlt `SENTRY_ISSUES_TOKEN`, läuft der Workflow neutral (SKIP) — kein
  Alarm. Der Guard erstellt die Issues direkt (kein Bot-PR, daher kein
  AUTOMATION_TOKEN nötig).

## 5. Sicherheit

- Token getrennt vom CI-Mapping-Token (`auth.token`) — getrennte Scopes,
  Lebensdauern, Zwecke. Der Guard gibt nie einen Token aus (Selbsttest I7).
- Der Issue-Body trägt nur technische Crash-Zusammenfassung (keine
  benutzerbezogenen Felder; weder die App noch der Watchdog überträgt PII).
  Vollständiger Stacktrace bleibt im privaten Sentry-Report (Permalink).