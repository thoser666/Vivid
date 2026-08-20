#!/usr/bin/env bash
# Testet das Pre-Push-Gate (scripts/pre-push.sh) im --dry-run-Modus:
# alle drei CI-Checks müssen aufgelistet werden, ohne sie auszuführen.
# (Der Dry-Run startet kein Gradle — der Test ist schnell und offline.)
set -euo pipefail

cd "$(dirname "$0")/.."

out="$(bash scripts/pre-push.sh --dry-run)"

fail() {
  echo "❌ FAIL: $1"
  exit 1
}

grep -q "testDebugUnitTest" <<< "$out" || fail "testDebugUnitTest fehlt im Dry-Run"
grep -q "lintDebug" <<< "$out" || fail "lintDebug fehlt im Dry-Run"
grep -q "check_sentry_optout_mapping.sh" <<< "$out" || fail "check_sentry_optout_mapping.sh fehlt im Dry-Run"
grep -q "test_stage_suffix_guard.sh" <<< "$out" || fail "test_stage_suffix_guard.sh fehlt im Dry-Run"
grep -q "test_release_safety.sh" <<< "$out" || fail "test_release_safety.sh fehlt im Dry-Run"
grep -q "test_roadmap_reservation.sh" <<< "$out" || fail "test_roadmap_reservation.sh fehlt im Dry-Run"
grep -q "guard_secrets.sh" <<< "$out" || fail "guard_secrets.sh fehlt im Dry-Run"
grep -q "check_i18n.sh" <<< "$out" || fail "check_i18n.sh fehlt im Dry-Run"
grep -q "test_check_i18n.sh" <<< "$out" || fail "test_check_i18n.sh fehlt im Dry-Run"
grep -q "check_markdown_anchors.sh" <<< "$out" || fail "check_markdown_anchors.sh fehlt im Dry-Run"
grep -q "test_github_anchors.sh" <<< "$out" || fail "test_github_anchors.sh fehlt im Dry-Run"
grep -q "check_parity_log.sh" <<< "$out" || fail "check_parity_log.sh fehlt im Dry-Run"
grep -q "test_parity_log.sh" <<< "$out" || fail "test_parity_log.sh fehlt im Dry-Run"
grep -q "Alle Checks grün" <<< "$out" || fail "Abschlussmeldung fehlt im Dry-Run"

# Der Release-Build ist optional: ohne PRE_PUSH_RELEASE darf er NICHT auftauchen,
# mit PRE_PUSH_RELEASE=1 müssen BEIDE Kanäle (APK + AAB) aufgelistet sein.
if grep -q "assembleRelease" <<< "$out"; then
  fail "assembleRelease erscheint ohne PRE_PUSH_RELEASE=1 im Dry-Run"
fi
if grep -q "bundlePlayRelease" <<< "$out"; then
  fail "bundlePlayRelease erscheint ohne PRE_PUSH_RELEASE=1 im Dry-Run"
fi
out_release="$(PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh --dry-run)"
grep -q "assembleRelease" <<< "$out_release" || fail "assembleRelease fehlt im Dry-Run mit PRE_PUSH_RELEASE=1"
grep -q "bundlePlayRelease" <<< "$out_release" || fail "bundlePlayRelease fehlt im Dry-Run mit PRE_PUSH_RELEASE=1"

[[ -f scripts/install-git-hooks.sh ]] || fail "scripts/install-git-hooks.sh fehlt"

echo "✅ Pre-Push-Gate: Dry-Run listet alle Checks (Release-Build nur optional), Installer vorhanden."
