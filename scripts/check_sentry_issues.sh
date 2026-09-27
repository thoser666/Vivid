#!/usr/bin/env bash
# Sentry-Issues-Guard (Vivid): Watchdog-/Failover-Schicht zur nativen
# Sentry-GitHub-Integration (docs/sentry-alerts.md §2).
#
# Was er tut: zieht ALLE unresolved Sentry-Issues des Projekts, filtert die
# bereits als GitHub-Issue erfassten per Marker
#     <!-- vivid-sentry-issue: <sentry-id> -->
# und öffnet für die restlichen ein GitHub-Issue mit bug_report-artiger
# Struktur + Sentry-Zusammenfassung (Level-Zeile, Top-Frame/Metadata,
# Permalink, Events-/Nutzer-Count, First/Last-Seen). Die "Level:"-Zeile im
# Body wird vom Triage-Workflow (automation-sentry-triage.yml) automatisch in
# ein severity:*-Label + ggf. crash-Label übersetzt — exakt wie bei der
# nativen Route.
#
# Verhältnis zur nativen Integration: Läuft jene (Echtzeit-Alert-Regel), sind
# alle unresolved Issues bereits via Marker erfasst → der Guard findet nichts
# Neues (Dedup-Zähler "0 neue"). Bricht sie weg (Integration nicht verbunden,
# Alert-Regel fehlt/defekt), fängt dieser stündliche Cron die Lücke und macht
# die Erstellung von außen überprüfbar. KEIN Ersatz für die Echtzeit-Route.
#
# Verdicts (bewusst KEIN Gate-Blocker — fehlender Token/Netz ist neutral):
#   OK     — N Issue(s) neu als GitHub-Issue erstellt / alle bereits erfasst
#   SKIP   — kein Sentry-Token, kein GitHub-Token, HTTP 401/403/Netz
#   FEHLER — unverständliche API-Antwort oder Aufruf-Fehler (exit 1)
#
# Tokens & Quellen (docs/sentry-issues.md):
#   SENTRY_ISSUES_TOKEN — User-Token, Scope project:read (30 Tage)
#   GITHUB_ISSUES_TOKEN — GitHub-Token (CI: secrets.GITHUB_TOKEN)
#   sentry.properties: issues.token → resolve.token → stats.token → auth.token
#   --print-token-source meldet nur die gewählte QUELLE, nie den Token.
#
# Aufruf-Konvention: Python-Heredocs laufen mit `python -X utf8` (cp1252-
# Windows-Crash-Regression, siehe test_sentry_resolve.sh R12).
#
# Fixture-Modus für Offline-Tests: SENTRY_ISSUES_FIXTURE=<dir> mit
#   issues.json    — Sentry-Issues-Liste (Array)
#   ghissues.json  — GitHub-Issue-Bestand (Array, nur .title/.body genutzt)
#   getstatus      — simulierter Sentry-HTTP-Code (Default 200)
#   gh_getstatus   — simulierter GitHub-Listen-HTTP-Code (Default 200)
#   ghstatus       — simulierter GitHub-Erstell-HTTP-Code (Default 201)
set -euo pipefail
cd "$(dirname "$0")/.."

ORG="${SENTRY_ORG:-privat-jb}"
PROJECT_SLUG="${SENTRY_PROJECT:-vivid}"
GH_REPO="${GITHUB_REPOSITORY:-thoser666/Vivid}"
DOCS_HINT="docs/sentry-issues.md"
FIXTURE="${SENTRY_ISSUES_FIXTURE:-}"
PROPS="${SENTRY_PROPERTIES_FILE:-sentry.properties}"
API="https://sentry.io/api/0"
GH_API="https://api.github.com"

dryrun=0
print_src=0
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dryrun=1; shift ;;
    --print-token-source) print_src=1; shift ;;
    *)
      echo "FEHLER: unbekanntes Argument: $1 (Aufruf: scripts/check_sentry_issues.sh [--dry-run] [--print-token-source])"
      exit 1 ;;
  esac
done

# Arbeitsverzeichnis: überschreibbar für Tests (SENTRY_ISSUES_WORKDIR +
# SENTRY_ISSUES_KEEP=1, damit Payloads inspizierbar bleiben).
tmp="${SENTRY_ISSUES_WORKDIR:-}"
if [ -z "$tmp" ]; then tmp=$(mktemp -d); fi
mkdir -p "$tmp"
if [ "${SENTRY_ISSUES_KEEP:-}" != "1" ]; then
  trap 'rm -rf "$tmp"' EXIT
fi

# ── Token-Quelle (Sentry) ────────────────────────────────────────────────
token=""
source="none"
if [ "${SENTRY_ISSUES_TOKEN+x}" = "x" ]; then
  token="$SENTRY_ISSUES_TOKEN"
  source="env"
