#!/usr/bin/env bash
# Selbsttest für scripts/check_apk_size.sh.
#
# Prüft das Verhalten, nicht die Existenz von Zeilen: erzeugt sparse APK-
# Attrappen exakter Größe (Python-stdlib, kein dd-Schreiben von 30 MB) und
# kontrolliert den Exit-Code in fünf Fällen.
#
#   1 Positiv : 29 MiB bei Limit 30    → Exit 0
#   2 Grenzfall: exakt 30 MiB          → Exit 0 (Limit ist inklusiv)
#   3 Negativ : 31 MiB bei Limit 30    → Exit 1
#   4 Negativ : fehlende Datei         → Exit 2 (kein stiller Pass)
#   5 Negativ : Limit per Env strenger → Exit 1
set -euo pipefail

cd "$(dirname "$0")/.."
PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if ! "$PY" -c 'import sys' >/dev/null 2>&1; then
  if command -v py >/dev/null 2>&1; then PY="py -3"; fi
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "❌ FAIL: $1"; exit 1; }

make_sparse() { # $1 = Pfad, $2 = Bytes
  # shellcheck disable=SC2086 (PY kann "py -3" mit Argument sein)
  $PY -c 'import sys;p=sys.argv[1];open(p,"wb").truncate(int(sys.argv[2]))' "$1" "$2"
}

expect_exit() { # $1 = erwarteter Exit, $2 = Beschreibung, $3 = APK-Pfad, $4 = optionales Limit
  local expected="$1" desc="$2" apk="$3" limit="${4:-}" rc=0
  set +e
  if [ -n "$limit" ]; then
    APK_LIMIT_MB="$limit" bash scripts/check_apk_size.sh "$apk" >/dev/null 2>&1
  else
    bash scripts/check_apk_size.sh "$apk" >/dev/null 2>&1
  fi
  rc=$?
  set -e
  if [ "$rc" -ne "$expected" ]; then
    fail "$desc (Exit $rc statt $expected)"
  fi
  echo "✅ $desc (Exit $expected)"
}

MIB=1048576
make_sparse "$TMP/small.apk"  $(( 29 * MIB ))
make_sparse "$TMP/edge.apk"   $(( 30 * MIB ))
make_sparse "$TMP/large.apk"  $(( 31 * MIB ))

expect_exit 0 "29 MiB bei Limit 30 erkannt"            "$TMP/small.apk"
expect_exit 0 "exakt 30 MiB liegt noch im Limit"       "$TMP/edge.apk"
expect_exit 1 "31 MiB über Limit erkannt"              "$TMP/large.apk"
expect_exit 2 "fehlende APK ist Aufruffehler (Exit 2)" "$TMP/does-not-exist.apk"
expect_exit 1 "strengeres Limit per APK_LIMIT_MB"      "$TMP/edge.apk" 25

# Ohne Argument muss ebenfalls Exit 2 kommen (kein stiller Pass).
rc=0
set +e
bash scripts/check_apk_size.sh >/dev/null 2>&1
rc=$?
set -e
[ "$rc" -eq 2 ] || fail "Aufruf ohne Argument (Exit $rc statt 2)"
echo "✅ Aufruf ohne Argument ist Aufruffehler (Exit 2)"

echo "✅ check_apk_size.sh: alle Fälle bestanden (2 Positiv, 4 Negativ)."
