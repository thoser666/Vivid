#!/usr/bin/env bash
# Guard: gh-CLI-Flags in Workflows gegen die lokale gh-Version validieren.
#
# Hintergrund (Vorfall deploy-fdroid, 21.09.2026): Der Stable-Version-Step
# nutzte `gh release list --exclude-prereleases` — dieser Flag existiert
# nicht (korrekt: `--exclude-pre-releases`). Der Run scheiterte erst im Job
# (`unknown flag`, Exit 1), obwohl derselbe Fehler 30 Sekunden lokal zu
# finden gewesen wäre: `gh release list --exclude-prereleases` → usage-Fehler.
#
# Dieser Guard extrahiert aus allen Workflow-Dateien die `gh <cmd> [<sub>]`
# Aufrufe mit ihren `--flag`-Token und validiert jeden gegen die Hilfe der
# LOKAL installierten gh-CLI:
#   - Gruppe mit Subcommand (z. B. `gh release list`): Flags aus
#     `gh <cmd> <sub> --help`
#   - Leaf-Command (z. B. `gh api`): Flags aus `gh <cmd> --help`
# Ein unbekanntes Flag (Typo, aus einer anderen gh-Version kopiert) failt
# hart, BEVOR ein Run am Job scheitert. Bewusste Opt-outs pro Zeile:
#   … --sonderflag …  # gh-flag-exempt: --sonderflag
#
# Grenzen (bewusst): Nur Long-Flags (`--x`); Short-Flags (`-f`) werden nicht
# geprüft. Nur Zeilen mit auflösbarem gh-Aufruf. Die Zeilen-Parsing-Last
# liegt in EINEM awk-Pass je Datei (Subprocess-Spawns pro Zeile wären auf
# Windows-Git-Bash minutes-langsam).
#
# Exit 0 = alle Flags bekannt (oder sauber exempt),
# Exit 1 = mindestens ein unbekanntes Flag.
#
# Selbsttest: scripts/test_gh_cli_flags.sh (Fixtures, offline).
set -euo pipefail

WF_DIR="${1:-.github/workflows}"
GH_BIN="${GH_BIN:-gh}"

fail() { echo "❌ [gh-flags] $1"; exit 1; }

if [ ! -d "$WF_DIR" ]; then
  fail "Workflow-Verzeichnis '$WF_DIR' nicht gefunden."
fi
if ! command -v "$GH_BIN" >/dev/null 2>&1; then
  fail "gh-CLI nicht gefunden (GH_BIN='$GH_BIN') — Guard braucht sie zur Validierung."
fi

# ── gh-Referenz (einmalig, gecacht) ──────────────────────────────────────────
# Colon-Suffix ist das verlässliche Listenformat in gh-Hilfetexten
# ("  create:        Create a new release").
colon_entries() {
  "$GH_BIN" "$@" --help 2>/dev/null | sed -n 's/^[[:space:]]*\([a-z][a-z0-9-]*\):.*/\1/p' | tr '\n' ' '
}

TOP_CMDS="$(colon_entries)"
[ -n "$TOP_CMDS" ] || fail "Konnte die gh-Top-Level-Commands nicht aus 'gh --help' extrahieren."

declare -A FLAGSETS  # "cmd[:sub]" -> Newline-Liste der gültigen Flags
declare -A SUBS_CACHE # cmd -> Space-Liste der Subcommands

flags_of() { # $1+ = weitere gh-Argumente (z. B. "release list")
  "$GH_BIN" "$@" --help 2>/dev/null \
    | grep -oE '(^|[[:space:]])-{1,2}[A-Za-z][A-Za-z0-9-]+' | tr -d ' ' | sort -u
}

GLOBAL_FLAGS="$(flags_of)"   # gh --help

subs_of() { # $1=cmd — gecachte Subcommand-Liste
  if [ -z "${SUBS_CACHE[$1]:-}" ]; then
    SUBS_CACHE[$1]="$(colon_entries "$1")"
  fi
  printf '%s' "${SUBS_CACHE[$1]}"
}

resolve_for() { # $1=cmd $2=sub (oder leer) → Flag-Liste nach stdout
  local cmd="$1" sub="$2" key
  if [ -n "$sub" ]; then
    key="$cmd:$sub"
    if [ -z "${FLAGSETS[$key]:-}" ]; then
      FLAGSETS[$key]="$(flags_of "$cmd" "$sub")"
    fi
    printf '%s' "${FLAGSETS[$key]}"
    return
  fi
  key="$cmd"
  if [ -z "${FLAGSETS[$key]:-}" ]; then
    local group_subs
    group_subs="$(subs_of "$cmd")"
    if [ -n "$group_subs" ]; then
      # Gruppen-Kontext ohne erkannten Subcommand: Flags der Gruppe
      # (INHERITED FLAGS, z. B. --repo) + globale Flags.
      FLAGSETS[$key]="$( { flags_of "$cmd"; printf '%s\n' "$GLOBAL_FLAGS"; } | sort -u)"
    else
      # Leaf-Command: eigene FLAGS + INHERITED FLAGS stehen in der Hilfe.
      FLAGSETS[$key]="$(flags_of "$cmd")"
    fi
  fi
  printf '%s' "${FLAGSETS[$key]}"
}

