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
#   T5  Lambda ohne Callback-Aufruf im selben Range -> Exit 1 (C5)
#   T6  Fassade nicht REMOVED              -> Exit 1 (C6)
#   T7  Mapping veraltet (älter als Quelle)-> Exit 1 (C2)
#   T8  Zwei Mappings (release+playRelease, beide ok) -> Exit 0
#   T9  Zwei Mappings, eines fehlt (explizit) -> Exit 1 (streng)
#   T10 Default-Modus, beide Kanäle vorhanden   -> Exit 0
#   T11 Default-Modus, playRelease fehlt        -> Exit 0 + Warnung „nicht gebaut“ (weich)
#   T12 Default-Modus, beide Kanäle fehlen      -> Exit 0 + Warnung „nicht gebaut“ (weich)
#   T13 --strict, Kanal fehlt                   -> Exit 1 (hart)
#   T14 C0 korrekt komponiert                   -> Exit 0
#   T15 C0 direkter Opt-out-Aufruf in VividApplication -> Exit 1
#   T16 C0 Filter ohne Opt-out-Fabriksaufruf     -> Exit 1 (R8 wuerde Fabrik entfernen)
#   T17 C0 fehlende Filterquelle                 -> Exit 1
#   T18 --composition-only ohne Mapping, gebrochen -> Exit 1 (hart)
#   T19 --composition-only ohne Mapping, intakt   -> Exit 0
#   T20 echter Arbeitsbaum (C0 ohne Overrides)     -> Exit 0
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
CHECK="$SCRIPT_DIR/check_sentry_optout_mapping.sh"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

PASS=0

# ── Stub-Mapping: bildet die ECHTE R8-Struktur nach ───────────────────────────
# Die Inline-Records hängen NICHT unter dem Klassenblock von io.sentry.SentryClient,
# sondern am Ende des Blocks einer anderen Klasse (hier:
# com.vivid.feature.chat.bot.ProfanityFilter). Der Partner-Record im selben
# Call-Site-Range ist die von R8 erzeugte synthetische Lambda-Bruecke
# ($$ExternalSyntheticLambda0.execute(SentryEvent, Hint)) — seit die beforeSend-
# Kette aus einer eigenen Transport-Filter-Fabrik kommt (SentryTransportFilter.kt,
# #267). Genau diese Form ist am echten Mapping gemessen worden
# (standardRelease/standardPlayRelease, 06.10.2026); C5 prueft deshalb die vom
# SDK festgelegte Aufrufsignatur (SentryEvent, Hint) und nicht den Klassennamen,
# den R8 frei waehlt:
#   270:283 → Lambda UND synthetische Bruecke
#   392:406 → zweite Inline-Kopie, wieder mit Bruecken-Partner
stub_mapping() {
  cat <<'EOF'
# compiler: R8
com.vivid.irlbroadcaster.SentryOptOutKt -> R8$$REMOVED$$CLASS$$123:
io.sentry.SentryClient -> io.sentry.u4:
    22:29:io.sentry.SentryOptions$BeforeSendCallback com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback(kotlin.jvm.functions.Function0):31:31 -> d
com.vivid.feature.chat.bot.ProfanityFilter -> wy3:
    270:283:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> j
    270:283:io.sentry.SentryEvent com.vivid.feature.chat.bot.ProfanityFilterKt$$ExternalSyntheticLambda0.execute(io.sentry.SentryEvent,io.sentry.Hint):0 -> q
    392:406:io.sentry.SentryEvent com.vivid.irlbroadcaster.SentryOptOutKt.sentryBeforeSendCallback$lambda$0(kotlin.jvm.functions.Function0,io.sentry.SentryEvent,io.sentry.Hint):32:32 -> l
    392:406:io.sentry.SentryEvent com.vivid.feature.chat.bot.ProfanityFilterKt$$ExternalSyntheticLambda0.execute(io.sentry.SentryEvent,io.sentry.Hint):0 -> r
EOF
}

