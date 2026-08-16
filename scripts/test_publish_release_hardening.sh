#!/usr/bin/env bash
# Regressionstest: gehärtete stabile Release-Publizierung (publish_release-Lane,
# if-stable-Zweig) lokal gegen einen Mock-gh — berührt KEINE echten Releases.
# Der Harness extrahiert die ECHTE Lane aus fastlane/Fastfile (kein duplizierter
# Code) und simuliert gh release view/create/delete mit einem State-JSON.
#
# Geprüfte Szenarien:
#   S1 frischer Tag        → Release wird erstellt (published + APK-Asset)
#   S2 Re-Run, komplett    → Skip ("already exists and is complete")
#   S3 Draft-Rest          → Delete + Recreate (kein stilles Skipping)
#   S4 transiente Fehler   → Retry (Versuch 1/3, 2/3) bis Erfolg
#   S5 permanenter Fehler  → Rollback NUR des Rest-Releases (Tag bleibt), Run rot
#   S6 Draft + Fehler      → Rollback löscht den Draft, Run rot
#   S7 Tag bleibt immer    → Beweis: stabiler Pfad führt NIE eine Tag-Löschung
#                            aus (Command-Log + statischer Source-Guard)
#
# Läuft im CI (android_fastlane.yml, Job "Self-Test publish_release (Hardening)")
# und lokal: bash scripts/test_publish_release_hardening.sh  (Exit 0 = grün)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT

STATE="$SCRATCH/state"
mkdir -p "$STATE"
export MOCK_GH_STATE_DIR="$STATE"
export MOCK_GH_APK="$SCRATCH/dummy.apk"
printf 'dummy apk content — nur für File.exist?-Check\n' > "$MOCK_GH_APK"

HARNESS="$SCRIPT_DIR/publish_release_harness.rb"

PASS=0
FAIL=0

reset_state() {
  echo '[]' > "$STATE/releases.json"
  rm -f "$STATE/fail_create" "$STATE/draft_then_fail"
}

seed_release() {
  python -c '
import json, sys
data = json.load(open(sys.argv[1]))
data.append(json.loads(sys.argv[2]))
json.dump(data, open(sys.argv[1], "w"))
' "$STATE/releases.json" "$1"
}

state_count() {
  python -c 'import json,sys; print(len(json.load(open(sys.argv[1]))))' "$STATE/releases.json"
}

state_is_draft() {
  python -c '
import json,sys
data = json.load(open(sys.argv[1]))
print("none" if not data else ("true" if data[0].get("isDraft") else "false"))
' "$STATE/releases.json"
}

assert_has() { # name expected_substring
  if grep -qF -- "$2" <<<"$OUT"; then
    echo "PASS: $1"; PASS=$((PASS+1))
  else
    echo "FAIL: $1 — '$2' nicht in Ausgabe"; FAIL=$((FAIL+1))
  fi
}

assert_not_has() { # name unexpected_substring
  if grep -qF -- "$2" <<<"$OUT"; then
    echo "FAIL: $1 — unerwartet '$2'"; FAIL=$((FAIL+1))
  else
    echo "PASS: $1"; PASS=$((PASS+1))
  fi
}

assert_count() { # name expected_count
  actual=$(state_count)
  if [ "$actual" = "$2" ]; then
    echo "PASS: $1 (State=$actual)"; PASS=$((PASS+1))
  else
    echo "FAIL: $1 — State=$actual, erwartet $2"; FAIL=$((FAIL+1))
  fi
}

echo "== S1: frischer Wegwerf-Tag → Release wird erstellt"
reset_state
OUT=$(ruby "$HARNESS" 2>&1) || true
assert_has "S1.1 erstellt" "Publishing GitHub release v9.9.9-test"
assert_has "S1.2 mock create" "mock: release erstellt v9.9.9-test"
assert_has "S1.3 lane ok" "LANE_OK"
assert_count "S1.4 state" 1
[ "$(state_is_draft)" = "false" ] && echo "PASS: S1.5 published" || { echo "FAIL: S1.5 not published"; FAIL=$((FAIL+1)); }

echo "== S2: Re-Run bei komplettem Release → Skip (kein neues Create)"
reset_state
seed_release '{"tagName":"v9.9.9-test","isDraft":false,"assets":[{"name":"app-release.apk"}]}'
OUT=$(ruby "$HARNESS" 2>&1) || true
assert_has "S2.1 skip" "already exists and is complete - skipping"
assert_has "S2.2 lane ok" "LANE_OK"
assert_not_has "S2.3 kein create" "Publishing GitHub release v9.9.9-test"
assert_count "S2.4 state" 1

