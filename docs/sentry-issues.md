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
nie den Token. Für die GitHub-Seite nutzt der Guard `GITHUB_ISSUES_TOKEN` —
in CI ist das **`AUTOMATION_TOKEN`** (PAT), **nicht** `GITHUB_TOKEN`:
Von `GITHUB_TOKEN` ausgelöste `issues.opened`-Ereignisse erzeugen laut
GitHub-Dokumentation **keine neuen Workflow-Runs** — damit bliebe der
Triage-Autolabel (`severity:*`/`crash`) nach einer Watchdog-Erstellung
permanent aus. Ein PAT-Ereignis dagegen triggert die Triage-Automation wie
die native Route.

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
  Alarm. Erstellt wird mit dem **AUTOMATION_TOKEN** (PAT), damit die
  Triage-Automation auf das `issues.opened`-Ereignis reagieren kann
  (GITHUB_TOKEN-Events würden keine Workflow-Runs auslösen).

## 5. Triage-Verfahren für Watchdog-Crash-Issues

Der Watchdog erstellt Issues automatisch — die Attribution ist **manuelle
Arbeit**. Verbindliches Verfahren (verbindlich seit dem Doppel-Issue
#164/#215, Sentry VIVID-36 — derselbe Crash wurde zweimal gemeldet und beim
ersten Mal als „nicht reproduzierbar“ geschlossen, weil die Versions-Spur
nicht gezogen wurde):

1. **Versions-Spur ziehen, bevor „nicht reproduzierbar“ geschlossen wird:**
   Crash-Fenster (first-seen/last-seen) gegen Release-Timeline matchen
   (`git tag --sort=creatordate` + versionCode-Formel
   `major*1_000_000+minor*1_000+patch*10+4, beta = …2 / stable = …4`).
   Events enden typischerweise, weil die Nutzer ein Update gezogen haben —
   nicht, weil der Bug verschwunden ist. Danach `git log -G` über die
   Crash-API (z. B. `verticalScroll\(`) im Fenster: Änderungen um last-seen
   herum sind Fix-Kandidaten (auch unattributierte „Beifang“-Änderungen wie
   Test-Coverage-Runden). **Wichtig (Sentry-Ingress-Ebenen):** Das Sentry
   Android SDK instrumentiert auch Timber — `Timber.e` erzeugt
   **error**-Events (kein Crash!). Der Frame zeigt dann auf die
   Logging-Stelle (z. B. `probePort$core`), nicht auf die Crash-API;
   fatal + `Net.java:bind0` heißt dagegen Prozess-Crash in der Bind-API.
   Der `app.version_code`-Tag läuft erst seit dem 27.09.2026 (ea16fadc) —
   ältere Events sind **versionlos**; Attribution dann über Build-
   Archäologie (Tag-Enthaltensein per `git merge-base --is-ancestor`)
   statt über Sentry-Tags.
2. **Doppel-Meldungen desselben Sentry-Issue verbinden:** Vor der Schließung
   `gh issue list --label sentry` nach gleichem Top-Frame/Befund durchsuchen
   (Body-Marker `vivid-sentry-issue: <id>` identifiziert das Sentry-Issue
   eindeutig) — ein Befund = ein Attributions-Thread.
3. **Real identifizierte, versionsgebundene Crashes** landen in der
   [CrashAdvisoryRegistry](../core/src/main/java/com/vivid/core/startup/CrashAdvisory.kt)
   (Range + Workaround; siehe RELEASE.md → 🛰️ Sentry-Ops). Bei stiller
   Entschärfung durch einen früheren Commit: nachträglich attribuieren
   (Registry-Eintrag + Regressionstest auf dem heutigen Pfad), damit die
   Landmine nicht reaktiviert werden kann.
