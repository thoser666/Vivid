#!/usr/bin/env bash
# Regressionstest: CodeQL-Kotlin-Trace (security-codeql.yml).
#
# Historie (Bundle-Lag, github/codeql#22404, empirisch 10.09.–11.09.2026):
# codeql-action v4.38.0 (Bundle 2.27.0) lehnte Kotlin ≥ 2.4.20 ab („Kotlin
# version 2.4.20 is too recent. CodeQL currently supports versions below
# 2.4.20") — der Extractor-Support (PR github/codeql#20018) war upstream
# gemergt, aber in keinem released Bundle ausgeliefert. Abhilfe 11.09.2026:
# Pin-Step, der den Trace-Build im Runner auf Kotlin 2.4.10 zwang.
# AUFGELÖST 15.10.2026: Bundle 2.27.2 (Default von codeql-action v4.38.3,
# lib/defaults.json) liefert den Extractor — der Pin-Step ist entfernt, der
# Trace baut wieder MIT DER Katalog-Version (2.4.21); empirisch verifiziert
# per workflow_dispatch-Lauf auf dem Verify-Branch (Beleg: RELEASE.md
# „Bundle-Lag-Lehre"). Der Vertrag wird hier zum neuen Stand umgeschrieben:
# KEIN Kotlin-Override im Trace-Pfad mehr.
#
# Vertrag (Läuft im Pre-Push-Gate + CI):
#   K1  init/analyze/upload-sarif überall SHA-gepinnt MIT Versionskommentar
#       und auf DENSELBEN Release-Stand (dynamisch über alle Workflows —
#       gemischte Versionen erzeugen die CodeQL-Mixed-Version-Warnung;
#       familienweite Bumps via Dependabot-PRs #236/#237/#238, 30.09.2026)
#   K2  KEIN Kotlin-Pin-Step im Trace-Pfad (der Workaround „Pin Kotlin for
#       CodeQL extractor" ist entfernt; Wiedereinführung erfordert eine
#       bewusste Vertragsänderung inkl. check_codeql_blockade.sh + Doku)
#   K3  KEIN Versions-Override im Workflow: weder hartkodiertes 2.4.10 noch
#       ein sed auf den Versionskatalog — der Trace baut die Katalog-Version
#   K4  Build-Step bleibt :app:assembleDebug (Trace deckt das App-Modul ab)
#   K5  KEIN anderer Workflow pinnt Kotlin runter (Normal-CI baut dieselbe
#       Katalog-Version wie der Trace)
#   K6  Katalog-Versionen synchron (kotlin == jetbrainsKotlinJvm)
# Exit 0 = grün.
# Offline-Testbarkeit: nur grep/awk auf die committed Workflow-/Katalog-Dateien.
# Test-Root ueber CODEQL_TEST_ROOT ueberschreibbar (Standard: Repo-Root) —
# so kann scripts/test_codeql_family.sh den Vorfallzustand #290/#293 auf eine
# Temp-Kopie der Workflows schreiben und den K1.4-Vertrag dort rot nachweisen,
# ohne das Repo selbst anzufassen.
set -euo pipefail

cd "${CODEQL_TEST_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"

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

# K2: Der Kotlin-Pin-Step ist ENTFERNT. Solange Bundle 2.27.0 blockierte,
# musste der Pin VOR dem Build liegen; heute darf im Trace-Pfad kein
# Kotlin-Override mehr stehen — sonst baut der Trace eine andere Version,
# als die normale CI baut, ohne jeden sichtbaren Fehler.
PIN_LINE=$(awk '/name: Pin Kotlin for CodeQL extractor/{print NR; exit}' "$CODEDQL")
if [ -z "$PIN_LINE" ]; then
  echo "  ✅ K2 Kein Kotlin-Pin-Step im Trace-Pfad (Trace baut Katalog-Version)"
else
  echo "  ❌ K2 Kotlin-Pin-Step wieder vorhanden (Zeile $PIN_LINE) — Wiedereinführung erfordert eine bewusste Vertragsänderung (check_codeql_blockade.sh + RELEASE.md)"
  FAILED=1
fi

# K3: Kein Versions-Override im Workflow — der Trace baut die Katalog-Version.
# Geprüft wird der NICHT-Kommentar-Teil: die historische Kausaldoku im
# Kommentar darf die alte 2.4.10 durchaus nennen (#22404), ausführbar darf
# sie nicht sein (dieselbe Kommentar-Falle wie T15.15, nur umgekehrt).
code_active="$(grep -vE '^[[:space:]]*#' "$CODEDQL" || true)"
if printf '%s' "$code_active" | grep -qE '2\.4\.10'; then
  echo "  ❌ K3.1 hartkodiertes Downgrade-Rest (2.4.10) im ausführbaren Teil"
  FAILED=1
else
  echo "  ✅ K3.1 kein hartkodiertes Downgrade-Rest (2.4.10) im ausführbaren Teil"
fi
if printf '%s' "$code_active" | grep -qE 'sed -i'; then
  echo "  ❌ K3.2 sed-Override auf den Versionskatalog im ausführbaren Teil"
  FAILED=1
else
  echo "  ✅ K3.2 kein sed-Override auf den Versionskatalog im ausführbaren Teil"
fi

# K4: Trace-Build bleibt das App-Modul (deterministischer Extractor-Umfang).
check "K4.1 :app:assembleDebug im Trace-Build" "$CODEDQL" ':app:assembleDebug --console=plain'

# K5: Kein anderer Workflow darf Kotlin runterpinnt — Normale CI
# (android-ci, release-pipeline, distribution-stable) baut dieselbe
# Katalog-Version wie der Trace-Pfad.
for f in .github/workflows/android-ci.yml .github/workflows/release-pipeline.yml .github/workflows/distribution-stable.yml; do
  notcheck "K5.$(basename "$f") kein Kotlin-Downgrade (2.4.10)" "$f" 'kotlin = "2\.4\.10"'
done

# K6: Katalog-Konsistenz — kotlin == jetbrainsKotlinJvm (Sync-Guard K1–K5).
# Die frühere Pair-Bedingung (KSP < 2.4.20) entfällt mit dem Pin-Ziel 2.4.10:
# Sie war ausschließlich für das Pin-Paar 2.4.10 + KSP 2.3.11 gesetzt; KSP
# wird wie jede andere Abhängigkeit von Dependabot geführt (#286: 2.3.12).
KOTLIN=$(grep -m1 '^kotlin = ' "$TOML" | sed 's/.*"\([^"]*\)".*/\1/')
KOTLIN_JVM=$(grep -m1 '^jetbrainsKotlinJvm = ' "$TOML" | sed 's/.*"\([^"]*\)".*/\1/')
if [ -n "$KOTLIN" ] && [ "$KOTLIN" = "$KOTLIN_JVM" ]; then
  echo "  ✅ K6.1 kotlin == jetbrainsKotlinJvm ($KOTLIN)"
else
  echo "  ❌ K6.1 kotlin ($KOTLIN) != jetbrainsKotlinJvm ($KOTLIN_JVM)"
  FAILED=1
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ CodeQL-Kotlin-Trace intakt — Trace-Build kompiliert mit der Katalog-Version (kein Override)."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1