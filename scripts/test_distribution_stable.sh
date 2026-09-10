#!/usr/bin/env bash
# Regressionstest: wöchentliche Stable-Distribution (distribution-stable.yml).
#
# Vertrag (docs/distribution.md, seit 10.09.2026):
#   - Stable-GitHub-Releases erscheinen NUR wöchentlich (Mo 03:00 UTC) oder
#     manuell per workflow_dispatch — NIEMALS bei push/tag/release-Ereignissen.
#   - Nightly (täglich) läuft UNVERÄNDERT in release-pipeline.yml.
#   - Der Job wählt die neueste NICHT vollständig verteilte v*-Version, baut
#     sie mit `release_github tag:"$TAG"` (beide Flavor-APKs + SHA256SUMS) und
#     spiegelt danach CHANGELOG.md (GH_TOKEN-Ereignisse feuern kein
#     release:published — Update läuft inline, wie in release-pipeline.yml).
#   - Sind alle Versionen vollständig verteilt, endet der Lauf sauber (exit 0).
#   - deploy-fdroid.yml feuert seitdem auf wöchentlichem Schedule + Manuell,
#     KEIN release:published mehr; das Repo lädt nur den Standard-Flavor
#     (app-standard-release.apk), sonst doppelte versionCodes im Repo.
#
# Läuft im CI (release-pipeline.yml, Job "Self-Test Stable-Distribution")
# und lokal: bash scripts/test_distribution_stable.sh  (Exit 0 = grün)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

DIST=.github/workflows/distribution-stable.yml
FDROID=.github/workflows/deploy-fdroid.yml
RELEASE=.github/workflows/release-pipeline.yml

echo "▶ [test_distribution_stable] Szenarien D1–D12"

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

# D1: Wöchentlicher Trigger (Mo 03:00 UTC) — die Stable-Publikation darf nie
# bei einem Event< alle 7 Tage dauern, niemals häufiger.
check "D1.1 weekly schedule Mo 03:00 UTC" "$DIST" "cron: '0 3 \* \* 1'"
check "D1.2 workflow_dispatch (Nachholen)" "$DIST" "workflow_dispatch:"

# D2: Keine Event-Trigger — push/tags/release dürfen NICHT stabil publizieren
# (GitHub unterscheidet nicht, welcher Cron einen Schedule-Run ausgelöst hat →
# ein zweiter Cron in release-pipeline.yml hätte Stable fälschlich täglich
# publiziert; das ist der Grund für den separaten Workflow).
notcheck "D2.1 kein push-Trigger" "$DIST" '^[[:space:]]{2}push:'
notcheck "D2.2 kein pull_request-Trigger" "$DIST" '^[[:space:]]{2}pull_request:'
notcheck "D2.3 kein release-Trigger" "$DIST" '^[[:space:]]{2}release:'
notcheck "D2.4 kein Tag-Push-Publizier-(if)-Pfad" "$DIST" 'startsWith\(github\.ref, .refs/tags/v.\)'

# D3: Der tag-bestimmende Schritt validiert Dispatch-Inputs und existierende
# Tags (fehlende Tags = klarer Abbruch, kein stilles Versagen).
check "D3.1 version-Input-Validierung (^vX.Y.Z…)" "$DIST" 'v\[0-9\]\+\\\.\[0-9\]\+\\\.\[0-9\]\+'
check "D3.2 Tag-Existenz-Check" "$DIST" 'git show-ref --verify --quiet "refs/tags/\$TAG"'

# D4: Semver-Sortierung und Vollständigkeits-Prüfung (BEIDE Flavor-APKs +
# SHA256SUMS) — unveröffentlichte oder unvollständige Versionen werden gewählt.
check "D4.1 semver-Sortierung" "$DIST" 'git tag -l "v\*" --sort=-v:refname'
check "D4.2 Vollständigkeits-Assets (app-standard-release.apk)" "$DIST" 'index\("app-standard-release\.apk"\)'
check "D4.3 Vollständigkeits-Assets (app-foss-release.apk)" "$DIST" 'index\("app-foss-release\.apk"\)'
check "D4.4 Vollständigkeits-Assets (SHA256SUMS.txt)" "$DIST" 'index\("SHA256SUMS\.txt"\)'

