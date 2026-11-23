#!/usr/bin/env bash
# Prüft, ob eine gebaute APK unter dem Distributionslimit liegt.
#
# Hintergrund: IzzyOnDroid nimmt laut App Inclusion Policy maximal 30 MB pro
# APK an ("we cannot currently accept APK files over 30 MB") und hält pro App
# höchstens so viel vor. F-Droid baut dieselbe universelle APK. Der Wert ist
# deshalb ein hartes Gate für jede Einreichung, nicht nur eine Warnung.
#
# Aufruf:
#   bash scripts/check_apk_size.sh <pfad/zur/app.apk>
#   APK_LIMIT_MB=25 bash scripts/check_apk_size.sh <apk>   # strenger prüfen
#
# Exit-Codes:
#   0 = APK vorhanden und <= Limit
#   1 = APK zu groß
#   2 = Aufruffehler (kein Argument / Datei fehlt) — bewusst KEIN stiller Pass:
#       eine nicht prüfbare APK darf nie wie ein bestandener Check aussehen.
set -euo pipefail

LIMIT_MB="${APK_LIMIT_MB:-30}"
APK="${1:-}"

if [ -z "$APK" ]; then
  echo "❌ [apk-size] Aufruf: bash scripts/check_apk_size.sh <pfad/zur/app.apk>" >&2
  exit 2
fi
if [ ! -f "$APK" ]; then
  echo "❌ [apk-size] APK nicht gefunden: $APK (erst bauen, z. B. ./gradlew assembleFossRelease)" >&2
  exit 2
fi
if ! printf '%s' "$LIMIT_MB" | grep -qE '^[0-9]+$'; then
  echo "❌ [apk-size] APK_LIMIT_MB ist keine Zahl: '$LIMIT_MB'" >&2
  exit 2
fi

size_bytes="$(wc -c < "$APK" | tr -d ' ')"
limit_bytes=$(( LIMIT_MB * 1024 * 1024 ))
# MiB mit einer Nachkommastelle, ohne bc: ganzzahlig runden.
size_mib="$(awk -v b="$size_bytes" 'BEGIN { printf "%.1f", b / 1048576 }')"
limit_mib="$(awk -v b="$limit_bytes" 'BEGIN { printf "%.1f", b / 1048576 }')"

if [ "$size_bytes" -gt "$limit_bytes" ]; then
  echo "❌ [apk-size] $(basename "$APK") ist ${size_mib} MiB — Limit ${limit_mib} MiB (${LIMIT_MB} MB)."
  echo "   IzzyOnDroid: max. 30 MB pro APK. Große Brocken zuerst prüfen:"
  echo "   unzip -l \"$APK\" | awk '/lib\\// {split(\$4,a,\"/\"); s[a[2]]+=\$1} END {for (k in s) printf \"%-14s %6.1f MB\\n\", k, s[k]/1048576}'"
  exit 1
fi

echo "✅ [apk-size] $(basename "$APK"): ${size_mib} MiB / Limit ${limit_mib} MiB (${LIMIT_MB} MB)."
