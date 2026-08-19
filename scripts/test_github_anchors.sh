#!/usr/bin/env bash
# Offline-Selbsttest gegen EMPIRISCH von GitHub verifizierte Anker-IDs.
#
# Problem: Der normale Selbsttest (test_markdown_anchors.sh) ist zirkulär —
# er prüft, dass der Checker zu seiner EIGENEN Berechnung passt. Dieser Test
# bricht den Zirkel: scripts/github_anchors_golden.tsv enthält Header →
# Anker-Paare, deren IDs direkt aus den GERENDERTEN GitHub-Seiten extrahiert
# wurden (inkl. Edge Cases: Variation-Selector U+FE0F, 3-facher Bindestrich,
# em-dash, Ampersand, Stern/Plus, Umlaute, Klammern).
#
# Der Test erzeugt aus jedem Golden-Header eine Mini-Fixture, ruft den Checker
# im --dump-anchors-Modus auf und vergleicht die berechnete ID mit GitHub.
# Weicht die Berechnung vom GitHub-Rendering ab → Test schlägt fehl.
#
# Läuft im CI (android.yml) und lokal: bash scripts/test_github_anchors.sh
set -euo pipefail

cd "$(dirname "$0")/.."
GOLDEN="scripts/github_anchors_golden.tsv"

[[ -f "$GOLDEN" ]] || { echo "❌ FAIL: $GOLDEN fehlt"; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "❌ FAIL: $1"; exit 1; }

count=0
while IFS=$'\t' read -r header expected; do
  [[ -z "$header" ]] && continue
  # Mini-Fixture: nur der Header (--dump-anchors braucht keine Links)
  printf '%s\n' "$header" > "$TMP/case.md"
  actual="$(bash scripts/check_markdown_anchors.sh --dump-anchors "$TMP/case.md" | cut -f2)"
  count=$((count + 1))
  if [[ "$actual" != "$expected" ]]; then
    echo "❌ Header: $header"
    echo "   GitHub (erwartet): $expected"
    echo "   Checker (ist)    : $actual"
    fail "Anker-Berechnung weicht vom GitHub-Rendering ab"
  fi
  echo "✅ $header → $actual"
done < "$GOLDEN"

[[ "$count" -gt 0 ]] || fail "Golden-File ist leer"
echo "✅ check_markdown_anchors.sh: $count GitHub-verifizierte Anker-IDs bestätigt."
