#!/usr/bin/env bash
# Selbsttest für den I18n-Guard (scripts/check_i18n.sh).
#
# Beweist offline mit Wegwerf-Fixtures, dass der Guard greift:
#   F1  Positiv: sauberes Fixture-Modul (alle Strings in Ressourcen) → grün
#   F2  Negativ: hartkodierter UI-String (Text("…")) → rot
#   F3  Negativ: values-en fehlt ein Key → rot
#   F4  Negativ: stream_url_hint ohne Owncast → rot
#   F5  Negativ: values-ru fehlt ein Key → rot
#   F6  Real: der echte Repo-Stand ist grün (Regression)
#   F7  Negativ: deutscher Timber-Log-String (einzeilig) → rot, davor ein
#       Kontrolllauf, der beweist, dass nur dieser String die Rotfärbung trägt
#   F8  Negativ: deutscher Timber-Log-String (mehrzeilig) → rot
set -euo pipefail

cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

FAILED=0
fail() { echo "  ✗ $*"; FAILED=1; }
pass() { echo "  ✓ $*"; }

# ── Fixture-Modul anlegen ────────────────────────────────────────────────
mkdir -p "$TMP/fx/src/main/java/x" "$TMP/fx/src/main/res/values" "$TMP/fx/src/main/res/values-en" "$TMP/fx/src/main/res/values-fr" "$TMP/fx/src/main/res/values-ru"
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
perl -pi -e 's/Beispiel/Example/' "$TMP/fx/src/main/res/values-en/strings.xml"
# Englische Fassung des Hints: bewusst mit „presets“ (klein) — der en-Guard
# ist case-sensitiv, der de-Guard erwartet „Presets“. Das Fixture muss beide
# Varianten abdecken, damit F1 grün ist.
perl -pi -e 's|z. B. Owncast. Die Plattform-Vorlagen oben sind nur Presets.|e.g. Owncast. The platform presets above are just presets.|' "$TMP/fx/src/main/res/values-en/strings.xml"
# Französische Fassung (values-fr ist jetzt Pflicht-Sprache im Guard):
# de-Guard erwartet „Presets“, en-Guard „presets“, fr-Guard „préréglages“.
cp "$TMP/fx/src/main/res/values/strings.xml" "$TMP/fx/src/main/res/values-fr/strings.xml"
perl -pi -e 's/Beispiel/Exemple/' "$TMP/fx/src/main/res/values-fr/strings.xml"
perl -pi -e 's|z. B. Owncast. Die Plattform-Vorlagen oben sind nur Presets.|p. ex. Owncast. Les modèles de plateforme ci-dessus ne sont que des préréglages.|' "$TMP/fx/src/main/res/values-fr/strings.xml"
# Russische Fassung: der Inhalts-Guard erwartet „пресет“.
cp "$TMP/fx/src/main/res/values/strings.xml" "$TMP/fx/src/main/res/values-ru/strings.xml"
perl -pi -e 's/Beispiel/Пример/' "$TMP/fx/src/main/res/values-ru/strings.xml"
perl -pi -e 's|z. B. Owncast. Die Plattform-Vorlagen oben sind nur Presets.|например, Owncast. Шаблоны платформ выше — только пресеты.|' "$TMP/fx/src/main/res/values-ru/strings.xml"

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
perl -pi -e '$_ = "" if /fx_title/' "$TMP/fx/src/main/res/values-en/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F3 sollte rot sein (fx_title fehlt in values-en)"
else
  pass "F3 rot wie erwartet"
fi

echo "▶ [i18n-test] F4: stream_url_hint ohne Owncast → rot"
# values-en wieder vollständig machen, aber Owncast aus dem Hint entfernen
perl -pi -e 's|</resources>|    <string name="fx_title">Example</string>\n</resources>|' "$TMP/fx/src/main/res/values-en/strings.xml"
perl -pi -e 's/e.g. Owncast\./e.g. a self-hosted server./' "$TMP/fx/src/main/res/values-en/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F4 sollte rot sein (Hint ohne Owncast)"
else
  pass "F4 rot wie erwartet"
fi

echo "▶ [i18n-test] F5: fehlende values-ru-Übersetzung → rot"
perl -pi -e '$_ = "" if /fx_title/' "$TMP/fx/src/main/res/values-ru/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F5 sollte rot sein (fx_title fehlt in values-ru)"
else
  pass "F5 rot wie erwartet"
fi

echo "▶ [i18n-test] F7: deutscher Timber-Log-String (einzeilig) → rot"
# F3/F4/F5 hinterlassen bewusste Risse im Fixture. Erst wieder ganz grün
# machen — nur dann beweist die folgende Rotfärbung, dass allein der
# deutsche Log-String sie trägt (Negativkontrolle, AGENTS.md „Mutationen prüfen“).
perl -pi -e 's/e.g. a self-hosted server./e.g. Owncast./' "$TMP/fx/src/main/res/values-en/strings.xml"
perl -pi -e 's|</resources>|    <string name="fx_title">Пример</string>\n</resources>|' "$TMP/fx/src/main/res/values-ru/strings.xml"
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  pass "Kontrolllauf grün (Fixture wieder hergestellt)"
else
  fail "Kontrolllauf sollte grün sein, bevor der Log-Guard getestet wird"
fi
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
fun log() { timber.log.Timber.w("Verbindung fehlgeschlagen - wird erneut versucht") }
EOF
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F7 sollte rot sein (deutscher Timber-String)"
else
  pass "F7 rot wie erwartet"
fi

echo "▶ [i18n-test] F8: deutscher Timber-Log-String (mehrzeilig) → rot"
# Der String steht hier in der Zeile nach dem Aufruf — der Guard darf sich
# nicht auf einzeilige Aufrufe beschränken (Slurp-Lesart, s. check_i18n.sh).
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
fun log(port: Int) {
    timber.log.Timber.w(
        "Port belegt - naechster Versuch",
        port,
    )
}
EOF
if I18N_MODULES="$TMP/fx" I18N_HINT_MODULE="$TMP/fx" bash scripts/check_i18n.sh > /dev/null 2>&1; then
  fail "F8 sollte rot sein (mehrzeiliger deutscher Timber-String)"
else
  pass "F8 rot wie erwartet"
fi

# Fixture aufräumen (F6 prüft den echten Repo-Stand, braucht das Fixture nicht).
cat > "$TMP/fx/src/main/java/x/Dummy.kt" <<'EOF'
package x
EOF

echo "▶ [i18n-test] F6: echter Repo-Stand → grün (Regression)"
if bash scripts/check_i18n.sh > /dev/null 2>&1; then
  pass "F6 grün"
else
  fail "F6 sollte grün sein — Repo hat I18n-Verstöße?"
fi

if [[ "$FAILED" == "1" ]]; then
  echo "❌ [i18n-test] Selbsttest fehlgeschlagen."
  exit 1
fi
echo "✅ [i18n-test] Alle 8 Fälle grün."
