#!/usr/bin/env bash
# Bereitet die Play-Upload-Secrets für den ersten Play-Upload vor
# (UPLOAD_KEYSTORE_BASE64, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS,
#  UPLOAD_KEY_PASSWORD, PLAY_JSON_KEY_FILE, PLAY_JSON_KEY_DATA).
#
# Setzt den dokumentierten Ablauf aus RELEASE.md („🔑 Secrets für den ersten
# Play-Upload vorbereiten“, Schritte B–E) praktisch um:
#   1. Upload-Keystore lokalisieren (oder neu erzeugen, `--generate`)
#   2. base64-kodieren → upload-keystore.jks.b64 (eine Zeile) + Fingerprint anzeigen
#   3. upload_cert.pem exportieren (für die Play Console, enthält KEINEN privaten Key)
#   4. GitHub-Secrets setzen (`--set`, via gh, Werte werden NIE ausgegeben)
#   5. Verifikation: Guard, gh secret list, Fingerprint-Abgleich
#
# OHNE `--set` läuft das Skript nur in der Vorbereitungs-/Verifikationsphase
# (Schritte 1–3 + 5) und druckt die auszuführenden gh-Befehle — ohne Werte zu
# zeigen. Mit `--set` werden die Secrets direkt in GitHub hinterlegt.
#
# Sicherheit: Secret-Werte werden ausschließlich aus Dateien / stdin gelesen
# und niemals ins Log geschrieben; Passwörter kommen aus Env oder interaktiv
# (read -s). Der Secret-Guard (scripts/guard_secrets.sh) prüft anschließend,
# dass nichts ins Repo gelangt ist.
set -euo pipefail

# ── Konfiguration ────────────────────────────────────────────────────────────
KEYSTORE="${KEYSTORE_PATH:-upload-keystore.jks}"
B64_FILE="upload-keystore.jks.b64"
CERT_FILE="upload_cert.pem"
ALIAS="${UPLOAD_KEY_ALIAS:-upload}"
DO_SET=0
GENERATE=0
SERVICE_ACCOUNT_JSON=""

usage() {
  cat <<'EOF'
Verwendung: bash scripts/prepare_play_secrets.sh [Optionen]

  --keystore <pfad>   Upload-Keystore (Default: upload-keystore.jks bzw. $KEYSTORE_PATH)
  --json <datei>      Service-Account-JSON (wird PLAY_JSON_KEY_DATA; Pfad wird PLAY_JSON_KEY_FILE)
  --generate          Keystore neu erzeugen, wenn er nicht existiert (keytool)
  --set               Secrets tatsächlich in GitHub setzen (gh); ohne: nur vorbereiten + prüfen
  -h, --help          Diese Hilfe

Voraussetzungen: JDK 17 (keytool), gh (authentifiziert), bei --set zusätzlich
die Passwörter als Env: UPLOAD_KEYSTORE_PASSWORD / UPLOAD_KEY_PASSWORD
(ohne Env werden sie interaktiv per read -s abgefragt — nie als Argument).
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore) KEYSTORE="$2"; shift 2 ;;
    --json)     SERVICE_ACCOUNT_JSON="$2"; shift 2 ;;
    --generate) GENERATE=1; shift ;;
    --set)      DO_SET=1; shift ;;
    -h|--help)  usage; exit 0 ;;
    *) echo "❌ Unbekannte Option: $1"; usage; exit 1 ;;
  esac
done

# ── Voraussetzungen ──────────────────────────────────────────────────────────
for cmd in keytool gh base64; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "❌ '$cmd' fehlt — Voraussetzung nicht erfüllt (JDK 17 + GitHub CLI)."
    exit 1
  fi
done
if ! gh auth status >/dev/null 2>&1; then
  echo "❌ gh ist nicht authentifiziert — bitte `gh auth login`."
  exit 1
fi

# ── Schritt 1: Keystore lokalisieren / erzeugen ─────────────────────────────
if [ ! -f "$KEYSTORE" ]; then
  if [ "$GENERATE" -eq 1 ]; then
    # Env erlaubt ein nicht-interaktives Erzeugen; ohne Env interaktiv (read -s).
    if [ -z "${UPLOAD_KEYSTORE_PASSWORD:-}" ]; then
      read -r -s -p "Store-Passwort für den neuen Keystore: " UPLOAD_KEYSTORE_PASSWORD; echo
      read -r -s -p "Key-Passwort (PKCS12: identisch zum Store-Passwort empfehlen): " UPLOAD_KEY_PASSWORD; echo
    fi
    UPLOAD_KEY_PASSWORD="${UPLOAD_KEY_PASSWORD:-$UPLOAD_KEYSTORE_PASSWORD}"
    keytool -genkeypair -v \
      -keystore "$KEYSTORE" -alias "$ALIAS" \
      -keyalg RSA -keysize 4096 -validity 10000 -sigalg SHA256withRSA \
      -storepass "$UPLOAD_KEYSTORE_PASSWORD" -keypass "$UPLOAD_KEY_PASSWORD" \
      -dname "CN=Vivid Play Upload, O=Vivid, C=DE" >/dev/null
    echo "✅ Keystore erzeugt: $KEYSTORE (Alias '$ALIAS')"
  else
    echo "❌ Keystore nicht gefunden: $KEYSTORE"
    echo "   Entweder --generate verwenden oder nach RELEASE.md (Abschnitt"
    echo "   „🔐 Play-Upload-Keystore erzeugen & UPLOAD_*-Secrets hinterlegen“) erzeugen."
    exit 1
  fi
