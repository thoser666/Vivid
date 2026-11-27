# Release Notes: Vivid v0.5.51-beta

**Release Date:** 2026-11-27
**Version:** 0.5.51-beta (versionCode 5512, nach `major*1_000_000 + minor*1_000 + patch*10 + 2`)
**Channel:** Beta (GitHub Releases → „Latest" → Obtainium → eigenes F-Droid-Repo)

> **Warum dieser Cut:** `v0.5.50-beta` ist am Emulator-Gate der
> Stable-Distribution gescheitert (Run 45948779761, alle drei Versuche) — und
> zwar deterministisch: Seit dem Szenen-Compositor hat `MainActivity` **zwei**
> Compose-Roots (Hauptfenster + `Presentation` auf dem virtuellen Display), zwei
> Smoke-Tests protokollierten ihren Baum aber per `onRoot()` und verlangten
> damit genau einen Root (`Expected exactly '1' node but found '2' nodes`).
> Lokal auf `vivid_api34` reproduziert; der Fix (`07524c7f`) protokolliert alle
> Roots, die Assertions bleiben unverändert. Ein Tag wird nicht neu geschrieben
> — dieser Cut enthält deshalb die 0.5.50-Inhalte **plus** den Fix.
> `0.5.50-beta` wurde nie veröffentlicht.

## 🎉 Highlights (neu gegenüber `v0.5.49-beta`)

### 📦 F-Droid-/IzzyOnDroid-Reife
- **APK deutlich kleiner:** 57,2 MB → **15,9 MiB** (16.653.936 B) — die
  WebRTC-Prebuilds (`org.webrtc`/`libjingle`) sind aus dem Release-APK
  ausgeschlossen; `scripts/check_apk_size.sh` hält die Grenze mit Selbsttest.
- **Store-Metadaten innerhalb der Limits** (Play + IzzyOnDroid): Titel,
  Kurz-/Langbeschreibung und Changelogs geprüft von
  `scripts/check_play_metadata.sh`.
- **Store-Screenshots neu** — reproduzierbar auf einem eigenen AVD aufgenommen,
  mit gemockter Vorschau statt des Emulator-Testmusters.

### 🔁 Reproduzierbares Nightly
- **Compose-Mapping-Anhang wird kanonisch sortiert**: AGP hängt
  `compose-mapping.txt` an `mapping.txt` an, und dessen Reihenfolge war nicht
  stabil — über `pg_map_hash` und Sentry-`ProguardUuids` kippte damit das
  gesamte APK, obwohl der R8-Teil byte-identisch war.
- Neu `scripts/test_compose_mapping_canonical.rb`: prüft die **Eigenschaft**
  (Permutationsinvarianz, Idempotenz, UTF-8-Byte-Ordnung) gegen die echte
  Implementierung, nicht gegen eine Kopie.

### 🔧 F-Droid-Metadaten automatisieren sich selbst
- `release_alpha`/`release_beta` tragen den neuen Tag **vor** dem Tag-Push in
  den `Builds`-Block ein (`fastlane/fdroid_metadata.rb`).
- **versionCode-Ableitung korrigiert:** `assembleFossRelease` ohne
  `-PversionCode` rechnet stufenbewusst wie `release_safety.rb`.

## 🐛 Fixes
- **#433** — Log-Zeilen mit fehlendem Level crashten die Persistenz/Sicht.
- **#432** — korrupte `settings.preferences_pb` wird durch Defaults ersetzt
  statt die App zu blockieren.
- Der Encoder-Modus-Check blockiert den Streamstart nicht mehr.
- RootEncoder-Teardown: GL-Teardown-Guard zurückgenommen (`stop()` kann unseren
  Aufruf nicht ablehnen) — die Fehlerklasse #453/#445 wird weiter beobachtet.
- Sentry: lokaler Mapping-Upload nur noch per Opt-in (`-PsentryUpload`),
  Event-Stacks über `--attach-event-stack`, `io.sentry.environment` trennt
  Development und Production.

## 🧪 Tests & Guardrails
- **Emulator-Gate repariert:** `StartupSmokeTest` und
  `AccessibilityComplianceInstrumentedTest` protokollieren jetzt **alle**
  Compose-Roots statt genau einen anzunehmen. Belege: lokal auf `vivid_api34`
  beide Flavors grün (`foss`/`standard`: 20 Tests, 0 Fehler, 9 übersprungen —
  unverändert gegenüber vorher); CI-Beleg folgt mit dem Stable-Distributions-Run.
- Pre-Push-Gate und CI prüfen APK-Größe, Play-Metadaten, F-Droid-Metadaten
  (inkl. Builds-Block-Updater), Compose-Mapping-Kanonisierung und
  Reproduzierbarkeit — jeweils mit Selbsttests und Mutationsproben.

## ⚠️ Bekannte offene Punkte
- **#453/#445** (`RejectedExecutionException` beim Teardown, critical) und
  **#429** („Go Live always crashes on Pixel 7") sind **offen**.
- **IzzyOnDroid-Einreichung:** RFP zeigt auf diesen Tag; offen ist nur noch die
  Erklärung zur KI-Unterstützung.
- 9 von 20 instrumentierten Tests sind auf API 34 weiterhin übersprungen
  (Geräte-/Hardware-Annahmen) — unverändert, nicht Teil dieses Cuts.

## 📥 Installation & Update

| Kanal | Bezug |
|-------|-------|
| **GitHub Releases** | „Latest" (diese Version) — FOSS-/Standard-APK + `SHA256SUMS.txt` |
| **Obtainium** | zieht automatisch das neueste GitHub-Release |
| **Eigenes F-Droid-Repo** | wöchentlicher Build aus den Stable-Releases |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt; Einreichung folgt |

- **Update-Pfad:** von `0.5.49-beta` (5492) direkt installierbar (5512 > 5492);
  nightly → beta ✅; beta → nightly wäre ein Downgrade (vorher deinstallieren).
- **Prüfsummen:** `SHA256SUMS.txt` im Release, `cosign verify-blob` mit
  `SHA256SUMS.txt.bundle`.

## 🔗 Referenzen

- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Reproduzierbarkeit: [RELEASE.md](../RELEASE.md) → Automatischer CI-Check
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
