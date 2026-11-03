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

**CI-Workaround für Event-Tags (seit 26.10.2026):** dieses CI-Secret ist die
operative Quelle für die Event-Anreicherung (§3). Derselbe Token zieht pro
neuem Issue zusätzlich das Latest-Event — erst im CI-Lauf werden die Event-
Tags (`app.version_code`, `release`, `dist`) lesbar. Ein lokaler Token ohne
Mitgliedschaft in der Autoritäts-Org (`privat-jb`) scheitert bereits an der
Issues-Liste selbst (403 → SKIP); ohne Event-Leserecht fällt nur die
Anreicherung weich (Fallback-Zeile im Body, §5.1).

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
bash scripts/check_sentry_issues.sh --print-event-tags 7806859006,7806863798
                                            # read-only: Versions-Spur/Umfeld-Tags
                                            # einer bestehenden SID (kein GitHub-
                                            # Zugriff, keine Token-Ausgabe)
bash scripts/check_sentry_issues.sh --print-event-stack 7821630074,7821627754
                                            # read-only: Exception-/Thread-Kette
                                            # des Latest-Events (Typ/Wert, Frames
                                            # neueste zuerst, in_app markiert) —
                                            # der Pfad für §5.1, wenn der lokale
                                            # Token keine Event-Lesescope hat
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
- **Event-Anreicherung (Release-Spur, seit 26.10.2026):** pro **neuem**
  Kandidaten zusätzlich `GET /api/0/issues/{sid}/events/latest/` mit demselben
  Token. Die Tags `app.version_code`, `app.version`, `release`, `dist`, `os`,
  `device`, `environment` (plus `release`-Objekt als Fallback für
  release/dist) landen als `- **Release-Spur (Latest-Event):** …`-Zeile im
  Issue-Body — der versionCode der Crash-Sitzung ist damit direkt sichtbar;
  die manuelle Release-Fenster-Methode (§5.1) bleibt Fallback für versionlose
  ältere Events. Antwortet der Event-Call mit 401/403/000 oder ungültigem
  JSON, steht stattdessen eine Fallback-Zeile mit dem Statuscode im Body —
  die Issue-Erstellung selbst bleibt davon **nicht berührt** (Watchdog-
  Verdicts sind weiterhin keine Gate-Blocker). Der Lauf meldet am Ende
  `Event-Tags: N angereichert, M ohne Lesbarkeit`. In Fixture-Modus nur bei
  `event_<sid>.json` (200) bzw. `eventstatus_<sid>` (simulierter Code) —
  ohne beide Dateien wird der Event-Call offline gar nicht simuliert.
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
- **Read-only Event-Tag-Abfrage (`workflow_dispatch`-Input `event_tags`):**
  mit `-f event_tags=<sid>,<sid>` zieht derselbe Lauf (und dasselbe
  CI-Secret) die Latest-Event-Tags der genannten bestehenden SIDs und
  druckt pro SID eine `EVENT_TAGS <sid>: …`-Zeile ins Run-Log — der Leg-,
  der bestehende Befund-Issues vor ihrer Anreicherung entstanden sind (z.B.
  Build-Identität bei PR- vs. Store-Build, §5.1). Input läuft über Env
  (`EVENT_TAGS_INPUT`), kein `${{ }}`-Interpolieren im `run:`-Block.
  Ohne Input laufen Cron-Verhalten und Erstellung unverändert.
- **Read-only Stack-Abfrage (`workflow_dispatch`-Input `event_stack`):** der
  gleiche Lauf druckt pro SID die Exception-/Thread-Kette des Latest-Events
  (`EVENT_STACK <sid>` + Frames, neueste zuerst, `in_app` markiert). Das ist
  der Weg, die Attributionspflicht aus §5.1 zu erfüllen, wenn der lokale Token
  keine Event-Lesescope hat (403) — das CI-Secret hat sie.

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
   statt über Sentry-Tags. Seit der Event-Anreicherung (§3) steht der
   `app.version_code`-Tag neuer Watchdog-Issues direkt im Body
   (`Release-Spur (Latest-Event)`); die Fenster-Methode bleibt Fallback für
   versionlose Events und für Anreicherungs-Lücken.
