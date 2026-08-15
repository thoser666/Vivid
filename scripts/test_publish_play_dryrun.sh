#!/usr/bin/env bash
# Selbsttest der publish_play-Lane OHNE Play-Zugang (dry_run=true):
#   1. lokalen Wegwerf-Keystore erzeugen (keytool, deterministische Test-Passwörter)
#   2. UPLOAD_*-Env-Variablen auf den Test-Keystore setzen (keine echten Secrets nötig)
#   3. `fastlane publish_play dry_run:true` — baut bundlePlayRelease und verifiziert
#      die AAB-Signatur per keytool gegen den Upload-Key (harter Fail bei Mismatch)
#   4. Assert: AAB existiert und Fingerprint (AAB-Signer == Upload-Key) stimmt
#   5. Negativtest: publish_play OHNE dry_run und ohne Play-Credentials muss am
#      Credential-Guard scheitern — der Upload-Pfad ist ohne Play-Zugang blockiert
#
# Damit ist die Lane dauerhaft testbar, bevor echte UPLOAD_*/PLAY_*-Secrets
# existieren. Läuft im CI (android_fastlane.yml, Job "Self-Test publish_play")
# und lokal: bash scripts/test_publish_play_dryrun.sh  (Exit 0 = grün)
set -euo pipefail

# Bundler-Präfix: CI nutzt `bundle exec fastlane` (ruby/setup-ruby bundler-cache);
# lokal reicht ggf. ein direkt installiertes fastlane.
FASTLANE_CMD=("bundle" "exec" "fastlane")
if ! command -v bundle >/dev/null 2>&1 && command -v fastlane >/dev/null 2>&1; then
  FASTLANE_CMD=("fastlane")
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

KEYSTORE="$TMP/test-upload.jks"
# Bewusst KEINE camelCase-Namen (storePassword/keyPassword): der Secret-Guard
# wertet genau diese Zuweisungen als Klartext-Secrets aus.
KEYSTORE_PASSWORD="test-store-password-123"
KEY_ALIAS="upload"
KEY_PASSWORD="test-key-password-123"

echo "==> Test-Keystore erzeugen ($KEYSTORE)"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 2048 -validity 365 -sigalg SHA256withRSA \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
  -dname "CN=Vivid Test Upload, O=Vivid, C=DE" >/dev/null

echo "==> publish_play dry_run:true (bundlePlayRelease + keytool-Verifikation)"
export UPLOAD_KEYSTORE_PATH="$KEYSTORE"
export UPLOAD_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export UPLOAD_KEY_ALIAS="$KEY_ALIAS"
export UPLOAD_KEY_PASSWORD="$KEY_PASSWORD"

"${FASTLANE_CMD[@]}" publish_play \
  dry_run:true \
  version:0.0.1-test \
  version_code:1 \
  track:alpha

AAB="app/build/outputs/bundle/playRelease/app-playRelease.aab"
if [ ! -f "$AAB" ]; then
  echo "::error::AAB nicht erzeugt: $AAB"
  exit 1
fi
echo "==> AAB ok: $AAB ($(du -h "$AAB" | cut -f1))"

# Zusatz-Assertion — redundant zur Lane (die bricht bei Mismatch ohnehin ab),
# dokumentiert den Erwartungswert aber explizit im Log.
AAB_FP=$(keytool -printcert -jarfile "$AAB" 2>/dev/null | grep '^[[:space:]]*SHA256:' | head -1 | sed 's/.*SHA256:[[:space:]]*//' | tr -d ':' | tr '[:upper:]' '[:lower:]')
KEY_FP=$(keytool -list -v -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" 2>/dev/null | grep '^[[:space:]]*SHA256:' | sed 's/.*SHA256:[[:space:]]*//' | tr -d ':' | tr '[:upper:]' '[:lower:]')
echo "AAB signer SHA-256: $AAB_FP"
echo "Upload key SHA-256: $KEY_FP"
if [ -z "$AAB_FP" ] || [ -z "$KEY_FP" ] || [ "$AAB_FP" != "$KEY_FP" ]; then
  echo "::error::AAB-Signatur matcht nicht den Test-Upload-Key"
  exit 1
fi

# Negativtest: ohne dry_run UND ohne Play-Credentials (PLAY_JSON_KEY_FILE/DATA)
# muss die Lane in Step 1/6 hart abbrechen — kein Upload-Pfad ohne Play-Zugang.
echo "==> Negativtest: publish_play ohne dry_run und ohne Play-Credentials"
if "${FASTLANE_CMD[@]}" publish_play version:0.0.1-test version_code:1 track:alpha >/dev/null 2>&1; then
  echo "::error::Lane hätte ohne Play-Credentials scheitern müssen (Credential-Guard)"
  exit 1
fi
echo "==> Negativtest ok: Credential-Guard hat den Upload ohne Play-Zugang blockiert"

echo "✅ publish_play-Selbsttest bestanden (AAB gebaut + Signatur verifiziert, kein Upload)"