# Neutrale Sandbox-Quellen fuer C0, bewusst VOR allen Stub-Mappings angelegt:
# so ist jedes davon juenger als die Quellen und C2 (Veraltung) schlaegt nicht
# aus dem Ruder. T1–T13 fahren damit ueber run_check ISOLIERT C2–C6. Ohne diese
# Overrides prueften sie C0 gegen den echten Arbeitsbaum — jede C0-Mutation
# schlaege dann schon bei T1 an und die eigentlichen C0-Tests (T14–T19) wuerden
# nie erreicht. Den echten Stand prueft ausdruecklich T20.
Q_APP_OK="$SANDBOX/q-app-ok.kt"
Q_FILTER_OK="$SANDBOX/q-filter-ok.kt"
Q_APP_BARE="$SANDBOX/q-app-bare.kt"
Q_FILTER_DIRECT="$SANDBOX/q-filter-direct.kt"
Q_FILTER_MISSING="$SANDBOX/q-filter-missing.kt"

cat > "$Q_APP_OK" <<'EOF'
options.beforeSend = sentryBeforeSendWithTransportFilter { sentryEnabled }
EOF
cat > "$Q_FILTER_OK" <<'EOF'
internal fun sentryBeforeSendWithTransportFilter(isEnabled: () -> Boolean) =
    sentryBeforeSendCallback(isEnabled)
EOF
cat > "$Q_APP_BARE" <<'EOF'
options.beforeSend = sentryBeforeSendCallback { sentryEnabled }
EOF
# Die Komposition faellt still auf applySentryOptOut zurueck: die Fabrik hat
# keinen Aufrufer mehr, R8 entfernt sie, C3–C6 schlagen beim Release-Build fehl.
cat > "$Q_FILTER_DIRECT" <<'EOF'
internal fun sentryBeforeSendWithTransportFilter(isEnabled: () -> Boolean) =
    applySentryOptOut(it, isEnabled())
EOF

run_check() {
  local mapping="$1"
  set +e
  SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" \
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

# T5: Lambda existiert, teilt seinen Range aber mit keinem Callback-Aufruf -> C5
M_OUTSIDE="$SANDBOX/mapping-outside.txt"
stub_mapping | awk 'BEGIN{done=0} /sentryBeforeSendCallback\$lambda\$0/ && !done {sub(/^ *[0-9]+:[0-9]+:/, "999:999:"); done=1} {print}' > "$M_OUTSIDE"
expect_fail "T5  Lambda ohne Callback-Aufruf im selben Range (C5)" "$M_OUTSIDE"

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
SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" \
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
OUT_DEF_MISSING="$(SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" MAPPING_RELEASE="$M_DEF_REL" MAPPING_PLAYRELEASE="$SANDBOX/playRelease-fehlt.txt" bash "$CHECK" 2>&1)"
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
OUT_DEF_BOTH="$(SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" MAPPING_RELEASE="$SANDBOX/rel-fehlt.txt" MAPPING_PLAYRELEASE="$SANDBOX/play-fehlt.txt" bash "$CHECK" 2>&1)"
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
SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" MAPPING_RELEASE="$M_DEF_REL" MAPPING_PLAYRELEASE="$SANDBOX/play-fehlt.txt" bash "$CHECK" --strict >/dev/null 2>&1
RC_STRICT=$?
set -e
if [[ "$RC_STRICT" != "0" ]]; then
  echo "✅ T13 --strict, Kanal fehlt -> Exit $RC_STRICT (hart)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T13 (--strict lief mit Exit 0, obwohl ein Kanal fehlt)"
  exit 1
fi

# ── C0: R8-Vorbedingung der Komposition (quellenseitig, ohne Mapping) ─────────
# C0 sichert die Bedingung, unter der C3–C6 überhaupt gelten: die nachgewiesene
# Opt-out-Fabrik muss im beforeSend-Pfad referenziert bleiben. Wird sie durch einen
# eigenen Callback ersetzt, entfernt R8 sie und der Mapping-Nachweis faellt erst
# beim naechsten Release-Build auf — lange nach der Ursache.
# Die Sandbox-Quellen bilden je eine Variante ab; MAPPING zeigt auf ein volle
# Mapping, damit ausschliesslich C0 den Exit-Code bestimmt.
run_c0() {  # $1 = App-Quelle, $2 = Filter-Quelle
  # Das Mapping wird pro Lauf frisch erzeugt und damit juenger als die
  # Sandbox-Quellen: sonst schlaegt C2 (Veraltung) an und ueberlagert die
  # eigentliche C0-Aussage — der Test pruefe dann den falschen Check.
  stub_mapping > "$SANDBOX/c0-mapping.txt"
  set +e
  SRC_APP_OVERRIDE="$1" SRC_FILTER_OVERRIDE="$2" bash "$CHECK" "$SANDBOX/c0-mapping.txt" >/dev/null 2>&1
  local rc=$?
  set -e
  return $rc
}

