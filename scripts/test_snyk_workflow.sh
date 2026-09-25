#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
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
grep -A2 '^  snyk-monitor:' "$file" | grep -q "if: github.event_name == 'schedule'" \
  || fail "snyk-monitor: schedule/dispatch-Bedingung fehlt (Skip darf den Monitor nicht betreffen)"

echo "✅ [snyk-workflow-test] CLI-Migration, JDK, Timeout, SARIF-Guard und Dependabot-Skip sind vorhanden."
