#!/usr/bin/env bash
# Sentry-Resolve-Guard (Vivid): schließt erledigte Sentry-Issues automatisch mit
# der nächsten Version (Weg-3-Automation, Auftrag 25.09.2026).
#
# Vertrag: Ein Issue gilt als erledigt, wenn es das Issue-Tag
#   fix-release: <version>
# trägt (Key fix-release, Wert = Version ohne führendes 'v', case-insensitiv —
# z. B. "0.6.0-beta" für den Release-Tag v0.6.0-beta). Beim Stable-Publish einer
# Version ruft der Workflow distribution-stable.yml dieses Script mit
# --version "$TAG" auf; alle Issues mit passendem fix-release-Tag werden per
# Sentry-Bulk-Update als resolved/inNextRelease markiert. Sentry schließt sie
# automatisch, sobald Events aus dieser Release eintreffen (Status-Detail
# "inNextRelease", siehe Sentry-API "Bulk update issues": PUT /projects/{org}/
# {project}/issues/?id=…&id=…).
#
# Verdicts (bewusst KEIN Gate-Blocker — der Workflow-Step läuft continue-on-error):
#   OK    — N Issues als resolved (inNextRelease) markiert, oder 0 passend
#   SKIP  — neutral (kein Token, HTTP 401/403/Netzwerk/unerwarteter Status)
#   FEHLER — unverständliche API-Antwort oder falscher Aufruf (exit 1)
#
# Token-Quelle (in dieser Reihenfolge):
#   1. $SENTRY_RESOLVE_TOKEN — User-Token mit project:write + project:read.
#      Erstellung: docs/sentry-stats.md, Abschnitt 6.
#   2. sentry.properties (resolve.token) — derselbe Token, lokal abgelegt
#      (die Datei ist gitignored); praktisch für Live-Läufe ohne Env-Setup.
#   3. sentry.properties (stats.token / auth.token) — Lese-Fallbacks: deren PUT
#      liefert 403 (kein project:write) → SKIP mit Scope-Hinweis.
#  --print-token-source meldet nur die gewählte QUELLE, nie den Token.
#
# Aufruf-Konvention: Python-Heredocs laufen mit `python -X utf8` — sonst
# crasht der Guard auf Windows-Locales (cp1252) mit verschleiertem
# UnicodeEncodeError an —/→/ü in der Ausgabe (stderr wurde gefiltert,
# Regression vom 25.09.2026, entdeckt am Pre-Push-Gate R3).
#
# Fixture-Modus für Offline-Tests: SENTRY_RESOLVE_FIXTURE=<dir> mit
# issues.json (Liste echter API-Antwort-Datensätze), optional `status`
# (simulierter HTTP-Code des Bulk-PUT, Default 200) und `getstatus`
# (simulierter HTTP-Code der Lesen-Abfrage, Default 200) — kein Netz, kein Token.
set -euo pipefail
cd "$(dirname "$0")/.."

ORG="${SENTRY_ORG:-privat-jb}"
PROJECT_SLUG="${SENTRY_PROJECT:-vivid}"
DOCS_HINT="docs/sentry-stats.md"
FIXTURE="${SENTRY_RESOLVE_FIXTURE:-}"
PROPS="${SENTRY_PROPERTIES_FILE:-sentry.properties}"
API="https://sentry.io/api/0"

version=""
dryrun=0
print_src=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) version="${2:-}"; shift 2 ;;
    --dry-run) dryrun=1; shift ;;
    --print-token-source) print_src=1; shift ;;
    *)
      echo "FEHLER: unbekanntes Argument: $1 (Aufruf: scripts/check_sentry_resolve.sh --version <vX.Y.Z[..]> [--dry-run])"
      exit 1 ;;
  esac
done

if [ -z "$version" ]; then
  echo "FEHLER: --version <vX.Y.Z> fehlt (Release-Tag, z. B. v0.6.0-beta)"
  exit 1
fi

# Normalisierte Vergleichsversion (ohne führendes v, lowercase) — identische
# Regel wie python norm() in Phase A unten.
norm_version=$(printf '%s' "$version" | sed -e 's/^[vV]//' | tr '[:upper:]' '[:lower:]')

