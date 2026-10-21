#!/usr/bin/env bash
# Selbsttest: Dependabot Auto-Merge — die Fehlerursache LESEN statt raten.
#
# Beleg fuer den alten Defekt: Run 38247604436 (13.10.2026, upload-artifact-
# Bump, PR #289), zwei Haelften in einem Lauf:
#   1) `gh pr merge --auto --squash ... 2>/dev/null` scheiterte, und der
#      Fehlertext (`GraphQL: refusing to allow a GitHub App to create or update
#      workflow .github/workflows/release-pipeline.yml without `workflows`
#      permission (mergePullRequest)`) landete im verschluckten stderr.
#   2) Der Fallback erriet daraus "keine Branch-Protection" — falsch: das Repo
#      hat allow_auto_merge=true, und die Pflicht-Checks "Build & Test" und
#      "Secret Guard" stehen. Der direkte Nachschub-Merge scheiterte dann mit
#      demselben echten Fehler und machte den Job rot (Run-Log, Zeile
#      `##[error]Process completed with exit code 1`).
#
# Gegenprobe zur Ticket-Behauptung ("GITHUB_TOKEN darf Workflow-PRs nie
# mergen"): PR #291 (setup-ruby) aenderte distribution-stable.yml UND
# release-pipeline.yml und wurde ueber --auto von app/github-actions gemergt
# (Run 38247767474: "Auto-Merge aktiviert (squash)"). Die Verweigerung haengt
# also nicht vorhersehbar an der Dateiliste. Deshalb prueft dieser Test die
# Klassifizierung am FEHLERFALL, nicht ein Vorab-Blocking nach Dateien — ein
# Vorab-Block wuerde #291-Klasse regressieren, ein Verschlucken erzeugt rote
# Laeufe mit falscher Diagnose.
#
# Methodik (Hausregeln): Verhaltenstests, keine Zeilenvergleiche — der run-Block
# wird per wf_step_run aus dem YAML gezogen und mit einem gh-Stub ausgefuehrt.
# Der Stub emuliert das FILTERVERHALTEN des Originals (#262): `--jq` wird mit
# echtem jq gegen die Fixture-JSON ausgewertet, sonst wird die Treue des Stubs
# geprueft statt der Logik des Steps. Fehlertexte sind die realen aus den
# belegten Runs bzw. den gh-cli-Issues #11493/#13398/#8206.
#
# Mutationsnachweis: vier Mutationen des Snippets muessen die jeweilige
# Assertion FEHLSCHLAGEN lassen. Das beweist, dass die Szenarien den Defekt
# ueberhaupt erkennen — ein Test, der nie rot wird, wuerde nichts belegen.
# Die Negativkontrolle ist der unveraenderte Snippet: er muss alle Szenarien
# bestehen.
#
# Aufruf: bash scripts/test_dependabot_auto_merge.sh
set -euo pipefail
cd "$(dirname "$0")/.."

source scripts/lib_workflow_yaml.sh

WF=".github/workflows/dependabot-auto-merge.yml"
SELF="scripts/test_dependabot_auto_merge.sh"
PASS=0
FAIL=0
ok()  { PASS=$((PASS + 1)); }
bad() { printf '❌ FAIL: %s\n' "$1" >&2; FAIL=$((FAIL + 1)); }

