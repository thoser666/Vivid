#!/usr/bin/env bash
#
# set_emulator_locale.sh <LOCALE>
#
# Setzt die System-Locale eines laufenden Emulators über den Settings-Store
# (`system_locales`) und macht sie per Framework-Neustart (`stop`/`start`)
# wirksam — NICHT per `adb reboot`: Ein Reboot lässt den Emulator nach dem
# Boot in "device offline" hängen und der boot_completed-Check erreicht nie 1.
# Der Framework-Neustart funktioniert auf google_apis-Images (userdebug) mit
# `adb root` und das Gerät bleibt verbunden.
#
# Abschließend prüft ein Locale-Proof die EFFEKTIVE Geräte-Konfiguration
# (`cmd activity get-config`), damit der Lauf ein echter Beweis für die
# Locale-Robustheit ist — nicht nur ein gesetzter Settings-Wert.
#
# Aufruf: bash scripts/set_emulator_locale.sh "de-DE"
# Exit 0 = Locale wirksam, Exit 1 = Locale-Proof fehlgeschlagen.
set -u

LOCALE="${1:?Verwendung: set_emulator_locale.sh <en-US|de-DE>}"
EXPECTED_LANG=$(echo "$LOCALE" | cut -d- -f1)

echo "→ Setze System-Locale auf ${LOCALE} und starte Framework neu …"
adb root >/dev/null 2>&1 || true
adb wait-for-device

# Locale in den Settings-Store schreiben und Framework neu starten.
adb shell "settings put system system_locales ${LOCALE}"
adb shell "stop && start"
adb wait-for-device

# Auf den Framework-Boot warten (max. 5 Minuten).
for i in $(seq 1 60); do
  B=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$B" = "1" ] && break
  sleep 5
done
sleep 5

# Locale-Proof: EFFEKTIVE Geräte-Konfiguration auswerten.
# Zwei Formate von `cmd activity get-config`:
#   API ≤ 34:  config: {mcc=310 mnc=260 locale=en_US layoutDirection=0 …}
#   API 35+:   config: mcc310-mnc260-de-rDE-ldltr-sw411dp-…
CFG=$(adb shell "cmd activity get-config" | tr -d '\r')
ACTUAL_LANG=$(echo "$CFG" | sed -nE 's/.*locale=([a-z]{2})([-_][A-Za-z]{2})?.*/\1/p')
[ -z "$ACTUAL_LANG" ] && ACTUAL_LANG=$(echo "$CFG" | sed -nE 's/.*-mnc[0-9]+-([a-z]{2})(-[A-Za-z]{2})?-.*/\1/p')

echo "→ Effektive Geräte-Locale: ${ACTUAL_LANG:-<leer>} (erwartet: ${EXPECTED_LANG})"
if [ -z "$ACTUAL_LANG" ] || [ "$ACTUAL_LANG" != "$EXPECTED_LANG" ]; then
  echo "::error::Locale-Proof fehlgeschlagen: erwartet ${LOCALE}, Geräte-Config: ${CFG}"
  exit 1
fi

echo "→ Locale-Proof OK (${ACTUAL_LANG})"
