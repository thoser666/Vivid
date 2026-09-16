#!/usr/bin/env bash
# Manifest-Security-Guard (Vivid): schützt die Backup-Konfiguration der App.
#
#   C1: allowBackup muss EXPLIZIT "false" sein (attributfrei == true == Backup
#       aller App-Daten inkl. OAuth-Tokens; Code-Scanning-Finding #471).
#   C2: Backup-Verdrahtung muss real schützen. Attribute sind erlaubt
#       (Lint DataExtractionRules verlangt sie ab Android 12+), aber jede
#       verdrahtete Datei muss existieren und mindestens eine echte
#       <exclude>-Regel enthalten. Leere Template-Rules (Stand vor
#       2026-09-12) verdrahteten keinen Schutz und täuschten ihn vor
#       (Finding #471) — genau das bleibt verboten.
#
# Aufruf: bash scripts/check_manifest_security.sh [PFAD_ZUM_MANIFEST]
# Selbsttest: bash scripts/test_manifest_security.sh
set -euo pipefail

cd "$(dirname "$0")/.."
MANIFEST="${1:-app/src/main/AndroidManifest.xml}"

fail() { echo "❌ [manifest-security] $1"; exit 1; }

[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml nicht gefunden: $MANIFEST"

# C1: allowBackup explizit false.
if grep -q 'android:allowBackup="true"' "$MANIFEST"; then
  fail "C1 verletzt: android:allowBackup=\"true\" — Backup muss deaktiviert sein (Finding #471)."
fi
if ! grep -q 'android:allowBackup="false"' "$MANIFEST"; then
  fail "C1 verletzt: android:allowBackup fehlt (DEFAULT ist true) — muss explizit \"false\" sein (Finding #471)."
fi

# C2: jede verdrahtete Backup-Regeldatei muss existieren und real exclusen.
BASE_DIR="$(dirname "$MANIFEST")"
for attr in fullBackupContent dataExtractionRules; do
  val=$(grep -oE "android:${attr}=\"[^\"]+\"" "$MANIFEST" | head -1 | sed -E 's/.*="([^"]+)"/\1/') || true
  [[ -n "$val" ]] || continue
  file="${val#@xml/}"
  rules="$BASE_DIR/res/xml/$file.xml"
  if [[ ! -f "$rules" ]]; then
    fail "C2 verletzt: $attr verweist auf fehlende Regeldatei $rules (Finding #471)."
  fi
  if ! grep -q '<exclude' "$rules"; then
    fail "C2 verletzt: $attr verweist auf $rules ohne echte <exclude>-Regel — leeres Template (Finding #471)."
  fi
done

echo "✅ [manifest-security] allowBackup=false, Backup-Verdrahtung schützt real (C1/C2 ok)."