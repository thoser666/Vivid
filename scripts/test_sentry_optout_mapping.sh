#!/usr/bin/env bash
# Tests für scripts/check_sentry_optout_mapping.sh — deterministisch, offline:
# ein Stub-Mapping im Sandbox-Ordner simuliert die echten R8-Inline-Records.
# Aufruf: bash scripts/test_sentry_optout_mapping.sh   (Exit-Code 0 = alle grün)
#
# Getestet wird:
#   T1  Stub mit echter Inline-Struktur    -> Exit 0 (Opt-out-Logik nachgewiesen)
#   T2  Mapping fehlt, expliziter Pfad     -> Exit 1 (C1, streng)
#   T3  Fabrik-Record fehlt                -> Exit 1 (C3)
#   T4  Lambda-Record fehlt                -> Exit 1 (C4)
#   T5  Lambda hat keinen SentryClient-Range-Bezug  -> Exit 1 (C5)
#   T6  Fassade nicht REMOVED              -> Exit 1 (C6)
#   T7  Mapping veraltet (älter als Quelle)-> Exit 1 (C2)
#   T8  Zwei Mappings (release+playRelease, beide ok) -> Exit 0
#   T9  Zwei Mappings, eines fehlt (explizit) -> Exit 1 (streng)
#   T10 Default-Modus, beide Kanäle vorhanden   -> Exit 0
#   T11 Default-Modus, playRelease fehlt        -> Exit 0 + Warnung „nicht gebaut“ (weich)
#   T12 Default-Modus, beide Kanäle fehlen      -> Exit 0 + Warnung „nicht gebaut“ (weich)
#   T13 --strict, Kanal fehlt                   -> Exit 1 (hart)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
CHECK="$SCRIPT_DIR/check_sentry_optout_mapping.sh"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

PASS=0

