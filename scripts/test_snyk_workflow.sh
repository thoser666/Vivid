#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib_workflow_yaml.sh
file=.github/workflows/security-snyk.yml
fail() { echo "❌ [snyk-workflow-test] $1"; exit 1; }
[[ -s "$file" ]] || fail "Workflow fehlt oder ist leer"

markers=(
  # setup-Action muss SHA-gepinnt sein (keine beweglichen Tags)
  'snyk/actions/setup@9adf32b1121593767fc3c057af55b55db032dc04'
  'snyk test --all-projects'
  '--exclude=build,.gradle'
  'actions/setup-java@'
  "java-version: '17'"
  'timeout-minutes: 20'
  'sarif-file-output=./snyk-results.sarif'
  'hashFiles('
  'security-events: write'
  # Dependabot-Skip (SNYK-0005-Vertrag): Job-Level-Skip für dependabot[bot]
  "if: github.actor != 'dependabot[bot]'"
  # Fork-Skip (PR #230): SNYK_TOKEN fließt nicht in Fork-PR-Runs, die CLI
  # scheitert sonst reproduzierbar — der Job skippt Fork-PRs komplett.
  "(github.event_name != 'pull_request' || github.event.pull_request.head.repo.fork == false)"
)
for marker in "${markers[@]}"; do
  grep -Fq -- "$marker" "$file" || fail "Pflichtmarker fehlt: $marker"
done

if grep -Eq 'snyk/actions/(gradle-jdk17|gradle-jdk21)@' "$file"; then
  fail "abgekündigte Snyk-Gradle-Action wird weiterhin verwendet"
fi

# Dependabot-Skip-Vertrag: Der Monitor darf NICHT vom Actor-Skip betroffen
# sein — das Dashboard-Update bleibt schedule/dispatch-seitig (sonst würde
# der Skip ein echtes Überwachungsloch reißen).
# Struktur, nicht Abstand: war ein `grep -A2`-Fenster auf den Job-Key.
# Jetzt wird die if-Bedingung des Jobs direkt aus dem YAML gelesen und exakt
# verglichen — so faellt auch eine zusaetzlich gesetzte Actor-Bedingung auf.
[[ "$(wf_job_if "$file" snyk-monitor)" == "github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'" ]] \
  || fail "snyk-monitor if muss genau schedule||workflow_dispatch sein (Skip darf den Monitor nicht betreffen) (ist: $(wf_job_if "$file" snyk-monitor))"

# SARIF-Multi-Run-Vertrag (Enforcement 30.09.2026, Run 36667123590): Snyk
# schreibt ein Run-Objekt pro Projekt in EINE Datei; GitHub lehnt mehrere
# Runs derselben Kategorie ab. Der Fixup-Step muss existieren, vor dem
# Upload liegen und je Run eine eigene automationDetails.id vergeben.
grep -Fq 'Make SARIF categories unique per project' "$file" \
  || fail "SARIF-Kategorie-Fixup-Step fehlt (Multi-Run-Upload wird von GitHub abgelehnt)"
grep -Fq 'automationDetails' "$file" \
  || fail "Fixup muss je Run eine eigene automationDetails.id (Kategorie) vergeben"
FIXUP_LINE=$(grep -n 'Make SARIF categories unique per project' "$file" | cut -d: -f1)
UPLOAD_LINE=$(grep -n 'upload-sarif@' "$file" | cut -d: -f1)
[[ -n "$FIXUP_LINE" && -n "$UPLOAD_LINE" && "$FIXUP_LINE" -lt "$UPLOAD_LINE" ]] \
  || fail "SARIF-Fixup muss VOR dem Upload-Schritt liegen"

echo "✅ [snyk-workflow-test] CLI-Migration, JDK, Timeout, SARIF-Guard, Dependabot- und Fork-Skip, SARIF-Kategorie-Fixup sind vorhanden."
