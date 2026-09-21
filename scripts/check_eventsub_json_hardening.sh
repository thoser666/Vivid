#!/usr/bin/env bash
# Guard: EventSub-JSON-Härtung (feature-chat).
#
# Hintergrund (Shared-Chat-Audit): Twitch erweitert EventSub-Payloads laufend
# um neue Felder (zuletzt `shared_chat` sowie `source_broadcaster_user_id/_login/
# _name`, `source_message_id`, `source_badges` auf `channel.chat.message`).
# Vivids Verteidigung dagegen ist konsequent `kotlinx.serialization.Json` mit
# `ignoreUnknownKeys = true` — brechen künftige Felder den Parser, fallen
# Chat-Nachrichten still aus Overlay/Bot, ohne dass ein Build rot wird.
#
# Dieser Guard erzwingt deshalb: Jede `Json { … }`-Instanziierung im
# Production-Code von feature-chat MUSS `ignoreUnknownKeys = true` setzen.
# Eine neue Instanz ohne die Option ist ein harter Fehler (Pre-Push-Gate + CI).
# Ein bewusstes Opt-out ist möglich: Wird die Datei der Instanz als
# `@Suppress("EventSubJsonIgnoreUnknownKeys")` markiert, weist der Guard den
# Verstoß als `SUPPRESSED` aus und bleibt grün — die Entscheidung ist damit
# sichtbar dokumentiert statt still.
#
# Exit 0 = alle Instanzen gehärtet (oder sauber suppressionsmarkiert),
# Exit 1 = mindestens eine ungeschützte Instanz.
#
# Selbsttest: scripts/test_eventsub_json_hardening.sh (Fixtures, offline).
set -euo pipefail

SRC_DIR="${1:-feature-chat/src/main/java}"

fail() { echo "❌ [eventsub-json] $1"; exit 1; }

if [ ! -d "$SRC_DIR" ]; then
  fail "Quellverzeichnis '$SRC_DIR' nicht gefunden."
fi

KT_FILES="$(find "$SRC_DIR" -name '*.kt')"
if [ -z "$KT_FILES" ]; then
  echo "✅ [eventsub-json] 0 Json-Instanziierung(en) geprüft — keine Kotlin-Dateien in '$SRC_DIR'."
  exit 0
fi

VIOLATIONS=0
SUPPRESSED=0
INSTANCES=0

# awk markiert JEDE gefundene Instanz als ok/bad (nicht nur Verstöße), damit
# die Prüfabdeckung im Abschluss ehrlich ausgewiesen wird.
while IFS= read -r line_info; do
  file="${line_info%%:*}"
  rest="${line_info#*:}"
  lineno="${rest%%:*}"
  verdict="${rest#*:}"
  INSTANCES=$((INSTANCES + 1))
  if [ "$verdict" = "ok" ]; then
    continue
  fi
  if grep -qF '@Suppress("EventSubJsonIgnoreUnknownKeys")' "$file"; then
    echo "  ⚠️  SUPPRESSED  $file:$lineno — Json { … } ohne ignoreUnknownKeys (bewusst markiert)"
    SUPPRESSED=$((SUPPRESSED + 1))
  else
    echo "  ❌ $file:$lineno — Json { … } ohne ignoreUnknownKeys"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done < <(awk '
  # Instanz = "Json" als Bezeichner gefolgt von "{", keine Kommentarzeile.
  ( $0 ~ /(^|[^A-Za-z0-9_])Json[[:space:]]*\{/ ) && ( $0 !~ /^[[:space:]]*(\/\/|\*|\/\*)/ ) {
    start = NR; buf = $0
    # Mehrzeilige Initialisierungen einlesen (bis zur schließenden Klammer).
    while (buf !~ /\}/ && (NR - start) < 12 && (getline nextline) > 0) {
      buf = buf "\n" nextline
    }
    if (buf ~ /ignoreUnknownKeys[[:space:]]*=[[:space:]]*true/) {
      print FILENAME ":" start ":ok"
    } else {
      print FILENAME ":" start ":bad"
    }
  }
' $KT_FILES)

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  fail "$VIOLATIONS Json-Instanziierung(en) ohne ignoreUnknownKeys = true — neue/erweiterte Twitch-Felder (z. B. shared_chat, source_*) würden den Parser crashen. Option ergänzen oder die Datei bewusst mit @Suppress(\"EventSubJsonIgnoreUnknownKeys\") markieren."
fi

NOTE=""
if [ "$SUPPRESSED" -gt 0 ]; then NOTE=" ($SUPPRESSED suppressionsmarkiert)"; fi
echo "✅ [eventsub-json] $INSTANCES Json-Instanziierung(en) geprüft — alle mit ignoreUnknownKeys = true$NOTE."
