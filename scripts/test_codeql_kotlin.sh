#!/usr/bin/env bash
# Regressionstest: CodeQL-Kotlin-Extractor-Pin (security-codeql.yml).
#
# Problem (Bundle-Lag, github/codeql#22404, empirisch 10.09.–11.09.2026):
# codeql-action v4.38.0 (aktuellster Stand) liefert Bundle 2.27.0 aus, das
# Kotlin ≥ 2.4.20 ablehnt („Kotlin version 2.4.20 is too recent. CodeQL
# currently supports versions below 2.4.20") — ein released Bundle mit dem
# 2.4.20-Extractor existiert bislang nicht. Der Workflow pinnt deshalb den
# CodeQL-Trace-Build im Runner auf Kotlin 2.4.10 (bewährtes Pair mit KSP
# 2.3.11, < 2.4.20); die normale CI baut unverändert mit 2.4.20.
#
# Vertrag (Läuft im Pre-Push-Gate + CI):
#   K1  init/analyze/upload-sarif überall SHA-gepinnt MIT Versionskommentar
#       und auf DENSELBEN Release-Stand (dynamisch über alle Workflows —
#       gemischte Versionen erzeugen die CodeQL-Mixed-Version-Warnung;
#       familienweite Bumps via Dependabot-PRs #236/#237/#238, 30.09.2026)
#   K2  Pin-Step (Kotlin → 2.4.10) liegt VOR dem Build-Step (Reihenfolge!)
#   K3  Pin zielt auf 2.4.10 und liest die Quelle dynamisch aus dem Katalog
#       (driftet der Katalog auf 2.4.20+ weiter FRÜHER, greift der Pin weiter)
#   K4  Build-Step bleibt :app:assembleDebug (Trace deckt das App-Modul ab)
#   K5  KEIN anderer Workflow pinnt heimlich auf 2.4.10 (Normal-CI bleibt 2.4.20)
#   K6  Katalog-Versionen synchron (kotlin == jetbrainsKotlinJvm), KSP < 2.4.20
# Exit 0 = grün.
# Offline-Testbarkeit: nur grep/awk auf die committed Workflow-/Katalog-Dateien.
set -euo pipefail

cd "$(dirname "$0")/.."

CODEDQL=.github/workflows/security-codeql.yml
TOML=gradle/libs.versions.toml

echo "▶ [test_codeql_kotlin] Szenarien K1–K6"

FAILED=0
check() {
  local name="$1" file="$2" pattern="$3"
  if grep -qE -- "$pattern" "$file" 2>/dev/null; then
    echo "  ✅ $name"
  else
    echo "  ❌ $name — Muster nicht gefunden: $pattern (in $file)"
    FAILED=1
  fi
}
notcheck() {
  local name="$1" file="$2" pattern="$3"
  if grep -qE -- "$pattern" "$file" 2>/dev/null; then
    echo "  ❌ $name — Muster DARF nicht vorkommen: $pattern (in $file)"
    FAILED=1
  else
    echo "  ✅ $name"
  fi
}