command -v jq >/dev/null 2>&1 || { echo "❌ [auto-merge-test] jq fehlt — der Stub braucht es fuer --jq." >&2; exit 1; }
[[ -f "$WF" ]] || { echo "❌ [auto-merge-test] $WF fehlt." >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ── Fixtures: die realen Fehlertexte ────────────────────────────────────────
ERR_WF="$TMP/err_workflows_permission.txt"      # Run 38247604436 (direkt belegt)
ERR_EN="$TMP/err_automerge_permission.txt"      # cli/cli#11493 (enablePullRequestAutoMerge)
ERR_DIS="$TMP/err_auto_merge_disabled.txt"      # cli/cli#13398
ERR_UNK="$TMP/err_check_failing.txt"            # cli/cli#8206
CMT_EMPTY="$TMP/comments_empty.json"
CMT_MARKED="$TMP/comments_marked.json"

printf '%s\n' 'GraphQL: refusing to allow a GitHub App to create or update workflow `.github/workflows/release-pipeline.yml` without `workflows` permission (mergePullRequest)' > "$ERR_WF"
printf '%s\n' 'GraphQL: Pull request refusing to allow a GitHub App to create or update workflow `.github/workflows/release.yml` without `workflows` permission (enablePullRequestAutoMerge)' > "$ERR_EN"
printf '%s\n' 'GraphQL: Auto merge is not allowed for this repository (enablePullRequestAutoMerge)' > "$ERR_DIS"
printf '%s\n' 'GraphQL: Required status check "Build & Test" is failing. (mergePullRequest)' > "$ERR_UNK"
printf '%s' '{"comments":[]}' > "$CMT_EMPTY"
printf '%s' '{"comments":[{"body":"schon mal gemeldet"},{"body":"Hinweis\n<!-- dependabot-auto-merge: manual-merge-required -->"}]}' > "$CMT_MARKED"

# ── gh-Stub: emuliert die drei Subcommands des Steps inklusive --jq ─────────
# Ein Stub, der --jq ignoriert, liefert dem Step rohe JSON — genau die Falle aus
# #262. Deshalb wertet der Stub den Filter mit echtem jq ueber die Fixture aus.
cat > "$TMP/gh" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$GH_LOG"
case "${1:-} ${2:-}" in
  "pr merge")
    if [ -n "${GH_MERGE_ERR:-}" ] && [ -f "$GH_MERGE_ERR" ]; then
      cat "$GH_MERGE_ERR" >&2
    fi
    exit "${GH_MERGE_RC:-0}"
    ;;
  "pr view")
    filter=""
    prev=""
    for a in "$@"; do
      if [ "$prev" = "--jq" ]; then filter="$a"; fi
      prev="$a"
    done
    if [ -z "$filter" ]; then
      echo "gh-Stub: --jq fehlt — der Filter des Originals wurde nicht ausgewertet ($*)" >&2
      exit 3
    fi
    jq -r "$filter" "$GH_COMMENTS_JSON"
    ;;
  "pr comment")
    exit 0
    ;;
  *)
    echo "gh-Stub: unbekannter gh-Aufruf: $*" >&2
    exit 99
    ;;
esac
STUB

# ── Sandbox: den echten run-Block mit gestubbtem gh ausfuehren ──────────────
CASE=0
snip_run() { # $1 = run-Snippet des Steps
  CASE=$((CASE + 1))
  local d="$TMP/run$CASE"
  mkdir -p "$d/bin" "$d/tmp"
  cp "$TMP/gh" "$d/bin/gh"
  chmod +x "$d/bin/gh"
  : > "$d/gh.log"
  : > "$d/out"
  : > "$d/err"
  set +e
  OUT="$(PATH="$d/bin:$PATH" \
        TMPDIR="$d/tmp" \
        GH_LOG="$d/gh.log" \
        GITHUB_OUTPUT="$d/out" \
        GH_MERGE_ERR="${GH_MERGE_ERR:-}" \
        GH_MERGE_RC="${GH_MERGE_RC:-0}" \
        GH_COMMENTS_JSON="${GH_COMMENTS_JSON:-$CMT_EMPTY}" \
        PR_URL="https://example.invalid/thoser666/Vivid/pull/1" \
        GH_TOKEN="stub-token" \
        bash --noprofile --norc -eo pipefail -c "$1" 2>"$d/err")"
  RC=$?
  set -e
  ERRTXT="$(cat "$d/err")"
  GHLOG="$(cat "$d/gh.log")"
  GHOUT="$(cat "$d/out")"
}

seen() { printf '%s\n%s\n%s' "$OUT" "$ERRTXT" "$GHOUT" | grep -qF -- "$1"; }
comment_calls() { printf '%s\n' "$GHLOG" | grep -c '^pr comment' || true; }

# ── Verhaltensszenarien (Rueckgabe 0 = Erwartung erfuellt) ──────────────────

# B2 — Negativkontrolle: alles gruen, kein Kommentar, keine Sonderbehandlung.
sc_auto_ok() {
  GH_MERGE_ERR=""; GH_MERGE_RC=0; GH_COMMENTS_JSON="$CMT_EMPTY"
  snip_run "$1"
  [ "$RC" -eq 0 ] || return 1
  seen 'merge_action=auto-merge' || return 1
  seen 'Auto-Merge aktiviert' || return 1
  [ "$(comment_calls)" -eq 0 ] || return 1
}

# B1 — der eigentliche Defekt: Verweigerung wegen fehlendem `workflows`-Scope
# soll GRUEN enden (erwarteter Zustand), mit Warnung, korrekter Merge-Aktion
# und genau EINEM Hinweiskommentar am PR.
sc_manual_required() {
  GH_MERGE_ERR="$ERR_WF"; GH_MERGE_RC=1; GH_COMMENTS_JSON="$CMT_EMPTY"
  snip_run "$1"
  [ "$RC" -eq 0 ] || return 1
  seen 'merge_action=manual-required' || return 1
  seen '::warning' || return 1
  seen 'manuell mergen' || return 1
  if seen 'keine Branch-Protection'; then return 1; fi
  [ "$(comment_calls)" -eq 1 ] || return 1
  # Der KOMMENTAR muss den Marker tragen — nur dann greift spaeter die
  # Idempotenz (S5) auf denselben String.
  printf '%s\n' "$GHLOG" | grep '^pr comment' \
    | grep -qF -- 'dependabot-auto-merge: manual-merge-required' || return 1
}

