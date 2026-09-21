#!/usr/bin/env bash
# Selbsttest: gh-CLI-Flag-Guard (scripts/check_gh_cli_flags.sh) — Fixtures
# offline bis auf die gh-Referenz-Aufrufe (Hilfetexte der lokalen CLI).
#
#   G1 bekanntes Flag (gh release list --limit)   → grün, exit 0
#   G2 unbekanntes Flag (--exclude-prereleases)   → rot, exit 1
#   G3 exempt-markiertes unbekanntes Flag          → grün mit EXEMPT-Ausweis
#   G4 Subcommand-Auflösung (release list vs view) → Flags je Subcommand
#   G5 Kommentar-Prosa mit gh + Flag               → geprüft gegen den Kontext
#   G6 nur *.yml/*.yaml auf Ebene 1 werden gescannt
#   G7 fehlendes Verzeichnis                       → rot
#   G8 echter Workflow-Baum                        → beweist den Live-Fix
#
# Nutzung: bash scripts/test_gh_cli_flags.sh   (Exit 0 = alle grün)
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_gh_cli_flags.sh"
fail() { echo "❌ [test-gh-flags] $1"; exit 1; }
pass() { echo "✅ [test-gh-flags] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mk() { mkdir -p "$TMP/$1"; }

# ── G1: bekanntes Flag → grün ────────────────────────────────────────────────
mk g1
cat >"$TMP/g1/wf.yml" <<'EOF'
      - name: List
        run: |
          VERSION=$(gh release list --limit 1 --exclude-pre-releases --json tagName -q '.[0].tagName')
EOF
OUT=$(bash "$GUARD" "$TMP/g1") || fail "G1: bekannte Flags müssen grün sein (bekam $?)."
pass "G1: bekanntes Flag → grün."

# ── G2: unbekanntes Flag → rot ───────────────────────────────────────────────
mk g2
cat >"$TMP/g2/wf.yml" <<'EOF'
      - name: List
        run: |
          VERSION=$(gh release list --exclude-prereleases --json tagName)
EOF
rc=0
bash "$GUARD" "$TMP/g2" >"$TMP/g2.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "G2: unbekanntes Flag muss rot sein."; fi
grep -q "\-\-exclude-prereleases wird von 'gh release list'" "$TMP/g2.out" || fail "G2: Verstoß-Zeile fehlt: $(cat "$TMP/g2.out")"
pass "G2: unbekanntes Flag → rot mit präziser Zeile."

# ── G3: exempt-markiertes Flag → grün mit Ausweis ────────────────────────────
mk g3
cat >"$TMP/g3/wf.yml" <<'EOF'
      - name: List
        run: gh release view $TAG --legacyflag  # gh-flag-exempt: --legacyflag
EOF
OUT=$(bash "$GUARD" "$TMP/g3") || fail "G3: exemptes Flag muss grün bleiben (bekam $?)."
echo "$OUT" | grep -q "EXEMPT" || fail "G3: EXEMPT-Ausweis fehlt: $OUT"
pass "G3: gh-flag-exempt → grün mit EXEMPT-Ausweis."

# ── G4: Subcommand-Auflösung ─────────────────────────────────────────────────
mk g4
cat >"$TMP/g4/wf.yml" <<'EOF'
      - name: Mixed
        run: |
          gh release view v1 --json tagName
          gh release list --json tagName
EOF
OUT=$(bash "$GUARD" "$TMP/g4") || fail "G4: beide Subcommand-Flags müssen gültig sein (bekam $?)."
pass "G4: Subcommand-Auflösung (view/list) → grün."

# ── G5: Kommentar-Prosa mit gh + Flag → gegen Kontext geprüft ────────────────
mk g5
cat >"$TMP/g5/wf.yml" <<'EOF'
      # Dokumentation: sonst könnte man `gh release list --limit 5` nutzen.
      - name: Real call
        run: gh release list --limit 5
EOF
OUT=$(bash "$GUARD" "$TMP/g5") || fail "G5: gültige Flags in Kommentaren dürfen nicht stören (bekam $?)."
pass "G5: Kommentar-Prosa mit gültigen Flags → grün."

# ── G6: nur *.yml/*.yaml auf Ebene 1 ─────────────────────────────────────────
mk g6
cat >"$TMP/g6/wf.yml" <<'EOF'
      - name: List
        run: gh release list --limit 1
EOF
cat >"$TMP/g6/other.txt" <<'EOF'
      - run: gh release list --totally-unknown-flag
EOF
mkdir -p "$TMP/g6/nested"
cat >"$TMP/g6/nested/deep.yml" <<'EOF'
      - run: gh release list --totally-unknown-flag
EOF
OUT=$(bash "$GUARD" "$TMP/g6") || fail "G6: Nested/TXT-Dateien dürfen nicht gescannt werden (bekam $?)."
pass "G6: nur Top-Level-YAML wird gescannt."

# ── G7: fehlendes Verzeichnis → rot ──────────────────────────────────────────
rc=0
bash "$GUARD" "$TMP/nope" >"$TMP/g7.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "G7: fehlendes Verzeichnis muss rot sein."; fi
pass "G7: fehlendes Verzeichnis → rot."

# ── G8: echter Workflow-Baum beweist den Live-Fix ────────────────────────────
if bash "$GUARD" >/dev/null 2>&1; then :; else
  fail "G8: der echte Workflow-Baum muss nach dem Flag-Fix grün sein."
fi
pass "G8: echte Workflows → alle gh-Flags gegen die lokale CLI bekannt."

echo ""
echo "✅ [test-gh-flags] Alle 8 Fälle grün."
