#!/usr/bin/env bash
# I18n-Guard (Vivid): drei Checks, die eine Rückkehr zur String-Hartkodierung
# verhindern (siehe docs/i18n-plan.md §5):
#
#   1. Externalisierungs-Gate: `Text("…")`, `contentDescription = "…"` und
#      `label = "…"` / `title = "…"`-Literale in den UI-Modulen sind verboten.
#      Ausnahmen (bewusst nicht lokalisiert, siehe i18n-plan §4):
#      - AppChatStreamControl.kt  → !diag-Bot-Ausgabe (Bot-Antworten bleiben
#        in der Streamer-Sprache, wie alle Bot-Texte in feature-chat)
#      - Bot-Antworten/-Befehle in feature-chat (BotCommandProcessor,
#        ChatBotEngine, TwitchSendChatClient-Exceptions) — bewusst in der
#        Sprache des Streamers/Viewers, keine UI-Texte
#      - rein technische Strings (Logs, WakeLock-Tags, URLs, Routen,
#        WidgetFormatters-Einheiten wie km/h) werden von den Mustern nicht
#        erfasst bzw. sind bewusst konstant.
#   2. Vollständigkeits-Check: `values/strings.xml` ↔ `values-en/strings.xml`
#      und `values-fr/strings.xml` müssen in jedem Modul dieselben Keys
#      haben (fehlende Übersetzung = Fehler; verwaiste Keys = Fehler).
#   3. stream_url_hint-Inhalts-Guard: Der Hinweis unter dem Stream-URL-Feld
#      muss in BEIDEN Sprachen die Kernaussagen nennen (RTMP, SRT, Owncast,
#      Presets), damit die custom-Plattform-Fähigkeit sichtbar bleibt.
#
# Aufruf:  bash scripts/check_i18n.sh
set -euo pipefail

cd "$(dirname "$0")/.."

FAILED=0
fail() { echo "  ✗ $*"; FAILED=1; }

echo "▶ [i18n] 1/3 Externalisierungs-Gate (keine hartkodierten UI-Strings in src/main)"
# Zu prüfende Module (überschreibbar für den Selbsttest per I18N_MODULES).
if [[ -n "${I18N_MODULES:-}" ]]; then
  read -r -a UI_MODULES <<< "$I18N_MODULES"
else
  UI_MODULES=(
    "feature-settings/src/main"
    "feature-obs-control/src/main"
    "feature-streaming/src/main"
    "feature-chat/src/main"
    "feature-widgets/src/main"
    "app/src/main"
    "core/src/main"
  )
fi
# Erlaubte Dateien: Bot-Antworten/-Ausgaben, die bewusst NICHT lokalisiert
# werden (i18n-plan §4 — Bot-Texte in der Sprache des Streamers/Viewers):
# !diag-Ausgabe der Owner-Steuerung und Moderation-Client-Bestätigungen.
ALLOWLIST_FILES=(
  "app/src/main/java/com/vivid/irlbroadcaster/AppChatStreamControl.kt"
  "feature-chat/src/main/java/com/vivid/feature/chat/twitch/TwitchModerationClient.kt"
)

HITS=$(grep -rn -E 'Text\("|contentDescription = "[A-Za-zÄÖÜäöü]|label = "[A-Za-zÄÖÜäöü]|title = "[A-Za-zÄÖÜäöü]' "${UI_MODULES[@]}" --include="*.kt" \
  | grep -vE "stringResource|R\.string" \
  | grep -vFf <(printf '%s\n' "${ALLOWLIST_FILES[@]}") || true)

if [[ -n "$HITS" ]]; then
  echo "$HITS"
  fail "Hartkodierte UI-Strings gefunden — bitte in res/values*/strings.xml externalisieren."
else
  echo "  ✓ keine hartkodierten UI-Strings"
fi

