#!/usr/bin/env bash
# Sentry-Health-Guard (Vivid): Ingest-Probe + Rate-Limit-Header-Check.
#
# Schickt ein minimal gültiges Probe-Event an den echten Ingest-Endpunkt des
# DSN-Projekts und bewertet Antwort + Rate-Limit-Header:
#
#   OK     — HTTP 200 ohne Rate-Limit-Header (Ingest lebt, keine Drossel)
#   WARN   — HTTP 200 MIT X-Sentry-Rate-Limits/Retry-After, oder HTTP 429
#            (Quota-/Ratenlimit-Signal — Sentry-Usage im Dashboard prüfen)
#   FEHLER — HTTP 400/401/403 (DSN-/Envelope-Problem) → exit 1
#   SKIP   — ohne --live (opt-in), Netzwerk, unerwarteter Status → exit 0
#
# Bewusst OPT-IN (--live): Die DSN ist öffentlich — automatische Proben bei
# jedem Push würden das Dashboard mit Probe-Events zumüllen. Das Pre-Push-Gate
# prüft nur die Envelope-Struktur offline (test_sentry_health.sh). Header-
# Inhalte werden nie ausgegeben (nur welche Header gesetzt sind).
#
# Modi:
#   --live            echte Probe (POST an den Ingest)
#   --print-envelope  Envelope nach stdout (kein POST, Struktur-Inspektion)
#   --print-dsn       DSN-Bestandteile aus dem Manifest (KEY/HOST/PROJ)
# Fixture-Modus für Offline-Tests: SENTRY_HEALTH_FIXTURE=<dir> mit `status`
# (HTTP-Code) + `headers` (Antwort-Header) — kein Netz, kein POST.
set -euo pipefail
cd "$(dirname "$0")/.."

MANIFEST="app/src/main/AndroidManifest.xml"
DOCS_HINT="docs/sentry-stats.md"
FIXTURE="${SENTRY_HEALTH_FIXTURE:-}"
LIVE=0
PRINT_ENVELOPE=0
PRINT_DSN=0
for arg in "$@"; do
  case "$arg" in
    --live) LIVE=1 ;;
    --print-envelope) PRINT_ENVELOPE=1 ;;
    --print-dsn) PRINT_DSN=1 ;;
    *) echo "SKIP: unbekanntes Argument: $arg"; exit 0 ;;
  esac
done

# DSN aus dem Manifest (Attribut-Reihenfolge-tolerant).
dsn=$(MANIFEST="$MANIFEST" python - <<'PY'
import os, re
text = open(os.environ["MANIFEST"], encoding="utf-8").read()
tag = re.search(r'<meta-data[^>]*io\.sentry\.dsn[^>]*/>', text)
value = re.search(r'android:value="([^"]+)"', tag.group(0)) if tag else None
print(value.group(1) if value else "")
PY
)
if [ -z "$dsn" ]; then echo "SKIP: keine DSN im Manifest gefunden"; exit 0; fi

KEY=$(printf '%s' "$dsn" | sed -n 's|^https://\([0-9a-fA-F]*\)@.*|\1|p')
HOST=$(printf '%s' "$dsn" | sed -n 's|^https://[0-9a-fA-F]*@\([^/]*\)/.*|\1|p')
PROJ=$(printf '%s' "$dsn" | sed -n 's|.*/\([0-9][0-9]*\)$|\1|p')
if [ -z "$KEY" ] || [ -z "$HOST" ] || [ -z "$PROJ" ]; then
  echo "SKIP: DSN-Format unverständlich: $dsn"; exit 0
fi

if [ "$PRINT_DSN" = "1" ]; then
  echo "KEY=$KEY HOST=$HOST PROJ=$PROJ"
  exit 0
fi

