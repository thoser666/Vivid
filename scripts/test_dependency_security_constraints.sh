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

# Buildscript-Classpath-Block (root): BC- und FreeMarker-Sicherheits-Pins
# (Dependabot-Alerts #67/#68 critical/high, #69 critical) sind dort nötig,
# weil `allprojects { resolutionStrategy }` den Root-Buildscript-Classpath
# (AGP/Kover/Lint-Tooling) NICHT abdeckt. Dateiweite greps wären zu schwach:
# dieselben Marker treten auch im allprojects-Block auf. Deshalb wird der
# buildscript-Block extrahiert und jeder Pin dort block-scharf geprüft
# (FAIL-CLOSED: fehlt der Block, schlägt die Prüfung fehl).
buildscript_block=$(awk '/^buildscript \{/,/^\}/' "$file")
[ -n "$buildscript_block" ] || fail "buildscript-Classpath-Block fehlt in $file"
for marker in \
  '"org.bouncycastle" to "bcprov-jdk18on"' \
  '"org.bouncycastle" to "bcpkix-jdk18on"' \
  '"org.bouncycastle" to "bcutil-jdk18on"' \
  'useVersion("1.85")' \
  '"org.freemarker" to "freemarker"' \
  'useVersion("2.3.35")' \
  'CVE-2026-84939'; do
  grep -Fq "$marker" <<<"$buildscript_block" || \
    fail "Buildscript-Classpath-Pin fehlt: $marker"
done
echo "✅ [dependency-security-test] Buildscript-Classpath-Pins (BC 1.85, FreeMarker 2.3.35) block-scharf verankert."
