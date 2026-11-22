#!/usr/bin/env bash
# Reproduzierbarkeits-Vergleich des Verify-Jobs (Nightly + Version-Tag).
#
# Aufruf:
#   bash scripts/compare_reproducibility.sh <published> <rebuild-a> <rebuild-b>
#
# Jedes Argument ist ein Verzeichnis mit denselben Release-Asset-Namen:
#   app-standard-release.apk, output-metadata.json, mapping.txt,
#   app-standard-playRelease.aab (nur wenn im Release publiziert)
# Die Rebuild-Verzeichnisse füllt scripts/stage_repro_artifacts.sh.
#
# Verglichen werden zwei Fragen:
#   1) published vs. build A — ist das VERÖFFENTLICHTE Artefakt der Build dieses
#      Commits? (das eigentliche Gate)
#   2) build A vs. build B — ist der BUILD selbst deterministisch? (zwei frische
#      Builds desselben Commits, derselbe Runner, dieselben Parameter, B mit
#      geleertem :app-Baum und ohne Build-Cache)
# Ohne (2) ist bei einer Abweichung nicht entscheidbar, ob der Build oder die
# Umgebung die Ursache ist. Nightly 18.11.2026: der angehängte
# Compose-Group-Mapping-Block war in der Reihenfolge permutiert (79 von 3.322
# Zeilen, Menge identisch), der R8-Teil byte-identisch — das sah ohne
# Gegenprobe wie ein Umgebungsproblem aus.
#
# mapping.txt wird zweistufig verglichen:
#   * R8-Teil (alles vor dem Blockkopf `ComposeStackTrace -> $$compose:`):
#     BIT-IDENTISCH. Dort steht der Obfuskationsplan; eine Abweichung ist nie
#     nur eine Reihenfolge.
#   * Compose-Anhang: kanonisch (beide Seiten `LC_ALL=C sort`). AGP hängt ihn
#     aus intermediates/compose_mapping/<variant>/compose-mapping.txt an, und
#     dessen Reihenfolge war nicht stabil. Der Build sortiert ihn seit dem Fix
#     selbst (app/build.gradle.kts), deshalb ist der Normalfall die
#     bit-identische Datei — weicht NUR die Reihenfolge ab, warnt der Job
#     sichtbar (::warning::), weil dann die Kanonisierung im Build nicht
#     gewirkt hat, statt den Lauf grundlos rot zu machen.
#
# Exit 0 = reproduzierbar, 1 = Abweichung, 2 = Aufruf-/Umgebungsfehler.
set -euo pipefail

# Blockkopf des Compose-Anhangs, den AGP an mapping.txt anhängt.
# Einfache Quotes: `$$` wäre in doppelten Quotes die PID der Shell.
COMPOSE_MARKER='-> $$compose:'

die() {
  echo "::error::$*"
  exit 2
}

