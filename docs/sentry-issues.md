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
5. **Zuweisen erst beim Beginnen der Arbeit (seit 03.10.2026, #260).** Neue
   Issues entstehen **unzugewiesen** — betrifft Attributions-Threads,
   Triage-Follow-ups und Task-Issues gleichermaßen. Das ist Absicht: ein
   Issue ohne Assignee ist das Signal „frei“ und damit die Grundlage dafür,
   dass sich Contributors offene Issues heraussuchen können. Erst wenn mit
   der Umsetzung tatsächlich begonnen wird, wird das Issue übernommen:
   `gh issue edit <nr> --add-assignee thoser666` — **nicht** `--assignee`
   beim `gh issue create`. Erledigt = geschlossen; ein offenes Issue ohne
   Assignee ist ausdrücklich **kein** Mangel. (Die frühere Regel — Zuweisung
   schon beim Anlegen, Hausmuster aus #215/#225 — war das Gegenteil und hat
   durch die Vorabreservierung jede Selbstbedienung blockiert.)
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
1 Event) — attribuiert und geschlossen. Dieselbe Fehlerklasse wie diese
Nicht-Crash-Befunde, aber jetzt mit Ursachenbehebung statt wiederholter
Triage: #267 ist in Punkt 7 beschrieben.

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

