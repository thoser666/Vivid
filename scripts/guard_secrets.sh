#!/usr/bin/env bash
# Guard: verhindert, dass Keystores/Secrets ins Git-Repo gelangen.
# Läuft im CI (android.yml) und lokal — Exit-Code 0 = sauber, 1 = Verstoß.
#
# Zwei Schutzebenen:
#   1) Dateinamen-Guard  : verbotene Dateien (getrackt ODER gestaged)
#   2) Content-Guard     : Klartext-Secrets im Inhalt getrackter Dateien
#                          (git grep — nur getrackte Dateien, kein Arbeitsbaum-Rauschen)
#
# Bewusst PRÄZISE Muster (keine False-Positives):
#   - Debug-Werte im getrackten fastlane/.env.default (KEYSTORE_PASSWORD=android)
#     und Platzhalter in der Doku (KEYSTORE_PASSWORD=<store-password>) sind erlaubt
#   - Nur echte Secrets triggern: reale Keystore-Dateien, storePassword=/keyPassword=
#     mit Wert, GitHub-/AWS-/Sentry-Token, private Schlüsselblöcke
set -euo pipefail

# ── Konfiguration ────────────────────────────────────────────────────────────
# Dateinamen/Globs, die NIE ins Repo dürfen (nur Dateinamen-Basename, kein Pfad)
FORBIDDEN_NAMES=(
  "keystore.properties"
  "local.properties"
  "sentry.properties"
  "crashlytics-build.properties"
  "debug.keystore"
  "release.keystore"
)
FORBIDDEN_EXT=(
  "jks"
  "keystore"
  "p12"
  "pfx"
  "pem"
)
# Env-Dateien mit echten Secrets (nicht .env.default — das ist dokumentiert/dev)
FORBIDDEN_ENV_FILES=(
  "fastlane/.env.release"
  "fastlane/.env.production"
)

# ── Hilfsfunktionen ──────────────────────────────────────────────────────────
FAIL=0
violation() { # $1 = Beschreibung, $2 = Datei
  echo "❌ [guard] $1: $2"
  FAIL=1
}

# ── 1) Dateinamen-Guard ──────────────────────────────────────────────────────
# Getrackte Dateien (auch via `git add -f` eingecheckte ignorierte Dateien)
# + aktuell gestagte Dateien (git add ohne Commit — der häufigste Unfall).
# Ignorierte, aber ungetrackte Dateien im Arbeitsbaum (z. B. lokale
# keystore.properties) sind hier bewusst KEIN Fehler — sie können ohne `-f`
# nicht committet werden und .gitignore verhindert das ohnehin.
check_names() { # $1 = Quelle ("getrackt"/"gestaged"); Liste kommt via stdin (NUL-terminiert)
  local src="$1" f base ext
  # Warnung: die Liste enthält NUL-Bytes und darf NICHT in eine Variable
  # (Command-Substitution schneidet am ersten NUL ab) — daher via stdin.
  while IFS= read -r -d '' f; do
    base="$(basename "$f")"
    ext="${base##*.}"
    for n in "${FORBIDDEN_NAMES[@]}"; do
      if [ "$base" = "$n" ]; then
        violation "Verbotene Datei ($src)" "$f"
      fi
    done
    for e in "${FORBIDDEN_EXT[@]}"; do
      if [ "$ext" = "$e" ] && [ "$base" != "$ext" ]; then
        violation "Keystore-Datei ($src)" "$f"
      fi
    done
    for ef in "${FORBIDDEN_ENV_FILES[@]}"; do
      if [ "$f" = "$ef" ]; then
        violation "Env-Datei mit Secrets ($src)" "$f"
      fi
    done
  done
}

check_names "getrackt" < <(git ls-files -z)
check_names "gestaged" < <(git diff --cached --name-only -z 2>/dev/null || true)

# ── 2) Content-Guard (nur getrackte Dateien) ─────────────────────────────────
# Kein git grep bei leeren Repos/keinen Dateien abbrechen lassen
if git ls-files | grep -q .; then
  # a) Signing-Passwörter im Properties-Stil — nur MIT realem Literal-Wert
  #    Erlaubt (kein Verstoß): Platzhalter (<store-password>, ..., leer, auch in
  #    Anführungszeichen) und Variablen-Referenzen (build.gradle.kts weist
  #    Env-Variablen zu, z. B. `storePassword = keystorePassword` — KEIN Literal).
  #    Alles andere mit nicht-leerem Wert gilt als echtes Klartext-Secret.
  is_placeholder() { # $1 = Wert → 0 wenn erlaubt (Platzhalter/Variable), 1 wenn verdächtig
    [ -z "$1" ] && return 0
    # Anführungszeichen abstreifen (storePassword = "<store-password>")
    local v="${1#\"}"; v="${v%\"}"; v="${v#\'}"; v="${v%\'}"
    echo "$v" | grep -qE '^<|^\.\.\.$|^\$|^(keystorePassword|keyPassword|keystorePath|keyAlias)$'
  }
  while IFS=: read -r f rest; do
    # rest enthält "Zeile:Text" — Wert hinter '=' extrahieren
    line="${rest%%:*}"
    text="${rest#*:}"
    value="$(echo "$text" | sed -n 's/^[[:space:]]*storePassword[[:space:]]*=[[:space:]]*//p' | head -1)"
    if ! is_placeholder "$value"; then
      violation "Klartext storePassword" "$f:$line"
    fi
    value="$(echo "$text" | sed -n 's/^[[:space:]]*keyPassword[[:space:]]*=[[:space:]]*//p' | head -1)"
    if ! is_placeholder "$value"; then
      violation "Klartext keyPassword" "$f:$line"
    fi
  done < <(git grep -n -E '(storePassword|keyPassword)[[:space:]]*=' 2>/dev/null || true)

  # b) Echte Secrets — hochspezifische Token-Formate.
  #    WICHTIG: Process Substitution statt Pipe — sonst läuft der while-Loop in
  #    einer Subshell und FAIL=1 geht verloren. (|| true: git grep ohne Treffer
  #    liefert Exit 1 — mit pipefail kein Abbruch.)
  while IFS=: read -r f rest; do
    violation "GitHub-Token im Klartext" "$f:${rest%%:*}"
  done < <(git grep -n -E 'gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}' 2>/dev/null || true)
  while IFS=: read -r f rest; do
    violation "AWS Access Key im Klartext" "$f:${rest%%:*}"
  done < <(git grep -n -E 'AKIA[0-9A-Z]{16}' 2>/dev/null || true)
  while IFS=: read -r f rest; do
    violation "Sentry-Auth-Token im Klartext" "$f:${rest%%:*}"
  done < <(git grep -n -E 'sntrys_[A-Za-z0-9_]{16,}' 2>/dev/null || true)
  while IFS=: read -r f rest; do
    violation "Privater Schlüsselblock" "$f:${rest%%:*}"
  done < <(git grep -n -E -- '-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY' 2>/dev/null || true)
fi

# ── Ergebnis ─────────────────────────────────────────────────────────────────
if [ "$FAIL" -eq 1 ]; then
  echo "❌ [guard] Secrets/Keystores im Repo gefunden — Abbruch."
  exit 1
fi
echo "✅ [guard] Keine Keystores oder Klartext-Secrets gefunden."