# B1b — derselbe Zustand am zweiten Einstiegspunkt der API (enable-Pfad).
sc_manual_required_enable() {
  GH_MERGE_ERR="$ERR_EN"; GH_MERGE_RC=1; GH_COMMENTS_JSON="$CMT_EMPTY"
  snip_run "$1"
  [ "$RC" -eq 0 ] || return 1
  seen 'merge_action=manual-required' || return 1
  if seen 'keine Branch-Protection'; then return 1; fi
  [ "$(comment_calls)" -eq 1 ] || return 1
}

# B3 — Auto-Merge-Setting aus: Konfigurationsfehler, hart rot, aber mit dem
# ECHTEN Text statt der alten Fehldiagnose.
sc_disabled() {
  GH_MERGE_ERR="$ERR_DIS"; GH_MERGE_RC=1; GH_COMMENTS_JSON="$CMT_EMPTY"
  snip_run "$1"
  [ "$RC" -eq 1 ] || return 1
  seen 'merge_action=disabled' || return 1
  seen 'Auto merge is not allowed' || return 1
  if seen 'keine Branch-Protection'; then return 1; fi
}

# B4 — unbekannter Grund: hart rot UND der Fehlertext aus stderr steht im
# Log. Genau das leistete das alte `2>/dev/null` nicht.
sc_unknown_surfaces() {
  GH_MERGE_ERR="$ERR_UNK"; GH_MERGE_RC=1; GH_COMMENTS_JSON="$CMT_EMPTY"
  snip_run "$1"
  [ "$RC" -eq 1 ] || return 1
  seen 'merge_action=failed' || return 1
  seen 'Required status check' || return 1
}

# B5 — Idempotenz: der Hinweiskommentar existiert bereits, kein zweiter.
sc_idempotent() {
  GH_MERGE_ERR="$ERR_WF"; GH_MERGE_RC=1; GH_COMMENTS_JSON="$CMT_MARKED"
  snip_run "$1"
  [ "$RC" -eq 0 ] || return 1
  seen 'merge_action=manual-required' || return 1
  [ "$(comment_calls)" -eq 0 ] || return 1
}

# Statisch auf dem Code (Kommentarzeilen raus — Prosa darf Altes zitieren,
# der Code darf es nicht mehr tun).
sc_static() {
  local code
  code="$(printf '%s\n' "$1" | grep -v '^[[:space:]]*#' || true)"
  printf '%s' "$code" | grep -qF 'gh pr merge --auto --squash "$PR_URL"' || return 1
  if printf '%s' "$code" | grep -qF '2>/dev/null'; then return 1; fi
  if printf '%s' "$code" | grep -qF 'gh pr merge --squash'; then return 1; fi
  if printf '%s' "$code" | grep -qF 'Auto-Merge nicht verfügbar'; then return 1; fi
  printf '%s' "$code" | grep -qF 'without `workflows` permission' || return 1
  printf '%s' "$code" | grep -qF 'Auto merge is not allowed' || return 1
}

# ── Erwartungs-Helfer: Szenario gegen Snippet, mit Soll-Zustand ─────────────
expect() { # $1=Name $2=Szenario-Funktion $3=Snippet $4=pass|fail [$5=ran]
  local res
  if "$2" "$3"; then res=pass; else res=fail; fi
  if [ "$res" = "$4" ]; then ok
  else bad "$1: erwartet '$4', erhalten '$res'"; fi
  # Bei Mutationen zaehlt nur ein FEHLSCHLAG, der aus der Aussage kommt: ein
  # Snippet, das gar nichts schreibt (Syntax-/Ausfuehrungsfehler), wuerde jeden
  # Erwartungswert "fail" erfuellen und damit nichts belegen.
  if [ "${5:-}" = "ran" ] && [ -z "$GHOUT" ]; then
    bad "$1: mutiertes Snippet hat nichts geschrieben (Absturz?) — der Mutationsnachweis waere damit wertlos"
  fi
}

mutate() { # $1=Snippet $2=alt $3=neu — versagt hart, wenn das Muster fehlt
  local s="$1"
  case "$s" in
    *"$2"*) ;;
    *) echo "Mutation nicht anwendbar (Muster fehlt im Snippet): $2" >&2; return 2 ;;
  esac
  printf '%s' "${s/"$2"/"$3"}"
}

