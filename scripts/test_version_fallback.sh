#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Version-Code-Fallback-Selbsttest ==="
ruby scripts/test_version_fallback.rb

echo "=== Gradle-Konfiguration (Konfigurations-Task, kein voller Build) ==="
export JAVA_HOME="C:\\Program Files\\OpenJDK\\jdk-25"
./gradlew :app:dependencies --quiet
