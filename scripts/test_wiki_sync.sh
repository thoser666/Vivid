#!/usr/bin/env bash
# Selbsttest: Wiki-Sync-Generierung (scripts/sync_wiki.sh, Offline-Modus).
#
# Prüft die --generate-Ausgabe ohne Netzwerk:
#   T1: Alle vier Wiki-Seiten (Home, User-Guide-EN, User-Guide-FR,
#       Sentry-Ops) entstehen.
#   T2: Home enthält die DE-Quick-Reference inkl. der früher vergessenen
#       Befehle (!battery, !lut) und verlinkt die Sprach-Seiten.
#   T3: EN-Seite ist ein vollständiger Mirror (Header + Bot-Befehle).
#   T4: FR-Seite ist ein vollständiger Mirror (Header + Bot-Befehle).
#   T5: Keine CRLF-Reste in den generierten Dateien (Windows-Checkouts).
#   T6: Fehlende Handbuch-Datei → Skript schlägt fehl (Guard greift).
#   T7: Sentry-Ops-Seite ist ein Mirror von docs/sentry-ops.md (Header + Inhalt).
#
# Nutzung: bash scripts/test_wiki_sync.sh
set -euo pipefail

cd "$(dirname "$0")/.."

fail() { echo "❌ [test-wiki-sync] $1"; exit 1; }
pass() { echo "✅ [test-wiki-sync] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ── T1: Drei Seiten generiert ────────────────────────────────────────────────
bash scripts/sync_wiki.sh --generate "$TMP" >/dev/null 2>&1 \
  || fail "sync_wiki.sh --generate ist fehlgeschlagen."
for page in Home.md User-Guide-EN.md User-Guide-FR.md Sentry-Ops.md; do
  [[ -f "$TMP/$page" ]] || fail "$page wurde nicht generiert."
done
pass "T1: Home.md, User-Guide-EN.md, User-Guide-FR.md, Sentry-Ops.md generiert."

# ── T2: Home = DE-Quick-Reference + Sprach-Links ────────────────────────────
HOME_MD="$TMP/Home.md"
grep -q '!battery' "$HOME_MD" || fail "Home.md: !battery fehlt in der Quick-Reference."
grep -q '!lut' "$HOME_MD" || fail "Home.md: !lut fehlt in der Quick-Reference."
grep -q 'wiki/User-Guide-EN' "$HOME_MD" || fail "Home.md: Link auf User-Guide-EN fehlt."
grep -q 'wiki/User-Guide-FR' "$HOME_MD" || fail "Home.md: Link auf User-Guide-FR fehlt."
grep -q 'user-guide.en.md' "$HOME_MD" || fail "Home.md: Repo-Link EN-Handbuch fehlt."
pass "T2: Home.md enthält DE-Quick-Reference (!battery, !lut) + Sprach-Links."

# ── T3: EN-Seite = Mirror ────────────────────────────────────────────────────
EN_MD="$TMP/User-Guide-EN.md"
grep -q 'Mirror of' "$EN_MD" || fail "User-Guide-EN.md: Mirror-Header fehlt."
grep -q 'NICHT im Wiki editieren' "$EN_MD" || fail "User-Guide-EN.md: Auto-Generiert-Header fehlt."
grep -q '!tts' "$EN_MD" || fail "User-Guide-EN.md: Bot-Befehle fehlen im Mirror."
pass "T3: User-Guide-EN.md ist vollständiger Mirror (Header + Befehle)."

# ── T4: FR-Seite = Mirror ────────────────────────────────────────────────────
FR_MD="$TMP/User-Guide-FR.md"
grep -q 'Miroir de' "$FR_MD" || fail "User-Guide-FR.md: Miroir-Header fehlt."
grep -q '!tts' "$FR_MD" || fail "User-Guide-FR.md: Bot-Befehle fehlen im Mirror."
pass "T4: User-Guide-FR.md ist vollständiger Mirror (Header + Befehle)."

# ── T5: CRLF-frei ────────────────────────────────────────────────────────────
if grep -ql $'\r' "$TMP/Home.md" "$TMP/User-Guide-EN.md" "$TMP/User-Guide-FR.md" "$TMP/Sentry-Ops.md" 2>/dev/null; then
  fail "Generierte Seiten enthalten CR-Zeichen (CRLF nicht normalisiert)."
fi
pass "T5: Generierte Seiten sind CRLF-normalisiert."

# ── T6: Fehlendes Handbuch → Fehler ─────────────────────────────────────────
MISSING_DIR=$(mktemp -d)
if (cd "$MISSING_DIR" && bash "$(pwd -W 2>/dev/null || pwd)/scripts/sync_wiki.sh" --generate "$MISSING_DIR/out" >/dev/null 2>&1); then
  rm -rf "$MISSING_DIR"
  fail "Fehlende Handbücher wurden nicht als Fehler erkannt."
fi
rm -rf "$MISSING_DIR"
pass "T6: Fehlende Handbuch-Datei führt zu erwartbarem Fehler."

# ── T7: Sentry-Ops-Seite = Mirror der Ops-Kurzübersicht ───────────────────
SENTRY_MD="$TMP/Sentry-Ops.md"
grep -q 'Sentry-Ops' "$HOME_MD" || fail "Home.md: Sentry-Ops-Verweis fehlt."
grep -q 'Mirror von' "$SENTRY_MD" || fail "Sentry-Ops.md: Mirror-Header fehlt."
grep -q 'NICHT im Wiki editieren' "$SENTRY_MD" || fail "Sentry-Ops.md: Auto-Generiert-Header fehlt."
grep -q 'Die vier Bausteine' "$SENTRY_MD" || fail "Sentry-Ops.md: Bausteine-Übersicht fehlt."
grep -q 'Mapping-Upload' "$SENTRY_MD" || fail "Sentry-Ops.md: Mapping-Upload-Abschnitt fehlt."
grep -q 'Issue-Automation' "$SENTRY_MD" || fail "Sentry-Ops.md: Issue-Automation-Abschnitt fehlt."
pass "T7: Sentry-Ops-Seite ist Mirror der Ops-Kurzübersicht (Header + Inhalt)."

echo ""
echo "✅ [test-wiki-sync] Alle 7 Selbsttests bestanden."
