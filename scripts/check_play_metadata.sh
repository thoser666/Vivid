#!/usr/bin/env bash
# Prüft die Vollständigkeit der Google-Play-Store-Metadaten
# (fastlane/metadata/android) — als CI-Gate vor einem Play-Upload und als
# laufender Check in android.yml:
#
#   ✅ Icon        : images/icon.png — PNG, exakt 512×512
#   ✅ Screenshots : images/phoneScreenshots/ — ≥ 2 PNG/JPEG, Seitenverhältnis
#                    16:9 ODER 9:16 (Toleranz ±1 %, nur ganzzahlige Pixel-Auflösung)
#   ✅ Store-Listing: pro Locale (en-US Pflicht, alle weiteren konsistent)
#                    title.txt, short_description.txt, full_description.txt —
#                    vorhanden und nicht leer
#   ✅ Changelog   : pro Locale changelogs/default.txt — vorhanden und nicht leer
#
# Bildmaße werden deterministisch per eingebettetem Python-Snapshot geprüft
# (PNG-IHDR / JPEG-SOF-Marker, nur stdlib) — kein ImageMagick/PIL nötig.
#
# Exit-Code 0 = vollständig · 1 = Lücke (hart für CI).
# METADATA_DIR kann auf ein anderes Verzeichnis zeigen (Selbsttest-Fixtures).
set -euo pipefail

# Portabler Python-Aufruf (CI/Ubuntu: python3 · Windows: python bzw. py -3)
PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
# python/python3 können auf den Windows-Store-Alias zeigen (harmloser Fehler
# bei --version) — py (Launcher) ist dann die zuverlässige Wahl.
if ! "$PY" -c 'import struct' >/dev/null 2>&1; then
  if command -v py >/dev/null 2>&1; then PY="py -3"; fi
fi

META="${METADATA_DIR:-fastlane/metadata/android}"
FAIL=0
# Python-Helfer als Temp-Datei (py -3 - würde stdin hängen; Datei ist überall robust)
IMAGE_DIMS_PY="$(cat <<'PYEOF'
import struct, sys

def png_size(data):
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    w, h = struct.unpack('>II', data[16:24])
    return w, h

def jpeg_size(data):
    if data[:2] != b'\xff\xd8':
        return None
    i = 2
    while i < len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        seg_len = struct.unpack('>H', data[i + 2:i + 4])[0]
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            h, w = struct.unpack('>HH', data[i + 5:i + 9])
            return w, h
        i += 2 + seg_len
    return None

path = sys.argv[1]
with open(path, 'rb') as f:
    data = f.read(64 * 1024)
size = png_size(data) or jpeg_size(data)
if size is None:
    print('NOT_IMAGE')
elif png_size(data):
    print(f'PNG {size[0]} {size[1]}')
else:
    print(f'JPEG {size[0]} {size[1]}')
PYEOF
)"
IMAGE_DIMS_TMP="$(mktemp)"
trap 'rm -f "$IMAGE_DIMS_TMP"' EXIT
printf '%s\n' "$IMAGE_DIMS_PY" > "$IMAGE_DIMS_TMP"

image_dims() { # $1 = Datei → "PNG W H" | "JPEG W H" | "NOT_IMAGE"
  # shellcheck disable=SC2086 (PY kann "py -3" mit Argument sein)
  $PY "$IMAGE_DIMS_TMP" "$1" 2>/dev/null || echo "NOT_IMAGE"
}

fail() { # $1 = Meldung
  echo "❌ [play-metadata] $1"
  FAIL=1
}

# ── 1) Icon ──────────────────────────────────────────────────────────────────
ICON="$META/images/icon.png"
if [ ! -f "$ICON" ]; then
  fail "Icon fehlt: images/icon.png"
else
  dims="$(image_dims "$ICON")"
  case "$dims" in
    "PNG 512 512") echo "✅ Icon: images/icon.png (512×512 PNG)" ;;
    "NOT_IMAGE")   fail "Icon ist kein PNG/JPEG: $ICON ($dims)" ;;
    *)             fail "Icon muss 512×512 PNG sein — ist: $dims ($ICON)" ;;
  esac
