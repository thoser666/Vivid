#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
file=build.gradle.kts
fail() { echo "❌ [dependency-security-test] $1"; exit 1; }
for marker in \
  '"io.netty"' \
  'netty-handler' \
  '4.1.137.Final' \
  '"org.apache.commons"' \
  'commons-lang3' \
  '3.18.0' \
  '"org.bouncycastle"' \
  'bcprov-jdk18on' \
  'bcpkix-jdk18on' \
  'bcutil-jdk18on' \
  '1.85'; do
  grep -Fq "$marker" "$file" || fail "Constraint fehlt: $marker"
done
echo "✅ [dependency-security-test] Sicherheitsconstraints vollständig."
