#!/usr/bin/env bash
# Regressionstest: Root-Fix gegen die CI-Suppression auf Bot-PRs.
#
# Hintergrund (Vorfall PRs #140/#142/#143): Workflows, die Bot-Branches per
# GITHUB_TOKEN pushen, erzeugen PRs, auf denen GitHub die pull_request-
# Workflows unterdrückt (Rekursions-Schutz) — die Pflicht-Checks
# "Build & Test" und "Secret Guard" laufen nie und der PR bleibt BLOCKED.
# Fix: Push läuft über ein USER-Credential (Secret AUTOMATION_TOKEN, Fallback
# GITHUB_TOKEN mit ::warning::); der PR-Create läuft per REST (`gh api .../pulls`),
# weil `gh pr create` die GraphQL-Mutation createPullRequest nutzt, an der
# Fine-grained PATs auch mit korrekter Pull-requests-Permission scheitern
# ("Resource not accessible by personal access token", Run 34022706363).
#
# Geprüfte Szenarien:
#   T1 checkout token      → Bot-PR-Workflows checken mit AUTOMATION_TOKEN-
#                            Fallback aus (credentials.OutputStream folgt dem Push)
#   T2 PR-Step GH_TOKEN    → derselbe Fallback-Ausdruck im env des Commit/PR-Steps
#   T3 Loop-Guard          → Commit-Author bleibt github-actions[bot]
#                            (hängt am Author, nicht am Credential)
#   T4 Fallback-Hinweis    → ::warning::-Zeile für fehlendes AUTOMATION_TOKEN
#                            vorhanden (alle 3 Dateien)
#   T5 release-pipeline    → user-scoped Push via git remote set-url +
#                            GH_TOKEN (scoped, kein Checkout-Token-Swap)
#   T6 F-Droid-Kommentar-Duplikat → der alte Inline-Kommentarblock wurde
#                            entfernt (kein doppeltes "Branch Protection verbietet")
#   T7 kein `gh pr create`  → GraphQL-Mutation scheitert an Fine-grained PATs;
#                            alle 3 Dateien nutzen stattdessen REST
#   T8 REST-PR-Create       → `gh api repos/.../pulls` in allen 3 Dateien
#   T9 Orphan-Rollback      → scheitert der PR-Create, wird der Bot-Branch
#                            automatisch gelöscht (Vorfall: 3 Orphan-Branches
#                            aus den 403-Runs 34021546368/34022706363/34023536729)
#   T10 Rebase-Härtung      → Changelog-Mirror rebaset auf weitergelaufenes
#                            develop; bei Konflikt Neugenerierung (Vorfall
#                            CONFLICTING-PR #149)
#
# Läuft im CI (android-ci.yml, Job "Build & Test") und lokal:
# bash scripts/test_bot_pr_credentials.sh  (Exit 0 = grün)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

echo "▶ [test_bot_pr_credentials] Szenarien T1–T10"

FAILED=0
check() {
  local name="$1" file="$2" pattern="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    echo "  ✅ $name"
  else
    echo "  ❌ $name — Muster nicht gefunden: $pattern (in $file)"
    FAILED=1
  fi
}

# T1+T2: Fallback-Ausdruck im Checkout (persistierte Push-Credentials) UND
# im env des Commit/PR-Steps (gh pr create + Push-Kontext).
check "T1a changelog: checkout token mit AUTOMATION_TOKEN-Fallback" \
  .github/workflows/automation-changelog.yml \
  'token: \$\{\{ secrets\.AUTOMATION_TOKEN \|\| secrets\.GITHUB_TOKEN \}\}'
check "T1b fdroid: checkout token mit AUTOMATION_TOKEN-Fallback" \
  .github/workflows/deploy-fdroid.yml \
  'token: \$\{\{ secrets\.AUTOMATION_TOKEN \|\| secrets\.GITHUB_TOKEN \}\}'
check "T2a changelog: PR-Step GH_TOKEN mit Fallback" \
  .github/workflows/automation-changelog.yml \
  'GH_TOKEN: \$\{\{ secrets\.AUTOMATION_TOKEN \|\| secrets\.GITHUB_TOKEN \}\}'
check "T2b fdroid: PR-Step GH_TOKEN mit Fallback" \
  .github/workflows/deploy-fdroid.yml \
  'GH_TOKEN: \$\{\{ secrets\.AUTOMATION_TOKEN \|\| secrets\.GITHUB_TOKEN \}\}'

# T3: Loop-Guard bleibt am Commit-Author hängen (nicht am Credential).
check "T3a changelog: Author bleibt github-actions[bot]" \
  .github/workflows/automation-changelog.yml \
  'user\.name "github-actions\[bot\]"'
check "T3b fdroid: Author bleibt github-actions[bot]" \
  .github/workflows/deploy-fdroid.yml \
  'user\.name "github-actions\[bot\]"'
check "T3c release-pipeline: Author bleibt github-actions[bot]" \
  .github/workflows/release-pipeline.yml \
  'user\.name "github-actions\[bot\]"'

