# 📦 Distribution & Vertriebskanäle

Wie Vivid gebaut und verteilt wird: Kanäle, Kadenzen, Signierung, Reproduzierbarkeit und die
Einreichungswege für F-Droid-Repositorien. Die **Strategie** (Play vs. F-Droid vs. IzzyOnDroid,
Entscheidungen und Roadmap) steht in [RELEASE.md](../RELEASE.md) → „Vertriebskanäle“ — dieses
Dokument beschreibt den **technischen Ablauf** dahinter.

## Überblick: Kanäle

| Kanal | Wann | Arbeitsschritte im Workflow | Artefakte |
|---|---|---|---|
| **🌙 Nightly** | täglich 06:00 UTC (`schedule`) + manuell | `release-pipeline.yml` → Build + Test + publish | `app-standard-release.apk` + `SHA256SUMS.txt` (standard) + `mapping.txt` + `output-metadata.json` (nur Standard-Flavor; prerelease) |
| **🚀 Stable** | wöchentlich Mo 03:00 UTC + manuell | `distribution-stable.yml` → wählt neuestes noch nicht verteiltes `v*`-Release → Build (Standard **und** foss) + Checksummen + cosign keyless-Signatur + publish | `app-standard-release.apk` + `app-foss-release.apk` + `SHA256SUMS.txt` + `SHA256SUMS.txt.bundle` |
| **🧪 Beta / Versions-Tag** | manuell, bei Bedarf | **Normalfall:** `distribution-stable.yml` mit `-f version=vX.Y.Z-beta` · **Ausweichweg:** `release-pipeline.yml` dispatch auf `refs/tags/vX.Y.Z-beta` → Build (Standard **und** foss) + Checksummen + cosign keyless-Signatur + publish + Reproduzierbarkeits-Gate | wie Stable, **plus** `mapping.txt` + `output-metadata.json` |
| **🛰 F-Droid-Repo** (eigenes) | wöchentlich Mo 04:00 UTC + manuell | `deploy-fdroid.yml` → lädt Stable-APKs, `fdroid update` → GitHub Pages | `repo/index.xml` + `archive/index.xml` |

Das Stable-Release wird also **wöchentlich statt bei jedem Tag-Push** publiziert. Ein neuer
`v*`-Tag baut und testet weiterhin sofort in der Pipeline (`build`/`test`-Jobs), löst aber **kein**
sofortiges Release-Publishing mehr aus — GitHub-Cronjobs kann man nicht pro Quelle unterscheiden,
deshalb gibt es pro Kadenz einen eigenen Workflow. Alles zusätzlich manuell per `workflow_dispatch` auslösbar.

> ⚠️ **Ein `v*-Beta` zu publizieren: welcher Weg?** Beide erzeugen ein Release mit
> denselben Artefakten (beide Flavor-APKs, `SHA256SUMS.txt`, `.bundle`), beide sind
> seit #262 cosign-signiert — es gibt keinen „falschen“ Weg mehr, nur einen
> vorgesehenen:
>
> ```bash
> # Normalfall (empfohlen): nimmt am wöchentlichen Untergrenzen- und
> # Repair-Algorithmus von distribution-stable.yml teil.
> gh workflow run distribution-stable.yml --ref develop --field version=v0.6.0-beta
> ```
>
> Der Ausweichweg `gh workflow run release-pipeline.yml --ref v0.6.0-beta`
> (Tag als Ref) ist dann nötig, wenn der Beta sofort raus soll, ohne auf den
> Montag zu warten. Er war am 02.10.2026 der Auslöser für einen nicht
> konformen Release: Der cosign-Stand fehlte (der Block stand nur in
> distribution-stable.yml), das Reproduzierbarkeits-Gate prüfte ein
> **fremdes** Nightly statt des Beta, und der Beta-Kanal war überhaupt
> erst prüfbar, seit der Fastfile auch `mapping.txt` +
> `output-metadata.json` mitveröffentlicht. Details im nächsten Abschnitt.