[[ $# -eq 3 ]] || die "Aufruf: $0 <published-dir> <rebuild-a-dir> <rebuild-b-dir> (erhalten: $# Argument(e))"
PUBLISHED="$1"
BUILD_A="$2"
BUILD_B="$3"
for dir in "$PUBLISHED" "$BUILD_A" "$BUILD_B"; do
  [[ -d "$dir" ]] || die "Verzeichnis fehlt: $dir"
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ERRORS=0
err() { echo "::error::$*"; ERRORS=$((ERRORS + 1)); }
warn() { echo "::warning::$*"; }
ok() { echo "✅ $*"; }
sha256_of() { sha256sum "$1" | cut -d' ' -f1; }

require() {
  [[ -f "$1" ]] || die "Artefakt fehlt: $1 (Downloads bzw. Rebuild-Staging unvollständig)"
}

# Zeilennummer des ersten Compose-Anhang-Eintrags (1-basiert), 0 = kein Anhang.
appendix_start() {
  local file="$1" nr
  nr="$(grep -n -m1 -F -- "$COMPOSE_MARKER" "$file" | cut -d: -f1 || true)"
  printf '%s\n' "${nr:-0}"
}

compare_file() { # $1=Bezeichnung $2=Dateiname $3=Datei A $4=Datei B
  local label="$1" name="$2" file_a="$3" file_b="$4"
  if cmp -s "$file_a" "$file_b"; then
    ok "$name ($label): BIT-IDENTISCH"
    return 0
  fi
  err "$name unterscheidet sich ($label): $(sha256_of "$file_a") vs. $(sha256_of "$file_b")"
  return 1
}

compare_mapping() { # $1=Label A $2=Datei A $3=Label B $4=Datei B
  local label_a="$1" file_a="$2" label_b="$3" file_b="$4"
  local start_a start_b dir cmp_message
  if cmp -s "$file_a" "$file_b"; then
    ok "mapping.txt ($label_a vs. $label_b): BIT-IDENTISCH"
    return 0
  fi
  start_a="$(appendix_start "$file_a")"
  start_b="$(appendix_start "$file_b")"
  if [[ "$start_a" == "0" || "$start_b" == "0" ]]; then
    local has_a='ja' has_b='ja'
    [[ "$start_a" == "0" ]] && has_a='nein'
    [[ "$start_b" == "0" ]] && has_b='nein'
    err "mapping.txt ($label_a vs. $label_b): Compose-Anhang vorhanden? $label_a=$has_a, $label_b=$has_b — der Anhang ist Bestandteil des Mappings, ohne ihn ist die Datei nicht vergleichbar"
    return 1
  fi
  dir="$(mktemp -d "$WORK/mapping.XXXXXX")"
  # R8-Teil: Byte-Vergleich, kompromisslos.
  head -n "$((start_a - 1))" "$file_a" >"$dir/a.r8"
  head -n "$((start_b - 1))" "$file_b" >"$dir/b.r8"
  if ! cmp -s "$dir/a.r8" "$dir/b.r8"; then
    cmp_message="$(cmp "$dir/a.r8" "$dir/b.r8" 2>&1 || true)"
    err "mapping.txt ($label_a vs. $label_b): R8-Teil unterscheidet sich — $cmp_message"
    return 1
  fi
  # Compose-Anhang: kanonisch (sortierte Menge).
  tail -n "+$start_a" "$file_a" | LC_ALL=C sort >"$dir/a.appendix"
  tail -n "+$start_b" "$file_b" | LC_ALL=C sort >"$dir/b.appendix"
  if cmp -s "$dir/a.appendix" "$dir/b.appendix"; then
    warn "mapping.txt ($label_a vs. $label_b): R8-Teil identisch, Compose-Anhang nur in der REIHENFOLGE verschieden (als sortierte Menge byte-gleich). Die Kanonisierung in app/build.gradle.kts (doLast der merge*ComposeMapping-Task) hat also nicht gewirkt — als gleich bewertet, aber sie ist die Ursache solcher Abweichungen."
    return 0
  fi
  err "mapping.txt ($label_a vs. $label_b): Compose-Anhang unterscheidet sich auch als sortierte Menge"
  diff -u "$dir/a.appendix" "$dir/b.appendix" >"$dir/appendix.diff" 2>&1 || true
  head -n 20 "$dir/appendix.diff"
  return 1
}

compare_set() { # $1=Label A $2=Verz. A $3=Label B $4=Verz. B
  local label_a="$1" dir_a="$2" label_b="$3" dir_b="$4" rc=0 name
  local aab='app-standard-playRelease.aab' has_aab_a='nein' has_aab_b='nein'
  require "$dir_a/mapping.txt"
  require "$dir_b/mapping.txt"
  for name in app-standard-release.apk output-metadata.json; do
    require "$dir_a/$name"
    require "$dir_b/$name"
  done
  compare_mapping "$label_a" "$dir_a/mapping.txt" "$label_b" "$dir_b/mapping.txt" || rc=1
  for name in app-standard-release.apk output-metadata.json; do
    compare_file "$label_a vs. $label_b" "$name" "$dir_a/$name" "$dir_b/$name" || rc=1
  done
  [[ -f "$dir_a/$aab" ]] && has_aab_a='ja'
  [[ -f "$dir_b/$aab" ]] && has_aab_b='ja'
  if [[ "$has_aab_a" == "nein" && "$has_aab_b" == "nein" ]]; then
    echo "ℹ️  $aab in beiden Seiten nicht vorhanden — AAB-Vergleich übersprungen"
  elif [[ "$has_aab_a" != "$has_aab_b" ]]; then
    err "$aab nur auf einer Seite vorhanden ($label_a=$has_aab_a, $label_b=$has_aab_b) — Staging unvollständig"
    rc=1
  else
    compare_file "$label_a vs. $label_b" "$aab" "$dir_a/$aab" "$dir_b/$aab" || rc=1
  fi
  return "$rc"
}

echo "== 1) Veröffentlichtes Release gegen Build A =="
published_vs_a=0
compare_set published "$PUBLISHED" rebuild-a "$BUILD_A" || published_vs_a=1

echo
echo "== 2) Build A gegen Build B (Determinismus des Builds selbst) =="
a_vs_b=0
compare_set rebuild-a "$BUILD_A" rebuild-b "$BUILD_B" || a_vs_b=1

echo
if [[ "$a_vs_b" -ne 0 ]]; then
  err "Attribution: der BUILD ist NICHT deterministisch — zwei frische Builds desselben Commits (A und B, gleicher Runner, gleiche Parameter) unterscheiden sich. Ursache im Build suchen (Reihenfolge/Zufall/Zeitstempel), nicht beim Runner und nicht im Release."
elif [[ "$published_vs_a" -ne 0 ]]; then
  err "Attribution: der Build ist deterministisch (A == B), das VERÖFFENTLICHTE Artefakt weicht aber ab — das Release wurde aus einer abweichenden Umgebung/Toolchain gebaut (z. B. anderer Runner, andere Abhängigkeiten). Das Release neu erzeugen; im Build ist nichts zu reparieren."
fi

if [[ "$ERRORS" -gt 0 ]]; then
  echo "❌ $ERRORS Abweichung(en) — Reproduzierbarkeit verletzt"
  exit 1
fi
echo "🎉 Alle Artefakte reproduzierbar (published == build A == build B)"
