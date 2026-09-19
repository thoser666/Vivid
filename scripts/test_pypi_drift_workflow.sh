#!/usr/bin/env bash
# Selbsttest: PyPI-Drift-Workflow (.github/workflows/automation-pypi-drift.yml,
# offline).
#
# Prüft die Verträge, die den Workflow sicher und idempotent machen:
#   W1: YAML ist gültig
#   W2: Wöchentlicher Cron (Dienstag 06:30 UTC) + workflow_dispatch-Trigger
#   W3: permissions: {} top-level; Job nur contents/pull-requests write
#   W4: Alle Actions SHA-gepinnt (checkout, setup-python)
#   W5: Drift-Detektor per --check (kein eigenes Paritäts-Grep)
#   W6: Regenerierung nutzt den Generator (identische Pipeline wie die
#       manuelle Regenerierung) und validiert per pip-pinning-Selbsttest
#   W7: Loop-Sicherheit: Workflow läuft NICHT auf push/pull_request (kein
#       Regenerierungs-Loop) und Bot-PRs erben die Standard-Pflicht-Checks
#   W8: Credential-Verdrahtung: AUTOMATION_TOKEN-Checkout-Fallback + Push-Step
#       mit Fallback und ::warning:: bei fehlendem Token (Konsistenz mit dem
#       Bot-PR-Credential-Selbsttest T1/T2/T4)
#   W9: REST-PR-Create mit Inline-Orphan-Rollback (Konsistenz mit T7/T8/T9)
#   W10: Rebase-Härtung wie die Changelog-Mirror (Konsistenz mit T10)
#   W11: Generator + pip-pinning-Guard existieren und sind Gate-verdrahtet
#   W12: Vollautomatischer Merge — Checks-Polling auf die beiden
#       Pflicht-Checks, Squash-Merge per REST, fail-soft (roter Check
#       oder Timeout laesst den PR offen; kein Rollback nach Create)
#
# Exit 0 = alle Checks bestanden, Exit 1 = mindestens ein Check fehlgeschlagen.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF="$ROOT/.github/workflows/automation-pypi-drift.yml"
GEN="$ROOT/scripts/gen_fdroid_requirements.py"
GUARD="$ROOT/scripts/test_pip_pinning.sh"
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
    grep_q "$WF" "jobs:" && grep_q "$WF" "runs-on: ubuntu-24.04" \
        && ok 1 "YAML-Struktur (Grobcheck, kein PyYAML)" \
        || bad 1 "YAML-Struktur (Grobcheck)"
fi

# W2: Cron + workflow_dispatch
grep_q "$WF" 'cron: "30 6 \* \* 2"' && ok 2 "wöchentlicher Cron (Di 06:30 UTC)" \
    || bad 2 "Cron fehlt/abweichend"
grep_q "$WF" "workflow_dispatch:" && ok 2 "workflow_dispatch vorhanden" \
    || bad 2 "workflow_dispatch fehlt"

# W3: Minimalprivilegien
grep_q "$WF" "permissions: {}" && ok 3 "permissions: {} top-level" \
    || bad 3 "top-level permissions fehlt"
grep_q "$WF" "contents: write" && grep_q "$WF" "pull-requests: write" \
    && ok 3 "Job-Permission contents+pull-requests write (Push/PR via USER-Credential)" \
    || bad 3 "Job-Permission fehlt"

# W4: Actions SHA-gepinnt (Haus-Pins)
check_pin() {
    local action="$1"
    if grep -E "uses: ${action}@[0-9a-f]{40}" "$WF" >/dev/null 2>&1; then
        ok 4 "Action SHA-gepinnt: $action"
    else
        bad 4 "Action nicht SHA-gepinnt: $action"
    fi
}
check_pin "actions/checkout"
check_pin "actions/setup-python"

# W5: Drift-Detektor per --check
grep_q "$WF" 'gen_fdroid_requirements.py --check' \
    && ok 5 "Drift-Detektor nutzt --check (byte-identischer Vergleich)" \
    || bad 5 "Drift-Detektor fehlt/abweichend"

# W6: Regenerierung + Post-Verifikation
grep_q "$WF" "python scripts/gen_fdroid_requirements.py" \
    && ok 6 "Regenerierung über den Generator (identische Pipeline wie manuell)" \
    || bad 6 "Generator-Aufruf fehlt"
grep_q "$WF" "scripts/test_pip_pinning.sh" \
    && ok 6 "Post-Verifikation per pip-pinning-Selbsttest" \
    || bad 6 "pip-pinning-Verifikation fehlt"

# W7: Loop-Sicherheit — Trigger-Namen exklusiv (schedule/dispatch), kein
# push/pull_request; der Workflow kann den eigenen PR-Merge nicht triggern.
if grep -qE "^on:|^\s{2}push:|^\s{2}pull_request" "$WF" 2>/dev/null; then
    if grep -qE "^\s{2}(push|pull_request):" "$WF"; then
        bad 7 "Workflow lauscht auf push/pull_request (Loop-Risiko)"
    else
        ok 7 "Trigger exklusiv schedule/dispatch (kein Loop-Risiko)"
    fi
else
    bad 7 "on:-Block nicht gefunden"
fi

