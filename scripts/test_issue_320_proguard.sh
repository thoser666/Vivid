#!/usr/bin/env bash
set -euo pipefail
# #320: Verifiziert, dass die ProGuard-Keep-Rules für Gson / Request-Interface
# (app/proguard-rules.pro) enthalten sind — schützt vor VIVID-3V (JsonIOException:
# "Abstract classes can't be instantiated!") bei R8-Miniaturisierung.

FILE="app/proguard-rules.pro"

fail() {
  echo "❌ #320 ProGuard-Guard: $1 fehlt in $FILE"
  exit 1
}

grep -q 'Gson / OBS WebSocket (Issue #320 / VIVID-3V)' "$FILE" || fail 'Header-Block'
grep -q 'security\.\*\*' "$FILE" || fail 'Keep security classes'
grep -q 'Request' "$FILE" || fail 'Keep Request interface'
grep -q 'requests\.\*' "$FILE" || fail 'Keep request implementations'

echo "✅ #320 ProGuard-Guard: alle Keep-Rules vorhanden in $FILE"
