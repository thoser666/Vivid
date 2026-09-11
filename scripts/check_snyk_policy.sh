#!/usr/bin/env bash
# Guard: Snyk-Policy-Hygiene (.snyk) + Workflow-Verdrahtung.
#
# Hintergrund: SNYK-RUBY-RUBYZIP-19666145 (Directory Traversal, Snyk-Research
# ohne CVE/GHSA — OSV listet nur die Alt-CVEs 2017–2019) ist in rubyzip 2.4.1
# nicht behebbar: fastlane pinnt 'rubyzip >= 2.0.0, < 3.0.0', der Fix liegt erst
# in 3.4.0+. Der Ignore in .snyk ist die sanktionierte Lösung; dieser Guard
# verhindert, dass er verrottet:
#   1. Jeder Ignore-Eintrag braucht einen reason (>= 40 Zeichen) und ein
#      zukünftiges expires-Datum.
#   2. Abgelaufene Einträge sind ein harter Fehler (Ignorieren gilt nie
#      unbegrenzt — bewusste Reassess-Pflicht).
#   3. security-snyk.yml muss die Policy an test UND monitor übergeben
#      (--policy-path), sonst ist der Ignore wirkungslos (bekanntes
#      --all-projects-Verhalten).
#   4. 'patch:'-Direktiven sind verboten (erzeugen nicht-committbare Artefakte).
#   5. Advisory (online): lockert fastlane den rubyzip-Constraint auf 3.x,
#      wird gewarnt — dann Ignore entfernen und bündeln.
#
# Umgebungsvariablen für den Offline-Selbsttest:
#   SNYK_POLICY_FILE   — alternative .snyk-Datei (Fixture)
#   SNYK_WORKFLOW_FILE — alternativer Workflow (Fixture)
#   SNYK_GUARD_NOW     — festes 'heutiges' Datum (ISO 8601) statt date -u
#   SNYK_GUARD_NO_NET  — Skip-Flag für den Online-Fastlane-Locker-Check (CI)
#   FASTLANE_LOCKED    — Override für den Online-Check (Fixture-Antwort)
#   FASTLANE_URL       — alternative rubygems.org-URL
set -euo pipefail

POLICY_FILE="${SNYK_POLICY_FILE:-.snyk}"
WORKFLOW_FILE="${SNYK_WORKFLOW_FILE:-.github/workflows/security-snyk.yml}"
TODAY="${SNYK_GUARD_NOW:-$(date -u +%F)}"
SKIP_NET="${SNYK_GUARD_NO_NET:-}"

fail() { echo "❌ [snyk-policy-guard] $1" >&2; exit 1; }
warn() { echo "::warning::[snyk-policy] $1"; }

PY="$(command -v python3 || command -v python || true)"
[ -n "$PY" ] || fail "weder python3 noch python gefunden (Precondition des Pre-Push-Gates)"

[ -s "$POLICY_FILE" ] || fail "Snyk-Policy $POLICY_FILE fehlt oder ist leer"

# --- Direktiven-Whitelist ---------------------------------------------------
# 'patch: {}' (leer) ist Teil der kanonischen, von 'snyk ignore' generierten
# Form und erlaubt; nicht-leere patch-Einträge erzeugen nicht-committbare
# Artefakte und sind verboten.
if grep -Eq '^[[:space:]]*patch:' "$POLICY_FILE" && \
   ! grep -Eq '^[[:space:]]*patch:[[:space:]]*\{\}[[:space:]]*$' "$POLICY_FILE"; then
  fail "nicht-leere 'patch:'-Direktive ist nicht erlaubt (erzeugt Artefakte, nicht committbar)"
fi
grep -Eq '^version:' "$POLICY_FILE" || fail "Policy ohne version:-Schlüssel"
grep -Eq '^ignore:' "$POLICY_FILE" || fail "Policy ohne ignore:-Sektion"

# --- Ignore-Einträge: reason (>= 40 Zeichen) + zukünftiges expires -----------
policy_out="$("$PY" - "$POLICY_FILE" "$TODAY" <<'PYEOF'
import re
import sys
from datetime import datetime, timezone

policy = open(sys.argv[1], encoding="utf-8").read()
today = sys.argv[2]

entries = re.findall(r"^  (SNYK-[A-Z0-9-]+):\n((?:[ \t]+.*\n?)*)", policy, re.M)
if not entries:
    print("❌ [snyk-policy-guard] kein Ignore-Eintrag (SNYK-*) in der Policy", file=sys.stderr)
    sys.exit(1)

