#!/usr/bin/env bash
# Check: validiert den Aktualisierungslog in PARITY.md — jeder Eintrag muss
# in der Commit-Spalte einen gültigen 7-Zeichen-Git-Hash (hex) tragen statt
# des Platzhalters „—". Damit bleibt der Log nachvollziehbar (welcher Commit
# hat die Änderung tatsächlich eingebracht?) und Platzhalter-Einträge wie
# beim I18n-Abschluss (Vorfall 2026-08-20, war „—" statt `cee9141`) werden
# strukturell verhindert.
#
# Geprüft wird: alle Zeilen der Tabelle „## 🔄 Aktualisierungslog" im
# Format `| DATUM | HASH | ÄNDERUNG |`. HASH muss exakt 7 Zeichen lang und
# hexadezimal (0-9a-f, kleingeschrieben wie `git rev-parse --short`) sein.
# Neueste Einträge stehen oben — nur der Kopf (Überschrift, Spaltentrenner)
# wird übersprungen.
#
# Aufruf:  bash scripts/check_parity_log.sh [PARITY_DATEI]
#          (Standard: PARITY.md im Repo-Root; für Test-Fixtures überschreibbar)
set -euo pipefail

cd "$(dirname "$0")/.."
FILE="${1:-PARITY.md}"

if [[ ! -f "$FILE" ]]; then
  echo "❌ [parity-log] $FILE fehlt."
  exit 1
fi

# Python-Interpreter finden: `command -v` allein reicht nicht — die
# Windows-Store-Alias-Stubs (python3.exe) melden sich als vorhanden,
# brechen aber erst beim Aufruf mit Exit 49 ab. Deshalb wird der Kandidat
# tatsächlich ausgeführt (stderr verworfen, da die Aliase dort die
# Store-Werbung ausgeben).
PY=""
for cand in python3 python "py -3"; do
  if $cand -c 'pass' >/dev/null 2>&1; then
    PY="$cand"
    break
  fi
done
if [[ -z "$PY" ]]; then
  echo "❌ [parity-log] Kein Python-Interpreter gefunden (python3/python/py -3)."
  exit 1
fi

"$PY" - "$FILE" <<'PYEOF'
import re
import sys

# UTF-8-Ausgabe erzwingen (Windows-Konsolen nutzen sonst cp1252 und brechen
# an ✅/❌ ab — gleicher Fix wie in check_markdown_anchors.sh).
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

path = sys.argv[1]
with open(path, encoding="utf-8", errors="replace") as fh:
    lines = fh.read().splitlines()

# Nur die Zeilen der Tabelle „## 🔄 Aktualisierungslog" prüfen.
in_log = False
rows = []
for line in lines:
    if line.startswith("## ") and "Aktualisierungslog" in line:
        in_log = True
        continue
    if in_log and line.startswith("## "):
        break
    if in_log and re.match(r"^\|\s*2026-", line):
        rows.append(line)

errors = []
for row in rows:
    cells = [c.strip() for c in row.strip().strip("|").split("|")]
    if len(cells) < 3:
        errors.append(f"Zeile ohne 3 Spalten: {row[:80]}")
        continue
    date, commit, _rest = cells[0], cells[1], cells[2]
    # Backticks (wie in der echten PARITY.md: | 2026-08-20 | `cee9141` | …)
    # sind Teil der Markdown-Formatierung, nicht des Hashes.
    commit = commit.strip("`")
    if not re.fullmatch(r"[0-9a-f]{7}", commit):
        errors.append(
            f"{date}: Commit-Spalte '{commit}' ist kein 7-Zeichen-Git-Hash "
            f"(0-9a-f) — bitte `git rev-parse --short <commit>` eintragen."
        )

if errors:
    for e in errors:
        print(f"❌ {path}: {e}")
    print(f"❌ [parity-log] {len(errors)} Log-Eintrag/Einträge ohne gültigen Commit-Hash.")
    sys.exit(1)

print(f"✅ [parity-log] {len(rows)} Log-Einträge, alle mit gültigem 7-Zeichen-Commit-Hash.")
PYEOF
