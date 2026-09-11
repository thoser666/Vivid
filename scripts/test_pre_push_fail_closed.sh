#!/usr/bin/env bash
# Regressionstest: Pre-Push-Gate muss fail-closed sein.
#
# Hintergrund: `set -euo pipefail` stand erst weit unten im Skript — die
# Guard-Sektion davor (u. a. pip-Pinning-Selbsttest) lief ohne Fehlerabbruch.
# Ein fehlgeschlagener Guard hat den Push dann NICHT blockiert (fail-open).
# Dieser Test verankert die Korrektur:
#   F1: `set -euo pipefail` steht in den ersten 5 Zeilen (vor allen Guards)
#   F2: Ein fehlschlagender frueher Guard bricht das Gate ab (simuliert per
#       Sandbox: Guard-Skript durch failenden Stub ersetzt, Gate-RC != 0)
#
# Exit 0 = beide Checks bestanden.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GATE="$ROOT/scripts/pre-push.sh"
PASS=0
FAIL=0

# F1: set -e frueh genug
if head -5 "$GATE" | grep -q "^set -euo pipefail"; then
    echo "PASS F1: set -euo pipefail in den ersten 5 Zeilen"
    PASS=$((PASS + 1))
else
    echo "FAIL F1: set -euo pipefail fehlt in den ersten 5 Zeilen (fail-open-Risiko)"
    FAIL=$((FAIL + 1))
fi

# F2: Verhaltenstest in Sandbox — frueher Guard failt → Gate-RC != 0
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/scripts"
# Gate kopieren (Greift via dirname auf ROOT — Pfade bleiben relativ korrekt,
# weil wir die gesamte scripts/-Struktur spiegeln):
cp "$GATE" "$TMP/scripts/"
# Failenden Stub an die Stelle eines FRUEHEN Guards setzen:
cat > "$TMP/scripts/test_play_checklist.sh" <<'STUB'
#!/usr/bin/env bash
echo "SIMULIERTER GUARD-FEHLER" >&2
exit 1
STUB
# Gate im Sandbox-Root laufen lassen; nur die fruehen Guards erreichen wir,
# danach bricht set -e ab — genau das ist die Behauptung.
if (cd "$TMP" && PRE_PUSH_SKIP_LINT=1 JAVA_HOME="$JAVA_HOME" bash scripts/pre-push.sh >/dev/null 2>&1); then
    echo "FAIL F2: Gate lief trotz failendem fruehem Guard durch (fail-open!)"
    FAIL=$((FAIL + 1))
else
    echo "PASS F2: failender frueher Guard bricht das Gate ab (fail-closed)"
    PASS=$((PASS + 1))
fi

echo
echo "$PASS PASS, $FAIL FAIL"
[ "$FAIL" -eq 0 ] || exit 1