elif [ -z "$FIXTURE" ] && [ -f "$PROPS" ]; then
  read_prop() {
    (grep -E "^$1=" "$PROPS" 2>/dev/null || true) \
      | tail -1 | cut -d= -f2- | tr -d '\r' | sed 's/^"\(.*\)"$/\1/'
  }
  token=$(read_prop 'issues\.token')
  source="issues.token"
  if [ -z "$token" ]; then
    token=$(read_prop 'resolve\.token')
    source="resolve.token"
  fi
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

# ── API-Status-SKIPs ─────────────────────────────────────────────────────
skip_sentry_status() {
  case "$1" in
    000) echo "SKIP: Sentry nicht erreichbar (Netzwerk, Issues-Abfrage) — kein Watchdog-Lauf ($DOCS_HINT)"; exit 0 ;;
    401) echo "SKIP: Sentry-Token ungültig/abgelaufen — neu erstellen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: Sentry-Token ohne Lesescopes — project:read nötig ($DOCS_HINT)"; exit 0 ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $1 (Sentry-Issues-Abfrage)"; exit 0 ;;
  esac
}
skip_gh_status() {
  case "$1" in
    000) echo "SKIP: GitHub nicht erreichbar (Netzwerk, Issue-Listen/-Erstellung)"; exit 0 ;;
    401) echo "SKIP: GitHub-Token ungültig — GITHUB_ISSUES_TOKEN prüfen ($DOCS_HINT)"; exit 0 ;;
    403) echo "SKIP: GitHub-Token ohne issues:write — GITHUB_ISSUES_TOKEN/Workflow-Permissions prüfen ($DOCS_HINT)"; exit 0 ;;
    404) echo "SKIP: GitHub-Repo $GH_REPO nicht gefunden bzw. kein Zugriff"; exit 0 ;;
    *)   echo "SKIP: unerwarteter HTTP-Status $1 (GitHub-API)"; exit 0 ;;
  esac
}

# ── Phase A-Vorbereitung: Quellen (Sentry + GitHub-Bestand) ─────────────
frag_files=""
gh_files=""
rc=0
if [ -n "$FIXTURE" ]; then
  if [ ! -d "$FIXTURE" ]; then echo "SKIP: Fixture-Verzeichnis fehlt: $FIXTURE"; exit 0; fi
  getstatus=$(cat "$FIXTURE/getstatus" 2>/dev/null || echo 200)
  [ "$getstatus" = "200" ] || skip_sentry_status "$getstatus"
  frag_files="$FIXTURE/issues.json"
  gh_getstatus=$(cat "$FIXTURE/gh_getstatus" 2>/dev/null || echo 200)
  if [ "$gh_getstatus" = "200" ]; then
    gh_files="$FIXTURE/ghissues.json"
  else
    skip_gh_status "$gh_getstatus"
  fi
else
  if [ -z "$token" ]; then
    echo "SKIP: kein Sentry-Lese-Token — SENTRY_ISSUES_TOKEN setzen oder issues.token in sentry.properties ablegen (Token-Erstellung: $DOCS_HINT)"
    exit 0
  fi
  if [ -z "${GITHUB_ISSUES_TOKEN:-}" ]; then
    echo "SKIP: kein GitHub-Token (GITHUB_ISSUES_TOKEN) — Dedup wäre blind, keine Erstellung ohne Bestandsabgleich ($DOCS_HINT)"
    exit 0
  fi

  # Sentry-Issues (unresolved, sort=new) mit Pagination.
  : > "$tmp/frags"
  cursor=""
  for page in $(seq 1 25); do
    url="$API/projects/$ORG/$PROJECT_SLUG/issues/?query=is:unresolved&sort=new&limit=100"
    if [ -n "$cursor" ]; then url="$url&cursor=$cursor"; fi
    getstatus=$(curl -sS -D "$tmp/hdr.$page" -o "$tmp/page.$page" -w '%{http_code}' \
      -H "Authorization: Bearer $token" "$url" 2>/dev/null || echo 000)
    if [ "$getstatus" != "200" ]; then skip_sentry_status "$getstatus"; fi
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

  # GitHub-Bestand (state=all → auch geschlossene Issues deduplizieren Markern).
  : > "$tmp/ghfrags"
  gh_next="$GH_API/repos/$GH_REPO/issues?state=all&per_page=100&page=1"
  for page in $(seq 1 25); do
    gh_getstatus=$(curl -sS -D "$tmp/ghhdr.$page" -o "$tmp/ghpage.$page" -w '%{http_code}' \
      -H "Authorization: Bearer ${GITHUB_ISSUES_TOKEN:-}" \
      -H "Accept: application/vnd.github+json" "$gh_next" 2>/dev/null || echo 000)
    if [ "$gh_getstatus" != "200" ]; then skip_gh_status "$gh_getstatus"; fi
    echo "$tmp/ghpage.$page" >> "$tmp/ghfrags"
    gh_files=$(cat "$tmp/ghfrags")
    gh_next=$(CURLHDR="$tmp/ghhdr.$page" python -X utf8 - <<'PY'
import os, re
h = open(os.environ["CURLHDR"], encoding="utf-8", errors="replace").read()
m = re.search(r'<([^>]+)>\s*;\s*rel="next"', h)
print(m.group(1) if m else "")
PY
)
    [ -z "$gh_next" ] && break
  done
