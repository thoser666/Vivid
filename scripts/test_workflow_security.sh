#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "❌ [workflow-security-test] $1"
  exit 1
}

require_file() {
  [[ -s "$1" ]] || fail "Workflow fehlt oder ist leer: $1"
}

require_top_level_empty_permissions() {
  local file="$1"
  require_file "$file"
  grep -Eq '^permissions:[[:space:]]*\{\}[[:space:]]*$' "$file" \
    || fail "Top-Level-Permissions müssen leer sein: $file"
}

# Workflows with write access keep it at the smallest job scope.
for file in \
  .github/workflows/automation-changelog.yml \
  .github/workflows/check-moblin-features.yml \
  .github/workflows/dependabot-auto-merge.yml \
  .github/workflows/deploy-fdroid.yml \
  .github/workflows/release-drafter.yml \
  .github/workflows/security-codeql.yml \
  .github/workflows/security-scorecard.yml \
  .github/workflows/security-snyk.yml; do
  require_top_level_empty_permissions "$file"
done

# The PR title is data passed through the environment, never interpolated into a
# run script. This is the concrete regression for CodeQL DangerousWorkflowID #24.
file=.github/workflows/dependabot-auto-merge.yml
if grep -Eq 'PR_TITLE="\$\{\{[[:space:]]*github\.event\.pull_request\.title' "$file"; then
  fail "Pull-request title is interpolated directly into a shell assignment"
fi
grep -Fq 'PR_TITLE: ${{ github.event.pull_request.title }}' "$file" \
  || fail "Pull-request title must be passed through step environment"
grep -Fq 'printf' "$file" \
  || fail "Pull-request title must be consumed as quoted data"
grep -Fq '$PR_TITLE' "$file" \
  || fail "Pull-request title must be consumed through the environment"

# Security scanning jobs retain only the permission needed for SARIF upload.
grep -A5 -F 'snyk-test:' .github/workflows/security-snyk.yml \
  | grep -Fq 'security-events: write' \
  || fail "Snyk test job must retain security-events write"
grep -A7 -F 'snyk-monitor:' .github/workflows/security-snyk.yml \
  | grep -Fq 'contents: read' \
  || fail "Snyk monitor job must retain contents read"
grep -A7 -F 'analysis:' .github/workflows/security-scorecard.yml \
  | grep -Fq 'security-events: write' \
  || fail "Scorecard job must retain security-events write"

# The source-level warning must have an actual use, not a suppression.
grep -Fq 'Modifier.alpha(deletedAlpha)' feature-chat/src/main/java/com/vivid/feature/chat/ui/ChatOverlay.kt \
  || fail "Deleted-message alpha is not applied"
grep -Fq 'color = if (message.isAction) Color(0xFFB0BEC5) else textColor' feature-chat/src/main/java/com/vivid/feature/chat/ui/ChatOverlay.kt \
  || fail "Configured chat text color is not applied"

echo "✅ [workflow-security-test] Permissions, PR input handling, and ChatOverlay findings are guarded."
