#!/usr/bin/env bash
# Tests für scripts/guard_secrets.sh — deterministisch, ohne Netzwerk:
# ein Sandbox-Git-Repo simuliert Verstöße, der Guard muss sie finden.
# Aufruf: bash scripts/test_guard_secrets.sh   (Exit-Code 0 = alle grün)
#
# Getestet wird:
#   T1  Sauberes Repo          -> Exit 0, keine Verstöße
#   T2  keystore.properties    -> Dateinamen-Guard (getrackt) schlägt an
#   T3  *.jks (git add -f)     -> Dateinamen-Guard (getrackt via -f) schlägt an
#   T4  *.pem                  -> Dateinamen-Guard (getrackt) schlägt an
#   T5  gestagte .jks          -> Dateinamen-Guard (gestaged, noch nicht committet)
#   T6  storePassword=Literal  -> Content-Guard schlägt an
#   T7  GitHub-Token           -> Content-Guard (ghp_) schlägt an
#   T8  Privater Schlüssel     -> Content-Guard (BEGIN ... PRIVATE KEY) schlägt an
#   T9  Platzhalter/Variablen  -> KEIN Verstoß (storePassword=<pw> / =keystorePassword)
#   T10 .env.default-Debugwerte-> KEIN Verstoß (KEYSTORE_PASSWORD=android)
#   T11 upload-keystore.jks    -> Dateinamen-Guard (Play-Upload-Key)
#   T12 play-credentials.json  -> Dateinamen-Guard (Play-Service-Account)
#   T13 fastlane/.env.play     -> Env-Datei-Guard (Play-Upload-Secrets)
#   T14 RELEASE.md-Kotlin-Snippet (UPLOAD_*-Env-Vals) -> KEIN Verstoß
#   T15 Fastfile-Env-Interpolation (keytool, ENV[..])  -> KEIN Verstoß
#   T16 PEM-Zertifikat (BEGIN CERTIFICATE)             -> KEIN Verstoß (nur PRIVATE KEY)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD="$SCRIPT_DIR/guard_secrets.sh"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

PASS=0
FAIL=0
check() { # $1=Name, $2=erwarteter Exit (0/1), rest=Kommando
  local name="$1" want="$2"
  shift 2
  if "$@" >/dev/null 2>&1; then got=0; else got=1; fi
  if [ "$got" -eq "$want" ]; then
    PASS=$((PASS + 1))
    echo "PASS: $name"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL: $name (erwartet Exit $want, bekommen $got)"
  fi
}

# Test-Repo im Sandbox aufsetzen
setup_repo() {
  rm -rf "$SANDBOX/repo"
  mkdir -p "$SANDBOX/repo"
  cd "$SANDBOX/repo"
  git init -q
  git config user.email test@example.com
  git config user.name "Test"
  # Basis-Content: harmlos + die erlaubten Muster aus dem echten Repo
  mkdir -p app fastlane scripts
  cat > app/build.gradle.kts <<'EOF'
storePassword = keystorePassword
keyPassword = keyPassword
EOF
  cat > fastlane/.env.default <<'EOF'
KEYSTORE_PASSWORD=android
KEY_ALIAS=androiddebugkey
EOF
  cat > scripts/note.md <<'EOF'
Doku: KEYSTORE_PASSWORD=<store-password> ist nur ein Platzhalter.
EOF
  git add -A
  git commit -qm init
}

# T1: sauber
setup_repo
check "T1 sauberes Repo -> Exit 0" 0 bash "$GUARD"

# T2: keystore.properties getrackt
setup_repo
echo "storePassword=SuperSecret123" > keystore.properties
git add keystore.properties
git commit -qm bad
check "T2 keystore.properties getrackt -> Exit 1" 1 bash "$GUARD"

# T3: *.jks via git add -f (ignoriert, aber gezwungen eingecheckt)
setup_repo
mkdir -p keystores
printf '\x00\x01' > keystores/release.jks
git add -f keystores/release.jks
git commit -qm bad
check "T3 release.jks via -f getrackt -> Exit 1" 1 bash "$GUARD"

# T4: *.pem getrackt
setup_repo
echo "-----BEGIN PRIVATE KEY-----" > key.pem
git add key.pem
git commit -qm bad
check "T4 key.pem getrackt -> Exit 1" 1 bash "$GUARD"

# T5: .jks gestaged, aber noch nicht committet
setup_repo
printf '\x00\x01' > debug.keystore
git add debug.keystore
check "T5 debug.keystore gestaged -> Exit 1" 1 bash "$GUARD"

# T6: storePassword-Literal in getrackter Datei
setup_repo
echo "storePassword=H4x0rPass!" > app/secret.properties
git add app/secret.properties
git commit -qm bad
check "T6 storePassword-Literal -> Exit 1" 1 bash "$GUARD"

# T7: GitHub-Token
setup_repo
echo "token=ghp_1234567890abcdefghijklmnopqrstuvwxyz" > notes.txt
git add notes.txt
git commit -qm bad
check "T7 GitHub-Token -> Exit 1" 1 bash "$GUARD"

# T8: Privater Schlüsselblock
setup_repo
cat > id_rsa <<'EOF'
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAA
-----END OPENSSH PRIVATE KEY-----
EOF
git add id_rsa
git commit -qm bad
check "T8 OpenSSH-Private-Key -> Exit 1" 1 bash "$GUARD"

