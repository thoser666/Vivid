#!/usr/bin/env bash
# Sentry-Resolve-Guard (Vivid): schließt erledigte Sentry-Issues automatisch mit
# der nächsten Version (Weg-3-Automation, Auftrag 25.09.2026).
#
# Vertrag (--version): Ein Issue gilt als erledigt, wenn seine EVENTS den Tag
#   fix-release: <version>
# tragen (Key fix-release, Wert = Version ohne führendes 'v', case-insensitiv —
# z. B. "0.6.0-beta" für den Release-Tag v0.6.0-beta). Beim Stable-Publish einer
# Version ruft der Workflow distribution-stable.yml dieses Script mit
# --version "$TAG" auf; alle Issues mit passendem Tag werden per
# Sentry-Bulk-Update als resolved/inNextRelease markiert. Sentry schließt sie
# automatisch, sobald Events aus dieser Release eintreffen (Status-Detail
# "inNextRelease", siehe Sentry-API "Bulk update issues": PUT /projects/{org}/
# {project}/issues/?id=…&id=…).
#
# WICHTIG (Befund 01.11.2026): Der Tag ist Event-Daten, kein Issue-Attribut —
# Sentry bietet weder im Dashboard noch per API eine Möglichkeit, ihn an ein
# BESTEHENDES Issue zu hängen (der Bulk-PUT akzeptiert nur status/statusDetails/
# ignoreDuration/isPublic/merge/assignedTo/hasSeen/isBookmarked; offener
# Feature-Request: getsentry/sentry#5572). Ohne ein App-seitiges Sentry.setTag
# findet dieser Modus folglich nichts (OK: 0 Issues) — das ist der Vertrag,
# kein Defekt. Der Weg am Release-Schnitt ist der direkte Bulk-PUT mit den
# Issue-IDs; das Aufruf-Rezept steht in docs/sentry-stats.md Abschnitt 6
# (Punkt 4). Dafuer kennt der Guard den Modus --ids <id[,id…]>: er resolvt die
# genannten IDs direkt (Status "resolved", OHNE inNextRelease) — fuer Befunde,
# die nicht "in der naechsten Version behoben", sondern als erwarteter
# Betriebszustand bzw. "kein Defekt" geschlossen werden (docs/sentry-issues.md
# §5.10). Der Workflow automation-sentry-resolve.yml dispatcht genau diesen
# Modus; der Tag-Vertrag (--version) bleibt unveraendert daneben bestehen.
#
# --ids kennt zusaetzlich --in-next-release (seit 27.11.2026): dann setzt der
# Bulk-PUT dasselbe Status-Detail wie der Versions-Pfad (resolved/inNextRelease).
# Gedacht fuer Befunde, deren Fix mit einem konkreten Release erstmals
# ausgeliefert wurde, ohne dass ein fix-release-Event-Tag existiert (#429 /
# VIVID-5Q: Fix 1b74d05 erstmals in v0.5.51-beta). Ohne das Flag bleibt --ids
# beim blossen "resolved" — fuer "kein Defekt"-Entscheide weiterhin richtig.
# Nur zusammen mit --ids; der --version-Pfad setzt inNextRelease immer.
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
# SENTRY_RESOLVE_BODY_OUT=<datei> kopiert den erzeugten PUT-Body dorthin
# (Selbsttest-Hook, damit die inNextRelease-Zusage am JSON haengt).
set -euo pipefail
# Repo-Root: normalerweise über $0. Aus $RUNNER_TEMP (Stable-Publish pflegt
# die Gate-Skripte dort, damit der Arbeitsbaum nicht vom Ziel-Tag stammt —
# Vorfall #250) ist $0/.. kein Repo mehr, dann muss VIVID_REPO_ROOT gesetzt
# sein. Ohne gesetzte Variable bleibt das alte Verhalten exakt erhalten.
cd "${VIVID_REPO_ROOT:-$(dirname "$0")/..}"