2. **Doppel-Meldungen desselben Sentry-Issue verbinden:** Vor der Schließung
   `gh issue list --label sentry` nach gleichem Befund durchsuchen — identisch
   ist er nur bei gleicher SID (Body-Marker `vivid-sentry-issue: <id>`
   identifiziert das Sentry-Issue eindeutig); gleicher Top-Frame ist nur ein
   Suchschlüssel — ein Befund = ein Attributions-Thread.
   **Timber-Log-Signaturen sind eigene Sentry-Gruppen und werden nie als
   Duplikat geschlossen (verbindlich seit 2026-10-29, #335/#350):**
   `Timber.e(…)` erzeugt ein *error*-Event ohne Crash (§5.1); Sentry gruppiert
   es nach Fingerprint, nicht nach dem Meldungstext. Der „Top-Frame“ des
   Watchdogs ist nur die Log-Stelle — gleicher Top-Frame und sogar identischer
   Satz belegen deshalb **keinen** doppelten Befund. Belegter Fall: #335
   (VIVID-44, Marker `7811694380`, 18 Events, 2026-10-25,
   `0.5.30-beta+5304`) und #350 (VIVID-46, Marker `7812865315`, erstellt
   2026-10-26 mit 10 Events, letztes Event `0.5.48-beta+5484`) heißen beide
   `Idle camera: Configuration failed` und stammen aus derselben Log-Zeile
   (`idlePreviewCallbacks.onCameraError`) — zwei SIDs, zwei Gruppen. Welches
   Frame den Fingerprint trennt, ist aus dem Issue-Body nicht ablesbar (die
   Event-API antwortete 403); die Gruppierung entscheidet Sentry, nicht der
   Titel.
   **Regel:** gleicher Marker = ein Befund, wird in einen Thread konsolidiert.
   Verschiedene SIDs werden **nie** ineinander geschlossen — gleicher Titel,
   gleiche Log-Zeile und gleiches Gerät reichen nicht. Sie laufen als
   **Geschwister** (verlinkt, z. B. #350 ↔ #336/#325) mit je eigener
   Versions-Spur (§5.1) und je eigener Event-Zahl im Body.
   **Warum strikt:** der Watchdog dedupliziert gegen `state=all` (§3). Ein
   geschlossenes Issue unterdrückt jede weitere Meldung *derselben* SID, ohne
   dass ein neues entsteht — die Gruppe wird still, der nächste Rücklauf ist
   im Backlog unsichtbar. Dieses Risiko ist bei #335 eingetreten: am
   2026-10-26 als „duplicate of #336“ geschlossen, obwohl #335 eine eigene SID
   (VIVID-44) und eine andere Signatur als VIVID-43 trug — VIVID-44 ist damit
   für Rückläufe blind. Falsch als Duplikat geschlossene Gruppen werden
   deshalb wieder geöffnet statt über einen Nachbarn mitgeführt.
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

### 5a. ProGuard / R8 Keep-Rules für Gson und Request-Interface (Issue #320)

**Problem:** R8 kann Gson-Datenklassen und das Request-Interface nicht obfuscen,
weil Gson zur Laufzeit Klassen instanziiert und das Interface als Typ nutzt.
Das führt zu `JsonIOException: Abstract classes can't be instantiated!`
(Issue #320 / VIVID-3V).

**Lösung:** ProGuard-Keep-Rules in `app/proguard-rules.pro`:
- `-keep class com.vivid.core.network.obs.security.** { *; }` — schützt Security-Datenklassen
- `-keepclassmembers class com.vivid.core.network.obs.requests.Request { *; }` — schützt das Request-Interface
- `-keep class com.vivid.core.network.obs.requests.* { *; }` — schützt Request-Implementierungen

**Test:** `scripts/test_issue_320_proguard.sh` validiert die Anwesenheit dieser Keep-Rules im ProGuard-File.

**Docs-Update:** Die ProGuard-Regeln sind in der Issue-Description und im PARITY.md dokumentiert (siehe Abschnitt „Moblin-Features & Android-Plattform“).

6. **Schließung: GitHub-Issue manuell, Sentry-Issue per Bulk-PUT.** Beide
   Ebenen sind getrennt (siehe `docs/sentry-stats.md` §6): Sentry-Issues werden
   auf `resolved/inNextRelease` gesetzt, die GitHub-Issues des Befunds schließt
   niemand automatisch (der Watchdog legt nur an, er schließt nie; Historie:
   #215/#228 wurden manuell geschlossen). Verfahren: beim Release-Schnitt die
   erledigten Sentry-Issues per Bulk-PUT mit ihren IDs resolven (Rezept in
   `docs/sentry-stats.md` §6 Punkt 4; im Dashboard entspricht das „Resolve → in
   next release“), nach dem Stable-Publish das GitHub-Issue manuell schließen.
   **Ein `fix-release`-Tag ist dafür kein Weg:** Tags sind Event-Daten, Sentry
   erlaubt kein nachträgliches Setzen an einem bestehenden Issue (Befund
   01.11.2026, §6). Ein „resolved“-Vermerk in einem Attributionskommentar
   bezieht sich immer auf die Sentry-Ebene, nie auf den GitHub-Issue-Status.

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
   `io.ktor.client.plugins.ConnectTimeoutException` (#248),
   `java.io.EOFException` (#284, VIVID-3S).

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
   (#242, ein vorzeitiger Server-Abbruch kann ein Serverfehler sein). Der
   gefilterte `java.io.EOFException` (#284, VIVID-3S) wird bewusst **nicht** mit dem
   `ClosedReadChannelException` gleichgesetzt: der `EOF` ist der unvermittelte
   Transport-Abbruch ohne Close-Handshake (Gegenseite beendet, Netz weg) auf einem
   gefangenen Pfad und damit Umgebung; der protokollnahe Kanal-Schluss kann dagegen
   ein Serverfehler sein und bleibt sichtbar.

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

8. **Closure-Praxis für Single-Event-Validierungs-OOMs (seit 26.10.2026, #334/#337).** Die
   Einzelereignis-Fatals dieser Familie werden **ohne Fix** geschlossen — zulässig nur auf Basis
   einer unabhängigen Entkräftung der Release-Ebene (§5.1). Vier Bedingungen müssen **alle**
   zutreffen und im Closure-Kommentar einzeln benannt werden:
   - **(a) Genau ein Event am ganzen Build:** `firstEvent` == `lastEvent` == Crash-Zeitstempel
     (auf die Sekunde) — ablesbar aus der Releases-API, die auch ohne Event-Leserecht
     funktioniert. Die eine betroffene Nutzerin ist damit die einzige Session dieses Builds.
   - **(b) Schnitt→Event-Latenz von Minuten** am dokumentierten Validierungstag (z.B. sieben
     Releases in acht Stunden): Minuten nach dem Schnitt kann der Build keinen Feld-User
     erreicht haben (Play-Rollout braucht Stunden bis Tage).
   - **(c) Keine Wiederholung in allen folgenden Releases** — Fenster explizit nennen (z.B.
     elf Releases 0.5.33-beta→0.5.43-beta; Watchdog-Zähler stabil, neueste Releases event-frei).
   - **(d) Kein Fix vorhanden:** `git log --grep memory|oom|heap|leak` seit dem Fenster ist leer;
     der Kommentar sagt das ausdrücklich (Closure claimt nie „behoben“) und begründet, warum die
     Registry nach §5.3 nicht füllbar ist (keine Ursache, Top-Frame nur Allokationsort).
   Der Closure-Kommentar (englisch, Beleg-Format) nennt zusätzlich die **nicht** getanen Schritte:
   die Sentry-Gruppe bleibt `unresolved` (System der Record; das Sentry-Resolve nach §5.6 erst
   mit einem echten Fix), und falls Event-Tags nicht lesbar waren (403 lokal), steht das im
   Kommentar — die Schließung ruht auf der Release-Ebene, nicht auf Event-Tags.
   **Nachweis-Kette:** dieselbe Signatur aktualisiert nur die Sentry-Gruppe und wird in GitHub
   **nicht** neu angelegt (Dedup gegen `state=all`); eine **neue** Signatur legt der Watchdog
   automatisch als neues Issue an. Die Geräte-Ebene (Event-Tags via `--print-event-tags`, §2/§4)
   bestätigt die Lesart nachträglich, ist aber **keine** Voraussetzung — Beispiel: beide OOMs
   (#334/#337, versionCodes 5294/5324, Latenzen 3½ bzw. 6 Minuten) feuerten auf V2454A, dem per
   `com.vivid.debug`-Event nachgewiesenen Entwicklungsgerät.
   **Grenze (Gegenbeispiel #324/#325):** treffen die vier Punkte nicht zu — Quell-Release spannte
   13 Tage mit 6 Gruppen über die Feldwelt, Build-Identität also offen —, bleibt das Issue offen.
   Dieselbe Event-Tag-Abfrage liefert dann den Nachweis, der die Schließung entweder trägt
   (Test-Artfakt) oder verhindert (Feld-Build, wie bei #324/#325 belegt). Nicht anwendbar bei
   identifizierter Ursache (§5.3) und bei Probe-Issues mit 0 betroffenen Nutzern (§5.4).

9. **Kamera-Cluster `Camera no detected` attribuiert (01.11.2026, #325/#381/#382/#390).** Die
   Stack-Ebene, die §5.1 bisher fehlte, ist mit `--print-event-stack` (§2/§4) lesbar geworden.
   **Wurzel ist RootEncoder 2.7.5, nicht App-Code:** am Event `7822036893` bleibt die Kette lesbar
   — `CameraOpenException: Camera no detected` ← `getCameraIdForFacing` ←
   `getCameraIdForFacing$default` ← `getCameraResolutions` ← `prepareCamera`
   (`com.pedro.encoder.input.video`). `getCameraIdForFacing` wirft, wenn `CameraManager` fuer die
   angeforderte Blickrichtung keine Kamera-Id kennt — Geraete-/Umgebungsebene, alle Events
   `0.5.68-beta+5684` bzw. `0.5.74-beta+5746` auf V2454A, Android 16, `environment=production`.
   **App-seitig folgt daraus eine Falle:** der Wurf passiert in einem Task, den die Library auf
   ihren eigenen Handler postet (`run` → `Handler.handleCallback`), und damit **vor**
   `onConfigureFailed`. Das `try/catch` um `preview.prepareCamera(gl.surfaceTexture, 1280, 720, 30)`
   in `StreamingEngine.startIdlePreviewIfReady()` sieht ihn nicht, `onCameraError` ebenfalls nicht
   (der Callback laeuft erst nach einem Configure-Versuch) — der Wurf verlaesst den Looper und
   wird fatal. Deshalb ist dieser Cluster **nicht** ueber die #350-Mechanik abfangbar.
   `#382` ist die Log-Seite derselben Lage: ein Event **ohne** Exception, nur ein Thread-Eintrag
   von `main` mit `Thread.getStackTrace`-Frames in genau diesem geposteten Task — kein eigener
   Defekt, aber wegen eigener SID auch kein Duplikat (§5.2).
   **Nebenbefund #350 (`7812865315`, `0.5.65-beta+5654`):** der Stack zeigt `onConfigureFailed`
   einer `CameraConstrainedHighSpeedCaptureSession`. Der Report entsteht **nach** der
   Identitaetspruefung aus `27bcef79` — ein zurueckgezogenes Preview haette gar kein Event
   erzeugt. Der Fix wirkt also, die restlichen Events sind ein echter `CONFIGURE_FAILED`;
   auffaellig ist der High-Speed-Pfad, waehrend der Idle-Preview 30 fps anfordert (Diskrepanz
   offen, Next Step im Issue).
   **Attributionsgrenze, strukturell statt zufaellig:** `app/build.gradle.kts` laedt das
   ProGuard-Mapping nur mit gesetztem `SENTRY_AUTH_TOKEN` hoch
   (`autoUploadProguardMapping.set(System.getenv("SENTRY_AUTH_TOKEN") != null)`). Die Nightly-Builds,
   die die Feld-Events tragen, tun das nicht — deshalb bleiben App-Frames als `G`, `q0`, `d`, `u`,
   `l` stehen, obwohl die Events `r8-map-id-…`-Modul-Ids tragen. Auch der neue Stack-Modus kann das
   nicht aufloesen: **vor** jeder Methoden-Attribution muss das Mapping fuer den ausgelieferten
   Build vorhanden sein.
   **Behoben (01.11.2026), Ursache war die Verdrahtung, nicht das Plugin:** der Job
   `publish-play` in `release-pipeline.yml` baut das AAB fuer den Play-Kanal — also genau die
   Builds, die die Feld-Events tragen — und setzte `SENTRY_AUTH_TOKEN` nie. Im Release-Run
   41174214302 erschien `uploadSentryProguardMappingsStandardPlayRelease` ausschliesslich im
   Dry-Run-Selbsttest, nicht im Auslieferungspfad; der Kommentar im Fastfile behauptete das
   Gegenteil und nannte eine Workflow-Datei, die es nicht mehr gibt. Jetzt gilt: Token/Org/Project
   im Play-Step **und** im `build-release`-Step, ein Fail-closed-Block vor dem Upload (`::error::`
   + `exit 1`; ein leeres Secret zaehlt wie ein fehlendes) und der Gradle-Schalter prueft
   `!System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank()` statt nur `!= null` — ein gesetztes, aber
   leeres Env-Feld haette den Upload sonst ohne Credentials eingeschaltet. **Guard:**
   `scripts/check_sentry_mapping_upload.sh` (M1–M5 ueber `wf_step_env`/`wf_step_run`) mit
   Selbsttest `scripts/test_sentry_mapping_upload.sh` (T0–T7; u. a. vertippter Step-Name und
   abgeschalteter Schalter werden rot); beide laufen im Pre-Push-Gate und im CI-Job.

   **Gegencheck nach dem naechsten Nightly-/Stable-Build (messbar, nicht „sieht gut aus"):** Der
   Stack-Modus gibt je Event eine Zeile `EVENT_DEOBFUSCATION <SID>: mapped|obfuscated|no-app-frames`
   mit den Zaehlern `r8-map-id`-Frames / `com.vivid`-Frames aus — `mapped` beweist, dass Sentry das
   Mapping des Builds hat, `obfuscated` das Gegenteil, `no-app-frames` heisst „der Stack enthaelt
   ohnehin keinen App-Code" (dann sagt der Befund nichts ueber den Upload). Baseline der fuenf
   SIDs vom 31.10./01.11. (`7821630074`, `7821627754`, `7821592525`, `7822036893`, `7812865315`)
   ist `obfuscated` — **gemessen am 01.11.2026** (CI-Lauf 41281476277): `7821630074` 32 r8-Frames/132 gesamt,
   `7821627754` 5/13, `7821592525` 5/15, `7822036893` 7/15, `7812865315` 6/17, jeweils 0 `com.vivid`-Frames.
   Erwartet wird `mapped` fuer den ersten Nightly-/Stable-Build, der mit `SENTRY_AUTH_TOKEN` gebaut wurde —
   dieser Pfad braucht **keine** Play-Registrierung: ausgeliefert wird `assembleStandardRelease` als
   `app-standard-release.apk` im GitHub-Release (`nightly-*` bzw. Version-Tag), mit `mapping.txt` als Asset. Events aus aelteren Builds bleiben dauerhaft obfuscated — das ist kein Fehler,
   sondern die Grenze der nachtraeglichen Zuordnung. Der stille Skip hat im Build-Log den Fingerabdruck `skipping upload.` direkt nach
   `Task :app:uploadSentryProguardMappings<Variante>`.
   **Korrektur (01.11.2026):** Dieser Nachweis stammte aus dem **Play-Selbsttest (`dry_run`)** und sagt
   nichts ueber den ausgelieferten Build — dort lief die Variante `standardPlayRelease`, die fuer die
   Auslieferung gar nicht gebaut wird. Der Befund „Upload wird uebersprungen" ist damit fuer den
   Auslieferungspfad **nicht** belegt, nur fuer den Play-Selbsttest.
   Ein lokaler Gegenabgleich `sha256(mapping.txt)` gegen die r8-Id der Frames funktioniert nicht (Probe mit
   zwei veroeffentlichten Nightly-Mappings: kein Treffer) — die r8-Id ist keine einfache Datei-Pruefsumme.
   Verlaesslich ist allein die Klassifikation am Event (`EVENT_DEOBFUSCATION`).

   **Filter-Entscheidung (§5.7) — kein neuer Allowlist-Eintrag.** `CameraOpenException: Camera no
   detected` ist (a) **FATAL** (unbehandelter Wurf aus dem Looper) und damit nach Sicherheitsnetz 1
   des Transport-Filters tabu, und (b) kein erwarteter Netzwerk-Betriebszustand, sondern eine
   Geraetefrage, die als Crash sichtbar bleiben muss. Die Allowlist waechst weiterhin nur mit einem
   attribuierten Issue **und** einem Ereignis, das die drei Netze ueberhaupt passieren koennte —
   beides liegt hier nicht vor. Die Entscheidung ist damit dokumentiert, ohne Code zu aendern; der
   Beleg steht in diesem Abschnitt und in den englischen Kommentaren an #374/#381/#382/#390/#350.

### 5.10 Kamera-Klasse im Feld (31.10.–01.11.2026): eine Gerätequelle, kein Defekt

**Befund (01.11.2026, Abgleich der offenen Watchdog-Issues #362–#395):** 34 der
37 offenen Gruppen sind dieselbe Klasse — `Camera error (idlePreview=…): Open
camera … failed`, `pa0/ja0: Camera no detected`, `Idle camera: Configuration
failed`, `Camera recover exhausted (available:0)`. Alle tragen **eine** Nutzerin
und **ein** Gerät (`V2454A`, das dokumentierte Entwicklungsgerät), Level
`error` (kein Crash), Builds `0.5.46`–`0.5.74-beta`. Die Ausnahmen: VIVID-4T
(#374, `ApplicationNotResponding`, fatal, 1 Event), VIVID-43 (#336, 20 Events,
`CAMERA_DISCONNECTED`) und VIVID-46 (#350, 10 Events, Idle-Kamera-Config).

**Verdikt:** erwarteter Gerätezustand auf einem Gerät, dessen Kamera während der
Entwicklung von anderen Prozessen belegt/getrennt wird — kein App-Defekt, keine
Feldwirkung. Die Filter-Entscheidung aus §5.9 (kein Allowlist-Eintrag, die
Klasse bleibt sichtbar) wird **nicht** aufgeweicht: gefiltert würde nur die
Sichtbarkeit eines echten Kamera-Ausfalls verloren gehen.

**Schließung:** über `check_sentry_resolve.sh --ids …` (bzw. den Dispatch
`automation-sentry-resolve.yml`) mit Status `resolved` **ohne** `inNextRelease` —
die Gruppe bleibt damit offen für ein Wiederauftreten. GitHub-Issues werden
dabei **nicht** geschlossen (Automation schreibt nur die Sentry-API); sie
bleiben als Beleg stehen, bis sie manuell geschlossen werden.

**Offen bleibt VIVID-4T (#374, ANR fatal):** ein Einzel-Event, das inzwischen
eine begründete Zuschreibung hat — `main` wartet in
`Semaphore.acquireUninterruptibly()`, und die einzige bekannte Semaphore-Warte
dieser App liegt in RootEncoders `Camera2ApiManager.openCameraId` (§5.12 nennt
Belege und Grenze der Zuschreibung). Die Behebung ist seit 03.11.2026 im Code
(§5.13: Kamera-Open im Hintergrund-Scope), aber **nicht** auf einem Gerät
gegengeprüft — deshalb bleibt die Sentry-Gruppe offen, bis ein Nightly-Lauf
ohne ANR vorliegt.

### 5.11 `Unable to start service` (#327, VIVID-3Z): Fix war schon da, Test fehlte

**Befund (01.11.2026).** Die Gruppe VIVID-3Z (`RuntimeException: Unable to start
service com.vivid.irlbroadcaster.StreamingService ...`, fatal, 2 Ereignisse / 2
Nutzer, 22.10.) stand noch offen, obwohl der Fix am 23.10. mit `5fd40ac` landete —
`StreamingService.startAsForeground()` meldet seitdem per Boolean, ob der
Foreground-Zustand steht, und der Startpfad ist von einem `catch (Exception)`
umschlossen, das über `reportStreamStartFailure()` einen sichtbaren
`Failed`-Zustand schreibt. Der letzte Stack (`--print-event-stack 7806985927`)
bestaetigt den Pfad: `StreamingService.onStartCommand` → `StreamingEngine.startStream`
→ `startSourceStream` → `ScreenCaptureVideoSource.startStream`.

Die Behauptung des Tickets war also **nicht** mehr der Ist-Zustand — und der in
`PARITY.md` vermerkte Rest („kein Unit-Test fuer die Service-Klasse, dieser Pfad ist
nur per Kompilat belegt") haette den Fix bei der naechsten Umstellung wieder
unbemerkt zurueckdrehen lassen.

**Was jetzt gilt.** Die Start-/Fehler-Klammer liegt als reine Funktion in
`StreamingServiceSupport.guardStreamStart(log, start, onFailure)`: eine Exception
aus dem Start verlaesst den Service nicht mehr, sondern landet im Log und im
Engine-Zustand; der Aufrufer raeumt auf (`teardown()`, `START_NOT_STICKY`).
Gefangen wird bewusst nur `Exception` — ein `Error` (z. B. `OutOfMemoryError`)
bleibt beim Crash-Reporting und wird nicht als „Streamstart fehlgeschlagen"
verkleidet.

**Beleg.** `StreamingServiceSupportTest` prueft drei Faelle: Exception → `false`,
Log + `onFailure` je genau einmal, kein Entweichen; Erfolg → `true`, keine
Meldung; `OutOfMemoryError` → propagiert unveraendert, `onFailure` bleibt stumm.
Mutationsprobe (01.11.2026): `onFailure()` im Guard entfernt → genau dieser Test
rot (`guardStreamStart reports a start failure without letting it escape`,
`BUILD FAILED in 27s`); zurueckgebaut → `BUILD SUCCESSFUL in 25s`.

**Status der Gruppe.** VIVID-3Z (`7806985927`) wird nach dem Beleg resolved
(`--ids`, ohne `inNextRelease`): die Codebasis enthaelt den Fix, aber der
gemeldete Build liegt davor — ein erneutes Auftreten soll die Gruppe wieder
oeffnen, statt sie als „in der naechsten Version behoben" stillzustellen.

**Kamera-Cluster `Camera no detected`: Pre-Flight-Pruefung (02.11.2026, #325/#381/#385/#390/#392).**
Aus §5.1 Punkt 9 folgt, dass der fatale Wurf **app-seitig nicht abfangbar** ist, solange
`prepareCamera` ueberhaupt aufgerufen wird — der einzige saubere Hebel ist, den Aufruf zu
vermeiden. `StreamingEngine` prueft deshalb jetzt vor beiden Kamera-Pfaden, ob es die
angeforderte Kamera gibt (`cameraAvailability`, injizierbar wie `idlePreviewFactory`):

- **Idle-Preview:** `startIdlePreviewIfReady()` prueft `camera?.currentCameraId` (leer =
  Rueckkamera) **vor** dem GL-Aufbau; fehlt die Kamera, wird die Vorschau gar nicht erst
  aufgebaut (`Timber.w`, kein Crash).
- **Stream/Recording:** `startCameraStream()` prueft dieselbe Id vor
  `prepareStreamEncoders()` und endet bei fehlender Kamera in
  `StreamingState.Failed(streaming_error_camera_unavailable)` — ein lokalisierter Fehler
  (de/en/fr/ru) statt eines Fatals.

`defaultCameraAvailable` liest `CameraManager.cameraIdList` (+ `LENS_FACING` fuer die
Rueckkamera); ist die Liste selbst nicht lesbar, bleibt das **Bestandsverhalten** (fail-open) —
die Pruefung darf funktionierende Geraete nicht blockieren.

**Beleg.** Neu `StreamingEngineCameraGuardTest` (3 Tests): Idle-Preview baut ohne Kamera nichts
auf (Factory-Zaehler 0), Streamstart endet in `streaming_error_camera_unavailable` ohne
`prepareVideo()`/`startStream()`, und eine vorhandene Kamera nimmt den normalen Pfad
(Negativkontrolle). **Mutationsprobe (02.11.2026):** beide Guards per `false &&` deaktiviert →
`3 tests completed, 2 failed` (die Negativkontrolle blieb gruen), zurueckgebaut → gruen.

**Grenze der Behebung.** Adressiert ist die Familie, in der die Kamera **nicht existiert**
(`Camera no detected`, 16+ Events, fatal). Nicht adressiert: `Open camera N failed`
(Kamera existiert, Oeffnen scheitert — eigener Cluster, Retry-Politik aus `27bcef79`) und die
offene #350-Diskrepanz (High-Speed-Session vs. 30-fps-Idle-Preview). Die Issues bleiben bis
zum Nachweis im naechsten Nightly offen; ein Kommentar mit diesem Befund haengt an der Familie.

### 5.12 `Open camera … failed` (#371–#399): zwei Wurfstellen, ein ANR-Beleg, zwei Änderungen

**Befund (02.11.2026, RootEncoder 2.7.5 dekompiliert).** Die Familie hat
**zwei** Meldungen, die wie eine aussehen, aber aus verschiedenen Stellen
kommen:

- `Open camera <id> failed` — der **synchrone** Wurf in
  `Camera2ApiManager.openCameraId()`. Nach `cameraManager.openCamera(id, this,
  cameraHandler)` blockiert dieselbe Methode auf dem **aufrufenden Thread** in
  `semaphore.acquireUninterruptibly()`; der `catch` verpackt jede Exception
  (typisch `CameraAccessException: CAMERA_IN_USE`/`MAX_CAMERAS_IN_USE`) in
  diese Meldung.
- `Open camera failed: <n>` — der **asynchrone** Pfad über
  `Camera2ApiManager.onError(device, error)`. Er schließt das Gerät, gibt das
  Semaphor frei und meldet `error` als Zahl; `4` ist
  `ERROR_CAMERA_DEVICE`, d. h. der Kamera-HAL des Geräts, nicht die App.

**ANR-Beleg (VIVID-4T, #374) — Zuschreibung, kein Beweis.** Der gespeicherte
Event-Stack ist eindeutig auf der Wartestelle: `main` → `park` →
`LockSupport.park` → `AbstractQueuedSynchronizer.acquireShared` →
`Semaphore.acquireUninterruptibly` → obfuskierte App-Frames, ohne
`InputEventReceiver`/`ViewRootImpl`/`Choreographer` (also kein Input- und kein
Render-Problem). Dieses Repository konstruiert selbst **kein** `Semaphore`
(`git grep` leer), der Wartepunkt gehört also einer Abhängigkeit. RootEncoder
2.7.5 ist in dieser App die einzige bekannte Stelle, die ein Semaphor
`acquireUninterruptibly` nimmt — in `Camera2ApiManager.openCameraId()`
unmittelbar nach `cameraManager.openCamera(...)`. Damit ist die Kette
(`StreamingService.onStartCommand` → `startStream` → Kamera-Open auf dem
Main-Thread) die plausibelste Erklärung, aber der Stack bleibt wegen der
fehlenden ProGuard-Mapping-Uploads (`autoUploadProguardMapping` nur mit
`SENTRY_AUTH_TOKEN`) bis auf Methodenebene unlesbar. Die Doku führt die
Zuschreibung deshalb als begründete Hypothese; bewiesen wird sie erst mit
einem gemappten Event oder einer Reproduktion.

**Zweiter Kamera-Open im Fehlerzweig.** Der alte Fehlerzweig rief nach
`failStream()` sofort `startIdlePreviewIfReady()` — ein zweiter
`openCamera`-Versuch im selben Aufrufzug, ohne dass dem Kamera-Server Zeit
bleibt, das Gerät nach dem Encoder-Teardown freizugeben. Ob er gelingt, ist ein
Wettlauf; scheitert er, entsteht ein zusätzlicher Fehlerpfad (im
Vorschau-Callback als eigenes `Idle camera: …`-Event). Der zweite Öffnungsversuch
war damit selbst eine Fehlerquelle, die vom eigentlichen Startfehler nicht zu
unterscheiden ist.

**Was jetzt gilt.**

1. **Verzögerte Vorschau-Wiederherstellung mit Budget** (`schedulePreviewRecovery()`,
   `RECOVERY_PREVIEW_DELAY_MS = 400`): kein Kamera-Open mehr im Fehlerzweig,
   genau ein Wiederaufbau pro Fehlerfall, und der Aufbau unterbleibt, wenn
   inzwischen ein neuer Start läuft (`Preparing`/`Streaming`) oder die Quelle
   nicht mehr `CAMERA` ist. 400 ms sind lang genug, damit der Kamera-Server
   das Gerät freigibt, und kurz genug, dass die Vorschau ohne sichtbare Pause
   zurückkommt.
2. **Pfad-Angabe und Umgebungs-Kontext am Fehler** (#371/#375/#376/#382
   unterschieden `idlePreview=true|false`, im Code war der Live-Pfad aber
   stumm — nur der Vorschau-Callback loggte `Idle camera: %s`). Neu:
   `CameraErrorPath.STREAM`/`IDLE_PREVIEW` in der Fehlerzeile
   (`Camera error (stream): …`) und eine Kontextzeile mit Pfad, Kamera-Id,
   verfügbaren Ids, gleichzeitigen Kamera-Gruppen
   (`CameraManager.concurrentCameraIds`) sowie Streaming-/Recording-Zustand.

   Die Kontextzeile geht bewusst über `Timber.w`, nicht über `Timber.e`:
   `sentry-android-timber` 8.58 setzt `minEventLevel = ERROR` und
   `minBreadcrumbLevel = INFO` (bytecode-verifiziert an
   `SentryTimberIntegration`). Damit landet der variable Teil als Breadcrumb am
   nächsten Fehler-Event und im In-App-Log, ohne pro Kamera-Id eine neue
   Sentry-Gruppe anzulegen. Die Fehlerzeile selbst bleibt stabil; die Id steht
   ohnehin in RootEncoders eigener Meldung.

   **Nebenbefund für die Triage:** die Zeichenkette
   `Camera error (idlePreview=…)` existiert in Code und Git-Historie **nicht**
   (`git log -S` über alle Branches leer) und wird auch vom Watchdog nicht
   erzeugt. Die Gruppentitel stammen also aus Sentry (Umbenennung oder eigener
   Fingerprint); verbindlich für die Zuordnung ist `metadata.value`
   (`Open camera … failed`).

**Beleg.** 10 neue Tests: `CameraErrorDiagnosticsTest` (4, Format und
Leerfälle), `StreamingEngineCameraRecoveryTest` (4, kein Open im Fehlerzug,
genau eine geplante Aktion, Budget, Inertheit bei laufendem Stream),
`StreamingEngineCameraContextTest` (2, Kontext + Fehlerzeile je Pfad über einen
`Timber`-Recording-Tree). Mutationsproben (02.11.2026):
`schedulePreviewRecovery()` → `startIdlePreviewIfReady()` ⇒ alle vier
Recovery-Tests rot, die Kontext-Tests grün; Kontext-Zeile aus
`reportCameraError()` entfernt ⇒ genau der Live-Pfad-Test rot, der
Vorschau-Test (der seine Zeile separat setzt) grün; jeweils zurückgebaut →
`TESTS_RC=0`.

**Grenze der Behebung.** Der `openCamera`-Aufruf läuft weiter auf dem
aufrufenden Thread — im Service-Start ist das der `main`-Thread. Die Semaphore
kann dort also weiterhin blockieren, wenn `openCamera` zwar zurückkehrt, aber
weder `onOpened` noch `onError` kommt. Diese Grenze ist mit §5.13 aufgehoben:
der Open läuft seit 03.11.2026 im seriellen Hintergrund-Scope.

### 5.13 Kamera-Open verlässt den Main-Thread (A1, 03.11.2026)

**Was geändert ist.** Zwei Stellen riefen RootEncoder so auf, dass der
Semaphor-Wartepunkt aus §5.12 auf dem Aufrufer-Thread lag:

- **Idle-Vorschau:** `startIdlePreviewIfReady()` rief `prepareCamera(...)` und
  `openCameraId(...)` direkt im Aufruf (Main, sobald die SurfaceView angehängt
  wird).
- **Stream/Recording:** `startCameraStream()` rief `prepareStreamEncoders(cam)`
  direkt im Aufruf, und der kommt beim Start aus dem Service
  (`StreamingService.onStartCommand`, Main) oder aus dem UI-Button.

Beides läuft jetzt über `launchCameraOpen` in den vorhandenen seriellen
`captureScope` (`Dispatchers.IO.limitedParallelism(1)`); nur der Abschluss
(Vorschau anhängen, Controls, `cam.startStream`) kommt per `runOnMain` zurück
auf den Main-Thread. Seriell deshalb, weil zwei gleichzeitige Öffnungsversuche
denselben Fehler erzeugen würden, den §5.12 beschreibt.

**Neue Guards, die der asynchrone Open erst nötig macht.** Vorher blockierte
der Open den Main-Thread und schloss diese Lücken von selbst:

- `legacyIdlePreviewAllowed()` verlangt zusätzlich
  `_streamingState !is Preparing/Streaming`. Sonst würde die Vorschau während
  eines laufenden Starts dieselbe Kamera anfordern (Doppel-Open).
- `completeCameraStart()` bricht ab, wenn der Zustand nicht mehr `Preparing`
  ist. Während des Hintergrund-Opens kann der Nutzer gestoppt haben; ohne den
  Guard hätte der Abschluss den Stream trotzdem gestartet.
- `openIdleCamera()` prüft `idlePreviewCamera === preview` vor dem Öffnen: ein
  inzwischen ersetzter Aufbau darf nicht doch noch öffnen.

**Beleg.** Neu `StreamingEngineCameraThreadTest` (5 Tests): der Open läuft
nachweislich auf einem anderen Thread als der Aufrufer (Produktions-Default,
gemessen an `Thread.currentThread()` in `prepareVideo`), der Zustand steht
sofort auf `Preparing` (die UI wartet nicht), zwei Starts erzeugen genau einen
Öffnungsversuch, ein Abschluss nach `stopStream()` startet keinen Stream, und
die Vorschau bleibt während `Preparing` geschlossen. Die bestehenden
Engine-Tests setzen `launchCameraOpen`/`runOnMain` auf Inline-Ausführung, damit
sie dieselbe Zustandsfolge sehen wie vorher; `scheduleRecoveryPreview` wird
dort ebenfalls gestubbt (Produktions-Default ist `Handler.postDelayed`, in der
JVM ein werfender Stub). **Mutationsproben (03.11.2026):**
`launchCameraOpen` inline → Thread-Test rot (1/5); `Preparing`-Guard in
`legacyIdlePreviewAllowed` entfernt → Vorschau-Test rot (1/5);
Abbruch-Guard in `completeCameraStart` entfernt → Stop-Test rot (1/5); jeweils
zurückgebaut, Modul-Tests grün.

**Grenze.** Der Open ist damit vom Main-Thread weg, aber nicht bewiesen ist,
dass der ANR *dieser* Semaphor war (§5.12). Auf einem Gerät ist die Änderung
nicht gegengeprüft: die Verlagerung von `prepareCamera`/`prepareVideo` in einen
Hintergrund-Thread ist für RootEncoder 2.7.5 nicht dokumentiert, die
GL-Anhänge- und Control-Aufrufe bleiben aber weiterhin auf Main. Wenn der
Gerätetest eine Abweichung zeigt (z. B. schwarze Vorschau beim ersten
Anhängen), ist die Rücknahme klein: `launchCameraOpen` auf
`{ work -> work() }` stellen.

## 6. Sicherheit

- Token getrennt vom CI-Mapping-Token (`auth.token`) — getrennte Scopes,
  Lebensdauern, Zwecke. Der Guard gibt nie einen Token aus (Selbsttest I7).
- Der Issue-Body trägt nur technische Crash-Zusammenfassung (keine
  benutzerbezogenen Felder; weder die App noch der Watchdog überträgt PII).
  Vollständiger Stacktrace bleibt im privaten Sentry-Report (Permalink).