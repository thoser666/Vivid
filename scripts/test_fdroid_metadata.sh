#!/usr/bin/env bash
# Regressionstest: F-Droid-/IzzyOnDroid-Submissions-Metadata für den foss-Flavor.
#
# Geprüfte Szenarien:
#   M1 Pflichtfelder   → Categories, License, SourceCode, IssueTracker, AutoName,
#                        Summary, Description vorhanden (fdroiddata-Pflichtkette)
#   M2 Paketname       → com.vivid.foss (foss-Flavor applicationId)
#   M3 UpdateCheckMode → Tags (nightlies haben keine v*-Tags → bleiben draußen)
#   M4 Builds-Block    → ≥1 Eintrag mit versionName/versionCode/commit/gradle
#   M5 foss-Build      → gradle assembleFossRelease (NICHT assembleStandardRelease)
#   M6 output          → app-foss-release.apk
#   M7 versionCode-Konsistenz → versionCode im Eintrag == deterministisch aus der
#                        versionName via fastlane/release_safety.rb (dieselbe
#                        Quelle wie release_beta) — fdroidserver blockt sonst
#   M8 no-nightly      → kein versionName/versionCode eines rolling nightlies
#   M9 config-konsistenz → fdroid/config-fdroid-main.yml baut assembleFossRelease
#                        (die Submission-Vorlage darf NICHT mehr den Standard-
#                        Flavor mit Sentry bauen — FOSS-Konformität)
#   M10 self-hosted-Repos bleiben getrennt → deploy-fdroid.yml löscht die
#                        committed foss-Metadaten beim Selbst-Repo-Build (sonst
#                        Index-Duplikat com.vivid.foss ohne APK → fdroid update rot)
#
# Läuft im CI (release-pipeline.yml, Job "Self-Test F-Droid-Metadata") und
# lokal: bash scripts/test_fdroid_metadata.sh  (Exit 0 = grün)
# Plain Ruby (Psych ist in Ruby eingebaut), keine Fastlane-Abhängigkeit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

echo "▶ [test_fdroid_metadata] Szenarien M1–M10"

FAILED=0
CHECK() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "  ✅ $name"
  else
    echo "  ❌ $name"
    FAILED=1
  fi
}

RUBY_SCRIPT=$(cat <<'RUBY'
require "yaml"
$LOAD_PATH.unshift File.expand_path("fastlane", Dir.pwd)
require "release_safety"

path = "fdroid/metadata/com.vivid.foss.yml"
raise "Metadata fehlt: #{path}" unless File.file?(path)
meta = YAML.safe_load(File.read(path), aliases: true)
raise "Metadata ist kein Mapping" unless meta.is_a?(Hash)

fails = []
ok = ->(label, cond) { puts(cond ? "  ✅ #{label}" : "  ❌ #{label}"); fails << label unless cond }

# M1: fdroiddata-Pflichtkette
%w[Categories License SourceCode IssueTracker AutoName Summary Description].each do |key|
  ok.call("M1 Pflichtfeld #{key}", meta.key?(key) && !(meta[key].to_s.strip.empty?))
end

# M2: Hint im Header + Paketdateiname
ok.call("M2 Datei heißt com.vivid.foss.yml", File.basename(path) == "com.vivid.foss.yml")

# M3: UpdateCheckMode begrenzt auf Tags (keine nightlies)
ok.call("M3 UpdateCheckMode == Tags", meta["UpdateCheckMode"].to_s == "Tags")

# M4–M7: Builds-Block
builds = meta["Builds"]
ok.call("M4 Builds-Block vorhanden", builds.is_a?(Array) && !builds.empty?)
if builds.is_a?(Array) && !builds.empty?
  entry = builds.first
  ok.call("M4 Builds-Eintrag komplett",
    entry.is_a?(Hash) &&
    %w[versionName versionCode commit gradle output].all? { |k| entry.key?(k) })
  if entry.is_a?(Hash)
    gradle = Array(entry["gradle"])
    ok.call("M5 gradle assembleFossRelease (kein Standard-Flavor)",
      gradle.include?("assembleFossRelease") && !gradle.include?("assembleStandardRelease"))
    ok.call("M6 output app-foss-release.apk", entry["output"].to_s == "app-foss-release.apk")
    derived = version_code_for(entry["versionName"].to_s.sub(/^v/, ""))
    ok.call("M7 versionCode konsistent zur versionName (release_safety.rb): #{entry['versionName']}",
      derived == entry["versionCode"])
  end
end

# M8: kein nightly drin
joined = meta.to_s
ok.call("M8 kein nightly-Eintrag", !joined.include?("nightly"))

exit(fails.empty? ? 0 : 1)
RUBY
)

RUBY_TMP="$(mktemp)"
trap 'rm -f "$RUBY_TMP"' EXIT
printf '%s' "$RUBY_SCRIPT" > "$RUBY_TMP"
if ! ruby "$RUBY_TMP"; then
  echo "❌ Metadata-Szenarien fehlgeschlagen"
  exit 1
fi

# M9: Submission-Vorlage muss foss bauen (config-fdroid-main.yml)
CHECK "M9.1 config-fdroid-main.yml baut assembleFossRelease" \
  grep -q 'assembleFossRelease' fdroid/config-fdroid-main.yml
CHECK "M9.2 keine assembleStandardRelease in der FOSS-Vorlage" \
  bash -c '! grep -q "assembleStandardRelease" fdroid/config-fdroid-main.yml'
CHECK "M9.3 config-fdroid-main.yml output app-foss-release.apk" \
  grep -q 'output: app-foss-release.apk' fdroid/config-fdroid-main.yml
CHECK "M9.4 config-fdroid-main.yml verweist auf die gepflegte Metadata" \
  grep -q 'fdroid/metadata/com.vivid.foss.yml' fdroid/config-fdroid-main.yml

# M10: Selbst-gehostetes Repo (deploy-fdroid.yml) bleibt standard-only
CHECK "M10.1 deploy-fdroid rm -rf metadata (foss-Metadaten getrennt)" \
  grep -q 'rm -rf metadata' .github/workflows/deploy-fdroid.yml
CHECK "M10.2 deploy-fdroid lädt nur den Standard-Flavor" \
  grep -q -- '--pattern "app-standard-release.apk"' .github/workflows/deploy-fdroid.yml

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ Alle Checks grün — foss-Metadata ist einreichungsbereit."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1