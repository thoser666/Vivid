#!/usr/bin/env bash
# Manifest-Security-Guard (Vivid): schützt die Backup-Konfiguration der App.
#
#   C1: allowBackup muss EXPLIZIT "false" sein (attributfrei == true == Backup
#       aller App-Daten inkl. OAuth-Tokens; Code-Scanning-Finding #471).
#   C2: Backup-Verdrahtung muss real schützen. Attribute sind erlaubt
#       (Lint DataExtractionRules verlangt sie ab Android 12+), aber jede
#       verdrahtete Datei muss existieren und mindestens eine echte
#       <exclude>-Regel enthalten. Leere Template-Rules (Stand vor
#       2026-09-12) verdrahteten keinen Schutz und täuschten ihn vor
#       (Finding #471) — genau das bleibt verboten.
#   C3: MediaProjection-FGS-Typ — die Screen-Capture-Quelle braucht
#       FOREGROUND_SERVICE_MEDIA_PROJECTION + foregroundServiceType
#       mediaProjection (Android 14+, VIVID-39/#221).
#
# Aufruf: bash scripts/check_manifest_security.sh [PFAD_ZUM_MANIFEST]
# Selbsttest: bash scripts/test_manifest_security.sh
set -euo pipefail

cd "$(dirname "$0")/.."
MANIFEST="${1:-app/src/main/AndroidManifest.xml}"

fail() { echo "❌ [manifest-security] $1"; exit 1; }

[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml nicht gefunden: $MANIFEST"

# C1: allowBackup muss explizit false sein (attributfrei == true == Backup des gesamten App-Store einschließlich OAuth-Tokens).
# Android-Semantik: der letzte Attributwert gewinnt — daher Zugriff auf alle allowBackup-Zeilen und Entscheidung über die letzte.
mapfile -t ab_lines < <(grep -oE 'android:allowBackup="[^"]+"' "$MANIFEST" || true)
if [[ ${#ab_lines[@]} -eq 0 ]]; then
  fail "C1 verletzt: allowBackup fehlt (Default ist true) — muss explizit \"false\" sein (Finding #471)."
fi
last_ab="${ab_lines[-1]}"
if [[ "$last_ab" != 'android:allowBackup="false"' ]]; then
  fail "C1 verletzt: allowBackup endet nicht mit false (Default ist true) — muss explizit \"false\" sein (Finding #471)."
fi

# C2: jede verdrahtete Backup-Regeldatei muss existieren und real exclusen.
BASE_DIR="$(dirname "$MANIFEST")"
# C2: jede verdrahtete Backup-Regeldatei muss existieren und mindestens eine echte <exclude>-Regel enthalten.
# Android-Semantik: der letzte Attributwert gewinnt — daher Zugriff auf alle Treffer, nicht nur auf den ersten (head -1).
# Extrahiere alle Attributwerte pro Attributname (vollständige Liste), bilde den letzten.
for attr in fullBackupContent dataExtractionRules; do
  # Extrahiere alle Werte dieser Attribut-Art (mehrere sind möglich); tauche zurück zum letzten.
  mapfile -t vals < <(grep -oE "android:${attr}=\"[^\"]+\"" "$MANIFEST" | sed -E 's/.*="([^"]+)"/\1/' || true)
  [[ ${#vals[@]} -gt 0 ]] || continue
  val="${vals[-1]}"   # Android: letzter Attributwert gewinnt
  file="${val#@xml/}"
  rules="$BASE_DIR/res/xml/$file.xml"
  if [[ ! -f "$rules" ]]; then
    fail "C2 verletzt: $attr verweist auf fehlende Regeldatei $rules (Finding #471)."
  fi
  if ! grep -q '<exclude' "$rules"; then
    fail "C2 verletzt: $attr verweist auf $rules ohne echte <exclude>-Regel — leeres Template (Finding #471)."
  fi
done

# C3: MediaProjection-FGS-Typ. Die Screen-Capture-Quelle (S2) streamt über
# MediaProjection.createVirtualDisplay() — Android 14+ verlangt dafür einen FGS
# vom Typ mediaProjection (Permission + foregroundServiceType). Fehlt eines,
# crasht der Go-Live mit SecurityException "Media projections" als
# "Unable to start service" (Sentry VIVID-39, Issue #221).
if ! grep -q 'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION' "$MANIFEST"; then
  fail "C3 verletzt: FOREGROUND_SERVICE_MEDIA_PROJECTION-Permission fehlt (VIVID-39/#221)."
fi
if ! grep -qE 'android:foregroundServiceType="[^"]*mediaProjection' "$MANIFEST"; then
  fail "C3 verletzt: StreamingService.foregroundServiceType ohne mediaProjection (VIVID-39/#221)."
fi

echo "✅ [manifest-security] allowBackup=false, Backup-Verdrahtung schützt real, MediaProjection-FGS-Typ gesetzt (C1/C2/C3 ok)."