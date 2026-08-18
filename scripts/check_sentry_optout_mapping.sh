#!/usr/bin/env bash
# Sentry-Opt-out-Mapping-Check (Vivid):
# Weist per R8-Mapping-Datei nach, dass die beforeSend-Opt-out-Logik
# (app/src/main/java/com/vivid/irlbroadcaster/SentryOptOut.kt)
# im Release-Build tatsächlich enthalten ist — R8 hat die einzige
# Callback-Implementierung in io.sentry.SentryClient eingebettet.
#
# Geprüft werden BEIDE Release-Kanäle:
#   - release     (APK,  assembleRelease)
#   - playRelease (AAB,  bundlePlayRelease, Upload-Key-Kanal für Play)
#
# Nachgewiesen wird pro Mapping (C1–C6):
#   C1  Mapping-Datei existiert (Release-Build wurde gebaut)
#   C2  Mapping ist frischer als die Opt-out-Quellen (kein veralteter Stand)
#   C3  Fabrik `sentryBeforeSendCallback(...)` wurde inlined (Inline-Record)
#   C4  Lambda `sentryBeforeSendCallback$lambda$0` existiert als Inline-Record
#   C5  Die Lambda-Inline-Records liegen innerhalb der Klasse `io.sentry.SentryClient`
#       (d. h. die Logik ist im SDK-Aufruf-Pfad verankert, nicht weggeschnitten)
#   C6  Die Fassadenklasse SentryOptOutKt ist entfernt (R8$$REMOVED$$CLASS$$) —
#       Beweis, dass vollständig inlined (nicht nur umbenannt) wurde
#
# Aufruf:
#   bash scripts/check_sentry_optout_mapping.sh                  # release + playRelease (Default)
#   bash scripts/check_sentry_optout_mapping.sh <pfad> [<pfad>…] # nur die genannten Mappings
# Exit-Code 0 = Opt-out-Logik in ALLEN geprüften Mappings nachgewiesen; 1 = mind. ein Nachweis fehlgeschlagen.
set -euo pipefail

cd "$(dirname "$0")/.."

SRC_OPTOUT="app/src/main/java/com/vivid/irlbroadcaster/SentryOptOut.kt"
SRC_APP="app/src/main/java/com/vivid/irlbroadcaster/VividApplication.kt"

# Prüft ein einzelnes Mapping (C1–C6). Exit 0 = Nachweis ok, 1 = fehlgeschlagen.
check_one() {
  local MAPPING="$1"
  local rc=0

  # C1: Mapping vorhanden?
  [[ -f "$MAPPING" ]] || {
    echo "❌ [mapping] ($MAPPING) Kein Mapping — zuerst Release-Build ausführen (PRE_PUSH_RELEASE=1 git push bzw. ./gradlew assembleRelease / bundlePlayRelease)."
    return 1
  }

  # C2: Mapping nicht älter als die Opt-out-Quellen?
  if [[ -f "$SRC_OPTOUT" && "$MAPPING" -ot "$SRC_OPTOUT" ]] || \
     [[ -f "$SRC_APP" && "$MAPPING" -ot "$SRC_APP" ]]; then
    echo "❌ [mapping] ($MAPPING) Mapping ist älter als die Opt-out-Quellen ($SRC_OPTOUT / $SRC_APP) — veraltet, Release-Build erneut ausführen (PRE_PUSH_RELEASE=1)."
    return 1
  fi

  # C3: Fabrik wurde inlined (Inline-Record im Mapping).
  grep -q "SentryOptOutKt.sentryBeforeSendCallback(kotlin.jvm.functions.Function0)" "$MAPPING" || {
    echo "❌ [mapping] ($MAPPING) Fabrik sentryBeforeSendCallback fehlt — Opt-out-Code wurde nicht inlined oder ist nicht enthalten."
    return 1
  }

  # C4: Lambda-Inline-Record vorhanden.
  local LAMBDA_PATTERN="SentryOptOutKt.sentryBeforeSendCallback\\\$lambda\\\$0"
  grep -q "$LAMBDA_PATTERN" "$MAPPING" || {
    echo "❌ [mapping] ($MAPPING) Lambda sentryBeforeSendCallback\$lambda\$0 fehlt — Opt-out-Logik nicht im Release-Build."
    return 1
  }

  # C5: Der/die Lambda-Records liegen innerhalb der Klasse io.sentry.SentryClient.
  local LAMBDA_LINE ENCLOSER
  LAMBDA_LINE="$(grep -n "$LAMBDA_PATTERN" "$MAPPING" | head -1 | cut -d: -f1)"
  ENCLOSER="$(awk -v t="$LAMBDA_LINE" 'NR<=t && /^[a-z].* -> /{last=$0} END{print last}' "$MAPPING")"
  grep -q "^io.sentry.SentryClient -> " <<< "$ENCLOSER" || {
    echo "❌ [mapping] ($MAPPING) Opt-out-Lambda ist nicht in io.sentry.SentryClient inlined (Kontext: ${ENCLOSER:-unbekannt}) — Logik evtl. weggeschnitten."
    return 1
  }

  # C6: Fassadenklasse vollständig entfernt (Beweis der Inline-Optimierung).
  grep -q "SentryOptOutKt -> R8\\\$\\\$REMOVED\\\$\\\$CLASS" "$MAPPING" || {
    echo "❌ [mapping] ($MAPPING) SentryOptOutKt-Fassade nicht als REMOVED markiert — R8 hat nicht vollständig inlined."
    return 1
  }

  echo "✅ [mapping] ($MAPPING) Sentry-Opt-out-Logik nachgewiesen (Lambda inlined in io.sentry.SentryClient)."
  return 0
}

# Default: BEIDE Release-Kanäle. Explizite Pfade: nur die genannten.
if [[ $# -ge 1 ]]; then
  MAPPINGS=("$@")
else
  MAPPINGS=(
    "app/build/outputs/mapping/release/mapping.txt"       # release — APK
    "app/build/outputs/mapping/playRelease/mapping.txt"   # playRelease — AAB
  )
fi

RC=0
for M in "${MAPPINGS[@]}"; do
  check_one "$M" || RC=1
done

if [[ "$RC" == "0" ]]; then
  echo "✅ [mapping] Alle ${#MAPPINGS[@]} Release-Kanäle: Opt-out-Logik nachgewiesen."
else
  echo "❌ [mapping] Mindestens ein Release-Kanal ohne Nachweis — siehe oben."
fi
exit "$RC"
