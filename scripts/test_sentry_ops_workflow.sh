#!/usr/bin/env bash
# Selbsttest: Sentry-Ops-Review-Workflow (automation-sentry-ops.yml) — offline,
# kein Netz, kein echtes Guard-Post und keine Issue-Aufrufe.
#
#   W1 Skripte existieren, sind ausführbar, bash-Syntax ok
#   W2 Workflow existiert; alter monatlicher Stats-Workflow ist entfernt
#   W3 YAML ist gültig
#   W4 Wöchentlicher Cron (Mo 06:30 UTC) + workflow_dispatch
#   W5 permissions: {} top-level, Job nur issues: write (Minimalprivileg)
#   W6 Beide Actions SHA-gepinnt (checkout, github-script)
#   W7 Guard-Verdrahtung: Stats (Secret SENTRY_STATS_TOKEN) + Health --live,
#      beide mit continue-on-error + tee + PIPESTATUS (echter Exit-Code)
#   W8 Issue-Automation: Dedup-Marker, reopen-Loop-Schutz (Kommentar statt
#      Neu-Eröffnung), Auto-Close-Pfad, Label ops
#   W9 Gate-Verdrahtung: Selbsttest im pre-push.sh, alte Namen entfernt
set -u
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
ok()   { PASS=$((PASS + 1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  ❌ $1"; }
check() { # check <Beschreibung> <Befehl...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$desc"; else fail "$desc"; fi
}

WF=".github/workflows/automation-sentry-ops.yml"
OLD_WF=".github/workflows/automation-sentry-stats.yml"
OLD_TEST="scripts/test_sentry_stats_workflow.sh"

echo "▶ [sentry-ops-workflow-test] Prüfungen W1–W9 gegen $WF"

# W1 — Grundlage
check "W1 check_sentry_stats.sh ausführbar + Syntax ok" \
  bash -c '[ -x scripts/check_sentry_stats.sh ] && bash -n scripts/check_sentry_stats.sh'
check "W1 check_sentry_health.sh ausführbar + Syntax ok" \
  bash -c '[ -x scripts/check_sentry_health.sh ] && bash -n scripts/check_sentry_health.sh'
check "W1 Selbsttest-Skripte ausführbar" \
  bash -c '[ -x scripts/test_sentry_stats.sh ] && [ -x scripts/test_sentry_health.sh ]'

# W2 — Ersetzung
check "W2 Ops-Workflow existiert" [ -f "$WF" ]
if [ -e "$OLD_WF" ]; then fail "W2 alter monatlicher Stats-Workflow entfernt"; else ok "W2 alter monatlicher Stats-Workflow entfernt"; fi
if [ -e "$OLD_TEST" ]; then fail "W2 alter Workflow-Selbsttest entfernt"; else ok "W2 alter Workflow-Selbsttest entfernt"; fi

# W3 — YAML valide
check "W3 YAML gültig" python -c "import yaml,sys; yaml.safe_load(open('$WF', encoding='utf-8'))"

# W4 — Trigger
check "W4 wöchentlicher Cron (30 6 * * 1)" grep -q "cron: '30 6 \* \* 1'" "$WF"
check "W4 workflow_dispatch vorhanden" grep -q "workflow_dispatch" "$WF"
if grep -q "cron: '0 6 9 \* \*'" "$WF"; then fail "W4 kein monatlicher Cron mehr"; else ok "W4 kein monatlicher Cron mehr"; fi

# W5 — Minimalprivilegien
check "W5 permissions: {} top-level" grep -q "^permissions: {}" "$WF"
check "W5 Job nur issues: write" grep -q "issues: write" "$WF"
if grep -E "^\s+(contents|pull-requests|actions|packages): write" "$WF" >/dev/null; then
  fail "W5 keine weiteren Write-Permissions"
else
  ok "W5 keine weiteren Write-Permissions"
fi

# W6 — SHA-Pins
check "W6 checkout SHA-gepinnt" grep -qE "uses: actions/checkout@[0-9a-f]{40}" "$WF"
check "W6 github-script SHA-gepinnt" grep -qE "uses: actions/github-script@[0-9a-f]{40}" "$WF"

# W7 — Guard-Verdrahtung
check "W7 Stats-Guard aufgerufen" grep -q "scripts/check_sentry_stats.sh" "$WF"
check "W7 Lese-Token-Secret verdrahtet" grep -q "SENTRY_STATS_TOKEN: \${{ secrets.SENTRY_STATS_TOKEN }}" "$WF"
check "W7 Health-Probe im Live-Modus" grep -q "scripts/check_sentry_health.sh --live" "$WF"
check "W7 Stats-Step continue-on-error" grep -q "continue-on-error: true" "$WF"
check "W7 Ausgabe via tee + PIPESTATUS (echter Exit-Code)" \
  bash -c 'grep -q "tee guard-output.txt" "'"$WF"'" && grep -q "PIPESTATUS" "'"$WF"'"'

# W8 — Issue-Automation (Dedup + Idempotenz + Auto-Close)
check "W8 Warn-Marker vorhanden" grep -q "sentry-ops-warn" "$WF"
check "W8 Config-Marker vorhanden" grep -q "sentry-ops-config" "$WF"
check "W8 Issue-Dedup (Kommentar statt Neu-Eröffnung — reopen-Loop-Schutz)" \
  bash -c 'grep -q "issues.listForRepo" "'"$WF"'" && grep -q "createComment" "'"$WF"'" && grep -q "issues.create" "'"$WF"'"'
check "W8 Auto-Close-Pfad vorhanden" grep -q "state: 'closed'" "$WF"
check "W8 Label ops gesetzt" grep -q "labels: \['ops'\]" "$WF"
check "W8 Verdict-Parsing (Stats + Health getrennt)" \
  bash -c 'grep -q "statsVerdict" "'"$WF"'" && grep -q "healthVerdict" "'"$WF"'"'
check "W8 Konfig-SKIP-Muster deckt event:read-Scope ab (Issue #203)" \
  bash -c 'grep -q "event:read-Scope" "'"$WF"'"'
check "W8 Konfig-SKIP-Muster deckt abgelaufene Tokens ab (401-Variante)" \
  bash -c 'grep -q "ungültig/abgelaufen" "'"$WF"'"'
check "W8 Konfig-SKIP-Entscheidung über Needle-Liste (erweiterbar)" \
  bash -c 'grep -q "configSkipNeedles" "'"$WF"'"'
check "W8 neutrale Netzwerk-SKIPs lösen KEIN Konfig-Issue aus" \
  bash -c '! grep -q "nicht erreichbar" "'"$WF"'"'

# W9 — Gate-Verdrahtung
check "W9 Selbsttest im Pre-Push-Gate verdrahtet" grep -q "scripts/test_sentry_ops_workflow.sh" scripts/pre-push.sh
check "W9 Stats-Selbsttest weiterhin im Gate" grep -q "scripts/test_sentry_stats.sh" scripts/pre-push.sh
check "W9 Health-Selbsttest weiterhin im Gate" grep -q "scripts/test_sentry_health.sh" scripts/pre-push.sh
if grep -q "test_sentry_stats_workflow" scripts/pre-push.sh; then
  fail "W9 alter Testname aus Gate entfernt"
else
  ok "W9 alter Testname aus Gate entfernt"
fi

# Leck-Prüfung: Workflow darf niemals Token-Werte drucken
if grep -E "echo.*SENTRY_STATS_TOKEN|\\\$SENTRY_STATS_TOKEN\"" "$WF" >/dev/null; then
  fail "W8 Token nie im Output"
else
  ok "W8 Token nie im Output"
fi

echo "▶ [sentry-ops-workflow-test] $PASS grün, $FAIL rot"
[ "$FAIL" -eq 0 ] && { echo "✅ [sentry-ops-workflow-test] Sentry-Ops-Workflow vertragstreu (W1–W9)."; exit 0; }
exit 1
