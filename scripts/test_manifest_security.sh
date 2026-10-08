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
#   F10–F24: Android-Semantik „letzter Attributwert gewinnt" für doppelte/
#   mehrfache Attribute (allowBackup, fullBackupContent, dataExtractionRules)
#   plus C2-/C3-Randfälle und echter Repo-Stand.
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

# ── F10: doppeltes allowBackup (true dann false) → grün — Android-Semantik: letzter Wert gewinnt ──
# Ohne diese Änderung würde der Guard scheitern, weil er den ersten Treffer (true) genommen hat.
# Jetzt (C1 entscheidet über existierende allowBackup="false" ohne Positions-Anker) ist das ok.
# Wichtig: manif_mp bringt C3 (Permission + FGS-Typ) mit, sonst schlägt C3 an, nicht C1 —
# der Fall ist damit ein vollständiges Fixutur.
manif_mp F10 'android:allowBackup="true" android:allowBackup="false"'
if bash "$GUARD" "$TMP/F10.xml" >/dev/null 2>&1; then :; else
  fail "F10: doppeltes allowBackup (true dann false) muss grün sein (Android-Semantik: letzter Wert gewinnt)."
fi
pass "F10: doppeltes allowBackup (true dann false) → grün."

# ── F11: doppeltes fullBackupContent, unterschiedliche Werte, letzter gewinnt ──
# Erster Eintrag ist leere Template-Regel (schlecht), zweiter ist echter Schutz (gut).
# Jetzt (C2 greift auf alle Werte, bildet den letzten) muss das grün sein.
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/empty_rules.xml"
printf '%s\n' '<full-backup-content>' '    <exclude domain="root" path="." />' '</full-backup-content>' >"$TMP/res/xml/real_rules.xml"
manif_mp F11 'android:allowBackup="false" android:fullBackupContent="@xml/empty_rules" android:fullBackupContent="@xml/real_rules"'
if bash "$GUARD" "$TMP/F11.xml" >/dev/null 2>&1; then :; else
  fail "F11: doppeltes fullBackupContent mit letztem echten Schutz muss grün sein (Android-Semantik)."
fi
pass "F11: doppeltes fullBackupContent, letztergewinn → grün."

# ── F12: doppeltes fullBackupContent, echter Schutz dann leere Template → rot (erstes ok, letztes schlecht) ──
# Umdrehung der Semantik: jetzt ist der erste Eintrag der echte Schutz, der zweite die leere Vorlage.
# Android-Semantik: das Ergebnis ist das letzte Attribut → das Guard-Ergebnis muss FEHLER sein.
printf '%s\n' '<full-backup-content>' '    <exclude domain="root" path="." />' '</full-backup-content>' >"$TMP/res/xml/first_real.xml"
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/second_empty.xml"
manif_mp F12 'android:allowBackup="false" android:fullBackupContent="@xml/first_real" android:fullBackupContent="@xml/second_empty"'
if bash "$GUARD" "$TMP/F12.xml" >/dev/null 2>&1; then
  fail "F12: doppeltes fullBackupContent, letzte leere Vorlage → muss rot sein (Android-Semantik)."
fi
pass "F12: doppeltes fullBackupContent, letztes schlecht → rot."

# ── F13: doppeltes dataExtractionRules, letztergewinn → grün ──────────────────────────────────────────────
printf '%s\n' '<data-extraction-rules>' '    <cloud-backup>' '        <exclude domain="root" path="." />' '    </cloud-backup>' '</data-extraction-rules>' >"$TMP/res/xml/data_first_real.xml"
printf '%s\n' '<data-extraction-rules>' '    <cloud-backup>' '        <exclude domain="root" path="." />' '    </cloud-backup>' '</data-extraction-rules>' >"$TMP/res/xml/data_second_real.xml"
manif_mp F13 'android:allowBackup="false" android:dataExtractionRules="@xml/data_first_real" android:dataExtractionRules="@xml/data_second_real"'
if bash "$GUARD" "$TMP/F13.xml" >/dev/null 2>&1; then :; else
  fail "F13: doppeltes dataExtractionRules, letztergewinn → muss grün sein (Android-Semantik)."
fi
pass "F13: doppeltes dataExtractionRules, letztergewinn → grün."

# ── F14: C2 ohne Attribut (kein fullBackupContent, kein dataExtractionRules) → grün ──
# Das Guard muss dann einfach alle Attribute überspringen (keine Vorlage = kein Backup per Lint, dafür kein Guard-Problem).
manif_mp F14 'android:allowBackup="false"'
if bash "$GUARD" "$TMP/F14.xml" >/dev/null 2>&1; then :; else
  fail "F14: keine Backup-Attribute (nur allowBackup=false) → muss grün sein."
fi
pass "F14: keine Backup-Attribute → grün."

# ── F15: C2 mit Attribut, aber kein letzter Wert (nur ein Wert) → F8/F9-ähnlich ──
# Das Guard muss genau einen Wert verarbeiten können (der letzte ist der einzige).
# Hier: leere Vorlage → rot.
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/only_empty.xml"
manif_mp F15 'android:allowBackup="false" android:fullBackupContent="@xml/only_empty"'
if bash "$GUARD" "$TMP/F15.xml" >/dev/null 2>&1; then
  fail "F15: einAttr mit leerer Vorlage → muss rot sein."