# Gemeinsame Zwischenablage der Phasen (GET-Pages, PUT-Url/Body, Marker).
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# API-Status-SKIPs der Lesen-Abfrage (live + Fixture-getstatus).
skip_getstatus() {
  case "$1" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk, Issues-Abfrage)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen — neu erstellen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne Lesescopes — project:read nötig ($DOCS_HINT)"; exit 0 ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $1 (Issues-Abfrage)"; exit 0 ;;
  esac
}
# API-Status-SKIPs der Schreib-/Resolve-Abfrage (live + Fixture-status).
skip_putstatus() {
  case "$1" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk, Resolve-Ausführung)"; exit 0 ;;
    401) echo "SKIP: Token ungültig/abgelaufen — neu erstellen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Token ohne Schreibscopes — project:write für das Resolven nötig ($DOCS_HINT)"; exit 0 ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $1 (Resolve-Abfrage)"; exit 0 ;;
  esac
}

token=""
source="none"
if [ "${SENTRY_RESOLVE_TOKEN+x}" = "x" ]; then
  # Explizit gesetzt (auch leer = bewusst kein sentry.properties-Fallback).
  token="$SENTRY_RESOLVE_TOKEN"
  source="env"
elif [ -z "$FIXTURE" ] && [ -f "$PROPS" ]; then
  read_prop() {
    (grep -E "^$1=" "$PROPS" 2>/dev/null || true) \
      | tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^"\(.*\)"$/\1/'
  }
  token=$(read_prop 'resolve\.token')
  source="resolve.token"
  if [ -z "$token" ]; then
    token=$(read_prop 'stats\.token')
    source="stats.token"
  fi
  if [ -z "$token" ]; then
    token=$(read_prop 'auth\.token')
    source="auth.token"
  fi
fi

if [ "$print_src" = "1" ]; then
  echo "TOKEN_SOURCE=$source"
  exit 0
fi

# Issues-Quelle: Fixture (offline) oder Live-GET mit Pagination (rel="next").
frag_files=""
if [ -n "$FIXTURE" ]; then
  if [ ! -d "$FIXTURE" ]; then echo "SKIP: Fixture-Verzeichnis fehlt: $FIXTURE"; exit 0; fi
  getstatus=$(cat "$FIXTURE/getstatus" 2>/dev/null || echo 200)
  [ "$getstatus" = "200" ] || skip_getstatus "$getstatus"
  frag_files="$FIXTURE/issues.json"
else
  if [ -z "$token" ]; then
    echo "SKIP: kein Sentry-Resolve-Token — SENTRY_RESOLVE_TOKEN setzen oder resolve.token in sentry.properties ablegen (Token-Erstellung: $DOCS_HINT)"
    exit 0
  fi
  : > "$tmp/frags"
  cursor=""
  for page in $(seq 1 25); do
    url="$API/projects/$ORG/$PROJECT_SLUG/issues/?query=is:unresolved&limit=100"
    if [ -n "$cursor" ]; then url="$url&cursor=$cursor"; fi
    getstatus=$(curl -sS -D "$tmp/hdr.$page" -o "$tmp/page.$page" -w '%{http_code}' \
      -H "Authorization: Bearer $token" "$url" 2>/dev/null || echo 000)
    if [ "$getstatus" != "200" ]; then skip_getstatus "$getstatus"; fi
    echo "$tmp/page.$page" >> "$tmp/frags"
    frag_files=$(cat "$tmp/frags")
    cursor=$(CURLHDR="$tmp/hdr.$page" python -X utf8 - <<'PY'
import os, re
h = open(os.environ["CURLHDR"], encoding="utf-8", errors="replace").read()
m = re.search(r'<([^>]*cursor=[^>]*)>\s*;\s*rel="next"', h)
print(m.group(1) if m else "")
PY
)
    [ -z "$cursor" ] && break
  done
fi

