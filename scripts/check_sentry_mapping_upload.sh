#!/usr/bin/env bash
# Sentry-Mapping-Upload-Guard (Vivid):
# Prüft, dass JEDER Job, der ein ausgeliefertes Release-Artefakt baut, die
# Sentry-Zugangsdaten mitbekommt — sonst überspringt das Gradle-Plugin den
# ProGuard-Mapping-Upload (autoUploadProguardMapping in app/build.gradle.kts
# haengt an SENTRY_AUTH_TOKEN) und Sentry kann die Events dieses Builds nicht
# deobfuscieren.
#
# Anlass (01.11.2026): die Events der Kamera-Familie (0.5.68-beta+5684,
# 0.5.74-beta+5746, V2454A) tragen App-Frames als `G`, `q0`, `u`, `l` — der
# Play-Kanal buildet im Job `publish-play` (release-pipeline.yml) und dieser
# Job setzte SENTRY_AUTH_TOKEN nie. Der Kommentar im Fastfile behauptete das
# Gegenteil ("müssen im CI-Job gesetzt sein (android_fastlane.yml
# publish-release)"); android_fastlane.yml gibt es nicht mehr, und der
# Play-Pfad war nicht abgedeckt. Genau diese Luecke schliesst der Guard.
#
# Geprüft wird strukturiert ueber scripts/lib_workflow_yaml.sh (wf_step_env,
# wf_step_run) — kein Zeilenfenster, kein `grep -A N`:
#   M1  app/build.gradle.kts koppelt den Upload weiter an SENTRY_AUTH_TOKEN
#       (die Zeile ist der einzige Schalter; ein stilles `set(false)` faellt auf)
#   M2  release-pipeline.yml / publish-play / "Build AAB and upload to Google Play"
#       setzt SENTRY_AUTH_TOKEN (Secret-Referenz), SENTRY_ORG, SENTRY_PROJECT
#       UND bricht ohne Token laut ab (::error:: statt stillem Skip)
#   M3  release-pipeline.yml / publish-release / "Build and publish release"
#       (Nightly-APK via fastlane release_github) setzt dieselben drei Werte
#   M4  release-pipeline.yml / build-release / "Build Release APK" setzt den Token
#   M5  distribution-stable.yml / "Build and publish stable release" setzt den Token
#
# Pfade sind per Env ueberschreibbar (RELEASE_PIPELINE_OVERRIDE,
# DISTRIBUTION_STABLE_OVERRIDE, APP_GRADLE_OVERRIDE), damit der Selbsttest
# offline gegen mutierte Sandbox-Kopien fahren kann — sonst koennte er nie rot
# werden.
#
# Exit 0 = alle Nachweise gruen; 1 = mindestens ein Nachweis fehlt/falsch.
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib_workflow_yaml.sh
source scripts/lib_workflow_yaml.sh

REL="${RELEASE_PIPELINE_OVERRIDE:-.github/workflows/release-pipeline.yml}"
DIST="${DISTRIBUTION_STABLE_OVERRIDE:-.github/workflows/distribution-stable.yml}"
GRADLE="${APP_GRADLE_OVERRIDE:-app/build.gradle.kts}"

TOKEN_REF='${{ secrets.SENTRY_AUTH_TOKEN }}'
RC=0
CHECKS=0

fail() {
  echo "❌ [sentry-mapping] $*"
  RC=1
}

# expect_sentry_env <datei> <job> <step-teilstueck> <kurzname>
# Prueft die drei Werte, die das Gradle-Plugin braucht. "nicht gefunden"
# liefert bei wf_* immer `-` — ein Tippfehler im Job-/Step-Namen kann also
# nicht als bestandener Check durchgehen.
expect_sentry_env() {
  local file="$1" job="$2" step="$3" label="$4"
  local token org project

  token="$(wf_step_env "$file" "$job" "$step" SENTRY_AUTH_TOKEN)"
  org="$(wf_step_env "$file" "$job" "$step" SENTRY_ORG)"
  project="$(wf_step_env "$file" "$job" "$step" SENTRY_PROJECT)"

  if [[ "$token" != "$TOKEN_REF" ]]; then
    fail "$label: SENTRY_AUTH_TOKEN fehlt oder ist keine Secret-Referenz (job=$job, step=$step, gefunden: $token) — ohne Token ueberspringt das Gradle-Plugin den Mapping-Upload und Sentry bleibt obfusciert."
  fi
  if [[ "$org" == "-" || -z "$org" ]]; then
    fail "$label: SENTRY_ORG fehlt (job=$job, step=$step) — das Plugin kennt das Ziel-Organisation nicht."
  fi
  if [[ "$project" == "-" || -z "$project" ]]; then
    fail "$label: SENTRY_PROJECT fehlt (job=$job, step=$step) — das Plugin kennt das Ziel-Projekt nicht."
  fi
}

# --- M1: der Schalter selbst ------------------------------------------------
CHECKS=$((CHECKS + 1))
if [[ ! -f "$GRADLE" ]]; then
  fail "M1: $GRADLE fehlt — die Upload-Bedingung ist nicht pruefbar."
elif ! grep -qF 'autoUploadProguardMapping.set(!System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank())' "$GRADLE"; then
  fail "M1: $GRADLE koppelt autoUploadProguardMapping nicht mehr an SENTRY_AUTH_TOKEN (erwartet: autoUploadProguardMapping.set(!System.getenv(\"SENTRY_AUTH_TOKEN\").isNullOrBlank()))."
fi

# --- M2: Play-Kanal (die Builds, die die Feld-Events tragen) -----------------
CHECKS=$((CHECKS + 1))
expect_sentry_env "$REL" publish-play "Build AAB and upload to Google Play" "M2 (Play-Beta-Kanal)"
play_run="$(wf_step_run "$REL" publish-play "Build AAB and upload to Google Play")"
if [[ "$play_run" == "-" ]]; then
  fail "M2: run-Snippet des Play-Upload-Steps nicht lesbar — der Fail-closed-Block ist nicht nachweisbar."
elif ! grep -q 'SENTRY_AUTH_TOKEN' <<<"$play_run"; then
  fail "M2: der Play-Upload-Step prueft SENTRY_AUTH_TOKEN nicht selbst — ein fehlendes Secret bliebe ein stiller Skip."
elif ! grep -q '::error::' <<<"$play_run"; then
  fail "M2: der Play-Upload-Step bricht bei fehlendem Token nicht laut ab (kein ::error::)."
fi

# --- M3: Nightly-APK (fastlane release_github) -------------------------------
CHECKS=$((CHECKS + 1))
expect_sentry_env "$REL" publish-release "Build and publish release" "M3 (Nightly-APK)"

# --- M4: Release-APK auf main ------------------------------------------------
CHECKS=$((CHECKS + 1))
expect_sentry_env "$REL" build-release "Build Release APK" "M4 (Release-APK, main)"

# --- M5: woechentlicher Stable-Kanal ----------------------------------------
CHECKS=$((CHECKS + 1))
expect_sentry_env "$DIST" "publish-stable" "Build and publish stable release" "M5 (Stable-Kanal)"

if [[ $RC -eq 0 ]]; then
  echo "✅ [sentry-mapping] $CHECKS Nachweise gruen: der Mapping-Upload ist in allen Release-Kanaelen verdrahtet (M1–M5)."
fi
exit "$RC"
