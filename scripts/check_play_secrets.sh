#!/usr/bin/env bash
# Prereq-Guard für den „Publish to Google Play“-Job (release-pipeline.yml).
#
# Prüft, ob alle für den Play-Upload nötigen Secrets gesetzt sind:
#   - UPLOAD_KEYSTORE_BASE64 / UPLOAD_KEYSTORE_PASSWORD / UPLOAD_KEY_ALIAS /
#     UPLOAD_KEY_PASSWORD (Upload-Key, getrennt vom Release-Key)
#   - PLAY_JSON_KEY_FILE oder PLAY_JSON_KEY_DATA (Service-Account, eins von beiden)
#
# Ohne vollständige Secrets gibt der Guard ready=false aus und der Rest des
# Jobs wird übersprungen (klare ::notice::-Meldung statt rotem Fehlschlag —
# ein manueller Dispatch ohne Secrets ist sonst nicht von einem echten
# Play-Fehler unterscheidbar). Konfigurationsanleitung:
# docs/distribution.md, Abschnitt „Google Play aktivieren (Secrets)“.
#
# Ausgabe: "ready=true" oder "ready=false" auf stdout (GITHUB_OUTPUT-Format).
# Exit-Code ist immer 0 — der Guard selbst scheitert nie, er entscheidet nur.
set -euo pipefail

missing=""
[ -z "${UPLOAD_KEYSTORE_BASE64:-}" ] && missing=" UPLOAD_KEYSTORE_BASE64"
[ -z "${UPLOAD_KEYSTORE_PASSWORD:-}" ] && missing="$missing UPLOAD_KEYSTORE_PASSWORD"
[ -z "${UPLOAD_KEY_ALIAS:-}" ] && missing="$missing UPLOAD_KEY_ALIAS"
[ -z "${UPLOAD_KEY_PASSWORD:-}" ] && missing="$missing UPLOAD_KEY_PASSWORD"
if [ -z "${PLAY_JSON_KEY_FILE:-}" ] && [ -z "${PLAY_JSON_KEY_DATA:-}" ]; then
  missing="$missing PLAY_JSON_KEY_FILE_or_PLAY_JSON_KEY_DATA"
fi

if [ -n "$(echo "$missing" | tr -d ' ')" ]; then
  echo "::notice::Play-Upload wird uebersprungen — fehlende Secrets:$missing. Konfiguration: docs/distribution.md, Abschnitt \"Google Play aktivieren (Secrets)\"."
  echo "ready=false"
else
  echo "ready=true"
fi