# W8: Credential-Verdrahtung (Konsistenz mit T1/T2/T4)
grep_q "$WF" 'token: ${{ secrets.AUTOMATION_TOKEN || secrets.GITHUB_TOKEN }}' \
    && ok 8 "Checkout mit AUTOMATION_TOKEN-Fallback (T1-Konvention)" \
    || bad 8 "Checkout-Token-Fallback fehlt"
grep_q "$WF" 'GH_TOKEN: ${{ secrets.AUTOMATION_TOKEN || secrets.GITHUB_TOKEN }}' \
    && ok 8 "Push-/PR-Step mit Credential-Fallback (T2-Konvention)" \
    || bad 8 "Step-Credential-Fallback fehlt"
grep_q "$WF" '::warning::AUTOMATION_TOKEN ist nicht gesetzt' \
    && ok 8 "::warning:: bei fehlendem AUTOMATION_TOKEN (T4-Konvention)" \
    || bad 8 "::warning:: fehlt"

# W9: REST-PR-Create + Inline-Rollback (Konsistenz mit T7/T8/T9)
if grep -qE '^[[:space:]]*gh pr create' "$WF"; then
    bad 9 "gh pr create vorhanden (GraphQL scheitert an PATs)"
else
    ok 9 "kein gh pr create (T7-Konvention)"
fi
grep_q "$WF" 'gh api repos/${{ github.repository }}/pulls -f title=' \
    && ok 9 "REST-PR-Create (T8-Konvention)" \
    || bad 9 "REST-PR-Create fehlt"
grep_q "$WF" 'PR-Create fehlgeschlagen — Rollback' \
    && grep_q "$WF" 'git/refs/heads/\$BRANCH' \
    && ok 9 "Inline-Orphan-Rollback (T9-Konvention)" \
    || bad 9 "Orphan-Rollback fehlt/abweichend"

# W10: Rebase-Härtung (Konsistenz mit T10)
grep_q "$WF" 'merge-base --is-ancestor "HEAD~1" origin/develop' \
    && ok 10 "Rebase-Ancestor-Check (T10-Konvention)" \
    || bad 10 "Ancestor-Check fehlt"
grep_q "$WF" 'git rebase origin/develop' \
    && grep_q "$WF" 'git rebase --abort' \
    && ok 10 "Rebase mit Konflikt-Fallback (Neugenerierung)" \
    || bad 10 "Rebase-Fallback fehlt"

# W11: Generator + Guard existieren und sind Gate-verdrahtet
[ -f "$GEN" ] && ok 11 "Generator vorhanden (scripts/gen_fdroid_requirements.py)" \
    || bad 11 "Generator fehlt"
[ -f "$GUARD" ] && ok 11 "pip-pinning-Selbsttest vorhanden (bash-Invocation, kein Exec-Bit nötig)" \
    || bad 11 "pip-pinning-Selbsttest fehlt"
grep_q "$ROOT/scripts/pre-push.sh" "scripts/test_pip_pinning.sh" \
    && ok 11 "pip-pinning-Guard ist Gate-verdrahtet" \
    || bad 11 "Gate-Verdrahtung fehlt"

# W12: Vollautomatischer Merge (Checks pollen -> Squash-Merge, fail-soft)
grep_q "$WF" "checks: read"     && ok 12 "Job-Permission checks: read (GITHUB_TOKEN-Fallback kann Checks lesen)"     || bad 12 "checks: read fehlt (Fallback-Pfad waere 403)"
grep_q "$WF" 'gh api -X PUT "repos/${{ github.repository }}/pulls/$PR/merge"'     && grep_q "$WF" "merge_method=squash"     && ok 12 "Squash-Merge per REST (kein gh pr merge --auto, Review-Pflicht blockiert)"     || bad 12 "REST-Merge fehlt/abweichend"
grep_q "$WF" "conclusion"     && grep_q "$WF" '"Build & Test"'     && grep_q "$WF" '"Secret Guard"'     && ok 12 "Checks-Polling auf die beiden Pflicht-Checks (exact-match)"     || bad 12 "Pflicht-Check-Polling fehlt/abweichend"
grep_q "$WF" "Checks fehlgeschlagen"     && grep_q "$WF" "Timeout nach"     && grep_q "$WF" "PR bleibt offen"     && ok 12 "Fail-soft: roter Check/Timeout laesst den PR offen (::error::, kein Rollback)"     || bad 12 "Fail-soft-Pfade fehlen"
grep_q "$WF" "steps.pr.outputs.pr"     && grep_q "$WF" "steps.merge.outputs.merged"     && ok 12 "PR-Number verdrahtet (Merge-Gate + Summary-Ausgabe)"     || bad 12 "PR-Number-Verdrahtung fehlt"
grep_q "$WF" "delete_branch_on_merge"     && ok 12 "Branch-Delete nach Merge an Repo-Setting delegiert (kein Doppel-Delete)"     || bad 12 "delete_branch_on_merge-Dokumentation fehlt"
# Kein natives Auto-Merge (wuerde an der Review-Pflicht ewig pending bleiben):
grep_q "$WF" "pr merge --auto"     && bad 12 "gh pr merge --auto vorhanden (haengt ewig an required_reviews=1)"     || ok 12 "kein natives Auto-Merge (Admin-Override per REST-PUT ist der Pfad)"

echo ""
echo "=== PyPI-Drift-Workflow-Selbsttest: $PASS PASS, $FAIL FAIL ==="
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks grün."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1
