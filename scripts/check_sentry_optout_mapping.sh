#!/usr/bin/env bash
# Sentry-Opt-out-Mapping-Check (Vivid):
# Weist per R8-Mapping-Datei nach, dass die beforeSend-Opt-out-Logik
# (app/src/main/java/com/vivid/irlbroadcaster/SentryOptOut.kt)
# im Release-Build tatsächlich enthalten ist — R8 hat die einzige
# Callback-Implementierung in io.sentry.SentryClient eingebettet.
#
# Geprüft werden BEIDE Release-Kanäle:
#   - release     (APK,  assembleRelease)
#   - standardPlayRelease (AAB,  bundleStandardPlayRelease, Upload-Key-Kanal für Play)
#
# Nachgewiesen wird pro Mapping (C1–C6); C0 laeuft quellenseitig, ohne Mapping:
#   C0  R8-Vorbedingung: VividApplication haengt den beforeSend ueber
#       sentryBeforeSendWithTransportFilter ein, und diese Fabrik ruft die
#       nachgewiesene Opt-out-Fabrik sentryBeforeSendCallback auf (nicht
#       applySentryOptOut direkt). Nur so bleibt die Fabrik im Release-Build
#       referenziert — sonst entfernt R8 sie und C3–C6 schlagen fehl, aber erst
#       beim naechsten Release-Build, also lange nach der Ursache.
#   C1  Mapping-Datei existiert (Release-Build wurde gebaut)
#   C2  Mapping ist frischer als die Opt-out-Quellen (kein veralteter Stand)
#   C3  Fabrik `sentryBeforeSendCallback(...)` wurde inlined (Inline-Record)
#   C4  Lambda `sentryBeforeSendCallback$lambda$0` existiert als Inline-Record
#   C5  Der Lambda-Record teilt seinen Call-Site-Range mit einem Record, der
#       die BeforeSendCallback-Aufrufsignatur `(SentryEvent, Hint)` trägt — die
#       Lambda-Logik ist in den Sentry-Aufrufpfad inlined (nicht weggeschnitten).
#       WICHTIG: An R8 wählt den Partner-Record, nicht an einem Klassennamen —
#       je nach Inlining-Lage ist der Aufrufer `io.sentry.SentryClient` selbst
#       oder eine von R8 erzeugte synthetische Lambda-Brücke.
#   C6  Die Fassadenklasse SentryOptOutKt ist entfernt (R8$$REMOVED$$CLASS$$) —
#       Beweis, dass vollständig inlined (nicht nur umbenannt) wurde
#
# Aufruf:
#   bash scripts/check_sentry_optout_mapping.sh                  # Default: release + playRelease
#   bash scripts/check_sentry_optout_mapping.sh <pfad> [<pfad>…] # nur die genannten Mappings
#   bash scripts/check_sentry_optout_mapping.sh --strict         # fehlendes Mapping = harter Fehler
#   bash scripts/check_sentry_optout_mapping.sh --composition-only  # nur C0, ohne Mapping, hart
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

# Quellpfade per Env ueberschreibbar (SRC_APP/SRC_FILTER/SRC_OPTOUT_OVERRIDE),
# damit der Selbsttest C0 offline gegen Sandbox-Dateien fahren kann — sonst prueft
# er immer den echten Arbeitsbaum und koennte nie rot werden.
SRC_OPTOUT="${SRC_OPTOUT_OVERRIDE:-app/src/main/java/com/vivid/irlbroadcaster/SentryOptOut.kt}"
SRC_APP="${SRC_APP_OVERRIDE:-app/src/main/java/com/vivid/irlbroadcaster/VividApplication.kt}"
SRC_FILTER="${SRC_FILTER_OVERRIDE:-app/src/main/java/com/vivid/irlbroadcaster/SentryTransportFilter.kt}"

# C0: R8-Vorbedingung der Inline-Nachweise. Laeuft immer, auch ohne Mapping —
# genau das ist der Zweck: der Bruch soll im normalen Gate auffallen und nicht
# erst beim Release-Build. Kein Zeilenfenster, nur reine Existenzpruefungen.
check_composition() {
  local rc=0
  if [[ ! -f "$SRC_FILTER" ]]; then
    echo "❌ [composition] $SRC_FILTER fehlt — die beforeSend-Kette ist nicht nachvollziehbar."
    return 1
  fi
  if ! grep -q 'sentryBeforeSendWithTransportFilter' "$SRC_APP"; then
    echo "❌ [composition] $SRC_APP ruft sentryBeforeSendWithTransportFilter nicht auf — die Transport-Filterung haengt nicht im beforeSend-Pfad."
    rc=1
  fi
  if grep -q 'options\.beforeSend[[:space:]]*=[[:space:]]*sentryBeforeSendCallback' "$SRC_APP"; then
    echo "❌ [composition] $SRC_APP setzt beforeSend direkt auf sentryBeforeSendCallback — der Transport-Filter waere unwirksam. Kette ueber sentryBeforeSendWithTransportFilter bauen."
    rc=1
  fi
  if ! grep -q 'sentryBeforeSendCallback(' "$SRC_FILTER"; then
    echo "❌ [composition] $SRC_FILTER ruft die nachgewiesene Fabrik sentryBeforeSendCallback nicht auf — R8 entfernt sie samt C3–C6-Nachweis. Statt applySentryOptOut direkt zu nutzen: komponieren."
    rc=1
  fi
  if [[ "$rc" == "0" ]]; then
    echo "✅ [composition] beforeSend haengt an der Transport-Filterkette, die die Opt-out-Fabrik aufruft (R8-Vorbedingung erfuellt)."
  fi
  return "$rc"
}

