#!/usr/bin/env bash
# Emulator-Test-Setup: Debug-Test-APKs installieren + Runtime-Permissions granten
# ================================================================================
# Hintergrund (#249, Vorfall Tag-Run 36849526447): Der CAMERA-Auto-Request des
# Streaming-Screens (94c4e7db) oeffnete beim Betreten des Start-Screens den
# Systemdialog "GrantPermissionsActivity" IM SELBEN Task ueber der MainActivity —
# die Compose-UI-Tests sahen einen leeren Semantik-Baum
# (IllegalStateException: No compose hierarchies found, 11 Tests / 5 Klassen rot).
#
# Gegenmassnahme (zweibeinig, Empfehlung aus #249):
#   1. App-Seite: Der Auto-Request ist aus dem Streaming-Screen entfernt — die
#      Kamera-Permission wird nur noch im Go-Live-Flow (User-Geste) angefordert;
#      die Idle-Preview startet nur, wenn die Permission bereits erteilt ist
#      (StreamingScreen.kt; die Engine guardt startIdlePreviewIfReady zusaetzlich
#      selbst per checkSelfPermission).
#   2. Gate-Seite (DIESES Skript): Auf dem Emulator sind die Permissions fuer die
#      Test-Packages explizit gegranted, bevor die instrumentierten Tests laufen.
#
# Warum nicht einfach `adb shell pm grant` im CI-Step? `pm grant` schlaegt fehl,
# wenn das Package nicht installiert ist — auf einem frischen CI-Emulator ist
# com.vivid.debug zu Skriptbeginn NICHT installiert (die APKs werden erst vom
# connected*-Gradle-Task gebaut+installiert). Deshalb installiert dieses Skript
# die Debug-Test-APKs beider Flavors vorconnected (assemble-Pfade), grantet dann
# und laesst die anschliessenden connected*-Tasks gegen die bereits installierten
# APKs laufen (Gradle installiert identische APKs nicht neu).
#
# Verwendete Permissions (App-Manifest, deklariert seit jeher):
#   - CAMERA:                    Streaming-Screen (Vorschau + Go-Live)
#   - RECORD_AUDIO:              Go-Live (Mikrofon-Pfad)
#   - POST_NOTIFICATIONS:        Go-Live ab API 33 (Streaming-Notification)
#   - ACCESS_FINE_LOCATION +     TextInfoWidget (GPS-Koordinaten/Geschwindigkeit)
#   - ACCESS_COARSE_LOCATION
#
# Alle Grants sind best-effort gegen Installationsversagen des ANDEREN Flavors
# gehaertet (foss-APK fehlt → Grant fuer foss wird uebersprungen, nicht fatal):
# der Failure-Modus der CI-Legs soll der echte Test, nicht das Setup sein.
# Ein fehlendes ZIEL-Flavor-APK ist dagegen fatal (kein Grant -> echter Test
# wuerde am Systemdialog haengen — genau der #249-Zustand).
#
# Nutzung (Emulator laeuft bereits — im CI via android-emulator-runner):
#   bash scripts/emulator_test_setup.sh [api-level]
set -euo pipefail

API_LEVEL="${1:-}"
if [ -n "$API_LEVEL" ] && ! [ "$API_LEVEL" -ge 0 ] 2>/dev/null; then
  echo "::error::emulator_test_setup: ungueltiges API-Level '$API_LEVEL'"
  exit 1
fi

ADB="${ADB:-adb}"
"$ADB" wait-for-device
if [ -n "$API_LEVEL" ]; then
  BOOT_API="$("$ADB" shell getprop ro.build.version.sdk | tr -d '[:space:]\r')"
  if [ "$BOOT_API" != "$API_LEVEL" ]; then
    echo "::warning::emulator_test_setup: API-Level-Drift erwartet=$API_LEVEL gelaufen=$BOOT_API (fahre mit $BOOT_API fort)"
  fi
fi

# Standard-Debug-APKs (Standard-Flavor) — Varianten-Konvention:
# App:   build/outputs/apk/<flavor>/debug/app-<flavor>-debug.apk
# Test:  build/outputs/apk/androidTest/<flavor>/debug/app-<flavor>-debug-androidTest.apk
STANDARD_APK="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
STANDARD_TEST_APK="app/build/outputs/apk/androidTest/standard/debug/app-standard-debug-androidTest.apk"
# FOSS-Debug-APKs
FOSS_APK="app/build/outputs/apk/foss/debug/app-foss-debug.apk"
FOSS_TEST_APK="app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk"

STANDARD_PKG="com.vivid.debug"          # applicationId com.vivid + .debug
FOSS_PKG="com.vivid.foss.debug"         # applicationId com.vivid + .foss + .debug

fail() { echo "::error::emulator_test_setup: $*"; exit 1; }
for f in "$STANDARD_APK" "$STANDARD_TEST_APK" "$FOSS_APK" "$FOSS_TEST_APK"; do
  [ -f "$f" ] || fail "erwartetes APK fehlt: $f (zuerst assembleStandardDebug/assembleFossDebug + assemble*-DebugAndroidTest bauen)"
done

install_pair() {
  local label="$1" apk="$2" test_apk="$3"
  echo "→ Installiere $label …"
  "$ADB" install -r -t "$apk" > /dev/null
  "$ADB" install -r -t "$test_apk" > /dev/null
}

install_pair "standard-debug ($STANDARD_PKG)" "$STANDARD_APK" "$STANDARD_TEST_APK"
install_pair "foss-debug ($FOSS_PKG)" "$FOSS_APK" "$FOSS_TEST_APK"

# POST_NOTIFICATIONS existiert erst ab API 33; aeltere Laufwerke ignorieren es
# ohnehin — Grants darueber hinaus schlagen mit SecurityException fehl.
grants=(android.permission.CAMERA android.permission.RECORD_AUDIO)
if [ -z "$API_LEVEL" ] || [ "$API_LEVEL" -ge 33 ]; then
  grants+=(android.permission.POST_NOTIFICATIONS)
fi
grants+=(android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION)

grant_to() {
  local pkg="$1" perms="$2"
  # Whitespace-stripped Einzeiler pro Shell-Command (adb exec-out vs. shell
  # Kudos: \r-Reste in der Ausgabe tolerieren).
  for p in $perms; do
    if "$ADB" shell pm grant "$pkg" "$p" 2> /dev/null; then
      echo "  ✓ $pkg: $p"
    else
      echo "::warning::emulator_test_setup: pm grant $pkg $p fehlgeschlagen (Package nicht installiert oder Permission nicht anforderbar)"
    fi
  done
}

echo "→ Grant Runtime-Permissions (beide Debug-Flavors) …"
grant_to "$STANDARD_PKG" "${grants[*]}"
grant_to "$FOSS_PKG" "${grants[*]}"

echo "✅ Emulator-Setup fertig: beide Debug-Flavors installiert, Permissions gegranted"