# T14: korrekt komponiert -> Exit 0 (nur C0 entscheidet, Mapping ist vollstaendig)
if run_c0 "$Q_APP_OK" "$Q_FILTER_OK"; then
  echo "✅ T14 C0 korrekt komponiert -> Exit 0"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T14 (C0 schlug an, obwohl App die Filterfabrik und die Fabrik den Opt-out aufruft)"
  exit 1
fi

# T15: VividApplication setzt beforeSend direkt auf die Opt-out-Fabrik -> C0 hart
if ! run_c0 "$Q_APP_BARE" "$Q_FILTER_OK"; then
  echo "✅ T15 C0 direkter Opt-out-Aufruf in VividApplication -> Exit 1"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T15 (C0 blieb gruen — der Transport-Filter waere unwirksam)"
  exit 1
fi

# T16: Komposition faellt still auf applySentryOptOut zurueck -> C0 hart
if ! run_c0 "$Q_APP_OK" "$Q_FILTER_DIRECT"; then
  echo "✅ T16 C0 Filter ohne Opt-out-Fabriksaufruf -> Exit 1"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T16 (C0 blieb gruen — R8 wuerde die nachgewiesene Fabrik entfernen)"
  exit 1
fi

# T17: Negativkontrolle — die Filterquelle fehlt vollstaendig. Auch das muss
# hart scheitern, aber aus dem anderen Grund; haelt fest, dass C0 nicht bloss
# auf Textmuster im Arbeitsbaum anschlaegt, sondern die Datei wirklich braucht.
if ! run_c0 "$Q_APP_OK" "$Q_FILTER_MISSING"; then
  echo "✅ T17 C0 fehlende Filterquelle -> Exit 1"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T17 (C0 blieb gruen, obwohl die Filterquelle fehlt)"
  exit 1
fi

# T18: --composition-only braucht kein Mapping und ist hart — genau das macht C0
# im normalen Gate blockierend, waehrend C2–C6 ohne Release-Build weich bleiben.
# Gegenprobe: im Default-Modus waere derselbe Bruch nur eine Warnung.
set +e
SRC_APP_OVERRIDE="$Q_APP_BARE" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" \
  bash "$CHECK" --composition-only >/dev/null 2>&1
RC_COMP_BROKEN=$?
set -e
if [[ "$RC_COMP_BROKEN" != "0" ]]; then
  echo "✅ T18 --composition-only, Komposition gebrochen -> Exit $RC_COMP_BROKEN (hart, ohne Mapping)"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T18 (--composition-only blieb gruen — C0 wuerde im Gate nicht blockieren)"
  exit 1
fi

set +e
SRC_APP_OVERRIDE="$Q_APP_OK" SRC_FILTER_OVERRIDE="$Q_FILTER_OK" \
  bash "$CHECK" --composition-only >/dev/null 2>&1
RC_COMP_OK=$?
set -e
if [[ "$RC_COMP_OK" == "0" ]]; then
  echo "✅ T19 --composition-only, Komposition in Ordnung -> Exit 0"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T19 (--composition-only schlug am intakten Arbeitsstand an)"
  exit 1
fi

# T20: der ECHTE Arbeitsbaum. Ohne diesen Test koennte die ausgelieferte
# Komposition kaputt sein, waehrend alle Sandbox-Tests gruen bleiben — genau das
# waere der stille Ausfall, den C0 verhindern soll. Laeuft ohne SRC-Overrides.
set +e
bash "$CHECK" --composition-only >/dev/null 2>&1
RC_REAL=$?
set -e
if [[ "$RC_REAL" == "0" ]]; then
  echo "✅ T20 echter Arbeitsbaum: beforeSend-Komposition in Ordnung"
  PASS=$((PASS + 1))
else
  echo "❌ FAIL: T20 (der echte Arbeitsbaum verletzt C0 — die Sandbox-Tests pruefen nur die Sandbox)"
  exit 1
fi

echo "✅ Sentry-Opt-out-Mapping-Check: $PASS/$PASS Tests grün."
