#!/usr/bin/env bash
# Selbsttest für scripts/check_play_metadata.sh:
#   baut ein Wegwerf-Metadaten-Fixture (METADATA_DIR) mit generierten PNGs
#   (Python-stdlib, kein PIL) und prüft:
#     Positiv  : vollständiges Fixture → Exit 0
#     Negativ 1: fehlendes Icon → Exit 1
#     Negativ 2: zu wenige/verkehrte Screenshots → Exit 1
#     Negativ 3: fehlende Locale-Datei → Exit 1
# Läuft im CI (android-ci.yml) und lokal: bash scripts/test_play_metadata.sh
set -euo pipefail

cd "$(dirname "$0")/.."
# Portabler Python-Aufruf (CI/Ubuntu: python3 · Windows: python bzw. py -3)
PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if ! "$PY" -c 'import struct' >/dev/null 2>&1; then
  if command -v py >/dev/null 2>&1; then PY="py -3"; fi
fi
CHECK="bash scripts/check_play_metadata.sh"

# ── PNG-Generator (Python-stdlib): 1×1-farbiges PNG mit W×H ────────────────
# (als Temp-Datei statt stdin — py -3 - würde sonst auf stdin hängen)
PNG_GEN_PY="$(mktemp)"
trap 'rm -rf "$TMP" "$PNG_GEN_PY"' EXIT
cat > "$PNG_GEN_PY" <<'PYEOF'
import struct, sys, zlib
path, w, h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
def chunk(typ, data):
    c = struct.pack('>I', len(data)) + typ + data
    return c + struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)
ihdr = struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)  # 8-bit RGB
raw = b''.join(b'\x00' + b'\x00\x80\x00' * w for _ in range(h))
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(path, 'wb').write(png)
PYEOF

make_png() { # $1 = Pfad, $2 = Breite, $3 = Höhe
  # shellcheck disable=SC2086 (PY kann "py -3" mit Argument sein)
  $PY "$PNG_GEN_PY" "$1" "$2" "$3"
}

TMP="$(mktemp -d)"
META="$TMP/metadata/android"

fail() { echo "❌ FAIL: $1"; exit 1; }
expect_exit() { # $1 = erwarteter Exit, $2 = Beschreibung
  local expected="$1" desc="$2" rc=0
  # Exit-Code einfangen — bei set -e bricht sonst jeder Fehlschlag das Skript ab
  set +e
  METADATA_DIR="$META" README_FILE="$TMP/README.md" \
    bash scripts/check_play_metadata.sh >/dev/null 2>&1
  rc=$?
  set -e
  if [ "$rc" -ne "$expected" ]; then
    fail "$desc (Exit $rc statt $expected)"
  fi
  echo "✅ $desc (Exit $expected)"
}

# ── Fixture: vollständig ─────────────────────────────────────────────────────
build_fixture() { # $1 = images-Ordner, $2 = Anzahl Screenshots, $3 = Fehlende-Locale-Datei (""=keine)
  local images="$1" count="$2" missing="$3" loc
  mkdir -p "$META/images/phoneScreenshots"
  make_png "$images/icon.png" 512 512
  for i in $(seq 1 "$count"); do
    make_png "$images/phoneScreenshots/${i}_en-US.png" 1080 1920   # 9:16, Name wie Play-Metadaten
  done
  for loc in en-US de-DE; do
    mkdir -p "$META/$loc/changelogs"
    [ "$missing" = "title.txt" ] && [ "$loc" = "en-US" ] && continue
    printf 'Vivid' > "$META/$loc/title.txt"
    printf 'Live-Streaming-App' > "$META/$loc/short_description.txt"
    printf 'Beschreibung %s' "$loc" > "$META/$loc/full_description.txt"
    printf 'Neuerungen' > "$META/$loc/changelogs/default.txt"
  done
}

# README-Fixture: referenziert die Fixture-Screenshots (Konsistenz-Guard braucht
# eine README, deren Bildpfade auf den Metadaten-Pfad zeigen).
build_readme() { # $1 = Anzahl referenzierter Screenshots
  local count="$1" i
  {
    echo "# Fixture-README"
    echo ""
    echo "## Screenshots"
    for i in $(seq 1 "$count"); do
      echo "<img src=\"$META/images/phoneScreenshots/${i}_en-US.png\" width=\"250\">"
    done
  } > "$TMP/README.md"
}

# ── Positiv: alles vorhanden → Exit 0 ───────────────────────────────────────
build_fixture "$META/images" 2 ""
build_readme 2
expect_exit 0 "vollständiges Fixture erkannt"

# ── Negativ 1: Icon fehlt ───────────────────────────────────────────────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 ""
build_readme 2
rm "$META/images/icon.png"
expect_exit 1 "fehlendes Icon erkannt"

# ── Negativ 2: nur 1 Screenshot ─────────────────────────────────────────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 1 ""
build_readme 1
expect_exit 1 "zu wenige Screenshots erkannt"

# ── Negativ 3: falsches Seitenverhältnis (4:3 statt 9:16/16:9) ──────────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 ""
build_readme 2
make_png "$META/images/phoneScreenshots/2_en-US.png" 1600 1200   # 4:3
expect_exit 1 "falsches Screenshot-Format erkannt"

# ── Negativ 4: Locale-Datei fehlt ───────────────────────────────────────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 "title.txt"
build_readme 2
expect_exit 1 "fehlende Locale-Datei erkannt"

# ── Negativ 5: Icon falsche Größe ───────────────────────────────────────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 ""
build_readme 2
make_png "$META/images/icon.png" 256 256
expect_exit 1 "Icon mit falscher Größe erkannt"

# ── Negativ 6: README referenziert fehlende Datei (Konsistenz-Guard) ────────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 ""
# README verweist auf 3_en-US.png, das es nicht gibt
{
  echo "# Fixture-README"
  echo "<img src=\"$META/images/phoneScreenshots/1_en-US.png\">"
  echo "<img src=\"$META/images/phoneScreenshots/3_en-US.png\">"
} > "$TMP/README.md"
expect_exit 1 "README-Referenz auf fehlende Datei erkannt"

# ── Negativ 7: Screenshot fehlt in der README-Galerie (Konsistenz-Guard) ────
rm -rf "$TMP"; META="$TMP/metadata/android"
build_fixture "$META/images" 2 ""
build_readme 1   # referenziert nur 1_en-US.png, 2_en-US.png fehlt in der README
expect_exit 1 "in der README vergessener Screenshot erkannt"

echo "✅ check_play_metadata.sh: alle Fälle bestanden (1 Positiv, 7 Negativ)."
