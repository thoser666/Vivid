#!/usr/bin/env bash
# Selbsttest für die geteilten Release-Safety-Funktionen (fastlane/release_safety.rb).
#
# Beweist:
#   1) version_code_for — deterministischer versionCode (5012 für v0.5.1-beta etc.)
#   2) highest_stage_code — Downgrade-Guard innerhalb eines Tracks (z. B. beta)
#   3) highest_other_code — Quer-Track-Vergleich (Warnung, wenn ein anderer Track
#      einen höheren versionCode hat)
#   4) Strukturell: BEIDE Lanes (release_alpha + release_beta) nutzen dieselben
#      Funktionen — damit die Logik nie wieder auseinanderläuft.
#
# Wichtig (Windows/MSYS): Argumente mit Single-Quotes werden beim Spawn von
# ruby.exe verschluckt — die Werte werden deshalb als reine ARGV-Argumente
# übergeben (Tag-Listen leerzeichengetrennt), nicht per eval-String.
# Läuft offline, kein fastlane nötig.
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "❌ FAIL: $1"
  exit 1
}

release_safety_rb="$(cd fastlane && pwd)/release_safety.rb"
[[ -f "$release_safety_rb" ]] || fail "fastlane/release_safety.rb fehlt"

# check <desc> <erwartet> <funktion> <args…> — ruft die Funktion mit ARGV auf.
check() {
  local desc="$1" expected="$2" fn="$3"
  shift 3
  local actual
  actual="$(ruby -e 'require ARGV[0]; fn = ARGV[1]; v = case fn
                     when "version_code_for" then version_code_for(ARGV[2])
                     when "highest_stage_code" then highest_stage_code(ARGV[2].split, ARGV[3])
                     when "highest_other_code" then highest_other_code(ARGV[2].split, ARGV[3])
                     end; puts v.nil? ? "nil" : v.inspect' \
    "$release_safety_rb" "$fn" "$@")"
  if [[ "$actual" != "$expected" ]]; then
    fail "$desc: erwartet $expected, war $actual"
  fi
}

# version_code_for — Schema major*1e6 + minor*1e3 + patch*10 + Stufe.
check "versionCode v0.5.1-beta"  "5012" version_code_for "0.5.1-beta"
check "versionCode v0.5.0-beta"  "5002" version_code_for "0.5.0-beta"
check "versionCode v0.5.1-alpha" "5011" version_code_for "0.5.1-alpha"
check "versionCode v0.6.0-alpha" "6001" version_code_for "0.6.0-alpha"
check "versionCode v0.5.1 (stable)" "5014" version_code_for "0.5.1"
check "versionCode v0.5-beta (2-stellig)" "5002" version_code_for "0.5-beta"
check "versionCode Müll → nil"   "nil"  version_code_for "garbage"

# highest_stage_code — höchster versionCode einer Stufe (Downgrade-Guard).
check "höchster beta-Code"  "5012" highest_stage_code "v0.5.0-beta v0.6.0-alpha v0.5.1-beta v0.5.1" beta
check "höchster alpha-Code" "6001" highest_stage_code "v0.5.0-beta v0.6.0-alpha v0.5.1-beta" alpha
check "keine beta-Tags → nil" "nil" highest_stage_code "v0.5.1" beta

# highest_other_code — Quer-Track: höchster fremder versionCode, eigener ausgeschlossen.
check "Quer-Track max (ohne sich selbst)" "6001" highest_other_code "v0.6.0-alpha v0.5.1-beta v0.5.0-beta" v0.5.1-beta
check "nur eigener Tag → nil"             "nil"  highest_other_code "v0.5.1-beta" v0.5.1-beta

# 4) Strukturell: beide Lanes müssen dieselben Safety-Funktionen mit korrektem
#    Stufen-Parameter aufrufen (alpha-Track → "alpha", beta-Track → "beta").
for lane in release_alpha release_beta; do
  awk "/lane :$lane/,/^  end/" fastlane/Fastfile | grep -q "version_code_for(" \
    || fail "$lane ruft version_code_for nicht auf (Safety 2)"
  awk "/lane :$lane/,/^  end/" fastlane/Fastfile | grep -q "highest_stage_code(" \
    || fail "$lane ruft highest_stage_code nicht auf (Safety 3)"
  awk "/lane :$lane/,/^  end/" fastlane/Fastfile | grep -q "highest_other_code(" \
    || fail "$lane ruft highest_other_code nicht auf (Safety 4)"
done
awk '/lane :release_alpha/,/^  end/' fastlane/Fastfile | grep -q 'highest_stage_code(all_tags, "alpha")' \
  || fail "release_alpha prüft nicht auf die alpha-Track-Monotonie"
awk '/lane :release_beta/,/^  end/' fastlane/Fastfile | grep -q 'highest_stage_code(all_tags, "beta")' \
  || fail "release_beta prüft nicht auf die beta-Track-Monotonie"

# 5) Windows/cmd.exe-Falle: Backtick-git-Aufrufe mit Single-Quotes (z. B. 'v*')
#    würden lokal unter Windows nie matchen — nur Double-Quotes sind shell-
#    übergreifend sicher (cmd.exe UND bash). Struktureller Regressions-Guard.
if grep -n '`.*git' fastlane/Fastfile | grep -q "'"; then
  fail "Backtick-git-Aufrufe mit Single-Quotes gefunden (cmd.exe-Falle — Double-Quotes verwenden)"
fi

# 6) Syntax beider Ruby-Dateien.
ruby -c fastlane/Fastfile >/dev/null || fail "fastlane/Fastfile ist syntaktisch kaputt"
ruby -c "$release_safety_rb" >/dev/null || fail "fastlane/release_safety.rb ist syntaktisch kaputt"

echo "✅ Release-Safety: 12 Funktionsfälle grün, beide Lanes nutzen dieselben Funktionen, Syntax OK."