4. **Probe-/Test-Issues** („delete me“, `probe=health-check`-Quelle,
   0 betroffene Nutzer) ohne Registry-Eintrag direkt schließen — der
   Watchdog dedupliziert gegen `state=all`, legt sie nicht erneut an
   (erledigt für #223/#227, VIVID-3F/3G).
5. **Neue Issues immer zuweisen:** Jedes manuell angelegte Issue wird
   sofort beim Anlegen dem Maintainer zugewiesen —
   `gh issue create … --assignee thoser666` (betrifft Attributions-Threads,
   Triage-Follow-ups und Task-Issues gleichermaßen; Assignee-Vermerk ist
   Teil des Hausmusters, siehe #215/#225). Scheitert die Zuweisung beim
   Anlegen, nachziehen mit `gh issue edit <nr> --add-assignee thoser666`;
   ein offenes, unzugewiesenes Issue gilt als nicht abgeschlossen.
6. **Schließung: GitHub-Issue manuell, Sentry-Issue per Automation.** Der
   fix-release-Tag (siehe `docs/sentry-stats.md` §6) arbeitet **nur
   Sentry-seitig** — beim Stable-Publish resolvt der Resolve-Guard die
   getaggten Sentry-Issues (`resolved/inNextRelease`), die GitHub-Issues
   des Befunds schließt er **nicht** (der Watchdog legt nur an, er schließt
   nie; Historie: #215/#228 wurden manuell geschlossen). Verfahren:
   Beim Release-Schnitt die Sentry-Issues im Dashboard mit
   `fix-release: <version>` taggen (vor dem Stable-Publish), nach dem
   Stable-Publish das GitHub-Issue manuell schließen. Ein „resolved“-Vermerk
   in einem Attributionskommentar bezieht sich immer auf die Sentry-Ebene,
   nie auf den GitHub-Issue-Status.

Beispiel einer vollständigen Nach-Attribution: #215 (VIVID-36) →
`CAM-FOCUS-INFINITE-SCROLL` (Doppel-Scroll in `SettingsCameraScreen`,
kranker Range 5102–5122, still entschärft in 009972ad, Regressionstests in
`SettingsSubScreensRobolectricTest`).

Beispiel Range-Korrektur nach ausgelieferten Versionen: #228/#222
(VIVID-37/3E, BindException EADDRINUSE) — die Registry-Range von
`REMOTE-EADDRINUSE-STARTUP` war ursprünglich 5000–5144 („Fix ab 5162“),
obwohl die 5162er-Härtung (99d14bd5) nur die synchrone Port-Probe fing und
das Probe→Bind-Rennen des asynchronen Engine-Binds offenließ (fatal-Events
bis 26.09.2026). Korrektur auf 5000–5172, Workaround ≥ 5182 (65c93832,
Bind-Verifikation). VIVID-3E ist dasselbe Phänomen als **gefangener**
Fehler (Timber.e → error-Event, Frame `RemoteControlServer.kt:probePort$core`).

Beispiel VIVID-39 (#221, MediaProjection-FGS-Typ): erster Crash aus der
Screen-Capture-Quelle — 4 Events / 3 Nutzer (08.09.–21.09.2026),
`Unable to start service ... StreamingService ...: SecurityException: Media
projections`. Android 14+ verlangt für
`MediaProjection.createVirtualDisplay()` einen laufenden FGS vom Typ
`mediaProjection`; der StreamingService meldete nur `microphone|camera` an, die
Exception verließ synchron `onStartCommand` und crashte den Prozess. Fix
(06012c4a-Log zu `f6d3274f`): Manifest-Permission +
`foregroundServiceType="microphone|camera|mediaProjection"`, FGS-Typ nur bei
aktiver Screen-Capture-Quelle (pure Funktion
`StreamingServiceSupport.requiresMediaProjectionFgs`), Defense-in-Depth in
Service und Quelle, Registry-Eintrag `MEDIA-PROJECTION-FGS-TYPE`, Manifest-Guard
C3 (Selbsttest F8/F9). Die Nicht-Crash-Befunde derselben Runde (#217/#218/#219/
#224/#242/#244/#248) waren **error**-Level auf gefangenen Pfaden (OBS-WebSocket
`OBSWebSocketClient.kt:131-133`, Socket-/Connect-Timeouts, RootEncoder-interner
`JobCancellationException`) bzw. native Einzel-Signale (SIGSEGV/SIGABRT, je
1 Event) — attribuiert und geschlossen.

6. **CrashAdvisory-Range beim Release-Schnitt konkretisieren:** Der Schnitt
   aktualisiert Workaround **und** `maxVersionCode` — der Vertrag der Registry
   ist „**veröffentlichte** Version, letzter betroffener Build = Fix-Build − 1“
   (siehe KDoc in `CrashAdvisory.kt`). Platzhalter-Prognosen (z. B. „5194 als
   mögliche v0.5.19-stable“) gehören **nicht** in die Range: Releases werden aus
   dem bereits gefixten Baum geschnitten, ein späterer 5194er-Build kann den
   Crash also gar nicht tragen — solche Builds zu strecken erzeugt nur eine
   falsche Advisory. Deshalb: `maxVersionCode` = letzter **tatsächlich
   ausgelieferter** betroffener Build, `evaluate(5194)` und `evaluate(5202)`
   müssen `null` liefern (Regressionstest in `CrashAdvisoryTest`), und die
   Release-Notes nennen dieselbe Range wie der Code.

## 6. Sicherheit

- Token getrennt vom CI-Mapping-Token (`auth.token`) — getrennte Scopes,
  Lebensdauern, Zwecke. Der Guard gibt nie einen Token aus (Selbsttest I7).
- Der Issue-Body trägt nur technische Crash-Zusammenfassung (keine
  benutzerbezogenen Felder; weder die App noch der Watchdog überträgt PII).
  Vollständiger Stacktrace bleibt im privaten Sentry-Report (Permalink).