echo "▶ [i18n] 2/3 Vollständigkeits-Check values/ ↔ values-en/ ↔ values-fr/"
for mod in "${UI_MODULES[@]}"; do
  # Module können mit oder ohne "/src/main" angegeben werden — für die
  # res-Pfade wird der Suffix normalisiert (z. B. "feature-settings" →
  # "feature-settings/src/main/res/...").
  res_root="${mod%/src/main}"
  de="$res_root/src/main/res/values/strings.xml"
  keys_de=$(grep -o 'name="[^"]*"' "$de" | sed 's/name="//;s/"//' | sort)
  for lang in en fr; do
    loc="$res_root/src/main/res/values-$lang/strings.xml"
    if [[ ! -f "$de" ]]; then
      fail "$mod: $de fehlt"
      continue
    fi
    if [[ ! -f "$loc" ]]; then
      fail "$mod: $loc fehlt (jede Pflicht-Sprache braucht die Datei)"
      continue
    fi
    keys_loc=$(grep -o 'name="[^"]*"' "$loc" | sed 's/name="//;s/"//' | sort)
    missing_loc=$(comm -23 <(echo "$keys_de") <(echo "$keys_loc"))
    missing_de=$(comm -13 <(echo "$keys_de") <(echo "$keys_loc"))
    if [[ -n "$missing_loc" ]]; then
      fail "$mod: ohne values-$lang-Übersetzung: $(echo "$missing_loc" | tr '\n' ' ')"
    fi
    if [[ -n "$missing_de" ]]; then
      fail "$mod: nur in values-$lang (verwaist): $(echo "$missing_de" | tr '\n' ' ')"
    fi
  done
  keys_en=$(grep -o 'name="[^"]*"' "$res_root/src/main/res/values-en/strings.xml" | sed 's/name="//;s/"//' | sort)
  keys_fr=$(grep -o 'name="[^"]*"' "$res_root/src/main/res/values-fr/strings.xml" | sed 's/name="//;s/"//' | sort)
  if [[ "$keys_de" == "$keys_en" && "$keys_de" == "$keys_fr" ]]; then
    echo "  ✓ $mod: alle Keys übersetzt"
  fi
done

echo "▶ [i18n] 3/3 stream_url_hint-Inhalts-Guard (alle drei Sprachen)"
# Modul des Hinweistexts (überschreibbar für den Selbsttest per I18N_HINT_MODULE).
hint_module="${I18N_HINT_MODULE:-feature-settings}"
hint_de=$(grep -o '<string name="stream_url_hint">[^<]*' "$hint_module/src/main/res/values/strings.xml" | sed 's/.*>//' || true)
hint_en=$(grep -o '<string name="stream_url_hint">[^<]*' "$hint_module/src/main/res/values-en/strings.xml" | sed 's/.*>//' || true)
hint_fr=$(grep -o '<string name="stream_url_hint">[^<]*' "$hint_module/src/main/res/values-fr/strings.xml" | sed 's/.*>//' || true)
for lang in "de:$hint_de" "en:$hint_en" "fr:$hint_fr"; do
  code="${lang%%:*}"
  text="${lang#*:}"
  if [[ -z "$text" ]]; then
    fail "stream_url_hint fehlt in Sprache $code"
    continue
  fi
  for keyword in RTMP SRT Owncast; do
    if [[ "$text" != *"$keyword"* ]]; then
      fail "stream_url_hint ($code) nennt $keyword nicht"
    fi
  done
  # Deutsch „Presets“, Englisch „presets“, Französisch „préréglages“
  # — groß-/klein-sensitiv je Sprache
  if [[ "$code" == "de" && "$text" != *"Presets"* ]]; then
    fail "stream_url_hint (de) ordnet die Vorlagen nicht als Presets ein"
  fi
  if [[ "$code" == "en" && "$text" != *"presets"* ]]; then
    fail "stream_url_hint (en) ordnet die Vorlagen nicht als presets ein"
  fi
  if [[ "$code" == "fr" && "$text" != *"préréglages"* ]]; then
    fail "stream_url_hint (fr) ordnet die Vorlagen nicht als préréglages ein"
  fi
done
if [[ -n "$hint_de" && -n "$hint_en" && -n "$hint_fr" ]]; then
  echo "  ✓ stream_url_hint nennt RTMP/SRT/Owncast + Presets in allen drei Sprachen"
fi

if [[ "$FAILED" == "1" ]]; then
  echo "❌ [i18n] I18n-Checks fehlgeschlagen."
  exit 1
fi
echo "✅ [i18n] Alle I18n-Checks grün."
