#!/usr/bin/env bash
# CONTRIBUTORS.md-Erinnerung: Fork-PR-Merges auf develop finden und melden,
# solange der Beitragseintrag fehlt oder nach dem Merge noch `offen` ist
# (Vorfall 29.09.2026: #213 stand nach dem Merge weiter als `offen` in
# CONTRIBUTORS.md, #230 fehlte ganz — beides fiel erst durch eine
# Nutzer-Rueckfrage auf).
#
# Vertrag (siehe CONTRIBUTING.md, Abschnitt "Danksagung Dritter"):
#   - Trigger: jeder Push auf develop (+ workflow_dispatch) — ein Fork-Squash-
#     Merge erzeugt einen develop-Push, der Check laeuft also automatisch.
#   - State-Fenster: "seit dem letzten Reminder-Kommentar". Der Kommentar
#     traegt am Ende `<!-- contributors-state: <ISO-8601> -->` (atomar im
#     selben API-Call), damit nichts doppelt gemeldet wird. Ohne Issue:
#     Vollscan ab Epoche — idempotent, weil gepflegte Eintraege nicht melden.
#   - Dedup: existiert eine OFFENE Reminder-Issue, wird kommentiert, sonst
#     eine neue eroeffnet; geschlossene Issues liefern nur noch den State.
#   - Fork-Erkennung ueber die Pulls-API: `head.repo == null` (Fork nach dem
#     Merge geloescht) oder `head.repo.fork == true`. Same-repo-Branches
#     (Maintainer, Dependabot) sind damit ausgeschlossen.
#   - Bewusst KEIN Bot-PR (kein Branch-Push, kein PR-Create): der T1-T13-
#     Vertrag aus test_bot_pr_credentials.sh gilt hier nicht — `issues:
#     write` genuegt; GITHUB_TOKEN fliesst nur in Issue-Text (Daten, kein Code).
#   - Die Pflege selbst bleibt Maintainer-Handarbeit; die Automatisierung
#     erinnert nur. Fail-soft: keine Fork-Merges im Fenster = gruener No-Op.
#   - Nutzt nur `gh api` (mit gh-embedded gojq via --jq) — keine System-jq-
#     Abhaengigkeit, keine gh-Flags jenseits der Standard-API-Flags.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="${GITHUB_REPOSITORY:-thoser666/Vivid}"
MARKER='<!-- contributors-reminder -->'
STATE_TOKEN='contributors-state:'
EPOCH='1970-01-01T00:00:00Z'
CONTRIBUTORS_FILE="${CONTRIBUTORS_FILE:-CONTRIBUTORS.md}"
PER_PAGE=100
MAX_PAGES=10   # Sicherheitsnetz gegen Endlos-Blaettern (Repo-Skala: ~20 PRs/Tag)

command -v gh >/dev/null 2>&1 || { echo "::error::gh CLI nicht gefunden"; exit 1; }
[[ -s "$CONTRIBUTORS_FILE" ]] || { echo "::error::$CONTRIBUTORS_FILE fehlt oder ist leer"; exit 1; }

api() { gh api "$@"; }

# ── 1. Reminder-Issues (Marker im Body, echte Issues ohne PR-Anhaengsel) ──
# Jüngste Issue mit Marker = State-Quelle (auch geschlossen); jüngste OFFENE
# mit Marker = Kommentarziel.
LATEST_ANY=$(api "repos/$REPO/issues?state=all&sort=created&direction=desc&per_page=50" \
  --jq "[.[] | select(.pull_request == null and .body != null and (.body | contains(\"$MARKER\")))][0] // empty | .number" 2>/dev/null || true)
LATEST_OPEN=$(api "repos/$REPO/issues?state=open&sort=created&direction=desc&per_page=50" \
  --jq "[.[] | select(.pull_request == null and .body != null and (.body | contains(\"$MARKER\")))][0] // empty | .number" 2>/dev/null || true)

