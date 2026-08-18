#!/usr/bin/env bash
# Tests für scripts/check_sentry_optout_mapping.sh — deterministisch, offline:
# ein Stub-Mapping im Sandbox-Ordner simuliert die echten R8-Inline-Records.
# Aufruf: bash scripts/test_sentry_optout_mapping.sh   (Exit-Code 0 = alle grün)
#
# Getestet wird:
#   T1  Stub mit echter Inline-Struktur    -> Exit 0 (Opt-out-Logik nachgewiesen)
#   T2  Mapping fehlt                      -> Exit 1 (C1)
#   T3  Fabrik-Record fehlt                -> Exit 1 (C3)
#   T4  Lambda-Record fehlt                -> Exit 1 (C4)
#   T5  Lambda nicht in SentryClient       -> Exit 1 (C5)
#   T6  Fassade nicht REMOVED              -> Exit 1 (C6)
#   T7  Mapping veraltet (älter als Quelle)-> Exit 1 (C2)
#   T8  Zwei Mappings (release+playRelease, beide ok) -> Exit 0
#   T9  Zwei Mappings, eines fehlt         -> Exit 1
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
CHECK="$SCRIPT_DIR/check_sentry_optout_mapping.sh"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

PASS=0

# ── Stub-Mapping: bildet die echte R8-Struktur nach ──────────────────────────
stub_mapping() {
  cat <<'EOF'
# compiler: R8
com.vivid.irlbroadcaster.SentryOptOutKt -> R8$$REMOVED$$CLASS$$123:
io.sentry.SentryClient -> io.sentry.u4:
    23:29:io.sentry.SentryOptions$BeforeSendCallback com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback(kotlin.jvm.functions.Function0):31:31 -> e
    270:283:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> j
    392:406:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> l
EOF
}

run_check() {
  local mapping="$1"
  set +e
  bash "$CHECK" "$mapping" >/dev/null 2>&1
  local rc=$?
  set -e
  return $rc
}

expect_ok() {  # $1 = Testname, $2 = Mapping
  if run_check "$2"; then
    echo "✅ $1"
    PASS=$((PASS + 1))
  else
    echo "❌ FAIL: $1 (Check lief mit Exit != 0, obwohl er bestehen sollte)"
    exit 1
  fi
}

expect_fail() {  # $1 = Testname, $2 = Mapping
  if run_check "$2"; then
    echo "❌ FAIL: $1 (Check lief mit Exit 0, obwohl er scheitern sollte)"
    exit 1
  else
    echo "✅ $1"
    PASS=$((PASS + 1))
  fi
}

# T1: vollständiger Stub mit echter Struktur -> grün
M_OK="$SANDBOX/mapping-ok.txt"
stub_mapping > "$M_OK"
expect_ok "T1  Stub mit echter Inline-Struktur" "$M_OK"

# T2: Mapping fehlt -> C1
M_MISSING="$SANDBOX/gibt-es-nicht.txt"
expect_fail "T2  Mapping fehlt (C1)" "$M_MISSING"

# T3: Fabrik-Record fehlt -> C3
M_NO_FACTORY="$SANDBOX/mapping-no-factory.txt"
stub_mapping | grep -v "sentryBeforeSendCallback(kotlin.jvm.functions.Function0)" > "$M_NO_FACTORY"
expect_fail "T3  Fabrik-Record fehlt (C3)" "$M_NO_FACTORY"

# T4: Lambda-Record fehlt -> C4
M_NO_LAMBDA="$SANDBOX/mapping-no-lambda.txt"
stub_mapping | grep -v 'sentryBeforeSendCallback\$lambda\$0' > "$M_NO_LAMBDA"
expect_fail "T4  Lambda-Record fehlt (C4)" "$M_NO_LAMBDA"

# T5: Lambda existiert, aber außerhalb von SentryClient -> C5
M_OUTSIDE="$SANDBOX/mapping-outside.txt"
stub_mapping | sed 's/^io.sentry.SentryClient -> /io.sentry.OtherClass -> /' > "$M_OUTSIDE"
expect_fail "T5  Lambda nicht in SentryClient (C5)" "$M_OUTSIDE"

# T6: Fassade nicht als REMOVED markiert -> C6
M_NO_REMOVED="$SANDBOX/mapping-no-removed.txt"
stub_mapping | grep -v 'SentryOptOutKt -> R8\$\$REMOVED' > "$M_NO_REMOVED"
expect_fail "T6  Fassade nicht REMOVED (C6)" "$M_NO_REMOVED"

# T7: Mapping älter als die Opt-out-Quellen -> C2 (mtime in die Vergangenheit)
M_STALE="$SANDBOX/mapping-stale.txt"
stub_mapping > "$M_STALE"
touch -d '2020-01-01 00:00:00' "$M_STALE"
expect_fail "T7  Mapping veraltet (C2)" "$M_STALE"

# T8: Zwei Mappings (release + playRelease), beide ok -> Exit 0 (Multi-Mapping-Modus)
M_REL="$SANDBOX/mapping-release.txt"
M_PLAY="$SANDBOX/mapping-playRelease.txt"
stub_mapping > "$M_REL"
stub_mapping > "$M_PLAY"
set +e
bash "$CHECK" "$M_REL" "$M_PLAY" >/dev/null 2>&1
RC_DUAL_OK=$?
set -e
if [[ "$RC_DUAL_OK" == "0" ]]; then
  echo "✅ T8  Zwei Mappings (release+playRelease) beide ok"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T8 (Check lief mit Exit $RC_DUAL_OK, obwohl beide Mappings ok sind)"
  exit 1
fi

# T9: Zwei Mappings, eines fehlt -> Exit 1 (Multi-Mapping-Modus)
set +e
bash "$CHECK" "$M_REL" "$SANDBOX/playRelease-fehlt.txt" >/dev/null 2>&1
RC_DUAL_MISSING=$?
set -e
if [[ "$RC_DUAL_MISSING" != "0" ]]; then
  echo "✅ T9  Zwei Mappings, eines fehlt (Exit $RC_DUAL_MISSING)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T9 (Check lief mit Exit 0, obwohl ein Mapping fehlt)"
  exit 1
fi

echo "✅ Sentry-Opt-out-Mapping-Check: $PASS/$PASS Tests grün."
