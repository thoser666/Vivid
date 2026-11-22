#!/usr/bin/env bash
# Regressionstest: Verify-Reproducibility-Nightly (release-pipeline.yml)
# =====================================================================
# Hintergrund (Vorfall 06.09.2026, Run 34009482168): Der Verify-Job lud
# `app-release.apk` aus dem Nightly-Release — die Release-Lane publiziert
# aber seit Einführung der Flavors (standard/foss) das Asset als
# `app-standard-release.apk`. Der Job scheiterte folglich an jedem
# Nightly/Dispatch mit "app-release.apk fehlt im Release" (schon die
# Schedule-Runs 04.09./05.09.).
#
# Geprüfte Szenarien:
#   T1 verify-Job lädt das echte Asset app-standard-release.apk
#   T2 verify-Job enthält KEINE Referenz mehr auf das alte app-release.apk
#   T3 Rebuild-Tasks sind flavor-qualifiziert (assembleStandardRelease /
#      bundleStandardPlayRelease) mit den publizierten Version-Params
#   T4 Compare-Pfade nutzen die flavor-Output-Pfade (apk/standard/release,
#      mapping/standardRelease, bundle/standardPlayRelease) — ohne
#      Pre-Flavor-Pfade (apk/release, mapping/release, bundle/release)
#   T5 Sentry-Opt-out-Check im Verify-Job nutzt das standardRelease-Mapping
#   T6 Build-Release-Artefakt-Upload (release-pipeline + android-ci) nutzt
#      den flavor-Pfad app-standard-release.apk
#   T7 check_sentry_optout_mapping.sh: Default-Mapping ist standardRelease
#   T8 update_changelog.sh: Artefakte-Zeile nennt app-standard-release.apk
#   T9 Fastfile-Fallbacks (lane_context-Ersatz) nutzen flavor-Pfade
#   T10 release-pipeline.yml bleibt valides YAML
#   T11 Changelog-Mirror listet SHA256SUMS.txt als Nightly-Artefakt
#       (Nightly-Releases tragen die Prüfsummendatei seit den
#       Distributions-Quick-Wins — der Mirror muss das spiegeln)
#
# #262 (Publish-Pfad release-pipeline erzeugte nicht-konforme Releases):
#   T12 cosign-Signierung existiert AUCH im publish-release-Pfad — vorher
#       stand der Block ausschließlich in distribution-stable.yml, wodurch jedes
#       Release aus release-pipeline ohne SHA256SUMS.txt.bundle blieb
#       (v0.5.20-beta, Run 36987429609).
#   T13 der Verify-Job bestimmt das zu prüfende Release aus dem Ref statt zu
#       raten und liest die Version aus den Metadaten statt aus dem Titel.
#   T14–T16 VERHALTENSTests: das echte run-Snippet des Read-Steps läuft in
#       einer Sandbox mit gestubbtem gh/curl/unzip. Geprüft wird, WELCHES
#       Release geladen wird — Vorfallsfall (Tag-Dispatch muss das Beta nehmen,
#       nicht ein altes Nightly), Nightly-Pfad, und der Fehlerfall ohne
#       Nightly. Erst dadurch ist die Aussage belegt statt behauptet: mit dem
#       alten Code lieferte Szenario T14 ein nightly-*-Tag.
#   T19 die Publish-/Merge-Snippets (Signierung, Auto-Merge) sind auf
#       fail-loud verriegelt (set -eu bzw. set -euo pipefail) — ein still
#       auf blosses `set -e` zurückgebauter Scope wird hier rot.
#
# Repro-Fix (Nightly 18.11.2026: Compose-Anhang permutiert, R8-Teil identisch):
#   T20 der Verify-Job baut ZWEIMAL (A + B) und vergleicht über
#       scripts/compare_reproducibility.sh; Build B leert den :app-Baum und
#       nutzt keinen Build-Cache, und der Build kanonisiert den Anhang in der
#       merge*ComposeMapping-Task (sonst bewiese der Vergleich nichts).
#   T21 VERHALTENSTests des Vergleichsskripts an Fixtures: R8-Teil strikt,
#       Compose-Anhang kanonisch — und die Attribution unterscheidet
#       "Build nicht deterministisch" von "veröffentlichtes Artefakt weicht ab".

set -euo pipefail
cd "$(dirname "$0")/.."
# Interpreter über die Library: bar `python` trägt auf Windows kein PyYAML
# (nur der `py -3`-Launcher), sonst wären T14/T15 lokal rot ohne Workflow-Fehler.
source scripts/lib_workflow_yaml.sh
SCRIPT_DIR="$(pwd)/scripts"
# YAML-Zugriffe strukturell statt per Zeilenfenster (lib_workflow_yaml.sh).
source "$SCRIPT_DIR/lib_workflow_yaml.sh"