SINCE="$EPOCH"
if [[ -n "$LATEST_ANY" ]]; then
  CREATED=$(api "repos/$REPO/issues/$LATEST_ANY" --jq .created_at)
  LAST_STATE=$(api "repos/$REPO/issues/$LATEST_ANY/comments?per_page=100" \
    --jq '([.[] | select(.body != null and (.body | contains("'"$STATE_TOKEN"'"))) ] | last | .body) // empty' 2>/dev/null \
    | sed -n "s/.*$STATE_TOKEN \([0-9T:.Z-]*\).*/\1/p" | tail -1 || true)
  # Neuerer von Issue-Erstellung und State-Kommentar gewinnt; nur validierte
  # ISO-Zeitstempel werden eingebettet (kein Daten-in-Code-Vektor).
  CAND="$CREATED"
  [[ "$LAST_STATE" =~ ^[0-9T:.Z-]+$ ]] && CAND="$LAST_STATE"
  if [[ "$CAND" > "$SINCE" ]]; then SINCE="$CAND"; fi
fi

# ── 2. Fork-PR-Merges im Fenster einsammeln (sort=updated, Seitenschleife) ──
MERGED_ROWS=()   # "nummer|merged_at|url|sha|title"
PAGE=1
while :; do
  RESP=$(api "repos/$REPO/pulls?state=closed&sort=updated&direction=desc&per_page=$PER_PAGE&page=$PAGE" \
    --jq ".[] | select(.merged_at != null and .merged_at > \"$SINCE\" and ((.head.repo == null) or (.head.repo.fork == true))) | [.number, .merged_at, .html_url, (.merge_commit_sha // \"-\"), .title] | @tsv" 2>/dev/null || true)
  COUNT=$(printf '%s' "$RESP" | grep -c . || true)
  if [[ "$COUNT" -gt 0 ]]; then
    while IFS=$'\t' read -r num mat url sha title; do
      [[ -z "$num" ]] && continue
      MERGED_ROWS+=("$num|$mat|$url|$sha|$title")
    done <<< "$RESP"
  fi
  # Abbruch: aeltester Eintrag der Seite liegt vor dem State-Fenster
  # (sort=updated ⇒ alle weiteren Seiten sind noch aelter) oder die Seite
  # ist nicht voll (Ende der Liste) oder Seitenlimit erreicht.
  OLDEST_UPDATED=$(api "repos/$REPO/pulls?state=closed&sort=updated&direction=desc&per_page=$PER_PAGE&page=$PAGE" \
    --jq 'last | .updated_at // empty' 2>/dev/null || true)
  [[ -z "$OLDEST_UPDATED" ]] && break
  [[ "$OLDEST_UPDATED" < "$SINCE" ]] && break
  [[ "$COUNT" -lt "$PER_PAGE" ]] && break
  [[ "$PAGE" -ge "$MAX_PAGES" ]] && break
  PAGE=$((PAGE + 1))
done

# ── 3. Pflege-Status je Fork-Merge ermitteln ──
#  fehlt    : keine Zeile mit `|#<Nummer> |` in CONTRIBUTORS.md
#  offen    : Zeile vorhanden, Status aber noch `offen` (Merge schon durch)
#  umgesetzt: Zeile vorhanden und gepflegt (Zielzustand)
#  unlesbar : Zeile vorhanden, aber letzte Zelle nicht auswertbar → melden
status_for() { # $1 = PR-Nummer
  local row status
  row=$(grep -E "^[[:space:]]*[|].*[|] *#$1 *[|]" "$CONTRIBUTORS_FILE" | tail -1 || true)
  if [[ -z "$row" ]]; then
    echo "fehlt"
    return
  fi
  status=$(printf '%s' "$row" | tr -d '\r' | awk -F'|' \
    '{for (i = NF; i >= 1; i--) if ($i ~ /[^ \t]/) {gsub(/^[ \t]+|[ \t]+$/, "", $i); print $i; exit}}')
  [[ -z "$status" ]] && status="unlesbar"
  echo "$status"
}

