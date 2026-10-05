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

set -euo pipefail
cd "$(dirname "$0")/.."
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
check "T4.1 compare APK-Pfad" \
  grep -q 'published.apk app/build/outputs/apk/standard/release/app-standard-release.apk' <<<"$VERIFY_SECTION"
check "T4.2 compare Mapping-Pfad" \
  grep -q 'published-mapping.txt app/build/outputs/mapping/standardRelease/mapping.txt' <<<"$VERIFY_SECTION"
check "T4.3 compare Metadata-Pfad" \
  grep -q 'published-output-metadata.json app/build/outputs/apk/standard/release/output-metadata.json' <<<"$VERIFY_SECTION"
check "T4.4 compare AAB-Pfad (optionaler Zweig)" \
  grep -q 'published.aab app/build/outputs/bundle/standardPlayRelease/app-standard-playRelease.aab' <<<"$VERIFY_SECTION"
check_absent "T4.5 kein Pre-Flavor-Pfad apk/release" \
  grep -q 'apk/release/' <<<"$VERIFY_SECTION"
check_absent "T4.6 kein Pre-Flavor-Pfad mapping/release" \
  grep -q 'mapping/release/' <<<"$VERIFY_SECTION"

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
check "T10.1 release-pipeline.yml parst als YAML" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    yaml.safe_load(f)
"
check "T10.2 android-ci.yml parst als YAML" python3 -c "
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
check "T13.3 Version aus den publizierten Metadaten (jq)" \
  grep -q "jq -r '.versionName // empty' published-output-metadata.json" <<<"$READ_RUN"
check "T13.4 versionCode ebenfalls aus den Metadaten" \
  grep -q "jq -r '.versionCode // empty' published-output-metadata.json" <<<"$READ_RUN"
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
    printf '{"versionName":"%s","versionCode":%s}\n' "$VR_VNAME" "$VR_VCODE" > "$out" ;;
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
  GITHUB_OUTPUT="$sandbox/out" VR_LOG="$sandbox/urls" \
  VR_RELEASES="$3" VR_REV="$4" VR_VNAME="$5" VR_VCODE=5150 \
  PATH="$sandbox/bin:$PATH" bash -c "cd '$sandbox' && bash read.sh" > "$sandbox/log" 2>&1 || VR_RC=$?
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
VERIFY_SHA_USES="$(python3 -c "
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
  test "$(python3 -c "
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

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_verify_reproducibility.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
