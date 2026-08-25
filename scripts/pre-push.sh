#!/usr/bin/env bash
# Pre-Push-Gate (Vivid): führt die gleichen Checks wie die CI lokal aus, bevor
# ein Push losgeschickt wird. Verhindert rote CI-Läufe — z. B. der Fall, dass
# ein Modul nur compiliert, aber nie getestet wurde (CI fand den verpassten
# :feature-settings-Test).
#
# Aufruf:
#   bash scripts/pre-push.sh                    # manuell (Tests + Lint + Mapping + Guard)
#   bash scripts/pre-push.sh --dry-run          # zeigt nur die Checks an (Tests/Übersicht)
#   PRE_PUSH_SKIP_LINT=1 bash scripts/pre-push.sh  # Lint überspringen (nur Tests + Guard)
#   PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh    # zusätzlich Release-Build (R8/ProGuard)
#
# Der Mapping-Check (check_sentry_optout_mapping.sh) läuft automatisch, wenn ein
# frisches Release-Mapping vorliegt (nach PRE_PUSH_RELEASE=1 garantiert) und
# weist nach, dass die Sentry-Opt-out-Logik im R8-Release-Build enthalten ist.
# Semantik: Ohne Release-Build weich — fehlende Kanäle meldet der Check selbst
# als „nicht gebaut“ (übersprungen), nur echte Nachweis-Fehlschläge sind rot.
# Nach PRE_PUSH_RELEASE=1 streng (--strict): beide Mappings müssen existieren.
#
# Die Release-Builds (assembleRelease + bundlePlayRelease) sind OPTIONAL: sie
# laufen R8/ProGuard + Resource-Shrinking und fangen so Signatur-/ProGuard-
# Probleme lokal, bevor sie die CI erreichen. Signierung fällt ohne die
# KEYSTORE_*/UPLOAD_*-Secrets auf den Debug-Keystore zurück (gleiches Verhalten
# wie in der CI ohne Secrets).
#
# Als Git-Hook installieren:  bash scripts/install-git-hooks.sh
# Einen einzelnen Push umgehen: git push --no-verify
set -euo pipefail

cd "$(dirname "$0")/.."

DRY_RUN=0
for arg in "$@"; do
  if [[ "$arg" == "--dry-run" ]]; then
    DRY_RUN=1
  fi
done

run() {
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "   [dry-run] $*"
  else
    "$@"
  fi
}

echo "▶ [pre-push] Unit-Tests aller Module (wie CI: ./gradlew testDebugUnitTest)"
run ./gradlew testDebugUnitTest --console=plain

if [[ "${PRE_PUSH_SKIP_LINT:-0}" != "1" ]]; then
  echo "▶ [pre-push] Lint (warningsAsErrors, wie CI: ./gradlew lintDebug)"
  run ./gradlew lintDebug --console=plain
fi

# Optional: Release-Builds mit R8/ProGuard + Resource-Shrinking — BEIDE Kanäle,
# damit der anschließende Mapping-Check (release + playRelease) frische Mappings
# vorfindet: assembleRelease (APK) + bundlePlayRelease (AAB, Upload-Key-Kanal).
if [[ "${PRE_PUSH_RELEASE:-0}" == "1" ]]; then
  echo "▶ [pre-push] Release-Builds (R8/ProGuard, PRE_PUSH_RELEASE=1: ./gradlew assembleRelease bundlePlayRelease)"
  run ./gradlew assembleRelease bundlePlayRelease --console=plain
fi

# Automatischer Mapping-Check: weist nach, dass die Sentry-Opt-out-Logik in
# BEIDEN Release-Kanälen (release=APK + playRelease=AAB, R8) überlebt — Lambda
# muss in io.sentry.SentryClient inlined sein. Nach PRE_PUSH_RELEASE=1 sind die
# Mappings garantiert frisch (Pflicht). Ohne Release-Build läuft der Check
# automatisch, wenn frische Mappings vorliegen (z. B. vom letzten Release-Build);
# sonst Hinweis statt Fehlschlag, da der Release-Build bewusst optional ist.
# Fastfile-Suffix-Guard (Ruby, offline): verhindert, dass release_alpha/
# release_beta mit explizitem version: ohne Stufen-Suffix einen stabilen Tag
# erzeugen (Vorfall v0.5.1 vom 18.08.2026). Reine Funktions-/Strukturprüfung,
# kein fastlane-Lauf nötig.
echo "▶ [pre-push] Fastfile-Suffix-Guard (scripts/test_stage_suffix_guard.sh)"
run bash scripts/test_stage_suffix_guard.sh

