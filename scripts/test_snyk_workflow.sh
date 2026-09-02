#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
file=.github/workflows/security-snyk.yml
fail() { echo "❌ [snyk-workflow-test] $1"; exit 1; }
[[ -s "$file" ]] || fail "Workflow fehlt oder ist leer"

for marker in \
  'snyk/actions/setup@v1.0.0' \
  'snyk test --all-projects' \
  'snyk monitor --all-projects' \
  '--exclude=build,\\.gradle' \
  'actions/setup-java@' \
  "java-version: '17'" \
  'timeout-minutes: 20' \
  'sarif-file-output=./snyk-results.sarif' \
  'hashFiles('\''snyk-results.sarif'\'') != '\''' \
  'security-events: write'; do
  grep -Fq "$marker" "$file" || fail "Pflichtmarker fehlt: $marker"
done

if grep -Eq 'snyk/actions/(gradle-jdk17|gradle-jdk21)@' "$file"; then
  fail "abgekündigte Snyk-Gradle-Action wird weiterhin verwendet"
fi
if grep -Eq -- '--sarif-file-output=[^[:space:]\\]+' "$file" && ! grep -Fq -- '--sarif-file-output=./snyk-results.sarif' "$file"; then
  fail "SARIF-Ausgabepfad ist nicht deterministisch"
fi

echo "✅ [snyk-workflow-test] CLI-Migration, JDK, Timeout und SARIF-Guard sind vorhanden."
