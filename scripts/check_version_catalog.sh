#!/usr/bin/env bash
# Guard: prüft, dass der Version-Catalog (libs.versions.toml) konsistente
# Versions-Keys für Artefakte desselben Groups verwendet.
#
# Regel: Alle Artefakte mit demselben Maven-Group-Prefix (z.B. androidx.media3,
# androidx.compose.ui, io.ktor) müssen denselben version.ref verwenden.
# Verschiedene Version-Keys für dieselbe Gruppe deuten auf Vergessenes hin
# (z.B. media3-exoplayer auf Key A, media3-ui auf Key B).
#
# Exit-Code 0 = konsistent, 1 = Verstoß.
set -euo pipefail

CATALOG="${1:-gradle/libs.versions.toml}"

if [ ! -f "$CATALOG" ]; then
  echo "❌ [catalog-check] $CATALOG nicht gefunden."
  exit 1
fi

# ── [versions] Block einlesen ────────────────────────────────────────────────
declare -A VERSIONS
in_versions=0
while IFS= read -r line; do
  # Block-Header erkennen
  if [[ "$line" =~ ^\[versions\] ]]; then
    in_versions=1
    continue
  fi
  if [[ "$line" =~ ^\[.*\] ]] && [ "$in_versions" -eq 1 ]; then
    break
  fi
  [ "$in_versions" -eq 0 ] && continue

  # Leerzeilen und Kommentare überspringen
  [[ "$line" =~ ^[[:space:]]*$ ]] && continue
  [[ "$line" =~ ^[[:space:]]*# ]] && continue

  # key = "value" parsen
  key="$(echo "$line" | sed -n 's/^[[:space:]]*\([^=[:space:]]*\).*=.*/\1/p' | tr -d '[:space:]')"
  value="$(echo "$line" | sed -n 's/.*=[[:space:]]*"\([^"]*\)".*/\1/p')"
  [ -n "$key" ] && [ -n "$value" ] && VERSIONS["$key"]="$value"
done < "$CATALOG"

# ── [libraries] Block: Artefakte nach Group gruppieren ───────────────────────
# Struktur: alias = { group = "...", name = "...", version.ref = "..." }
# Oder:     alias = { module = "...", version.ref = "..." }
# Oder:     alias = { module = "..." }  (kein version → BOM-managed)
declare -A GROUP_KEY_MAP   # group_prefix -> version_key (erster Fund)
declare -A GROUP_VERSIONS  # group_prefix -> version_key (alle Funde, pipe-separiert)
FAIL=0

in_libs=0
current_group=""
current_version_ref=""

while IFS= read -r line; do
  if [[ "$line" =~ ^\[libraries\] ]]; then
    in_libs=1
    continue
  fi
  if [[ "$line" =~ ^\[.*\] ]] && [ "$in_libs" -eq 1 ]; then
    break
  fi
  [ "$in_libs" -eq 0 ] && continue

  [[ "$line" =~ ^[[:space:]]*$ ]] && continue
  [[ "$line" =~ ^[[:space:]]*# ]] && continue

  # Group extrahieren
  current_group="$(echo "$line" | sed -n 's/.*group[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p')"

  # Falls kein group-Feld, versuche aus module zu extrahieren (Format: "group:name")
  if [ -z "$current_group" ]; then
    local_module="$(echo "$line" | sed -n 's/.*module[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p')"
    if [[ "$local_module" == *:* ]]; then
      current_group="${local_module%%:*}"
    fi
  fi

  # version.ref extrahieren
  current_version_ref="$(echo "$line" | sed -n 's/.*version\.ref[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p')"

  # Kein version.ref → BOM-managed, überspringen
  [ -z "$current_version_ref" ] && continue
  # Kein group → überspringen
  [ -z "$current_group" ] && continue

  # Group-Prefix: erster 2-Segment-Prefix (z.B. "androidx.media3" aus "androidx.media3")
  # Vollständigen Group-String als Key verwenden für präzisere Checks
  group_prefix="$current_group"

  # Prüfen, ob diese Gruppe bereits einen anderen version.key verwendet
  existing="${GROUP_KEY_MAP[$group_prefix]:-}"
  if [ -n "$existing" ] && [ "$existing" != "$current_version_ref" ]; then
    echo "❌ [catalog-check] Group '$group_prefix' verwendet verschiedene version.ref:"
    echo "   - $existing (erstes Artefakt)"
    echo "   - $current_version_ref (aktuelles Artefakt)"
    FAIL=1
  fi
  GROUP_KEY_MAP["$group_prefix"]="$current_version_ref"

  # Alle Keys für Summary sammeln
  prev="${GROUP_VERSIONS[$group_prefix]:-}"
  GROUP_VERSIONS["$group_prefix"]="${prev:+$prev | }$current_version_ref"

done < "$CATALOG"

# ── Ergebnis ─────────────────────────────────────────────────────────────────
if [ "$FAIL" -eq 1 ]; then
  echo ""
  echo "❌ [catalog-check] Inkonsistente Versions-Keys im Catalog gefunden."
  echo "   Alle Artefakte derselben Gruppe sollten denselben version.ref verwenden."
  exit 1
fi

echo "✅ [catalog-check] $(echo "${!GROUP_KEY_MAP[@]}" | wc -w | tr -d ' ') Gruppen geprüft, alle konsistent."