# ── Phase A: Matches offline berechnen (kein Netz). Liefert bei Treffern die
# PUT-Url/-Body als Dateien + Marker WOULD_RESOLVE=N; bei 0 Treffern ein OK.
rc=0
out_a=$(SENTRY_RESOLVE_FRAG_FILES="$frag_files" \
  SENTRY_RESOLVE_VERSION="$norm_version" \
  SENTRY_RESOLVE_VERSION_RAW="$version" \
  SENTRY_RESOLVE_API="$API" \
  SENTRY_RESOLVE_ORG="$ORG" \
  SENTRY_RESOLVE_PROJECT="$PROJECT_SLUG" \
  SENTRY_RESOLVE_TMP="$tmp" python -X utf8 - 2>/dev/null <<'PY' || rc=$?
import json, os, sys

version = os.environ["SENTRY_RESOLVE_VERSION"]
version_raw = os.environ["SENTRY_RESOLVE_VERSION_RAW"]
files = [f for f in os.environ["SENTRY_RESOLVE_FRAG_FILES"].split() if f]

def norm(v):
    return (v or "").strip().lstrip("vV").lower()

issues = []
for f in files:
    try:
        data = json.load(open(f, encoding="utf-8"))
    except Exception as e:
        print("FEHLER: Issues-Antwort (%s) ist kein gültiges JSON (%s)" % (f, e))
        sys.exit(1)
    if not isinstance(data, list):
        print("FEHLER: Issues-Antwort (%s) ist keine Liste — Sentry-API-Feldformat geändert?" % f)
        sys.exit(1)
    issues.extend(data)

matches = []
for it in issues:
    if not isinstance(it, dict):
        continue
    if it.get("status") == "resolved":
        continue
    tags = it.get("tags") or []
    for t in tags:
        if isinstance(t, dict):
            key, value = t.get("key"), t.get("value")
        elif isinstance(t, (list, tuple)) and len(t) >= 2:
            key, value = t[0], t[1]
        else:
            continue
        if (key or "").lower() == "fix-release" and norm(value) == version:
            matches.append((str(it.get("id", "?")), str(it.get("title", "?"))))
            break

print("Projekt: %s (Org %s) | Version: %s (normalisiert: %s)" % (
    os.environ["SENTRY_RESOLVE_PROJECT"], os.environ["SENTRY_RESOLVE_ORG"], version_raw, version))
print("Geprüft: %d unresolved Issues (query=is:unresolved)" % len(issues))
if not matches:
    print("OK: 0 Issues mit Tag fix-release:%s — nichts zu resolven (%d unresolved geprüft)" % (version, len(issues)))
    sys.exit(0)

ids = [i for (i, _) in matches]
url = "%s/projects/%s/%s/issues/?id=%s" % (
    os.environ["SENTRY_RESOLVE_API"], os.environ["SENTRY_RESOLVE_ORG"],
    os.environ["SENTRY_RESOLVE_PROJECT"], "&id=".join(ids))
body = json.dumps({"status": "resolved", "statusDetails": {"inNextRelease": True}})
tmp = os.environ["SENTRY_RESOLVE_TMP"]
open(os.path.join(tmp, "put_url"), "w", encoding="utf-8").write(url)
open(os.path.join(tmp, "put_body"), "w", encoding="utf-8").write(body)
open(os.path.join(tmp, "put_ids"), "w", encoding="utf-8").write(",".join(ids))
for i, t in matches:
    print("RESOLVE: %s → %s" % (i, t))
print("WOULD_RESOLVE=%d" % len(matches))
sys.exit(0)
PY
)
echo "$out_a"
if [ "$rc" -ne 0 ]; then
  exit "$rc"
fi
# 0 Treffer → Phase A hat bereits das OK-Urteil gedruckt.
printf '%s\n' "$out_a" | grep -q '^OK: 0 Issues' && exit 0

count=$(printf '%s\n' "$out_a" | grep -o 'WOULD_RESOLVE=[0-9]*' | cut -d= -f2)
if [ -z "$count" ]; then
  echo "FEHLER: Phase-A-Output ohne WOULD_RESOLVE-Marker — Script-Formatbruch?"
  exit 1
fi
ids=$(cat "$tmp/put_ids")

if [ "$dryrun" = "1" ]; then
  echo "OK (dry-run): $count Issue(s) würden als resolved (inNextRelease) markiert — Version $norm_version (IDs: $ids)"
  exit 0
fi

# ── Phase B: Bulk-PUT ausführen (live) bzw. Status simulieren (Fixture).
if [ -n "$FIXTURE" ]; then
  putstatus=$(cat "$FIXTURE/status" 2>/dev/null || echo 200)
else
  putstatus=$(curl -sS -o /tmp/sentry_resolve_put.$$.json -w '%{http_code}' \
    -X PUT -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    -d @"$tmp/put_body" "$(cat "$tmp/put_url")" 2>/dev/null || echo 000)
fi

case "$putstatus" in
  200|204)
    echo "OK: $count Issue(s) als resolved (inNextRelease) markiert — erledigt mit Version $norm_version (IDs: $ids)"
    exit 0 ;;
  *) skip_putstatus "$putstatus" ;;
esac