FAIL=0
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}
# Negations-Variante: `!` kann nicht als Funktionsargument uebergeben werden
# (Expandierung macht daraus einen Kommandonamen) — daher eigener Helper.
check_absent() {
  local desc="$1"; shift
  if ! "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}

WORKFLOW=.github/workflows/release-pipeline.yml
VERIFY_START=$(grep -n '^  verify-reproducibility:' "$WORKFLOW" | cut -d: -f1)
VERIFY_END=$(awk -v s="$VERIFY_START" 'NR>s && /^  [a-z-]+:/{print NR; exit}' "$WORKFLOW")
VERIFY_SECTION=$(sed -n "${VERIFY_START},${VERIFY_END}p" "$WORKFLOW")

echo "== T1: Asset-Download nutzt app-standard-release.apk =="
check "T1.1 Download app-standard-release.apk" \
  grep -q 'published.apk "$BASE/app-standard-release.apk"' <<<"$VERIFY_SECTION"
check "T1.2 Fehlermeldung benennt das echte Asset" \
  grep -q 'app-standard-release.apk fehlt im Release' <<<"$VERIFY_SECTION"

echo "== T2: Kein alter Asset-Name mehr im verify-Job =="
check_absent "T2.1 kein app-release.apk-Download" \
  grep -q 'app-release\.apk' <<<"$VERIFY_SECTION"

echo "== T3: Rebuild-Tasks flavor-qualifiziert =="
check "T3.1 Rebuild via assembleStandardRelease" \
  grep -q './gradlew :app:assembleStandardRelease' <<<"$VERIFY_SECTION"
check "T3.2 Rebuild-AAB via bundleStandardPlayRelease" \
  grep -q './gradlew :app:bundleStandardPlayRelease' <<<"$VERIFY_SECTION"
check "T3.3 Rebuild mit publizierten Version-Params" \
  grep -q -- '-PversionName=' <<<"$VERIFY_SECTION" &&
  grep -q -- '-PversionCode=' <<<"$VERIFY_SECTION"

echo "== T4: Compare-Pfade flavor-korrekt, keine Pre-Flavor-Pfade =="
# Die Rebuild-Artefakte werden von scripts/stage_repro_artifacts.sh unter die
# Release-Asset-Namen kopiert (der Job baut zweimal, Build B leert :app).
STAGE=scripts/stage_repro_artifacts.sh
check "T4.1 Staging kopiert das APK aus dem flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/app-standard-release.apk' "$STAGE"
check "T4.2 Staging kopiert das Mapping aus dem flavor-Pfad" \
  grep -q 'app/build/outputs/mapping/standardRelease/mapping.txt' "$STAGE"
check "T4.3 Staging kopiert die Metadaten aus dem flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/output-metadata.json' "$STAGE"
check "T4.4 Staging kopiert das AAB aus dem flavor-Pfad (optionaler Zweig)" \
  grep -q 'app/build/outputs/bundle/standardPlayRelease/app-standard-playRelease.aab' "$STAGE"
check_absent "T4.5 kein Pre-Flavor-Pfad apk/release" \
  grep -q 'apk/release/' "$STAGE"
check_absent "T4.6 kein Pre-Flavor-Pfad mapping/release" \
  grep -q 'mapping/release/' "$STAGE"
check_absent "T4.7 kein Pre-Flavor-Pfad bundle/release" \
  grep -q 'bundle/release/' "$STAGE"

echo "== T5: Sentry-Mapping-Nachweis im verify-Job =="
check "T5.1 check_sentry_optout_mapping.sh auf standardRelease-Mapping" \
  grep -q 'check_sentry_optout_mapping.sh app/build/outputs/mapping/standardRelease/mapping.txt' <<<"$VERIFY_SECTION"

echo "== T6: Build-Release-Artefakt-Upload flavor-korrekt =="
check "T6.1 release-pipeline Upload app-standard-release.apk" \
  grep -q 'path: app/build/outputs/apk/standard/release/app-standard-release.apk' "$WORKFLOW"
check "T6.2 android-ci Upload app-standard-release.apk" \
  grep -q 'path: app/build/outputs/apk/standard/release/app-standard-release.apk' .github/workflows/android-ci.yml

echo "== T7: Mapping-Check-Default =="
check "T7.1 Default MAPPING_RELEASE = standardRelease" \
  grep -q 'MAPPING_RELEASE:-app/build/outputs/mapping/standardRelease/mapping.txt' scripts/check_sentry_optout_mapping.sh

echo "== T8: Changelog-Artefakte-Zeile =="
check "T8.1 nightly-Artefakte-Zeile app-standard-release.apk" \
  grep -q 'app-standard-release.apk' scripts/update_changelog.sh
check "T8.2 nightly-Artefakte-Zeile nennt SHA256SUMS.txt" \
  grep -q 'SHA256SUMS.txt' scripts/update_changelog.sh

echo "== T9: Fastfile-Fallback-Pfade =="
check "T9.1 APK-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/app-standard-release.apk' fastlane/Fastfile
check "T9.2 Mapping-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/mapping/standardRelease/mapping.txt' fastlane/Fastfile
check "T9.3 Metadata-Fallback flavor-Pfad" \
  grep -q 'app/build/outputs/apk/standard/release/output-metadata.json' fastlane/Fastfile
check_absent "T9.4 kein Pre-Flavor-Fallback im Fastfile" \
  grep -qE 'apk/release/|mapping/release/' fastlane/Fastfile
check "T9.5 Nightly-Prüfsummen-Anhang in publish_release vorhanden" \
  grep -q 'assets << options\[:checksums\] if options\[:checksums\] && File.exist?(options\[:checksums\])' fastlane/Fastfile
check "T9.6 genau 2 Prüfsummen-Anhänge (stable + nightly)" \
  bash -c '[ "$(grep -cF "options[:checksums] && File.exist?(options[:checksums])" fastlane/Fastfile)" -eq 2 ]'

echo "== T10: Workflow-YAML valide =="
check "T10.1 release-pipeline.yml parst als YAML" wf_python_run -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    yaml.safe_load(f)
"
check "T10.2 android-ci.yml parst als YAML" wf_python_run -c "
import yaml, io
with io.open('.github/workflows/android-ci.yml', encoding='utf-8') as f:
    yaml.safe_load(f)
"

echo "== T11: Changelog-Mirror spiegelt die Nightly-Prüfsummendatei =="
check "T11.1 Mirror-Artefakte-Zeile enthält SHA256SUMS.txt" \
  grep -q 'SHA256SUMS.txt' scripts/update_changelog.sh

echo "== T12: cosign-Signierung im publish-release-Pfad (#262) =="
# Struktur statt Text: die Steps werden über Name/uses gefunden, nicht über eine
# Zeilenposition. Assertions bewusst exakt — ein zweiter, abweichend gepinnter
# cosign-installer wäre eine neue Supply-Chain-Lücke und soll auffallen.
COSIGN_INSTALLER='sigstore/cosign-installer@6f9f17788090df1f26f669e9d70d6ae9567deba6'
check "T12.1 cosign-installer gepinnt (identisch zu distribution-stable)" \
  test "$(wf_step_uses "$WORKFLOW" publish-release 'Install cosign')" = "$COSIGN_INSTALLER"
check "T12.2 cosign v3 als cosign-release gepinnt" \
  test "$(wf_step_with "$WORKFLOW" publish-release 'Install cosign' 'cosign-release')" = 'v3.1.3'
# Die Bedingung spiegelt das Fastlane-Kriterium `stable` (GITHUB_REF_TYPE == tag
# && Name beginnt mit v). Ohne if wäre der Step im Nightly-Lauf bedingungslos
# aktiv — dort ist der Tag unbekannt, weil er erst zur Laufzeit im Fastfile
# entsteht (nightly-<UTC>), und ${{ github.ref_name }} wäre der Branch-Name.
check "T12.3 Install-Step an refs/tags/v gebunden" \
  test "$(wf_step_if "$WORKFLOW" publish-release 'Install cosign')" = "startsWith(github.ref, 'refs/tags/v')"
check "T12.4 Sign-Step an refs/tags/v gebunden" \
  test "$(wf_step_if "$WORKFLOW" publish-release 'Sign SHA256SUMS')" = "startsWith(github.ref, 'refs/tags/v')"
SIGN_RUN="$(wf_step_run "$WORKFLOW" publish-release 'Sign SHA256SUMS')"
check "T12.5 Upload mit --clobber (idempotent, Repair-Pfad bleibt möglich)" \
  grep -q 'gh release upload .* --clobber' <<<"$SIGN_RUN"
check "T12.6 Tag aus github.ref_name, nicht geraten" \
  grep -q 'github.ref_name' <<<"$SIGN_RUN"
check "T12.7 Signiert wird die SHA256SUMS.txt des Releases" \
  grep -q 'gh release download .* -p SHA256SUMS.txt' <<<"$SIGN_RUN"
# ⚠️ Zwei Fallen, die die Mutationsprobe M1 aufgedeckt hat — beide hatten
#    denselben Effekt: der Check blieb grün, obwohl das `if` entfernt war.
#    (a) wf_step_if gibt bei "kein if gesetzt" per Hauskonvention `-` zurück,
#        nicht den Leerstring — `test … = ''` ist also immer wahr.
#    (b) Ein `check … test X && test Y` zerlegt die Shell in ZWEI Kommandos:
#        check sieht nur `test X` und meldet PASS, während `&&` den
#        Gesamtzustand verwirft. Deshalb EIN Kommando via `bash -c`.
COND_COSIGN="$(wf_step_if "$WORKFLOW" publish-release 'Install cosign')"
check "T12.8 cosign-Step ist nicht unbedingt (Bedingung gesetzt, nicht leer)" \
  bash -c '[ -n "$1" ] && [ "$1" != "-" ]' _ "$COND_COSIGN"

echo "== T13: Verify-Job bestimmt das Ziel statt zu raten (#262) =="
READ_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'Read published release')"
if [ "$READ_RUN" = "-" ] || [ -z "$READ_RUN" ]; then
  echo "  ❌ T13.0 run-Snippet nicht extrahierbar (wf_step_run gab '-' zurück)"
  FAIL=$((FAIL + 1))
  READ_RUN=""