echo "== S3: Draft-Rest (abgebrochener Run) → Delete + Recreate"
reset_state
seed_release '{"tagName":"v9.9.9-test","isDraft":true,"assets":[]}'
OUT=$(ruby "$HARNESS" 2>&1) || true
assert_has "S3.1 incomplete erkannt" "exists but is incomplete"
assert_has "S3.2 delete" "mock: release geloescht v9.9.9-test (gefunden)"
assert_has "S3.3 recreate" "mock: release erstellt v9.9.9-test"
assert_has "S3.4 lane ok" "LANE_OK"
assert_count "S3.5 state" 1
[ "$(state_is_draft)" = "false" ] && echo "PASS: S3.6 published" || { echo "FAIL: S3.6 not published"; FAIL=$((FAIL+1)); }

echo "== S4: transiente Fehler → Retry bis Erfolg"
reset_state
echo 2 > "$STATE/fail_create"
OUT=$(ruby "$HARNESS" 2>&1) || true
assert_has "S4.1 versuch 1" "Versuch 1/3"
assert_has "S4.2 versuch 2" "Versuch 2/3"
assert_has "S4.3 lane ok" "LANE_OK"
assert_not_has "S4.4 kein 3. Versuch" "Versuch 3/3"
assert_count "S4.5 state" 1

echo "== S5: permanenter Fehler → Rollback (nur Rest-Release, Tag bleibt), Run rot"
reset_state
echo 5 > "$STATE/fail_create"
OUT=$(ruby "$HARNESS" 2>&1) || true
# Hinweis: "Versuch 3/3" wird per Design nie gedruckt — die Retry-Meldung
# erscheint nur bei attempts < 3 (Versuch 1 und 2); nach dem 3. Fehlschlag
# geht es direkt in den Rollback. "Versuch 2/3" beweist: 3 Versuche gemacht.
assert_has "S5.1 3 Versuche (letzte Retry-Meldung 2/3)" "Versuch 2/3"
assert_has "S5.2 endgültig" "endgültig fehlgeschlagen"
assert_has "S5.3 rollback-msg" "Rest-Release v9.9.9-test gelöscht"
assert_not_has "S5.4 kein lane ok" "LANE_OK"
assert_count "S5.6 state leer" 0

echo "== S6: Draft-Rest + permanenter Fehler → Rollback loescht den Draft"
reset_state
touch "$STATE/draft_then_fail"
OUT=$(ruby "$HARNESS" 2>&1) || true
assert_has "S6.1 3x draft-fehler" "DRAFT erstellt, dann Fehler"
assert_has "S6.2 endgültig" "endgültig fehlgeschlagen"
assert_has "S6.3 draft geloescht" "Rest-Release v9.9.9-test gelöscht"
assert_not_has "S6.4 kein lane ok" "LANE_OK"
assert_count "S6.6 state leer (Draft weg)" 0

echo "== S7: stabiler Pfad fuehrt NIE eine Tag-Loeschung aus (Rollback-Szenario)"
reset_state
echo 5 > "$STATE/fail_create"
OUT=$(ruby "$HARNESS" 2>&1) || true
# Command-Log des Harness (Zeilen "[cmdlog] ...") = die WIRKLICH ausgeführten
# Kommandos. Eine Tag-Löschung wäre "git push origin :refs/tags/..." oder
# "git tag -d ..." — beides darf im stabilen Pfad nie auftauchen.
assert_not_has "S7.1 kein refs/tags-Push im cmdlog" ":refs/tags/"
assert_not_has "S7.2 kein git tag -d im cmdlog" "tag -d"
# Positivkontrolle: der Rollback (gh release delete) MUSS im cmdlog stehen —
# beweist, dass das Log vollständig ist und nicht nur leer daherkommt.
assert_has "S7.3 Rollback-Delete im cmdlog (Positivkontrolle)" "[cmdlog] gh release delete v9.9.9-test --yes"
assert_has "S7.4 Create-Versuche im cmdlog" "[cmdlog] gh release create v9.9.9-test"
assert_not_has "S7.5 kein lane ok" "LANE_OK"
assert_count "S7.6 state leer" 0

echo
echo "=========================================="
echo "ERGEBNIS: $PASS PASS, $FAIL FAIL"
[ "$FAIL" -eq 0 ] && echo "✅ ALLE SZENARIEN GRÜN" || echo "❌ FEHLER IM TEST"
exit "$FAIL"