ORG="${SENTRY_ORG:-privat-jb}"
PROJECT_SLUG="${SENTRY_PROJECT:-vivid}"
DOCS_HINT="docs/sentry-stats.md"
FIXTURE="${SENTRY_RESOLVE_FIXTURE:-}"
PROPS="${SENTRY_PROPERTIES_FILE:-sentry.properties}"
API="https://sentry.io/api/0"

# Pfadform fuer native Python-Aufrufe: MSYS/Git-Bash wandelt in einer
# MEHRTEILIGEN Env-Liste nur den ersten Eintrag nach C:/… um — die restlichen
# bleiben /tmp/… und sind fuer python.exe unlesbar ("No such file or directory"
# auf eine existierende Datei, Selbsttest R23). Darum jeden Pfad selbst
# umformen; auf Linux (kein cygpath) bleibt der Pfad unveraendert.
py_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

version=""
ids=""
dryrun=0
in_next=0
print_src=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) version="${2:-}"; shift 2 ;;
    --ids) ids="${2:-}"; shift 2 ;;
    --in-next-release) in_next=1; shift ;;
    --dry-run) dryrun=1; shift ;;
    --print-token-source) print_src=1; shift ;;
    *)
      echo "FEHLER: unbekanntes Argument: $1 (Aufruf: scripts/check_sentry_resolve.sh (--version <vX.Y.Z[..]> | --ids <id[,id…]> [--in-next-release]) [--dry-run])"
      exit 1 ;;
  esac
done

# Genau ein Auswahl-Modus: --version (Event-Tag-Vertrag) oder --ids (Triage-
# Entscheid beim Release-Schnitt), nie beide.
if [ -n "$version" ] && [ -n "$ids" ]; then
  echo "FEHLER: --version und --ids schliessen sich aus (genau ein Auswahl-Modus)"
  exit 1
fi
# --in-next-release ist nur eine Verfeinerung von --ids: der Versions-Pfad
# setzt inNextRelease ohnehin, ein alleinstehendes Flag waere ein Aufruffehler.
# Bewusst VOR der Auswahl-Pruefung: sonst faengt der generische "--version oder
# --ids fehlt"-Zweig das Flag ab und der Aufruffehler bleibt unspezifisch
# (genau daran lief die Mutationsprobe B zunaechst gruen durch).
if [ "$in_next" = "1" ] && [ -z "$ids" ]; then
  echo "FEHLER: --in-next-release braucht --ids (der --version-Pfad setzt inNextRelease ohnehin)"
  exit 1
fi
if [ -z "$version" ] && [ -z "$ids" ] && [ "$print_src" != "1" ]; then
  echo "FEHLER: --version <vX.Y.Z> oder --ids <id[,id…]> fehlt (Release-Tag bzw. Sentry-Issue-IDs)"
  exit 1
fi

# ID-Liste normalisieren: Whitespace weg, leere Glieder weg, rein numerisch
# (Sentry-IDs sind Zahlen; alles andere ist ein Aufruffehler, kein API-Fall).
ids_norm=""
if [ -n "$ids" ]; then
  ids_norm=$(printf '%s' "$ids" | tr -d ' \t' | tr ',' '\n' | sed '/^$/d' | paste -sd, -)
  if [ -z "$ids_norm" ]; then
    echo "FEHLER: --ids braucht mindestens eine Sentry-Issue-ID (z. B. --ids 7821630074,7821627754)"
    exit 1
  fi
  case "$ids_norm" in
    *[!0-9,]*)
      echo "FEHLER: --ids enthaelt ein Nicht-Zahlen-Glied: $ids_norm (erwartet z. B. 7821630074,7821627754)"
      exit 1 ;;
  esac
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

