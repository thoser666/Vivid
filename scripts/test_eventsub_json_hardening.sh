#!/usr/bin/env bash
# Selbsttest: EventSub-JSON-Härtungs-Guard (scripts/check_eventsub_json_hardening.sh)
# — Fixtures offline, kein Netz, kein Gradle.
#
#   E1 gehärtete Instanz                 → grün, exit 0
#   E2 Instanz ohne die Option           → rot, exit 1
#   E3 suppressionsmarkiertes Opt-out    → grün (SUPPRESSED-Ausweis), exit 0
#   E4 mehrzeilige Instanz               → erkannt, rot
#   E5 KDoc-/Kommentarzeile mit „Json {" → ignoriert (kein False Positive)
#   E6 gemischte Datei (2 ok + 1 bad)    → rot, exakt 1 Verstoß
#   E7 leeres Verzeichnis                → grün, 0 Instanzen
#   E8 fehlendes Verzeichnis             → rot
#
# Nutzung: bash scripts/test_eventsub_json_hardening.sh   (Exit 0 = alle grün)
set -euo pipefail

cd "$(dirname "$0")/.."

GUARD="scripts/check_eventsub_json_hardening.sh"
fail() { echo "❌ [test-eventsub-json] $1"; exit 1; }
pass() { echo "✅ [test-eventsub-json] $1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ── E1: gehärtete Instanz → grün ─────────────────────────────────────────────
mkdir -p "$TMP/e1/src"
cat >"$TMP/e1/src/A.kt" <<'EOF'
package test

private val json = Json { ignoreUnknownKeys = true }
EOF
OUT=$(bash "$GUARD" "$TMP/e1/src") || fail "E1: gehärtete Instanz muss exit 0 liefern (bekam $?)."
echo "$OUT" | grep -q "1 Json-Instanziierung(en) geprüft" || fail "E1: Zählung fehlt in: $OUT"
pass "E1: gehärtete Instanz → grün, 1 Instanz gezählt."

# ── E2: Instanz ohne die Option → rot ────────────────────────────────────────
mkdir -p "$TMP/e2/src"
cat >"$TMP/e2/src/A.kt" <<'EOF'
package test

private val json = Json { isLenient = true }
EOF
rc=0
bash "$GUARD" "$TMP/e2/src" >"$TMP/e2.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "E2: Instanz ohne ignoreUnknownKeys muss exit 1 liefern."; fi
grep -q "Json { … } ohne ignoreUnknownKeys" "$TMP/e2.out" || fail "E2: Verstoß-Zeile fehlt: $(cat "$TMP/e2.out")"
pass "E2: fehlende Option → rot mit Verstoß-Zeile."

# ── E3: suppressionsmarkiertes Opt-out → grün ────────────────────────────────
mkdir -p "$TMP/e3/src"
cat >"$TMP/e3/src/A.kt" <<'EOF'
package test

@Suppress("EventSubJsonIgnoreUnknownKeys")
private val json = Json { isLenient = true }
EOF
OUT=$(bash "$GUARD" "$TMP/e3/src") || fail "E3: Suppress-Markierung muss grün bleiben (bekam $?)."
echo "$OUT" | grep -q "SUPPRESSED" || fail "E3: SUPPRESSED-Ausweis fehlt in: $OUT"
pass "E3: suppressionsmarkiertes Opt-out → grün mit SUPPRESSED-Ausweis."

# ── E4: mehrzeilige Instanz → erkannt ────────────────────────────────────────
mkdir -p "$TMP/e4/src"
cat >"$TMP/e4/src/A.kt" <<'EOF'
package test

private val json = Json {
    isLenient = true
    encodeDefaults = true
}
EOF
rc=0
bash "$GUARD" "$TMP/e4/src" >"$TMP/e4.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "E4: mehrzeilige Instanz ohne Option muss rot sein."; fi
pass "E4: mehrzeilige Instanz → erkannt, rot."

# ── E5: Kommentarzeile mit „Json {" → kein False Positive ────────────────────
mkdir -p "$TMP/e5/src"
cat >"$TMP/e5/src/A.kt" <<'EOF'
package test

/**
 * Json { ignoreUnknownKeys = false } nur als Prosabeispiel im KDoc.
 */
// Json { ist auch in einer Kommentarzeile kein Code.
private val json = Json { ignoreUnknownKeys = true }
EOF
OUT=$(bash "$GUARD" "$TMP/e5/src") || fail "E5: Kommentar-Zeilen dürfen nicht zählen (bekam $?)."
echo "$OUT" | grep -q "1 Json-Instanziierung(en) geprüft" || fail "E5: Nur die echte Instanz darf zählen: $OUT"
pass "E5: KDoc-/Kommentarzeilen werden ignoriert."

# ── E6: gemischte Datei (2 ok + 1 bad) → rot, exakt 1 Verstoß ────────────────
mkdir -p "$TMP/e6/src"
cat >"$TMP/e6/src/A.kt" <<'EOF'
package test

private val a = Json { ignoreUnknownKeys = true }

private val b = Json { ignoreUnknownKeys = true; isLenient = true }

private val c = Json { encodeDefaults = true }
EOF
rc=0
bash "$GUARD" "$TMP/e6/src" >"$TMP/e6.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "E6: eine schlechte Instanz unter guten muss rot sein."; fi
grep -q "1 Json-Instanziierung(en) ohne" "$TMP/e6.out" || fail "E6: Fehlermeldung muss exakt 1 Verstoß nennen: $(cat "$TMP/e6.out")"
pass "E6: gemischte Datei → rot, exakt der eine Verstoß."

# ── E7: leeres Verzeichnis → grün, 0 Instanzen ───────────────────────────────
mkdir -p "$TMP/e7/src"
OUT=$(bash "$GUARD" "$TMP/e7/src") || fail "E7: leeres Verzeichnis muss grün sein (bekam $?)."
echo "$OUT" | grep -q "0 Json-Instanziierung(en) geprüft" || fail "E7: Zählung 0 fehlt: $OUT"
pass "E7: leeres Verzeichnis → grün, 0 Instanzen."

# ── E8: fehlendes Verzeichnis → rot ──────────────────────────────────────────
rc=0
bash "$GUARD" "$TMP/does-not-exist" >"$TMP/e8.out" 2>&1 || rc=$?
if [ "$rc" -eq 0 ]; then fail "E8: fehlendes Verzeichnis muss rot sein."; fi
pass "E8: fehlendes Verzeichnis → rot."

# ── E9: echter Baum (feature-chat) bleibt gehärtet ───────────────────────────
OUT=$(bash "$GUARD") || fail "E9: der echte feature-chat-Baum muss grün sein (bekam $?)."
pass "E9: feature-chat-Produktionscode → alle Instanzen gehärtet ($OUT)."

echo ""
echo "✅ [test-eventsub-json] Alle 9 Fälle grün."
