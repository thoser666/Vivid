#!/usr/bin/env bash
# Wächter: Ist die CodeQL-Kotlin-2.4.20-Blockade voraussichtlich aufgehoben?
#
# Empirischer Stand (2026-09-10, CodeQL-Run 34509237841): Auch Bundle 2.27.0
# (Default von codeql-action v4.38.0) lehnt Kotlin 2.4.20 ab — „Kotlin version
# 2.4.20 is too recent. CodeQL currently supports versions below 2.4.20".
# Der Extractor-Support ist upstream gemerged (github/codeql#20018, Issue
# #22404 geschlossen 08.09.2026), aber in KEINEM released Bundle ausgeliefert.
#
# SEIT 11.09.2026 ist CodeQL dank Workaround GRÜN: security-codeql.yml pinnt
# den Trace-Build auf Kotlin 2.4.10 (< 2.4.20, bewährtes Pair mit KSP 2.3.11;
# Tests: scripts/test_codeql_kotlin.sh). Dieser Wächter bleibt ADVISORY als
# Revert-Frühwarnung: Sobald ein Bundle > 2.27.0 den 2.4.20-Extractor liefert,
# ist der Pin-Step (Name „Pin Kotlin for CodeQL extractor") zu ENTFERNEN und
# der Kompromiss zu dokumentieren.
#
# Verhalten (schlank, exit immer 0 außer bei echten Skript-Fehlern):
#   1. Kotlin < 2.4.20 im Katalog?          → Guard inaktiv (exit 0).
#   2. Neuestes codeql-action-Bundle > 2.27.0 (BLOCKADE_BUNDLE)?
#                                           → ::warning:: „Blockade mutmaßlich
#                                             aufgehoben — Pin-Step entfernen +
#                                             CodeQL-Run verifizieren".
#   3. Bundle <= 2.27.0 / API tot / Antwort kaputt → still (exit 0).
# Die Verifikation erfolgt manuell per CodeQL-workflow_dispatch-Lauf; der
# Wächter ersetzt sie nicht (deshalb kein harter Exit-Code).
#
# Offline-Testbarkeit (Selbsttest: scripts/test_codeql_blockade.sh):
#   CODEQL_BUNDLE_JSON   — vorgerenderte Latest-Release-API-Antwort statt curl
#   KOTLIN_VERSION_FILE  — alternative libs.versions.toml (Fixture)
#   CODEQL_ISSUE_URL     — alternative Referenz (nur Doku im Warning-Text)
set -euo pipefail

KOTLIN_FILE="${KOTLIN_VERSION_FILE:-gradle/libs.versions.toml}"
LATEST_URL="${CODEQL_BUNDLE_URL:-https://api.github.com/repos/github/codeql-action/releases/latest}"
# Empirisch blockiertes Bundle (2026-09-10): 2.27.0. Hebt ein späteres Bundle
# auf, muss dieser Wert UND die RELEASE.md-Blockade-Notiz angepasst werden.
BLOCKADE_BUNDLE="2.27.0"

warn() { echo "::warning::[codeql-blockade] $1"; }

# Kotlin-Version aus dem Versions-Katalog lesen (leer, falls Zeile fehlt).
current_kotlin() {
  grep -m1 '^kotlin = ' "$KOTLIN_FILE" 2>/dev/null | sed 's/.*"\([^"]*\)".*/\1/' || true
}

# Versions-Tripel → Zahl (Vergleich ohne bc).
ver_to_num() {
  local major minor patch
  IFS='.' read -r major minor patch <<<"$1"
  echo "$((10#${major:-0} * 1000000 + 10#${minor:-0} * 1000 + 10#${patch:-0}))"
}

kotlin="$(current_kotlin)"
[ -n "$kotlin" ] || exit 0
# Guard nur relevant, solange die blockierte Version im Katalog liegt.
[ "$(ver_to_num "$kotlin")" -ge "$(ver_to_num 2.4.20)" ] || exit 0

# Neuestes Bundle beschaffen (Override für Tests; Fehler = neutral weiter).
if [ -n "${CODEQL_BUNDLE_JSON:-}" ]; then
  json="$CODEQL_BUNDLE_JSON"
else
  json="$(curl -sfL --max-time 30 "$LATEST_URL" 2>/dev/null || printf '')"
fi
[ -n "$json" ] || exit 0

tag="$(printf '%s' "$json" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)"
[ -n "$tag" ] || exit 0
# Tags wie "codeql-bundle-v2.27.0" → "2.27.0"; Nicht-Bundle-Tags (Action-Releases) ignorieren.
bundle="$(printf '%s' "$tag" | sed -n 's/^codeql-bundle-v\([0-9.]*\)$/\1/p')"
[ -n "$bundle" ] || exit 0

if [ "$(ver_to_num "$bundle")" -gt "$(ver_to_num "$BLOCKADE_BUNDLE")" ]; then
  warn "Neuestes CodeQL-Bundle $bundle > $BLOCKADE_BUNDLE: Kotlin-2.4.20-Extractor mutmaßlich ausgeliefert (github/codeql#22404). THEN den Pin-Step (Name: 'Pin Kotlin for CodeQL extractor') in security-codeql.yml ENTFERNEN, CodeQL per workflow_dispatch verifizieren und diese Blockade-Notiz (RELEASE.md) + BLOCKADE_BUNDLE in scripts/check_codeql_blockade.sh anpassen."
fi
exit 0
