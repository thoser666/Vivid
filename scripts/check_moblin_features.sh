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

# Extrahiere Features aus der Moblin-README (Zeilen mit "- " nach "## Features").
# Reine URL-Zeilen (z. B. App-Links) sind keine Features und werden gefiltert.
grep -E "^- " "$MOBLIN_FILE" | \
    sed 's/^-[ ]*//' | \
    sed 's/\.$//' | \
    grep -Ev '^https?://' | \
    sort -u > "$TMP_DIR/moblin_features.txt"

MOBLIN_COUNT=$(wc -l < "$TMP_DIR/moblin_features.txt")
echo "   Moblin-Features gefunden: $MOBLIN_COUNT"

echo "▶ [moblin-check] Lade PARITY.md..."

# grep -c gibt bei 0 Treffern "0" UND Exit 1 zurueck - das `|| echo 0`
# wuerde daher eine zweite Zeile anhaengen. Deshalb: Zuweisung mit explizitem
# Fallback statt ODER-Kette.
PARITY_COUNT=$(grep -cE "^\| [A-ZÄÖÜa-zäöü]" "$PARITY_FILE" 2>/dev/null) || PARITY_COUNT=0
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
        # Extrahiere charakteristische Schluesselwoerter (>= 4 Zeichen).
        # Generische Woerter (take/many/supports/...) werden rausgefiltert,
        # sonst matchen sie als Substring an beliebiger Stelle (Fehlalarme).
        keywords=$(echo "$feature" | tr '[:upper:]' '[:lower:]' | \
            sed 's/[^a-z0-9 ]//g' | \
            tr ' ' '\n' | \
            awk 'length >= 4' | \
            grep -Ev '^(take|this|that|with|from|into|have|will|them|they|also|some|many|each|both|when|then|than|only|very|more|most|over|under|after|before|while|about|other|using|your|support|supports|example|including|include|allows|allow|per|via|new|via|like)$' | \
            head -5)
        
        # Ein charakteristischer Treffer genuegt: das ist Beweis genug, dass
        # das Feature im Tracker existiert. Mit Schwellwert 2 blieben
        # Einwort-Features zwingend unmatchbar (Fehlalarme).
        # Alias-Map uebersetzt haeufige englische Feature-Woerter auf die
        # (ueberwiegend deutschen) PARITY-Begriffe; Plural-s wird zusaetzlich
        # als Variante abgestreift ("snapshots" -> "snapshot").
        found=0
        while IFS= read -r kw; do
            [ -z "$kw" ] && continue
            variants="$kw"
            case "$kw" in
                *s) variants="$variants ${kw%s}" ;;
            esac
            case "$kw" in
                localization) variants="$variants lokalisierung i18n sprache" ;;
                battery) variants="$variants akku" ;;
                snapshot) variants="$variants schnappschuss" ;;
                printer) variants="$variants drucker" ;;
                torch) variants="$variants taschenlampe lantern" ;;
                cosmetics) variants="$variants kosmetik" ;;
                resolution) variants="$variants auflösung" ;;
            esac
            for v in $variants; do
                if grep -qi "$v" "$PARITY_FILE" 2>/dev/null; then
                    found=1
                    break 2
                fi
            done
        done <<< "$keywords"
        
        if [ "$found" -eq 0 ]; then
            NEW_FEATURES=$((NEW_FEATURES + 1))
            echo "### $NEW_FEATURES. $feature"
            echo ""
            echo "❌ Noch nicht im PARITY-Tracker. Antwortbogen (Kriterien: docs/vision.md):"
            echo ""
            echo "- [ ] **Vision-Check (5 Fragen):** Streamer-Nutzen · Mobile-Realität · Parität/Ökosystem · Wartbarkeit · FOSS — Ergebnis: ☐ Aufnehmen ☐ Zurückstellen ☐ Ablehnen"
            echo "- [ ] **Feature-Familie / vermuteter PARITY-Bucket:** ___"
            echo "- [ ] **Ziel-Milestone:** ☐ M2 ☐ M3 ☐ M4 ☐ Backlog"
            echo ""
        fi
    done < "$TMP_DIR/moblin_features.txt"
    
    echo ""
    if [ "$NEW_FEATURES" -gt 0 ]; then
        echo "**$NEW_FEATURES neue/vollständig fehlende Features gefunden.**"
        echo ""
        echo "→ Vorschlag: Jedes Finding unten mit dem Vision-Antwortbogen bewerten (docs/vision.md), dann PARITY.md aktualisieren oder dokumentiert ablehnen."
    else
        echo "**✅ Keine neuen Features gefunden — PARITY.md ist aktuell.**"
    fi
} > "report.md"

cat "report.md"

if [ "$NEW_FEATURES" -gt 0 ]; then
    echo ""
    echo "❌ $NEW_FEATURES neue Features gefunden — Gap-Analyse empfohlen."
    exit 1
else
    echo ""
    echo "✅ Keine neuen Features gefunden."
    exit 0
fi
