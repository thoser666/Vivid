#!/usr/bin/env bash
# Pre-Push-Gate (Vivid): führt die gleichen Checks wie die CI lokal aus, bevor
# ein Push losgeschickt wird. Verhindert rote CI-Läufe — z. B. der Fall, dass
# ein Modul nur compiliert, aber nie getestet wurde (CI fand den verpassten
# :feature-settings-Test).
#
# Aufruf:
#   bash scripts/pre-push.sh                    # manuell (Tests + Lint + Guard)
#   bash scripts/pre-push.sh --dry-run          # zeigt nur die Checks an (Tests/Übersicht)
#   PRE_PUSH_SKIP_LINT=1 bash scripts/pre-push.sh  # Lint überspringen (nur Tests + Guard)
#   PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh    # zusätzlich Release-Build (R8/ProGuard)
#
# Der Release-Build (assembleRelease) ist OPTIONAL: er läuft R8/ProGuard +
# Resource-Shrinking und fängt so Signatur-/ProGuard-Probleme lokal, bevor sie
# die CI erreichen. Signierung fällt ohne KEYSTORE_PATH-Secrets auf den
# Debug-Keystore zurück (gleiches Verhalten wie in der CI ohne Secrets).
#
# Als Git-Hook installieren:  bash scripts/install-git-hooks.sh
# Einen einzelnen Push umgehen: git push --no-verify
set -euo pipefail

cd "$(dirname "$0")/.."

DRY_RUN=0
for arg in "$@"; do
  if [[ "$arg" == "--dry-run" ]]; then
    DRY_RUN=1
  fi
done

run() {
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "   [dry-run] $*"
  else
    "$@"
  fi
}

echo "▶ [pre-push] Unit-Tests aller Module (wie CI: ./gradlew testDebugUnitTest)"
run ./gradlew testDebugUnitTest --console=plain

if [[ "${PRE_PUSH_SKIP_LINT:-0}" != "1" ]]; then
  echo "▶ [pre-push] Lint (warningsAsErrors, wie CI: ./gradlew lintDebug)"
  run ./gradlew lintDebug --console=plain
fi

# Optional: Release-Build mit R8/ProGuard + Resource-Shrinking (assembleRelease).
if [[ "${PRE_PUSH_RELEASE:-0}" == "1" ]]; then
  echo "▶ [pre-push] Release-Build (R8/ProGuard, PRE_PUSH_RELEASE=1: ./gradlew assembleRelease)"
  run ./gradlew assembleRelease --console=plain
fi

echo "▶ [pre-push] Secret-Guard (scripts/guard_secrets.sh)"
run bash scripts/guard_secrets.sh

echo "✅ [pre-push] Alle Checks grün — Push freigegeben."
