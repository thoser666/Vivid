#!/usr/bin/env bash
# Wächter: Frühwarnung beim nächsten Kotlin-Bump über dem empirisch
# verifizierten CodeQL-Extractor-Stand (advisory, exit immer 0).
#
# Historie (Bundle-Lag, github/codeql#22404): Bundle 2.27.0 lehnte
# Kotlin 2.4.20 ab, obwohl der Extractor-Support upstream gemerged war
# (PR github/codeql#20018) — „Issue geschlossen ≠ im Bundle ausgeliefert"
# wurde im September zweimal teuer (Wächter zu früh entfernt b2d92ed,
# dann Pin-Step auf 2.4.10 als Workaround ab 11.09.2026).
#
# AUFGELÖST 15.10.2026: Bundle 2.27.2 (Default von codeql-action v4.38.3,
# lib/defaults.json) liefert den Extractor — der Pin-Step in
# security-codeql.yml ist entfernt, der Trace baut die Katalog-Version
# (2.4.21). Verifiziert per workflow_dispatch-Lauf auf dem Verify-Branch
# (Beleg: RELEASE.md „Bundle-Lag-Lehre").
#
# NEUER Zweck dieses Wächters: Sobald der Versionskatalog eine Kotlin-Version
# ÜBER dem verifizierten Stand trägt, warnt er — der nächste Kotlin-Bump
# darf erst gemergt werden, nachdem ein CodeQL-Lauf mit dieser Version grün
# war (workflow_dispatch auf dem Branch); sonst fällt der Trace-Build wieder
# blockiert aus wie im September.
#
# Verhalten (schlank, offline, exit immer 0 außer bei echten Skript-Fehlern):
#   1. Keine Kotlin-Version im Katalog           → still (exit 0).
#   2. Katalog-Kotlin <= KNOWN_EXTRACTOR_KOTLIN  → still (verifizierter Stand).
#   3. Katalog-Kotlin darüber                     → ::warning:: mit
#      Verifikationsanweisung (workflow_dispatch, #22404, ggf. Pin-Wieder-
#      einführung inkl. Anpassungspflichten).
# Offline-Testbarkeit (Selbsttest: scripts/test_codeql_blockade.sh):
#   KOTLIN_VERSION_FILE — alternative libs.versions.toml (Fixture)
set -euo pipefail

KOTLIN_FILE="${KOTLIN_VERSION_FILE:-gradle/libs.versions.toml}"

# Empirisch verifizierter Extractor-Stand: Kotlin 2.4.21 extrahiert grün mit
# Bundle 2.27.2 (codeql-action v4.38.3) — workflow_dispatch-Verifikation am
# 15.10.2026. Hebt der Katalog darüber hinaus an, ist die Verifikation fällig.
KNOWN_EXTRACTOR_KOTLIN="2.4.21"
KNOWN_EXTRACTOR_BUNDLE="2.27.2"

warn() { echo "::warning::[codeql-blockade] $1"; }

# Kotlin-Version aus dem Versions-Katalog lesen (leer, falls Zeile fehlt).
current_kotlin() {
  grep -m1 '^kotlin = ' "$KOTLIN_FILE" 2>/dev/null | sed 's/.*"\([^"]*\)".*/\1/' || true
}

# Versions-Tripel → Zahl (Vergleich ohne bc).
ver_to_num() {
  local major minor patch
  IFS='.' read -r major minor patch <<<"$1"
  echo "$((10#${major:-0} * 1000000 + 10#${minor:-0} * 1000 + 10#${patch:-0}))"
}

kotlin="$(current_kotlin)"
[ -n "$kotlin" ] || exit 0
# Kaputter Katalogwert (nicht numerisches X.Y.Z) gilt als neutral — derselbe
# fail-soft-Fall wie die alte „API-Antwort kaputt"-Semantik; sonst würde
# ver_to_num in Bash-Arithmetik abstürzen (Selbsttest T5b deckt das ab).
[[ "$kotlin" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || exit 0
# Guard nur relevant, sobald der Katalog über dem verifizierten Stand driftet.
[ "$(ver_to_num "$kotlin")" -gt "$(ver_to_num "$KNOWN_EXTRACTOR_KOTLIN")" ] || exit 0

warn "Kotlin $kotlin im Katalog liegt ueber dem empirisch verifizierten Extractor-Stand $KNOWN_EXTRACTOR_KOTLIN (Bundle $KNOWN_EXTRACTOR_BUNDLE, github/codeql#22404): VOR dem Merge des Kotlin-Bumps einen CodeQL-Lauf per workflow_dispatch auf dem Branch verifizieren — sonst faellt der Trace-Build wie im September blockiert aus. Bei Bedarf den Kotlin-Pin analog wieder einfuehren UND scripts/test_codeql_kotlin.sh (K2/K3) sowie RELEASE.md anpassen."
exit 0
