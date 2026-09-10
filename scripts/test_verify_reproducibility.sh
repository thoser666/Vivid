#!/usr/bin/env bash
# Regressionstest: Verify-Reproducibility-Nightly (release-pipeline.yml)
# =====================================================================
# Hintergrund (Vorfall 06.09.2026, Run 34009482168): Der Verify-Job lud
# `app-release.apk` aus dem Nightly-Release — die Release-Lane publiziert
# aber seit Einführung der Flavors (standard/foss) das Asset als
# `app-standard-release.apk`. Der Job scheiterte folglich an jedem
# Nightly/Dispatch mit "app-release.apk fehlt im Release" (schon die
# Schedule-Runs 04.09./05.09.).
#
# Geprüfte Szenarien:
#   T1 verify-Job lädt das echte Asset app-standard-release.apk
#   T2 verify-Job enthält KEINE Referenz mehr auf das alte app-release.apk
#   T3 Rebuild-Tasks sind flavor-qualifiziert (assembleStandardRelease /
#      bundleStandardPlayRelease) mit den publizierten Version-Params
#   T4 Compare-Pfade nutzen die flavor-Output-Pfade (apk/standard/release,
#      mapping/standardRelease, bundle/standardPlayRelease) — ohne
#      Pre-Flavor-Pfade (apk/release, mapping/release, bundle/release)
#   T5 Sentry-Opt-out-Check im Verify-Job nutzt das standardRelease-Mapping
#   T6 Build-Release-Artefakt-Upload (release-pipeline + android-ci) nutzt
#      den flavor-Pfad app-standard-release.apk
#   T7 check_sentry_optout_mapping.sh: Default-Mapping ist standardRelease
#   T8 update_changelog.sh: Artefakte-Zeile nennt app-standard-release.apk
#   T9 Fastfile-Fallbacks (lane_context-Ersatz) nutzen flavor-Pfade
#   T10 release-pipeline.yml bleibt valides YAML
#   T11 Changelog-Mirror listet SHA256SUMS.txt als Nightly-Artefakt
#       (Nightly-Releases tragen die Prüfsummendatei seit den
#       Distributions-Quick-Wins — der Mirror muss das spiegeln)

set -euo pipefail
cd "$(dirname "$0")/.."

