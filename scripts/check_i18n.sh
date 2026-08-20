#!/usr/bin/env bash
# I18n-Guard (Vivid): drei Checks, die eine Rückkehr zur String-Hartkodierung
# verhindern (siehe docs/i18n-plan.md §5):
#
#   1. Externalisierungs-Gate: `Text("…")`, `contentDescription = "…"` und
#      `label = "…"` / `title = "…"`-Literale in den UI-Modulen sind verboten.
#      Ausnahmen (bewusst nicht lokalisiert, siehe i18n-plan §4):
#      - AppChatStreamControl.kt  → !diag-Bot-Ausgabe (Bot-Antworten bleiben
#        in der Streamer-Sprache, wie alle Bot-Texte in feature-chat)
#      - rein technische Strings (Logs, WakeLock-Tags, URLs, Routen) werden
#        von den Mustern nicht erfasst.
#   2. Vollständigkeits-Check: `values/strings.xml` ↔ `values-en/strings.xml`
#      müssen in jedem Modul dieselben Keys haben (fehlende Übersetzung =
#      Fehler; verwaiste Keys = Fehler).
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
    "app/src/main"
  )
fi
# Erlaubte Dateien: Bot-Diagnose-Ausgabe (!diag), die bewusst NICHT lokalisiert
# wird (i18n-plan §4 — Bot-Antworten in der Sprache des Streamers/Viewers).
ALLOWLIST_FILES=(
  "app/src/main/java/com/vivid/irlbroadcaster/AppChatStreamControl.kt"
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

echo "▶ [i18n] 2/3 Vollständigkeits-Check values/ ↔ values-en/"
for mod in "${UI_MODULES[@]}"; do
  # Module können mit oder ohne "/src/main" angegeben werden — für die
  # res-Pfade wird der Suffix normalisiert (z. B. "feature-settings" →
  # "feature-settings/src/main/res/...").
  res_root="${mod%/src/main}"
  de="$res_root/src/main/res/values/strings.xml"
  en="$res_root/src/main/res/values-en/strings.xml"
  if [[ ! -f "$de" ]]; then
    fail "$mod: $de fehlt"
    continue
  fi
  if [[ ! -f "$en" ]]; then
    fail "$mod: $en fehlt (jede Pflicht-Sprache braucht die Datei)"
    continue
  fi
  keys_de=$(grep -o 'name="[^"]*"' "$de" | sed 's/name="//;s/"//' | sort)
  keys_en=$(grep -o 'name="[^"]*"' "$en" | sed 's/name="//;s/"//' | sort)
  missing_en=$(comm -23 <(echo "$keys_de") <(echo "$keys_en"))
  missing_de=$(comm -13 <(echo "$keys_de") <(echo "$keys_en"))
  if [[ -n "$missing_en" ]]; then
    fail "$mod: ohne values-en-Übersetzung: $(echo "$missing_en" | tr '\n' ' ')"
  fi
  if [[ -n "$missing_de" ]]; then
    fail "$mod: nur in values-en (verwaist): $(echo "$missing_de" | tr '\n' ' ')"
  fi
  [[ -z "$missing_en" && -z "$missing_de" ]] && echo "  ✓ $mod: alle Keys übersetzt"
done

echo "▶ [i18n] 3/3 stream_url_hint-Inhalts-Guard (beide Sprachen)"
# Modul des Hinweistexts (überschreibbar für den Selbsttest per I18N_HINT_MODULE).
hint_module="${I18N_HINT_MODULE:-feature-settings}"
hint_de=$(grep -o '<string name="stream_url_hint">[^<]*' "$hint_module/src/main/res/values/strings.xml" | sed 's/.*>//' || true)
hint_en=$(grep -o '<string name="stream_url_hint">[^<]*' "$hint_module/src/main/res/values-en/strings.xml" | sed 's/.*>//' || true)
for lang in "de:$hint_de" "en:$hint_en"; do
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
  # Deutsch „Presets“, Englisch „presets“ — groß-/klein-sensitiv je Sprache
  if [[ "$code" == "de" && "$text" != *"Presets"* ]]; then
    fail "stream_url_hint (de) ordnet die Vorlagen nicht als Presets ein"
  fi
  if [[ "$code" == "en" && "$text" != *"presets"* ]]; then
    fail "stream_url_hint (en) ordnet die Vorlagen nicht als presets ein"
  fi
done
if [[ -n "$hint_de" && -n "$hint_en" ]]; then
  echo "  ✓ stream_url_hint nennt RTMP/SRT/Owncast + Presets in beiden Sprachen"
fi

if [[ "$FAILED" == "1" ]]; then
  echo "❌ [i18n] I18n-Checks fehlgeschlagen."
  exit 1
fi
echo "✅ [i18n] Alle I18n-Checks grün."