fi
# #263: Der Revisionsvergleich ist aus dem Read-Step in den Resolve-Step
# gewandert (die erwartete Revision kommt aus dem Git-Graphen, nicht aus dem
# Lauf). Deshalb wird der Resolve-Step-Snippet schon hier extrahiert — T13.6
# prueft die Fehlermeldung und gehoert damit an DIESE Quelle, nicht an den
# Read-Step.
RESOLVE_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'Resolve published tag to commit')"
check "T13.1 Zielwahl hängt an github.ref_type" \
  grep -q 'github.ref_type' <<<"$READ_RUN"
check "T13.2 Tag kommt aus github.ref_name" \
  grep -q 'github.ref_name' <<<"$READ_RUN"
# AGPs output-metadata.json (Schema v3) verschachtelt versionName/versionCode
# unter elements[0] — NICHT auf Top-Level. Die Top-Level-Abfrage lieferte am
# 2026-10-11 leere Werte (Run 38049290429: "versionName/versionCode nicht
# lesbar"), weil der Job erst nach dem AUTOMATION_TOKEN-Fix überhaupt bis hier
# lief. Der Guard pinnt daher den verschachtelten Pfad (Blöcke escaped, sonst
# matcht `[0]` in BRE nur das Zeichen `0`).
check "T13.3 Version aus den publizierten Metadaten (jq, AGP-elements[0])" \
  grep -q "jq -r '.elements\[0\].versionName // empty' published-output-metadata.json" <<<"$READ_RUN"
check "T13.4 versionCode ebenfalls aus den Metadaten (AGP-elements[0])" \
  grep -q "jq -r '.elements\[0\].versionCode // empty' published-output-metadata.json" <<<"$READ_RUN"
# Der Klammer-sed auf den Release-Titel lieferte bei "Vivid v0.5.20-beta"
# leere Werte (keine Klammern im Titel) — der Rebuild waere mit leeren
# -P-Properties gelaufen. Er darf nicht zurückkehren.
check_absent "T13.5 kein Titel-Parsing mehr im Read-Step" \
  grep -q "sed -n 's/.*(" <<<"$READ_RUN"
check "T13.6 Fehlermeldung nennt Release und beide Revisionen" \
  grep -q 'Revision mismatch: Release \$TAG wurde aus' <<<"$RESOLVE_RUN"
check_absent "T13.7 alte Meldung ohne Kontext entfernt" \
  grep -q 'echo "::error::Version/Revision mismatch' <<<"$READ_RUN"
# Der Version-Tag-Pfad muss mapping.txt + output-metadata.json veröffentlichen,
# sonst ist der Beta-Kanal nicht verifizierbar (der Job laed genau die beiden).
# ⚠️ ZÄHLUNG, nicht blosses Vorkommen: beide Zeilen standen im Nightly-Zweig
# schon vor #262. Ein `grep -q` ohne -c war daher grün, als die Mutation nur den
# Version-Tag-Zweig entfernte (das Nightly-Vorkommen traf sie) — der Check
# prüfte damit die falsche Hälfte der Aussage. Erwartet ist je EIN Vorkommen
# pro Zweig, also genau zwei.
check "T13.8 Fastfile publiziert mapping.txt in BEIDEN Zweigen (Version-Tag + Nightly)" \
  bash -c '[ "$(grep -cF "assets << mapping if File.exist?(mapping)" fastlane/Fastfile)" -eq 2 ]'
check "T13.9 Fastfile publiziert output-metadata.json in BEIDEN Zweigen" \
  bash -c '[ "$(grep -cF "assets << metadata if File.exist?(metadata)" fastlane/Fastfile)" -eq 2 ]'
check "T13.10 Release-Artefaktpfade werden am Projektstamm verankert" \
  grep -q 'def release_artifact_path' fastlane/Fastfile

echo "== T14–T16: Verhaltenstest der Zielwahl (#262) =="
# Führt das echte run-Snippet des Read-Steps mit gestubbtem gh/curl/unzip aus.
#   $1 ref_type (tag|branch), $2 ref_name, $3 JSON von `gh release list`,
#   $4 revision im "gepackten" APK, $5 erwarteter Version-Name
# Ergebnis: VR_LOG (geladene Download-URLs), VR_OUT (GITHUB_OUTPUT), VR_RC.
vr_select() {
  local sandbox; sandbox="$(mktemp -d)"
  mkdir -p "$sandbox/bin"
  cat > "$sandbox/bin/gh" <<'STUB'
#!/usr/bin/env bash
# gh-Emulation fuer `gh release list`: der echte gh wertet --jq selbst aus
# (gojq-Semantik), also MUSS der Stub das auch tun — sonst liefert er das
# rohe JSON zurueck und der Test prueft den Stub statt des Snippets. Genau
# daran scheiterte der erste Entwurf: TAG wurde zum kompletten JSON-Array
# und der Fehlerfall (leeres Ergebnis) blieb unentdeckt.
if [ "$1" = "release" ] && [ "$2" = "list" ]; then
  filter=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --jq) filter="$2"; shift 2 ;;
      *) shift ;;
    esac
  done
  if [ -z "$filter" ]; then
    printf '%s\n' "$VR_RELEASES"
  else
    printf '%s\n' "$VR_RELEASES" | jq -r "$filter"
  fi
  exit 0
fi
exit 1
STUB
  cat > "$sandbox/bin/curl" <<'STUB'
#!/usr/bin/env bash
out=""; url=""
while [ $# -gt 0 ]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    -sfL|-sS) shift ;;
    *) url="$1"; shift ;;
  esac
