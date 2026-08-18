#!/usr/bin/env bash
# Selbsttest für den Fastfile-Suffix-Guard (fastlane/stage_suffix.rb).
#
# Beweist zweierlei:
#   1) Die pure Funktion stage_suffix_ok? lehnt einen expliziten version:-
#      Parameter OHNE Stufen-Suffix ab (der v0.5.1-Vorfall vom 18.08.2026 —
#      ein stabiler Tag statt v0.5.1-beta) und akzeptiert nur gültige
#      <stage>-Tags. Läuft offline, kein fastlane nötig.
#   2) BEIDE Release-Lanes (release_alpha/release_beta) rufen den Guard mit
#      dem korrekten Stufen-Suffix auf (strukturelle Prüfung per awk).
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "❌ FAIL: $1"
  exit 1
}

stage_suffix_rb="$(cd fastlane && pwd)/stage_suffix.rb"
[[ -f "$stage_suffix_rb" ]] || fail "fastlane/stage_suffix.rb fehlt"

# 1) Pure Funktion: erwarteter boolescher Wert je Fall.
check_case() {
  local desc="$1" tag="$2" stage="$3" expected="$4"
  local actual
  actual="$(ruby -e 'require ARGV[0]; puts stage_suffix_ok?(ARGV[1], ARGV[2])' \
    "$stage_suffix_rb" "$tag" "$stage")"
  if [[ "$actual" != "$expected" ]]; then
    fail "$desc (stage=$stage, tag=$tag): erwartet $expected, war $actual"
  fi
}

# Positive Fälle — gültige <stage>-Tags (mit und ohne v-Präfix, 2- oder 3-stellig).
check_case "gültiger Beta-Tag"           "v0.5.1-beta"    "beta"  "true"
check_case "gültiger Beta-Tag ohne v"    "0.5.1-beta"     "beta"  "true"
check_case "gültiger Beta-Tag 2-stellig" "v0.5-beta"      "beta"  "true"
check_case "gültiger Alpha-Tag"          "v0.5.1-alpha"   "alpha" "true"
check_case "gültiger Alpha-Tag ohne v"   "0.5.1-alpha"    "alpha" "true"

# Negative Fälle — der v0.5.1-Vorfall und Abweichungen.
check_case "STABILER Tag (der Vorfall)"  "v0.5.1"         "beta"  "false"
check_case "STABILER Tag ohne v"         "0.5.1"          "beta"  "false"
check_case "falsche Stufe (alpha bei beta)" "v0.5.1-alpha" "beta" "false"
check_case "falsche Stufe (beta bei alpha)" "v0.5.1-beta"  "alpha" "false"
check_case "rc bei beta"                 "v0.5.1-rc"      "beta"  "false"
check_case "STABILER Tag bei alpha"      "v0.5.1"         "alpha" "false"
check_case "nightly-Tag (kein Release)"  "nightly-20260818-095427" "beta" "false"
check_case "Müll"                        "vbeta"          "beta"  "false"

# 2) Strukturell: beide Lanes müssen den Guard mit dem korrekten Suffix aufrufen.
for lane in release_alpha release_beta; do
  awk "/lane :$lane/,/^  end/" fastlane/Fastfile | grep -q "stage_suffix_ok?" \
    || fail "$lane ruft den Suffix-Guard nicht auf"
done
awk '/lane :release_alpha/,/^  end/' fastlane/Fastfile | grep -q 'stage_suffix_ok?(tag, "alpha")' \
  || fail "release_alpha prüft nicht auf '-alpha'"
awk '/lane :release_beta/,/^  end/' fastlane/Fastfile | grep -q 'stage_suffix_ok?(tag, "beta")' \
  || fail "release_beta prüft nicht auf '-beta'"

# 3) Syntax beider Ruby-Dateien (require_relative wird nicht ausgeführt).
ruby -c fastlane/Fastfile >/dev/null || fail "fastlane/Fastfile ist syntaktisch kaputt"
ruby -c "$stage_suffix_rb" >/dev/null || fail "fastlane/stage_suffix.rb ist syntaktisch kaputt"

echo "✅ Fastfile-Suffix-Guard: 13 Funktionsfälle grün, beide Lanes rufen den Guard, Syntax OK."
