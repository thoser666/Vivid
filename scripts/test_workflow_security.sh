#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Gemeinsame YAML-Zugriffe (scripts/lib_workflow_yaml.sh) — die fensterfreie
# Alternative zu `grep -A N` als Struktur-Prüfung. Siehe dort die Begründung.
source scripts/lib_workflow_yaml.sh

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
  .github/workflows/automation-wiki-sync.yml \
  .github/workflows/check-moblin-features.yml \
  .github/workflows/dependabot-auto-merge.yml \
  .github/workflows/deploy-fdroid.yml \
  .github/workflows/release-drafter.yml \
  .github/workflows/security-codeql.yml \
  .github/workflows/security-scorecard.yml \
  .github/workflows/security-snyk.yml; do
  require_top_level_empty_permissions "$file"
done

# CodeQL guard note: the weekly automation-codeql-kotlin.yml workflow was
# removed on 2026-09-10 and its premise was WRONG: action pin and default
# BUNDLE are separate versions, and even bundle 2.27.0 (action v4.38.0,
# released 2026-09-09) still REJECTS Kotlin 2.4.20 ("too recent") — support
# is merged upstream (github/codeql#20018, issue #22404 closed 2026-09-08)
# but not shipped in any released bundle yet. Empirical evidence: CodeQL run
# 34509237841. The advisory watcher scripts/check_codeql_blockade.sh tracks
# the blockade; all codeql-action pins across workflows must use the SAME
# release (init/analyze/upload-sarif) to avoid the mixed-version warning;
# see RELEASE.md Bundle-Lag-Lehre.

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

# Moblin findings must carry the vision.md answer sheet so new features are
# evaluated against the project vision, not just listed.
grep -Fq 'docs/vision.md' scripts/check_moblin_features.sh \
  || fail "moblin check report must reference the vision criteria"
grep -Fq 'Vision-Check' scripts/check_moblin_features.sh \
  || fail "moblin check report must contain the vision answer sheet"

# Community triage: least privilege, pinned action, and an idempotency
# marker so each enhancement issue receives the vision checklist at most once.
file=.github/workflows/community-requests.yml
[[ -f "$file" ]] || fail "community-requests.yml must exist"
# Fensterfrei: `permissions: {}` muss auf Spalte 0 vor `jobs:` stehen. Ein
# head-N-Fenster bricht, sobald der Header-Kommentar waechst (Vorfall #258:
# genau so ist der Contributors-Reminder-Check unten mitgebrochen).
awk '/^jobs:/{exit} /^permissions:[[:space:]]*\{\}[[:space:]]*$/{f=1} END{exit !f}' "$file" \
  || fail "community-requests workflow must default-deny permissions"
# Struktur, nicht Abstand: exakter Permission-Satz aus dem YAML.
[[ "$(wf_job_permissions "$file" triage)" == "issues=write" ]] \
  || fail "community-requests triage job permissions must be exactly issues=write (ist: $(wf_job_permissions "$file" triage))"
grep -Fq 'actions/github-script@60a0d83039c74a4aee543508d2ffcb1c3799cdea' "$file" \
  || fail "community-requests github-script must be SHA-pinned"
grep -Fq 'community-triage -->' "$file" \
  || fail "community-requests comment must carry an idempotency marker"
grep -Fq "labels: 'enhancement'" "$file" \
  || fail "community-requests must target enhancement issues only"
grep -Fq 'docs/vision.md' "$file" \
  || fail "community-requests must reference the vision criteria"

# Security scanning jobs retain ONLY the permission needed for SARIF upload.
#
# Struktur, nicht Abstand: diese Pruefung war ein `grep -A9`-Fenster, und ein
# Kommentar in dieser Datei dokumentierte bereits, dass das Fenster einmal
# wegen eines gewachsenen if:-Blocks von Hand breiter gestellt werden musste.
# Jetzt wird der Permission-Satz exakt aus dem YAML gelesen — dadurch fällt
# auch eine ZUSÄTZLICH vergebene Berechtigung auf, nicht nur eine fehlende.
[[ "$(wf_job_permissions .github/workflows/security-snyk.yml snyk-test)" == "contents=read;security-events=write" ]] \
  || fail "Snyk test job permissions must be exactly contents=read;security-events=write (ist: $(wf_job_permissions .github/workflows/security-snyk.yml snyk-test))"
[[ "$(wf_job_permissions .github/workflows/security-snyk.yml snyk-monitor)" == "contents=read" ]] \
  || fail "Snyk monitor job permissions must be exactly contents=read (ist: $(wf_job_permissions .github/workflows/security-snyk.yml snyk-monitor))"

