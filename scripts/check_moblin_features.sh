#!/usr/bin/env bash
# check_moblin_features.sh — Vergleicht die Moblin-README-Features mit PARITY.md
# und meldet neue/fehlende Features. Wöchentlich via GitHub Actions geplant.
#
# Usage: bash scripts/check_moblin_features.sh
# Exit 0 = keine neuen Features, Exit 1 = neue Features gefunden

set -euo pipefail

MOBLIN_URL="https://raw.githubusercontent.com/eerimoq/moblin/main/README.md"
PARITY_FILE="PARITY.md"
TMP_DIR=$(mktemp -d)
MOBLIN_FILE="$TMP_DIR/moblin_readme.md"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

echo "▶ [moblin-check] Lade Moblin-README..."
curl -sL "$MOBLIN_URL" -o "$MOBLIN_FILE" || { echo "❌ Download fehlgeschlagen"; exit 1; }

echo "▶ [moblin-check] Extrahiere Feature-Liste aus Moblin-README..."

# Extrahiere Features aus der Moblin-README (Zeilen mit "- " nach "## Features")
grep -E "^- " "$MOBLIN_FILE" | \
    sed 's/^-[ ]*//' | \
    sed 's/\.$//' | \
    sort -u > "$TMP_DIR/moblin_features.txt"

MOBLIN_COUNT=$(wc -l < "$TMP_DIR/moblin_features.txt")
echo "   Moblin-Features gefunden: $MOBLIN_COUNT"

echo "▶ [moblin-check] Lade PARITY.md..."

PARITY_COUNT=$(grep -cE "^\| [A-ZÄÖÜa-zäöü]" "$PARITY_FILE" 2>/dev/null || echo 0)
echo "   PARITY-Features gefunden: $PARITY_COUNT"

echo "▶ [moblin-check] Vergleiche Features..."

# Erstelle einen Bericht über fehlende Features
NEW_FEATURES=0
{
    echo "# Moblin-Feature-Vergleich"
    echo ""
    echo "**Datum:** $(date +%Y-%m-%d)"
    echo "**Moblin-Features:** $MOBLIN_COUNT"
    echo "**PARITY-Features:** $PARITY_COUNT"
    echo ""
    
    while IFS= read -r feature; do
        # Extrahiere Schlüsselwörter (mindestens 3 Zeichen)
        keywords=$(echo "$feature" | tr '[:upper:]' '[:lower:]' | \
            sed 's/[^a-z0-9 ]//g' | \
            tr ' ' '\n' | \
            awk 'length >= 3' | \
            head -5)
        
        # Prüfe ob mindestens 2 Schlüsselwörter in PARITY.md vorkommen
        match_count=0
        while IFS= read -r kw; do
            if [ -n "$kw" ] && grep -qi "$kw" "$PARITY_FILE" 2>/dev/null; then
                match_count=$((match_count + 1))
            fi
        done <<< "$keywords"
        
        # Feature als "fehlend" markieren wenn weniger als 2 Keywords matchen
        if [ "$match_count" -lt 2 ]; then
            echo "- ❌ **$feature**"
            NEW_FEATURES=$((NEW_FEATURES + 1))
        fi
    done < "$TMP_DIR/moblin_features.txt"
    
    echo ""
    if [ "$NEW_FEATURES" -gt 0 ]; then
        echo "**$NEW_FEATURES neue/vollständig fehlende Features gefunden.**"
        echo ""
        echo "→ Vorschlag: Gap-Analyse durchführen und PARITY.md aktualisieren."
    else
        echo "**✅ Keine neuen Features gefunden — PARITY.md ist aktuell.**"
    fi
} > "$TMP_DIR/report.txt"

cat "$TMP_DIR/report.txt"

if [ "$NEW_FEATURES" -gt 0 ]; then
    echo ""
    echo "❌ $NEW_FEATURES neue Features gefunden — Gap-Analyse empfohlen."
    exit 1
else
    echo ""
    echo "✅ Keine neuen Features gefunden."
    exit 0
fi