FAIL=0
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}
# Negations-Variante: `!` kann nicht als Funktionsargument uebergeben werden
# (Expandierung macht daraus einen Kommandonamen) — daher eigener Helper.
check_absent() {
  local desc="$1"; shift
  if ! "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}

WORKFLOW=.github/workflows/release-pipeline.yml
VERIFY_START=$(grep -n '^  verify-reproducibility:' "$WORKFLOW" | cut -d: -f1)
VERIFY_END=$(awk -v s="$VERIFY_START" 'NR>s && /^  [a-z-]+:/{print NR; exit}' "$WORKFLOW")
VERIFY_SECTION=$(sed -n "${VERIFY_START},${VERIFY_END}p" "$WORKFLOW")

echo "== T1: Asset-Download nutzt app-standard-release.apk =="
check "T1.1 Download app-standard-release.apk" \
  grep -q 'published.apk "$BASE/app-standard-release.apk"' <<<"$VERIFY_SECTION"
check "T1.2 Fehlermeldung benennt das echte Asset" \
  grep -q 'app-standard-release.apk fehlt im Release' <<<"$VERIFY_SECTION"

echo "== T2: Kein alter Asset-Name mehr im verify-Job =="
check_absent "T2.1 kein app-release.apk-Download" \
  grep -q 'app-release\.apk' <<<"$VERIFY_SECTION"

echo "== T3: Rebuild-Tasks flavor-qualifiziert =="
check "T3.1 Rebuild via assembleStandardRelease" \
  grep -q './gradlew :app:assembleStandardRelease' <<<"$VERIFY_SECTION"
check "T3.2 Rebuild-AAB via bundleStandardPlayRelease" \
  grep -q './gradlew :app:bundleStandardPlayRelease' <<<"$VERIFY_SECTION"
check "T3.3 Rebuild mit publizierten Version-Params" \
  grep -q -- '-PversionName=' <<<"$VERIFY_SECTION" &&
  grep -q -- '-PversionCode=' <<<"$VERIFY_SECTION"

echo "== T4: Compare-Pfade flavor-korrekt, keine Pre-Flavor-Pfade =="
check "T4.1 compare APK-Pfad" \
  grep -q 'published.apk app/build/outputs/apk/standard/release/app-standard-release.apk' <<<"$VERIFY_SECTION"
check "T4.2 compare Mapping-Pfad" \
  grep -q 'published-mapping.txt app/build/outputs/mapping/standardRelease/mapping.txt' <<<"$VERIFY_SECTION"
check "T4.3 compare Metadata-Pfad" \
  grep -q 'published-output-metadata.json app/build/outputs/apk/standard/release/output-metadata.json' <<<"$VERIFY_SECTION"
check "T4.4 compare AAB-Pfad (optionaler Zweig)" \
  grep -q 'published.aab app/build/outputs/bundle/standardPlayRelease/app-standard-playRelease.aab' <<<"$VERIFY_SECTION"
check_absent "T4.5 kein Pre-Flavor-Pfad apk/release" \
  grep -q 'apk/release/' <<<"$VERIFY_SECTION"
check_absent "T4.6 kein Pre-Flavor-Pfad mapping/release" \
  grep -q 'mapping/release/' <<<"$VERIFY_SECTION"

echo "== T5: Sentry-Mapping-Nachweis im verify-Job =="
check "T5.1 check_sentry_optout_mapping.sh auf standardRelease-Mapping" \
  grep -q 'check_sentry_optout_mapping.sh app/build/outputs/mapping/standardRelease/mapping.txt' <<<"$VERIFY_SECTION"

echo "== T6: Build-Release-Artefakt-Upload flavor-korrekt =="
check "T6.1 release-pipeline Upload app-standard-release.apk" \
  grep -q 'path: app/build/outputs/apk/standard/release/app-standard-release.apk' "$WORKFLOW"
check "T6.2 android-ci Upload app-standard-release.apk" \
  grep -q 'path: app/build/outputs/apk/standard/release/app-standard-release.apk' .github/workflows/android-ci.yml

echo "== T7: Mapping-Check-Default =="
check "T7.1 Default MAPPING_RELEASE = standardRelease" \
  grep -q 'MAPPING_RELEASE:-app/build/outputs/mapping/standardRelease/mapping.txt' scripts/check_sentry_optout_mapping.sh

echo "== T8: Changelog-Artefakte-Zeile =="
check "T8.1 nightly-Artefakte-Zeile app-standard-release.apk" \
  grep -q 'app-standard-release.apk' scripts/update_changelog.sh
check "T8.2 nightly-Artefakte-Zeile nennt SHA256SUMS.txt" \
  grep -q 'SHA256SUMS.txt' scripts/update_changelog.sh

echo "== T9: Fastfile-Fallback-Pfade =="
check "T9.1 APK-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/app-standard-release.apk' fastlane/Fastfile
check "T9.2 Mapping-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/mapping/standardRelease/mapping.txt' fastlane/Fastfile
check "T9.3 Metadata-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/output-metadata.json' fastlane/Fastfile
check_absent "T9.4 kein Pre-Flavor-Fallback im Fastfile" \
  grep -qE 'apk/release/|mapping/release/' fastlane/Fastfile
check "T9.5 Nightly-Prüfsummen-Anhang in publish_release vorhanden" \
  grep -q 'assets << options\[:checksums\] if options\[:checksums\] && File.exist?(options\[:checksums\])' fastlane/Fastfile
check "T9.6 genau 2 Prüfsummen-Anhänge (stable + nightly)" \
  bash -c '[ "$(grep -cF "options[:checksums] && File.exist?(options[:checksums])" fastlane/Fastfile)" -eq 2 ]'

echo "== T10: Workflow-YAML valide =="
check "T10.1 release-pipeline.yml parst als YAML" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    yaml.safe_load(f)
"
check "T10.2 android-ci.yml parst als YAML" python3 -c "
import yaml, io
with io.open('.github/workflows/android-ci.yml', encoding='utf-8') as f:
    yaml.safe_load(f)
"

echo "== T11: Changelog-Mirror spiegelt die Nightly-Prüfsummendatei =="
check "T11.1 Mirror-Artefakte-Zeile enthält SHA256SUMS.txt" \
  grep -q 'SHA256SUMS.txt' scripts/update_changelog.sh

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_verify_reproducibility.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