# Geteilte Release-Safety (Ruby, offline): versionCode-Ableitung, Track-Monotonie
# (kein Downgrade) und Quer-Track-Vergleich — beide Lanes müssen dieselben
# Funktionen nutzen, damit sie nicht wieder auseinanderlaufen.
echo "▶ [pre-push] Release-Safety (scripts/test_release_safety.sh)"
run bash scripts/test_release_safety.sh

# Roadmap-Reservierung (Ruby, offline): v0.6.0-beta wird abgelehnt, solange das
# Streaming-Erweiterungs-Bucket (RIST/WHIP/RTMP-Pull/4K-HEVC/SRTLA) in PARITY.md
# nicht vollständig ✅ ist — die Bucket-Nummerierung darf nicht vorzeitig belegt
# werden (RELEASE.md → Roadmap → Nummerierung).
echo "▶ [pre-push] Roadmap-Reservierung (scripts/test_roadmap_reservation.sh)"
run bash scripts/test_roadmap_reservation.sh

echo "▶ [pre-push] Sentry-Opt-out-Mapping-Check (scripts/check_sentry_optout_mapping.sh)"
if [[ "${PRE_PUSH_RELEASE:-0}" == "1" ]]; then
  # Nach PRE_PUSH_RELEASE=1 sind BEIDE Mappings garantiert gebaut → streng:
  # fehlendes Mapping = harter Fehler (kein stilles Überspringen).
  run bash scripts/check_sentry_optout_mapping.sh --strict
else
  # Ohne Release-Build weich: fehlende Kanäle meldet der Check als „nicht gebaut“
  # (übersprungen); Exit 1 hier = echter Nachweis-Fehlschlag auf vorhandenem Mapping.
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "   [dry-run] bash scripts/check_sentry_optout_mapping.sh"
  elif bash scripts/check_sentry_optout_mapping.sh; then
    :
  else
    echo "   ⚠️  Mapping-Nachweis fehlgeschlagen — Release-Build erneut ausführen (PRE_PUSH_RELEASE=1 bash scripts/pre-push.sh)."
  fi
fi

echo "▶ [pre-push] Secret-Guard (scripts/guard_secrets.sh)"
run bash scripts/guard_secrets.sh

# I18n-Guard: Externalisierungs-Gate (keine hartkodierten UI-Strings),
# values/ ↔ values-en/ Vollständigkeit und stream_url_hint-Inhalts-Guard.
echo "▶ [pre-push] I18n-Guard (scripts/check_i18n.sh)"
run bash scripts/check_i18n.sh

# I18n-Guard-Selbsttest (Fixtures): beweist, dass der Guard hartkodierte
# Strings, fehlende Übersetzungen und Hint-Rückfälle rot meldet.
echo "▶ [pre-push] I18n-Guard-Selbsttest (scripts/test_check_i18n.sh)"
run bash scripts/test_check_i18n.sh

# Markdown-Anker-Check: validiert alle internen [text](datei.md#anker)-Links
# deterministisch gegen die GitHub-Anker der Ziel-Dateien (tote Anker nach
# Abschnitts-Umbenennungen brechen so lokal, nicht erst in der CI).
echo "▶ [pre-push] Markdown-Anker-Check (scripts/check_markdown_anchors.sh)"
run bash scripts/check_markdown_anchors.sh

# GitHub-Golden-Test: beweist offline, dass die Anker-Berechnung exakt dem
# GitHub-Rendering entspricht (14 empirisch verifizierte IDs, inkl. VS16).
echo "▶ [pre-push] GitHub-Golden-Anker-Test (scripts/test_github_anchors.sh)"
run bash scripts/test_github_anchors.sh

# PARITY-Log-Guard: jeder Aktualisierungslog-Eintrag in PARITY.md muss einen
# gültigen 7-Zeichen-Commit-Hash tragen statt „—" (nachvollziehbarer Log).
# Mit --check-exists wird zusätzlich geprüft, dass der Hash existiert und ein
# Vorfahre von HEAD ist — fängt Rebase-Orphans ab (Vorfall c0fb445→7ae806a).
echo "▶ [pre-push] PARITY-Log-Guard (scripts/check_parity_log.sh)"
run bash scripts/check_parity_log.sh --check-exists

# PARITY-Log-Guard-Selbsttest (Fixtures): beweist, dass Platzhalter „—" und
# ungültige Hashes rot gemeldet werden und saubere Logs grün bleiben.
echo "▶ [pre-push] PARITY-Log-Guard-Selbsttest (scripts/test_parity_log.sh)"
run bash scripts/test_parity_log.sh

echo "✅ [pre-push] Alle Checks grün — Push freigegeben."
