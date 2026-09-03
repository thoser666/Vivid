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
)
for marker in "${markers[@]}"; do
  grep -Fq -- "$marker" "$file" || fail "Pflichtmarker fehlt: $marker"
done

if grep -Eq 'snyk/actions/(gradle-jdk17|gradle-jdk21)@' "$file"; then
  fail "abgekündigte Snyk-Gradle-Action wird weiterhin verwendet"
fi

echo "✅ [snyk-workflow-test] CLI-Migration, JDK, Timeout und SARIF-Guard sind vorhanden."
