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

# Moblin weekly check: the github-script step must dedup open issues, otherwise
# every run creates a new (false-positive) issue.
file=.github/workflows/check-moblin-features.yml
grep -Fq 'issues.listForRepo' "$file" \
  || fail "check-moblin github-script must list open moblin issues before creating a new one"
grep -Fq 'issues.createComment' "$file" \
  || fail "check-moblin github-script must comment on the existing issue instead of duplicating"

# The comparison script filters bare URL rows (not features), maps common
# English feature words onto the German PARITY vocabulary ("battery" -> "akku")
# and strips plural-s - otherwise tracked features are reported as missing
# ("Battery indicator", "Take snapshots") and every weekly run files a
# false-positive issue.
file=scripts/check_moblin_features.sh
grep -Eq "grep -Ev '\^https\?://'" "$file" \
  || fail "moblin check must filter bare URL rows"
grep -Fq 'variants="$variants akku"' "$file" \
  || fail "moblin check must map English keywords onto German PARITY terms"
grep -Fq 'variants="$variants ${kw%s}"' "$file" \
  || fail "moblin check must try the singular form alongside the plural"
grep -Eq 'break 2' "$file" \
  || fail "moblin check must stop at the first characteristic keyword hit"
grep -Fq 'take|this|that|with|from|into' "$file" \
  || fail "moblin check must filter generic words to avoid substring false alarms"
grep -Fq 'variants="$variants auflösung"' "$file" \
  || fail "moblin check must map resolution onto the German PARITY term"

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

# Gradle Wrapper validation must run in CI, SHA-pinned, before the first
# gradlew invocation (scorecard BinaryArtifacts #40 assurance).
wrapper_step=$(grep -B1 -A2 -F 'gradle/actions/wrapper-validation@' .github/workflows/android-ci.yml || true)
[[ -n "$wrapper_step" ]] \
  || fail "android-ci.yml must run gradle/actions/wrapper-validation"
echo "$wrapper_step" | grep -Eq 'gradle/actions/wrapper-validation@[0-9a-f]{40}' \
  || fail "gradle/actions/wrapper-validation must be pinned to a full 40-char commit SHA"
first_gradle_use=$(grep -n -m1 'run: \./gradlew\|gradle/actions/setup-gradle' .github/workflows/android-ci.yml | cut -d: -f1)
validation_line=$(grep -n 'gradle/actions/wrapper-validation@' .github/workflows/android-ci.yml | cut -d: -f1)
[[ -n "$first_gradle_use" && -n "$validation_line" && "$validation_line" -lt "$first_gradle_use" ]] \
  || fail "wrapper-validation must run before the first gradlew invocation"

# Scorecard maintainer annotations must exist, stay valid YAML, and only
# use the official reason vocabulary (ossf/scorecard config/README.md).
[[ -s .github/scorecard.yml ]] \
  || fail ".github/scorecard.yml (maintainer annotations) is missing"
python - <<'PYEOF' || fail "scorecard.yml annotations are invalid"
import sys
import yaml

doc = yaml.safe_load(open(".github/scorecard.yml", encoding="utf-8"))
annotations = doc.get("annotations") or []
assert annotations, "no annotations present"
allowed = {"test-data", "remediated", "not-applicable", "not-supported", "not-detected"}
required = {"binary-artifacts", "fuzzing"}
covered = set()
for entry in annotations:
    checks = entry.get("checks") or []
    reasons = entry.get("reasons") or []
    assert checks and reasons, "annotation without checks or reasons"
    covered.update(checks)
    for r in reasons:
        assert r.get("reason") in allowed, f"invalid reason: {r.get('reason')}"
assert required <= covered, f"missing annotations for: {sorted(required - covered)}"
PYEOF

echo "✅ [workflow-security-test] Permissions, PR input handling, ChatOverlay findings, wrapper validation, and scorecard annotations are guarded."
