# AGENTS.md — Hausregeln für Coding-Agents

Diese Datei wird von Coding-Agents automatisch geladen. Die ausführlichen
Begründungen stehen in `CONTRIBUTING.md` und den Docs; hier steht nur, was
eine Sitzung wissen muss, bevor sie etwas anfasst.

## Issues: erst zuweisen, wenn die Arbeit beginnt

**Neue Issues werden unzugewiesen angelegt.** Es wird **kein**
`--assignee` beim `gh issue create` mitgegeben.

Ein offenes Issue ohne Assignee ist das Signal „frei“ — genau so können
sich Contributors offene Issues heraussuchen. Wer ein Issue wirklich
anfängt, übernimmt es:

```bash
gh issue create --title "…" --body-file …      # Assignee bleibt leer
gh issue edit <nr> --add-assignee thoser666    # erst beim Beginnen der Arbeit
```

Erledigt = **geschlossen**. Ein offenes, unzugewiesenes Issue ist kein
Mangel und wird nicht nachassigniert.

Das ist eine bewusste Umkehrung der früheren Regel („neue Issues immer
schon beim Anlegen zuweisen“, Hausmuster aus #215/#225): die Vorabreservierung
hatte jede Selbstbedienung blockiert, ohne einen zusätzlichen Nutzen zu
bringen. Quelle: `docs/sentry-issues.md` §5.

## Umgang mit offenen Issues

- Offene Issues **durchlesen**, bevor ein neues angelegt wird — es kann
  bereits einen Vorgänger geben (Dedupe gegen `state=all`).
- Befunde mit belegter Ursache als Kommentar an das bestehende Issue hängen,
  nicht parallel daneben anlegen.
- Ein Issue abschließen: Befund-Nachweis als Kommentar, dann
  `gh issue close <nr> --reason completed`.
- Die Behauptung eines Tickets vor der Umsetzung prüfen. Mehrfach kam es vor,
  dass die Diagnose im Ticket falsch war und ein Fix gegen eine nicht
  existierende Fehlfunktion gebaut worden wäre (#259) — die Aussage gegen die
  echten Daten fahren, dann handeln.

## Arbeitsweise

- **Hausmuster schlägt Einzelfallentscheidung.** Vor dem ersten Edit
  `CONTRIBUTING.md` und das passende `docs/*.md` lesen.
- **Keine Zeilenfenster in Guards** (`grep -A N`, `head -N` als Assertion,
  `sed -n 'N,Mp'`, `awk 'NR==N'`). Struktur-Fakten über
  `scripts/lib_workflow_yaml.sh` (`wf_*`) ausdrücken — vgl.
  `scripts/test_no_line_windows.sh`.
- **Kein Tippfehler im Guard darf wie ein bestandener Check aussehen.**
  Unbekannter Pfad/Job/Key liefert `-`, und jeder in Guards benutzte `wf_*`-Name
  wird gegen die Library-Definition geprüft.
- **Reihenfolge/Aussagen verhaltensbasiert prüfen, wenn Logik im Spiel ist.**
  Für Shell-Logik in einem `run:`-Block: Snippet extrahieren (`wf_step_run`),
  mit Stubs ausführen, Ergebnis kontrollieren.
- **Mutationen prüfen.** Ein neuer Guard, der nie rot wird, beweist nichts.
  Die Änderung gezielt zurückbauen und den roten Test zeigen — plus eine
  Negativkontrolle, die grün bleiben muss.
- **Vor dem Push** `scripts/pre-push.sh` (läuft als Hook automatisch mit).
  Dauer ~10–15 min, im Hintergrund starten und pollen.
- **`git push` nicht erzwingen**; Branch Protection auf `develop` verlangt PRs
  für Force-Push, normale Pushes laufen durch.

## Chaos nicht dem Zufall überlassen

Wenn im Repo etwas Ungeklärtes liegt (fremde Modifikation im Arbeitsbaum,
uneingeschränktes `grep`-Fenster, toter Helfer), benennen und nicht
stillschweigend mitnehmen. Beim Push „alles“ kommt auch fremdes Material mit —
vorher die Entscheidung einholen.

## Doku

- `PARITY.md`: nach dem Code-Commit eine `docs(parity):`-Zeile mit dem
  **7-stelligen** Hash (`git rev-parse --short=7`), angehängt als erste Zeile
  in `## 🔄 Aktualisierungslog` plus eine Zeile in
  `## 🔍 Zuordnung der Log-Commit-Hashes`. Die Datei ist **LF**.
- `.freebuff/vivid-status.html` wird per
  `bash scripts/generate_status_dashboard.sh` regeneriert und mit `-f`
  gestaged; nach dem Commit mit `git checkout --` zurücksetzen.