# Envelope (Header-Zeile, Item-Header, Payload) — strukturtreu an den
# selbst verifizierten Probe-Envelopestil angelehnt.
env_lines=$(ENVELOPE_ENV="health-probe" python - <<'PY'
import datetime, json, os, uuid
event_id = uuid.uuid4().hex
now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
payload = {
    "platform": "other",
    "environment": os.environ.get("ENVELOPE_ENV", "health-probe"),
    "release": "vivid-health-probe@0",
    "message": {"formatted": "Vivid health probe (delete me)"},
    "tags": {"probe": "health-check"},
}
data = json.dumps(payload, separators=(",", ":"))
header = json.dumps({"event_id": event_id, "sent_at": now}, separators=(",", ":"))
print(len(data.encode("utf-8")))
print(header)
print(data)
PY
)
PAYLOAD_LEN=$(printf '%s\n' "$env_lines" | sed -n '1p')
ENV_HDR=$(printf '%s\n' "$env_lines" | sed -n '2p')
PAYLOAD=$(printf '%s\n' "$env_lines" | sed -n '3p')
FULL=$(printf '%s\n{"type":"event","length":%s}\n%s' "$ENV_HDR" "$PAYLOAD_LEN" "$PAYLOAD")

if [ "$PRINT_ENVELOPE" = "1" ]; then
  printf '%s\n' "$FULL"
  exit 0
fi

HDRFILE=""
if [ -n "$FIXTURE" ]; then
  if [ ! -d "$FIXTURE" ]; then echo "SKIP: Fixture-Verzeichnis fehlt: $FIXTURE"; exit 0; fi
  status=$(cat "$FIXTURE/status" 2>/dev/null || echo 200)
  HDRFILE="$FIXTURE/headers"
else
  if [ "$LIVE" != "1" ]; then
    echo "SKIP: Live-Probe nicht angefordert — opt-in mit --live (die DSN ist oeffentlich; automatische Proben wuerden das Dashboard zumuellen). Struktur-Selbsttest: bash scripts/test_sentry_health.sh. Details: $DOCS_HINT"
    exit 0
  fi
  BODY=$(mktemp); HDRFILE=$(mktemp)
  status=$(curl -sS -o "$BODY" -D "$HDRFILE" -w '%{http_code}' \
    -X POST "https://$HOST/api/$PROJ/envelope/" \
    -H "Content-Type: application/x-sentry-envelope" \
    -H "X-Sentry-Auth: Sentry sentry_key=$KEY, sentry_version=7" \
    --data-binary "$FULL" 2>/dev/null || echo 000)
  rm -f "$BODY"
fi

# Verdict — Header-Inhalte werden absichtlich nicht ausgegeben (nur welche
# Header gesetzt sind), damit manipulierte Header nichts einschleppen können.
rl=$(grep -ci '^x-sentry-rate-limits:' "$HDRFILE" 2>/dev/null || true)
ra=$(grep -ci '^retry-after:' "$HDRFILE" 2>/dev/null || true)
[ -n "$FIXTURE" ] || rm -f "$HDRFILE"
which=""; [ "${rl:-0}" -gt 0 ] 2>/dev/null && which="X-Sentry-Rate-Limits"
[ "${ra:-0}" -gt 0 ] 2>/dev/null && which="${which:+$which + }Retry-After"

case "$status" in
  200)
    if [ -n "$which" ]; then
      echo "WARN: Probe akzeptiert (HTTP 200), aber Rate-Limit-Header gesetzt ($which) — Quota-/Ratenlimit-Signal, Sentry-Usage im Dashboard prüfen"
    else
      echo "OK: Ingest lebt — Probe akzeptiert (HTTP 200), keine Rate-Limit-Header"
    fi ;;
  429) echo "WARN: HTTP 429 — Rate-Limited (Quota erschöpft?) — Sentry-Usage im Dashboard prüfen" ;;
  400|401|403) echo "FEHLER: HTTP $status — DSN-/Envelope-Problem (Details: $DOCS_HINT)"; exit 1 ;;
  000) echo "SKIP: Ingest nicht erreichbar (Netzwerk)" ;;
  *)   echo "SKIP: unerwarteter HTTP-Status $status" ;;
esac
