#!/usr/bin/env bash
# Selbsttest: Manifest-Security-Guard (scripts/check_manifest_security.sh).
#
#   F1: allowBackup="true" → rot (exit 1)
#   F2: allowBackup fehlt (Default true) → rot
#   F3: fullBackupContent-Attribut ohne Regeldatei → rot
#   F4: allowBackup=false sauber + MediaProjection-FGS → grün (exit 0)
#   F5: echter Repo-Stand (Attribute + echte Regeldateien) → grün
#   F6: Attribute + Regeldateien mit echten excludes (Fixture) → grün
#   F7: Attribut auf existierende, leere Regeldatei → rot (Template)
#   F8: MediaProjection-FGS vollständig fehlend → rot (C3)
#   F9: MediaProjection-Permission ohne foregroundServiceType → rot (C3)
#
# Nutzung: bash scripts/test_manifest_security.sh
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_manifest_security.sh"
fail() { echo "❌ [test-manifest-security] $1"; exit 1; }
pass() { echo "✅ [test-manifest-security] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

manifest() { # $1 = Dateiname ohne .xml, $2 = Inhalt
  printf '%s' "$2" >"$TMP/$1.xml"
}
# Minimales Manifest mit MediaProjection-FGS (Permission + foregroundServiceType).
manif_mp() { # $1 = Dateiname ohne .xml, $2 = application-Attribute
  printf '%s\n' \
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">' \
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />' \
    "<application $2>" \
    '  <service android:name="com.vivid.irlbroadcaster.StreamingService" android:foregroundServiceType="microphone|camera|mediaProjection" />' \
    '</application>' \
    '</manifest>' >"$TMP/$1.xml"
}
expect_fail() { # $1 = Beschreibung, $2 = Fixture-Datei, $3 = erwartete Meldungs-Fragment
  local rc
  if MANIFEST="$TMP/$2" bash "$GUARD" "$TMP/$2" >"$TMP/out.txt" 2>&1; then
    fail "$1: Guard muss exit 1 liefern."
  else
    # Nur hier ist der Guard-Exit-Code verfügbar — ein `if` ohne `else` liefert
    # sonst pauschal 0 (Bash-Semantik), egal wie der Guard endete.
    rc=$?
  fi
  [[ "$rc" -eq 1 ]] || fail "$1: erwarteter Exit 1, bekam $rc."
  grep -q "$3" "$TMP/out.txt" || fail "$1: Meldung enthält nicht '$3'."
  pass "$1."
}

# ── F1: allowBackup="true" → rot ────────────────────────────────────────────
manifest F1 '<application android:allowBackup="true" />'
expect_fail "F1: allowBackup=true" "F1.xml" "C1 verletzt"

# ── F2: allowBackup fehlt (Default true) → rot ──────────────────────────────
manifest F2 '<application android:networkSecurityConfig="@xml/network_security_config" />'
expect_fail "F2: allowBackup fehlt" "F2.xml" "C1 verletzt"

# ── F3: allowBackup=false + FullBackupContent-Attribut → rot ────────────────
manifest F3 '<application android:allowBackup="false" android:fullBackupContent="@xml/backup_rules" />'
expect_fail "F3: Template-Backup-Attribut" "F3.xml" "C2 verletzt"

# ── F4: allowBackup=false sauber + MediaProjection-FGS → grün ───────────────
manif_mp F4 'android:allowBackup="false"'
if bash "$GUARD" "$TMP/F4.xml" >/dev/null 2>&1; then :; else
  fail "F4: sauberes Manifest muss exit 0 liefern."
fi
pass "F4: allowBackup=false + MediaProjection-FGS sauber."

# ── F6: Attribute + echte Regeldateien (Fixture) → grün ────────────────────
mkdir -p "$TMP/res/xml"
printf '%s\n' '<full-backup-content>' '    <exclude domain="root" path="." />' '</full-backup-content>' >"$TMP/res/xml/backup_rules.xml"
printf '%s\n' '<data-extraction-rules>' '    <cloud-backup>' '        <exclude domain="root" path="." />' '    </cloud-backup>' '</data-extraction-rules>' >"$TMP/res/xml/data_extraction_rules.xml"
manif_mp F6 '<application android:allowBackup="false" android:fullBackupContent="@xml/backup_rules" android:dataExtractionRules="@xml/data_extraction_rules" />'
if bash "$GUARD" "$TMP/F6.xml" >/dev/null 2>&1; then :; else
  fail "F6: Attribute mit echten Regeldateien müssen grün sein (Lint fordert die Verdrahtung)."
fi
pass "F6: Attribute + echte Regeldateien → grün."

# ── F7: Attribut auf existierende, leere Regeldatei → rot ──────────────────
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/empty_rules.xml"
manifest F7 '<application android:allowBackup="false" android:fullBackupContent="@xml/empty_rules" />'
expect_fail "F7: leere Regeldatei" "F7.xml" "C2 verletzt"

# ── F8: MediaProjection-FGS vollständig fehlend → rot (C3) ─────────────────
manifest F8 '<application android:allowBackup="false" />'
expect_fail "F8: MediaProjection-Permission fehlt" "F8.xml" "C3 verletzt"

# ── F9: Permission da, foregroundServiceType ohne mediaProjection → rot ───
printf '%s\n' \
  '<manifest xmlns:android="http://schemas.android.com/apk/res/android">' \
  '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />' \
  '<application android:allowBackup="false">' \
  '  <service android:name="com.vivid.irlbroadcaster.StreamingService" android:foregroundServiceType="microphone|camera" />' \
  '</application>' \
  '</manifest>' >"$TMP/F9.xml"
expect_fail "F9: FGS-Typ ohne mediaProjection" "F9.xml" "C3 verletzt"

# ── F5: echter Repo-Stand → grün ────────────────────────────────────────────
if bash "$GUARD" >/dev/null 2>&1; then :; else
  fail "F5: echtes Manifest (app/src/main/AndroidManifest.xml) muss grün sein."
fi
pass "F5: echter Repo-Stand → grün."

echo "✅ [test-manifest-security] Alle 9 Fälle grün."