# Liefert 0, wenn $2 in $1 enthalten ist (Liste: space/komma/newline-separiert;
# pure bash — "grep -qxF --limit" würde "--limit" als Option essen).
list_has() {
  local list="$1" item="$2"
  list="${list// /$'\n'}"
  list="${list//,/$'\n'}"
  [[ $'\n'"$list"$'\n' == *$'\n'"$item"$'\n'* ]]
}

# ── Workflow-Scan: EIN awk-Pass je Datei ─────────────────────────────────────
# awk extrahiert pro Zeile: gh-Aufruf (cmd [+ sub-Kandidat]), alle --flags,
# Exempts; Ausgabe als TSV-Record. Die Bash-Loop macht nur noch die gecachten
# Membership-Checks (kein Subprocess pro Zeile).
SCAN='
{
  line = $0
  # gh-Aufruf: erstes "gh <cmd> [<sub-Kandidat>]" (nicht Teil eines Bezeichners).
  if (match(line, /(^|[^A-Za-z0-9_-])gh[ \t]+[a-z][a-z0-9-]+([ \t]+[a-z][a-z0-9-]+)?/)) {
    call = substr(line, RSTART, RLENGTH)
    sub(/^.*gh[ \t]+/, "", call)
    n = split(call, parts, /[ \t]+/)
    cmd = parts[1]
    subcand = (n >= 2) ? parts[2] : ""
  } else {
    next
  }
  # Alle Long-Flags der Zeile (inkl. der im gh-Aufruf stehenden).
  flags = ""
  rest = line
  while (match(rest, /(^|[ \t])--?[A-Za-z][A-Za-z0-9-]+/)) {
    tok = substr(rest, RSTART, RLENGTH)
    # Nur Whitespace strippen — die -/-Bindestriche gehoeren zum Token
    # ("^[ \t-]+" wuerde beide Striche von --limit fressen).
    gsub(/^[ \t]+/, "", tok)
    if (tok ~ /^--/) {
      flags = flags (flags == "" ? "" : ",") tok
    }
    rest = substr(rest, RSTART + RLENGTH)
  }
  if (flags == "") { next }
  # Exempts: alles nach "gh-flag-exempt:" (Komma/space-separiert).
  exempts = ""
  if (match(line, /gh-flag-exempt:[^#]*/)) {
    exempts = substr(line, RSTART + length("gh-flag-exempt:"), RLENGTH - length("gh-flag-exempt:"))
    gsub(/^[ \t]+|[ \t]+$/, "", exempts)
  }
  printf "%s\t%d\t%s\t%s\t%s\t%s\n", FILENAME, FNR, cmd, subcand, flags, exempts
}
'

VIOLATIONS=0
CHECKED=0

# Records erst in eine Temp-Datei schreiben — die Auswertungs-Loop läuft
# dann in derselben Shell (Pipeline-while würde in einer Subshell laufen und
# die Zähler verschlucken).
RECORDS="$(mktemp)"
trap 'rm -f "$RECORDS"' EXIT

while IFS= read -r -d '' file; do
  awk "$SCAN" "$file" >> "$RECORDS"
done < <(find "$WF_DIR" -maxdepth 1 \( -name '*.yml' -o -name '*.yaml' \) -print0)

while IFS=$'\t' read -r file lineno cmd subcand flags exempts; do
  list_has "$TOP_CMDS" "$cmd" || continue
  sub=""
  if [ -n "$subcand" ]; then
    subs="$(subs_of "$cmd")"
    list_has "$subs" "$subcand" && sub="$subcand"
  fi

  flags_for_cmd="$(resolve_for "$cmd" "$sub")"
  [ -n "$flags_for_cmd" ] || continue

  oldIFS="$IFS"
  IFS=','
  for flag in $flags; do
    [ -n "$flag" ] || continue
    CHECKED=$((CHECKED + 1))
    list_has "$flags_for_cmd" "$flag" && continue
    list_has "$GLOBAL_FLAGS" "$flag" && continue
    list_has "$exempts" "$flag" && {
      echo "  ⚠️  EXEMPT     $file:$lineno $flag (gh-flag-exempt)"
      continue
    }
    echo "  ❌ $file:$lineno — $flag wird von 'gh $cmd${sub:+ $sub}' (lokal: $("$GH_BIN" --version | head -1)) nicht angeboten"
    VIOLATIONS=$((VIOLATIONS + 1))
  done
  IFS="$oldIFS"
done < "$RECORDS"

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  fail "$VIOLATIONS unbekannte gh-Flag(s) in Workflows — lokal prüfen ('gh <cmd> <sub> --help'); der zugehörige Run würde im Job mit 'unknown flag' scheitern. Bewusstes Opt-out per '# gh-flag-exempt: --flag' in derselben Zeile."
fi

echo "✅ [gh-flags] $CHECKED Flag-Verwendung(en) gegen lokale gh-CLI geprüft — alle bekannt."
