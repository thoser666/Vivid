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
grep -q "guard_secrets.sh" <<< "$out" || fail "guard_secrets.sh fehlt im Dry-Run"
grep -q "Alle Checks grün" <<< "$out" || fail "Abschlussmeldung fehlt im Dry-Run"

# Der Release-Build ist optional: ohne PRE_PUSH_RELEASE darf er NICHT auftauchen,
# mit PRE_PUSH_RELEASE=1 muss er aufgelistet sein.
if grep -q "assembleRelease" <<< "$out"; then
  fail "assembleRelease erscheint ohne PRE_PUSH_RELEASE=1 im Dry-Run"
fi
out_release="$(PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh --dry-run)"
grep -q "assembleRelease" <<< "$out_release" || fail "assembleRelease fehlt im Dry-Run mit PRE_PUSH_RELEASE=1"

[[ -f scripts/install-git-hooks.sh ]] || fail "scripts/install-git-hooks.sh fehlt"

echo "✅ Pre-Push-Gate: Dry-Run listet alle Checks (Release-Build nur optional), Installer vorhanden."
