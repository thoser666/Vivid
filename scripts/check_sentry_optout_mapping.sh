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
#   C5  Der Lambda-Record teilt seinen Call-Site-Range mit einem
#       `io.sentry.SentryClient`-Methoden-Record — die Lambda-Logik ist in den
#       Sentry-Aufrufpfad inlined (nicht weggeschnitten). WICHTIG: R8 attribuiert
#       die Inline-Records nicht garantiert unter den Klassenblock von
#       `io.sentry.SentryClient` (seit dem ProfanityFilter-Commit hängen sie ggf.
#       unter einer anderen Klasse) — der Range-Bezug ist der robuste Nachweis.
#   C6  Die Fassadenklasse SentryOptOutKt ist entfernt (R8$$REMOVED$$CLASS$$) —
#       Beweis, dass vollständig inlined (nicht nur umbenannt) wurde
#
# Aufruf:
#   bash scripts/check_sentry_optout_mapping.sh                  # Default: release + playRelease
#   bash scripts/check_sentry_optout_mapping.sh <pfad> [<pfad>…] # nur die genannten Mappings
#   bash scripts/check_sentry_optout_mapping.sh --strict         # fehlendes Mapping = harter Fehler
#
# Unterscheidung „Kanal nicht gebaut“ vs. „Nachweis fehlgeschlagen“:
#   - Default (ohne Argumente): Ein fehlendes Mapping (Kanal nie gebaut) ist KEIN
#     harter Fehler — die vorhandenen Kanäle werden geprüft, der fehlende wird mit
#     „⚠️ nicht gebaut“ übersprungen (Exit 0, solange alle vorhandenen grün sind).
#   - Streng (--strict oder explizite Pfad-Argumente): Der Aufrufer fordert den
#     Kanal explizit an — fehlendes Mapping ist ein harter Fehler (Exit 1).
#   - „Nachweis fehlgeschlagen“ (Mapping vorhanden, aber C2–C6 scheitern) ist
#     IMMER ein harter Fehler — unabhängig vom Modus.
# Die Default-Pfade sind per Env überschreibbar (MAPPING_RELEASE/MAPPING_PLAYRELEASE),
# damit die Default-Semantik offline gegen Sandbox-Dateien getestet werden kann.
# Exit-Code 0 = alle vorhandenen Mappings nachgewiesen (fehlende übersprungen);
#             1 = mind. ein Nachweis fehlgeschlagen bzw. (streng) Kanal nicht gebaut.
set -euo pipefail

cd "$(dirname "$0")/.."

SRC_OPTOUT="app/src/main/java/com/vivid/irlbroadcaster/SentryOptOut.kt"
SRC_APP="app/src/main/java/com/vivid/irlbroadcaster/VividApplication.kt"

# Prüft ein einzelnes Mapping (C1–C6). Exit 0 = Nachweis ok, 1 = fehlgeschlagen.
check_one() {
  local MAPPING="$1"
  local rc=0

  # C1: Mapping vorhanden? — unterscheidet „Kanal nicht gebaut“ (weich, Default)
  # von „Nachweis fehlgeschlagen“ (hart). Streng nur mit --strict/expliziten Pfaden.
  if [[ ! -f "$MAPPING" ]]; then
    if [[ "$STRICT" == "1" ]]; then
      echo "❌ [mapping] ($MAPPING) Kanal NICHT GEBAUT — kein Mapping vorhanden (Release-Build ausführen: ./gradlew assembleRelease bundlePlayRelease bzw. :app:minifyReleaseWithR8 :app:minifyPlayReleaseWithR8)."
      return 1
    fi
    echo "⚠️ [mapping] ($MAPPING) Kanal nicht gebaut — Nachweis übersprungen (Release-Build ist optional; vollständiger Nachweis: PRE_PUSH_RELEASE=1 git push)."
    MISSING_COUNT=$((MISSING_COUNT + 1))
    return 0
  fi

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

  # C5: Der Lambda-Record teilt den Call-Site-Range mit einem
  # io.sentry.SentryClient-Record. (Nicht: „nächste Klassen-Zeile davor“ —
  # R8 attribuiert die Inline-Records seit dem ProfanityFilter-Commit nicht
  # mehr garantiert unter den Klassenblock von io.sentry.SentryClient.)
  local LAMBDA_RECORD LAMBDA_RANGE
  LAMBDA_RECORD="$(grep -m1 "$LAMBDA_PATTERN" "$MAPPING")"
  # Format des Inline-Records: "    270:283:io.sentry.SentryEvent com.vivid…$lambda$0(…):32:32 -> j"
  LAMBDA_RANGE="$(printf '%s' "$LAMBDA_RECORD" | sed -E 's/^ *([0-9]+:[0-9]+):.*/\1/')"
  if [[ -z "$LAMBDA_RANGE" ]] || ! grep -qE "^ *${LAMBDA_RANGE}:[^ ]* io\\.sentry\\.SentryClient\\.[A-Za-z0-9_]+\(" "$MAPPING"; then
    echo "❌ [mapping] ($MAPPING) Opt-out-Lambda-Record (Range ${LAMBDA_RANGE:-unbekannt}) hat keinen io.sentry.SentryClient-Record im selben Inline-Range — Logik evtl. nicht in den Sentry-Aufrufpfad inlined."
    return 1
  fi

  # C6: Fassadenklasse vollständig entfernt (Beweis der Inline-Optimierung).
  grep -q "SentryOptOutKt -> R8\\\$\\\$REMOVED\\\$\\\$CLASS" "$MAPPING" || {
    echo "❌ [mapping] ($MAPPING) SentryOptOutKt-Fassade nicht als REMOVED markiert — R8 hat nicht vollständig inlined."
    return 1
  }

  echo "✅ [mapping] ($MAPPING) Sentry-Opt-out-Logik nachgewiesen (Lambda-Inline-Record im SentryClient-Aufrufpfad)."
  return 0
}

# Modus: Default (weich) vs. streng (--strict bzw. explizite Pfade).
STRICT=0
MISSING_COUNT=0
if [[ $# -ge 1 ]]; then
  MAPPINGS=()
  for arg in "$@"; do
    if [[ "$arg" == "--strict" ]]; then
      STRICT=1
    else
      MAPPINGS+=("$arg")
    fi
  done
  # Explizit genannte Kanäle: Der Aufrufer fordert sie an — fehlend = hart.
  STRICT=1
else
  MAPPINGS=()
fi
if [[ ${#MAPPINGS[@]} -eq 0 ]]; then
  # Default: BEIDE Release-Kanäle (Pfade per Env überschreibbar für Tests).
  MAPPINGS=(
    "${MAPPING_RELEASE:-app/build/outputs/mapping/release/mapping.txt}"       # release — APK
    "${MAPPING_PLAYRELEASE:-app/build/outputs/mapping/playRelease/mapping.txt}"   # playRelease — AAB
  )
fi

RC=0
for M in "${MAPPINGS[@]}"; do
  check_one "$M" || RC=1
done

if [[ "$RC" == "0" ]]; then
  if [[ "$MISSING_COUNT" -gt 0 ]]; then
    echo "✅ [mapping] Alle $(( ${#MAPPINGS[@]} - MISSING_COUNT )) gebauten Kanäle: Opt-out-Logik nachgewiesen; $MISSING_COUNT Kanal/Kanäle nicht gebaut (übersprungen)."
  else
    echo "✅ [mapping] Alle ${#MAPPINGS[@]} Release-Kanäle: Opt-out-Logik nachgewiesen."
  fi
else
  echo "❌ [mapping] Mindestens ein Kanal ohne Nachweis — siehe oben."
fi
exit "$RC"
