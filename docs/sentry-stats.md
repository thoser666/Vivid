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
   - `event:read` (Event-Statistiken)
   - nichts weiter (kein `project:write`, kein `org:read`)
4. Ablaufdatum: **30 Tage** (Sentry-Default) — bewusst kurzlebig; nach
   Ablauf einfach neu erstellen (dieser Guard fällt dann auf SKIP zurück).
5. Token kopieren (Form `sntrys_…`) — er wird nur einmal angezeigt.

## 2. Nutzung (lokal)

```bash
SENTRY_STATS_TOKEN=sntrys_… bash scripts/check_sentry_stats.sh
```

Ausgabe (Verdicts):

```
OK: 42 Event(s) in 30d angenommen, 0 verworfen          # Pipeline lebt
OK: 0 Events in 30d (Projekt erreichbar, ruhig)         # ruhig, aber erreichbar
WARN: 42 …, 7 verworfen (Quota-/Ratenlimit-Signal …)    # Drops → Usage prüfen
SKIP: Token ohne Lesescopes — project:read + event:read nötig (docs/sentry-stats.md)
```

Exit-Codes: `0` für OK/WARN/SKIP (bewusst kein Gate-Blocker), `1` nur bei
unverständlicher API-Antwort (Feldformat geändert → fail-closed).

Ohne `SENTRY_STATS_TOKEN` fällt der Guard auf das CI-Token aus
`sentry.properties` zurück (SKIP mit Scopes-Hinweis, wenn es keine
Lesescopes hat — der Normalfall).

## 3. In CI verwenden (optional)

Das Repo-Secret `SENTRY_AUTH_TOKEN` ist das Mapping-Upload-Token (org:ci).
Für Statistik-Checks ein separates Secret setzen (z. B. `SENTRY_STATS_TOKEN`)
und den Guard in einem Workflow-Step aufrufen:

```yaml
- name: Sentry-Stats prüfen
  env:
    SENTRY_STATS_TOKEN: ${{ secrets.SENTRY_STATS_TOKEN }}
  run: bash scripts/check_sentry_stats.sh
```

## 4. Sicherheit

- Der User-Token ist **kein Repo-Secret-Ersatz** für den Mapping-Upload —
  getrennte Tokens, getrennte Scopes, getrennte Lebensdauern.
- Token nie in Logs/Screenshots; der Guard gibt das Token nie aus
  (Selbsttest S7 prüft das).
- Projekt: `privat-jb/vivid` (Projekt-ID `4509837327990784`, DSN im
  App-Manifest). Der Guard liest die ID selbst aus der Projekt-API.
