#!/usr/bin/env bash
# Selbsttest: Suppressions-Register-Review-Workflow
# (.github/workflows/automation-suppressions-register.yml, offline).
#
# Prüft die Verträge, die den Workflow sicher und idempotent machen:
#   W1: YAML ist gültig
#   W2: Monatlicher Cron (Tag 11) + workflow_dispatch-Trigger
#   W3: permissions: {} top-level, Job nur issues: write (Minimalprivileg)
#   W4: Alle Actions SHA-gepinnt (checkout, github-script)
#   W5: Guard läuft im Live-Modus (kein SUPPRESSIONS_GUARD_NO_NETWORK im Workflow)
#   W6: Guard-Fehler wird getragen (continue-on-error) und an den Issue-Step
#       weitergereicht (steps.guard.outcome)
#   W7: Issue-Titel + Idempotenz-Marker sind vorhanden (reopen-Loop-Schutz:
#       bestehendes Issue wird kommentiert statt neu erstellt)
#   W8: Erfolgsfall schließt das Review-Issue (state: closed)
#   W9: Der Guard existiert und ist Teil des Pre-Push-Gates (Konsistenz
#       zwischen Workflow und Gate)
#
# Exit 0 = alle Checks bestanden, Exit 1 = mindestens ein Check fehlgeschlagen.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF="$ROOT/.github/workflows/automation-suppressions-register.yml"
GUARD="$ROOT/scripts/check_suppressions_register.sh"
GATE="$ROOT/scripts/pre-push.sh"
PASS=0
FAIL=0

ok()  { echo "PASS W$1: $2"; PASS=$((PASS + 1)); }
bad() { echo "FAIL W$1: $2"; FAIL=$((FAIL + 1)); }

grep_q() { grep -q -- "$2" "$1" 2>/dev/null; }

# W1: YAML-Validierung (python3 + yaml, sonst Struktur-Grobcheck)
if python3 -c "import yaml" 2>/dev/null; then
    python3 - "$WF" <<'PYEOF' && ok 1 "YAML gültig" || bad 1 "YAML ungültig"
import sys, yaml
yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
PYEOF
else
    grep_q "$WF" "jobs:" && grep_q "$WF" "runs-on: ubuntu-latest" \
        && ok 1 "YAML-Struktur (Grobcheck, kein PyYAML)" \
        || bad 1 "YAML-Struktur (Grobcheck)"
fi

# W2: Cron + workflow_dispatch
grep_q "$WF" "cron: '0 6 11 \* \*'" && ok 2 "monatlicher Cron (Tag 11, 06:00 UTC)" \
    || bad 2 "Cron fehlt/abweichend"
grep_q "$WF" "workflow_dispatch:" && ok 2 "workflow_dispatch vorhanden" \
    || bad 2 "workflow_dispatch fehlt"

# W3: Minimalprivilegien
grep_q "$WF" "permissions: {}" && ok 3 "permissions: {} top-level" \
    || bad 3 "top-level permissions fehlt"
grep_q "$WF" "issues: write" && ok 3 "Job-Permission issues: write" \
    || bad 3 "Job-Permission fehlt"
if grep -E "contents: (read|write)" "$WF" >/dev/null 2>&1; then
    bad 3 "unnoetige contents-Permission"
else
    ok 3 "keine contents-Permission (Issue-only)"
fi

# W4: SHA-Pinning
SHA_COUNT=$(grep -cE "uses: .+@[0-9a-f]{40}" "$WF" || true)
USES_COUNT=$(grep -cE "^\s*uses:" "$WF" || true)
[ "$SHA_COUNT" -eq "$USES_COUNT" ] && [ "$USES_COUNT" -gt 0 ] \
    && ok 4 "alle $USES_COUNT Actions SHA-gepinnt" \
    || bad 4 "nicht alle Actions SHA-gepinnt ($SHA_COUNT/$USES_COUNT)"

# W5: Live-Modus (kein Offline-Override im Workflow)
if grep_q "$WF" "SUPPRESSIONS_GUARD_NO_NETWORK"; then
    bad 5 "Offline-Override im Workflow — Guard würde Live-Gegenprobe überspringen"
else
    ok 5 "Guard läuft im Live-Modus"
fi

# W6: Fehlerfortführung + Outcome-Weiterreichung
grep_q "$WF" "continue-on-error: true" && ok 6 "Guard-Fehler wird getragen" \
    || bad 6 "continue-on-error fehlt (Workflow würde vor Issue-Step abbrechen)"
grep -q 'steps.guard.outcome' "$WF" && ok 6 "outcome an Issue-Step weitergereicht" \
    || bad 6 "steps.guard.outcome fehlt"

# W7: Idempotenz (Titel + Marker)
grep_q "$WF" "Suppressions-Register: monatlicher Review" && ok 7 "Issue-Titel vorhanden" \
    || bad 7 "Issue-Titel fehlt"
grep_q "$WF" "suppressions-register-review" && ok 7 "Idempotenz-Marker vorhanden" \
    || bad 7 "Idempotenz-Marker fehlt"

# W8: Erfolgsfall schließt das Issue
grep -q "state: 'closed'" "$WF" && ok 8 "Erfolgsfall schließt Review-Issue" \
    || bad 8 "Issue-Close fehlt"

# W9: Guard + Gate-Konsistenz
[ -f "$GUARD" ] && ok 9 "Guard existiert" || bad 9 "Guard fehlt"
grep_q "$GATE" "check_suppressions_register.sh" \
    && ok 9 "Guard ist Teil des Pre-Push-Gates" \
    || bad 9 "Guard nicht im Pre-Push-Gate verdrahtet"

echo
echo "$PASS PASS, $FAIL FAIL"
[ "$FAIL" -eq 0 ] || exit 1