> ⚠️ **`release-pipeline.yml`: Dispatch ≠ Matrix-Verifikation.** Ein `workflow_dispatch`
> ohne weitere Flags ist ein **publizierender** Lauf (Nightly-Release). `dry_run=true`
> bremst ausschließlich den Play-Upload. Für eine reine Emulator-Matrix-Verifikation
> zusätzlich `-f matrix_only=true` setzen — das unterdrückt `publish-release`,
> `verify-reproducibility`, `publish-play` und `sweep-orphan-drafts`, lässt
> `emulator-tests`, Selbsttests und `build-debug` aber laufen. Vorfall 02.10.2026:
> Dispatch `36963108418` hat ohne diesen Input das Nightly `0.5.20-nightly.511`
> veröffentlicht. Contract: T17 in `scripts/test_emulator_matrix.sh`.
>
> Der Input ist `type: boolean` — die Job-Guards vergleichen deshalb gegen das
> Boolean-Literal `true` (`inputs.matrix_only != true`), **nicht** gegen den String
> `'true'`. GitHub-Ausdrücke coercen nicht, ein String-Vergleich ist also still
> wirkungslos und lässt den Guard-Job einfach durchlaufen. T17.4 sichert die
> Kopplung zwischen deklariertem Typ und Vergleichsliteral ab.

## Stable-Distribution (`distribution-stable.yml`)

**Zweck:** Neueste Version, Stand Montag 03:00 UTC, als „Latest“-Release veröffentlichen.

