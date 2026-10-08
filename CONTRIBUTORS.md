# Contributors / Danksagung

> **English summary:** This file credits **third-party contributors** (people
> outside the core maintainer team) and what they implemented — following the
> acknowledgment practice of projects like OBS Studio. One table only, columns
> `Person | Beitrag | Referenz | Status`, where `Referenz` is a GitHub
> issue/PR number (`#123`) and `Status` is `offen` (announced, not merged yet)
> or `umgesetzt` (merged/released). A maintainer adds the row on acceptance; the
> structure is enforced by `scripts/check_contributors.sh` (pre-push gate + CI).

Hier werden **Dritte** (Personen außerhalb des Kern-Teams) mit ihren Beiträgen
gewürdigt — angelehnt an die Danksagungs-Praxis von Projekten wie OBS Studio.
Beim Annehmen eines Beitrags (merged PR bzw. angenommenes Feature-Request)
ergänzt ein Maintainer die Zeile; `offen` → `umgesetzt`, sobald die Arbeit
gemergt ist. Erlaubte Werte und Struktur sind in `CONTRIBUTING.md` dokumentiert.

| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka (Ilya K) | In-App-Sprachauswahl + russische Lokalisierung (Appearance-Einstellungen, `values-ru`) | #213 | umgesetzt |
| smka (Ilya K) | Kamera-Vorschau wiederhergestellt + seitenverhältnistreu (Idle-Camera2-Preview vor dem Stream, Buffer/View auf unterstützte Ausgabe-Größen gematcht, Guards gegen Kamera-Konkurrenz) | #230 | umgesetzt |
| smka (Ilya K) | Main-Screen refaktoriert (Controls-Menü statt überlappender App-Bar, Scenes im Bottom-Sheet) + Kamera-Controls an den offenen Camera2-Manager, lokale MP4-Aufnahme ohne Stream, Idle-Preview durch GL (Filter/LUT/Boost), LUT-Indexierung korrigiert + trilineare Interpolation | #253 | umgesetzt |
| smka (Ilya K) | Streaming-Bitrate an RootEncoder-Grenze (App-Ökosystem in kbit/s, Encoder-Flächen in bit/s, Fehler-Alignment + Safe-Top-Off) + einheitlicher Stream-Start und Wiederherstellung des Encoder-States nach Fehlerfällen | #276 | umgesetzt |
