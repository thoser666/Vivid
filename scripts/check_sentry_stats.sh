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

  PROJECT_ID=$(PROJECT_JSON="$PROJECT_JSON" python - <<'PY'
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

  status=$(curl -sS -o /tmp/sentry_stats_events.$$.json -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    "$API/organizations/$ORG/events/?statsPeriod=30d&project=$PROJECT_ID" 2>/dev/null || echo 000)
  case "$status" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk, Events-Abfrage)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne event:read-Scope ($DOCS_HINT)"; exit 0 ;;
    200) : ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $status (Events-Abfrage)"; exit 0 ;;
  esac
  EVENTS_JSON=$(cat /tmp/sentry_stats_events.$$.json); rm -f /tmp/sentry_stats_events.$$.json
fi

# Auswertung (tolerant gegenüber Formvarianten, fail-closed bei unbekanntem Format).
PROJECT_JSON="$PROJECT_JSON" EVENTS_JSON="$EVENTS_JSON" python - <<'PY'
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

if totals is None:
    print("FEHLER: Events-Antwort ohne totals-Objekt — Sentry-API-Feldformat geändert?")
    sys.exit(1)

def num(key):
    try:
        return int(totals.get(key, 0))
    except (TypeError, ValueError):
        return 0

accepted = num("accepted")
# Bewusst ohne "filtered" (projektkonfiguriertes Inbound-Filtering ist keine Quota-Signal):
dropped = num("dropped") + num("rejected") + num("rateLimited")

first = proj.get("firstEvent") or proj.get("dateCreated") or "unbekannt"
print("Projekt-ID: %s | erstes Event: %s" % (proj.get("id", "?"), first))
if dropped > 0:
    print("WARN: %d Event(s) in 30d angenommen, %d verworfen (Quota-/Ratenlimit-Signal — Sentry-Usage im Dashboard prüfen)" % (accepted, dropped))
    sys.exit(0)
if accepted == 0:
    print("OK: 0 Events in 30d (Projekt erreichbar, ruhig)")
    sys.exit(0)
print("OK: %d Event(s) in 30d angenommen, 0 verworfen" % accepted)
sys.exit(0)
PY
