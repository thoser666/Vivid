#!/usr/bin/env bash
# Sentry-Event-Stats-Guard (Vivid): liefert die Event-Statistik des
# DSN-Projekts (Org privat-jb / Projekt vivid) mit klaren Verdicts:
#
#   OK   — Events in den letzten 30 Tagen angenommen (Pipeline lebt)
#   OK   — 0 Events (Projekt erreichbar, aber ruhig)
#   WARN — Events verworfen (dropped/rateLimited) → Quota-/Ratenlimit-Signal
#   SKIP — neutral (kein Token, 401 abgelaufen, 403 Scopes, Netzwerk)
#   FEHLER — unverständliche API-Antwort (Feldformat geändert) → exit 1
#
# Bewusst KEIN Gate-Blocker: Konfigurations-/Netzwerkprobleme sind neutrale
# SKIPs (exit 0). Das Pre-Push-Gate läuft nur den Offline-Selbsttest
# (scripts/test_sentry_stats.sh); der Live-Abruf ist ein Werkzeug für
# Menschen und CI-Jobs mit Token.
#
# Token-Quelle (in dieser Reihenfolge):
#   1. $SENTRY_STATS_TOKEN — kurzlebiger User-Token mit project:read +
#      event:read. Erstellung: docs/sentry-stats.md.
#   2. sentry.properties (stats.token) — derselbe User-Token, lokal
#      abgelegt (die Datei ist gitignored); praktisch für Live-Läufe ohne
#      Env-Setup.
#   3. sentry.properties (auth.token) — das CI-Token (meist ohne
#      Lesescopes), Fallback nur, wenn 1./2. nicht vorhanden sind.
#  --print-token-source meldet nur die gewählte QUELLE, nie den Token.
#
# Aufruf-Konvention: Python-Heredocs laufen mit `python -X utf8` — sonst
# geben Windows-Locales (cp1252) Umlaute/Striche als Mojibake aus
# (beobachtet am Pre-Push-Gate, 25.09.2026) und Locales ohne diese
# Zeichen (z. B. cp932/cp1251) crashen mit verschleiertem
# UnicodeEncodeError — dieselbe Falle wie im Resolve-Guard (bfb9c02).
#
# Fixture-Modus für Offline-Tests: SENTRY_STATS_FIXTURE=<dir> mit
# project.json + events.json (echte API-Antwortformen) und optional
# `status` (simulierter HTTP-Code, Default 200) — kein Netz, kein Token.
set -euo pipefail
cd "$(dirname "$0")/.."

ORG="${SENTRY_ORG:-privat-jb}"
PROJECT_SLUG="${SENTRY_PROJECT:-vivid}"
DOCS_HINT="docs/sentry-stats.md"
FIXTURE="${SENTRY_STATS_FIXTURE:-}"
PROPS="${SENTRY_PROPERTIES_FILE:-sentry.properties}"
API="https://sentry.io/api/0"

token=""
source="none"
if [ "${SENTRY_STATS_TOKEN+x}" = "x" ]; then
  # Explizit gesetzt (auch leer = bewusst kein sentry.properties-Fallback).
  token="$SENTRY_STATS_TOKEN"
  source="env"
elif [ -z "$FIXTURE" ] && [ -f "$PROPS" ]; then
  read_prop() {
    (grep -E "^$1=" "$PROPS" 2>/dev/null || true) \
      | tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^"\(.*\)"$/\1/'
  }
  token=$(read_prop 'stats\.token')
  source="stats.token"
  if [ -z "$token" ]; then
    token=$(read_prop 'auth\.token')
    source="auth.token"
  fi
fi

if [ "${1:-}" = "--print-token-source" ]; then
  echo "TOKEN_SOURCE=$source"
  exit 0
fi

if [ -n "$FIXTURE" ]; then
  if [ ! -d "$FIXTURE" ]; then echo "SKIP: Fixture-Verzeichnis fehlt: $FIXTURE"; exit 0; fi
  status=$(cat "$FIXTURE/status" 2>/dev/null || echo 200)
  case "$status" in
    200) : ;;
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen — neu erstellen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne Lesescopes — project:read + event:read nötig ($DOCS_HINT)"; exit 0 ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $status"; exit 0 ;;
  esac
  PROJECT_JSON=$(cat "$FIXTURE/project.json" 2>/dev/null || echo '{}')
  EVENTS_JSON=$(cat "$FIXTURE/events.json" 2>/dev/null || echo '{}')