# T9: Platzhalter + Variablen-Referenzen sind KEINE Verstöße
setup_repo
cat > app/build.gradle.kts <<'EOF'
storePassword = keystorePassword
storePassword = "<store-password>"
keyPassword = ...
EOF
git add app/build.gradle.kts
git commit -qm ok
check "T9 Platzhalter/Variablen -> Exit 0" 0 bash "$GUARD"

# T10: .env.default-Debug-Werte (KEYSTORE_PASSWORD=android) sind kein Verstoß
setup_repo
cat > fastlane/.env.default <<'EOF'
KEYSTORE_PASSWORD=android
KEY_ALIAS=androiddebugkey
KEY_PASSWORD=android
EOF
git add fastlane/.env.default
git commit -qm ok
check "T10 .env.default-Debug-Werte -> Exit 0" 0 bash "$GUARD"

# T11: upload-keystore.jks (Play-Upload-Key) getrackt
setup_repo
mkdir -p keystores
printf '\x00\x01' > keystores/upload-keystore.jks
git add -f keystores/upload-keystore.jks
git commit -qm bad
check "T11 upload-keystore.jks -> Exit 1" 1 bash "$GUARD"

# T12: play-credentials.json (Play-Service-Account mit private_key) getrackt
setup_repo
cat > play-credentials.json <<'EOF'
{
  "type": "service_account",
  "private_key": "-----BEGIN PRIVATE KEY-----\nFAKE\n-----END PRIVATE KEY-----\n"
}
EOF
git add play-credentials.json
git commit -qm bad
check "T12 play-credentials.json -> Exit 1" 1 bash "$GUARD"

# T13: fastlane/.env.play (Play-Upload-Env mit echten Secrets) getrackt
setup_repo
cat > fastlane/.env.play <<'EOF'
UPLOAD_KEYSTORE_PASSWORD=SuperSecret
PLAY_JSON_KEY_DATA={"type":"service_account"}
EOF
git add fastlane/.env.play
git commit -qm bad
check "T13 fastlane/.env.play -> Exit 1" 1 bash "$GUARD"

# T14: RELEASE.md-Kotlin-Snippet (upload-SigningConfig mit UPLOAD_*-Env-Vals) ist KEIN Verstoß
setup_repo
mkdir -p docs
cat > docs/play-signing.md <<'EOF'
```kotlin
android {
    signingConfigs {
        create("upload") {
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_PATH")
            val keystorePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            val keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
}
```
EOF
git add docs/play-signing.md
git commit -qm ok
check "T14 RELEASE.md-Kotlin-Snippet (UPLOAD_*-Vals) -> Exit 0" 0 bash "$GUARD"

# T15: Fastfile-publish_play-Snippet (Env-Interpolation statt Literalen) ist KEIN Verstoß
setup_repo
mkdir -p fastlane
cat > fastlane/Fastfile <<'EOF'
key_fp = `keytool -list -v -keystore "#{keystore}" -storepass "#{ENV["UPLOAD_KEYSTORE_PASSWORD"]}" -alias "#{ENV["UPLOAD_KEY_ALIAS"]}" 2>/dev/null | grep 'SHA256:'`.strip
UI.user_error!("UPLOAD_KEYSTORE_PASSWORD must be set") if ENV["UPLOAD_KEYSTORE_PASSWORD"].to_s.empty?
supply_opts[:json_key] = ENV["PLAY_JSON_KEY_DATA"] if ENV["PLAY_JSON_KEY_DATA"].to_s != ""
EOF
git add fastlane/Fastfile
git commit -qm ok
check "T15 Fastfile-Env-Interpolation -> Exit 0" 0 bash "$GUARD"

# T16: PEM-Zertifikat (oeffentlich, BEGIN CERTIFICATE) ist KEIN Privatkey-Verstoß
setup_repo
mkdir -p docs
cat > docs/cert-example.txt <<'EOF'
-----BEGIN CERTIFICATE-----
MIIBmTCCAT+gAwIBAgIUEXAMPLEONLYNOTAREALCERT
-----END CERTIFICATE-----
EOF
git add docs/cert-example.txt
git commit -qm ok
check "T16 CERTIFICATE-Block (kein PRIVATE KEY) -> Exit 0" 0 bash "$GUARD"

# T17: CRLF-Zeilenenden am Ende von Werten werden ignoriert
# (typisch fuer Dependabot-PRs mit Windows-Zeilenenden)
setup_repo
mkdir -p docs
echo -ne 'storePassword = keystorePassword
' > docs/play-signing.md
echo -ne 'keyPassword = keyPassword
' >> docs/play-signing.md
git add docs/play-signing.md
git commit -qm ok
check "T17 CRLF-Zeilenenden bei Variablen -> Exit 0" 0 bash "$GUARD"

# ── Ergebnis ─────────────────────────────────────────────────────────────────
echo "----------------------------------------"
echo "guard_secrets.sh: $PASS bestanden, $FAIL fehlgeschlagen"
[ "$FAIL" -eq 0 ] || exit 1
echo "✅ Alle Guard-Tests grün"
