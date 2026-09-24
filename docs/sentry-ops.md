# Sentry-Ops: Kurzübersicht (Stats, Health, Opt-out, Mapping-Upload)

Sentry ist die einzige Telemetrie der **Standard-Builds** — der FOSS-Build
(F-Droid) und der Startup-Safe-Mode initialisieren Sentry gar nicht. Diese
Seite fasst die vier Bausteine der Sentry-Ops-Landschaft kompakt zusammen;
Details stehen in den am Ende verlinkten Repo-Dokus.

## Die vier Bausteine

| Baustein | Was er tut | Wo er lebt | Selbsttest |
|---|---|---|---|
| 📊 **Stats** | Event-Statistiken (30 Tage) abfragen, Quota-Drops sichtbar machen | `scripts/check_sentry_stats.sh` (lokal/CI), wöchentlich im Ops-Workflow | S1–S9 |
| 🩺 **Health** | Probe-Event an den echten Ingest senden — lebt der Endpunkt, wird gedrosselt? | `scripts/check_sentry_health.sh --live` (opt-in; wöchentlich im Ops-Workflow als sanktionierter Cron-Nutzer) | HP1–HP9 |
| 🔒 **Opt-out** | Nutzer-Toggle „Fehlerberichte senden“ steuert Events **und** Error-Replay | App: `VividApplication` + `SentryOptOut.kt` + `SentryReplayPolicy` | Unit-Tests (pure Policy) |
| 🗺️ **Mapping-Upload** | ProGuard-Mappings hochladen, damit Crash-Reports symbolisiert im Dashboard landen | Release-Build: Gradle-Plugin `autoUploadProguardMapping` | Verify-Job der Release-Pipeline |

### 📊 Stats

- Benötigt einen **User-Token mit `project:read` + `event:read`** (bewusst
  kurzlebig, 30 Tage; Anleitung:
  [docs/sentry-stats.md §1](https://github.com/thoser666/Vivid/blob/develop/docs/sentry-stats.md)).
- Token-Quellen der Reihe nach: Env/Secret `SENTRY_STATS_TOKEN` →
  `stats.token` (nur lokal, gitignorede `sentry.properties`) → `auth.token`
  (CI-Mapping-Token, ohne Lesescopes → SKIP).
- Verdicts: **OK** (Pipeline lebt) · **WARN** (verworfene Events =
  Quota-/Ratenlimit-Signal — Drops sind verlorene Crash-Berichte/Replays) ·
  **SKIP** (Token/Netz, neutral) · **FEHLER** (API-Feldformat geändert,
  fail-closed).

### 🩺 Health

- Bewusst **opt-in**: Die DSN steht im öffentlichen App-Manifest — automatische
  Proben bei jedem Push würden das Sentry-Dashboard mit Probe-Events zumüllen.
  Genau **eine Probe pro Woche** sendet der Ops-Workflow; Probe-Events tragen
  `tags.probe=health-check` und lassen sich im Dashboard filtern/löschen.
- Verdicts: **OK** (HTTP 200 ohne Rate-Limit-Header) · **WARN** (Drossel-Signal:
  `X-Sentry-Rate-Limits`/`Retry-After` oder HTTP 429) · **FEHLER** (400/401/403
  — DSN-/Envelope-Problem) · **SKIP** (Netzwerk/unerwarteter Status).

### 🔒 Opt-out (App-seitig)

- `sendDefaultPii = false`; der Toggle wirkt **live pro Event** (`beforeSend`)
  und filtert separat auch **Replay-Envelopes** (`beforeSendReplay`), weil
  Replays keine Sentry-Events sind und der normale Callback sie nicht sieht.
- **Error-Replay statt Dauer-Recording**: `onErrorSampleRate 1.0`,
  `sessionSampleRate 0.0` — ohne Fehler wird nichts aufgezeichnet oder
  hochgeladen; drei Verteidigungslinien (Rates, Buffering-Start/Stop,
  separater Callback).
- Der Toggle entscheidet nur, **was die App sendet** — nicht, was die
  Ops-Seite abfragt. Beide Welten teilen sich lediglich das Dashboard.

### 🗺️ Mapping-Upload

- Symbolisierung deobfuskiert Stacktraces im Sentry-Dashboard; das
  Gradle-Plugin lädt das ProGuard-Mapping **automatisch im Release-Build**
  hoch (kein separates sentry-cli, UUID-Verknüpfung ins Manifest).
- Dafür gilt der **CI-Token** (`auth.token` in der gitignoreden
  `sentry.properties`, Scope `org:ci`) — bewusst **ohne Lesescopes** und
  strikt getrennt vom Stats-Lese-Token: getrennte Tokens, getrennte Scopes,
  getrennte Lebensdauern.

## Wöchentliche Issue-Automation

[`automation-sentry-ops.yml`](https://github.com/thoser666/Vivid/blob/develop/.github/workflows/automation-sentry-ops.yml)
führt **Stats-Guard + Health-Probe jeden Montag 06:30 UTC** aus (plus
manueller Dispatch) und verwaltet die Ergebnisse als deduplizierte Issues:

| Ergebnis | Reaktion |
|---|---|
| **WARN** (Quota-Drops bzw. Drossel-Signal) | Issue mit Guard-Ausgabe + Run-Link — kommentiert statt neu, solange eins offen ist |
| **FEHLER / Konfigurations-SKIP** (Token fehlt/ungültig, API-Format, 4xx am Ingest) | Konfigurations-Issue mit Behebungs-Hinweis |
| **OK / neutraler Netzwerk-SKIP** | offene Check-Issues schließen sich automatisch |

Fehlt das Repo-Secret `SENTRY_STATS_TOKEN`, läuft der Workflow trotzdem und
hält per Konfigurations-Issue die Erinnerung am Leben — stillem Versagen ist
damit vorgebaut. Berechtigungen: `permissions: {}` top-level, Job nur
`issues: write`, beide Actions SHA-gepinnt.

## Verweise

- [docs/sentry-stats.md](https://github.com/thoser666/Vivid/blob/develop/docs/sentry-stats.md)
  — Token-Anleitung (§1), wöchentlicher Ops-Workflow (§3), Health-Probe (§4),
  Sicherheit (§5)
- [RELEASE.md → 🛰️ Sentry-Ops](https://github.com/thoser666/Vivid/blob/develop/RELEASE.md)
  — interne Referenz inkl. Testschutz-Tabelle
- Scripts: [`check_sentry_stats.sh`](https://github.com/thoser666/Vivid/blob/develop/scripts/check_sentry_stats.sh)
  · [`check_sentry_health.sh`](https://github.com/thoser666/Vivid/blob/develop/scripts/check_sentry_health.sh)