else
  if [ -z "$token" ]; then
    echo "SKIP: kein Sentry-Lese-Token — SENTRY_STATS_TOKEN setzen oder stats.token in sentry.properties ablegen (Token-Erstellung: $DOCS_HINT)"
    exit 0
  fi
  status=$(curl -sS -o /tmp/sentry_stats_project.$$.json -w '%{http_code}' \
    -H "Authorization: Bearer $token" "$API/projects/$ORG/$PROJECT_SLUG/" 2>/dev/null || echo 000)
  case "$status" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen — neu erstellen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne Lesescopes — project:read + event:read nötig ($DOCS_HINT)"; exit 0 ;;
    200) : ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $status (Projekt-Abfrage)"; exit 0 ;;
  esac
  PROJECT_JSON=$(cat /tmp/sentry_stats_project.$$.json); rm -f /tmp/sentry_stats_project.$$.json

  PROJECT_ID=$(PROJECT_JSON="$PROJECT_JSON" python -X utf8 - <<'PY'
import json, os
try:
    d = json.loads(os.environ["PROJECT_JSON"])
    print(str(d.get("id", "")))
except Exception:
    print("")
PY
)
  if [ -z "$PROJECT_ID" ]; then
    echo "FEHLER: Projekt-Antwort ohne numerische id — Sentry-API-Feldformat geändert?"
    exit 1
  fi

  # Stats über den Org-Outcome-Summary-Endpoint (field=sum(quantity) je
  # Kategorie): liefert accepted + rate_limited/… — das Quota-Signal. Der
  # frühere Legacy-Endpoint /organizations/{org}/events/ antwortet seit der
  # API-Modernisierung mit HTTP 400 (Fix #203, Run 36104691848).
  status=$(curl -sS -o /tmp/sentry_stats_events.$$.json -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    "$API/organizations/$ORG/stats-summary/?field=sum(quantity)&statsPeriod=30d&project=$PROJECT_ID&category=error" 2>/dev/null || echo 000)
  case "$status" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk, Stats-Summary-Abfrage)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne Lesescopes (org:read für Stats-Summary nötig, $DOCS_HINT)"; exit 0 ;;
    200) : ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $status (Stats-Summary-Abfrage)"; exit 0 ;;
  esac
  EVENTS_JSON=$(cat /tmp/sentry_stats_events.$$.json); rm -f /tmp/sentry_stats_events.$$.json
fi

# Auswertung (tolerant gegenüber Formvarianten, fail-closed bei unbekanntem Format).
PROJECT_JSON="$PROJECT_JSON" EVENTS_JSON="$EVENTS_JSON" python -X utf8 - <<'PY'
import json, os, sys

try:
    proj = json.loads(os.environ["PROJECT_JSON"])
    events = json.loads(os.environ["EVENTS_JSON"])
except Exception as e:
    print("FEHLER: Antwort ist kein gültiges JSON (%s)" % e)
    sys.exit(1)

totals = None
for key in ("org", "project"):
    for entry in (events.get(key) or []):
        t = entry.get("totals")
        if isinstance(t, dict):
            totals = t
            break
    if totals is not None:
        break

# Neues Format (org stats-summary): {"projects":[{id,slug,stats:[{category,
# outcomes:{...}, totals:{...}}]}]}. Toleranz für beide Formen behalten —
# überall dort, wo das neue Format vorliegt, wird über Outcomes gerechnet.
projects = events.get("projects")
if isinstance(projects, list) and projects:
    entry = (projects[0].get("stats") or [{}])[0] if isinstance(projects[0], dict) else {}
    outcomes = entry.get("outcomes") if isinstance(entry, dict) else None
    totals = entry.get("totals") if isinstance(entry, dict) else None

if not isinstance(totals, dict):
    print("FEHLER: Stats-Antwort ohne totals/outcomes-Objekt — Sentry-API-Feldformat geändert?")
    sys.exit(1)

outcomes = outcomes if isinstance(outcomes, dict) else {}

def num(key):
    try:
        return int(outcomes.get(key, totals.get(key, 0)))
    except (TypeError, ValueError):
        return 0

accepted = num("accepted")
if not accepted:
    accepted = num("sum(quantity)")
# Bewusst ohne "filtered" (projektkonfiguriertes Inbound-Filtering ist keine Quota-Signal):
# Legacy-Aliase rejected/rateLimited laufen in die rate_limited-Anzeige ein.
rate_limited = (num("rate_limited") + num("rejected") + num("rateLimited"))
invalid = num("invalid")
client_discard = num("client_discard")
abuse = num("abuse")
cardinality_limited = num("cardinality_limited")
dropped = (rate_limited + invalid + client_discard + abuse + cardinality_limited)

def drop_split():
    parts = [
        ("rate_limited", rate_limited),
        ("invalid", invalid),
        ("client_discard", client_discard),
        ("abuse", abuse),
        ("cardinality_limited", cardinality_limited),
    ]
    return ", ".join("%s=%d" % (label, n) for label, n in parts if n > 0)

first = proj.get("firstEvent") or proj.get("dateCreated") or "unbekannt"
print("Projekt-ID: %s | erstes Event: %s" % (proj.get("id", "?"), first))
# client_discard ist bei Vivid BY DESIGN kein Quota-Signal: der vorab gefilterte
# beforeSend-Callback (SentryOptOut, AppSettings.sentryEnabled) verwirft Events
# gezielt, solange der Nutzer die Fehlerberichterstattung nicht aktiviert hat
# (Privatsphäre-by-Default, Sentry-Outcome client_discard). WARN-Alarm also nur
# für echte Quota-/Qualitäts-Signale (rate_limited/invalid/abuse/
# cardinality_limited); client_discard-only läuft als informative OK-Ausgabe.
quota = rate_limited + invalid + abuse + cardinality_limited
if quota > 0:
    print("WARN: %d Event(s) in 30d angenommen, %d verworfen (Quota-/Ratenlimit-Signal — Sentry-Usage im Dashboard prüfen)" % (accepted, dropped))
    print("Drops-Split: %s" % drop_split())
    sys.exit(0)
if dropped > 0:
    # Nur client_seitig verworfen — dokumentierter Opt-out-Kanal (Issue #210).
    print("OK: %d Event(s) in 30d angenommen; %d client_seitig verworfen (by design: beforeSend-Opt-out — kein Quota-Signal; Drops-Split: %s)" % (accepted, dropped, drop_split()))
    sys.exit(0)
if accepted == 0:
    print("OK: 0 Events in 30d (Projekt erreichbar, ruhig)")
    sys.exit(0)
print("OK: %d Event(s) in 30d angenommen, 0 verworfen" % accepted)
sys.exit(0)
PY