def parse_date(raw):
    raw = raw.strip().strip("'\"")
    m = re.match(r"(\d{4}-\d{2}-\d{2})", raw)
    if not m:
        return None
    try:
        return datetime.strptime(m.group(1), "%Y-%m-%d").replace(tzinfo=timezone.utc)
    except ValueError:
        return None

for sid, body in entries:
    lines = body.splitlines()
    reason_parts = []
    collecting = False
    reason_indent = 0
    for line in lines:
        # Kanonische Policy-Form: 'reason:' unter einem '*:'-Pfadschlüssel;
        # kompakte Form: '- reason:'. Beide akzeptieren wir.
        m = re.match(r"^(\s*)-?\s*reason:\s*(\S.*)$", line)
        if m and not collecting:
            reason_parts.append(m.group(2))
            collecting = True
            reason_indent = len(m.group(1)) + 1  # Inhalt muss tiefer eingerückt sein als der Key
            continue
        if collecting:
            stripped = line.strip()
            if not stripped:
                continue
            if re.match(r"^\s*expires:", line):
                collecting = False
                continue
            if len(line) - len(line.lstrip()) > reason_indent:
                reason_parts.append(stripped)
            else:
                collecting = False
    reason = " ".join(reason_parts)
    if len(reason) < 40:
        print(f"❌ [snyk-policy-guard] Ignore {sid}: reason fehlt oder ist zu kurz (< 40 Zeichen) — Begründung ist Pflicht", file=sys.stderr)
        sys.exit(1)

    exp_m = re.search(r"expires:\s*'?\"?([0-9T:.Zz+-]+)", body)
    if not exp_m:
        print(f"❌ [snyk-policy-guard] Ignore {sid}: kein expires-Datum", file=sys.stderr)
        sys.exit(1)
    exp = parse_date(exp_m.group(1))
    now = parse_date(today)
    if exp is None or now is None:
        print(f"❌ [snyk-policy-guard] Ignore {sid}: expires nicht parsbar ({exp_m.group(1)})", file=sys.stderr)
        sys.exit(1)
    if exp <= now:
        print(f"❌ [snyk-policy-guard] Ignore {sid}: expires {exp.date()} ist abgelaufen (heute: {now.date()}) — Eintrag neu bewerten und erneuern oder entfernen", file=sys.stderr)
        sys.exit(1)
print("policy-ok")
PYEOF
)" || exit 1

# --- Workflow-Verdrahtung: --policy-path an test UND monitor ----------------
[ -s "$WORKFLOW_FILE" ] || fail "Snyk-Workflow $WORKFLOW_FILE fehlt oder ist leer"
grep -Fq -- '--policy-path="$GITHUB_WORKSPACE/.snyk"' "$WORKFLOW_FILE" \
  || fail 'security-snyk.yml übergibt die Policy nicht (--policy-path="$GITHUB_WORKSPACE/.snyk") — der Ignore wäre wirkungslos'
policy_refs="$(grep -cF -- '--policy-path="$GITHUB_WORKSPACE/.snyk"' "$WORKFLOW_FILE")"
[ "$policy_refs" -ge 2 ] || fail "Policy wird nur ${policy_refs}× referenziert — test UND monitor brauchen --policy-path"

# --- Online-Check: Ist die Sperre bei fastlane noch nötig? -------------------
if [ -z "$SKIP_NET" ] && [ -z "${FASTLANE_LOCKED:-}" ]; then
  FASTLANE_URL="${FASTLANE_URL:-https://rubygems.org/api/v2/rubygems/fastlane/versions/latest.json}"
  latest_json="$(curl -sfL --max-time 20 "$FASTLANE_URL" 2>/dev/null || printf '')"
else
  latest_json="${FASTLANE_LOCKED:-}"
fi

if [ -n "$latest_json" ]; then
  constraint="$("$PY" - "$latest_json" <<'PYEOF'
import json, sys
try:
    d = json.loads(sys.argv[1])
    deps = (d.get('dependencies') or {}).get('runtime') or []
    print(next((x.get('requirements', '') for x in deps if 'rubyzip' in str(x.get('name', ''))), ''))
except Exception:
    print('')
PYEOF
)" 2>/dev/null || constraint=""
  if printf '%s' "$constraint" | grep -qE '(>=|~>)[[:space:]]*3\.'; then
    warn "fastlane erlaubt jetzt rubyzip 3.x ('$constraint') — Snyk-Ignore SNYK-RUBY-RUBYZIP-19666145 entfernen, Gemfile aktualisieren und bündeln!"
  fi
fi

echo "✅ [snyk-policy-guard] Policy, Expiry und Workflow-Verdrahtung sind konsistent."