fi

# ── 2) Screenshots (mind. 2, 16:9 oder 9:16) ────────────────────────────────
SHOT_DIR="$META/images/phoneScreenshots"
if [ ! -d "$SHOT_DIR" ]; then
  fail "Screenshots-Ordner fehlt: images/phoneScreenshots/ (mind. 2, 16:9 oder 9:16)"
else
  shots=()
  while IFS= read -r -d '' f; do shots+=("$f"); done < <(find "$SHOT_DIR" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' \) -print0)
  if [ "${#shots[@]}" -lt 2 ]; then
    fail "Screenshots: nur ${#shots[@]} Bild(er) gefunden — mind. 2 nötig ($SHOT_DIR)"
  else
    echo "✅ Screenshots: ${#shots[@]} Bilder gefunden"
  fi
  for f in "${shots[@]}"; do
    dims="$(image_dims "$f")"
    case "$dims" in
      "NOT_IMAGE") fail "Screenshot ist kein PNG/JPEG: $f" ;;
      *)
        set -- $dims  # PNG|JPEG W H
        local_w="$2"; local_h="$3"
        # Seitenverhältnis 16:9 (1.778) oder 9:16 (0.5625), Toleranz ±1 %
        if [ "$local_w" -eq 0 ] || [ "$local_h" -eq 0 ]; then
          fail "Screenshot mit ungültigen Maßen: $f ($dims)"
        else
          # shellcheck disable=SC2086 (PY kann "py -3" mit Argument sein)
          ratio="$($PY -c "print(round($local_w / $local_h, 4))")"
          ok16="$($PY -c "print(abs($ratio - 16/9) <= 0.01*16/9)")"
          ok916="$($PY -c "print(abs($ratio - 9/16) <= 0.01*9/16)")"
          if [ "$ok16" != "True" ] && [ "$ok916" != "True" ]; then
            fail "Screenshot-Seitenverhältnis: $f ist $dims ($ratio) — nur 16:9 oder 9:16 erlaubt"
          else
            orient="16:9"; [ "$ok916" = "True" ] && orient="9:16"
            echo "✅ Screenshot: $(basename "$f") ($dims, $orient)"
          fi
        fi
        ;;
    esac
  done
fi

# ── 3) Store-Listing + Changelog pro Locale ─────────────────────────────────
locales=()
while IFS= read -r -d '' d; do
  locales+=("$(basename "$d")")
done < <(find "$META" -mindepth 1 -maxdepth 1 -type d -print0 | sort -z)

if [ "${#locales[@]}" -eq 0 ]; then
  fail "Keine Locale-Ordner gefunden (erwartet: en-US, …)"
else
  echo "✅ Locales gefunden: ${locales[*]}"
fi

for loc in "${locales[@]}"; do
  [ "$loc" = "images" ] && continue
  for req in title.txt short_description.txt full_description.txt; do
    file="$META/$loc/$req"
    if [ ! -s "$file" ]; then
      fail "Locale $loc: $req fehlt oder ist leer"
    else
      echo "✅ Locale $loc: $req ($(wc -c < "$file") Bytes)"
    fi
  done
  changelog="$META/$loc/changelogs/default.txt"
  if [ ! -s "$changelog" ]; then
    fail "Locale $loc: changelogs/default.txt fehlt oder ist leer"
  else
    echo "✅ Locale $loc: changelogs/default.txt ($(wc -c < "$changelog") Bytes)"
  fi
done

# en-US ist Pflicht (Play verlangt mindestens Englisch)
if [ ! -d "$META/en-US" ]; then
  fail "Pflicht-Locale en-US fehlt"
fi

# ── Ergebnis ─────────────────────────────────────────────────────────────────
if [ "$FAIL" -eq 1 ]; then
  echo "❌ [play-metadata] Play-Metadaten unvollständig — siehe oben."
  exit 1
fi
echo "✅ [play-metadata] Play-Metadaten vollständig."
