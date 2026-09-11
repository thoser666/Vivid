#!/usr/bin/env bash
# Selbsttest: Snyk-Policy-Guard (scripts/check_snyk_policy.sh, offline).
#
# Szenarien:
#   T1: gültige Policy + verdrahteter Workflow    → grün
#   T2: reason fehlt                              → rot
#   T3: reason zu kurz (< 40 Zeichen)             → rot
#   T4: expires abgelaufen                        → rot
#   T5: expires fehlt                             → rot
#   T6: Workflow ohne --policy-path               → rot (Ignore wirkungslos)
#   T7: --policy-path nur 1× (nur test ODER monitor) → rot
#   T8: 'patch:'-Direktive                        → rot
#   T9: FASTLANE_LOCKED erlaubt 3.x               → ::warning:: (Ignore entfernen)
#   T10: FASTLANE_LOCKED hält < 3.0               → still
#   T11: Netzwerk tot (leere Antwort)             → still (advisory, exit 0)
set -euo pipefail
cd "$(dirname "$0")/.."

GUARD=scripts/check_snyk_policy.sh
WF=.github/workflows/security-snyk.yml
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }
expect_ok() { if ! env "$@" bash "$GUARD" >/dev/null 2>&1; then fail "$1: erwartete grün"; fi; }
expect_fail() { if env "$@" bash "$GUARD" >/dev/null 2>&1; then fail "$1: erwartete rot"; fi; }

policy_fixture() { # $1 = Zielpfad, $2 = reason-Text, $3 = expires (oder 'NONE')
  local dst="$1" reason="$2" expires="$3"
  {
    echo "version: v1.25.1"
    echo "ignore:"
    echo "  SNYK-RUBY-RUBYZIP-19666145:"
    if [ "$reason" != "NONE" ]; then
      echo "    - reason: $reason"
    fi
    if [ "$expires" != "NONE" ]; then
      echo "      expires: '$expires'"
    fi
  } >"$dst"
}

wf_fixture() { # $1 = Zielpfad, $2 = Anzahl --policy-path-Referenzen
  local dst="$1" n="$2"
  : >"$dst"
  [ "$n" -ge 1 ] && printf 'snyk test --policy-path="$GITHUB_WORKSPACE/.snyk"\n' >>"$dst"
  [ "$n" -ge 2 ] && printf 'snyk monitor --policy-path="$GITHUB_WORKSPACE/.snyk"\n' >>"$dst"
  return 0
}

# T1: gültiger Stand (echte Policy + echter Workflow, Netz übersprungen)
expect_ok SNYK_GUARD_NO_NET=1
pass "T1 gültige Policy + Verdrahtung → grün"

# T2: reason fehlt
policy_fixture "$TMP/t2" NONE '2026-12-10T00:00:00.000Z'
expect_fail SNYK_GUARD_NO_NET=1 SNYK_POLICY_FILE="$TMP/t2"
pass "T2 reason fehlt → rot"

# T3: reason zu kurz
policy_fixture "$TMP/t3" 'nur ein kurzer Grund' '2026-12-10T00:00:00.000Z'
expect_fail SNYK_GUARD_NO_NET=1 SNYK_POLICY_FILE="$TMP/t3"
pass "T3 reason < 40 Zeichen → rot"

# T4: expires abgelaufen
policy_fixture "$TMP/t4" "$(printf '%.0sx' {1..60})" '2025-01-01T00:00:00.000Z'
expect_fail SNYK_GUARD_NO_NET=1 SNYK_POLICY_FILE="$TMP/t4"
pass "T4 expires abgelaufen → rot"

# T5: expires fehlt
policy_fixture "$TMP/t5" "$(printf '%.0sx' {1..60})" NONE
expect_fail SNYK_GUARD_NO_NET=1 SNYK_POLICY_FILE="$TMP/t5"
pass "T5 expires fehlt → rot"

# T6: Workflow ohne --policy-path
wf_fixture "$TMP/wf6" 0
expect_fail SNYK_GUARD_NO_NET=1 SNYK_WORKFLOW_FILE="$TMP/wf6"
pass "T6 Workflow ohne --policy-path → rot"

# T7: --policy-path nur 1×
wf_fixture "$TMP/wf7" 1
expect_fail SNYK_GUARD_NO_NET=1 SNYK_WORKFLOW_FILE="$TMP/wf7"
pass "T7 --policy-path nur 1× → rot"

# T8: patch:-Direktive verboten
{
  echo "version: v1.25.1"
  echo "patch:"
  echo "  SNYK-RUBY-RUBYZIP-19666145: >"
  echo "    - patched"
  echo "ignore: {}"
} >"$TMP/t8"
expect_fail SNYK_GUARD_NO_NET=1 SNYK_POLICY_FILE="$TMP/t8"
pass "T8 patch:-Direktive → rot"

# T9: fastlane lockert den Constraint → Warning mit Handlungsanweisung
out="$(FASTLANE_LOCKED='{"dependencies":{"runtime":[{"name":"rubyzip","requirements":">= 3.0.0"}]}}' \
  bash "$GUARD" 2>&1)"
grep -q "::warning::" <<<"$out" || fail "T9: kein ::warning:: bei 3.x-Freigabe"
grep -q "entfernen" <<<"$out" || fail "T9: Warning nennt die Handlungsanweisung nicht"
t9="$out"
pass "T9 fastlane erlaubt 3.x → Warning"

# T10: Constraint unverändert (< 3.0) → still
out="$(FASTLANE_LOCKED='{"dependencies":{"runtime":[{"name":"rubyzip","requirements":">= 2.0.0, < 3.0.0"}]}}' \
  bash "$GUARD" 2>&1)"
grep -q "::warning::" <<<"$out" && fail "T10: falsches Warning bei unverändertem Constraint"
pass "T10 Constraint < 3.0 → still"

# T11: Netz tot → still (advisory darf niemals das Gate blockieren)
out="$(SNYK_GUARD_NO_NET=1 bash "$GUARD" 2>&1)"
grep -q "::warning::" <<<"$out" && fail "T11: Warning trotz Netz-Ausfall"
pass "T11 Netzwerk tot → still (advisory)"

# Konsistenz: der echte Guard-Output bei T9 erwähnt die Advisory-ID
grep -q "SNYK-RUBY-RUBYZIP-19666145" <<<"$t9" || fail "T9: Warning nennt die Advisory-ID nicht"

echo "✅ [test-snyk-policy] Alle 11 Selbsttests bestanden."