else
  echo "✅ Keystore vorhanden: $KEYSTORE"
fi

# Store-Passwort für die Verifikations-Schritte (Fingerprint/B64) besorgen.
if [ -z "${UPLOAD_KEYSTORE_PASSWORD:-}" ]; then
  read -r -s -p "Store-Passwort des Keystores: " UPLOAD_KEYSTORE_PASSWORD; echo
fi

# ── Schritt 2: base64-kodieren + Fingerprint ────────────────────────────────
echo "==> base64-kodieren → $B64_FILE"
base64 -w 0 "$KEYSTORE" > "$B64_FILE"
# awk statt wc -l: GNU base64 schreibt keine Trailing-Newline, wc -l zählt dann 0 Zeilen.
if [ "$(awk 'END { print NR }' "$B64_FILE")" -ne 1 ]; then
  echo "❌ $B64_FILE ist nicht eine einzige Zeile — bitte prüfen."
  exit 1
fi
echo "✅ $B64_FILE: $(wc -c < "$B64_FILE") Zeichen, eine Zeile"

FP=$(keytool -list -v -keystore "$KEYSTORE" -storepass "$UPLOAD_KEYSTORE_PASSWORD" -alias "$ALIAS" 2>/dev/null \
       | grep '^[[:space:]]*SHA256:' | sed 's/.*SHA256:[[:space:]]*//' | tr -d ':' | tr '[:upper:]' '[:lower:]')
if [ -z "$FP" ]; then
  echo "❌ Fingerprint nicht lesbar (falsches Passwort/Alias '$ALIAS'?)"
  exit 1
fi
echo "Upload-Key SHA-256: $FP  (mit der Play Console abgleichen!)"

# ── Schritt 3: Zertifikat für die Play Console exportieren ──────────────────
echo "==> Zertifikat exportieren → $CERT_FILE (nur öffentlich, für Play Console)"
keytool -export -rfc -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$UPLOAD_KEYSTORE_PASSWORD" -file "$CERT_FILE" >/dev/null
echo "✅ $CERT_FILE exportiert — in der Play Console unter"
echo "   Setup → App-Integrität → App-Signierung → „Upload-Key-Zertifikat“ hochladen."

# ── Schritt 4: GitHub-Secrets setzen (nur mit --set) ────────────────────────
set_secret() { # $1 = Name; Wert kommt via stdin (nie als Argument)
  local name="$1"
  printf '%s' "$(cat)" | gh secret set "$name"
  echo "✅ $name gesetzt"
}

if [ "$DO_SET" -eq 1 ]; then
  if [ -z "${UPLOAD_KEY_PASSWORD:-}" ]; then
    read -r -s -p "Key-Passwort (UPLOAD_KEY_PASSWORD): " UPLOAD_KEY_PASSWORD; echo
  fi
  echo "==> Secrets in GitHub setzen (Repo: $(gh repo view --json nameWithOwner -q .nameWithOwner))"
  set_secret UPLOAD_KEYSTORE_BASE64 < "$B64_FILE"
  set_secret UPLOAD_KEYSTORE_PASSWORD <<< "$UPLOAD_KEYSTORE_PASSWORD"
  set_secret UPLOAD_KEY_ALIAS <<< "$ALIAS"
  set_secret UPLOAD_KEY_PASSWORD <<< "$UPLOAD_KEY_PASSWORD"
  if [ -n "$SERVICE_ACCOUNT_JSON" ]; then
    if [ ! -f "$SERVICE_ACCOUNT_JSON" ]; then
      echo "❌ Service-Account-JSON nicht gefunden: $SERVICE_ACCOUNT_JSON"
      exit 1
    fi
    set_secret PLAY_JSON_KEY_DATA < "$SERVICE_ACCOUNT_JSON"
    set_secret PLAY_JSON_KEY_FILE <<< "$SERVICE_ACCOUNT_JSON"
    echo "ℹ️  PLAY_JSON_KEY_FILE ist nur für lokale Läufe gedacht — im CI wird"
    echo "   PLAY_JSON_KEY_DATA genutzt (Pfad existiert auf dem Runner nicht)."
  else
    echo "⚠️  PLAY_JSON_KEY_FILE / PLAY_JSON_KEY_DATA NICHT gesetzt — bitte mit"
    echo "   --json <service-account.json> nachreichen (Anleitung RELEASE.md Schritt C)."
  fi
else
  echo
  echo "==> Vorbereitung abgeschlossen (ohne --set). Auszuführende Befehle:"
  echo
  echo "  gh secret set UPLOAD_KEYSTORE_BASE64 < $B64_FILE"
  echo "  gh secret set UPLOAD_KEYSTORE_PASSWORD   # Store-Passwort"
  echo "  gh secret set UPLOAD_KEY_ALIAS           # $ALIAS"
  echo "  gh secret set UPLOAD_KEY_PASSWORD        # Key-Passwort"
  echo "  gh secret set PLAY_JSON_KEY_DATA < <service-account.json>"
  echo "  gh secret set PLAY_JSON_KEY_FILE         # nur lokal nötig"
  echo
  echo "   …oder erneut mit --set ausführen (Werte aus Dateien, nie ins Log)."
fi

# ── Schritt 5: Verifikation ─────────────────────────────────────────────────
echo "==> Verifikation"
bash scripts/guard_secrets.sh
echo "==> gh secret list:"
gh secret list
echo
echo "✅ Fertig — Abgleich: Upload-Key-SHA256 oben == Play Console. Danach"
echo "   publish-play per workflow_dispatch mit dry_run=true testen (RELEASE.md Testplan)."
