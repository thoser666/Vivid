#!/usr/bin/env bash
#
# set_emulator_locale.sh <LOCALE>
#
# Setzt die System-Locale eines laufenden Emulators über den Settings-Store
# (`system_locales`).  KEIN Framework-Neustart und KEIN `adb reboot`: Beides
# lässt den Emulator in der CI in "device offline" hängen (adbd antwortet
# nicht mehr, boot_completed erreicht nie 1).
#
# Der Settings-Store wirkt SOFORT für neue Prozesse — lokal verifiziert:
# `cmd activity get-config` springt unmittelbar auf die neue Locale um
# (z. B. `de-rDE`), ohne Neustart.  `connectedDebugAndroidTest` installiert
# und startet die Test-App frisch, die die Locale damit direkt bekommt.
#
# Locale-Proof: Die EFFEKTIVE Geräte-Konfiguration muss die erwartete Sprache
# enthalten — nur dann ist der Lauf ein echter Beweis für die
# Locale-Robustheit (und nicht ein versehentlich en-US gebliebener Lauf).
# Zwei Formate von `cmd activity get-config`:
#   API ≤ 34:  config: {mcc=310 mnc=260 locale=en_US layoutDirection=0 …}
#   API 35+:   config: mcc310-mnc260-de-rDE-ldltr-sw411dp-…
#
# Aufruf: bash scripts/set_emulator_locale.sh "de-DE"
# Exit 0 = Locale effektiv, Exit 1 = Locale-Proof fehlgeschlagen.
set -u

LOCALE="${1:?Verwendung: set_emulator_locale.sh <en-US|de-DE>}"
EXPECTED_LANG=$(echo "$LOCALE" | cut -d- -f1)

echo "→ Setze System-Locale auf ${LOCALE} (Settings-Store, kein Neustart) …"
adb shell "settings put system system_locales ${LOCALE}" || exit 1

# Locale-Proof gegen die EFFEKTIVE Geräte-Konfiguration.
CFG=$(adb shell "cmd activity get-config" | tr -d '\r')
ACTUAL_LANG=$(echo "$CFG" | sed -nE 's/.*locale=([a-z]{2})([-_][A-Za-z]{2})?.*/\1/p')
[ -z "$ACTUAL_LANG" ] && ACTUAL_LANG=$(echo "$CFG" | sed -nE 's/.*-mnc[0-9]+-([a-z]{2})(-[A-Za-z]{2})?-.*/\1/p')

echo "→ Effektive Geräte-Locale: ${ACTUAL_LANG:-<leer>} (erwartet: ${EXPECTED_LANG})"
if [ -z "$ACTUAL_LANG" ] || [ "$ACTUAL_LANG" != "$EXPECTED_LANG" ]; then
  echo "::error::Locale-Proof fehlgeschlagen: erwartet ${LOCALE}, Geräte-Config: ${CFG}"
  exit 1
fi

echo "→ Locale-Proof OK (${ACTUAL_LANG})"
