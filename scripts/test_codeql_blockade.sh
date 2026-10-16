#!/usr/bin/env bash
# Selbsttest: CodeQL-Blockade-Wächter (scripts/check_codeql_blockade.sh, offline).
#
# Der Wächter warnt seit der Blockaden-Aufhebung (15.10.2026) nicht mehr
# „der Pin ist entbehrlich", sondern umgekehrt: „der nächste Kotlin-Bump
# über dem verifizierten Extractor-Stand 2.4.21 braucht einen grünen
# CodeQL-Lauf vor dem Merge". Die Bundle-API-Abfrage fiel damit weg — der
# Wächter ist rein kataloggetrieben und offline deterministisch.
#
# Szenarien:
#   T1: Katalog-Kotlin == KNOWN (2.4.21, aktueller Repo-Stand) → still
#   T2: Katalog-Kotlin darunter (2.4.20 / 2.3.10)             → still
#   T3: Katalog-Kotlin darüber (2.4.22 / 2.5.0)               → ::warning::
#   T4: Warnung nennt Verifikationsweg (#22404 + workflow_dispatch + KNOWN-Stand)
#   T5: leere/kaputte/fehlende Katalog-Datei                  → still (exit 0)
#   T6: Warnung nennt die Anpassungspflichten (test_codeql_kotlin.sh + RELEASE.md)
set -euo pipefail
cd "$(dirname "$0")/.."

GUARD=scripts/check_codeql_blockade.sh
FIXTURE_TOML="$(mktemp)"
trap 'rm -f "$FIXTURE_TOML" "$FIXTURE_TOML".*' EXIT

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1" >&2; exit 1; }

# Haupt-Fixture: aktueller Repo-Stand (verifizierter Extractor-Stand).
cat >"$FIXTURE_TOML" <<'EOF'
[versions]
kotlin = "2.4.21"
agp = "9.4.0"
EOF

fixture_for() { # $1=Kotlin-Version
  sed "s/2\\.4\\.21/$1/" "$FIXTURE_TOML" > "$FIXTURE_TOML.$1"
  printf '%s' "$FIXTURE_TOML.$1"
}

# T1: Katalog == KNOWN (2.4.21) → Wächter inaktiv (Negativkontrolle)
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T1: verifizierter Stand 2.4.21 erzeugte Output: $out"
pass "T1 Katalog 2.4.21 (verifiziert) → still"

# T2: Kotlin-Versionen unter KNOWN → inaktiv
for v in 2.4.20 2.3.10; do
  out="$(KOTLIN_VERSION_FILE="$(fixture_for "$v")" bash "$GUARD" 2>&1)"
  [ -z "$out" ] || fail "T2: Katalog $v erzeugte Output: $out"
done
pass "T2 Katalog 2.4.20/2.3.10 (unter KNOWN) → still"

# T3: Versionen über KNOWN → Warning
for v in 2.4.22 2.5.0; do
  out="$(KOTLIN_VERSION_FILE="$(fixture_for "$v")" bash "$GUARD" 2>&1)"
  grep -q "::warning::" <<<"$out" || fail "T3: kein ::warning:: bei Katalog $v"
  grep -q "$v" <<<"$out" || fail "T3: Warning nennt die Katalog-Version $v nicht"
done
pass "T3 Katalog 2.4.22/2.5.0 (über KNOWN) → Warning"

t3_warning="$(KOTLIN_VERSION_FILE="$(fixture_for 2.5.0)" bash "$GUARD" 2>&1)"

# T4: Warnung nennt Verifikationsweg und verifizierten Stand
grep -q "22404" <<<"$t3_warning" || fail "T4: Warning verweist nicht auf #22404"
grep -q "workflow_dispatch" <<<"$t3_warning" || fail "T4: Warning nennt den Verifikationsweg nicht"
grep -q "2.4.21" <<<"$t3_warning" || fail "T4: Warning nennt den verifizierten Stand nicht"
pass "T4 Verifikationsweg (#22404, workflow_dispatch, KNOWN-Stand) dokumentiert"

# T5: leere/kaputte/fehlende Katalog-Datei → still (neutral, exit 0)
: > "$FIXTURE_TOML.leer"
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML.leer" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T5a: leere Datei erzeugte Output: $out"
printf '[versions]\nkotlin = kaputt\n' > "$FIXTURE_TOML.kaputt"
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML.kaputt" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T5b: kaputter Wert erzeugte Output: $out"
out="$(KOTLIN_VERSION_FILE="$FIXTURE_TOML.gibtnicht" bash "$GUARD" 2>&1)"
[ -z "$out" ] || fail "T5c: fehlende Datei erzeugte Output: $out"
pass "T5 leere/kaputte/fehlende Katalog-Datei → still"

# T6: Warning nennt beide Anpassungspflichten (Vertragstest + Doku)
grep -q "test_codeql_kotlin.sh" <<<"$t3_warning" || fail "T6: Anpassungspflicht test_codeql_kotlin.sh nicht dokumentiert"
grep -q "RELEASE.md" <<<"$t3_warning" || fail "T6: Anpassungspflicht RELEASE.md nicht dokumentiert"
pass "T6 Anpassungspflichten dokumentiert"

echo "✅ [test-codeql-blockade] Alle 6 Selbsttests bestanden."
