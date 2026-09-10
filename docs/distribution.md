# 📦 Distribution & Vertriebskanäle

Wie Vivid gebaut und verteilt wird: Kanäle, Kadenzen, Signierung, Reproduzierbarkeit und die
Einreichungswege für F-Droid-Repositorien. Die **Strategie** (Play vs. F-Droid vs. IzzyOnDroid,
Entscheidungen und Roadmap) steht in [RELEASE.md](../RELEASE.md) → „Vertriebskanäle“ — dieses
Dokument beschreibt den **technischen Ablauf** dahinter.

## Überblick: Kanäle

| Kanal | Wann | Arbeitsschritte im Workflow | Artefakte |
|---|---|---|---|
| **🌙 Nightly** | täglich 06:00 UTC (`schedule`) + manuell | `release-pipeline.yml` → Build + Test + publish | `app-standard-release.apk` + `SHA256SUMS.txt` (standard) + `mapping.txt` + `output-metadata.json` (nur Standard-Flavor; prerelease) |
| **🚀 Stable** | wöchentlich Mo 03:00 UTC + manuell | `distribution-stable.yml` → wählt neuestes noch nicht verteiltes `v*`-Release → Build (Standard **und** foss) + Checksummen + publish | `app-standard-release.apk` + `app-foss-release.apk` + `SHA256SUMS.txt` |
| **🛰 F-Droid-Repo** (eigenes) | wöchentlich Mo 04:00 UTC + manuell | `deploy-fdroid.yml` → lädt Stable-APKs, `fdroid update` → GitHub Pages | `repo/index.xml` + `archive/index.xml` |

Das Stable-Release wird also **wöchentlich statt bei jedem Tag-Push** publiziert. Ein neuer
`v*`-Tag baut und testet weiterhin sofort in der Pipeline (`build`/`test`-Jobs), löst aber **kein**
sofortiges Release-Publishing mehr aus — GitHub-Cronjobs kann man nicht pro Quelle unterscheiden,
deshalb gibt es pro Kadenz einen eigenen Workflow. Alles zusätzlich manuell per `workflow_dispatch` auslösbar.

## Stable-Distribution (`distribution-stable.yml`)

**Zweck:** Neueste Version, Stand Montag 03:00 UTC, als „Latest“-Release veröffentlichen.

1. **Tag-Auswahl:** Semver-Sortierung aller `v*`-Tags; ein Tag gilt als „noch nicht verteilt“,
   wenn sein GitHub-Release nicht vollständig ist (muss dauerhaft 3 Assets haben, siehe unten).
   Bei `workflow_dispatch` kann ein optionaler `version`-Input (Muster `v<major>.<minor>.<patch>`,
   optional mit Stufensuffix) den Kandidaten übersteuern.
2. **Build:** `bundle exec fastlane release_github tag:"$TAG"` baut **beide** Flavor:
   `assembleStandardRelease` (bereits aus der Pipeline bekannt) **und** `assembleFossRelease`.
3. **Checksummen:** `fastlane/sha256sums.rb` erzeugt `SHA256SUMS.txt` im GNU-Format
   (`<sha256>  <dateiname>`), deterministisch sortiert nach Basisname. Die Datei ist Bestandteil
   des Releases (per `fastlane`/`gh release upload`).
4. **Completeness-Regel** (in `fastlane/Fastfile` → `publish_release`): Ein Stable-Release ist
   **vollständig**, wenn es nicht Draft/Prerelease ist **und** alle drei Assets enthält:
   - `app-standard-release.apk`
   - `app-foss-release.apk`
   - `SHA256SUMS.txt`
   Ein unvollständiges Release wird gelöscht und neu erstellt (idempotent — ein schon vollständiges
   wird übersprungen).
5. **Keystore-Härtung:** Der foss-Build signiert mit demselben Release-Key; ohne `KEYSTORE_PATH`
   fällt Gradle auf einen Debug-Build zurück → der Workflow prüft die Keystore-Secrets vor dem Build.
6. **CHANGELOG-Mirror:** Die Release-Notes werden nach dem Publish per automatischem PR in
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
CurrentVersion: '0.5.14-beta'
CurrentVersionCode: 5142
Builds:
  - versionName: 0.5.14-beta
    versionCode: 5142
    commit: v0.5.14-beta
    gradle:
      - yes
    output: app-foss-release.apk
    scandelete:
      - app/src/main/generated
```

**versionCode-Schema** (fastlane/release_safety.rb): `major*1_000_000 + minor*1_000 + patch*10 + Stufe`
(alpha=1, beta=2, rc=3, stable=4). `0.5.14-beta` → `5142`. Der foss-Eintrag nutzt denselben
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

## Tests & Guards

Die Distribution-Pipeline ist durch GitHub-Actions-Selbsttests abgesichert (laufen in `pre-push.sh`
und in der CI; gegen gemocktes `gh`/fastlane, ohne Netz):

| Test | Was |
|---|---|
| `scripts/test_build_retry.sh` | `with_gradle_retry`-Muster, foss-Build im Stable-Zweig (T1–T5) |
| `scripts/test_publish_release_hardening.sh` | Completeness, Idempotenz, Upload-Assets (S1–S8) |
| `scripts/test_sha256sums.sh` | Checksummen-Format, Sortierung, Verifikation (H1–H6, inkl. Nightly-Scope) |
| `scripts/test_pinned_checksums.sh` | Permanenter Latest-APK-Permalink + Prüfsummen-Anhang in beiden Publikations-Zweigen (R1–R4) |
| `scripts/test_distribution_stable.sh` | Workflow: Tag-Auswahl, Dispatch-Validierung, Keystore-Guard, CHANGELOG-Mirror (D1–D12) |
| `scripts/test_fdroid_metadata.sh` | Metadata-Dateien + versionCode-Konsistenz (M1–M10) |
| `scripts/test_bot_pr_credentials.sh` | Secrets/Credentials-Disziplin in allen Workflows (inkl. T4-/T7-*/T8-*/T9-*/T10-Loops) |

## Zusammenfassung

- **Stable** = wöchentlich, beide Flavor + `SHA256SUMS.txt`, Completeness-geschützt, idempotent.
- **Nightly** = täglich, Standard-Flavor, prerelease.
- **Eigenes F-Droid-Repo** = wöchentlich aus Stable-APKs, GitHub Pages, eigenes Archiv.
- **F-Droid-Hauptrepo / IzzyOnDroid** = vorbereitet (`foss`-Flavor + Metadata), Einreichung bei Bedarf.
- **Accrescent** = dokumentierte Option, kein Ziel.