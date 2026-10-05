#!/usr/bin/env bash
# Regressionstest: Issue-Hausmuster „Zuweisung erst beim Beginnen der Arbeit"
# (#260). Vorher galt „neue Issues werden beim Anlegen dem Maintainer
# zugewiesen" — das hat jede Selbstbedienung blockiert, weil jedes Issue ab
# Geburt reserviert war.
#
# Der Test bewacht zwei Dinge:
#   1. Die neue Regel steht (AGENTS.md, docs/sentry-issues.md §5, CONTRIBUTING).
#   2. Die alte Regel nicht wiederkehrt. Das ist der eigentliche Zweck — eine
#      Regel, die nur durch Erinnern in einer Doku Datei existiert, fällt beim
#      nächsten Refactoring still auf den vorherigen Stand zurück.
#
# Läuft im Pre-Push-Gate: bash scripts/test_issue_assignment_rule.sh (Exit 0 = grün)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

echo "▶ [test_issue_assignment_rule] Issue-Zuweisung erst beim Beginnen der Arbeit (#260)"

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

# I1: AGENTS.md existiert überhaupt. Ohne diese Datei greift die Regel in
# Coding-Agent-Sitzungen nicht — sie ist der eigentliche Träger.
if [ -s AGENTS.md ]; then
  echo "  ✅ I1 AGENTS.md vorhanden (wird von Coding-Agents automatisch geladen)"
else
  echo "  ❌ I1 AGENTS.md fehlt — die Hausregel erreicht keine Agent-Sitzung"
  FAILED=1
fi

# I2: Die neue Regel steht in AGENTS.md, an beiden Ankerpunkten: kein
# --assignee beim Anlegen, --add-assignee beim Beginnen.
check "I2.1 AGENTS.md: Anlegen ohne --assignee" AGENTS.md 'gh issue create .*--body-file'
notcheck "I2.2 AGENTS.md: kein --assignee beim issue create" AGENTS.md 'gh issue create[^|]*--assignee'
check "I2.3 AGENTS.md: Zuweisung über --add-assignee beim Beginnen" AGENTS.md 'gh issue edit <nr> --add-assignee thoser666'
check "I2.4 AGENTS.md: unzugewiesen == frei" AGENTS.md 'frei'

# I3: docs/sentry-issues.md §5 nennt die neue Regel …
check "I3.1 sentry-issues.md: Regel 'Zuweisen erst beim Beginnen'" docs/sentry-issues.md 'Zuweisen erst beim Beginnen der Arbeit'
check "I3.2 sentry-issues.md: --add-assignee beim Beginnen" docs/sentry-issues.md 'gh issue edit <nr> --add-assignee thoser666'
# I4: … und die alte Regel ist wirklich weg. Die abgeschaffte Klausel
# wertete ein offenes unzugewiesenes Issue als Mangel — genau das Gegenteil.
notcheck "I4.1 sentry-issues.md: abgeschaffte Klausel entfernt" docs/sentry-issues.md 'unzugewiesenes Issue gilt als nicht abgeschlossen'
notcheck "I4.2 sentry-issues.md: keine Zuweisung mehr beim Anlegen" docs/sentry-issues.md 'issue create[^|]*--assignee thoser666'
notcheck "I4.3 release-notes: kein Verweis auf die alte Regel" docs/release-notes-v0.5.20-beta.md 'beim Anlegen dem Maintainer zugewiesen'

# I5: Die Release-Notes spiegeln die neue Regel.
check "I5 release-notes nennt die Selbstbedienung" docs/release-notes-v0.5.20-beta.md 'starten .*unzugewiesen'

# I6: Contributor-Seite: Anleitung zum Selbstnehmen muss existieren.
check "I6.1 CONTRIBUTING: Abschnitt zum Übernehmen" CONTRIBUTING.md '^### Ein offenes Issue übernehmen'
check "I6.2 CONTRIBUTING: --add-assignee-Befehl" CONTRIBUTING.md 'gh issue edit <nr> --add-assignee <dein-name>'
check "I6.3 CONTRIBUTING: Freigabe erlaubt" CONTRIBUTING.md 'remove-assignee'

# I7: Konsistenz mit den Issue-Templates. Die Web-Formulare waren schon immer
# unzugewiesen (`assignees: ''`) — sie dürfen nicht still auf den Maintainer
# umgestellt werden, sonst widerspricht die Self-Serve-Aussage der Realität.
for tpl in .github/ISSUE_TEMPLATE/bug_report.md .github/ISSUE_TEMPLATE/feature_request.md; do
  check "I7 $(basename "$tpl"): Template bleibt unzugewiesen" "$tpl" "^assignees: *''"
done

# I8: Kein Skript im Repo darf die alte Regel ausführen. Der Watchdog und die
# Automationen legen Issues an — ein `--assignee` dort würde die Regel kippen.
if grep -rnE 'gh issue create.*--assignee' scripts/ .github/workflows/ 2>/dev/null | grep -v test_issue_assignment_rule; then
  echo "  ❌ I8 ein Skript vergibt den Assignee noch beim Anlegen (siehe oben)"
  FAILED=1
else
  echo "  ✅ I8 kein Skript vergibt den Assignee beim Anlegen"
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ Alle Checks grün — Issues starten unzugewiesen und werden erst beim Beginnen übernommen."
  exit 0
fi
echo "❌ Mindestens ein Check fehlgeschlagen — siehe oben."
exit 1