# ── Snippet ziehen (CRLF aus dem Arbeitsbaum entfernen: sonst bricht bash) ──
SNIPPET="$(wf_step_run "$WF" auto-merge 'Enable auto-merge')"
if [ "$SNIPPET" = "-" ] || [ -z "$SNIPPET" ]; then
  echo "❌ [auto-merge-test] run-Snippet des Steps 'Enable auto-merge' nicht lesbar." >&2
  exit 1
fi
SNIPPET="${SNIPPET//$'\r'/}"

# ── Szenarien gegen den echten Code ─────────────────────────────────────────
expect "S1 Verweigerung endet gruen mit Kommentar" sc_manual_required "$SNIPPET" pass
expect "S1b Verweigerung (enable-Pfad) endet gruen" sc_manual_required_enable "$SNIPPET" pass
expect "S2 Auto-Merge ohne Fehler" sc_auto_ok "$SNIPPET" pass
expect "S3 Setting aus -> hart rot mit echtem Text" sc_disabled "$SNIPPET" pass
expect "S4 Unbekannter Fehler -> Text sichtbar" sc_unknown_surfaces "$SNIPPET" pass
expect "S5 Kommentar-idempotenz" sc_idempotent "$SNIPPET" pass
expect "S6 Code hat weder Verdeckung noch direkten Nachschub" sc_static "$SNIPPET" pass

# ── Mutationsnachweise: die Assertions MUESSEN bei gebrochenem Code fehlschlagen
M1="$(mutate "$SNIPPET" '2>"$ERR"' '2>/dev/null')"
expect "M1 stderr-Verdeckung wird erkannt" sc_unknown_surfaces "$M1" fail ran
# Mechanismus statt bloss "irgendetwas schlaegt fehl": M1 muss in den
# else-Zweig abrutschen — genau die Verdeckung, die hier abgeschafft wurde.
grep -qF 'merge_action=failed' <<<"$GHOUT" \
  && ok || bad "M1 landete nicht im else-Zweig (GHOUT: ${GHOUT:-leer})"
M2="$(mutate "$SNIPPET" 'without `workflows` permission' 'without workflowzzz permission')"
expect "M2 verlorene Klassifizierung wird erkannt" sc_manual_required "$M2" fail ran
grep -qF 'merge_action=failed' <<<"$GHOUT" \
  && ok || bad "M2 landete nicht im else-Zweig (GHOUT: ${GHOUT:-leer})"
M3="$(mutate "$SNIPPET" 'if [ "$have" != "true" ]; then' 'if true; then')"
expect "M3 fehlende Idempotenz wird erkannt" sc_idempotent "$M3" fail ran
[ "$(comment_calls)" -eq 1 ] \
  && ok || bad "M3 musste genau einen Kommentar erzwingen, waren: $(comment_calls)"
M4="$(mutate "$SNIPPET" 'elif grep -qF '"'"'Auto merge is not allowed'"'"' "$ERR"' 'elif grep -qF '"'"'Auto merge is not allowedzzz'"'"' "$ERR"')"
expect "M4 verwechselte Fehlerklasse wird erkannt" sc_disabled "$M4" fail ran
grep -qF 'merge_action=failed' <<<"$GHOUT" \
  && ok || bad "M4 landete nicht im else-Zweig (GHOUT: ${GHOUT:-leer})"

# ── Struktur (fensterfrei, exakte Werte aus dem YAML) ───────────────────────
got="$(wf_job_permissions "$WF" auto-merge)"
if [ "$got" == "contents=write;pull-requests=write" ]; then ok
else bad "Job-Permissions nicht exakt contents=write;pull-requests=write (ist: $got)"; fi

got="$(wf_step_if "$WF" auto-merge 'Enable auto-merge')"
if [ "$got" == "steps.check.outputs.auto_merge == 'true'" ]; then ok
else bad "Merge-Step muss an steps.check.outputs.auto_merge haengen (ist: $got)"; fi

if grep -q 'id: merge' "$WF"; then ok
else bad "Step braucht 'id: merge' — der Summary-Step liest steps.merge.outputs"; fi

if grep -q 'steps.merge.outputs.merge_action' "$WF"; then ok
else bad "Summary-Step muss das Ergebnis (steps.merge.outputs.merge_action) ausweisen"; fi

if grep -q 'GH_MERGE' "$WF"; then
  bad "Testvariable GH_MERGE im Produktiv-Workflow — der Stub-Datensatz gehoert ins Testskript"
else ok; fi

printf '✅ [dependabot-auto-merge] %d Pass, %d Fail (6 Verhaltensszenarien, 4 Mutationen, 5 Struktur-Checks) — Beleg-Runs: 38247604436 (Defekt), 38247767474 (Gegenprobe #291)\n' \
  "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
