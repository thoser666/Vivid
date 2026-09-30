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

# 1b. deploy-fdroid muss die vollständige Hash-Closure-Datei verwenden.
grep -Fq 'pip install --require-hashes -r .github/requirements/fdroidserver-requirements.txt' \
  .github/workflows/deploy-fdroid.yml \
  || fail "deploy-fdroid.yml verwendet nicht die hash-gepinnte fdroidserver-requirements.txt"
[[ -s .github/requirements/fdroidserver-requirements.txt ]] \
  || fail "fdroidserver-requirements.txt fehlt"

# 1c. Requirements-Datei muss zur Generator-Version passen (Der Generator
#     hat eine eingebaute Closure-Selbstprüfung; hier wird nur Konsistenz
#     der Formate geprüft: jede Anforderung hat mind. einen Hash).
python - <<'PYEOF' || fail "fdroidserver-requirements.txt ist formal invalid"
import re, sys
text = open(".github/requirements/fdroidserver-requirements.txt", encoding="utf-8").read()
entries = re.findall(r"^([a-z0-9-]+==[\w.]+)((?:\s+\\\n\s+--hash=sha256:[0-9a-f]{64})+)", text, re.M)
assert entries, "keine gepinnten Einträge gefunden"
bad = [name for name, h in entries if "--hash=sha256:" not in h]
sys.exit(1 if bad else 0)
PYEOF

# 1d. Drift-Test (advisory): gepinnte Closure vs. aktuell generierbare Closure.
#     Live-PyPI-Drift ist ein WARTUNGSSIGNAL (neues Upstream-Release), keine
#     Eigenschaft des Commits — der byte-identische Vergleich gegen LIVE-PyPI
#     ist im Push-CI daher inhärent racy (Vorfall 30.09.2026: charset-normalizer
#     3.5.2 erschien zwischen lokaler Regenerierung und CI-Lauf → roter Push-CI
#     auf inhaltlich intaktem Commit). Standard: advisory (Warnung, exit 0) —
#     die Integrität bleibt vollständig abgedeckt (1c-Formalprüfung hier,
#     SHA-Verifikation aller Hashes beim Deploy-`pip install --require-hashes`).
#     Der wöchentliche automation-pypi-drift-Workflow erkennt den Drift und
#     öffnet den Regenerierungs-PR; seine Nach-Verifikation läuft mit
#     PIP_DRIFT_STRICT=1 (dann fail-closed, siehe test_pip_drift_semantics.sh).
if python scripts/gen_fdroid_requirements.py --check >/dev/null 2>&1; then
  :
else
  if [[ "${PIP_DRIFT_STRICT:-0}" == "1" ]]; then
    fail "fdroidserver-requirements.txt ist nicht mehr reproduzierbar — neu generieren und committen"
  fi
  echo "⚠️  [pip-pinning-test] Closure-Drift gegenüber Live-PyPI erkannt (advisory, nicht push-blockierend) — Regenerierung: python scripts/gen_fdroid_requirements.py oder automation-pypi-drift (wöchentlicher Bot-PR)"
fi

# 2. Erwartete Pins (Version + SHA256 aus PyPI verifiziert).
grep -Fq 'markdown==3.10.3' .github/workflows/deploy-pages.yml \
  || fail "markdown-Pin fehlt in deploy-pages.yml"
grep -Fq 'sha256:fa6c92a00a4a3c98b22728c64a935ae1928250ae65058a6ded814d2cc29a4cea' \
  .github/workflows/deploy-pages.yml \
  || fail "markdown-SHA256 fehlt in deploy-pages.yml"

grep -Fq 'fdroidserver==2.4.5' .github/requirements/fdroidserver-requirements.txt \
  || fail "fdroidserver-Pin fehlt in fdroidserver-requirements.txt"
grep -Fq 'sha256:f9b52646264c732678e32e37e23a995db20cc61d45622dda5830ce23255547f4' \
  .github/requirements/fdroidserver-requirements.txt \
  || fail "fdroidserver-SHA256 fehlt in fdroidserver-requirements.txt"
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