done
[ -n "$url" ] && echo "$url" >> "$VR_LOG"
case "$url" in
  *output-metadata.json)
    # AGP Schema v3: versionName/versionCode liegen unter elements[0] — genau
    # wie im echten Release-Asset (roher Gradle-Output). Der Stub MUSS diese
    # Form nachbilden: mit einem flachen {"versionName":...} war T14 gruen,
    # obwohl der Job an der echten (verschachtelten) Datei scheiterte.
    printf '{"elements":[{"versionName":"%s","versionCode":%s}]}\n' "$VR_VNAME" "$VR_VCODE" > "$out" ;;
  *app-standard-playRelease.aab)
    exit 22 ;;   # kein AAB im Release → Optional-Zweig
  *) printf 'dummy' > "$out" ;;
esac
exit 0
STUB
  cat > "$sandbox/bin/unzip" <<'STUB'
#!/usr/bin/env bash
# Genau eine Zeile, im Format der echten version-control-info.textproto.
printf '  revision: "%s"\n' "$VR_REV"
STUB
  chmod +x "$sandbox/bin/gh" "$sandbox/bin/curl" "$sandbox/bin/unzip"
  # GitHub-Ausdrücke sind kein bash-Syntax und müssen vor dem Ausführen durch
  # Laufzeitwerte ersetzt werden.
  printf '%s\n' "$READ_RUN" | sed \
    -e "s/\\\${{ github\\.ref_type }}/$1/g" \
    -e "s/\\\${{ github\\.ref_name }}/$2/g" \
    -e 's/\${{ github\.sha }}/deadbeefdeadbeefdeadbeefdeadbeefdeadbeef/g' \
    -e 's/\${{ github\.repository }}/thoser666\/Vivid/g' > "$sandbox/read.sh"
  : > "$sandbox/out"; : > "$sandbox/urls"
  VR_RC=0
  # ⚠️ Ausfuehrung INNERHALB der Sandbox (Subshell mit cd). Das Snippet legt
  # nebenbei published.apk / published-mapping.txt / published-output-metadata.json
  # an — im Repo-Root landeten davon beim ersten Entwurf zwei untracked Dateien,
  # die erst beim `git status` auffielen und beim Commit hätten geblieben.
  # Der Test muss den Arbeitsbaum unberührt lassen.
  # ⚠️ PATH-Falle (MSYS/Windows): ein Kind-bash stellt beim Start
  # /c/msys64/mingw64/bin VOR die geerbte Reihenfolge — die Stubs in
  # $sandbox/bin wären dann unsichtbar, echtes `curl` liefe ins Netz und T14
  # wäre rot, ohne dass Snippet oder Workflow etwas falsch machen. Deshalb EIN
  # bash, PATH nach dem Start setzen und das Snippet sourcen (kein weiteres
  # Kind-bash, das die Reihenfolge erneut umstellen würde).
  GITHUB_OUTPUT="$sandbox/out" VR_LOG="$sandbox/urls" \
  VR_RELEASES="$3" VR_REV="$4" VR_VNAME="$5" VR_VCODE=5150 \
  bash -c 'cd "$1" || exit 1; PATH="$1/bin:$PATH"; . ./read.sh' _ "$sandbox" \
  > "$sandbox/log" 2>&1 || VR_RC=$?
  VR_OUT="$(cat "$sandbox/out")"
  VR_LOG="$(cat "$sandbox/urls")"
  VR_STDOUT="$(cat "$sandbox/log")"
  rm -rf "$sandbox"
  return 0
}

# Vorfallsfall #262: Dispatch auf refs/tags/v0.5.20-beta. Im Release-Raster
# liegt ein Nightly eines frueheren Schedule-Runs (nightly-20261002-043021,
# Run 36987429609) — genau das Release, das der alte Code geladen hat. Der
# neue Code muss den Beta-Tag nehmen.
RELEASES_BETA='[{"tagName":"nightly-20261002-043021","isPrerelease":true},{"tagName":"v0.5.20-beta","isPrerelease":false}]'
vr_select tag v0.5.20-beta "$RELEASES_BETA" \
  deadbeefdeadbeefdeadbeefdeadbeefdeadbeef 0.5.20-beta
if [ "$VR_RC" -eq 0 ] \
   && grep -q '/releases/download/v0.5.20-beta/app-standard-release.apk' <<<"$VR_LOG" \
   && ! grep -q 'nightly-' <<<"$VR_LOG"; then
  echo "  ✅ T14.1 Tag-Dispatch: der Beta-Tag wird geprueft, kein Nightly"
else
  echo "  ❌ T14.1 gewaehlt: RC=$VR_RC geladen=[$(tr '\n' ' ' <<<"$VR_LOG")] (erwartet: v0.5.20-beta, kein nightly-). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi
if grep -q '^version_name=0.5.20-beta$' <<<"$VR_OUT" \
   && grep -q '^version_code=5150$' <<<"$VR_OUT"; then
  echo "  ✅ T14.2 Version aus den Metadaten (nicht aus dem Titel)"
else
  echo "  ❌ T14.2 GITHUB_OUTPUT=[$(tr '\n' ' ' <<<"$VR_OUT")] (erwartet version_name=0.5.20-beta / version_code=5150). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# Nightly-Pfad (schedule, ref_type != tag): unveraendert das neueste
# nightly-Prerelease. Der Pfad darf durch die Tag-Branch nicht kaputtgegangen
# sein — er ist seit dem Nightly-Push der tägliche Normalfall.
RELEASES_NIGHTLY='[{"tagName":"v0.5.20-beta","isPrerelease":false},{"tagName":"nightly-20261003-110936","isPrerelease":true}]'
vr_select branch develop "$RELEASES_NIGHTLY" \
  deadbeefdeadbeefdeadbeefdeadbeefdeadbeef 0.5.20-nightly.511
if [ "$VR_RC" -eq 0 ] \
   && grep -q '/releases/download/nightly-20261003-110936/app-standard-release.apk' <<<"$VR_LOG" \
   && grep -q '^version_name=0.5.20-nightly.511$' <<<"$VR_OUT"; then
  echo "  ✅ T14.3 Nightly-Pfad: neuestes nightly-Prerelease wird geprueft"
else
  echo "  ❌ T14.3 RC=$VR_RC geladen=[$(tr '\n' ' ' <<<"$VR_LOG")] out=[$(tr '\n' ' ' <<<"$VR_OUT")] (erwartet nightly-20261003-110936 / 0.5.20-nightly.511). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# Fehlerfall bleibt ein Fehler: schedule ohne jedes nightly-Prerelease im
# Raster muss abbrechen, nicht still auf ein Version-Tag ausweichen.
vr_select branch develop '[{"tagName":"v0.5.20-beta","isPrerelease":false}]' \
  deadbeefdeadbeefdeadbeefdeadbeefdeadbeef 0.5.20-nightly.511
