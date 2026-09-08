#!/usr/bin/env bash
# Selbsttest: Security-Loop-Guard (scripts/check_security_loop.sh, Offline-Modus).
#
#   T1: Echte release-pipeline.yml  → alle 5 Checks grün, exit 0
#   T2: Fixture ohne Keystore-Härtung (C1) → exit 1
#   T3: Fixture ohne APK-Signatur-Check (C2) → exit 1
#   T4: Fixture ohne Hash-Vergleich (C3) → exit 1
#   T5: Fixture ohne Sentry-Opt-out (C4) → exit 1
#   T6: Fixture ohne AUTOMATION_TOKEN-Warnung (C5) → exit 1
#   T7: Fehlende Pipeline-Datei → exit 1
#   T8: Nicht existierendes Mapping-Argument → struktur-only, exit 0
#
# Nutzung: bash scripts/test_security_loop.sh
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_security_loop.sh"
fail() { echo "❌ [test-security-loop] $1"; exit 1; }
pass() { echo "✅ [test-security-loop] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ── T1: echte Pipeline → grün ───────────────────────────────────────────────
if bash "$GUARD" >"$TMP/out1.txt" 2>&1; then :; else
  fail "T1: echte Pipeline muss exit 0 liefern (bekam $?)."
fi
for c in C1 C2 C3 C4 C5; do
  grep -q "$c:" "$TMP/out1.txt" || fail "T1: Check $c fehlt in der Ausgabe."
done
pass "T1: echte Pipeline → alle 5 Checks grün, exit 0."

# ── Fixture-Basis: echte Pipeline kopieren und gezielt beschneiden ──────────
make_fixture() { # $1 = zu entfernendes Muster → Datei $TMP/fixture.yml
  grep -v "$1" .github/workflows/release-pipeline.yml >"$TMP/fixture.yml" \
    || true
}

# ── T2: C1 fehlt → exit 1 ───────────────────────────────────────────────────
make_fixture 'KEYSTORE_BASE64 fehlt'
SECURITY_LOOP_PIPELINE="$TMP/fixture.yml" bash "$GUARD" >"$TMP/out2.txt" 2>&1 \
  && fail "T2: fehlende Keystore-Härtung muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T2: erwarteter Exit 1, bekam ${rc:-?}."
grep -q 'C1 verletzt' "$TMP/out2.txt" || fail "T2: C1-Meldung fehlt."
pass "T2: fehlende Keystore-Härtung → exit 1."

# ── T3: C2 fehlt → exit 1 ───────────────────────────────────────────────────
make_fixture 'Verify published APK signature against release key'
SECURITY_LOOP_PIPELINE="$TMP/fixture.yml" bash "$GUARD" >"$TMP/out3.txt" 2>&1 \
  && fail "T3: fehlender APK-Signatur-Check muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T3: erwarteter Exit 1, bekam ${rc:-?}."
grep -q 'C2 verletzt' "$TMP/out3.txt" || fail "T3: C2-Meldung fehlt."
pass "T3: fehlender APK-Signatur-Check → exit 1."

# ── T4: C3 fehlt → exit 1 ───────────────────────────────────────────────────
make_fixture 'Compare hashes'
SECURITY_LOOP_PIPELINE="$TMP/fixture.yml" bash "$GUARD" >"$TMP/out4.txt" 2>&1 \
  && fail "T4: fehlender Hash-Vergleich muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T4: erwarteter Exit 1, bekam ${rc:-?}."
grep -q 'C3 verletzt' "$TMP/out4.txt" || fail "T4: C3-Meldung fehlt."
pass "T4: fehlender Hash-Vergleich → exit 1."

# ── T5: C4 fehlt → exit 1 ───────────────────────────────────────────────────
make_fixture 'check_sentry_optout_mapping.sh app/build/outputs/mapping/standardRelease/mapping.txt'
SECURITY_LOOP_PIPELINE="$TMP/fixture.yml" bash "$GUARD" >"$TMP/out5.txt" 2>&1 \
  && fail "T5: fehlender Sentry-Opt-out-Check muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T5: erwarteter Exit 1, bekam ${rc:-?}."
grep -q 'C4 verletzt' "$TMP/out5.txt" || fail "T5: C4-Meldung fehlt."
pass "T5: fehlender Sentry-Opt-out-Check → exit 1."

# ── T6: C5 fehlt → exit 1 ───────────────────────────────────────────────────
make_fixture 'AUTOMATION_TOKEN ist nicht gesetzt'
SECURITY_LOOP_PIPELINE="$TMP/fixture.yml" bash "$GUARD" >"$TMP/out6.txt" 2>&1 \
  && fail "T6: fehlende AUTOMATION_TOKEN-Warnung muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T6: erwarteter Exit 1, bekam ${rc:-?}."
grep -q 'C5 verletzt' "$TMP/out6.txt" || fail "T6: C5-Meldung fehlt."
pass "T6: fehlende AUTOMATION_TOKEN-Warnung → exit 1."

# ── T7: fehlende Pipeline-Datei → exit 1 ────────────────────────────────────
SECURITY_LOOP_PIPELINE="$TMP/does-not-exist.yml" bash "$GUARD" >"$TMP/out7.txt" 2>&1 \
  && fail "T7: fehlende Pipeline muss exit 1 liefern." || rc=$?
[[ "${rc:-0}" -eq 1 ]] || fail "T7: erwarteter Exit 1, bekam ${rc:-?}."
pass "T7: fehlende Pipeline-Datei → exit 1."

# ── T8: nicht existierendes Mapping-Argument → struktur-only, exit 0 ────────
if bash "$GUARD" "$TMP/no-such-mapping.txt" >"$TMP/out8.txt" 2>&1; then :; else
  fail "T8: nicht existierendes Mapping muss struktur-only exit 0 liefern."
fi
grep -q 'struktur-only' "$TMP/out8.txt" || fail "T8: struktur-only-Hinweis fehlt."
pass "T8: nicht existierendes Mapping → struktur-only, exit 0."

echo ""
echo "✅ [test-security-loop] Alle 8 Selbsttests bestanden."
