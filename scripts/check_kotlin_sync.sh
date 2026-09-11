#!/usr/bin/env bash
# Guard: kotlin und jetbrainsKotlinJvm im Version-Catalog müssen synchron sein.
#
# Hintergrund (Issue #110, Dependabot-Alert #63): Das Kotlin-Android- und das
# Kotlin-JVM-Plugin werden über SEPARATE Versions-Keys gepflegt
# (libs.versions.toml → [versions] kotlin / jetbrainsKotlinJvm). Beide müssen
# immer exakt dieselbe Kotlin-Version tragen, sonst kompiliert z. B. das JVM-only
# Tooling mit einer anderen Vorlage als die Android-Module — das Ergebnis ist
# inkonsistent und per Compiler-Fehlern schwer nachvollziehbar.
#
# Exit-Code 0 = synchron, 1 = Drift/fehlender Key (harter Fehler im Pre-Push-Gate
# und in der CI — ein Drift soll vor roten Builds auffallen).
#
# Selbtest: scripts/test_kotlin_sync.sh (Fixtures, offline) — läuft im Pre-Push-
# Gate und in android-ci.yml.
set -euo pipefail

CATALOG="${1:-gradle/libs.versions.toml}"
KEY_KOTLIN="${KOTLIN_SYNC_KOTLIN_KEY:-kotlin}"
KEY_JVM="${KOTLIN_SYNC_JVM_KEY:-jetbrainsKotlinJvm}"

if [ ! -f "$CATALOG" ]; then
  echo "❌ [kotlin-sync] $CATALOG nicht gefunden."
  exit 1
fi

read_key() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$CATALOG" | head -1
}

kotlin="$(read_key "$KEY_KOTLIN")"
jvm="$(read_key "$KEY_JVM")"

if [ -z "$kotlin" ]; then
  echo "❌ [kotlin-sync] Key '$KEY_KOTLIN' fehlt in $CATALOG."
  exit 1
fi
if [ -z "$jvm" ]; then
  echo "❌ [kotlin-sync] Key '$KEY_JVM' fehlt in $CATALOG."
  exit 1
fi

if [ "$kotlin" != "$jvm" ]; then
  echo "❌ [kotlin-sync] '$KEY_KOTLIN' ($kotlin) != '$KEY_JVM' ($jvm) — beide Keys müssen dieselbe Kotlin-Version tragen (Issue #110)."
  exit 1
fi

echo "✅ [kotlin-sync] '$KEY_KOTLIN' == '$KEY_JVM' == $kotlin"