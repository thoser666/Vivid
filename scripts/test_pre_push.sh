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

[[ -f scripts/install-git-hooks.sh ]] || fail "scripts/install-git-hooks.sh fehlt"

echo "✅ Pre-Push-Gate: Dry-Run listet alle drei Checks, Installer vorhanden."
