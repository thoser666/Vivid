#!/usr/bin/env bash
# Selbsttest für scripts/check_parity_log.sh:
#   beweist offline mit Wegwerf-Fixtures, dass der Guard greift:
#     P1  Positiv: sauberer Log (alle Einträge mit 7-Zeichen-Hash) -> gruen
#     P2  Negativ: Platzhalter (Em-Dash) in der Commit-Spalte      -> rot
#     P3  Negativ: ungueltiger Hash (nicht-hex / falsche Laenge)    -> rot
#     P4  Positiv: Header-/Trenner-Zeilen zaehlen nicht, Backtick-
#                  Hashes sind gueltig                              -> gruen
#     P5  Real: der echte Repo-Stand (PARITY.md) ist gruen
#                  (inkl. --check-exists: alle Hashes existieren und
#                  sind Vorfahren von HEAD)                         -> gruen
#     P6  Negativ: formatgueltiger, aber NICHT existierender Hash
#                  (mit --check-exists)                             -> rot
# Läuft im CI (android-ci.yml) und lokal: bash scripts/test_parity_log.sh
#
# Hinweis: Die Fixture-Zeilen sind bewusst ASCII — das Zeichen in der
# Platzhalter-Spalte (Unicode-Em-Dash) steht nur im heredoc-Inhalt, der von
# bash literal übernommen wird; die Labels/Meldungen bleiben ASCII, damit
# der Test auf Windows-Konsolen (cp1252) nicht an UTF-8 bricht.
set -euo pipefail

cd "$(dirname "$0")/.."
CHECK="bash scripts/check_parity_log.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL: $1"; exit 1; }
expect_exit() { # $1 = erwarteter Exit, $2 = Beschreibung, $3 = Datei, $4 = optionale Flags
  local expected="$1" desc="$2" file="$3" flags="${4:-}" rc=0
  set +e
  # shellcheck disable=SC2086 # flags ist genau ein Token (z. B. --check-exists)
  $CHECK $flags "$file" >/dev/null 2>&1
  rc=$?
  set -e
  if [ "$rc" -ne "$expected" ]; then
    fail "$desc (Exit $rc statt $expected)"
  fi
  echo "OK: $desc (Exit $expected)"
}

# ── P1: sauberer Log ───────────────────────────────────────────────────────
cat > "$TMP/clean.md" <<'EOF'
# PARITY
## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-20 | `9633fbf` | I18n-Log-Hashes ergaenzt |
| 2026-08-20 | `cee9141` | I18n-Externalisierung abgeschlossen |
| 2026-08-19 | `a3af8a5` | Chat-Overlay: Inline-Twitch-Emotes |

## Andere Sektion
| 2026-08-01 | - | nicht Teil des Logs |
EOF
expect_exit 0 "P1 sauberer Log -> gruen" "$TMP/clean.md"

# ── P2: Platzhalter (Em-Dash) ─────────────────────────────────────────────────────────────
# Das Em-Dash-Zeichen steht nur im heredoc-Inhalt (bash übernimmt ihn
# literal) — die Labels bleiben ASCII, damit der Test auch auf Windows-
# Konsolen (cp1252) läuft.
cat > "$TMP/dash.md" <<'EOF'
## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-20 | — | I18n-Externalisierung abgeschlossen |
EOF
expect_exit 1 "P2 Platzhalter (Em-Dash) -> rot" "$TMP/dash.md"

# ── P3: ungueltiger Hash ────────────────────────────────────────────────────
cat > "$TMP/badhash.md" <<'EOF'
## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-20 | zzz9999 | Kein Hex-Hash |
| 2026-08-19 | abc123 | Zu kurz |
EOF
expect_exit 1 "P3 ungueltige Hashes -> rot" "$TMP/badhash.md"

# ── P4: Header-Zeile zaehlt nicht, Backtick-Hashes sind gueltig ─────────────
cat > "$TMP/header.md" <<'EOF'
## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-20 | `deadbee` | Sauberer Eintrag mit Backticks |
EOF
expect_exit 0 "P4 Header-/Trenner-Zeilen ignoriert, Backtick-Hashes ok -> gruen" "$TMP/header.md"

# ── Real-Check des Repos (Regression) ───────────────────────────────────────
# Mit --check-exists: alle 42 echten Einträge müssen existieren und Vorfahren
# von HEAD sein (keine Rebase-Orphans wie c0fb445 nach dem Rebase 2026-08-21).
expect_exit 0 "P5 echter Repo-Stand (PARITY.md, --check-exists) -> gruen" "PARITY.md" "--check-exists"

# ── P6: formatgültiger, aber nicht existierender Hash (--check-exists) ───────
cat > "$TMP/nonexist.md" <<'EOF'
## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-21 | `1111111` | Existiert nicht im Repo |
EOF
expect_exit 1 "P6 nicht-existenter Hash (--check-exists) -> rot" "$TMP/nonexist.md" "--check-exists"

echo "OK: Alle 6 Faelle gruen."
