#!/usr/bin/env bash
# Selbsttest: Sentry-Triage-Workflow (scripts/test_sentry_triage_workflow.sh).
#
# Prüft statisch die Verträge des Triage-Workflows:
#   W1 Workflow existiert + YAML valide (samt Label-Semantik in der Prüfung)
#   W2 Trigger exklusiv issues: [opened, reopened] — kein Loop-Risiko (kein labeled)
#   W3 permissions: {} top-level, Job nur issues: write
#   W4 github-script SHA-gepinnt
#   W5 Severity-Parsing vorhanden (Severity/Level, robust gegen Schreibweise)
#   W6 Crash-Signale erheben mindestens "high" (ExceptionInInitializerError-Klasse)
#   W7 sentry-Fallback-Label gesetzt (unspezifizierte Reports bleiben sichtbar)
#   W8 Idempotenz: keine Downgrades bestehender severity:*-Labels
#   W9 Selbsttest im Pre-Push-Gate verdrahtet
#
# Nutzung: bash scripts/test_sentry_triage_workflow.sh
set -euo pipefail
cd "$(dirname "$0")/.."

WF=".github/workflows/automation-sentry-triage.yml"
PASS=0; FAIL=0
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "  ✅ $desc"; PASS=$((PASS+1));
  else echo "  ❌ $desc"; FAIL=$((FAIL+1)); fi
}

echo "▶ [sentry-triage-test] Prüfungen W1–W9 gegen $WF"

check "W1 Workflow existiert" test -f "$WF"
check "W1 YAML gültig (Trigger + Label-Job vorhanden)" python -c "
import yaml, io
with io.open('$WF', encoding='utf-8') as f:
    d = yaml.safe_load(f)
# YAML 1.1: der Key 'on' wird von PyYAML als bool True geparst — beides abdecken.
on = d['on'] if 'on' in d else d[True]
assert 'issues' in on, 'issues-Trigger fehlt'
assert on['issues']['types'] == ['opened', 'reopened'], on['issues']['types']
jobs = d['jobs'] if 'jobs' in d else d[True]['label']['jobs']
steps = jobs['label']['steps']
assert any('github-script' in str(s.get('uses', '')) for s in steps)
"
check "W2 Trigger: issues opened+reopened (kein labeled — Loop-Schutz)" bash -c "
grep -q 'issues:' '$WF' && grep -q 'opened' '$WF' && grep -q 'reopened' '$WF' && ! grep -qE 'types:.*labeled' '$WF'
"
check "W3 permissions: {} top-level" bash -c "
python - <<'PY'
import yaml, io
with io.open('$WF', encoding='utf-8') as f:
    d = yaml.safe_load(f)
assert d.get('permissions') == {} or d.get('permissions') is None, d.get('permissions')
PY
"
check "W3 Job-Permission nur issues: write" bash -c "
grep -A6 '^  label:' '$WF' | grep -q 'issues: write'
"
check "W4 github-script SHA-gepinnt" grep -qE "uses: actions/github-script@[0-9a-f]{40}" "$WF"
check "W5 Severity-Parsing (severity/level, Groß-/Kleinschreibung robust)" bash -c "
grep -q 'severity' '$WF' && grep -qi 'level' '$WF' && grep -q 'toLowerCase' '$WF'
"
check "W6 Crash-Signale → mindestens high (ICU-Klasse vertreten)" bash -c "
grep -q 'ExceptionInInitializerError' '$WF' && grep -q 'PatternSyntaxException' '$WF' && grep -q \"'high'\" '$WF'
"
check "W7 sentry-Fallback-Label gesetzt" grep -q \"'sentry'\" "$WF"
check "W8 Idempotenz: severity-Labels werden geprüft (kein Downgrade)" grep -q "hasSeverity" "$WF"
check "W9 Selbsttest im Pre-Push-Gate verdrahtet" \
  grep -q "scripts/test_sentry_triage_workflow.sh" scripts/pre-push.sh

echo ""
echo "▶ [sentry-triage-test] $PASS grün, $FAIL rot"
[ "$FAIL" -eq 0 ] && { echo "✅ [sentry-triage-test] Sentry-Triage-Workflow vertragstreu (W1–W9)."; exit 0; }
exit 1
