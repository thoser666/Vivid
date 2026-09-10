#!/usr/bin/env bash
# Regressionstest: Verifizierbare Distributions-Links (Distributions-Quick-Wins)
# ==============================================================================
# Hintergrund: APKs waren bisher nur über die Releases-LISTE ladbar (kein
# permanenter Direkt-Link), und der Nightly-Kanal lieferte keine Prüfsummen —
# nur der Stable-Kanal trug SHA256SUMS.txt. Diese Tests sichern beide
# Verbesserungen strukturell ab:
#   1. Der permanente Permalink releases/latest/download/app-standard-release.apk
#      (GitHub löst ihn automatisch auf das neueste STABLE-Release auf) muss im
#      README verlinkt bleiben — Nav-Zeile, Installations-Sektion, EN-Quickstart.
#   2. Die publish_release-Lane muss in BEIDEN Zweigen (stable + nightly) die
#      Prüfsummendatei an die Release-Assets anhängen — sonst würde ein
#      Refactoring stillschweigend den Nightly-Schutz wieder verlieren.
#
# Geprüfte Szenarien:
#   R1 README-Nav verlinkt den Permalink (nicht mehr nur die Releases-Liste)
#   R2 Installation-Sektion nennt Permalink + `sha256sum -c`-Verifikation
#   R3 EN-Quickstart nennt den Permalink
#   R4 Fastfile: beide Publikations-Zweige hängen die Prüfsummendatei an,
#      der Nightly-Zweig erzeugt sie für das Standard-APK
#
# Läuft im CI (release-pipeline.yml, Job "Self-Test Pinned-Checksums") und
# lokal: bash scripts/test_pinned_checksums.sh  (Exit 0 = grün)
set -euo pipefail
cd "$(dirname "$0")/.."

# Locale pinnen: die R1-Checks greppen Fixed-Strings mit Emoji (📥). Unter
# manchen Locales (z. B. en_US.UTF-8 im Git-Hook-Kontext) matcht grep -F die
# Multibyte-Sequenz nicht — unter C (byteweise) immer. Pinning macht den
# Test deterministisch über interaktive Shell und Pre-Push-Hook hinaus.
export LC_ALL=C

FAIL=0
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}

PERMALINK="releases/latest/download/app-standard-release.apk"

echo "== R1: README-Nav nutzt den permanenten Latest-APK-Permalink =="
check "R1.1 Nav-Download-Ziel ist der Permalink" \
  grep -qF "[📥 Download APK](../../$PERMALINK)" README.md
check "R1.2 Nav-Ziel ist NICHT mehr die bare Releases-Liste" \
  bash -c '! grep -qF "[📥 Download APK](../../releases)" README.md'

echo "== R2: Installation-Sektion erklärt Permalink + Prüfsummen-Verifikation =="
check "R2.1 Permalink in der Installations-Sektion" \
  grep -qF "$PERMALINK" README.md
check "R2.2 sha256sum -c-Verifikation dokumentiert" \
  grep -qF 'sha256sum -c' README.md

echo "== R3: EN-Quickstart =="
check "R3.1 direct download link im EN-Quickstart" \
  grep -qF "$PERMALINK" README.md

echo "== R4: Fastfile hängt Prüfsummen in BEIDEN Zweigen an =="
check "R4.1 genau 2 Prüfsummen-Upload-Anker (stable + nightly)" \
  bash -c '[ "$(grep -cF "options[:checksums] && File.exist?(options[:checksums])" fastlane/Fastfile)" -eq 2 ]'
check "R4.2 Nightly-Zweig erzeugt SHA256SUMS für das Standard-APK" \
  grep -qF 'Sha256sums.write([apk], File.dirname(apk))' fastlane/Fastfile
check "R4.3 Stable-Zweig erzeugt SHA256SUMS für beide Flavor" \
  grep -qF 'Sha256sums.write([apk, foss_apk], File.dirname(apk))' fastlane/Fastfile

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_pinned_checksums.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
