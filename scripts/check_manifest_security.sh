#!/usr/bin/env bash
# Manifest-Security-Guard (Vivid): schützt die Backup-Konfiguration der App.
#
#   C1: allowBackup muss EXPLIZIT "false" sein (attributfrei == true == Backup
#       aller App-Daten inkl. OAuth-Tokens; Code-Scanning-Finding #471).
#   C2: Keine Template-Attribute fullBackupContent/dataExtractionRules — die
#       früheren leeren Template-Rules (backup_rules.xml, data_extraction_rules.xml)
#       verdrahteten KEINEN echten Schutz und sind seit 2026-09-12 entfernt.
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

# C2: keine Backup-Template-Attribute mehr.
if grep -qE 'android:(fullBackupContent|dataExtractionRules)=' "$MANIFEST"; then
  fail "C2 verletzt: fullBackupContent/dataExtractionRules verdrahtet — bei allowBackup=false unnötig und irreführend (Finding #471)."
fi

echo "✅ [manifest-security] allowBackup=false, keine Template-Backup-Attribute (C1/C2 ok)."