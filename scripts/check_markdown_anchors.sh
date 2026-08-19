#!/usr/bin/env bash
# Check: validiert alle internen Markdown-Anker im Repo gegen die Ziel-Dateien.
# Läuft im CI (android.yml) und lokal — Exit-Code 0 = sauber, 1 = toter Anker.
#
# Was geprüft wird (externe URLs und GitHub-UI-Links werden übersprungen):
#   1) [x](#anker)          → Anker muss in DERSELBEN Datei existieren
#   2) [x](datei.md#anker)  → Ziel-Datei muss existieren UND den Anker haben
#   3) [x](datei.md)        → Ziel-Datei muss existieren
#   4) [x](bild.png|svg|toml|…) → Nicht-md-Ziele müssen existieren (fängt
#      kaputte README-Galerie-Bilder, fehlende SVGs, toml usw. ab)
#
# Anker-Berechnung = deterministischer GitHub-Algorithmus (identisch zu dem,
# was GitHub beim Rendern erzeugt — empirisch verifiziert):
#   Kleinbuchstaben, Markdown-Formatierung (*, `, ~) entfernen, dann alles
#   außer \w/Hyphen/Leerzeichen entfernen (Umlaute bleiben), Leerzeichen → '-'.
# Beispiel: "### 💬 Roadmap-Bucket: Multi-Plattform-Chat (Kick, YouTube, SOOP)"
#   → "-roadmap-bucket-multi-plattform-chat-kick-youtube-soop"
#
# Aufruf:  bash scripts/check_markdown_anchors.sh [ROOT]   (ROOT = Verzeichnis,
#           Standard: Repo-Root; für Test-Fixtures überschreibbar)
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="${1:-.}"

PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if ! "$PY" -c 'import re' >/dev/null 2>&1; then
  if command -v py >/dev/null 2>&1; then PY="py -3"; fi
fi

CHECK_PY="$(mktemp)"
trap 'rm -f "$CHECK_PY"' EXIT
cat > "$CHECK_PY" <<'PYEOF'
import os, re, sys, unicodedata

# UTF-8-Ausgabe erzwingen (Windows-Konsolen nutzen sonst cp1252 und brechen
# an ✅/❌ ab — vgl. Vorfall im Projekt beim PNG-Verifikationsskript)
try:
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass

# Wortzeichen wie Rubys \p{Word} (das GitHub nutzt): L/N/Pc/M-Kategorien.
# WICHTIG: Python-\w entfernt combining marks (z. B. U+FE0F), Rubys \p{Word}
# behält sie — GitHub-Anker wie „️-roadmap“ (U+FE0F + "-roadmap") für
# „## 🛣️ Roadmap“ enthalten den Variation-Selector (empirisch verifiziert).
def is_word_char(ch):
    if ch in '- ':
        return True
    cat = unicodedata.category(ch)
    return cat[0] in 'LN' or cat in ('Pc', 'Mn', 'Mc', 'Me')

# Deterministischer GitHub-Anker (empirisch gegen gerenderte Seiten verifiziert)
def anchor(header):
    h = re.sub(r'^#+\s*', '', header)          # ATX-# am Anfang
    h = re.sub(r'\s*#+\s*$', '', h).strip()    # schließende # (ATX)
    h = h.replace('*', '').replace('`', '').replace('~', '')  # Markdown-Formatierung
    h = h.lower()
    h = ''.join(c for c in h if is_word_char(c))
    return h.replace(' ', '-')

JUNK_DIRS = {'.git', 'build', '.gradle', 'node_modules', '.idea', 'caches', '.freebuff'}

def md_files(root):
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in JUNK_DIRS]
        for f in filenames:
            if f.lower().endswith('.md'):
                out.append(os.path.join(dirpath, f))
    return sorted(out)

def parse(path):
    """Liefert (header-anchors, link-targets) einer Datei — Codeblöcke übersprungen."""
    headers, links = [], []
    in_code = False
    try:
        with open(path, encoding='utf-8', errors='replace') as fh:
            for line in fh:
                s = line.rstrip('\n').strip()
                if s.startswith('```'):
                    in_code = not in_code
                    continue
                if in_code:
                    continue
                m = re.match(r'^(#{1,6})\s+(.+)$', s)
                if m:
                    headers.append(anchor(s))
                for lm in re.finditer(r'\[[^\]]*\]\(([^)\s]+)\)', s):
                    links.append(lm.group(1))
    except OSError as e:
        print(f"❌ {path}: nicht lesbar ({e})")
        sys.exit(1)
    return headers, links

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else '.'
    files = md_files(root)
    errors = []
    for path in files:
        headers, links = parse(path)
        header_set = set(headers)
        base = os.path.dirname(path)
        for target in links:
            if target.startswith(('http://', 'https://', 'mailto:', 'tel:', 'data:')):
                continue
            if target.startswith('#'):
                ref = target[1:]
                if ref and ref not in header_set:
                    errors.append(f"{path}: toter Anker '{target}' (kein Header in derselben Datei)")
                continue
            filepart, _, anch = target.partition('#')
            # GitHub-UI-Links (issues/new?template=…, absolute /-Pfade) sind keine
            # lokalen Dateien — überspringen (Query-Strings enden oft auf .md)
            if '?' in filepart or filepart.startswith('/') or not filepart:
                continue
            dest = os.path.normpath(os.path.join(base, filepart))
            # Relative GitHub-UI-Links (../../issues, ../../releases, …) verlassen
            # den Repo-Root und werden von GitHub zur Repo-Homepage aufgelöst —
            # keine lokalen Dateien, überspringen
            root_abs, dest_abs = os.path.abspath(root), os.path.abspath(dest)
            if root_abs != dest_abs and not dest_abs.startswith(root_abs + os.sep):
                continue
            is_md = filepart.lower().endswith('.md')
            # Existenzprüfung für ALLE Ziele (md: Datei; Bilder/SVG/toml/…: Datei
            # oder Verzeichnis) — kaputte Bild-/Asset-Links brechen so die CI
            if (os.path.isfile(dest) if is_md else os.path.exists(dest)) is False:
                errors.append(f"{path}: Ziel-Datei fehlt: '{filepart}'")
                continue
            if is_md and anch:
                dheaders, _ = parse(dest)
                if anch not in set(dheaders):
                    errors.append(f"{path}: toter Anker '{target}' in {filepart}")
    if errors:
        for e in errors:
            print("❌", e)
        print(f"❌ [markdown-anchors] {len(errors)} tote Anker/fehlende Ziele in {len(files)} Dateien.")
        sys.exit(1)
    print(f"✅ [markdown-anchors] {len(files)} Markdown-Dateien, alle internen Anker gültig.")

main()
PYEOF

# shellcheck disable=SC2086  # PY kann "py -3" mit Argument sein
$PY "$CHECK_PY" "$ROOT"
