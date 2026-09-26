# Sentry-Event-Statistiken abfragen (Lese-Token + Guard)

Der CI-Token in `sentry.properties` hat bewusst nur den Scope `org:ci`
(ProGuard-Mapping-Upload). Er kann **keine** Events/Statistiken lesen —
API-Abrufe antworten korrekt mit `403`. Für Dashboard-Statistiken (Events in
30 Tagen, Quota-Drops) gibt es einen separaten, kurzlebigen **User-Token**.

## 1. Token erstellen (Sentry Web-UI, ~2 Minuten)

1. <https://sentry.io/settings/account/api/auth-tokens/> öffnen
   (Account-Settings → Auth Tokens → **Create New Token**)
2. Token-Typ: **User Token** (nicht Internal Integration — der braucht
   Org-Owner-Rechte und ist dauerhaft)
3. Scopes **minimal** setzen:
   - `project:read` (Projekt-Details, Projekt-ID)
   - `org:read` (Organisations-Stats-Summary — der Quota-/Outcome-Abruf
     läuft über `/organizations/{org}/stats-summary/`)
   - `event:read` nur falls vorhanden (harmlos, 403-Hinweis deckt es ab)
   - nichts weiter (kein `project:write`, kein `org:write`)
4. Ablaufdatum: **30 Tage** (Sentry-Default) — bewusst kurzlebig; nach
   Ablauf einfach neu erstellen (dieser Guard fällt dann auf SKIP zurück).
5. Token kopieren (Form `sntrys_…`) — er wird nur einmal angezeigt.

## 2. Nutzung (lokal)

Entweder als Env-Var (Session-only):

```bash
SENTRY_STATS_TOKEN=sntrys_… bash scripts/check_sentry_stats.sh
```

Oder dauerhaft lokal in der gitignoreden `sentry.properties` (Projektstamm)
als eigene Zeile — **nicht** `auth.token` überschreiben (das ist das
CI-Mapping-Token):

```properties
stats.token=sntrys_…
```

Quellen-Reihenfolge: `SENTRY_STATS_TOKEN` → `stats.token` → `auth.token`
(CI-Token, meist ohne Lesescopes). `--print-token-source` meldet nur die
gewählte Quelle, nie den Token.

Ausgabe (Verdicts):

```
OK: 42 Event(s) in 30d angenommen, 0 verworfen          # Pipeline lebt
OK: 0 Events in 30d (Projekt erreichbar, ruhig)         # ruhig, aber erreichbar
WARN: 42 …, 7 verworfen (Quota-/Ratenlimit-Signal …)    # Drops → Usage prüfen
Drops-Split: rate_limited=5, client_discard=2           # Ursachen-Aufschlüsselung
SKIP: Token ohne Lesescopes — project:read + event:read nötig (docs/sentry-stats.md)
```

Bei WARN folgt auf die Zusammenfassung eine **Drops-Split**-Zeile mit den
einzelnen Outcome-Kategorien (nur Kategorien mit Werten größer 0):
`rate_limited` (inkl. Legacy-Aliase `rejected`/`rateLimited`), `invalid`,
`client_discard`, `abuse`, `cardinality_limited`. `filtered` zählt bewusst
NIE dazu (projektkonfiguriertes Inbound-Filtering ist kein Quota-Signal).
Das erlaubt die gezielte #210-Diagnose: `rate_limited` = Quota/Ratenlimit
(→ Sentry-Usage prüfen oder Sampling senken), `client_discard` = Client hat
Events selbst verworfen (z. B. zu groß, span/outcome), `invalid` = malformed
Events.

Exit-Codes: `0` für OK/WARN/SKIP (bewusst kein Gate-Blocker), `1` nur bei
unverständlicher API-Antwort (Feldformat geändert → fail-closed).

Ohne `SENTRY_STATS_TOKEN` fällt der Guard auf das CI-Token aus
`sentry.properties` zurück (SKIP mit Scopes-Hinweis, wenn es keine
Lesescopes hat — der Normalfall).

## 3. In CI verwenden (wöchentlicher Ops-Review-Workflow)

Der Workflow `.github/workflows/automation-sentry-ops.yml` führt Stats-Guard
**und** Health-Probe wöchentlich aus (Cron: montags 06:30 UTC, plus
`workflow_dispatch`; er ersetzt den früheren monatlichen Stats-Review) und
verwaltet die Ergebnisse als deduplizierte Issues:

- **WARN** (Stats: Quota-Drops — oder Health: Rate-Limit-Header /
  HTTP 429) → Issue mit Guard-Ausgabe und Run-Link (kommentiert statt neu,
  solange eins offen ist). Seit #210 trägt die WARN-Ausgabe zusätzlich die
  **Drops-Split**-Zeile (Ursachen je Outcome-Kategorie) — die Diagnose, ob
  das Quota/Ratenlimit (rate_limited) oder Client-seitige Verwerfungen
  (client_discard/invalid) die Ursache sind, steht damit direkt im Issue.
- **FEHLER / Konfigurations-SKIP** (Token fehlt, ungültig, ohne
  Lesescopes, API-Format geändert bzw. HTTP 400/401/403 am Ingest) →
  Konfigurations-Issue mit Behebungs-Hinweis
- **Konfigurations-SKIPs im Detail:** Der Issue-Step erkennt diese
  Guard-Texte als Konfigurationsproblem (Needle-Liste): `kein
  Sentry-Lese-Token` (404), `Token ungültig/abgelaufen` (401), `Token ohne
  Lesescopes`, `Token ohne event:read-Scope` (403) und `unerwarteter
  HTTP-Status` (400/404/500 — API-/Endpoint-Bruch). Nur diese öffnen/
  kommentieren Issues — ein SKIP-Variantentext, der nicht in der Liste
  stünde, würde fälschlich wie ein Erfolg auto-geschlossen (Fix: Issue
  #203, W8-Testschutz). Der Stats-Abruf nutzt den **Org-Outcome-Summary-
  Endpoint** (`/organizations/{org}/stats-summary/?field=sum(quantity)` —
  `accepted`/`rate_limited` je Kategorie als Quota-Signal); der frühere
  Legacy-Endpoint `/organizations/{org}/events/` antwortet inzwischen mit
  HTTP 400.
- **OK oder neutraler Netzwerk-SKIP** („Sentry nicht erreichbar“) → offene
  Check-Issues schließen sich automatisch

Das Secret `SENTRY_STATS_TOKEN` ist dafür einmalig zu hinterlegen
(User-Token aus Abschnitt 1, Scopes `project:read` + `event:read`): solange
es fehlt, läuft der Workflow trotzdem und hält per Konfigurations-Issue die
Erinnerung am Leben — stillem Versagen ist damit vorgebaut.

## 4. Health-Probe (Ingest lebendig?)

Neben der Statistik-Abfrage prüft `scripts/check_sentry_health.sh` die
andere Richtung: Ist der **Ingest-Endpunkt** des DSN-Projekts erreichbar und
wird ein minimal gültiges Probe-Event akzeptiert, ohne gedrosselt zu werden?

```bash
bash scripts/check_sentry_health.sh          # SKIP (opt-in-Vertrag)
bash scripts/check_sentry_health.sh --live   # echte Probe (POST an den Ingest)
bash scripts/check_sentry_health.sh --print-envelope   # Struktur ohne POST
bash scripts/check_sentry_health.sh --print-dsn        # KEY/HOST/PROJ aus dem Manifest
```

Verdicts: `OK` (HTTP 200, keine Rate-Limit-Header), `WARN` (200 mit
`X-Sentry-Rate-Limits`/`Retry-After`, oder 429 — Quota-/Ratenlimit-Signal),
`FEHLER` (400/401/403 — DSN-/Envelope-Problem, exit 1), `SKIP` (Netzwerk/
unerwarteter Status, exit 0).

**Bewusst opt-in:** Die DSN steht im öffentlichen Manifest — automatische
Proben bei jedem Push würden das Dashboard mit Probe-Events zumüllen. Das
Pre-Push-Gate prüft deshalb nur die Envelope-Struktur offline
(`test_sentry_health.sh`, HP1–HP9, inkl. Header-Injection-Probe). Die
Live-Probe bleibt ein Werkzeug mit Bedacht: Der **wöchentliche
Ops-Workflow (Abschnitt 3) ist der sanktionierte Cron-Nutzer** — genau eine
Probe pro Woche, disziplinierte Tag-Auswertung (Probe-Events tragen
`tags.probe=health-check` und environment `health-probe`, um sie im
Dashboard filtern/löschen zu können). Häufigere eigene Proben sind nicht
nötig — der Workflow meldet Drossel-Signale von selbst als WARN-Issue.

## 5. Sicherheit

- Der User-Token ist **kein Repo-Secret-Ersatz** für den Mapping-Upload —
  getrennte Tokens, getrennte Scopes, getrennte Lebensdauern.
- Token nie in Logs/Screenshots; der Guard gibt das Token nie aus
  (Selbsttest S7 prüft das).
- Projekt: `privat-jb/vivid` (Projekt-ID `4509837327990784`, DSN im
  App-Manifest). Der Guard liest die ID selbst aus der Projekt-API.

## 6. Erledigte Issues automatisch resolven (fix-release-Tag, Weg 3)

Erledigte („fix in nächster Version") Sentry-Issues werden beim **Stable-Publish
einer Version automatisch geschlossen** — ohne manuelles Durchklicken im
Dashboard. Mechanik:

1. Im Sentry-Dashboard an das Issue ein **Tag** hängen:
   `fix-release: <version>` — der Wert ist die Version **ohne führendes `v`**,
   case-insensitiv (z. B. `fix-release: 0.6.0-beta` für den Release-Tag
   `v0.6.0-beta`, `fix-release: 0.6.0-beta` und `0.6.0-BETA` zählen gleich).
2. Der wöchentliche Stable-Workflow (`.github/workflows/distribution-stable.yml`)
   ruft nach dem Publish des Versions-Tags den Resolve-Guard auf:
   `bash scripts/check_sentry_resolve.sh --version "$TAG"` (Step „Sentry:
   erledigte Issues resolven (fix-release → inNextRelease)").
3. Der Guard listet alle unresolved Issues des Projekts, filtert die mit
   passendem `fix-release`-Tag und markiert sie per Sentry-Bulk-Update
   (`PUT /api/0/projects/{org}/{project}/issues/?id=…` mit
   `{"status":"resolved","statusDetails":{"inNextRelease":true}}`).
   **Sentry schließt sie automatisch**, sobald Events aus genau dieser Release
   eintreffen — semantisch „Resolved in next release".

Lokal testen (Offline-Fixtures):

```bash
bash scripts/test_sentry_resolve.sh      # R1–R11, kein Netz
bash scripts/test_distribution_stable.sh # D14-Verträge des Workflow-Steps
bash scripts/check_sentry_resolve.sh --version v0.6.0-beta --dry-run   # nur anzeigen
bash scripts/check_sentry_resolve.sh --version v0.6.0-beta             # echt resolven
```

Verdicts: `OK: N Issue(s) als resolved (inNextRelease) markiert` / `OK: 0 Issues
mit Tag fix-release:…` (nichts zu tun) / `SKIP` (kein Token, HTTP 401/403/
Netzwerk/unerwarteter Status — bewusst **kein** Gate-Blocker: der Workflow-Step
läuft `continue-on-error`, ein Sentry-Ausfall blockiert die Distribution nie) /
`FEHLER` (unverständliche API-Antwort oder falscher Aufruf, exit 1).

**Token (`SENTRY_RESOLVE_TOKEN`):** eigener User-Token (gleiche UI wie Abschnitt
1, <https://sentry.io/settings/account/api/auth-tokens/>) mit Scopes
`project:write` **und** `project:read`, Ablauf 30 Tage. Lokal ablegen als Zeile
`resolve.token=…` in der gitignoreden `sentry.properties` (nicht `auth.token`
überschreiben). Quellen-Reihenfolge: `SENTRY_RESOLVE_TOKEN` → `resolve.token` →
`stats.token` → `auth.token` (die beiden Lese-Fallbacks liefern beim PUT 403 →
SKIP mit `project:write`-Hinweis). Für CI einmalig hinterlegen:

```bash
gh secret set SENTRY_RESOLVE_TOKEN --body sntrys_…
```

Die Distribution läuft ohne das Secret unverändert (der Guard fällt auf SKIP
zurück); das Issue-Tag-Contract bleibt dokumentiert und der Step ist im
Pre-Push-Gate offline abgesichert (`test_sentry_resolve.sh` R1–R11).
