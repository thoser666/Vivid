#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Version-Code-Fallback-Selbsttest ==="
ruby scripts/test_version_fallback.rb

echo "=== Gradle-Konfiguration (Konfigurations-Task, kein voller Build) ==="
# Gradle nutzt das konfigurierte JAVA_HOME bzw. Java aus PATH, auch auf macOS/Linux.
./gradlew :app:dependencies --quiet