# Header-Werte duerfen keine Steuerzeichen enthalten: ein Token mit
# angehaengtem Zeilenumbruch (Secret per Copy&Paste angelegt) macht den
# Authorization-Header ungueltig — nginx/Sentry sah dann eine LEERE
# Content-Type ("Unsupported media type \"\"", HTTP 415), Python bricht mit
# "Invalid header value" ab (Befunde 41303884737 / 41304202788). Der Umbruch
# wird deshalb vor jeder Verwendung entfernt, unabhaengig von der Quelle.
token=$(printf '%s' "$token" | tr -d '\r\n')

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
  # --ids: Einzelantworten der genannten IDs (issue-<id>.json) mitlesen — so
  # erreicht der Modus auch Issues, die die is:unresolved-Abfrage ausblendet.
  if [ -n "$ids_norm" ]; then
    for id in $(printf '%s' "$ids_norm" | tr ',' ' '); do
      if [ -f "$FIXTURE/issue-$id.json" ]; then
        frag_files="$frag_files $FIXTURE/issue-$id.json"
      fi
    done
  fi
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
  # --ids: die genannten IDs einzeln nachladen. Die is:unresolved-Abfrage
  # blendet bereits resolved Issues aus — genau die sollen aber auf
  # inNextRelease hochgezogen werden koennen (#429/VIVID-5Q war schon resolved).
  # 404 = unbekannte ID; die meldet Phase A als fehlend, kein Abbruch.
  if [ -n "$ids_norm" ]; then
    : > "$tmp/idfrags"
    for id in $(printf '%s' "$ids_norm" | tr ',' ' '); do
      code=$(curl -sS -o "$tmp/id.$id.json" -w '%{http_code}' \
        -H "Authorization: Bearer $token" "$API/issues/$id/" 2>/dev/null || echo 000)
      case "$code" in
        200) echo "$tmp/id.$id.json" >> "$tmp/idfrags" ;;
        404) : ;;
        *) skip_getstatus "$code" ;;
      esac
    done
    frag_files="$frag_files $(cat "$tmp/idfrags")"
  fi
fi

# Jeden Fragment-Pfad einzeln in die Python-Pfadform bringen (siehe py_path).
norm_frags=""
for f in $frag_files; do norm_frags="$norm_frags $(py_path "$f")"; done
frag_files="$norm_frags"