if [ "$VR_RC" -ne 0 ] && grep -q 'Kein nightly-Release gefunden' <<<"$VR_STDOUT"; then
  echo "  ✅ T14.4 schedule ohne Nightly bricht mit klarer Meldung ab"
else
  echo "  ❌ T14.4 RC=$VR_RC (erwartet != 0). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# #263: Der Read-Step VERGLEICHT die Revision nicht mehr — das ist der
# Resolve-Step (T18.4 prueft dieselbe Aussage: fremde Revision bleibt rot und
# nennt Release und beide Revisionen). Was im Read-Step jetzt failen muss, ist
# eine Revision, die sich gar nicht auslesen laesst: ein stilles Weiterlaufen
# mit leerem Wert waere schlimmer als der alte Vergleich, weil der Rebuild
# danach mit leeren -P-Properties liefe.
vr_select tag v0.5.20-beta "$RELEASES_BETA" "" 0.5.20-beta
if [ "$VR_RC" -ne 0 ] \
   && grep -q 'Revision im APK nicht lesbar' <<<"$VR_STDOUT"; then
  echo "  ✅ T14.5 nicht auslesbare Revision bricht schon im Read-Step ab"
else
  echo "  ❌ T14.5 RC=$VR_RC (erwartet != 0 mit 'Revision im APK nicht lesbar'). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

echo "== T15: Verify-Job haengt nicht mehr am Workflow-HEAD (#263) =="
# Der Job verglich die eingebaute Revision gegen github.sha — den Commit des
# Laufs. Der Release entsteht dagegen aus `origin/develop`, das der Publish-Step
# NACH dem Checkout frisch holt (fastlane/Fastfile Z. 444). Beide stimmen nur
# ueberein, solange nichts auf develop nachlaeuft; zwischen Publish und Verify
# liegen zwei Gradle-Builds.
#
# ⚠️ Geprueft wird der AUSDRUCK `${{ github.sha }}`, nicht die Zeichenkette
# `github.sha`: der Job muss weiterhin erklären können, warum er ihn nicht mehr
# benutzt (Prosa-Kommentar), und genau diese Unterscheidung hat schon einmal
# einen Guard gekippt (Kommentar-Zitate, #262). Ein Kommentar, der den Ausdruck
# nur erwaehnt, ist erlaubt — ihn zu VERWENDEN nicht.
RESOLVE_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'Resolve published tag to commit')"
if [ "$RESOLVE_RUN" = "-" ] || [ -z "$RESOLVE_RUN" ]; then
  echo "  ❌ T15.0 run-Snippet nicht extrahierbar (wf_step_run gab '-' zurück)"
  FAIL=$((FAIL + 1))
  RESOLVE_RUN=""
fi
# Gegenprobe auf die Quelle aus T13.6: dieselbe Aussage, dort am Read-STEP
# geprueft und seit #263 dort nicht mehr vorhanden. Verhindert, dass ein
# spaeterer Umbau die Meldung wieder in den falschen Step zurueckwandert.
VERIFY_SHA_USES="$(wf_python_run -c "
import io, re, sys, yaml
d = yaml.safe_load(io.open('$WORKFLOW', encoding='utf-8'))
job = d['jobs']['verify-reproducibility'] or {}
expr = '\${{ github.sha }}'
hits = []
for s in job.get('steps') or []:
    for key in ('if', 'run', 'env', 'with'):
        if expr in str(s.get(key, '')):
            hits.append(str(s.get('name')) + ':' + key)
print(','.join(hits))
")"
check "T15.1 Ausdruck github.sha kommt im Verify-Job nicht mehr vor" \
  test -z "$VERIFY_SHA_USES"
check "T15.2 Resolve-Step hat die ID ref" \
  test "$(wf_python_run -c "
import io, yaml
d = yaml.safe_load(io.open('$WORKFLOW', encoding='utf-8'))
step = next((s for s in d['jobs']['verify-reproducibility']['steps']
             if 'Resolve published tag' in str(s.get('name', ''))), {})
print(step.get('id', '-'))
")" = "ref"
check "T15.3 Resolve-Step liest den Tag aus dem Read-Step" \
  grep -q 'steps.nightly.outputs.tag' <<<"$RESOLVE_RUN"
check "T15.4 Resolve-Step liest die eingebaute Revision aus dem Read-Step" \
  grep -q 'steps.nightly.outputs.embedded_revision' <<<"$RESOLVE_RUN"
check "T15.5 Read-Step gibt tag= als Output aus" \
  grep -q 'echo "tag=\$TAG" >> "\$GITHUB_OUTPUT"' <<<"$READ_RUN"
check "T15.6 Read-Step gibt embedded_revision= als Output aus" \
  grep -q 'echo "embedded_revision=\$REV" >> "\$GITHUB_OUTPUT"' <<<"$READ_RUN"
# Annotated Tag (v*-beta, `git tag -a`): die Graph-API liefert die SHA des
# TAG-OBJEKTS. Ohne Peel waere der Vergleich bei jedem Version-Tag-Publish
# garantiert falsch.
check "T15.7 Resolve-Step peel't annotated Tags (git/tags-Endpunkt)" \
  grep -q 'git/tags/\$TAG_SHA' <<<"$RESOLVE_RUN"
check "T15.8 Peel nur bei Objekt-Typ tag" \
  grep -q 'if \[ "\$OBJ_TYPE" = "tag" \]' <<<"$RESOLVE_RUN"
check "T15.9 Unerwarteter Objekt-Typ wird abgewiesen" \
  grep -q 'Unerwarteter Objekt-Typ' <<<"$RESOLVE_RUN"
check "T15.10 Retry dreimal bei API-Fehlern" \
  grep -q 'for attempt in 1 2 3' <<<"$RESOLVE_RUN"
check "T15.11 fail-loud (set -eu) im Resolve-Step" \
  grep -q '^set -eu$' <<<"$RESOLVE_RUN"
check_absent "T15.12 Read-Step vergleicht die Revision nicht mehr selbst" \
  grep -q 'if \[ "\$REV" != ' <<<"$READ_RUN"

