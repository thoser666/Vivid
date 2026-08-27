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
# Mit `--check-exists` wird zusätzlich geprüft, dass jeder Hash tatsächlich
# existiert und ein Vorfahre von HEAD ist — nicht nur formatgültig. Das
# fängt Rebase-Orphans ab: Vorfall 2026-08-21 verwies der Log-Eintrag nach
# einem Rebase auf `c0fb445`, obwohl der Feature-Commit als `7ae806a`
# neu geschrieben worden war (der alte Hash lag nur noch im Reflog, nicht
# in der Geschichte). In Shallow-Clones fehlen historische Objekte lokal —
# dort wird die Existenzprüfung mit Warnung übersprungen (Format-Check
# bleibt aktiv).
#
# Aufruf:  bash scripts/check_parity_log.sh [--check-exists] [PARITY_DATEI]
#          (Standard: PARITY.md im Repo-Root; für Test-Fixtures überschreibbar)
set -euo pipefail

cd "$(dirname "$0")/.."

CHECK_EXISTS=0
FILE="PARITY.md"
for arg in "$@"; do
  case "$arg" in
    --check-exists) CHECK_EXISTS=1 ;;
    *) FILE="$arg" ;;
  esac
done

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

"$PY" - "$FILE" "$CHECK_EXISTS" <<'PYEOF'
import re
import subprocess
import sys

# UTF-8-Ausgabe erzwingen (Windows-Konsolen nutzen sonst cp1252 und brechen
# an ✅/❌ ab — gleicher Fix wie in check_markdown_anchors.sh).
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

path = sys.argv[1]
check_exists = sys.argv[2] == "1"

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
hashes = []
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
        continue
    hashes.append((date, commit))

# Existenz-/Reachability-Prüfung: jeder Hash muss ein Vorfahre von HEAD sein
# (bzw. HEAD selbst). Ein Hash, der nur noch im Reflog liegt (Rebase-Orphan),
# existiert zwar im Objekt-Store, ist aber kein Vorfahre — genau dieser Fall
# wird hier rot gemeldet. In Shallow-Clones fehlen historische Objekte lokal,
# dort wird die Prüfung mit Warnung übersprungen (Format-Check bleibt aktiv).
if check_exists and hashes:
    shallow = subprocess.run(
        ["git", "rev-parse", "--is-shallow-repository"],
        capture_output=True, text=True,
    )
    if shallow.returncode == 0 and shallow.stdout.strip() == "true":
        print("⚠️ [parity-log] Shallow-Clone erkannt — Hash-Existenzprüfung "
              "übersprungen (Format-Check bleibt aktiv).")
    else:
        for date, h in hashes:
            anc = subprocess.run(
                ["git", "merge-base", "--is-ancestor", h, "HEAD"],
                capture_output=True,
            )
            if anc.returncode == 0:
                continue
            exists = subprocess.run(
                ["git", "cat-file", "-e", h + "^{commit}"],
                capture_output=True,
            )
            if exists.returncode != 0:
                errors.append(
                    f"{date}: Hash '{h}' existiert nicht im Repo — bitte "
                    f"`git rev-parse --short <commit>` eintragen."
                )
            else:
                errors.append(
                    f"{date}: Hash '{h}' ist kein Vorfahre von HEAD — wurde "
                    f"der Commit rebased/orphaned? Bitte auf den tatsächlichen "
                    f"Hash korrigieren."
                )

if errors:
    for e in errors:
        print(f"❌ {path}: {e}")
    print(f"❌ [parity-log] {len(errors)} Log-Eintrag/Einträge ohne gültigen/existierenden Commit-Hash.")
    sys.exit(1)

if check_exists:
    print(f"✅ [parity-log] {len(rows)} Log-Einträge, alle mit gültigem 7-Zeichen-Commit-Hash und existierendem Vorfahren von HEAD.")
else:
    print(f"✅ [parity-log] {len(rows)} Log-Einträge, alle mit gültigem 7-Zeichen-Commit-Hash.")
PYEOF
