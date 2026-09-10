#!/usr/bin/env bash
# Selbsttest: Kotlin-Sync-Guard (scripts/check_kotlin_sync.sh) — Fixtures offline.
#
#   K1 beide Keys identisch        → grün, exit 0
#   K2 kotlin != jetbrainsKotlinJvm → Drift, exit 1
#   K3 kotlin fehlt                → roter Fehler
#   K4 jetbrainsKotlinJvm fehlt    → roter Fehler
#   K5 Catalog-Datei fehlt         → roter Fehler
#
# Nutzung: bash scripts/test_kotlin_sync.sh   (Exit 0 = alle grün)
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_kotlin_sync.sh"
fail() { echo "❌ [test-kotlin-sync] $1"; exit 1; }
pass() { echo "✅ [test-kotlin-sync] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

TOML_EQUAL="$TMP/equal.toml"
TOML_DRIFT="$TMP/drift.toml"
TOML_NO_KOTLIN="$TMP/no-kotlin.toml"
TOML_NO_JVM="$TMP/no-jvm.toml"

cat >"$TOML_EQUAL" <<'EOF'
[versions]
kotlin = "2.4.20"
jetbrainsKotlinJvm = "2.4.20"
EOF

cat >"$TOML_DRIFT" <<'EOF'
[versions]
kotlin = "2.4.20"
jetbrainsKotlinJvm = "2.4.10"
EOF

cat >"$TOML_NO_KOTLIN" <<'EOF'
[versions]
jetbrainsKotlinJvm = "2.4.20"
EOF

cat >"$TOML_NO_JVM" <<'EOF'
[versions]
kotlin = "2.4.20"
EOF

# ── K1: identisch → grün ─────────────────────────────────────────────────────
if bash "$GUARD" "$TOML_EQUAL"; then :; else
  fail "K1: synchroner Catalog muss exit 0 liefern (bekam $?)."
fi
pass "K1: kotlin == jetbrainsKotlinJvm → grün, exit 0."

# ── K2: Drift → rot ──────────────────────────────────────────────────────────
rc=0
bash "$GUARD" "$TOML_DRIFT" >"$TMP/out.txt" 2>&1 || rc=$?
if [[ $rc -eq 0 ]]; then
  fail "K2: Drift muss mit exit 1 abbrechen."
fi
grep -q '!=' "$TMP/out.txt" || fail "K2: Drift-Meldung (mit '!=') fehlt."
pass "K2: kotlin != jetbrainsKotlinJvm → rot, exit 1."

# ── K3: kotlin fehlt → rot ───────────────────────────────────────────────────
rc=0
bash "$GUARD" "$TOML_NO_KOTLIN" >"$TMP/out.txt" 2>&1 || rc=$?
if [[ $rc -eq 0 ]]; then
  fail "K3: fehlender kotlin-Key muss mit exit 1 abbrechen."
fi
grep -q "'kotlin' fehlt" "$TMP/out.txt" || fail "K3: Fehlermeldung für fehlenden kotlin-Key fehlt."
pass "K3: kotlin-Key fehlt → rot, exit 1."

# ── K4: jetbrainsKotlinJvm fehlt → rot ──────────────────────────────────────
rc=0
bash "$GUARD" "$TOML_NO_JVM" >"$TMP/out.txt" 2>&1 || rc=$?
if [[ $rc -eq 0 ]]; then
  fail "K4: fehlender jetbrainsKotlinJvm-Key muss mit exit 1 abbrechen."
fi
grep -q "'jetbrainsKotlinJvm' fehlt" "$TMP/out.txt" || fail "K4: Fehlermeldung für fehlenden jetbrainsKotlinJvm-Key fehlt."
pass "K4: jetbrainsKotlinJvm-Key fehlt → rot, exit 1."

# ── K5: Catalog-Datei fehlt → rot ────────────────────────────────────────────
rc=0
bash "$GUARD" "$TMP/does-not-exist.toml" >"$TMP/out.txt" 2>&1 || rc=$?
if [[ $rc -eq 0 ]]; then
  fail "K5: fehlende Catalog-Datei muss mit exit 1 abbrechen."
fi
grep -q 'nicht gefunden' "$TMP/out.txt" || fail "K5: 'nicht gefunden'-Meldung fehlt."
pass "K5: Catalog-Datei fehlt → rot, exit 1."

echo ""
echo "✅ [test-kotlin-sync] Alle 5 Selbsttests bestanden."