# ── Stub-Mapping: bildet die ECHTE R8-Struktur nach ───────────────────────────
# Seit dem ProfanityFilter-Commit hängen die Inline-Records NICHT mehr unter
# dem Klassenblock von io.sentry.SentryClient, sondern am Ende des Blocks einer
# anderen Klasse (hier: com.vivid.feature.chat.bot.ProfanityFilter). Der
# Inline-Nachweis (C5) läuft deshalb über den geteilten Call-Site-Range:
#   270:283 → Lambda UND io.sentry.SentryClient.executeBeforeSendFeedback
#   392:406 → zweite Inline-Kopie, wieder mit SentryClient-Partner-Record
stub_mapping() {
  cat <<'EOF'
# compiler: R8
com.vivid.irlbroadcaster.SentryOptOutKt -> R8$$REMOVED$$CLASS$$123:
io.sentry.SentryClient -> io.sentry.u4:
    22:29:io.sentry.SentryOptions$BeforeSendCallback com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback(kotlin.jvm.functions.Function0):31:31 -> d
com.vivid.feature.chat.bot.ProfanityFilter -> wy3:
    270:283:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> j
    270:283:io.sentry.SentryEvent io.sentry.SentryClient.executeBeforeSendFeedback(io.sentry.SentryEvent,io.sentry.SentryOptions$BeforeSendCallback):31:31 -> q
    392:406:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> l
    392:406:io.sentry.SentryEvent io.sentry.SentryClient.executeBeforeSendFeedback(io.sentry.SentryEvent,io.sentry.SentryOptions$BeforeSendCallback):31:31 -> r
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

# T5: Lambda existiert, hat aber keinen SentryClient-Record im selben Range -> C5
M_OUTSIDE="$SANDBOX/mapping-outside.txt"
stub_mapping | awk 'BEGIN{done=0} /sentryBeforeSendCallback\$lambda\$0/ && !done {sub(/^ *[0-9]+:[0-9]+:/, "999:999:"); done=1} {print}' > "$M_OUTSIDE"
expect_fail "T5  Lambda ohne SentryClient-Range-Bezug (C5)" "$M_OUTSIDE"

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

# T9: Zwei Mappings, eines fehlt (explizite Pfade = streng) -> Exit 1
set +e
bash "$CHECK" "$M_REL" "$SANDBOX/playRelease-fehlt.txt" >/dev/null 2>&1
RC_DUAL_MISSING=$?
set -e
if [[ "$RC_DUAL_MISSING" != "0" ]]; then
  echo "✅ T9  Zwei Mappings, eines fehlt (explizit, Exit $RC_DUAL_MISSING)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T9 (Check lief mit Exit 0, obwohl ein explizites Mapping fehlt)"
  exit 1
fi

# T10: Default-Modus (keine Argumente), beide Kanäle vorhanden -> Exit 0
M_DEF_REL="$SANDBOX/def-release.txt"
M_DEF_PLAY="$SANDBOX/def-playRelease.txt"
stub_mapping > "$M_DEF_REL"
stub_mapping > "$M_DEF_PLAY"
set +e
MAPPING_RELEASE="$M_DEF_REL" MAPPING_PLAYRELEASE="$M_DEF_PLAY" bash "$CHECK" >/dev/null 2>&1
RC_DEF_OK=$?
set -e
if [[ "$RC_DEF_OK" == "0" ]]; then
  echo "✅ T10 Default-Modus, beide Kanäle vorhanden (Exit 0)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T10 (Default-Modus lief mit Exit $RC_DEF_OK, obwohl beide Mappings ok sind)"
  exit 1
fi

# T11: Default-Modus, playRelease fehlt -> Exit 0 + „nicht gebaut“-Warnung (weich!)
set +e
OUT_DEF_MISSING="$(MAPPING_RELEASE="$M_DEF_REL" MAPPING_PLAYRELEASE="$SANDBOX/playRelease-fehlt.txt" bash "$CHECK" 2>&1)"
RC_DEF_MISSING=$?
set -e
if [[ "$RC_DEF_MISSING" == "0" ]] && grep -q "nicht gebaut" <<<"$OUT_DEF_MISSING"; then
  echo "✅ T11 Default-Modus, playRelease fehlt -> Exit 0 + Warnung „nicht gebaut“"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T11 (Exit $RC_DEF_MISSING, Ausgabe: $(echo "$OUT_DEF_MISSING" | tail -1))"
  exit 1
fi

# T12: Default-Modus, beide Kanäle fehlen -> Exit 0 + Warnung (nichts gebaut)
set +e
OUT_DEF_BOTH="$(MAPPING_RELEASE="$SANDBOX/rel-fehlt.txt" MAPPING_PLAYRELEASE="$SANDBOX/play-fehlt.txt" bash "$CHECK" 2>&1)"
RC_DEF_BOTH=$?
set -e
if [[ "$RC_DEF_BOTH" == "0" ]] && grep -q "nicht gebaut" <<<"$OUT_DEF_BOTH"; then
  echo "✅ T12 Default-Modus, beide Kanäle fehlen -> Exit 0 + Warnung"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T12 (Exit $RC_DEF_BOTH, Ausgabe: $(echo "$OUT_DEF_BOTH" | tail -1))"
  exit 1
fi

# T13: --strict, ein Kanal fehlt -> Exit 1 (hart)
set +e
MAPPING_RELEASE="$M_DEF_REL" MAPPING_PLAYRELEASE="$SANDBOX/play-fehlt.txt" bash "$CHECK" --strict >/dev/null 2>&1
RC_STRICT=$?
set -e
if [[ "$RC_STRICT" != "0" ]]; then
  echo "✅ T13 --strict, Kanal fehlt -> Exit $RC_STRICT (hart)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T13 (--strict lief mit Exit 0, obwohl ein Kanal fehlt)"
  exit 1
fi

echo "✅ Sentry-Opt-out-Mapping-Check: $PASS/$PASS Tests grün."
