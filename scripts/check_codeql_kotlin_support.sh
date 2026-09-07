#!/usr/bin/env bash
# Wächter: Ist das Kotlin-2.4.20-Re-Upgrade (Issue #110) wieder möglich?
#
# Hintergrund: Der Kotlin-Bump auf 2.4.20 wurde in a8766e5 revertiert, weil
# CodeQL 2.26.4 Kotlin 2.4.20 GA noch nicht extrahieren kann ("Kotlin version
# 2.4.20 is too recent" — Tracking: github/codeql#22404). Sobald CodeQL
# GA-Support liefert, muss das Re-Upgrade erfolgen — dieses Skript erkennt
# den Punkt automatisch, der Workflow automation-codeql-kotlin.yml kommentiert
# dann wöchentlich geprüft auf Issue #110.
#
# Logik:
#   1. Kotlin >= 2.4.20 in libs.versions.toml?  → Guard ist überflüssig (exit 0).
#   2. codeql#22404 closed + completed?         → RE-UPGRADE FÄLLIG (exit 10),
#                                                 ::warning:: + Step-Summary.
#   3. codeql#22404 offen / nicht abgeschlossen → weiterhin blockiert (exit 0).
#   4. API nicht erreichbar / Antwort kaputt    → Warnung, neutral (exit 0) —
#                                                 geplante Läufe sollen nicht
#                                                 an Netzwerk-Flakiness scheitern.
#
# Exit-Codes: 0 = keine Aktion, 10 = Re-Upgrade fällig (Aktion!), sonst Fehler.
#
# Testbarkeit (Offline-Selbsttest: scripts/test_codeql_guard.sh):
#   CODEQL_ISSUE_JSON      — vorgerenderte Issue-API-Antwort statt curl
#   CODEQL_ISSUE_URL       — alternative API-URL (Default: github/codeql#22404)
#   KOTLIN_VERSION_FILE    — alternative libs.versions.toml (Fixture)
set -euo pipefail

KOTLIN_FILE="${KOTLIN_VERSION_FILE:-gradle/libs.versions.toml}"
ISSUE_URL="${CODEQL_ISSUE_URL:-https://api.github.com/repos/github/codeql/issues/22404}"
TARGET_MAJOR=2 TARGET_MINOR=4 TARGET_PATCH=20

fail() { echo "❌ [codeql-kotlin-guard] $1" >&2; exit 1; }
info() { echo "ℹ️  [codeql-kotlin-guard] $1"; }
warn() { echo "::warning::$1"; }

# Aktuelle Kotlin-Version aus dem Versions-Katalog lesen.
current_kotlin() {
  grep -m1 '^kotlin = ' "$KOTLIN_FILE" | sed 's/.*"\([^"]*\)".*/\1/'
}

# Versions-Tripel → Zahl (Vergleich ohne bc).
ver_to_num() {
  local major minor patch
  IFS='.' read -r major minor patch <<<"$1"
  echo "$((10#${major:-0} * 1000000 + 10#${minor:-0} * 1000 + 10#${patch:-0}))"
}

# Issue-JSON beschaffen (Override für Tests; sonst curl, Fehler = leer).
fetch_issue_json() {
  if [[ -n "${CODEQL_ISSUE_JSON:-}" ]]; then
    printf '%s' "$CODEQL_ISSUE_JSON"
    return 0
  fi
  curl -sfL --max-time 30 "$ISSUE_URL" 2>/dev/null || printf ''
}

# Step-Summary schreiben, wenn in der CI vorhanden.
append_summary() {
  [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] || return 0
  cat >>"$GITHUB_STEP_SUMMARY" <<EOF
## CodeQL Kotlin Guard

$1
EOF
}

# ── 1) Bereits aktualisiert? ────────────────────────────────────────────────
[[ -f "$KOTLIN_FILE" ]] || fail "Versions-Katalog fehlt: $KOTLIN_FILE"
current="$(current_kotlin)"
[[ -n "$current" ]] || fail "Kotlin-Version in $KOTLIN_FILE nicht gefunden (Zeile 'kotlin = \"...\"')."
current_num="$(ver_to_num "$current")"
target_num="$(ver_to_num "${TARGET_MAJOR}.${TARGET_MINOR}.${TARGET_PATCH}")"
if (( current_num >= target_num )); then
  info "Kotlin $current ist bereits >= 2.4.20 — der Wächter ist überflüssig \
und kann zusammen mit automation-codeql-kotlin.yml entfernt werden."
  exit 0
fi

# ── 2) CodeQL-Support prüfen ────────────────────────────────────────────────
json="$(fetch_issue_json)"
if [[ -z "$json" ]]; then
  warn "CodeQL-Kotlin-Wächter konnte github/codeql#22404 nicht abfragen \
(Netzwerk/API). Nächster geplanter Lauf prüft erneut; Issue #110 bleibt \
vorläufig blockiert."
  append_summary "⚠️ **CodeQL-Kotlin-Wächter:** github/codeql#22404 nicht abfragbar — nächster Lauf prüft erneut. Kotlin-2.4.20-Re-Upgrade (Issue #110) bleibt vorläufig blockiert."
  exit 0
fi

# Kein Treffer (z. B. kaputtes/partialles JSON) = leerer String → neutrale
# Behandlung. '|| true' neutralisiert pipefail für fehlende Matches.
state="$(grep -o '"state" *: *"[a-z]*"' <<<"$json" | head -1 | sed 's/.*"\([a-z]*\)"$/\1/' || true)"
reason="$(grep -o '"state_reason" *: *"[a-z]*"' <<<"$json" | head -1 | sed 's/.*"\([a-z]*\)"$/\1/' || true)"

if [[ "$state" == "closed" && "$reason" == "completed" ]]; then
  warn "CodeQL unterstützt Kotlin 2.4.20 jetzt (github/codeql#22404 geschlossen). \
RE-UPGRADE FÄLLIG: Kotlin in gradle/libs.versions.toml auf 2.4.20 anheben \
(Referenz: revert a8766e5), CI inkl. CodeQL grün laufen lassen, dann Issue #110 schließen."
  append_summary "🚨 **Kotlin-2.4.20-Re-Upgrade ist fällig** (Issue #110, einst revertiert in \`a8766e5\`): github/codeql#22404 wurde geschlossen — CodeQL unterstützt Kotlin 2.4.20 GA. Nächste Schritte: \`kotlin\` in \`gradle/libs.versions.toml\` auf 2.4.20 anheben, CI inkl. CodeQL grün verifizieren, Issue #110 abschließen."
  info "Exit 10 — der Workflow automation-codeql-kotlin.yml kommentiert Issue #110."
  exit 10
fi

if [[ "$state" == "closed" ]]; then
  info "github/codeql#22404 ist geschlossen (state_reason: ${reason:-unbekannt}), \
aber nicht als 'completed' markiert — kein belastbares Support-Signal. \
Kotlin $current bleibt bei 2.4.10; Issue #110 bleibt blockiert."
  exit 0
fi

info "CodeQL unterstützt Kotlin 2.4.20 GA noch nicht (github/codeql#22404 offen). \
Aktuelle Version: Kotlin $current. Issue #110 bleibt blockiert — nächster geplanter \
Lauf prüft erneut."
append_summary "✅ **Kotlin-2.4.20-Re-Upgrade weiterhin blockiert** (github/codeql#22404 offen). Aktuell: Kotlin $current."
exit 0