# D5: Alles verteilt → sauberer exit 0 (kein Fehlschlag-Alarm bei Leerlauf).
check "D5.1 No-Op-Notice" "$DIST" '::notice::Alle v\*-Versionen'
check "D5.2 exit 0 auf No-Op" "$DIST" 'NO_TARGET=true'
check "D5.3 nachfolgende Steps am TAG-Guard hängen" "$DIST" "if: env.TAG != ''"

# D6: Publikation läuft über release_github MIT explizitem Tag (Stable-Pfad im
# Fastfile: beide Flavor-APKs + SHA256SUMS — siehe T5 im test_build_retry.sh).
check "D6.1 release_github tag:\$TAG" "$DIST" 'release_github tag:"\$TAG"'
check "D6.2 Keystore-Härtung (Debug-Key-Schutz)" "$DIST" 'KEYSTORE_BASE64 fehlt'

# D7: CHANGELOG-Spiegelung inline (GH_TOKEN-Releases feuern kein release:published).
check "D7.1 changelog-Mirror-Step" "$DIST" 'Update CHANGELOG.md from GitHub releases'
check "D7.2 update_changelog.sh" "$DIST" 'scripts/update_changelog.sh'
check "D7.3 Serialsierungs-Koncurrency ohne cancelling" "$DIST" 'cancel-in-progress: false'

# D8: release-pipeline publiziert NICHT mehr bei v*-Tag-Push (Push-Pfad raus).
notcheck "D8.1 kein refs/tags/v-Publizier-Pfad in publish-release" \
  "$RELEASE" "startswith\(github.ref, 'refs/tags/v'\)"
check "D8.2 nightly bleibt Schedule/Manual" \
  "$RELEASE" "if: github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'"
check "D8.3 nightly-Cron bleibt täglich" "$RELEASE" "cron: '0 6 \* \* \*'"

# D9: deploy-fdroid feuert NICHT mehr bei release:published.
notcheck "D9.1 kein release-Trigger in deploy-fdroid" "$FDROID" '^[[:space:]]{2}release:'
check "D9.2 weekly repo-Schedule Mo 04:00 UTC" "$FDROID" "cron: '0 4 \* \* 1'"

# D10: Self-hosted F-Droid-Repo lädt NUR den Standard-Flavor (foss-APK + foss
# Checksummen im Stable-Release dürfen nicht die versionCode-Eindeutigkeit im
# Repo brechen — fdroid update würde zwei APKs derselben Version sehen).
check "D10.1 nur app-standard-release.apk herunterladen" \
  "$FDROID" --pattern "app-standard-release.apk"
notcheck "D10.2 kein .apk-Wildcard-Download mehr" "$FDROID" '--pattern "\*\.apk"'

# D11: YAML-Validität (CI: python3+yaml; lokal übersprungen, wenn python3 oder
# pyyaml fehlen — die Workflows sind durch andere Guards abgedeckt).
if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  for f in "$DIST" "$FDROID" "$RELEASE"; do
    if python3 - "$f" <<'PY' 2>/dev/null
import io, sys, yaml
src = io.open(sys.argv[1], encoding="utf-8").read()
# YAML 1.1: "on:" parst als boolean True → key 'true'; für den Validity-Check egal
data = yaml.safe_load(src)
assert isinstance(data, dict), "root muss Mapping sein"
PY
    then
      echo "  ✅ D11 $(basename "$f") parst als YAML"
    else
      echo "  ❌ D11 $(basename "$f") parst NICHT als YAML"
      FAILED=1
    fi
  done
else
  echo "  (python3/pyyaml nicht verfügbar — YAML-Parse-Check übersprungen)"
fi

# D12: Es existiert ein Selbsttest, der den release_github-STABLE-Pfad (foss)
# und die Vollständigkeitsregel absichert (ASCII-Stern als ansi-proof).
check "D12.1 test_sha256sums.sh existiert" \
  scripts/test_sha256sums.sh 'sha256sum -c'
check "D12.2 hardening-Test deckt den incompleteness-Pfad ab" \
  scripts/test_publish_release_hardening.sh 'S8: published, aber nur Standard-APK'

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ Alle Checks grün — Stable-Distribution läuft wöchentlich, Nightly täglich."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1