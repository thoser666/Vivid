#!/usr/bin/env bash
# Staged die Rebuild-Artefakte des Verify-Jobs für den Vergleich.
#
# Aufruf: bash scripts/stage_repro_artifacts.sh <ziel-verzeichnis>
#
# Notwendig, weil der Job ZWEIMAL baut: Build B leert den :app-Build-Baum
# (`:app:clean`), die Artefakte von Build A müssen also vorher wegkopiert sein.
# Die Namen sind die Release-Asset-Namen — genau die, die
# scripts/compare_reproducibility.sh in jedem Verzeichnis erwartet.
set -euo pipefail
cd "$(dirname "$0")/.."

target="${1:-}"
if [[ -z "$target" ]]; then
  echo "::error::Aufruf: $0 <ziel-verzeichnis> (z. B. rebuild-a)" >&2
  exit 2
fi

mkdir -p "$target"
cp app/build/outputs/apk/standard/release/app-standard-release.apk "$target/"
cp app/build/outputs/apk/standard/release/output-metadata.json "$target/"
cp app/build/outputs/mapping/standardRelease/mapping.txt "$target/"

aab="app/build/outputs/bundle/standardPlayRelease/app-standard-playRelease.aab"
if [[ -f "$aab" ]]; then
  cp "$aab" "$target/"
  echo "AAB mitgestaged (app-standard-playRelease.aab)"
else
  echo "Kein AAB gebaut — AAB-Vergleich für dieses Verzeichnis übersprungen"
fi

echo "Artefakte für den Vergleich bereit: $target/"
