#!/usr/bin/env bash
# Selbsttest der publish_play-Lane OHNE Play-Zugang (dry_run=true):
#   1. lokalen Wegwerf-Keystore erzeugen (keytool, deterministische Test-Passwörter)
#   2. UPLOAD_*-Env-Variablen auf den Test-Keystore setzen (keine echten Secrets nötig)
#   3. `fastlane publish_play dry_run:true` — baut bundleStandardPlayRelease und verifiziert
#      die AAB-Signatur per keytool gegen den Upload-Key (harter Fail bei Mismatch)
#   4. Assert: AAB existiert und Fingerprint (AAB-Signer == Upload-Key) stimmt
#   5. Negativtest: publish_play OHNE dry_run und ohne Play-Credentials muss am
#      Credential-Guard scheitern — der Upload-Pfad ist ohne Play-Zugang blockiert
#   6. Secrets-Guard: check_play_secrets.sh muss ready=false melden OHNE die
#      GITHUB_OUTPUT-Datei mit einem ::notice::-Schlüssel zu vergiften
#      (Vorfall 02.10.2026 — der Guard war korrekt, der Job trotzdem rot)
#
# Damit ist die Lane dauerhaft testbar, bevor echte UPLOAD_*/PLAY_*-Secrets
# existieren. Läuft im CI (release-pipeline.yml, Job "Self-Test publish_play")
# und lokal: bash scripts/test_publish_play_dryrun.sh  (Exit 0 = grün)
set -euo pipefail

# Bundler-Präfix: CI nutzt `bundle exec fastlane` (ruby/setup-ruby bundler-cache);
# lokal reicht ggf. ein direkt installiertes fastlane.
FASTLANE_CMD=("bundle" "exec" "fastlane")
if ! bundle --version >/dev/null 2>&1 && command -v fastlane >/dev/null 2>&1; then
  FASTLANE_CMD=("fastlane")
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

KEYSTORE="$TMP/test-upload.jks"
# Bewusst KEINE camelCase-Namen (storePassword/keyPassword): der Secret-Guard
# wertet genau diese Zuweisungen als Klartext-Secrets aus.
# Store- und Key-Passwort BEWUSST identisch: modernes keytool (JDK 9+) erzeugt
# standardmaessig PKCS12-Keystores, in denen -keypass ignoriert wird (der Schluessel
# ist mit dem Store-Passwort geschuetzt) - unterschiedliche Passwoerter wuerden den
# Signatur-Check der Lane mit "bad key during decryption" scheitern lassen. Das
# entspricht der Empfehlung in RELEASE.md (PKCS12: storepass == keypass).
KEYSTORE_PASSWORD="test-upload-password-123"
KEY_ALIAS="upload"
KEY_PASSWORD="$KEYSTORE_PASSWORD"

echo "==> Test-Keystore erzeugen ($KEYSTORE)"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 2048 -validity 365 -sigalg SHA256withRSA \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
  -dname "CN=Vivid Test Upload, O=Vivid, C=DE" >/dev/null

echo "==> publish_play dry_run:true (bundleStandardPlayRelease + keytool-Verifikation)"
export UPLOAD_KEYSTORE_PATH="$KEYSTORE"
export UPLOAD_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
export UPLOAD_KEY_ALIAS="$KEY_ALIAS"
export UPLOAD_KEY_PASSWORD="$KEY_PASSWORD"

"${FASTLANE_CMD[@]}" publish_play \
  dry_run:true \
  version:0.0.1-test \
  version_code:1 \
  track:alpha

AAB="app/build/outputs/bundle/standardPlayRelease/app-standard-playRelease.aab"
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

# Regression Secrets-Guard (Vorfall 02.10.2026): der Workflow-Step leitet stdout
# nach $GITHUB_OUTPUT um. Landet die ::notice::-Meldung dort, parst der
# Actions-Runner die Datei strikt als key=value, scheitert mit "Unable to
# process file command 'output' successfully" und macht den Job rot — obwohl
# der Guard ready=false gemeldet hat und der Play-Upload korrekt übersprungen
# worden wäre. Deshalb: Notice nach stderr, stdout bleibt reines key=value.
echo "==> Secrets-Guard: ready=false ohne ::notice:: in GITHUB_OUTPUT"
GHO="$TMP/gh_output"
: > "$GHO"
# Das `>> "$GHO"` bildet den Workflow-Step nach ("run: bash
# scripts/check_play_secrets.sh >> \"$GITHUB_OUTPUT\"") — genau diese Umleitung
# ist es, die den Runner-Parser überhaupt erst erreicht.
env -u PLAY_JSON_KEY_FILE -u PLAY_JSON_KEY_DATA \
    UPLOAD_KEYSTORE_BASE64=dummy \
    UPLOAD_KEYSTORE_PASSWORD=dummy \
    UPLOAD_KEY_ALIAS=dummy \
    UPLOAD_KEY_PASSWORD=dummy \
    bash scripts/check_play_secrets.sh >> "$GHO"
if ! grep -qx 'ready=false' "$GHO"; then
  echo "::error::Guard meldete nicht ready=false, sondern:"
  cat "$GHO"
  exit 1
fi
if grep -q '^::' "$GHO"; then
  echo "::error::GITHUB_OUTPUT enthaelt einen ::notice::-Schlaessel:"
  cat "$GHO"
  echo "   -> der Actions-Runner bricht damit mit 'Unable to process file command' ab."
  exit 1
fi
if [ "$(wc -l < "$GHO")" -ne 1 ]; then
  echo "::error::GITHUB_OUTPUT muss genau eine Zeile (ready=false) enthalten, hat $(wc -l < "$GHO"):"
  cat "$GHO"
  exit 1
fi
echo "==> Secrets-Guard ok: 1 Zeile key=value, Notice auf stderr"

echo "✅ publish_play-Selbsttest bestanden (AAB gebaut + Signatur verifiziert, kein Upload)"
