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
#   T12 python3 = Windows-Store-Alias (Exit 49)    → grün (Fallback auf python)
#   T13 nur python3 vorhanden (CI ohne `python`)   → grün
#   T14 keines der Pythons läuft                   → rot (fail-loud, Precondition)
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

# --- T12–T14: Python-Interpreter-Resolution (Windows-Store-Alias-Klasse) -----
# Der Guard wählt den Interpreter per Execution-Probe (Hausmuster:
# gh_mock_release.sh) — `command -v` allein trügt, weil die Windows-Store-
# Alias-Stubs (python3.exe) existieren und erst bei der Ausführung mit
# Exit 49 scheitern. Diese Verhaltenstests legen beide Fehlschläge fest:
#   T12 python3 = Store-Stub, python echt       → muss grün laufen
#   T13 nur python3 echt, python kaputt (CI)    → muss grün laufen
#   T14 beide kaputt                            → hart rot, fail-loud
REALPY=""
for cand in python3 python; do
  if command -v "$cand" >/dev/null 2>&1 && "$cand" -c 'import sys' >/dev/null 2>&1; then
    # ABSOLUTER Pfad, nicht der Kandidatenname: der Stub würde sonst per
    # PATH-Lookup sich selbst auflösen und unendlich rekursieren (Vorfall
    # beim ersten Entwurf dieses Tests — Hänger bis zum Timeout).
    REALPY="$(command -v "$cand")"
    break
  fi
done
[ -n "$REALPY" ] || fail "T12: kein lauffaehiges Python im Testsystem — Selbsttest nicht ausführbar"
case "$REALPY" in
  /*) ;;
  *) fail "T12: command -v liefert keinen absoluten Pfad ('$REALPY') — Stub-Wrapper wuerde rekursieren" ;;
esac
STUB="$TMP/pystub"
mkdir -p "$STUB"
write_stub() { # $1 = Name, $2 = exec-Ziel oder '' für Exit-49-Stub (Store-Alias)
  if [ -n "$2" ]; then
    printf '#!/bin/sh\nexec %s "$@"\n' "$2" >"$STUB/$1"
  else
    printf '#!/bin/sh\necho "Python wurde nicht gefunden ..." >&2\nexit 49\n' >"$STUB/$1"
  fi
  chmod +x "$STUB/$1"
}

# T12: `command -v python3` findet den Stub, die Ausführung scheitert (49).
# Variante „nur command -v“ (ohne Execution-Probe) würde hier rot.
write_stub python3 ''
write_stub python "$REALPY"
expect_ok SNYK_GUARD_NO_NET=1 PATH="$STUB:$PATH"
pass "T12 Store-Alias-python3 wird per Execution-Probe umgangen (Fallback python)"

# T13: CI-Box ohne `python` — nur python3 trägt den Guard.
# Variante „nur python“ (ohne python3-Fallback) würde hier rot.
write_stub python3 "$REALPY"
write_stub python ''
expect_ok SNYK_GUARD_NO_NET=1 PATH="$STUB:$PATH"
pass "T13 python3-only-Umgebung trägt den Guard"

# T14: keiner der Kandidaten läuft → harter Abbruch mit Precondition-Meldung
# (fail-loud: der Guard ist Voraussetzung des Pre-Push-Gates, kein Skip).
write_stub python3 ''
write_stub python ''
out="$(env SNYK_GUARD_NO_NET=1 PATH="$STUB:$PATH" bash "$GUARD" 2>&1 || true)"
grep -q "kein lauffaehiges Python" <<<"$out" \
  || fail "T14: Guard bricht nicht fail-loud ab (Ausgabe: $out)"
pass "T14 kein lauffaehiges Python → harter Abbruch mit Precondition-Meldung"

echo "✅ [test-snyk-policy] Alle 14 Selbsttests bestanden."
