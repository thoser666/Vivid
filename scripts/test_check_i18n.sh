#!/usr/bin/env bash
# Selbsttest für den I18n-Guard (scripts/check_i18n.sh).
#
# Beweist offline mit Wegwerf-Fixtures, dass der Guard greift:
#   F1  Positiv: sauberes Fixture-Modul (alle Strings in Ressourcen) → grün
#   F2  Negativ: hartkodierter UI-String (Text("…")) → rot
#   F3  Negativ: values-en fehlt ein Key → rot
#   F4  Negativ: stream_url_hint ohne Owncast → rot
#   F5  Real: der echte Repo-Stand ist grün (Regression)
set -euo pipefail

cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILED=0
fail() { echo "  ✗ $*"; FAILED=1; }
pass() { echo "  ✓ $*"; }

# ── Fixture-Modul anlegen ────────────────────────────────────────────────
mkdir -p "$TMP/fx/src/main/java/x" "$TMP/fx/src/main/res/values" "$TMP/fx/src/main/res/values-en"
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
EOF
cat > "$TMP/fx/src/main/res/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="fx_title">Beispiel</string>
    <string name="stream_url_hint">Freie Ingest-URL: akzeptiert beliebige RTMP(S)- oder SRT-Ziele, z. B. Owncast. Die Plattform-Vorlagen oben sind nur Presets.</string>
</resources>
EOF
cp "$TMP/fx/src/main/res/values/strings.xml" "$TMP/fx/src/main/res/values-en/strings.xml"
sed -i 's/Beispiel/Example/' "$TMP/fx/src/main/res/values-en/strings.xml"
# Englische Fassung des Hints: bewusst mit „presets“ (klein) — der en-Guard
# ist case-sensitiv, der de-Guard erwartet „Presets“. Das Fixture muss beide
# Varianten abdecken, damit F1 grün ist.
sed -i 's|z. B. Owncast. Die Plattform-Vorlagen oben sind nur Presets.|e.g. Owncast. The platform presets above are just presets.|' "$TMP/fx/src/main/res/values-en/strings.xml"

echo "▶ [i18n-test] F1: sauberes Fixture → grün"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  pass "F1 grün"
else
  fail "F1 sollte grün sein"
fi

echo "▶ [i18n-test] F2: hartkodierter UI-String → rot"
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
import androidx.compose.material3.Text
@androidx.compose.runtime.Composable
fun Foo() { Text("Hallo Welt") }
EOF
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F2 sollte rot sein (Text(\"…\")-Literal)"
else
  pass "F2 rot wie erwartet"
fi

echo "▶ [i18n-test] F3: fehlende values-en-Übersetzung → rot"
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
EOF
sed -i '/fx_title/d' "$TMP/fx/src/main/res/values-en/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F3 sollte rot sein (fx_title fehlt in values-en)"
else
  pass "F3 rot wie erwartet"
fi

echo "▶ [i18n-test] F4: stream_url_hint ohne Owncast → rot"
# values-en wieder vollständig machen, aber Owncast aus dem Hint entfernen
sed -i 's|</resources>|    <string name="fx_title">Example</string>\n</resources>|' "$TMP/fx/src/main/res/values-en/strings.xml"
sed -i 's/e.g. Owncast\./e.g. a self-hosted server./' "$TMP/fx/src/main/res/values-en/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F4 sollte rot sein (Hint ohne Owncast)"
else
  pass "F4 rot wie erwartet"
fi

echo "▶ [i18n-test] F5: echter Repo-Stand → grün (Regression)"
if bash scripts/check_i18n.sh > /dev/null 2>&1; then
  pass "F5 grün"
else
  fail "F5 sollte grün sein — Repo hat I18n-Verstöße?"
fi

if [[ "$FAILED" == "1" ]]; then
  echo "❌ [i18n-test] Selbsttest fehlgeschlagen."
  exit 1
fi
echo "✅ [i18n-test] Alle 5 Fälle grün."