echo "== T18: Verhaltenstest der Tag-zu-Commit-Auflösung (#263) =="
# Führt das echte run-Snippet des Resolve-Steps mit einem gh-Stub aus, der den
# GIT-GRAPHEN nachbildet (nicht nur antwortet — siehe Hausmuster in
# CONTRIBUTING.md). Der Stub kennt bewusst KEIN github.sha: der neue Code darf
# es nicht brauchen. Baute jemand den Vergleich auf den Workflow-HEAD zurück,
# bliebe der Ausdruck unersetzt und der Lauf schläfe mit bash-Syntaxfehler fehl.
#   $1 tag            $2 obj_type (commit|tag|blob)
#   $3 obj_sha        $4 embedded_revision (aus dem APK)
#   $5 Anzahl API-Fehlversuche vor Erfolg   $6 peel_sha (bei annotated)
#
# ⚠️ Die Reihenfolge in den Aufrufen ist genauso Teil des Vertrags wie diese
# Liste: der erste Entwurf notierte hier $1 als obj_type und rief die Funktion
# mit der echten Signatur auf — T18.4/T18.6 prüften dadurch die falsche Variable
# und blieben faelschlich gruen bzw. rot.
vr_resolve() {
  local sandbox; sandbox="$(mktemp -d)"
  mkdir -p "$sandbox/bin"
  cat > "$sandbox/bin/gh" <<'STUB'
#!/usr/bin/env bash
# gh-Emulation fuer `gh api`: --jq wird SELBST ausgewertet (gojq-Semantik).
# Ohne das liefert der Stub die rohe JSON, der Code speichert sie kommentarlos
# als "Commit", und der Vergleich schlaegt grundlos fehl — genau der Fehler,
# den #262 beim release-list-Stub aufgedeckt hat und der hier beim ersten
# Entwurf erneut auftrat (T18.2 sah `{"object":{"sha":...}}` als Commit-SHA).
filter=""
args=()
while [ $# -gt 0 ]; do
  case "$1" in
    --jq) filter="$2"; shift 2 ;;
    *) args+=("$1"); shift ;;
  esac