REPORT_ROWS=()
SUMMARY_ROWS=()
for entry in "${MERGED_ROWS[@]}"; do
  IFS='|' read -r num mat url sha title <<< "$entry"
  status=$(status_for "$num")
  esc_title=${title//|/\\|}   # Pipe-Zeichen escapen, sonst bricht die Tabelle
  short_sha=${sha:0:7}
  SUMMARY_ROWS+=("| #$num | [PR öffnen]($url) | $mat | $status |")
  if [[ "$status" != "umgesetzt" ]]; then
    if [[ "$status" == "fehlt" ]]; then
      label="**fehlt** in CONTRIBUTORS.md"
    elif [[ "$status" == "offen" ]]; then
      label="**noch \`offen\`, aber bereits gemergt**"
    else
      label="**Statuszelle unlesbar** (\`$status\`)"
    fi
    REPORT_ROWS+=("- [ ] **#$num** — [$esc_title]($url) (Squash-Merge \`$short_sha\`, $mat) — Status: $label")
  fi
done

# ── 4. Melden: Kommentar an offene Reminder-Issue, sonst neue Issue ──
ACTION="keine"
if [[ "${#REPORT_ROWS[@]}" -gt 0 ]]; then
  NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  BODY_FILE=$(mktemp)
  trap 'rm -f "$BODY_FILE"' EXIT
  {
    echo "$MARKER"
    echo ""
    echo "## 🧾 Fork-PR-Merges ohne sauberen Beitragseintrag"
    echo ""
    echo "Fenster: Merge(s) seit **$SINCE** — erkannt durch \`.github/workflows/automation-contributors-reminder.yml\`."
    echo ""
    for row in "${REPORT_ROWS[@]}"; do echo "$row"; done
    echo ""
    echo "**Handlung (Maintainer, nach [CONTRIBUTING.md](CONTRIBUTING.md) „Danksagung Dritter“):**"
    echo ""
    echo "1. Zeile in \`CONTRIBUTORS.md\` ergänzen bzw. \`offen\` → \`umgesetzt\` flippen (Spalten: \`Person | Beitrag | Referenz | Status\`)."
    echo "2. Guard lokal prüfen: \`bash scripts/check_contributors.sh\`."
    echo "3. Commit nach Hausmuster (CONTRIBUTORS.md → PARITY-Append → Dashboard → Gate → Push), Issue danach schließen."
    echo ""
    echo "Die Pflege bleibt Maintainer-Handarbeit — dieser Reminder prüft nur das Nachziehen."
    echo ""
    echo "<!-- $STATE_TOKEN $NOW -->"
  } > "$BODY_FILE"

  if [[ -n "$LATEST_OPEN" ]]; then
    api -X POST "repos/$REPO/issues/$LATEST_OPEN/comments" -F "body=@$BODY_FILE" >/dev/null
    ACTION="Kommentar an Reminder-Issue #$LATEST_OPEN"
  else
    TITLE="🧾 CONTRIBUTORS.md-Pflege: Fork-PR-Merges ohne Beitragseintrag"
    NEW_NUM=$(api "repos/$REPO/issues" -f title="$TITLE" -F "body=@$BODY_FILE" --jq .number)
    ACTION="Reminder-Issue #$NEW_NUM erstellt"
  fi
fi

# ── 5. Run-Summary (immer) ──
# GITHUB_STEP_SUMMARY ist nur in der CI gesetzt — lokal/ohne Env wird nach
# /dev/null gespiegelt (Git-Bash kennt kein appendbares /dev/stdout).
SUMMARY_TARGET="${GITHUB_STEP_SUMMARY:-}"
{
  echo "## 🧾 Contributors-Reminder"
  echo ""
  if [[ "${#MERGED_ROWS[@]}" -eq 0 ]]; then
    echo "Keine Fork-PR-Merges seit **$SINCE** — nichts zu tun."
  else
    echo "| PR | Merge | Zeitpunkt | Beitragseintrag |"
    echo "|----|-------|-----------|-----------------|"
    for row in "${SUMMARY_ROWS[@]}"; do echo "$row"; done
    echo ""
    echo "- Meldungen: **${#REPORT_ROWS[@]}** — $ACTION"
  fi
} >> "${SUMMARY_TARGET:-/dev/null}"
echo "Contributors-Reminder: $ACTION (Fenster: $SINCE, Fork-Merges im Fenster: ${#MERGED_ROWS[@]}, Meldungen: ${#REPORT_ROWS[@]})"

exit 0