1. **Tag-Auswahl:** Semver-Sortierung aller `v*`-Tags **absteigend**; ein Tag gilt als
   „noch nicht verteilt“, wenn sein GitHub-Release nicht vollständig ist (muss dauerhaft
   4 Assets haben, siehe unten). **Untergrenze (#259):** der **erste vollständig verteilte
   Release beendet die Suche — alles Ältere ist obsolet und wird nie nachgeholt. Vorher
   lief die Schleife (`continue`) durch alle vollständigen Releases hindurch bis zum nächsten
   Loch; real vorhanden sind `v0.5.15-beta`, `v0.5.14` und `v0.5.8-beta` ohne Release,
   die dadurch als stable-Release nachgeholt worden wären. Der Repair-Pfad bleibt: ein
   Release **neuer** als die Untergrenze, dem nur `.bundle` fehlt, wird repariert.
   Bei `workflow_dispatch` kann ein optionaler `version`-Input (Muster `v<major>.<minor>.<patch>`,
   optional mit Stufensuffix) den Kandidaten übersteuern.
2. **Emulator-Gate (seit 24.09.2026, vor dem Build):** Der Publish-Job fährt vor
   `release_github` einen Emulator (API 34, x86_64, KVM) hoch und führt die
   instrumentierten UI-Tests (`:app:connectedStandardDebugAndroidTest` **und**
   `:app:connectedFossDebugAndroidTest` — Startup-Smoke-Test, Help-
   Navigation + Play-Screenshots, jeweils in **beiden** Flavors) **gegen den
   Ziel-Tag** aus (der Job hat ihn bereits ausgecheckt). Schlägt der
   Emulator-Test fehl, wird **nicht** veröffentlicht. Derselbe Gate gilt im
`release-pipeline.yml`: `emulator-tests` (API-Staffelung seit
    25.09.2026: ubuntu-x86_64 **API 34 + API 35 Pflicht**, API-36-Beobachter
    + macos-arm64 experimentell — die Beobachter-Leg stand ursprünglich auf 37
    und wurde am 02.10.2026 auf 36 gezogen, weil der Emulator-Runner
    `platforms;android-37` im Stable-SDK-Kanal des Runners nicht provisionieren
    kann: die Leg wäre dauerhaft rot **ohne Testaussage** gewesen) läuft jetzt
    auch bei `v*`-Tag-Pushen, nicht mehr nur manuell. Selbsttest:
   `scripts/test_emulator_matrix.sh` (T11/T12/T13/T14/T16).
   **Grant-Setup im Gate (seit 01.10.2026, #249):** Beide Gate-Wege
   (`release-pipeline.yml` Tag-Push und `distribution-stable.yml` Stable-Publish)
   granten die Runtime-Permissions vor den `connected*`-Tasks über
   `scripts/emulator_test_setup.sh` (installiert die Debug-APKs **beider**
   Flavors zuerst — `adb shell pm grant` scheitert an nicht installierten
   Packages, deshalb kann der Grant nicht in den connected-Task wandern).
   **fail-loud:** Der `android-emulator-runner` führt sein `script:`-Snippet
   **ohne** `set -e` aus — beide Workflows setzen deshalb explizit
   `set -eu` als erste Zeile. Ohne das würde ein fehlgeschlagenes
   Setup stillschweigend übersprungen und das Gate fiele mit der
   irreführenden Fehlermeldung `No compose hierarchies found` durch, statt die
   Ursache zu nennen (Vorfall 01.10.2026 — genau diese Verschleierung hat den
   #249-Befund in der ersten Runde schwer diagnostizierbar gemacht).
   **⚠️ POSIX-only, bewusst kein `pipefail`:** Der Runner ruft das Snippet über
   `/usr/bin/sh -c` auf, und auf ubuntu ist `/usr/bin/sh` **dash**. `set -o
   pipefail` bricht dort mit `Illegal option -o pipefail` und Exit-Code 2 ab —
   der Job stirbt in Zeile 1 des Script-Blocks, `emulator_test_setup.sh` läuft
   nie (Vorfall 02.10.2026, Dispatch-Run `36963108418`: alle drei ubuntu-Legs
   rot **nach** erfolgreichem Emulator-Boot in 38 s). Die Blöcke enthalten
   keine Pipes, `set -eu` genügt für fail-loud. Contract: T15.13/T15.14
   (exakter Wortlaut), T15.17 (kein `pipefail` in ausführbaren Zeilen),
   T15.18 (erste ausführbare Zeile real durch `/bin/sh` ausgeführt).
   Selbsttest: `scripts/test_emulator_matrix.sh` (T15).
   **Retry-Härtung (seit 25.09.2026):** Der Gate-Step läuft über
   `scripts/emulator_gate_retry.sh` (BuildRetry-Hausmuster): transiente
   Fehlerklassen (Suite-Fehlschlag, Geräteverlust, Boot-Fehler,
   Dependency-Auflösung) retryen 3× mit linearem Backoff (10 s/20 s);
   deterministische Fehler (Kompilierung) scheitern sofort (Vorrang),
   Unklassifiziertes bleibt fail-closed. Boot-Noise aus grünen Läufen ist
   bewusst kein Muster — Evidenz: Run 36025777687 (rot) vs. 36021918352
   (grün, derselbe Commit). Selbsttest: `scripts/test_emulator_gate_retry.sh`
   (E1–E10).
3. **Build:** `bundle exec fastlane release_github tag:"$TAG"` baut **beide** Flavor:
   `assembleStandardRelease` (bereits aus der Pipeline bekannt) **und** `assembleFossRelease`.
4. **Checksummen:** `fastlane/sha256sums.rb` erzeugt `SHA256SUMS.txt` im GNU-Format
   (`<sha256>  <dateiname>`), deterministisch sortiert nach Basisname. Die Datei ist Bestandteil
   des Releases (per `fastlane`/`gh release upload`).
5. **Completeness-Regel** (in `fastlane/Fastfile` → `publish_release` **und** im jq-Check des
   Workflows): Ein Stable-Release ist **vollständig**, wenn es nicht Draft/Prerelease ist **und**
   alle Assets enthält:
   - `app-standard-release.apk`
   - `app-foss-release.apk`
   - `SHA256SUMS.txt`
   - `SHA256SUMS.txt.bundle` (cosign-Sigstore-Bundle, seit dem Signatur-Update 10.09.2026 Pflicht —
     ein alter Release ohne Bundle wird beim nächsten Stable-Lauf repariert, nicht neu erstellt)
   Ein unvollständiges Release wird gelöscht und neu erstellt (idempotent — ein schon vollständiges
   wird übersprungen); der Fastfile-Pfad (3 Assets) bleibt bewusst konservativ, damit ältere
   Releases nicht mit dem Rebuild-Löschen abgerissen werden — die Signatur-Nachrüstung übernimmt
   der cosign-Step im Workflow.
6. **Signatur:** Nach dem Publish lädt der Workflow die veröffentlichte `SHA256SUMS.txt` herunter
   und signiert sie **keyless** per Sigstore/cosign (ambient OIDC-Token des Runners, `id-token: write`
   im Job; kein längerfristiges Key-Material im Repo). Das Sigstore-Bundle (`SHA256SUMS.txt.bundle`,
   Signatur + Zertifikat + Rekor-Eintrag in einer Datei) wird per `gh release upload --clobber`
   ans Release angehängt —
   idempotent, so bleibt der Repair-Pfad (Signatur fehlt) gefahrlos abspielbar.
6. **Keystore-Härtung:** Der foss-Build signiert mit demselben Release-Key; ohne `KEYSTORE_PATH`
   fällt Gradle auf einen Debug-Build zurück → der Workflow prüft die Keystore-Secrets vor dem Build.
7. **CHANGELOG-Mirror:** Die Release-Notes werden nach dem Publish per automatischem PR in
   `CHANGELOG.md` gespiegelt (AUTOMATION_TOKEN oder GitHub-Token, Rebase-Automation; bei Konflikt
   Rollback auf manuelle Erstellung).

## SHA256SUMS.txt

- **Geltungsbereich:** Stable-Releases tragen die Prüfsummen für **beide** Flavor (standard + foss),
  Nightly-Releases dieselbe GNU-Datei für das **Standard-APK** — beide Kanäle sind damit gegen
  Download-Korruption verifizierbar.
- **Format:** GNU-Checksums, passend zu `sha256sum -c SHA256SUMS.txt` (POSIX-Dateinamen ohne Sonderzeichen).
- **Sortierung:** deterministisch (`sort_by { |name| File.basename(name) }`) — reproduzierbarer Inhalt,
  damit Reproduzierbarkeits-Vergleiche nicht an der Reihenfolge scheitern.
- **Dateimodus:** `File.binwrite` — unter Windows kein CRLF-Umbruch (sonst schlägt `sha256sum -c` fehl).
- **Verifikation** im Selbsttest: `scripts/test_sha256sums.sh` (H1–H5 + Positivkontrolle `sha256sum -c`).
- **Wozu?** Downloads verifizierbar machen (vgl. Opt-in-Check in der App) und den F-Droid/Obtainium-
  Reproduzierbarkeits-Anspruch nachvollziehbar halten.

## Sigstore/cosign (keyless) Signatur der Checksummen

Die `SHA256SUMS.txt` der **Stable-Releases** ist kryptographisch **authentifiziert** — gegen die
Bloßstellung bei GitHub-Kompromittierung: Die Prüfsummen **allein** weisen nur Integrität nach
(die Datei könnte ein Angreifer mit den APK-Summen mitsamt neu signierten APKs austauschen).
Der cosign-Step (Workflow, `cosign-installer` SHA-gepinnt `6f9f177882…`) bindet die Identität
des Publishers ein.

- **Wie:** `cosign sign-blob` mit dem **ambienten OIDC-Token** des Runners (kein langfristiger
  Signatur-Key in Secrets/Repo). Zertifikat und Signatur landen im Sigstore-Transparency-Log
  (Rekor); das Bundle bindet die Fulcio-Identität (Zertifikat) mit ein.
- **Was signiert wird:** exakt die **veröffentlichte** `SHA256SUMS.txt` (im Workflow per
  `gh release download` geholt) — nicht das Build-Artefakt — damit die Signatur bytegenau die
  Datei deckt, die Nutzer herunterladen.
- **Repo-Auflösung:** Der Sign-Step läuft in `$RUNNER_TEMP` (außerhalb des git-Workspaces) und
  setzt deshalb `GH_REPO` — sonst kann `gh` das Repository nicht aus dem Remote ableiten
  (Vorfall Run 35496094329: „failed to run git: fatal: not a git repository“).
- **Version:** cosign ist auf **v3.1.3** gepinnt (`cosign-release`-Input des Installers,
  Migration vom v2.6.5-Pin am 20.09.2026). v3 schreibt bei `sign-blob` standardmäßig ein
  Sigstore-Bundle (Signatur + Zertifikat + Rekor-Eintrag in einer Datei) — genau dieses
  Format ist jetzt das Signatur-Asset; `--output-signature`/`--output-certificate` sind
  deprecated und werden nicht mehr verwendet.
- **Completeness:** `.bundle` (Signatur + Zertifikat) ist Pflicht-Asset (4-Assets-Regel, siehe
  oben); ein Release ohne Bundle gilt als unvollständig und wird beim nächsten Stable-Lauf
  **repariert**.
- **Verifikation** (einmalig `brew install cosign` / `apt install cosign`):

  ```bash
  cd <Download-Ordner>   # SHA256SUMS.txt + .bundle der Stable-Release-Seite herunterladen
  cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt
  # → Verified OK (Signatur + Zertifikat + Rekor-Eintrag aus dem Bundle)
  sha256sum -c SHA256SUMS.txt
  ```

  Das Zertifikat ist an die OIDC-Identität des Workflow gebunden
  (`https://github.com/thoser666/Vivid/.github/workflows/distribution-stable.yml @ refs/tags/v…`),
  die von Fulcio ausgestellt und in Rekor geloggt wird. Nightly-Releases (ephemer,
  werden nach 3 Tagen überschrieben) bleiben unauthentifiziert — dort reicht der Hash-Check.

- **Seit #262 auch im `release-pipeline.yml`-Pfad.** Vorher stand der cosign-Block
  ausschließlich in `distribution-stable.yml`, wodurch **jeder** über `publish-release`
  publizierte Release ohne `.bundle` blieb. Das war nicht nur ein Nightly-Problem:
  Der Job publiziert bei einem Dispatch auf `refs/tags/v…` auch **Version-Tags**, und
  genau darüber lief Run 36987429609, der `v0.5.20-beta` ohne Bundle erzeugte. Beide
  Workflows nutzen denselben gepinnten Installer
  (`sigstore/cosign-installer@6f9f177880…`, `cosign-release: 'v3.1.3'`); der
  Vertrag ist in `scripts/test_verify_reproducibility.sh` (T12) strukturell verankert.
- **Bedingung:** Beide cosign-Steps hängen an `startsWith(github.ref, 'refs/tags/v')`
  und spiegeln damit **exakt** das Fastlane-Kriterium `stable`. Ohne diese Bindung
  wäre der Step auch im Nightly-Lauf aktiv — dort ist der Tag unbekannt, weil er
  erst zur Laufzeit im Fastfile entsteht (`nightly-<UTC-Zeitstempel>`), und
  `${{ github.ref_name }}` wäre der Branch-Name.
- **Nightly bleibt bewusst unsigniert** (siehe oben): sein Tag ist der Workflow-Datei
  nicht bekannt, und das verhindert, dass der Schritt entweder wirkungslos oder
  falsch gebunden ist. Ein Nightly-Bundle gäbe es nur, wenn das Fastfile den Tag
  als Job-Output durchreichte.

## Reproduzierbarkeit des publizierten Releases

Der Job **„Verify Reproducibility (nightly)“** (`release-pipeline.yml`, `needs: publish-release`)
verifiziert das veröffentlichte Release **bitweise**: er lädt das APK, das Mapping und die
Output-Metadaten herunter, baut mit exakt denselben `-PversionName`/`-PversionCode` neu und
vergleicht mit `cmp`. Zusätzlich prüft er die APK-Signatur gegen den Release-Keystore.

Vier Regeln, die beim Entstehen des Jobs falsch waren und seit #262/#263 strukturell
festgeschrieben sind:

1. **Das Ziel-Release wird bestimmt, nicht geraten.** Früher suchte der Job ausschließlich
   `nightly-*`-Prereleases und nahm das neueste aus `gh release list --limit 5`. Bei einem
   Dispatch auf `refs/tags/v…` publiziert `publish-release` aber genau diesen Tag und **kein**
   Nightly — der Job verifizierte daraufhin ein bis zu Stunden altes Nightly eines früheren
   Schedule-Runs und verglich dessen Revision gegen den Tag-Commit. Vorfall Run 36987429609:
   Der Vergleich schlug an, aber **geprüft wurde nie das Beta**. Heute gilt:
   `github.ref_type == 'tag'` → `github.ref_name`, sonst das neueste `nightly-*`.
2. **Die Version kommt aus den Metadaten, nicht aus dem Titel.** Der Release-Titel hat zwei
   Formen (`Vivid nightly (0.5.20-nightly.511)` und `Vivid v0.6.0-beta`); der Klammer-`sed`
   lieferte beim Version-Tag **leere** Werte, der Rebuild wäre mit leeren
   `-P`-Properties gelaufen. Gelesen wird jetzt `versionName`/`versionCode` aus der
   publizierten `output-metadata.json`.
3. **Der Beta-Kanal braucht dafür überhaupt publizierbare Artefakte.** Der
   Version-Tag-Zweig des Fastfiles lud vorher nur `[apk, foss_apk, checksums]` hoch — das
   Mapping und die Metadaten blieben lokal im Runner, obwohl der Nightly-Zweig sie seit jeher
   mitveröffentlicht. Ohne sie ist Reproduzierbarkeit im Beta-Kanal **nicht prüfbar**, weil
   der Job genau diese beiden Dateien lädt.
4. **Die erwartete Revision kommt aus dem Tag, nicht aus dem Workflow-HEAD.** `#263` hat den
   Vergleich gegen `github.sha` abgeschafft. `github.sha` ist der Commit, den der Lauf
   gecheckt hat; der Release entsteht dagegen aus `origin/develop`, das der Publish-Step
   **nach** dem Checkout frisch holt (siehe `fastlane/Fastfile`, „Der Tag zeigt auf den
   AKTUELLEN develop-HEAD … nicht auf den möglicherweise veralteten Checkout-Commit"). Beide
   stimmen nur überein, solange nichts auf `develop` nachläuft — zwischen Publish und Verify
   liegen zwei Gradle-Builds (~10–15 min). Ein Push in diesem Fenster hätte den Lauf zu Unrecht
   rot gemacht, mit einer Meldung, die zum falschen Schluss („falsches Release heruntergeladen")
   führt.

   Statt `github.sha` löst der Step **„Resolve published tag to commit"** den Tag im
   Git-Graphen auf:

   ```text
   gh api repos/<repo>/git/ref/tags/<tag>   → .object.type, .object.sha
   type == "tag"  →  gh api repos/<repo>/git/tags/<sha>  → .object.sha   (eine Ebene peel'en)
   Vergleich: .object.sha  ==  Revision im APK
   ```

   ⚠️ **Der Peel ist nicht optional.** Die Tag-Typen im Repo sind gemischt (belegt per
   `git for-each-ref`):

   ```text
   nightly-20261002-115620  commit          ← lightweight (`git tag <sha>`)
   v0.5.9-beta              tag -> commit   ← annotated  (`git tag -a`)
   v0.5.8-beta.1            commit          ← lightweight
   ```

   Die Graph-API liefert bei annotated Tags die SHA des **Tag-Objekts**, nicht des Commits.
   Wer nicht peel't, vergleicht bei jedem Version-Tag-Publish die Commit-SHA gegen die
   Objekt-SHA — das ist **immer** falsch, also ein roter Job pro Publish.

   **Kein Rückfall.** Ist die API dreimal nicht erreichbar, bricht der Job hart ab. Ein stiller
   Rückfall auf `github.sha` wäre genau die Annahme, die hier beseitigt wird — und würde den
   Fehler nur wieder hinter dem grünen Job verstecken.

   Als Step-Outputs wandern dafür `tag` und `embedded_revision` aus dem Read-Step heraus; der
   Read-Step liest die Revision nur noch aus und bricht ab, wenn sie sich nicht auslesen lässt
   (sonst lief der nachfolgende Rebuild mit leeren `-P`-Properties).

Die Zielwahl ist als **Verhaltenstest** festgeschrieben, nicht per `grep` auf eine Zeile: das
echte `run`-Snippet läuft in einer Sandbox mit gestubbtem `gh`/`curl`/`unzip`, und geprüft wird,
**welches** Release geladen wird (T14.1–T14.5 in `scripts/test_verify_reproducibility.sh`).
Dabei gilt eine Pflicht für jeden Stub: **`gh release list --jq` muss den Filter selbst
auswerten.** Ein Stub, der das rohe JSON zurückgibt, prüft den Stub statt des Snippets — so
sah im ersten Entwurf `TAG` als kompletter JSON-Array aus und der Fehlerfall „kein Nightly
im Raster“ blieb unentdeckt.

> **Randbedingung:** Der Vergleich ist seit #263 tag- statt lauf-basiert. Eine echte
> Race-Bedingung ist damit ausgeschlossen; die Aussage „der Job prüft das Release, das dieser
> Lauf veröffentlicht hat" gilt jetzt für **beide** Kanäle, unabhängig davon, wie weit `develop`
> zwischenzeitlich gelaufen ist.

## F-Droid-Hauptrepo (f-droid.org) & IzzyOnDroid

Für das **Hauptrepo** gibt es den Sentry-freien `foss`-Flavor (`applicationId com.vivid.foss`).
F-Droid baut selbst aus dem Quellcode und signiert selbst — die eigene Release-Signatur gilt dort nicht.

### Pflege-Dateien (committed, frühzeitig gelernt: nie ins F-Droid-Eigen-Repo leaken)

- `fdroid/config-fdroid-main.yml` — Gradle-Konfiguration für fdroidserver (`assembleFossRelease`,
  Output `app-foss-release.apk`, Builds-Template mit versionCode/versionName).
- `fdroid/metadata/com.vivid.foss.yml` — Pflicht-Metadata (f-droid.org **und** IzzyOnDroid):

```yaml
Categories:
  - Video
Licenses:
  - MIT
AuthorName: thoser666
WebSite: https://github.com/thoser666/Vivid
SourceCode: https://github.com/thoser666/Vivid
IssueTracker: https://github.com/thoser666/Vivid/issues
Donate: https://github.com/sponsors/thoser666
Name: Vivid (FOSS)
AutoName: Vivid
Summary: IRL streaming client
Description: ...
UpdateCheckMode: Tags
VercodeOperation:
  - '%c + 400'        # reservierter Separator-Bereich Standard↔foss
CurrentVersion: '0.5.14'
CurrentVersionCode: 5144
Builds:
  - versionName: 0.5.14-beta
    versionCode: 5142
    commit: v0.5.14-beta
    gradle:
      - yes
    output: app-foss-release.apk
    scandelete:
      - app/src/main/generated
  - versionName: 0.5.14
    versionCode: 5144
    commit: v0.5.14
    gradle:
      - yes
    output: app-foss-release.apk
    scandelete:
      - app/src/main/generated
```

**versionCode-Schema** (fastlane/release_safety.rb): `major*1_000_000 + minor*1_000 + patch*10 + Stufe`
(alpha=1, beta=2, rc=3, stable=4). `0.5.14-beta` → `5142`, `0.5.14` → `5144`. Der foss-Eintrag nutzt denselben
versionCode wie Standard — F-Droid und der eigene Repo-Server können sich damit nicht in die Quere
kommen (keine Duplikate pro Repo). Bei Versionsbumps immer `release_safety.rb`/Test
`test_fdroid_metadata.sh` (M1–M10) konsistent aktualisieren.

### Einreichung (bei Bedarf)

1. `./gradlew assembleFossRelease` lokal bauen und prüfen.
2. `fdroid/config-fdroid-main.yml` + `fdroid/metadata/com.vivid.foss.yml` als Beleg mitliefern.
3. MR bei <https://gitlab.com/fdroid/fdroiddata/-/merge_requests> (f-droid.org) bzw. den
   IzzyOnDroid-Einreichungspfad nutzen; Review 2–8 Wochen.
4. **Wichtig:** Das Hauptrepo baut aus **committed** Tag — die metadata „steht“ einen Release **vor**
   seinem Tag-Push, also beide Dateien mit dem Feature-Commit mitreichen (Test `M1`).
5. Nach dem Merge: F-Droid signiert neu (Intent/Verify impliziert Neuinstallation).

> **Antifeature-Vorsorge:** `foss`-Flavor hat kein Sentry → kein „Tracking“-Label. Nach dem
> F-Droid-Merge im Flatpak/`.yml` `AntiFeatures` nicht ergänzen lassen.

## Eigener F-Droid-Repo-Server (GitHub Pages)

Wird wöchentlich aus den **Stable-Releases** gebaut (nur `app-standard-release.apk`; der foss-Build
läuft über den F-Droid-Hauptrepo-Pfad / GitHub-Releases `app-foss-release.apk`). Details zur
Archiv-Strategie (`archive_older: 5`, `archive/`) siehe [RELEASE.md](../RELEASE.md) → „Eigener F-Droid-Repo-Server“.

**Wichtiger Trennschnitt:** `deploy-fdroid.yml` entfernt die committed `fdroid/metadata/*` **vor**
der Generierung des Eigen-Repos (`rm -rf metadata`) — sonst würde die Hauptrepo-Metadata
(`com.vivid.foss`, vom Foss-Flavor) beim `fdroid update` des **eigenen** Repos mitlaufen und dort
Duplikate/fehlende alles erzeugen.

## Accrescent (Evaluierung, noch kein Ziel)

- **Stand:** Nicht aktiv — keine Regressionstests, kein eigenes Repo. Grundlage: [Accrescent](https://accrescent.app) liefert signierte, verifizierte Updates und ist ein Upgradepfad ohne Python/Repo-Server, aber: kein eigenständiger App-Store-Beta-Zweig, keine Downloads ohne Signatur-Verifikation.
- **Wann sinnvoll:** wenn Play-Store-Upload + F-Droid ausgereizt und ein zusätzlicher, signatur-verifizierender Kanal gewünscht ist. Dann: [Accrescent-Docs](https://accrescent.app/docs) → App-Entry + Github-Actions-Template nachziehen.
- **Konsequenz:** Es bleibt dokumentiert, aber **kein** P0-Ziel (⇒ nicht in den Kanälen der Tabelle oben).

## Google Play aktivieren (Secrets)

Der Play-Job (`publish-play` in `release-pipeline.yml`) läuft nur manuell per
`workflow_dispatch` („Play-Upload ist ein bewusster Akt“). Fehlen die Secrets,
**skippt** der Guard (`scripts/check_play_secrets.sh`) den Job mit klarer
`::notice::`-Meldung, statt rot zu scheitern — ein Dispatch ohne Konfiguration
ist so eindeutig vom echten Play-Fehler unterscheidbar.

Benötigte Secrets (Repo → Settings → Secrets and variables → Actions):

| Secret | Inhalt |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | Upload-Keystore (`.jks`), base64-kodiert: `base64 -w0 upload-keystore.jks` (Windows: `certutil -encode upload-keystore.jks out.txt`) — **getrennt** vom GitHub-Release-Signatur-Key |
| `UPLOAD_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `UPLOAD_KEY_ALIAS` | Key-Alias im Keystore |
| `UPLOAD_KEY_PASSWORD` | Key-Passwort |
| `PLAY_JSON_KEY_FILE` **oder** `PLAY_JSON_KEY_DATA` | Service-Account-JSON für die Play Developer API (eins von beiden; `DATA` = JSON-Inhalt direkt, `FILE` = Pfad zu einer auf dem Runner geschriebenen Datei) |

Service-Account anlegen (Kurzfassung):

1. [Play Console](https://play.google.com/console) → API-Zugriff → Google-Cloud-Projekt verknüpfen.
2. Im Cloud-Projekt einen Service-Account anlegen, JSON-Schlüssel erzeugen.
3. In der Play Console den Service-Account mit der Rolle *Admin* (oder mindestens
   *Releases verwalten*) zur App einladen und freigeben.
4. JSON-Inhalt als `PLAY_JSON_KEY_DATA` hinterlegen (empfohlen — kein Datei-Pfad nötig).

Upload-Key vs. App-Signatur: Bei Play **App Signing by Google** bleibt der
Release-Key bei Google; der `UPLOAD_KEYSTORE` signiert nur die hochgeladene AAB.
Erst nach dem Secrets-Setup verhält sich ein `workflow_dispatch`-Lauf mit
`dry_run: true` als vollwertiger Probedurchlauf (Build + Signaturverifikation,
kein Upload).

## Tests & Guards

Die Distribution-Pipeline ist durch GitHub-Actions-Selbsttests abgesichert (laufen in `pre-push.sh`
und in der CI; gegen gemocktes `gh`/fastlane, ohne Netz):

| Test | Was |
|---|---|
| `scripts/test_build_retry.sh` | `with_gradle_retry`-Muster, foss-Build im Stable-Zweig (T1–T5) |
| `scripts/test_publish_release_hardening.sh` | Completeness, Idempotenz, Upload-Assets (S1–S8) |
| `scripts/test_sha256sums.sh` | Checksummen-Format, Sortierung, Verifikation (H1–H6, inkl. Nightly-Scope) |
| `scripts/test_pinned_checksums.sh` | Permanenter Latest-APK-Permalink + Prüfsummen-Anhang in beiden Publikations-Zweigen (R1–R4) |
| `scripts/test_distribution_stable.sh` | Workflow: Tag-Auswahl inkl. Untergrenze, Dispatch-Validierung, Keystore-Guard, cosign-Signatur, CHANGELOG-Mirror (D1–D16, davon D16.4–D16.8 Verhaltenstests der Tag-Auswahl) |
| `scripts/test_verify_reproducibility.sh` | Verify-Job: flavor-korrekte Assets, Rebuild-Pfade, **Zielwahl des zu prüfenden Releases**, Versionsquelle und **Tag-zu-Commit-Auflösung** (T1–T18, davon T14.1–T14.5 und T18.1–T18.7 Verhaltenstests in einer gh/curl/unzip-Sandbox) |
| `scripts/test_fdroid_metadata.sh` | Metadata-Dateien + versionCode-Konsistenz (M1–M10) |
| `scripts/test_bot_pr_credentials.sh` | Secrets/Credentials-Disziplin in allen Workflows (inkl. T4-/T7-*/T8-*/T9-*/T10-Loops) |

## Zusammenfassung

- **Stable** = wöchentlich, beide Flavor + `SHA256SUMS.txt` + cosign-keyless-Signatur, Completeness-geschützt, idempotent.
- **Beta / Versions-Tag** = manuell; gleiche Artefakte und Signatur wie Stable, zusätzlich `mapping.txt` + `output-metadata.json` als Voraussetzung für das Reproduzierbarkeits-Gate.
- **Nightly** = täglich, Standard-Flavor, prerelease, **ohne** cosign-Bundle (Tag erst zur Laufzeit bekannt).
- **Eigenes F-Droid-Repo** = wöchentlich aus Stable-APKs, GitHub Pages, eigenes Archiv.
- **F-Droid-Hauptrepo / IzzyOnDroid** = vorbereitet (`foss`-Flavor + Metadata), Einreichung bei Bedarf.
- **Accrescent** = dokumentierte Option, kein Ziel.