#!/usr/bin/env bash
# Selbsttest: CodeQL-Blockade-Wächter (scripts/check_codeql_blockade.sh, offline).
#
# Szenarien:
#   T1: Kotlin < 2.4.20 im Katalog            → still (exit 0, kein Warning)
#   T2: Bundle 2.26.4 (<= Blockade-Stand)     → still
#   T3: Bundle 2.28.0 (> Blockade-Stand)      → ::warning:: mit Verweis auf #22404
#   T4: Latest-Release ist ein Action-Tag (kein Bundle-Tag) → still
#   T5: API-Antwort leer/kaputt               → still (neutral, exit 0)
#   T6: Warnung enthält Anpassungs-Pflichten (RELEASE.md + BLOCKADE_BUNDLE)
#   T7: Warnung nennt die Revert-Anweisung (Pin-Step in security-codeql.yml entfernen)
set -euo pipefail
cd "$(dirname "$0")/.."

GUARD=scripts/check_codeql_blockade.sh
FIXTURE_TOML="$(mktemp)"
FIXTURE_LATEST="$(mktemp)"
trap 'rm -f "$FIXTURE_TOML" "$FIXTURE_LATEST"' EXIT

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

cat >"$FIXTURE_TOML" <<'EOF'
[versions]
kotlin = "2.4.20"
agp = "9.4.0"
EOF

json_for() { printf '{"tag_name": "%s", "name": "x"}' "$1"; }

# T1: alte Kotlin-Version → Wächter inaktiv (eigene Fixture, Haupt-Fixture bleibt 2.4.20)
sed 's/2\.4\.20/2.3.10/' "$FIXTURE_TOML" > "$FIXTURE_TOML.old"
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML.old" CODEQL_BUNDLE_JSON="$(json_for codeql-bundle-v2.28.0)" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T1: alte Kotlin-Version erzeugte Output: $out"
pass "T1 Kotlin < 2.4.20 → still"

# T2: Bundle <= Blockade-Stand → still
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON="$(json_for codeql-bundle-v2.26.4)" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T2: Bundle 2.26.4 erzeugte Output: $out"
pass "T2 Bundle 2.26.4 → still"

# T2b: exakt Blockade-Stand → still (kein flasches Warning)
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON="$(json_for codeql-bundle-v2.27.0)" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T2b: Bundle 2.27.0 erzeugte Output: $out"
pass "T2b Bundle 2.27.0 (Blockade-Stand) → still"

# T3: neueres Bundle → Warning mit #22404-Verweis
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON="$(json_for codeql-bundle-v2.28.0)" bash "$GUARD" 2>&1)"
grep -q "::warning::" <<<"$out" || fail "T3: kein ::warning:: bei Bundle 2.28.0"
grep -q "22404" <<<"$out" || fail "T3: Warning verweist nicht auf #22404"
grep -q "workflow_dispatch" <<<"$out" || fail "T3: Warning nennt den Verifikationsweg nicht"
t3_warning="$out"
pass "T3 Bundle 2.28.0 → Warning"

# T4: Action-Tag statt Bundle-Tag → still (Nicht-Bundle-Tags ignorieren)
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON="$(json_for v4.38.0)" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T4: Action-Tag erzeugte Output: $out"
pass "T4 Action-Tag → still"

# T5: leere/kaputte API-Antwort → still (neutral)
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON="" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T5a: leere Antwort erzeugte Output: $out"
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" CODEQL_BUNDLE_JSON='{"kaputt": ' bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T5b: kaputte Antwort erzeugte Output: $out"
pass "T5 leere/kaputte Antwort → still"

# T6: Warning nennt beide Anpassungspflichten
grep -q "RELEASE.md" <<<"$t3_warning" && grep -q "BLOCKADE_BUNDLE" "$GUARD" \
  || fail "T6: Anpassungspflichten (RELEASE.md/BLOCKADE_BUNDLE) nicht dokumentiert"
pass "T6 Anpassungspflichten dokumentiert"

# T7: Warning nennt die Revert-Anweisung (Pin-Step entfernen) — der Workaround
# in security-codeql.yml (Kotlin 2.4.10) ist überflüssig, sobald das Bundle den
# 2.4.20-Extractor trägt; der Wächter muss genau das sagen.
grep -q "Pin Kotlin for CodeQL extractor" <<<"$t3_warning" \
  || fail "T7: Warning nennt die Revert-Anweisung (Pin-Step entfernen) nicht"
pass "T7 Revert-Anweisung (Pin-Step entfernen) dokumentiert"

echo "✅ [test-codeql-blockade] Alle 7 Selbsttests bestanden."