# K1: Alle codeql-action-Steps SHA-gepinnt (keine beweglichen Tags), mit
# Versionskommentar, und auf DENSELBEN Release-Stand — dynamisch über alle
# Workflows geprüft, damit familienweite Bumps (Dependabot-PRs #236/#237/#238)
# den Vertrag nicht mehr brechen wie die wörtliche v4.38.1-SHA davor.
PINS=$(grep -rhoE 'uses:[[:space:]]*github/codeql-action/(init|analyze|upload-sarif)@[0-9a-f]{40}[[:space:]]*#[[:space:]]*v[0-9.]+' .github/workflows/*.yml 2>/dev/null || true)
PIN_COUNT=$(printf '%s\n' "$PINS" | grep -c . || true)
if [ "$PIN_COUNT" -ge 4 ]; then
  echo "  ✅ K1.0 Alle codeql-action-Pins SHA+Versionskommentar ($PIN_COUNT Stellen)"
else
  echo "  ❌ K1.0 Zu wenige voll gepinnte codeql-action-Stellen ($PIN_COUNT, erwartet >= 4)"
  FAILED=1
fi
for a in init analyze upload-sarif; do
  if printf '%s\n' "$PINS" | grep -q "codeql-action/$a@"; then
    echo "  ✅ K1.$a SHA-gepinnt mit Versionskommentar"
  else
    echo "  ❌ K1.$a fehlt oder ist nicht SHA+Versionskommentar-gepinnt"
    FAILED=1
  fi
done
UNIQUE_SHAS=$(printf '%s\n' "$PINS" | grep -oE '@[0-9a-f]{40}' | sort -u | wc -l)
UNIQUE_VERS=$(printf '%s\n' "$PINS" | grep -oE 'v[0-9][0-9.]*' | sort -u | wc -l)
if [ "$UNIQUE_SHAS" -eq 1 ] && [ "$UNIQUE_VERS" -eq 1 ]; then
  echo "  ✅ K1.4 Same-Release über alle Pin-Stellen ($(printf '%s\n' "$PINS" | grep -oE 'v[0-9][0-9.]*' | sort -u))"
else
  echo "  ❌ K1.4 Gemischte codeql-action-Versionen (SHAs=$UNIQUE_SHAS, Versionen=$UNIQUE_VERS) — Mixed-Version-Warnung droht"
  FAILED=1
fi

# K2: Der Pin-Step muss VOR dem Build-Step liegen, sonst kompiliert der Build
# den Trace mit 2.4.20 und scheitert weiterhin (Reihenfolge ist entscheidend).
PIN_LINE=$(awk '/name: Pin Kotlin for CodeQL extractor/{print NR; exit}' "$CODEDQL")
BUILD_LINE=$(awk '/name: Build Debug APK \(CodeQL trace\)/{print NR; exit}' "$CODEDQL")
if [ -n "$PIN_LINE" ] && [ -n "$BUILD_LINE" ] && [ "$PIN_LINE" -lt "$BUILD_LINE" ]; then
  echo "  ✅ K2 Pin-Step (Zeile $PIN_LINE) liegt vor Build-Step (Zeile $BUILD_LINE)"
else
  echo "  ❌ K2 Pin-Step liegt NICHT vor Build-Step (Pin=$PIN_LINE, Build=$BUILD_LINE)"
  FAILED=1
fi

# K3: Pin-Mechanismus — hartes Ziel 2.4.10, Quelle dynamisch aus dem Katalog
# (grep auf `^kotlin = `), beide Versions-Keys (kotlin + jetbrainsKotlinJvm)
# werden ersetzt. Driftet der Katalog später, muss hier nichts angepasst werden.
check "K3.1 Ziel 2.4.10" "$CODEDQL" 'kotlin = \\"2\.4\.10\\"'
check "K3.2 dynamische Quelle aus dem Katalog" "$CODEDQL" 'grep -m1 .\^kotlin = . gradle/libs\.versions\.toml'
notcheck "K3.3 kein HARDCODED-Quell-2.4.20-Sed (dynamisch)" "$CODEDQL" 's/\^kotlin = "2\.4\.20"/'

# K4: Trace-Build bleibt das App-Modul (deterministischer Extractor-Umfang).
check "K4.1 :app:assembleDebug im Trace-Build" "$CODEDQL" ':app:assembleDebug --console=plain'

# K5: Kein anderer Workflow darf den Kotlin-Pin einschleusen — die normale CI
# (android-ci, release-pipeline, distribution-stable) baut unverändert 2.4.20.
for f in .github/workflows/android-ci.yml .github/workflows/release-pipeline.yml .github/workflows/distribution-stable.yml; do
  notcheck "K5.$(basename "$f") kein Kotlin-Downgrade (2.4.10)" "$f" 'kotlin = "2\.4\.10"'
done

# K6: Katalog-Konsistenz — kotlin == jetbrainsKotlinJvm (Sync-Guard K1–K5) und
# das Pin-Ziel 2.4.10 liegt unter der CodeQL-Grenze 2.4.20 (github/codeql#22404).
KOTLIN=$(grep -m1 '^kotlin = ' "$TOML" | sed 's/.*"\([^"]*\)".*/\1/')
KOTLIN_JVM=$(grep -m1 '^jetbrainsKotlinJvm = ' "$TOML" | sed 's/.*"\([^"]*\)".*/\1/')
KSP=$(grep -m1 '^ksp-version = ' "$TOML" | sed 's/.*"\([^"]*\)".*/\1/')
ver_to_num() {
  local major minor patch
  IFS='.' read -r major minor patch <<<"$1"
  echo "$((10#${major:-0} * 1000000 + 10#${minor:-0} * 1000 + 10#${patch:-0}))"
}
if [ -n "$KOTLIN" ] && [ "$KOTLIN" = "$KOTLIN_JVM" ]; then
  echo "  ✅ K6.1 kotlin == jetbrainsKotlinJvm ($KOTLIN)"
else
  echo "  ❌ K6.1 kotlin ($KOTLIN) != jetbrainsKotlinJvm ($KOTLIN_JVM)"
  FAILED=1
fi
if [ -n "$KSP" ] && [ "$(ver_to_num "$KSP")" -lt "$(ver_to_num 2.4.20)" ]; then
  echo "  ✅ K6.2 KSP $KSP < CodeQL-Grenze 2.4.20 (Extractor-Pair)"
else
  echo "  ❌ K6.2 KSP $KSP >= 2.4.20 — Pin-Ziel 2.4.10 ist kein gültiges Pair mehr"
  FAILED=1
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ CodeQL-Extractor-Pin intakt — Trace-Build kompiliert mit Kotlin < 2.4.20."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1