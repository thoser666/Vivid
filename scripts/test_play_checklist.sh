#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

file=RELEASE.md
fail() { echo "❌ [play-checklist-test] $1"; exit 1; }
[[ -s "$file" ]] || fail "RELEASE.md fehlt oder ist leer"

# Die Master-Checkliste muss alle drei Prioritätsstufen und die kritischen
# Upload-/Store-/Tester-Schritte enthalten.
for heading in "P0 — Blockierend" "P1 — Fürs" "P2 — Auslieferung"; do
  grep -Fq "$heading" "$file" || fail "Abschnitt fehlt: $heading"
done
for marker in \
  'Play-Entwicklerkonto' \
  'App „Vivid“ anlegen' \
  'upload_cert.pem' \
  'Service-Account + JSON-Key' \
  'UPLOAD_KEYSTORE_BASE64' \
  'PLAY_JSON_KEY_DATA' \
  'Echte Screenshots' \
  'Content Rating' \
  'Data Safety' \
  'Track `alpha` anlegen' \
  '≥2 Tester einladen' \
  'Erster echter Upload' \
  'Smoke-Test bestätigen'; do
  grep -Fq "$marker" "$file" || fail "Pflichtpunkt fehlt: $marker"
done

# Fortschritts- und Sicherheitsformulierungen dürfen keine echten Secretwerte
# im Dokument nahelegen.
grep -Fq 'niemals committen' "$file" || fail "Secret-Sicherheitswarnung fehlt"
grep -Fq 'ohne `dry_run`' "$file" || fail "Expliziter Echtgeld-Upload-Schritt fehlt"

echo "✅ [play-checklist-test] P0–P2-Checkliste vollständig und sicherheitskonform."
