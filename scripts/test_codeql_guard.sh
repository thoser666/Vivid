#!/usr/bin/env bash
# Selbsttest: CodeQL-Kotlin-Wächter (scripts/check_codeql_kotlin_support.sh,
# Offline-Modus mit Fixtures).
#
#   T1: Issue offen            → blockiert, exit 0
#   T2: Issue closed/completed → Re-Upgrade fällig, exit 10 + ::warning:: + Summary
#   T3: Issue closed/not_planned → kein belastbares Signal, exit 0
#   T4: Kotlin bereits 2.4.20  → Wächter überflüssig, exit 0 (ohne API-Zugriff)
#   T5: API nicht erreichbar   → Warnung, neutral, exit 0
#   T6: Kaputtes JSON          → neutral, exit 0
#
# Nutzung: bash scripts/test_codeql_guard.sh
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_codeql_kotlin_support.sh"
fail() { echo "❌ [test-codeql-guard] $1"; exit 1; }
pass() { echo "✅ [test-codeql-guard] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

SUMMARY="$TMP/summary.md"
TOML_240="$TMP/versions-2.4.10.toml"
TOML_2420="$TMP/versions-2.4.20.toml"
cat >"$TOML_240" <<'EOF'
[versions]
kotlin = "2.4.10"
ksp = "2.3.11"
EOF
cat >"$TOML_2420" <<'EOF'
[versions]
kotlin = "2.4.20"
ksp = "2.3.11"
EOF

JSON_OPEN='{"number": 22404, "state": "open", "title": "Kotlin: support 2.4.20"}'
JSON_COMPLETED='{"number": 22404, "state": "closed", "state_reason": "completed"}'
JSON_NOT_PLANNED='{"number": 22404, "state": "closed", "state_reason": "not_planned"}'
JSON_BROKEN='{"number": 22404, "state"'

run_guard() { # <summary-file> <toml> [json]
  rm -f "$1"
  local -a env_args=(GITHUB_STEP_SUMMARY="$1" KOTLIN_VERSION_FILE="$2")
  if [[ $# -ge 3 ]]; then env_args+=("CODEQL_ISSUE_JSON=$3"); fi
  local rc=0
  env "${env_args[@]}" bash "$GUARD" >"$TMP/out.txt" 2>&1 || rc=$?
  return "$rc"
}

# ── T1: Issue offen → blockiert, exit 0 ─────────────────────────────────────
if run_guard "$SUMMARY" "$TOML_240" "$JSON_OPEN"; then :; else
  fail "T1: offener Issue muss exit 0 liefern (bekam $?)."
fi
grep -q 'noch nicht' "$TMP/out.txt" || fail "T1: 'noch nicht'-Meldung fehlt."
grep -q 'blockiert' "$SUMMARY" || fail "T1: Step-Summary fehlt/blockiert."
pass "T1: offener Issue → blockiert, exit 0 + Step-Summary."

# ── T2: closed/completed → exit 10, Warnung + Summary ──────────────────────
rc=0
run_guard "$SUMMARY" "$TOML_240" "$JSON_COMPLETED" || rc=$?
[[ $rc -eq 10 ]] || fail "T2: closed/completed muss exit 10 liefern (bekam $rc)."
grep -q '::warning::' "$TMP/out.txt" || fail "T2: ::warning:: fehlt."
grep -q 'RE-UPGRADE FÄLLIG' "$TMP/out.txt" || fail "T2: Re-Upgrade-Meldung fehlt."
grep -q '2.4.20' "$SUMMARY" || fail "T2: Step-Summary mit 2.4.20 fehlt."
grep -q 'a8766e5' "$SUMMARY" || fail "T2: Revert-Referenz a8766e5 fehlt in der Summary."
pass "T2: closed/completed → exit 10 + ::warning:: + Step-Summary."

# ── T3: closed/not_planned → kein Signal, exit 0 ───────────────────────────
if run_guard "$SUMMARY" "$TOML_240" "$JSON_NOT_PLANNED"; then :; else
  fail "T3: closed/not_planned muss exit 0 liefern (bekam $?)."
fi
grep -q "nicht als 'completed'" "$TMP/out.txt" || fail "T3: not_planned-Hinweis fehlt."
pass "T3: closed/not_planned → neutral, exit 0."

# ── T4: Kotlin bereits 2.4.20 → überflüssig, exit 0, ohne API ──────────────
if run_guard "$SUMMARY" "$TOML_2420"; then :; else
  fail "T4: 2.4.20-Stand muss exit 0 liefern (bekam $?)."
fi
grep -q 'überflüssig' "$TMP/out.txt" || fail "T4: Überflüssig-Meldung fehlt."
pass "T4: Kotlin 2.4.20 → Wächter überflüssig, exit 0 (kein API-Zugriff)."

# ── T5: API nicht erreichbar → Warnung, exit 0 ─────────────────────────────
rc=0
env GITHUB_STEP_SUMMARY="$SUMMARY" KOTLIN_VERSION_FILE="$TOML_240" \
  CODEQL_ISSUE_URL="http://127.0.0.1:9/not-a-real-api" \
  bash "$GUARD" >"$TMP/out.txt" 2>&1 || rc=$?
[[ $rc -eq 0 ]] || fail "T5: API-Ausfall muss neutral (exit 0) bleiben (bekam $rc)."
grep -q '::warning::' "$TMP/out.txt" || fail "T5: Warnung bei API-Ausfall fehlt."
grep -q 'nicht abfragen' "$TMP/out.txt" || grep -q 'nicht abfragbar' "$SUMMARY" \
  || fail "T5: 'nicht abfragbar'-Meldung fehlt."
pass "T5: API-Ausfall → Warnung, neutral, exit 0."

# ── T6: Kaputtes JSON → neutral, exit 0 ────────────────────────────────────
if run_guard "$SUMMARY" "$TOML_240" "$JSON_BROKEN"; then :; else
  fail "T6: kaputtes JSON muss neutral (exit 0) bleiben (bekam $?)."
fi
pass "T6: kaputtes JSON → neutral, exit 0."

echo ""
echo "✅ [test-codeql-guard] Alle 6 Selbsttests bestanden."