fi

# ── Phase A: Kandidaten offline berechnen (kein Netz). Bei Kandidaten
# werden GitHub-Payloads als Dateien geschrieben + Marker WOULD_ISSUE=N;
# bei 0 Kandidaten druckt sie das OK-Urteil selbst.
out_a=$(SENTRY_ISSUES_FRAG_FILES="$frag_files" \
  SENTRY_ISSUES_GH_FILES="$gh_files" \
  SENTRY_ISSUES_API="$API" \
  SENTRY_ISSUES_ORG="$ORG" \
  SENTRY_ISSUES_PROJECT="$PROJECT_SLUG" \
  SENTRY_ISSUES_TMP="$tmp" python -X utf8 - 2>/dev/null <<'PY' || rc=$?
import json, os, re, sys

frag_files = [f for f in os.environ["SENTRY_ISSUES_FRAG_FILES"].split() if f]
gh_files = [f for f in os.environ["SENTRY_ISSUES_GH_FILES"].split() if f]

def load_json(f, what):
    try:
        data = json.load(open(f, encoding="utf-8"))
    except Exception as e:
        print("FEHLER: %s-Antwort (%s) ist kein gültiges JSON (%s)" % (what, f, e))
        sys.exit(1)
    if not isinstance(data, list):
        print("FEHLER: %s-Antwort (%s) ist keine Liste — API-Feldformat geändert?" % (what, f))
        sys.exit(1)
    return data

issues = []
for f in frag_files:
    issues.extend(load_json(f, "Sentry-Issues"))

existing_markers = set()
for f in gh_files:
    for it in load_json(f, "GitHub-Issues"):
        if not isinstance(it, dict):
            continue
        if it.get("pull_request"):
            continue
        body = str(it.get("body") or "")
        for m in re.finditer(r"vivid-sentry-issue:\s*(\d+)", body):
            existing_markers.add(m.group(1))

MARKER_TEMPLATE = "<!-- vivid-sentry-issue: %s -->"

def norm_head(s):
    return " ".join((s or "").split())

candidates = []
already_reported = 0
for it in issues:
    if not isinstance(it, dict):
        continue
    if it.get("status") == "resolved":
        continue
    sid = str(it.get("id", "?"))
    if sid in existing_markers:
        already_reported += 1
        continue
    title = str(it.get("title") or "Unbekannter Fehler")
    if len(title) > 120:
        title = title[:117] + "..."
    short = str(it.get("shortId") or ("SID-" + sid))
    level = str(it.get("level") or "error")
    metadata = it.get("metadata") or {}
    mtype = str(metadata.get("type") or "")
    mvalue = str(metadata.get("value") or "")
    mfunc = str(metadata.get("function") or "")
    mfile = str(metadata.get("filename") or "")
    top = (mfile and ("%s:%s" % (mfile, mfunc))) or mfunc or mtype or title
    permalink = str(it.get("permalink") or (
        "https://sentry.io/organizations/%s/issues/%s/?project=%s" % (
            os.environ["SENTRY_ISSUES_ORG"], sid, os.environ["SENTRY_ISSUES_PROJECT"])))
    count = str(it.get("count") or "0")
    users = str(it.get("userCount") or "0")
    first = str(it.get("firstSeen") or "-")
    last = str(it.get("lastSeen") or "-")

    tag_line = ""
    allowed = {"os", "device", "dist", "environment", "release"}
    vals = []
    for t in it.get("tags") or []:
        if isinstance(t, dict):
            k, v = str(t.get("key") or ""), str(t.get("value") or "")
        else:
            continue
        if k.lower() in allowed and v and v.lower() != "unknown":
            vals.append("%s=%s" % (k, v))
    if vals:
        joined = ", ".join(vals[:8])
        tag_line = "- **Tags:** %s" % (joined[:300])

    typeline = ""
    if mtype and mvalue:
        typeline = "- **Typ/Wert:** %s: %s" % (mtype, mvalue[:240])
    elif mtype:
        typeline = "- **Typ:** %s" % mtype

    body = "\n".join([
        MARKER_TEMPLATE % sid,
        "",
        "## Beschreibung / Description",
        "",
        "Sentry-Report: %s" % norm_head(short),
        "",
        "- **Top-Frame:** %s" % norm_head(top)[:200],
        typeline,
        "- **Sentry-Issue:** %s" % short,
        "- **Permalink:** %s" % permalink,
        "- **Level:** %s" % level,
        "- **Events:** %s (%s Nutzer betroffen)" % (count, users),
        "- **Erstmals gesehen:** %s" % first,
        "- **Zuletzt:** %s" % last,
        tag_line,
        "",
        "[Vollständigen Report in Sentry öffnen](%s)" % permalink,
        "",
        "## Schritte zum Reproduzieren / Steps to reproduce",
        "",
        "Stacktrace & Kontext im Sentry-Report (Permalink oben).",
        "",
        "## Weitere Infos / Additional context",
        "",
        "Automatisch erstellt vom Sentry-Issues-Watchdog (scripts/check_sentry_issues.sh, "
        "stündlicher Cron). Autorität: Org %s, Projekt %s (%s). Details: docs/sentry-issues.md." % (
            os.environ["SENTRY_ISSUES_ORG"], os.environ["SENTRY_ISSUES_PROJECT"], short),
    ])
    payload = {
        "title": "[Sentry] %s" % title,
        "body": body,
        "labels": ["sentry"],
    }
    candidates.append((sid, title, last, payload))

candidates.sort(key=lambda c: c[2])  # deterministisch: ältestes lastSeen zuerst

out = os.environ["SENTRY_ISSUES_TMP"]
for i, (sid, title, _last, payload) in enumerate(candidates, 1):
    with open(os.path.join(out, "issue_%d.json" % i), "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)
    print("CANDIDATE: %s → %s" % (sid, title))

print("Sentry: %d unresolved geprüft | bereits erfasst: %d | neu: %d" % (
    len(issues), already_reported, len(candidates)))
print("WOULD_ISSUE=%d" % len(candidates))
sys.exit(0)
PY
)
printf '%s\n' "$out_a"
if [ "$rc" -ne 0 ]; then
  exit "$rc"
fi

# Fehlerfälle daraus direkt weiterleiten (Fail-closed).
if printf '%s\n' "$out_a" | grep -q '^FEHLER:' ; then
  printf '%s\n' "$out_a" | grep '^FEHLER:'
  exit 1
fi

count=$(printf '%s\n' "$out_a" | grep -o 'WOULD_ISSUE=[0-9]*' | cut -d= -f2)
if [ -z "$count" ]; then
  echo "FEHLER: Phase-A-Output ohne WOULD_ISSUE-Marker — Script-Formatbruch?"
  exit 1
fi

if [ "$count" = "0" ]; then
  echo "OK: 0 neue Sentry-Issues — alle unresolved Reports sind bereits als GitHub-Issue erfasst (Watchdog: nichts zu tun)"
  exit 0
fi

if [ "$dryrun" = "1" ]; then
  echo "OK (dry-run): $count Issue(s) würden neu als GitHub-Issue erstellt — keine Erstellung ausgeführt"
  exit 0
fi

# ── Phase B: GitHub-Issues erstellen (live) bzw. Status simulieren (Fixture) ──
created=0
for i in $(seq 1 "$count"); do
  payload="$tmp/issue_$i.json"
  if [ -n "$FIXTURE" ]; then
    ghstatus=$(cat "$FIXTURE/ghstatus" 2>/dev/null || echo 201)
  else
    ghstatus=$(curl -sS -o "$tmp/gh_create_$i.json" -w '%{http_code}' \
      -X POST -H "Authorization: Bearer ${GITHUB_ISSUES_TOKEN:-}" \
      -H "Accept: application/vnd.github+json" -H "Content-Type: application/json" \
      -d @"$payload" "$GH_API/repos/$GH_REPO/issues" 2>/dev/null || echo 000)
  fi
  case "$ghstatus" in
    200|201)
      num=$(grep -o '"number": *[0-9]*' "$tmp/gh_create_$i.json" 2>/dev/null | head -1 | grep -o '[0-9]*' || true)
      echo "ISSUE: #${num:-?} erstellt — $(python -X utf8 -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['title'])" "$payload")"
      created=$((created + 1)) ;;
    401|403) skip_gh_status "$ghstatus" ;;
    422)
      echo "FEHLER: GitHub lehnt Issue-Payload ab (HTTP 422) — Payload/Repo-Konvention prüfen (Label 'sentry', Titel-Länge)"
      echo "Verfügbare Secrets/Endpoints sind NICHT betroffen — Details in $tmp/gh_create_$i.json"
      exit 1 ;;
    *) skip_gh_status "$ghstatus" ;;
  esac
done

echo "OK: $created Issue(s) neu als GitHub-Issue erstellt (Watchdog) — Sentry-Triage labelt automatisch nach (severity/crash)."
exit 0