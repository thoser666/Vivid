#!/usr/bin/env bash
# Guard: CONTRIBUTORS.md (Danksagungen Dritter) muss strukturell valide bleiben.
# Vertrag (siehe CONTRIBUTING.md):
#   - genau EINE Beitragstabelle mit Header `| Person | Beitrag | Referenz | Status |`
#   - Datenreihen: 4 Zellen, Person/Beitrag nicht leer, Referenz = `#<Nummer>`
#   (GitHub-Issue/-PR), Status ∈ {offen, umgesetzt}, Referenznummern eindeutig.
#   - Zelltext balanciert: `Person`/`Beitrag` mit paarigen `()`, `[]` und gerader
#   Backtick-Anzahl — Fänger für abgeschnittene Zeilen (#278: halb kopierte
#   Zeile mit offenem Klammerrest, strukturell „valide").
# Offline/deterministisch (kein Netzwerk) — der Selbsttest test_contributors.sh
# beweist die Fälle.
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${CONTRIBUTORS_FILE:-CONTRIBUTORS.md}"
[[ -s "$FILE" ]] || { echo "❌ [contributors-guard] $FILE fehlt oder ist leer"; exit 1; }

python -X utf8 - "$FILE" <<'PYEOF'
import re, sys
path = sys.argv[1]
lines = [l for l in open(path, encoding="utf-8").read().splitlines() if l.strip()]

FOUND_HEADERS = [i for i, l in enumerate(lines)
                 if l.strip().startswith("| Person | Beitrag | Referenz | Status |")]
if len(FOUND_HEADERS) != 1:
    print(f"❌ [contributors-guard] genau EIN Tabellen-Header "
          f"`| Person | Beitrag | Referenz | Status |` erwartet ({len(FOUND_HEADERS)} gefunden)")
    sys.exit(1)
i = FOUND_HEADERS[0]

if i + 1 >= len(lines) or not re.match(r"^\s*\|?\s*-{3,}\s*(\|\s*-{3,}\s*)+-?\s*\|?\s*$",
                                        lines[i + 1].strip()):
    print("❌ [contributors-guard] Tabellen-Trennzeile (--- | --- | …) fehlt oder ist ungültig")
    sys.exit(1)

rows, seen = 0, set()
for line in lines[i + 2:]:
    s = line.strip()
    if not s.startswith("|"):
        break  # Tabelle endet hier
    cells = [c.strip() for c in s.strip().strip("|").split("|")]
    if len(cells) != 4:
        print(f"❌ [contributors-guard] Zeile hat {len(cells)} statt 4 Zellen: {s}")
        sys.exit(1)
    person, beitrag, ref, status = cells
    if not person or not beitrag:
        print(f"❌ [contributors-guard] Person/Beitrag darf nicht leer sein: {s}")
        sys.exit(1)
    for cname, cell in (("Person", person), ("Beitrag", beitrag)):
        for o, c in (("(", ")"), ("[", "]")):
            if cell.count(o) != cell.count(c):
                print(f"❌ [contributors-guard] unbalancierte Klammer {o}{c} in {cname} "
                      f"(Zeile abgeschnitten?): {s}")
                sys.exit(1)
        if cell.count("`") % 2:
            print(f"❌ [contributors-guard] ungerade Anzahl Backticks in {cname} "
                  f"(Zeile abgeschnitten?): {s}")
            sys.exit(1)
    if not re.fullmatch(r"#\d+", ref):
        print(f"❌ [contributors-guard] Referenz muss `#<Nummer>` (GitHub-Issue/-PR) sein, gefunden: {ref}")
        sys.exit(1)
    if status not in ("offen", "umgesetzt"):
        print(f"❌ [contributors-guard] Status muss `offen` oder `umgesetzt` sein, gefunden: {status}")
        sys.exit(1)
    if ref in seen:
        print(f"❌ [contributors-guard] Referenz mehrfach gelistet: {ref}")
        sys.exit(1)
    seen.add(ref)
    rows += 1

if rows == 0:
    print("❌ [contributors-guard] Beitragstabelle enthält keine Datenzeilen")
    sys.exit(1)

print(f"✅ [contributors-guard] CONTRIBUTORS.md valide: {rows} Beitragseintrag/Einträge gepflegt.")
PYEOF