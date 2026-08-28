#!/usr/bin/env bash
# Tests für scripts/check_version_catalog.sh — deterministisch, ohne Netzwerk:
# ein temporärer TOML-Catalog simuliert konsistente/inkonsistente States.
# Aufruf: bash scripts/test_check_version_catalog.sh   (Exit 0 = alle grün)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKER="$SCRIPT_DIR/check_version_catalog.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
check() { # $1 = Beschreibung, $2 = erwarteter Exit, $3... = Argumente
  local desc="$1" expected="$2"; shift 2
  if bash "$CHECKER" "$@" >/dev/null 2>&1; then got=0; else got=1; fi
  if [ "$got" -eq "$expected" ]; then
    echo "PASS: $desc"; PASS=$((PASS+1))
  else
    echo "FAIL: $desc (erwartet Exit $expected, bekam $got)"; FAIL=$((FAIL+1))
  fi
}

# ── F1: Sauberer Catalog (alle Artefekte derselben Gruppe = gleicher Key) ───
cat > "$TMP/f1.toml" <<'EOF'
[versions]
ktor = "3.5.2"
media3 = "1.11.0"

[libraries]
ktor-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-cio = { group = "io.ktor", name = "ktor-client-cio", version.ref = "ktor" }
media3-exo = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
EOF
check "F1: Saubere Gruppen → grün" 0 "$TMP/f1.toml"

# ── F2: Inkonsistente Gruppe (gleicher Group, unterschiedliche Keys) ────────
cat > "$TMP/f2.toml" <<'EOF'
[versions]
ktor = "3.5.2"
ktorLogging = "3.4.0"

[libraries]
ktor-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktorLogging" }
EOF
check "F2: Inkonsistente Gruppe → rot" 1 "$TMP/f2.toml"

# ── F3: BOM-man Artefakte (kein version.ref) werden ignoriert ───────────────
cat > "$TMP/f3.toml" <<'EOF'
[versions]
composeBom = "2026.08.00"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
EOF
check "F3: BOM-managed (kein version.ref) → grün" 0 "$TMP/f3.toml"

# ── F4: Echte Gruppen mit identischem Wert, unterschiedlichem Key-Name ──────
cat > "$TMP/f4.toml" <<'EOF'
[versions]
datastorePrefs = "1.1.7"
datastorePrefsCore = "1.1.7"

[libraries]
ds-prefs = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePrefs" }
ds-prefs-core = { group = "androidx.datastore", name = "datastore-preferences-core", version.ref = "datastorePrefsCore" }
EOF
check "F4: Gleicher Wert, unterschiedlicher Key-Name → rot" 1 "$TMP/f4.toml"

# ── F5: Echter Repo-Stand (Regression) ──────────────────────────────────────
if [ -f "$SCRIPT_DIR/../gradle/libs.versions.toml" ]; then
  # Erwartung: Der aktuelle Catalog ist konsistent (alle Gruppen haben einen
  # einzigen version.ref) → also grün.
  check "F5: Echter Repo-Stand → grün (alle Gruppen konsistent)" 0 "$SCRIPT_DIR/../gradle/libs.versions.toml"
else
  echo "SKIP: F5 (gradle/libs.versions.toml nicht gefunden)"
fi

echo "----------------------------------------"
echo "check_version_catalog: $PASS bestanden, $FAIL fehlgeschlagen"
[ "$FAIL" -eq 0 ] || exit 1
echo "✅ Alle Catalog-Check-Tests grün"
