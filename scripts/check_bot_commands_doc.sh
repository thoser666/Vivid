#!/usr/bin/env bash
# Guard: Bot-Befehls-Doku-Konsistenz
#
# Source of Truth ist der BotCommandProcessor-Code (feature-chat). Der Guard
# extrahiert die kanonischen Befehlsnamen aus der dispatch()-Tabelle und prüft,
# dass jeder davon in der Quick-Reference-Tabelle aller drei Handbuch-Sprachen
# (DE/EN/FR) erwähnt wird. So kann ein neuer Bot-Befehl nicht mehr unbemerkt
# an der Doku vorbeishippen (das exakte !tts-Problem).
#
# Exit 0 = konsistent, Exit 1 = Befehl(e) fehlen in mindestens einer Sprache.
#
# Nutzung: bash scripts/check_bot_commands_doc.sh
set -euo pipefail

PROCESSOR="feature-chat/src/main/java/com/vivid/feature/chat/bot/BotCommandProcessor.kt"
GUIDES=(
  "docs/user-guide.md"
  "docs/user-guide.en.md"
  "docs/user-guide.fr.md"
)

# Aliase, die als eigener Eintrag in der Tabelle stehen und dieselbe Funktion
# abdecken — gemappt auf den kanonischen Namen aus dispatch().
declare -A ALIAS_MAP=(
  ["commands"]="help"
  ["hilfe"]="help"
  ["nowplaying"]="song"
  ["np"]="song"
  ["skip"]="next"
  ["go-live"]="start"
  ["go_live"]="start"
  ["livestart"]="start"
  ["end"]="stop"
  ["shutdown"]="stop"
  ["diagnose"]="diag"
  ["status"]="diag"
  ["test-alert"]="testalert"
  ["alert"]="testalert"
  ["lantern"]="torch"
  ["flashlight"]="torch"
  ["lowlight"]="boost"
  ["low-light"]="boost"
  ["akku"]="battery"
  ["color-space"]="colorspace"
  ["cs"]="colorspace"
  ["endpoll"]="pollend"
  ["end-poll"]="pollend"
  ["previous"]="prev"
  ["previous"]="prev"
  ["fx"]="filter"
)

missing_total=0

# 1) Kanonische Befehle aus der dispatch()-Tabelle extrahieren:
#    Zeilen der Form   "name", "alias" -> Result.Xyz   bzw.  "name" -> Result.Xyz
mapfile -t entries < <(sed -n '/private fun dispatch/,/^        }$/p' "$PROCESSOR" \
  | grep -oE '"[a-z_-]+" *->' | sed 's/"//g; s/ *->//' | sort -u)

if [[ ${#entries[@]} -eq 0 ]]; then
  echo "❌ [bot-commands-doc] Keine Befehle aus $PROCESSOR extrahiert — Muster prüfen."
  exit 1
fi

# 2) Aliase auf Kanonik normalisieren.
canonical_cmds=()
for cmd in "${entries[@]}"; do
  canonical_cmds+=("${ALIAS_MAP[$cmd]:-$cmd}")
done
mapfile -t canonical_cmds < <(printf '%s\n' "${canonical_cmds[@]}" | sort -u)

# 3) Jede Handbuch-Sprache prüfen.
for guide in "${GUIDES[@]}"; do
  if [[ ! -f "$guide" ]]; then
    echo "❌ [bot-commands-doc] Handbuch fehlt: $guide"
    exit 1
  fi
  missing=()
  for cmd in "${canonical_cmds[@]}"; do
    if ! grep -qiE "!${cmd//./\\.}([^a-z0-9_-]|\$)" "$guide"; then
      missing+=("!$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "❌ [bot-commands-doc] $guide: fehlende Bot-Befehle: ${missing[*]}"
    missing_total=1
  else
    echo "✅ [bot-commands-doc] $guide: alle ${#canonical_cmds[@]} Bot-Befehle dokumentiert."
  fi
done

if [[ $missing_total -ne 0 ]]; then
  echo "   Fix: Befehle in die Quick-Reference-Tabelle aller Sprachen aufnehmen"
  echo "   (Source of Truth: $PROCESSOR dispatch())."
  exit 1
fi
echo "✅ [bot-commands-doc] Handbücher (DE/EN/FR) decken alle Bot-Befehle ab."