# T4: Fallback-Warnhinweis in allen drei Dateien (::annotation für die Run-Anzeige).
for f in .github/workflows/automation-changelog.yml \
         .github/workflows/deploy-fdroid.yml \
         .github/workflows/release-pipeline.yml; do
  check "T4 ::warning:: bei fehlendem AUTOMATION_TOKEN ($(basename "$f"))" \
    "$f" '::warning::AUTOMATION_TOKEN ist nicht gesetzt'
done

# T5: release-pipeline spiegelt den Changelog im publish-release-Job —
# dort läuft der Push user-scoped (remote set-url mit x-access-token:GH_TOKEN).
check "T5 release-pipeline: user-scoped Push (git remote set-url)" \
  .github/workflows/release-pipeline.yml \
  'git remote set-url origin "https://x-access-token:\$\{GH_TOKEN\}@github\.com/'
check "T5 release-pipeline: GH_TOKEN mit AUTOMATION_TOKEN-Fallback" \
  .github/workflows/release-pipeline.yml \
  'GH_TOKEN: \$\{\{ secrets\.AUTOMATION_TOKEN \|\| secrets\.GITHUB_TOKEN \}\}'

# T6: Duplikat-Kommentarblock im fdroid-Run-Script wurde entfernt
# (Refactoring-Artefakt-Guard).
if [ "$(grep -c "Branch Protection verbietet direkte Bot-Pushes auf develop" .github/workflows/deploy-fdroid.yml)" -le 1 ]; then
  echo "  ✅ T6 fdroid: kein duplizierter Branch-Protection-Kommentarblock"
else
  echo "  ❌ T6 fdroid: Branch-Protection-Kommentarblock doppelt"
  FAILED=1
fi

# T7+T8: PR-Create per REST statt `gh pr create` — die GraphQL-Mutation
# createPullRequest ist mit Fine-grained PATs nicht nutzbar, auch mit
# korrekter Pull-requests-Permission (Run 34022706363).
for f in .github/workflows/automation-changelog.yml \
         .github/workflows/deploy-fdroid.yml \
         .github/workflows/release-pipeline.yml; do
  # Nur echte Kommando-Zeilen zählen (Zeilenanfang) — nicht Erwähnungen in
  # Kommentaren ("REST statt gh pr create …").
  if ! grep -qE '^[[:space:]]*gh pr create' "$f"; then
    echo "  ✅ T7 kein gh pr create ($(basename "$f"))"
  else
    echo "  ❌ T7 gh pr create noch vorhanden ($(basename "$f")) — GraphQL-Mutation scheitert an Fine-grained PATs"
    FAILED=1
  fi
  check "T8 REST-PR-Create ($(basename "$f"))" \
    "$f" 'gh api repos/\$\{\{ github\.repository \}\}/pulls -f title='
done

# T9: Orphan-Rollback — scheitert der PR-Create, muß der Bot-Branch gelöscht
# werden (Vorfall: die 403-Runs hinterließen jeweils Orphan-Branches, die
# manuell per API geräumt werden mußten). Guard: if !-Wrap um den REST-Call,
# DELETE-Ref-Call, ::error::-Ankündigung, exit 1 danach.
for f in .github/workflows/automation-changelog.yml \
         .github/workflows/deploy-fdroid.yml \
         .github/workflows/release-pipeline.yml; do
  check "T9.1 PR-Create in if !-Rollback-Wrap ($(basename "$f"))" \
    "$f" 'if ! gh api repos/\$\{\{ github\.repository \}\}/pulls -f title='
  check "T9.2 Branch-DELETE im Rollback ($(basename "$f"))" \
    "$f" 'git/refs/heads/\$BRANCH'
  check "T9.3 Rollback-::error::($(basename "$f"))" \
    "$f" '::error::PR-Create fehlgeschlagen — Rollback'
  check "T9.4 exit 1 nach Rollback ($(basename "$f"))" \
    "$f" 'kein Orphan)'
done

# T10: Rebase-Härtung der Changelog-Mirror-Workflows — während des Runs
# gemergte develop-Commits lassen den Bot-PR sonst als CONFLICTING zurück
# (Vorfall #149). Guard: fetch nach dem Commit, Ancestor-Check HEAD~1 vs.
# origin/develop, Rebase mit Konflikt-Fallback (Neugenerierung).
for f in .github/workflows/automation-changelog.yml \
         .github/workflows/release-pipeline.yml; do
  check "T10.1 Rebase-Ancestor-Check ($(basename "$f"))" \
    "$f" 'merge-base --is-ancestor "HEAD~1" origin/develop'
  check "T10.2 Rebase-Aufruf ($(basename "$f"))" \
    "$f" 'git rebase origin/develop'
  check "T10.3 Konflikt-Fallback: abort + Neugenerierung ($(basename "$f"))" \
    "$f" 'git rebase --abort'
  check "T10.4 ::notice:: bei weitergelaufenem develop ($(basename "$f"))" \
    "$f" '::notice::develop ist während des Runs weitergelaufen'
done

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ Alle Checks grün — Bot-PRs entstehen per User-Credential (AUTOMATION_TOKEN, Fallback GITHUB_TOKEN)."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1