# Fork-PR-Guard (PR #230): Release Drafter und Snyk skippen Fork-PRs —
# GITHUB_TOKEN/SNYK_TOKEN fließen nicht in Fork-PR-Runs, beide Jobs liefen
# sonst reproduziert rot. Die Klammerung schließt Push-Events ausdrücklich
# ein: github.event.pull_request.head.repo.fork ist dort null, ein nackter
# "== false"-Vergleich würde jeden develop/main-Push mit-skippen.
for file in .github/workflows/release-drafter.yml .github/workflows/security-snyk.yml; do
  grep -Fq "github.event_name != 'pull_request' || github.event.pull_request.head.repo.fork == false" "$file" \
    || fail "$file must skip fork pull_requests (secretless fork runs fail red, PR #230)"
  grep -Fq "(github.event_name != 'pull_request'" "$file" \
    || fail "$file fork-guard must be parenthesized so push events keep running"
done
[[ "$(wf_job_permissions .github/workflows/security-scorecard.yml analysis)" == "id-token=write;security-events=write" ]] \
  || fail "Scorecard job permissions must be exactly id-token=write;security-events=write (ist: $(wf_job_permissions .github/workflows/security-scorecard.yml analysis))"

# The source-level warning must have an actual use, not a suppression.
grep -Fq 'Modifier.alpha(deletedAlpha)' feature-chat/src/main/java/com/vivid/feature/chat/ui/ChatOverlay.kt \
  || fail "Deleted-message alpha is not applied"
grep -Fq 'color = if (message.isAction) Color(0xFFB0BEC5) else textColor' feature-chat/src/main/java/com/vivid/feature/chat/ui/ChatOverlay.kt \
  || fail "Configured chat text color is not applied"

# Gradle Wrapper validation must run in CI, SHA-pinned, before the first
# gradlew invocation (scorecard BinaryArtifacts #40 assurance).
wrapper_uses=$(wf_step_uses .github/workflows/android-ci.yml build 'gradle/actions/wrapper-validation')
[[ "$wrapper_uses" != "-" ]] \
  || fail "android-ci.yml must run gradle/actions/wrapper-validation"
[[ "$wrapper_uses" =~ ^gradle/actions/wrapper-validation@[0-9a-f]{40}$ ]] \
  || fail "gradle/actions/wrapper-validation must be pinned to a full 40-char commit SHA (ist: $wrapper_uses)"
first_gradle_use=$(grep -n -m1 'run: \./gradlew\|gradle/actions/setup-gradle' .github/workflows/android-ci.yml | cut -d: -f1)
validation_line=$(grep -n -m1 'gradle/actions/wrapper-validation@' .github/workflows/android-ci.yml | cut -d: -f1)
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

# Contributors-Reminder (Nacharbeit zum Vorfall 29.09.2026, #213/#230): der
# Auto-Issue-Workflow erinnert an die CONTRIBUTORS.md-Pflege nach Fork-PR-
# Merges. Er bleibt bewusst ein reiner Issue-Reminder: kein Branch-Push,
# kein PR-Create (der Bot-PR-Vertrag aus test_bot_pr_credentials.sh gilt
# nicht), State-Fenster atomar im Issue-Body.
#
# Berechtigungen (#258): nicht nur `issues: write`. Der Dank-Kommentar geht an
# einen Pull Request, und GitHub akzeptiert dort issues: write ODER
# pull_requests: write — mit issues: write allein starb der Lauf mit 403
# (Run 36995606481), nachdem das Reminder-Issue schon geschrieben war. Der
# frueher hier gepruegte `head -30`-Fensterbruch ist mit der Begruendung fuer
# pull-requests: write eingetreten (permissions: {} rutschte auf Zeile 32).
file=.github/workflows/automation-contributors-reminder.yml
[[ -f "$file" ]] || fail "contributors-reminder workflow must exist"
awk '/^jobs:/{exit} /^permissions:[[:space:]]*\{\}[[:space:]]*$/{f=1} END{exit !f}' "$file" \
  || fail "contributors-reminder workflow must default-deny permissions"
grep -Eq 'issues:[[:space:]]*write' "$file" \
  || fail "contributors-reminder must grant issues write"
grep -Eq 'pull-requests:[[:space:]]*write' "$file" \
  || fail "contributors-reminder must grant pull-requests write (Dank-Kommentar an einen PR wird sonst mit 403 abgelehnt)"
grep -Fq "github.actor != 'dependabot[bot]'" "$file" \
  || fail "contributors-reminder must skip dependabot actors"
grep -Fq 'contributors-state:' scripts/contributors_reminder.sh \
  || fail "contributors-reminder must persist its window state in the issue body"
grep -Fq 'head.repo == null' scripts/contributors_reminder.sh \
  || fail "contributors-reminder must detect deleted forks (head.repo == null)"
if grep -Eq 'gh pr create|git push' scripts/contributors_reminder.sh; then
  fail "contributors-reminder must stay a pure issue-reminder (no branch push, no PR create)"
fi

echo "✅ [workflow-security-test] Permissions, PR input handling, ChatOverlay findings, wrapper validation, and scorecard annotations are guarded."
