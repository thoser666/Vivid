#!/usr/bin/env bash
# Selbsttest: ICU-Regex-Klammer-Guard (scripts/check_icu_regex_braces.sh).
#
# Prüft gegen echte Fixtures (in temporären Git-Repos):
#   F1: Der originale Sentry-Crash-Pattern-Text (\{(road|city|country)}) → rot
#       (Regression gegen den Realbefund TEXT-INFO-WIDGET-REGEX-ICU).
#   F2: Auch die Resolver-Form \{(\w+)} → rot (zweite im Audit gefundene Stelle).
#   F3: Maskierte Form + Quantifier {1,5} + Klassen-Bereiche → grün (echter Repo-Stand).
#   F4: Testcode (src/test, *Test.kt) wird bewusst ignoriert → grün trotz bare brace.
#   F5: Fehlender Registry-Eintrag → rot (Advisory-Vertrag).
#   F6: Echter Repo-Stand → grün (Regression gegen die gefixten Stellen).
#
# Nutzung: bash scripts/test_icu_regex_braces.sh
set -euo pipefail
cd "$(dirname "$0")/.."

GUARD="scripts/check_icu_regex_braces.sh"
fail() { echo "❌ [test-icu-regex] $1"; exit 1; }
pass() { echo "✅ [test-icu-regex] $1"; }

[[ -f "$GUARD" ]] || fail "$GUARD existiert nicht."

# ── F1: Originaler Sentry-Crash-Pattern → rot ────────────────────────────────
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/repo/feature-widgets/src/main/java/x"
git -C "$TMP/repo" init -q
git -C "$TMP/repo" config user.email t@t; git -C "$TMP/repo" config user.name t
cat > "$TMP/repo/feature-widgets/src/main/java/x/W.kt" <<'KOTLIN'
class W {
    companion object {
        val P = Regex("\\{(road|city|country)}")
    }
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
mkdir -p "$TMP/repo/scripts" "$TMP/repo/core/src/main/java/com/vivid/core/startup"
cp "$GUARD" "$TMP/repo/scripts/"
cat > "$TMP/repo/core/src/main/java/com/vivid/core/startup/CrashAdvisory.kt" <<'KOTLIN'
object CrashAdvisoryRegistry {
    // TEXT-INFO-WIDGET-REGEX-ICU
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
if bash "$TMP/repo/scripts/check_icu_regex_braces.sh" >/dev/null 2>&1; then
  fail "F1: Crash-Pattern \\{(road|city|country)} wurde NICHT erkannt."
fi
pass "F1: Originaler Sentry-Crash-Pattern → rot."

# ── F2: Resolver-Form \{(\w+)} → rot ─────────────────────────────────────────
cat > "$TMP/repo/feature-widgets/src/main/java/x/W.kt" <<'KOTLIN'
class W {
    companion object {
        val P = Regex("\\{(\\w+)}")
    }
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
if bash "$TMP/repo/scripts/check_icu_regex_braces.sh" >/dev/null 2>&1; then
  fail "F2: Resolver-Form \\{(\\w+)} wurde NICHT erkannt."
fi
pass "F2: Resolver-Form \\{(\\w+)} → rot."

# ── F3: Maskierte Form + Quantifier → grün ───────────────────────────────────
cat > "$TMP/repo/feature-widgets/src/main/java/x/W.kt" <<'KOTLIN'
class W {
    companion object {
        val P1 = Regex("\\{(road|city|country)\\}")
        val P2 = Regex("""^obsws://([^:/?#]+):(\d{1,5})(?:/(.*))?$""")
        val P3 = Regex("\\b[0-9a-fA-F]{32,}\\b")
    }
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
if ! bash "$TMP/repo/scripts/check_icu_regex_braces.sh" >/dev/null 2>&1; then
  fail "F3: Maskierte Formen/Quantifier fälschlich rot."
fi
pass "F3: Maskierte Form + Quantifier {1,5}/{32,} → grün."

# ── F4: Testcode wird ignoriert ─────────────────────────────────────────────
mkdir -p "$TMP/repo/feature-widgets/src/test/java/x"
cat > "$TMP/repo/feature-widgets/src/test/java/x/WTest.kt" <<'KOTLIN'
class WTest {
    val P = Regex("\\{(\\w+)}") // JVM-Tests: bare brace ok
}
KOTLIN
cat > "$TMP/repo/feature-widgets/src/main/java/x/W.kt" <<'KOTLIN'
class W {
    companion object {
        val P1 = Regex("\\{(road|city|country)\\}")
    }
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
if ! bash "$TMP/repo/scripts/check_icu_regex_braces.sh" >/dev/null 2>&1; then
  fail "F4: Testcode wurde nicht ignoriert."
fi
pass "F4: Testcode (src/test/*Test.kt) wird ignoriert."

# ── F5: Fehlender Registry-Eintrag → rot ─────────────────────────────────────
cat > "$TMP/repo/core/src/main/java/com/vivid/core/startup/CrashAdvisory.kt" <<'KOTLIN'
object CrashAdvisoryRegistry {
    // (leer)
}
KOTLIN
git -C "$TMP/repo" add -A >/dev/null
if bash "$TMP/repo/scripts/check_icu_regex_braces.sh" >/dev/null 2>&1; then
  fail "F5: Fehlender Registry-Eintrag wurde nicht bemerkt."
fi
pass "F5: Fehlender Registry-Eintrag → rot."

# ── F6: Echter Repo-Stand → grün ─────────────────────────────────────────────
if ! bash "$GUARD" >/dev/null 2>&1; then
  fail "F6: Echter Repo-Stand ist rot — gefixte Stellen nicht sauber?"
fi
pass "F6: Echter Repo-Stand → grün (beide Fixstellen maskiert, Registry geführt)."

echo ""
echo "✅ [test-icu-regex] Alle 6 Selbsttests bestanden."