fi
pass "F15: einAttr mit leerer Vorlage → rot."

# ── F16: C1 mit allowBackup=false und allowBackup=true (false dann true → rot, Android-Semantik) ──
# Jetzt das andere Pol: eine allowBackup="false" UND eine allowBackup="true" danach.
# C1 prüft nur auf existierende allowBackup="false", aber Android würde true nehmen (letzter gewinnt).
# Der Guard wird trotzdem scheitern, weil keine allowBackup="false" existiert.
manif_mp F16 'android:allowBackup="false" android:allowBackup="true"'
if bash "$GUARD" "$TMP/F16.xml" >/dev/null 2>&1; then
  fail "F16: allowBackup=false dann true → muss rot sein (kein allowBackup=false am Ende)."
fi
pass "F16: allowBackup=false dann true → rot."

# ── F17: C1 mit nur allowBackup=true (kein false) → rot ──
manifest F17 '<application android:allowBackup="true" />'
if bash "$GUARD" "$TMP/F17.xml" >/dev/null 2>&1; then
  fail "F17: nur allowBackup=true → muss rot sein."
fi
pass "F17: nur allowBackup=true → rot."

# ── F18: Mehrfaches allowBackup=true, kein false → rot ──
manifest F18 '<application android:allowBackup="true" android:allowBackup="true" />'
if bash "$GUARD" "$TMP/F18.xml" >/dev/null 2>&1; then
  fail "F18: mehrfaches allowBackup=true, kein false → muss rot sein."
fi
pass "F18: mehrfaches allowBackup=true → rot."

# ── F19: doppeltes allowBackup=false (beide false) → grün ──
manif_mp F19 'android:allowBackup="false" android:allowBackup="false"'
if bash "$GUARD" "$TMP/F19.xml" >/dev/null 2>&1; then :; else
  fail "F19: doppeltes allowBackup=false → muss grün sein."
fi
pass "F19: doppeltes allowBackup=false → grün."

# ── F20: drei allowBackup, false:true:false → letzter false → grün ──
manif_mp F20 'android:allowBackup="false" android:allowBackup="true" android:allowBackup="false"'
if bash "$GUARD" "$TMP/F20.xml" >/dev/null 2>&1; then :; else
  fail "F20: drei allowBackup, letzter false → muss grün sein (Android-Semantik)."
fi
pass "F20: drei allowBackup, letzter false → grün."

# ── F21: drei allowBackup, false:true:true → letzter true → rot ──
manif_mp F21 'android:allowBackup="false" android:allowBackup="true" android:allowBackup="true"'
if bash "$GUARD" "$TMP/F21.xml" >/dev/null 2>&1; then
  fail "F21: drei allowBackup, letzter true → muss rot sein (Android-Semantik)."
fi
pass "F21: drei allowBackup, letzter true → rot."

# ── F22: dreifaches fullBackupContent, in der Mitte echter Schutz, letzte leere Vorlage → rot ──
# Dreiwertiger Fall: erster Wert leer, mittlerer echt, letzter leer.
# Android-Semantik: der letzte gewinnt → das Guard muss ROT sein (erster oder
# mittlerer Wert als Gewinner würde fälschlich grün liefern).
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/tri_empty_first.xml"
printf '%s\n' '<full-backup-content>' '    <exclude domain="root" path="." />' '</full-backup-content>' >"$TMP/res/xml/tri_real_middle.xml"
printf '%s\n' '<full-backup-content />' >"$TMP/res/xml/tri_empty_last.xml"
manif_mp F22 'android:allowBackup="false" android:fullBackupContent="@xml/tri_empty_first" android:fullBackupContent="@xml/tri_real_middle" android:fullBackupContent="@xml/tri_empty_last"'
if bash "$GUARD" "$TMP/F22.xml" >/dev/null 2>&1; then
  fail "F22: dreifaches fullBackupContent, letzte leere Vorlage → muss rot sein (Android-Semantik: letzter gewinnt)."
fi
pass "F22: dreifaches fullBackupContent, letzte leere Vorlage → rot."

# ── F23: C1 mit nur allowBackup=false → grün ──
manif_mp F23 'android:allowBackup="false"'
if bash "$GUARD" "$TMP/F23.xml" >/dev/null 2>&1; then :; else
  fail "F23: nur allowBackup=false → muss grün sein."
fi
pass "F23: nur allowBackup=false → grün."

# ── F24: C1 mit allowBackup="false" und allowBackup="true" (true dann false → grün, Android-Semantik) ──
# Das ist der Fall, in dem der Guard das erste true ignoriert und den letzten false nimmt.
manif_mp F24 'android:allowBackup="true" android:allowBackup="false"'
if bash "$GUARD" "$TMP/F24.xml" >/dev/null 2>&1; then :; else
  fail "F24: true dann false → muss grün sein (Android-Semantik: letzter false gewinnt)."
fi
pass "F24: true dann false → grün."

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

echo "✅ [test-manifest-security] Alle 24 Fälle grün."