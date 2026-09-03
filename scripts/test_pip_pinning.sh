#!/usr/bin/env bash
# Guard: pip-Installationen in den Deploy-Workflows müssen SHA-256-verifiziert
# sein (Scorecard PinnedDependencies). Regeln:
#   1. Jede `pip install`-Zeile ohne --require-hashes ist verboten.
#   2. Die gepinnten Pakete müssen die erwartete Version + Hash tragen
#      (Schutz gegen versehentliches Downgrade/Manipulation).
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "❌ [pip-pinning-test] $1"; exit 1; }

# 1. Keine pip install-Zeile ohne --require-hashes in den Deploy-Workflows.
for file in .github/workflows/deploy-pages.yml .github/workflows/deploy-fdroid.yml; do
  [[ -s "$file" ]] || fail "Workflow fehlt oder ist leer: $file"
  if grep -E '^\s*(python3 -m )?pip install' "$file" | grep -vq -- '--require-hashes'; then
    fail "Ungepinntes pip install in $file gefunden"
  fi
done

# 2. Erwartete Pins (Version + SHA256 aus PyPI verifiziert).
grep -Fq 'markdown==3.10.3' .github/workflows/deploy-pages.yml \
  || fail "markdown-Pin fehlt in deploy-pages.yml"
grep -Fq 'sha256:fa6c92a00a4a3c98b22728c64a935ae1928250ae65058a6ded814d2cc29a4cea' \
  .github/workflows/deploy-pages.yml \
  || fail "markdown-SHA256 fehlt in deploy-pages.yml"

grep -Fq 'fdroidserver==2.4.5' .github/workflows/deploy-fdroid.yml \
  || fail "fdroidserver-Pin fehlt in deploy-fdroid.yml"
grep -Fq 'sha256:f9b52646264c732678e32e37e23a995db20cc61d45622dda5830ce23255547f4' \
  .github/workflows/deploy-fdroid.yml \
  || fail "fdroidserver-SHA256 fehlt in deploy-fdroid.yml"

# 3. Kein unfixes pip install in irgendeinem anderen Workflow (Stichtagsprüfung
#    gegen neue Vorkommen).
other=$(grep -rlE '^\s*(python3 -m )?pip install' .github/workflows/*.yml \
  | grep -v -e deploy-pages.yml -e deploy-fdroid.yml || true)
if [[ -n "$other" ]]; then
  for f in $other; do
    if grep -E '^\s*(python3 -m )?pip install' "$f" | grep -vq -- '--require-hashes'; then
      fail "Ungepinntes pip install in $f — bitte mit --require-hashes pinnen"
    fi
  done
fi

echo "✅ [pip-pinning-test] pip-Installationen sind SHA-256-verifiziert gepinnt."
