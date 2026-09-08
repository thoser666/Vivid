#!/usr/bin/env bash
# Guard: Security-Loop für die Release-Pipeline.
#
# Prüft, dass die release-relevanten Sicherheitsregeln der Pipeline
# weiterhin vorhanden sind (Struktur-Guard, offline/deterministisch):
#   C1) Release-Keystore-Härtung: ohne KEYSTORE_BASE64 wird der Release-Build
#       hart abgebrochen (kein Debug-key-signiertes Release möglich).
#   C2) Signatur-Verifikation: das veröffentlichte APK/AAB wird gegen den
#       Release-Key geprüft (apksigner/keytool-Fingerprint-Vergleich).
#   C3) Reproduzierbarkeits-Hash-Vergleich: APK/Mapping/Metadata werden
#       bit-identisch gegen einen frischen Build geprüft.
#   C4) Sentry-Opt-out-Mapping-Nachweis in beiden Release-Kanälen.
#   C5) Bot-Workflows nutzen das User-Credential (AUTOMATION_TOKEN) —
#       kein stiller GITHUB_TOKEN-Fallback ohne ::warning::.
#
# Optionales Argument: Pfad zu einem R8-Mapping. Existiert die Datei, wird
# zusätzlich der Sentry-Opt-out-Nachweis am echten Mapping geführt (im CI
# nach dem Release-Build; lokal ohne Mapping = struktur-only, exit 0).
#
# Exit 0 = alle Regeln vorhanden, Exit 1 = eine Regel fehlt/verletzt.
#
# Testbarkeit (Offline-Selbsttest: scripts/test_security_loop.sh):
#   SECURITY_LOOP_PIPELINE — alternative Workflow-Datei (Fixture)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PIPELINE_FILE="${SECURITY_LOOP_PIPELINE:-$SCRIPT_DIR/../.github/workflows/release-pipeline.yml}"

fail() { echo "❌ [security-loop] $1" >&2; exit 1; }
info() { echo "ℹ️  [security-loop] $1"; }
pass() { echo "✅ [security-loop] $1"; }

[[ -f "$PIPELINE_FILE" ]] || fail "Release-Pipeline fehlt: $PIPELINE_FILE"

# C1: Keystore-Härtung — ohne Release-Key kein Release-Build.
grep -q 'KEYSTORE_BASE64 fehlt' "$PIPELINE_FILE" \
  || fail "C1 verletzt: Keystore-Härtung (Abbruch ohne KEYSTORE_BASE64) fehlt in $PIPELINE_FILE."
pass "C1: Keystore-Härtung vorhanden (Abbruch ohne Release-Key)."

# C2: Signatur-Verifikation gegen den Release-Key (APK + AAB).
grep -q 'Verify published APK signature against release key' "$PIPELINE_FILE" \
  || fail "C2 verletzt: APK-Signatur-Check fehlt."
grep -q 'Verify published AAB signature against release key' "$PIPELINE_FILE" \
  || fail "C2 verletzt: AAB-Signatur-Check fehlt."
pass "C2: APK- und AAB-Signatur-Checks gegen den Release-Key vorhanden."

# C3: Reproduzierbarkeits-Hash-Vergleich.
grep -q 'Compare hashes' "$PIPELINE_FILE" \
  || fail "C3 verletzt: Reproduzierbarkeits-Hash-Vergleich fehlt."
grep -q 'app-standard-release.apk' "$PIPELINE_FILE" \
  || fail "C3 verletzt: Flavor-korrekter Asset-Name app-standard-release.apk fehlt."
pass "C3: Reproduzierbarkeits-Hash-Vergleich mit Flavor-Asset-Namen vorhanden."

# C4: Sentry-Opt-out-Mapping-Nachweis in beiden Kanälen.
grep -q 'check_sentry_optout_mapping.sh app/build/outputs/mapping/standardRelease/mapping.txt' "$PIPELINE_FILE" \
  || fail "C4 verletzt: Sentry-Opt-out-Check (Release-Kanal) fehlt."
grep -q 'check_sentry_optout_mapping.sh app/build/outputs/mapping/standardPlayRelease/mapping.txt' "$PIPELINE_FILE" \
  || fail "C4 verletzt: Sentry-Opt-out-Check (Play-Kanal) fehlt."
pass "C4: Sentry-Opt-out-Mapping-Nachweis in beiden Kanälen verdrahtet."

# C5: Bot-Credential-Härtung — der GITHUB_TOKEN-Fallback muss sichtbar warnen.
grep -q 'AUTOMATION_TOKEN ist nicht gesetzt' "$PIPELINE_FILE" \
  || fail "C5 verletzt: ::warning:: bei fehlendem AUTOMATION_TOKEN fehlt."
pass "C5: Bot-Credential-Fallback warnt sichtbar (::warning::)."

# Optionales echtes Mapping: Sentry-Opt-out-Nachweis am gelieferten Artefakt.
if [[ $# -ge 1 && -n "$1" ]]; then
  mapping="$1"
  if [[ -f "$mapping" ]]; then
    info "Echtes Mapping übergeben — Sentry-Opt-out-Nachweis am Artefakt:"
    bash "$SCRIPT_DIR/check_sentry_optout_mapping.sh" "$mapping"
  else
    info "Mapping '$mapping' nicht vorhanden (z. B. lokaler Lauf ohne Release-Build) — struktur-only."
  fi
fi

pass "Security-Loop abgeschlossen: alle Release-Pipeline-Sicherheitsregeln vorhanden."
