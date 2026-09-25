#!/usr/bin/env bash
# Retry-Härtung für den Emulator-Gate-Step im Stable-Publish
# (distribution-stable.yml → "Run instrumented tests on emulator (release gate)").
#
# Motivation (24.09.2026, Release v0.5.19-beta, Run 36025777687): Der Stable-
# Publish scheiterte im Gate an :app:connectedStandardDebugAndroidTest
# ("There were failing tests") — bei EXAKT demselben Commit, dessen Suite
# 40 Minuten vorher im Tag-Push-Run (36021918352) auf derselben ubuntu-x86_64-
# Leg GRÜN war. Emulator-auf-Hosted-Runner ist eine inhärent umweltabhängige
# Stufe (geteilter Host, ColorBuffer-/adb-Instabilität); ein einmaliger roter
# Gate-Lauf darf ein Release nicht ohne Zweitprüfung stoppen.
#
# Evidenz-basierte Klassifikation (Run-Vergleich grün 36021918352 vs. rot
# 36025777687): "Unable to connect to adb daemon", "Failed to load snapshot
# 'default_boot'" und ColorBuffer-Warnungen stehen AUCH in grünen Läufen —
# normale Boot-Aufwärmphase. Sie sind BEWUSST KEINE Retry-Muster. Retry-würdig
# ist nur die Fehlerklasse am Log-Ende: Suite meldet Fehlschlag oder das
# Gerät verliert sich MID-TEST. Deterministische Fehler (Kompilierung der
# Test-Quellen) haben VORRANG und brechen sofort ab — kein Retry, keine
# verbrannten CI-Minuten.
#
# bewusst ENG gefasst wie fastlane/build_retry.rb: Alles Unklassifizierte
# scheitert sofort (fail-closed). Ein deterministisch kaputtes Commit
# scheitert auch nach 3 Versuchen → Run bleibt rot, NICHTS wird maskiert.
#
# Vertrag (Selbsttest: scripts/test_emulator_gate_retry.sh):
#   Exit 0  → Gate bestanden (erster oder späterer Versuch)
#   Exit rc → endgültig fehlgeschlagen (deterministisch, unklassifiziert
#             oder transient nach MAX_ATTEMPTS); rc = Exit-Code des Befehls
set -uo pipefail

MAX_ATTEMPTS=${MAX_ATTEMPTS:-3}
BASE_DELAY_SECONDS=${BASE_DELAY_SECONDS:-10} # linearer Backoff: 10 s / 20 s (wie build_retry.rb)
# Test-Hook: Selbsttest ersetzt sleep durch einen Recorder (kein echtes Warten in Tests).
SLEEP_CMD=${SLEEP_CMD:-sleep}

# <<PATTERNS-BEGIN>> (Selbsttest extrahiert die Muster zwischen diesen Markern)
# ── Deterministische Fehler (VORRANG vor Transient — sofortiges Scheitern) ──
DETERMINISTIC_PATTERNS=(
  'Compilation error'
  'unresolved reference'           # Kotlin-Compiler schreibt kleingeschrieben (e: … unresolved reference: foo)
  'error: cannot find symbol'
  'e: .*\.kt:[0-9]+'               # Kotlin-Compiler-Fehlerzeilen (e: file:///…/Foo.kt:12:1 …)
)

# ── Transiente Fehlerklassen (nur diese rechtfertigen einen Retry) ──────────
TRANSIENT_PATTERNS=(
  'There were failing tests'                                   # Instrumentierung meldet Fehlschlag (Vorfall 24.09.2026, Run 36025777687)
  ':app:connected[[:alnum:]]*DebugAndroidTest FAILED'          # Task-Signatur (standard- oder foss-Flavor)
  'device.*offline|device.*not found'                          # Emulator/Gerät mid-run verloren
  'adb: failed to get'                                         # adb-Abfrage am Gerät fehlgeschlagen (Geräteverlust-Klasse)
  'Failed to boot|Boot failed|emulator: ERROR|failed to initialize (HVF|KVM)|Emulator.*(crashed|timed out)'  # Boot-Klasse (Vorfall 06.09.2026, HVF_UNSUPPORTED)
  'Could not resolve all files|Could not download'             # Dependency-Auflösung (Netzwerk-Klasse, wie build_retry.rb)
)
# <<PATTERNS-END>>

classify() {
  # $1 = Pfad zur Output-Datei; gibt aus: ok | transient | deterministic | unclassified
  local file=$1 pat
  for pat in "${DETERMINISTIC_PATTERNS[@]}"; do
    if grep -qE "$pat" "$file"; then echo deterministic; return; fi
  done
  for pat in "${TRANSIENT_PATTERNS[@]}"; do
    if grep -qE "$pat" "$file"; then echo transient; return; fi
  done
  echo unclassified
}

# ── Modus 1: --classify <datei> — nur klassifizieren (Selbsttest/Diagnose) ──
if [ "${1:-}" = "--classify" ]; then
  [ -f "${2:-}" ] || { echo "::error::--classify: Datei fehlt" >&2; exit 64; }
  classify "$2"
  exit 0
fi

if [ $# -eq 0 ]; then
  echo "::error::emulator_gate_retry.sh: kein Befehl übergeben" >&2
  exit 64
fi

attempt=0
while :; do
  attempt=$((attempt + 1))
  out_file=$(mktemp)
  "$@" >"$out_file" 2>&1
  rc=$?
  cat "$out_file" # komplette Output-Transparenz — auch bei Retry wird nichts verschluckt
  if [ "$rc" -eq 0 ]; then
    if [ "$attempt" -gt 1 ]; then
      echo "::notice::Emulator-Gate bestanden im Versuch $attempt/$MAX_ATTEMPTS (vorherige Versuche: vermutlich transient)"
    fi
    rm -f "$out_file"
    exit 0
  fi
  verdict=$(classify "$out_file")
  rm -f "$out_file"
  if [ "$verdict" != transient ] || [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    if [ "$verdict" = transient ]; then
      echo "::error::Emulator-Gate endgültig fehlgeschlagen nach $attempt Versuchen (transienter Fehler blieb bestehen)" >&2
    else
      echo "::error::Emulator-Gate fehlgeschlagen (Klasse: $verdict — kein Retry, fail-closed)" >&2
    fi
    exit "$rc"
  fi
  wait=$((BASE_DELAY_SECONDS * attempt))
  echo "::warning::Emulator-Gate fehlgeschlagen (Versuch $attempt/$MAX_ATTEMPTS, vermutlich transient) — retry in ${wait}s"
  "$SLEEP_CMD" "$wait"
done