7. **Erwartete Netzwerk-Betriebszustände sind kein Meldegrund** (seit 05.10.2026,
   #267 / Sentry VIVID-3P). Die Befunde #217/#218/#219/#224/#242/#244/#248 waren
   allesamt **error**-Events auf gefangenen Pfaden und wurden korrekt als „kein
   Defekt" geschlossen — aber sie waren damit die fünfte bis zwölfte Wiederholung
   desselben Zustands, und der Watchdog legt für jede Wiederholung ein neues
   Issue an. „Kein Defekt" beantwortet die Frage, ob ein **Fehler** vorliegt, nicht
   die Frage, ob der Zustand **meldewürdig** ist; beides zu trennen ist Aufgabe des
   Meldepfads, nicht der Triage.

   **Wirkort:** `SentryTransportFilter.kt` (`app`) filtert im `beforeSend`-Callback
   — verkettet **hinter** dem Opt-out (`applySentryOptOut`), beide Vorgänge bleiben
   erhalten. Ein Event fällt nur durch, wenn **alle** drei Netze greifen:

   1. Das Level ist **explizit gesetzt** und **nicht** `FATAL`. Fehlt das Level
      (`null`), bleibt das Event stehen: ohne gesetztes Level lässt sich nicht
      ausschließen, dass es doch ein FATAL-Crash ist.
   2. Die **komplette** Cause-Kette besteht aus erlaubten Transporttypen. Ein
      einzelner unbekannter Typ lässt das Event durch.
   3. Es ist überhaupt ein Throwable vorhanden.

   **Allowlist** (jeder Eintrag durch ein attribuiertes Issue belegt, die Liste
   wächst nur mit einem echten Issue als Beleg): `java.net.NoRouteToHostException`
   (#267), `java.net.ConnectException` (#217), `java.net.SocketTimeoutException`
   (#219/#224/#244), `java.net.UnknownHostException` (#220),
   `io.ktor.client.plugins.ConnectTimeoutException` (#248).

   **Zwei Fallen, die den Filter still wirkungslos machen würden** — beide am
   Sentry-Artefakt verifiziert, beide in Tests festgenagelt:

   - **`SentryEvent.getExceptions()` ist auf dem `beforeSend`-Pfad `null`.** Auch
     nach `setThrowable` — die Kette entsteht erst bei der Serialisierung. Eine
     Policy, die `exceptions` auswertet, erkennt nichts und lässt das
     Issue-Rauschen scheinbar verschwunden sein. Die Policy läuft über
     `getThrowable()` (vererbt aus `SentryBaseEvent`), das die Kette trägt.
   - **`Mechanism.isHandled()` taugt nicht als Kriterium.** Der vom SDK beim Init
     gepflanzte `SentryTimberTree` ruft `captureEvent` **ohne** `Mechanism`; für
     `captureEvent` gilt die Sentry-Konvention `handled=false`. Eine Regel
     „unbehandelt → behalten" behielte damit jedes `Timber.e`-Event — das Filterziel
     wäre exakt verfehlt. Getrennt wird stattdessen über das Level: der Tree mappt
     Timber `ERROR` (Priorität 6) auf `SentryLevel.ERROR`, nur `ASSERT` (7) auf
     `FATAL`.

   **Basisklassen sind verboten.** `BindException` (#222/#228, VIVID-37/3E, echter
   Start-Crash, Registry `REMOTE-EADDRINUSE-STARTUP`) ist Geschwister der hier
   gefilterten Typen — alle vier erben von `java.net.SocketException`. Ein Abgleich
   auf die Oberklasse würde den echten Crash verschlucken; deshalb wird
   ausschließlich der **exakte** Typ verglichen. Ebenfalls außen vor: der
   `JobCancellationException` (#218, verschluckt echte Coroutine-Bugs, weil fast
   jede abgebrochene Koroutine damit endet) und der `ClosedReadChannelException`
   (#242, ein vorzeitiger Server-Abbruch kann ein Serverfehler sein).

   **Die beforeSend-Kette haengt an der Opt-out-Fabrik (R8-Vorbedingung, neu als
   C0 im `check_sentry_optout_mapping.sh`).** Die Transport-Filter-Fabrik ruft
   `sentryBeforeSendCallback` **auf**, statt den Opt-out inline nachzubauen. Der
   Grund ist nicht Geschmack: C3–C6 weisen die Opt-out-Fabrik per R8-Mapping
   nach, und dieser Nachweis traegt nur, solange genau diese Fabrik den
   Aufrufpfad bildet. Wuerde `VividApplication` sie durch einen eigenen Callback
   ersetzen, haette sie im Release-Build keinen Aufrufer mehr — R8 entfernte sie
   und der Nachweis schlaege erst beim naechsten Release-Build fehl, also lange
   nach der Ursache. C0 prueft die Komposition deshalb **quellenseitig** und ohne
   Mapping, laeuft ueber `--composition-only` und ist im Pre-Push-Gate hart
   verdrahtet, waehrend C2–C6 ohne Release-Build weich bleiben duerfen.

   **Nebenbefund an C5, am echten Mapping gemessen (06.10.2026):** C5 suchte
   bisher einen `io.sentry.SentryClient`-Record im geteilten Inline-Range. Mit der
   neuen Fabrik legt R8 den Aufrufer als synthetische Lambda-Bruecke an
   (`…$$ExternalSyntheticLambda0.execute(io.sentry.SentryEvent, io.sentry.Hint)`),
   worauf der alte Check an beiden Release-Kanaelen **hart rot** schlug — bei
   nachweislich vorhandener Logik (`sentryBeforeSendCallback`, sein Lambda,
   `classifySentryEvent` und `throwableTypeChain` stehen alle im Mapping). C5
   prueft deshalb jetzt die **vom SDK festgelegte Aufrufsignatur
   `(SentryEvent, Hint)`** statt eines Klassennamens, den R8 frei waehlt; der
   Stub im Selbsttest bildet genau diese gemessene Form ab. Das Verhalten bei
   weiterer R8-Drift bleibt hart, nicht still: ein Layout, das die Signatur nicht
   mehr traegt, faellt durch.

   **Richtung des Fehlverhaltens:** Der Filter darf zu **wenig** durchlassen —
   trifft die Kette einen Ktor-Wrapper, der nicht in der Allowlist steht, bleibt
   das Event stehen und der Watchdog meldet es weiter. Zu **viel** durchzulassen
   (ein echter Befund verschwindet) ist das schlimmere und laut Doku-Default nicht
   vertretbar. Für #267 war die tatsächliche Kette am Event nicht lesbar (lokaler
   Token ohne `project:read`, siehe §1), der Filter ist deshalb bewusst
   konservativ kalibriert und der Befund unten nennt diese Lücke ausdrücklich.

## 6. Sicherheit

- Token getrennt vom CI-Mapping-Token (`auth.token`) — getrennte Scopes,
  Lebensdauern, Zwecke. Der Guard gibt nie einen Token aus (Selbsttest I7).
- Der Issue-Body trägt nur technische Crash-Zusammenfassung (keine
  benutzerbezogenen Felder; weder die App noch der Watchdog überträgt PII).
  Vollständiger Stacktrace bleibt im privaten Sentry-Report (Permalink).