# Prüft ein einzelnes Mapping (C1–C6). Exit 0 = Nachweis ok, 1 = fehlgeschlagen.
check_one() {
  local MAPPING="$1"
  local rc=0

  # C1: Mapping vorhanden? — unterscheidet „Kanal nicht gebaut“ (weich, Default)
  # von „Nachweis fehlgeschlagen“ (hart). Streng nur mit --strict/expliziten Pfaden.
  if [[ ! -f "$MAPPING" ]]; then
    if [[ "$STRICT" == "1" ]]; then
      echo "❌ [mapping] ($MAPPING) Kanal NICHT GEBAUT — kein Mapping vorhanden (Release-Build ausführen: ./gradlew assembleRelease bundleStandardPlayRelease bzw. :app:minifyReleaseWithR8 :app:minifyPlayReleaseWithR8)."
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

  # C5: Der Lambda-Record teilt den Call-Site-Range mit einem Record, der die
  # BeforeSendCallback-Aufrufsignatur (SentryEvent, Hint) traegt — also dem Aufruf
  # selbst. Bewusst NICHT an einem Klassennamen festgemacht: R8 waehlt den
  # Partner-Record je nach Inlining-Lage selbst. Beim Aufruf direkt aus der
  # SentryClient-Methode (#267-Stand davor) war das
  #   io.sentry.SentryClient.executeBeforeSendFeedback(SentryEvent, BeforeSendCallback)
  # eine Ebene tiefer — das Lambda der neuen Transport-Filter-Fabrik — dagegen als
  #   ActivityResultRegistryKt$$ExternalSyntheticLambda0.execute(SentryEvent, Hint)
  # Beides ist derselbe Callback-Aufruf; die Signatur legt das SDK fest, nicht R8,
  # und ist damit der stabile Nachweis (gegenueber dem Klassennamen, der mit der
  # Obfuskation wandert).
  local LAMBDA_RECORD LAMBDA_RANGE
  LAMBDA_RECORD="$(grep -m1 "$LAMBDA_PATTERN" "$MAPPING")"
  # Format des Inline-Records: "    270:283:io.sentry.SentryEvent com.vivid…$lambda$0(…):32:32 -> j"
  LAMBDA_RANGE="$(printf '%s' "$LAMBDA_RECORD" | sed -E 's/^ *([0-9]+:[0-9]+):.*/\1/')"
  if [[ -z "$LAMBDA_RANGE" ]] || ! grep -qE "^ *${LAMBDA_RANGE}:[^ ]* +.*\\(io\\.sentry\\.SentryEvent,io\\.sentry\\.Hint\\)" "$MAPPING"; then
    echo "❌ [mapping] ($MAPPING) Opt-out-Lambda-Record (Range ${LAMBDA_RANGE:-unbekannt}) teilt seinen Inline-Range mit keinem Aufruf der Signatur (SentryEvent, Hint) — die Callback-Logik ist evtl. nicht in den Sentry-Aufrufpfad inlined."
    return 1
  fi

  # C6: Fassadenklasse vollständig entfernt (Beweis der Inline-Optimierung).
  grep -q "SentryOptOutKt -> R8\\\$\\\$REMOVED\\\$\\\$CLASS" "$MAPPING" || {
    echo "❌ [mapping] ($MAPPING) SentryOptOutKt-Fassade nicht als REMOVED markiert — R8 hat nicht vollständig inlined."
    return 1
  }

    echo "✅ [mapping] ($MAPPING) Sentry-Opt-out-Logik nachgewiesen (Lambda-Inline-Record teilt seinen Call-Site-Range mit einem Callback-Aufruf der Signatur (SentryEvent, Hint))."
  return 0
}

# Modus: Default (weich) vs. streng (--strict bzw. explizite Pfade) vs.
# --composition-only. Der dritte Modus prueft NUR C0: eine Quellcode-Invariante,
# die kein Mapping braucht und darum auch ohne Release-Build hart scheitern muss —
# C2–C6 duerfen ohne Release-Build weich bleiben, C0 nicht.
STRICT=0
COMPOSITION_ONLY=0
MISSING_COUNT=0
if [[ $# -ge 1 ]]; then
  MAPPINGS=()
  for arg in "$@"; do
    case "$arg" in
      --strict) STRICT=1 ;;
      --composition-only) COMPOSITION_ONLY=1 ;;
      *) MAPPINGS+=("$arg") ;;
    esac
  done
  # Explizit genannte Kanäle: Der Aufrufer fordert sie an — fehlend = hart.
  [[ "$COMPOSITION_ONLY" == "0" ]] && STRICT=1
else
  MAPPINGS=()
fi

if [[ "$COMPOSITION_ONLY" == "1" ]]; then
  check_composition
  exit $?
fi
if [[ ${#MAPPINGS[@]} -eq 0 ]]; then
  # Default: BEIDE Release-Kanäle (Pfade per Env überschreibbar für Tests).
  MAPPINGS=(
    "${MAPPING_RELEASE:-app/build/outputs/mapping/standardRelease/mapping.txt}"   # standardRelease — APK (standard-Flavor, seit Flavors standard/foss)
    "${MAPPING_PLAYRELEASE:-app/build/outputs/mapping/standardPlayRelease/mapping.txt}"   # standardPlayRelease — AAB
  )
fi

RC=0
check_composition || RC=1
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
