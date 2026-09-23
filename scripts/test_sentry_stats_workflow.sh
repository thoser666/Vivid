#!/usr/bin/env bash
# Selbsttest: Sentry-Stats-Review-Workflow
# (.github/workflows/automation-sentry-stats.yml, offline).
#
# Prüft die Verträge, die den Workflow sicher und idempotent machen:
#   W1: YAML ist gültig
#   W2: Monatlicher Cron (Tag 9) + workflow_dispatch
#   W3: permissions: {} top-level, Job nur issues: write (Minimalprivileg)
#   W4: Alle Actions SHA-gepinnt (checkout, github-script)
#   W5: Guard existiert, wird per tee + PIPESTATUS getragen und bekommt das
#       Secret SENTRY_STATS_TOKEN
#   W6: Guard-Ergebnis wird getragen (continue-on-error) und an den Issue-Step
#       weitergereicht (steps.guard.outcome)
#   W7: Beide Issue-Titel + Idempotenz-Marker vorhanden (reopen-Loop-Schutz)
#   W8: Erfolgs-/Neutralfall schließt offene Check-Issues (state: closed)
#   W9: Der Guard-Selbsttest ist Teil des Pre-Push-Gates (Konsistenz
#       zwischen Workflow und Gate)
#
# Exit 0 = alle Checks bestanden, Exit 1 = mindestens ein Check fehlgeschlagen.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF="$ROOT/.github/workflows/automation-sentry-stats.yml"
GUARD="$ROOT/scripts/check_sentry_stats.sh"
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
grep_q "$WF" "cron: '0 6 9 \* \*'" && ok 2 "monatlicher Cron (Tag 9, 06:00 UTC)" \
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

# W4: Actions SHA-gepinnt
grep_q "$WF" "actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683" \
    && ok 4 "checkout SHA-gepinnt" || bad 4 "checkout nicht SHA-gepinnt"
grep_q "$WF" "actions/github-script@60a0d83039c74a4aee543508d2ffcb1c3799cdea" \
    && ok 4 "github-script SHA-gepinnt" || bad 4 "github-script nicht SHA-gepinnt"

# W5: Guard-Verdrahtung (tee + PIPESTATUS) + Secret
[[ -s "$GUARD" ]] && ok 5 "Guard-Skript existiert" || bad 5 "Guard-Skript fehlt"
grep_q "$WF" "bash scripts/check_sentry_stats.sh 2>&1 | tee guard-output.txt" \
    && ok 5 "Guard-Ausgabe wird getragen (tee)" \
    || bad 5 "Guard-Ausgabe wird nicht getragen"
grep_q "$WF" 'exit "${PIPESTATUS\[0\]}"' \
    && ok 5 "Guard-Exit-Code wird durchgereicht (PIPESTATUS)" \
    || bad 5 "PIPESTATUS-Weiterreichung fehlt"
grep_q "$WF" 'SENTRY_STATS_TOKEN: ${{ secrets.SENTRY_STATS_TOKEN }}' \
    && ok 5 "Secret SENTRY_STATS_TOKEN verdrahtet" \
    || bad 5 "Secret-Verdrahtung fehlt"

# W6: continue-on-error + outcome
grep_q "$WF" "continue-on-error: true" \
    && ok 6 "Guard-Fehler wird getragen (continue-on-error)" \
    || bad 6 "continue-on-error fehlt"
grep_q "$WF" "steps.guard.outcome" \
    && ok 6 "Outcome wird an den Issue-Step weitergereicht" \
    || bad 6 "steps.guard.outcome fehlt"

# W7: Issue-Titel + Idempotenz-Marker (reopen-Loop-Schutz)
grep_q "$WF" "sentry-stats-warn" && ok 7 "WARN-Marker vorhanden" \
    || bad 7 "WARN-Marker fehlt"
grep_q "$WF" "sentry-stats-config" && ok 7 "Config-Marker vorhanden" \
    || bad 7 "Config-Marker fehlt"
grep_q "$WF" "Quota-Drops erkannt" && ok 7 "WARN-Issue-Titel vorhanden" \
    || bad 7 "WARN-Issue-Titel fehlt"
grep_q "$WF" "Lese-Token fehlt/ungültig" && ok 7 "Config-Issue-Titel vorhanden" \
    || bad 7 "Config-Issue-Titel fehlt"
grep_q "$WF" "issues.createComment" \
    && ok 7 "bestehende Issues werden kommentiert (Idempotenz)" \
    || bad 7 "Kein Kommentar-Pfad (reopen-Loop-Gefahr)"

# W8: Auto-Close-Pfad
grep_q "$WF" "state: 'closed'" \
    && ok 8 "Erfolgs-/Neutralfall schließt Issues" \
    || bad 8 "Kein Auto-Close-Pfad"

# W9: Gate-Konsistenz (Guard-Selbsttest im pre-push.sh)
grep_q "$GATE" "bash scripts/test_sentry_stats.sh" \
    && ok 9 "Guard-Selbsttest im Pre-Push-Gate" \
    || bad 9 "Guard-Selbsttest nicht im Gate"

echo
if [ "$FAIL" -eq 0 ]; then
    echo "✅ Alle $PASS Checks bestanden."
    exit 0
fi
echo "❌ $FAIL von $((PASS + FAIL)) Checks fehlgeschlagen."
exit 1