done
set -- "${args[@]:-}"
emit() {
  if [ -z "$filter" ]; then printf '%s\n' "$1"; else printf '%s\n' "$1" | jq -r "$filter"; fi
}
if [ "${1:-}" = "api" ]; then
  case "${2:-}" in
    repos/*/git/ref/tags/*)
      # Transiente API-Fehler als Zähler, damit der Retry-Pfad testbar ist.
      if [ -f "$VR_FAILFILE" ]; then
        left="$(cat "$VR_FAILFILE")"
        if [ "$left" -gt 0 ]; then
          echo "$((left - 1))" > "$VR_FAILFILE"
          echo "gh: HTTP 502 (transient)" >&2
          exit 1
        fi
      fi
      emit "$(printf '{"object":{"type":"%s","sha":"%s"}}' "$VR_OBJ_TYPE" "$VR_OBJ_SHA")"
      exit 0 ;;
    repos/*/git/tags/*)
      emit "$(printf '{"object":{"sha":"%s"}}' "$VR_PEEL_SHA")"
      exit 0 ;;
  esac
fi
exit 1
STUB
  # sleep stubben: der Retry wartet real 2+4 s, das Suite wuerde ausbreiten.
  printf '#!/usr/bin/env bash\nexit 0\n' > "$sandbox/bin/sleep"
  chmod +x "$sandbox/bin/gh" "$sandbox/bin/sleep"
  printf '%s\n' "$RESOLVE_RUN" | sed \
    -e "s/\\\${{ steps\\.nightly\\.outputs\\.tag }}/$1/g" \
    -e "s/\\\${{ steps\\.nightly\\.outputs\\.embedded_revision }}/$4/g" \
    -e 's/\${{ github\.repository }}/thoser666\/Vivid/g' > "$sandbox/resolve.sh"
  echo "$5" > "$sandbox/fails"
  : > "$sandbox/out"
  VR_RC=0
  VR_OBJ_TYPE="$2" VR_OBJ_SHA="$3" VR_PEEL_SHA="$6" VR_FAILFILE="$sandbox/fails" \
  GITHUB_OUTPUT="$sandbox/out" PATH="$sandbox/bin:$PATH" \
  bash "$sandbox/resolve.sh" > "$sandbox/log" 2>&1 || VR_RC=$?
  VR_OUT="$(cat "$sandbox/out")"
  VR_STDOUT="$(cat "$sandbox/log")"
  rm -rf "$sandbox"
  return 0
}

C1=1111111111111111111111111111111111111111
C2=2222222222222222222222222222222222222222
C3=3333333333333333333333333333333333333333

# T18.1: lightweight Tag (nightly-*) zeigt direkt auf den Commit.
vr_resolve nightly-20261003-110936 commit "$C1" "$C1" 0 "$C1"
if [ "$VR_RC" -eq 0 ] && grep -q "^commit=$C1$" <<<"$VR_OUT"; then
  echo "  ✅ T18.1 lightweight Tag: Vergleich gegen den Tag-Commit"
else
  echo "  ❌ T18.1 RC=$VR_RC out=[$(tr '\n' ' ' <<<"$VR_OUT")] (erwartet 0 / commit=$C1). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.2: annotated Tag (v*-beta). Objekt-SHA C3 != Commit C1 — der Peel ist der
# ganze Unterschied, ohne ihn schlaegt der Vergleich fehl.
vr_resolve v0.6.0-beta tag "$C3" "$C1" 0 "$C1"
if [ "$VR_RC" -eq 0 ] && grep -q "^commit=$C1$" <<<"$VR_OUT" && grep -q 'peel' <<<"$VR_STDOUT"; then
  echo "  ✅ T18.2 annotated Tag: eine Ebene gep peel't (Objekt $C3 ≠ Commit $C1)"
else
  echo "  ❌ T18.2 RC=$VR_RC out=[$(tr '\n' ' ' <<<"$VR_OUT")] (erwartet 0 / commit=$C1). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.3: Gegenprobe — das APK HAETTE die Tag-Objekt-SHA gebaut (Objekt C3,
# peel ergibt C1). Muss rot sein: es beweist, dass nicht etwa gegen die
# Objekt-SHA verglichen wird.
vr_resolve v0.6.0-beta tag "$C3" "$C3" 0 "$C1"
if [ "$VR_RC" -ne 0 ] && grep -q 'Revision mismatch: Release v0.6.0-beta' <<<"$VR_STDOUT"; then
  echo "  ✅ T18.3 Tag-Objekt-SHA statt Commit im APK wird erkannt"
else
  echo "  ❌ T18.3 RC=$VR_RC (erwartet != 0). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.4: echter Mismatch bleibt rot, mit beiden Revisionen im Text.
vr_resolve nightly-20261003-110936 commit "$C1" "$C2" 0 "$C1"
if [ "$VR_RC" -ne 0 ] && grep -q "wurde aus $C2 gebaut, der Tag zeigt auf $C1" <<<"$VR_STDOUT"; then
  echo "  ✅ T18.4 fremd gebautes Release bleibt rot und nennt beide Revisionen"
else
  echo "  ❌ T18.4 RC=$VR_RC (erwartet != 0 mit Kontext). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.5: die API ist dreimal nicht erreichbar → hart abbrechen. KEIN stiller
# Rueckfall auf github.sha, das waere genau die zu beseitigende Annahme.
vr_resolve nightly-20261003-110936 commit "$C1" "$C1" 99 "$C1"
if [ "$VR_RC" -ne 0 ] && grep -q 'nicht aufloesbar' <<<"$VR_STDOUT" && ! grep -q "$C1" <<<"$VR_STDOUT"; then
  echo "  ✅ T18.5 API dreimal tot: harter Abbruch, kein Rueckfall auf den HEAD"
else
  echo "  ❌ T18.5 RC=$VR_RC (erwartet != 0 ohne Commit-Vergleich). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.6: leere eingebaute Revision → Abbruch statt Vergleich gegen nichts.
vr_resolve nightly-20261003-110936 commit "$C1" "" 0 "$C1"
if [ "$VR_RC" -ne 0 ] && grep -q 'Voraussetzungen fehlen' <<<"$VR_STDOUT"; then
  echo "  ✅ T18.6 fehlende Voraussetzungen brechen ab"
else
  echo "  ❌ T18.6 RC=$VR_RC (erwartet != 0). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

# T18.7: unerwarteter Objekt-Typ (z. B. Blob) wird abgewiesen statt verglichen.
vr_resolve nightly-20261003-110936 blob "$C1" "$C1" 0 "$C1"
if [ "$VR_RC" -ne 0 ] && grep -q 'Unerwarteter Objekt-Typ' <<<"$VR_STDOUT"; then
  echo "  ✅ T18.7 unerwarteter Objekt-Typ wird abgewiesen"
else
  echo "  ❌ T18.7 RC=$VR_RC (erwartet != 0). Log: $VR_STDOUT"
  FAIL=$((FAIL + 1))
fi

echo "== T19: Fail-Loud der Publish-/Merge-Snippets (release-pipeline) =="
# Die publish-kritischen run-Snippets sind auf fail-loud verriegelt (Stand
# des Repo-Commits). Ein still auf blosses `set -e` zurückgebauter Scope
# überlebt einen ungesetzten Variablennamen (ohne -u) und einen toten
# Pipe-Teil (ohne -pipefail) — genau die Fehlerklasse, die dieses Gate
# schliesst. Die Erwartung ist EXAKT: jede Aufweichung wird hier rot, und ein
# Tippfehler im Step-Namen liefert "-" vom wf_step_run-Helper und ebenfalls
# rot (kein stillgruen bei unbekanntem Pfad).
rel_sign_first="$(wf_step_run "$WORKFLOW" publish-release 'Sign SHA256SUMS and attach' \
  | tr -d '\r' | awk 'NF && $1 !~ /^#/ {print; exit}')"
rel_merge_first="$(wf_step_run "$WORKFLOW" publish-release 'Auto-merge (checks' \
  | tr -d '\r' | awk 'NF && $1 !~ /^#/ {print; exit}')"
check "T19.1 Sign-Snippet (SHA256SUMS) beginnt mit set -eu" \
  test "$rel_sign_first" = "set -eu"
check "T19.2 Auto-Merge-Snippet beginnt mit set -euo pipefail (Pipes)" \
  test "$rel_merge_first" = "set -euo pipefail"

echo "== T20: Verify-Job baut zweimal und vergleicht kanonisch (Repro-Fix 18.11.2026) =="
BUILD_A_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'build A')"
BUILD_B_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'build B')"
COMPARE_RUN="$(wf_step_run "$WORKFLOW" verify-reproducibility 'Compare hashes')"
check "T20.1 Build A ist als Step extrahierbar" \
  bash -c '[ -n "$1" ] && [ "$1" != "-" ]' _ "$BUILD_A_RUN"
check "T20.2 Build B ist als Step extrahierbar" \
  bash -c '[ -n "$1" ] && [ "$1" != "-" ]' _ "$BUILD_B_RUN"
check "T20.3 Build B leert den :app-Build-Baum" \
  grep -q ':app:clean' <<<"$BUILD_B_RUN"
check_absent "T20.4 Build A leert nichts (erster Build im frischen Job)" \
  grep -q ':app:clean' <<<"$BUILD_A_RUN"
check "T20.5 Build B baut ohne Build-Cache" \
  grep -q -- '--no-build-cache' <<<"$BUILD_B_RUN"
check_absent "T20.6 Build A nutzt keinen Cache-Bypass (Vergleichsbasis bleibt der Normalfall)" \
  grep -q -- '--no-build-cache' <<<"$BUILD_A_RUN"
check "T20.7 Build A staged nach rebuild-a" \
  grep -q 'stage_repro_artifacts.sh rebuild-a' <<<"$BUILD_A_RUN"
check "T20.8 Build B staged nach rebuild-b" \
  grep -q 'stage_repro_artifacts.sh rebuild-b' <<<"$BUILD_B_RUN"
check "T20.9 Compare ruft das Vergleichsskript mit published, A und B" \
  grep -q 'compare_reproducibility.sh published rebuild-a rebuild-b' <<<"$COMPARE_RUN"
check "T20.10 Compare staged die Release-Assets unter die Asset-Namen" \
  grep -q 'cp published-mapping.txt published/mapping.txt' <<<"$COMPARE_RUN"
# Der alte Inline-Vergleich konnte nur "rot" sagen, nicht WARUM. Er darf nicht
# zurückkehren: die Attribution (Build vs. Umgebung) hängt an der Auswertung
# beider Builds und gehört damit in ein getestetes Skript.
check_absent "T20.11 kein Inline-cmp-Vergleich mehr im Workflow" \
  grep -qF 'cmp -s "$local_published"' <<<"$COMPARE_RUN"
check "T20.12 Vergleichsskript ist vorhanden" test -s scripts/compare_reproducibility.sh
check "T20.13 Staging-Skript ist vorhanden" test -s scripts/stage_repro_artifacts.sh
# Build-seitige Kanonisierung: sie muss IN der Merge-Task laufen (doLast), nicht
# in einem Nachlauf-Task — sonst sind die Bytes erst nach dem Anhängen sortiert.
check "T20.14 Kanonisierung hängt an der merge*ComposeMapping-Task" \
  grep -q 'it.name.startsWith("merge") && it.name.endsWith("ComposeMapping")' app/build.gradle.kts
check "T20.15 Kanonisierung läuft als doLast in der Merge-Task" \
  grep -q 'candidates.distinct().forEach { canonicalizeComposeMappingFile(it) }' app/build.gradle.kts
check "T20.16 Einträge werden sortiert (Comparator wird angewandt)" \
  grep -q 'sortedWith(composeMappingByteOrder)' app/build.gradle.kts
check "T20.17 Sortierung ist Byte-Reihenfolge (UTF-8), nicht UTF-16" \
  grep -q 'toByteArray(Charsets.UTF_8)' app/build.gradle.kts
check "T20.18 RELEASE.md nennt die Kanonisierung des Compose-Anhangs" \
  grep -q 'Compose-Group-Mapping' RELEASE.md

echo "== T21: Verhaltensprobe des Reproduzierbarkeits-Vergleichs (Fixtures) =="
# Baut die drei Verzeichnisse mit den Release-Asset-Namen nach. Die Zahlen in
# $3 sind die Anhang-Einträge (1 = alpha, 2 = beta, 3 = gamma, 9 = gamma mit
# anderem Ziel); die Reihenfolge der Ziffern ist die Reihenfolge im File.
# Aufruf: repro_mapping <datei> <obf-name im R8-Teil> <reihenfolge> [kein-anhang]
repro_mapping() {
  local target="$1" obf="$2" order="${3:-123}" mode="${4:-mit-anhang}"
  {
    printf '# compiler: R8\n'
    printf '# pg_map_hash: SHA-256 deadbeef\n'
    printf 'com.vivid.Foo -> a:\n'
    printf '    void bar() -> %s\n' "$obf"
    printf '    int baz() -> b\n'
  } >"$target"
  [[ "$mode" == "kein-anhang" ]] && return 0
  # Einfache Quotes: `$$compose` wäre sonst die PID der Shell.
  printf 'ComposeStackTrace -> $$compose:\n' >>"$target"
  local i digit line
  for ((i = 0; i < ${#order}; i++)); do
    digit="${order:i:1}"
    case "$digit" in
      1) line='    1:1:void com.vivid.Foo.alpha(androidx.compose.runtime.Composer,int):10:10 -> m$1' ;;
      2) line='    1:1:void com.vivid.Foo.beta(androidx.compose.runtime.Composer,int):11:11 -> m$2' ;;
      3) line='    1:1:void com.vivid.Foo.gamma(androidx.compose.runtime.Composer,int):12:12 -> m$3' ;;
      9) line='    1:1:void com.vivid.Foo.gamma(androidx.compose.runtime.Composer,int):12:12 -> m$9' ;;
    esac
    printf '%s\n' "$line" >>"$target"
  done
}
repro_seed() { # $1 = Basisverzeichnis: drei identische Artefakt-Sätze
  local base="$1" sub
  for sub in published rebuild-a rebuild-b; do
    mkdir -p "$base/$sub"
    printf 'apk-payload' >"$base/$sub/app-standard-release.apk"
    printf '{"elements":[{"versionName":"0.5.20-nightly.511","versionCode":5150}]}\n' \
      >"$base/$sub/output-metadata.json"
    repro_mapping "$base/$sub/mapping.txt" a 123
  done
}
repro_run() { # $1 = Fallverzeichnis -> REPRO_RC/REPRO_OUT
  REPRO_RC=0
  REPRO_OUT="$(bash scripts/compare_reproducibility.sh "$1/published" "$1/rebuild-a" "$1/rebuild-b" 2>&1)" \
    || REPRO_RC=$?
}
repro_expect() { # $1=Beschreibung $2=erwarteter RC, Rest: Muster im Output
  local desc="$1" want="$2"; shift 2
  local pattern missing=''
  if [[ "$REPRO_RC" -ne "$want" ]]; then
    echo "  ❌ $desc: RC=$REPRO_RC (erwartet $want). Output: $(tr '\n' ' ' <<<"$REPRO_OUT")"
    FAIL=$((FAIL + 1))
    return 0
  fi
  for pattern in "$@"; do
    grep -qF -- "$pattern" <<<"$REPRO_OUT" || missing+=" '$pattern'"
  done
  if [[ -n "$missing" ]]; then
    echo "  ❌ $desc: Muster fehlt:$missing. Output: $(tr '\n' ' ' <<<"$REPRO_OUT")"
    FAIL=$((FAIL + 1))
  else
    echo "  ✅ $desc"
  fi
}

R21="$(mktemp -d)"
trap 'rm -rf "$R21"' EXIT

D="$R21/t1"; repro_seed "$D"; repro_run "$D"
repro_expect "T21.1 identische Sätze: published == A == B ist grün" 0 \
  "Alle Artefakte reproduzierbar"

# Vorfallsfall 18.11.2026: derselbe Anhang, nur andere Reihenfolge.
D="$R21/t2"; repro_seed "$D"; repro_mapping "$D/published/mapping.txt" a 321
repro_run "$D"
repro_expect "T21.2 permutierter Anhang (Menge identisch) ist grün, aber warnt sichtbar" 0 \
  "::warning::" "nur in der REIHENFOLGE verschieden"

D="$R21/t3"; repro_seed "$D"; repro_mapping "$D/rebuild-a/mapping.txt" c 123
repro_run "$D"
repro_expect "T21.3 abweichender R8-Teil ist rot und wird als R8-Teil benannt" 1 \
  "R8-Teil unterscheidet sich"

D="$R21/t4"; repro_seed "$D"; repro_mapping "$D/rebuild-a/mapping.txt" a 129
repro_run "$D"
repro_expect "T21.4 abweichender Anhang ist auch als sortierte Menge rot" 1 \
  "auch als sortierte Menge"
check_absent "T21.4b Mengen-Differenz wird NICHT als reine Reihenfolge entschuldigt" \
  grep -qF -- 'nur in der REIHENFOLGE verschieden' <<<"$REPRO_OUT"

D="$R21/t5"; repro_seed "$D"; printf 'apk-payload-2' >"$D/published/app-standard-release.apk"
repro_run "$D"
repro_expect "T21.5 deterministischer Build + abweichendes Release wird der Umgebung zugeordnet" 1 \
  "VERÖFFENTLICHTE Artefakt weicht aber ab" "im Build ist nichts zu reparieren"

D="$R21/t6"; repro_seed "$D"; printf '{"elements":[]}\n' >"$D/rebuild-b/output-metadata.json"
repro_run "$D"
repro_expect "T21.6 zwei frische Builds mit Unterschied werden dem Build zugeordnet" 1 \
  "BUILD ist NICHT deterministisch"

D="$R21/t7"; repro_seed "$D"; repro_mapping "$D/published/mapping.txt" a 123 kein-anhang
repro_run "$D"
repro_expect "T21.7 fehlender Compose-Anhang auf einer Seite ist rot" 1 \
  "Compose-Anhang vorhanden? published=nein"

D="$R21/t8"; repro_seed "$D"; printf 'aab' >"$D/rebuild-a/app-standard-playRelease.aab"
repro_run "$D"
repro_expect "T21.8 AAB nur auf einer Seite ist rot (Staging unvollständig)" 1 \
  "nur auf einer Seite vorhanden"

# Positivkontrolle für den optionalen AAB-Zweig: identische AABs bleiben grün.
D="$R21/t9"; repro_seed "$D"
printf 'aab' >"$D/published/app-standard-playRelease.aab"
cp "$D/published/app-standard-playRelease.aab" "$D/rebuild-a/app-standard-playRelease.aab"
cp "$D/published/app-standard-playRelease.aab" "$D/rebuild-b/app-standard-playRelease.aab"
repro_run "$D"
repro_expect "T21.9 identisches AAB in allen drei Seiten bleibt grün" 0 \
  "Alle Artefakte reproduzierbar"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_verify_reproducibility.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