# ── Phase A: Matches offline berechnen (kein Netz). Liefert bei Treffern die
# PUT-Url/-Body als Dateien + Marker WOULD_RESOLVE=N; bei 0 Treffern ein OK.
rc=0
out_a=$(SENTRY_RESOLVE_FRAG_FILES="$frag_files" \
  SENTRY_RESOLVE_VERSION="$norm_version" \
  SENTRY_RESOLVE_VERSION_RAW="$version" \
  SENTRY_RESOLVE_IDS="$ids_norm" \
  SENTRY_RESOLVE_IN_NEXT="$in_next" \
  SENTRY_RESOLVE_API="$API" \
  SENTRY_RESOLVE_ORG="$ORG" \
  SENTRY_RESOLVE_PROJECT="$PROJECT_SLUG" \
  SENTRY_RESOLVE_TMP="$tmp" python -X utf8 - 2>/dev/null <<'PY' || rc=$?
import json, os, sys

version = os.environ["SENTRY_RESOLVE_VERSION"]
version_raw = os.environ["SENTRY_RESOLVE_VERSION_RAW"]
wanted_ids = [i for i in os.environ.get("SENTRY_RESOLVE_IDS", "").split(",") if i]
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
    if isinstance(data, dict):
        # Einzelabruf GET /issues/{id}/ liefert EIN Objekt statt einer Liste;
        # eine Antwort ohne id/status bleibt dagegen ein Formatbruch (R5).
        if "id" in data and "status" in data:
            data = [data]
        else:
            print("FEHLER: Issues-Antwort (%s) ist weder Liste noch Issue-Objekt — Sentry-API-Feldformat geändert?" % f)
            sys.exit(1)
    if not isinstance(data, list):
        print("FEHLER: Issues-Antwort (%s) ist keine Liste — Sentry-API-Feldformat geändert?" % f)
        sys.exit(1)
    issues.extend(data)

matches = []
already_next = []
seen = set()
in_next = os.environ.get("SENTRY_RESOLVE_IN_NEXT") == "1"
for it in issues:
    if not isinstance(it, dict):
        continue
    if wanted_ids:
        # Explizit genannte IDs duerfen auch schon resolved sein: nur so laesst
        # sich ein blosses "resolved" auf resolved/inNextRelease hochziehen
        # (#429). "ignored" bleibt aussen vor, und ohne --in-next-release wird
        # ein resolved Issue nicht angefasst — der Plain-Vertrag bleibt.
        issue_id = str(it.get("id", ""))
        if issue_id not in wanted_ids or issue_id in seen:
            continue
        status = it.get("status")
        if status == "ignored":
            continue
        if status == "resolved":
            details = it.get("statusDetails") or {}
            if details.get("inNextRelease") is True:
                already_next.append((issue_id, str(it.get("title", "?"))))
                seen.add(issue_id)
                continue
            if not in_next:
                continue
        seen.add(issue_id)
        matches.append((issue_id, str(it.get("title", "?"))))
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

if wanted_ids:
    print("Projekt: %s (Org %s) | Auswahl: --ids %s (%d ID(s))" % (
        os.environ["SENTRY_RESOLVE_PROJECT"], os.environ["SENTRY_RESOLVE_ORG"],
        ",".join(wanted_ids), len(wanted_ids)))
else:
    print("Projekt: %s (Org %s) | Version: %s (normalisiert: %s)" % (
        os.environ["SENTRY_RESOLVE_PROJECT"], os.environ["SENTRY_RESOLVE_ORG"], version_raw, version))
print("Geprüft: %d unresolved Issues (query=is:unresolved)" % len(issues))
if wanted_ids:
    hit = set(i for i, _ in matches) | set(i for i, _ in already_next)
    missing = [i for i in wanted_ids if i not in hit]
    if already_next:
        print("HINWEIS: %d ID(s) sind bereits resolved/inNextRelease — kein PUT noetig: %s"
              % (len(already_next), ",".join(i for i, _ in already_next)))
    if missing:
        print("HINWEIS: %d ID(s) nicht unter den unresolved Issues (schon resolved/ignored oder falsche ID): %s"
              % (len(missing), ",".join(missing)))
if not matches:
    if wanted_ids:
        print("OK: 0 Issues aus --ids — nichts zu resolven (%d unresolved geprüft)" % len(issues))
    else:
        print("OK: 0 Issues mit Tag fix-release:%s — nichts zu resolven (%d unresolved geprüft)" % (version, len(issues)))
    sys.exit(0)

ids = [i for (i, _) in matches]
url = "%s/projects/%s/%s/issues/?id=%s" % (
    os.environ["SENTRY_RESOLVE_API"], os.environ["SENTRY_RESOLVE_ORG"],
    os.environ["SENTRY_RESOLVE_PROJECT"], "&id=".join(ids))
if wanted_ids:
    body_obj = {"status": "resolved"}
    if os.environ.get("SENTRY_RESOLVE_IN_NEXT") == "1":
        body_obj["statusDetails"] = {"inNextRelease": True}
    body = json.dumps(body_obj)
else:
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

# Test-Hook (nur fuer den Offline-Selbsttest R19/R20): Body sichtbar machen,
# damit die inNextRelease-Zusage am gesendeten JSON haengt und nicht nur am
# Meldungstext. Ohne gesetzte Variable passiert nichts.
if [ -n "${SENTRY_RESOLVE_BODY_OUT:-}" ]; then
  cp "$tmp/put_body" "$SENTRY_RESOLVE_BODY_OUT"
fi

if [ "$dryrun" = "1" ]; then
  if [ -n "$ids_norm" ] && [ "$in_next" = "1" ]; then
    echo "OK (dry-run): $count Issue(s) würden als resolved (inNextRelease) markiert — IDs: $ids"
  elif [ -n "$ids_norm" ]; then
    echo "OK (dry-run): $count Issue(s) würden als resolved markiert (ohne inNextRelease) — IDs: $ids"
  else
    echo "OK (dry-run): $count Issue(s) würden als resolved (inNextRelease) markiert — Version $norm_version (IDs: $ids)"
  fi
  exit 0
fi

# ── Phase B: Bulk-PUT ausführen (live) bzw. Status simulieren (Fixture).
if [ -n "$FIXTURE" ]; then
  putstatus=$(cat "$FIXTURE/status" 2>/dev/null || echo 200)
else
  # PUT bewusst ueber Python statt curl: der Dispatch-Lauf 41303884737 bekam
  # fuer denselben Aufruf ein `415 Unsupported media type ""` von Sentry,
  # obwohl curl die Header korrekt setzt (lokal gegen einen Echo-Server
  # verifiziert). urllib setzt Header und Body deterministisch und legt im
  # Fehlerfall genau das ab, was gesendet wurde.
  putstatus=$(SENTRY_PUT_URL="$(cat "$tmp/put_url")" \
    SENTRY_PUT_BODY_FILE="$tmp/put_body" \
    SENTRY_PUT_TOKEN="$token" \
    SENTRY_PUT_OUT="/tmp/sentry_resolve_put.$$.json" \
    python -X utf8 - 2>/dev/null <<'PY' || echo 000
import os, urllib.error, urllib.request

url = os.environ["SENTRY_PUT_URL"]
body = open(os.environ["SENTRY_PUT_BODY_FILE"], "rb").read()
req = urllib.request.Request(url, data=body, method="PUT")
req.add_header("Authorization", "Bearer " + os.environ["SENTRY_PUT_TOKEN"])
req.add_header("Content-Type", "application/json")
req.add_header("Accept", "application/json")
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        print(resp.status)
        open(os.environ["SENTRY_PUT_OUT"], "wb").write(resp.read())
except urllib.error.HTTPError as err:
    print(err.code)
    open(os.environ["SENTRY_PUT_OUT"], "wb").write(err.read())
except Exception as exc:  # Netz/DNS/Timeout -> wie curl 000
    print("000")
    open(os.environ["SENTRY_PUT_OUT"], "wb").write(
        ("client-error: %s" % exc).encode("utf-8", "replace"))
PY
)
fi

case "$putstatus" in
  200|204)
    if [ -n "$ids_norm" ] && [ "$in_next" = "1" ]; then
      echo "OK: $count Issue(s) als resolved (inNextRelease) markiert — IDs: $ids"
    elif [ -n "$ids_norm" ]; then
      echo "OK: $count Issue(s) als resolved markiert (ohne inNextRelease) — IDs: $ids"
    else
      echo "OK: $count Issue(s) als resolved (inNextRelease) markiert — erledigt mit Version $norm_version (IDs: $ids)"
    fi
    exit 0 ;;
  *)
    # Fehlerantwort sichtbar machen (ohne Token): ein nacktes "unerwarteter
    # HTTP-Status" ist nicht diagnostizierbar — der Dispatch-Lauf 41303762451
    # endete mit 415 ohne jede Begruendung. Body gekuerzt/einzeilig, damit er
    # im CI-Log lesbar bleibt; Fixture-Laeufe haben keine Antwortdatei.
    if [ -z "$FIXTURE" ] && [ -s "/tmp/sentry_resolve_put.$$.json" ]; then
      echo "API-ANTWORT: $(tr '
' ' ' < "/tmp/sentry_resolve_put.$$.json" | cut -c1-300)"
    fi
    skip_putstatus "$putstatus" ;;
esac