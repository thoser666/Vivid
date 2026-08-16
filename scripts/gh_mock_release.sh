#!/usr/bin/env bash
# Mock gh — simuliert die GitHub Release API fuer den publish_release-
# Regressionstest (scripts/test_publish_release_hardening.sh). Fasst KEINE
# echten Releases an (reines State-JSON in MOCK_GH_STATE_DIR).
set -u
# Python waehlen, das WIRKLICH laeuft: python3 bevorzugen (CI: ubuntu-latest),
# aber der Windows-python3-Store-Stub (Microsoft Store Alias) scheitert beim
# Ausfuehren mit Exit != 0 — dann auf python fallen.
PY=""
for cand in python3 python; do
  if command -v "$cand" >/dev/null 2>&1 && "$cand" -c 'import sys' >/dev/null 2>&1; then
    PY="$cand"
    break
  fi
done
PY="${PY:-python}"; export PY
STATE_DIR="${MOCK_GH_STATE_DIR:?MOCK_GH_STATE_DIR muss gesetzt sein}"
STATE="$STATE_DIR/releases.json"
[ -f "$STATE" ] || echo '[]' > "$STATE"

ACTION="${1:-}"
shift || true

case "$ACTION" in
  release)
    SUB="${1:-}"
    shift || true
    case "$SUB" in
      view)
        TAG="${1:-}"
        OUT=$($PY -c '
import json, sys
try:
    data = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(2)
tag = sys.argv[2]
for r in data:
    if r.get("tagName") == tag:
        print(json.dumps({"isDraft": r.get("isDraft", False), "assets": r.get("assets", [])}))
        sys.exit(0)
sys.exit(1)
' "$STATE" "$TAG" 2>/dev/null)
        code=$?
        if [ "$code" -eq 0 ]; then echo "$OUT"; exit 0; fi
        exit 1
        ;;
      create)
        TAG="${1:-}"
        shift || true
        APK="${1:-}"
        # Transiente-Fehler-Simulation: Zaehler in Datei, damit er ueber mehrere
        # gh-Aufrufe (separate Prozesse) hinweg erhalten bleibt.
        FAILFILE="$STATE_DIR/fail_create"
        if [ -f "$FAILFILE" ]; then
          LEFT=$(cat "$FAILFILE")
          if [ "$LEFT" -gt 0 ]; then
            echo "mock: simulierter transienter Fehler (noch $LEFT Versuche)" >&2
            echo $((LEFT - 1)) > "$FAILFILE"
            exit 1
          fi
        fi
        # Abgebrochener-Run-Simulation: Draft anlegen, dann fehlschlagen.
        if [ -f "$STATE_DIR/draft_then_fail" ]; then
          $PY -c '
import json, sys
data = json.load(open(sys.argv[1]))
data.append({"tagName": sys.argv[2], "isDraft": True, "assets": []})
json.dump(data, open(sys.argv[1], "w"))
' "$STATE" "$TAG"
          echo "mock: DRAFT erstellt, dann Fehler (abgebrochener Run)" >&2
          exit 1
        fi
        $PY -c '
import json, sys
data = json.load(open(sys.argv[1]))
data.append({"tagName": sys.argv[2], "isDraft": False, "assets": [{"name": sys.argv[3].split("/")[-1]}]})
json.dump(data, open(sys.argv[1], "w"))
print("mock: release erstellt " + sys.argv[2])
' "$STATE" "$TAG" "$APK"
        exit $?
        ;;
      delete)
        TAG="${1:-}"
        $PY -c '
import json, sys
data = json.load(open(sys.argv[1]))
before = len(data)
data = [r for r in data if r.get("tagName") != sys.argv[2]]
json.dump(data, open(sys.argv[1], "w"))
print("mock: release geloescht " + sys.argv[2] + (" (gefunden)" if len(data) != before else " (keines)"))
' "$STATE" "$TAG"
        exit $?
        ;;
    esac
    ;;
esac
echo "mock: unbekanntes gh-Kommando: gh $ACTION $*" >&2
exit 1
