# Release Notes: Vivid v0.5.50-beta

**Release Date:** 2026-11-25
**Version:** 0.5.50-beta (versionCode 5502, nach `major*1_000_000 + minor*1_000 + patch*10 + 2`)
**Channel:** Beta (GitHub Releases → „Latest" → Obtainium → eigenes F-Droid-Repo)

> **Warum dieser Cut:** 285 Commits seit `v0.5.49-beta` (29.10.2026) — die
> F-Droid-/Izzy-Reifung, ein reproduzierbares Nightly und eine Reihe von
> Feld-Fixes. `0.6.0` bleibt weiterhin dem offenen
> Streaming-Erweiterungs-Bucket (RIST, WHIP, RTMP-Pull, SRTLA, 4K/HEVC)
> reserviert; dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie.

## 🎉 Highlights

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
  gesamte APK, obwohl der R8-Teil byte-identisch war. Der Build sortiert den
  Anhang jetzt vor der Hash-Bildung; der Verify-Job baut zweimal (A/B) und
  trennt „Build nicht deterministisch" von „veröffentlichtes Artefakt weicht
  ab".
- Neu `scripts/test_compose_mapping_canonical.rb`: prüft die **Eigenschaft**
  (Permutationsinvarianz, Idempotenz, UTF-8-Byte-Ordnung) gegen die echte
  Implementierung, nicht gegen eine Kopie.

### 🔧 F-Droid-Metadaten automatisieren sich selbst
- `release_alpha`/`release_beta` tragen den neuen Tag **vor** dem Tag-Push in
  den `Builds`-Block ein (`fastlane/fdroid_metadata.rb`) — der Tag enthält
  damit seinen eigenen Eintrag, den fdroidserver auscheckt. Ältere Tags zieht
  `fastlane sync_fdroid_metadata tag:vX.Y.Z` nach.
- **versionCode-Ableitung korrigiert:** `assembleFossRelease` ohne
  `-PversionCode` rechnet jetzt stufenbewusst wie `release_safety.rb` — vorher
  hätte F-Droid `v0.5.49-beta` als 5494 gebaut, während die Metadata 5492
  verlangte (Build abgelehnt).

## 🐛 Fixes
- **#433** — Log-Zeilen mit fehlendem Level crashten die Persistenz/Sicht:
  nullable DTO beim Parsen, `?` als Level-Label statt Absturz.
- **#432** — korrupte `settings.preferences_pb` wird durch Defaults ersetzt
  statt die App zu blockieren.
- Der Encoder-Modus-Check blockiert den Streamstart nicht mehr.
- RootEncoder-Teardown: GL-Teardown-Guard zurückgenommen (`stop()` kann unseren
  Aufruf nicht ablehnen) — die Fehlerklasse #453/#445 wird weiter beobachtet.
- Sentry: lokaler Mapping-Upload nur noch per Opt-in (`-PsentryUpload`),
  Event-Stacks über `--attach-event-stack`, `io.sentry.environment` trennt
  Development und Production.

## 🧪 Tests & Guardrails
- Pre-Push-Gate und CI prüfen jetzt: APK-Größe, Play-Metadaten,
  F-Droid-Metadaten (inkl. Builds-Block-Updater), Compose-Mapping-Kanonisierung,
  Reproduzierbarkeit — jeweils mit eigenen Selbsttests und Mutationsproben.
- Dependency-Pflege: `libs.versions.toml`, fdroidserver-Requirements,
  JDOM2/Jose4j gepinnt; MockK, KSP, `ruby/setup-ruby`, CodeQL-Familie,
  `release-drafter` aktualisiert.

## ⚠️ Bekannte offene Punkte
- **#453/#445** (`RejectedExecutionException` beim Teardown, critical) und
  **#429** („Go Live always crashes on Pixel 7") sind **offen**.
- **IzzyOnDroid-Einreichung** steht noch aus (Fingerprint + KI-Erklärung im
  RFP-Text sind Nutzerentscheidung).
- Die CI-Jobs `Run Tests` (Robolectric) und `Self-Test F-Droid-Metadata`
  (Tag-Check bei `fetch-depth: 1`) sind derzeit rot; das Nightly-Gate läuft
  deshalb seit dem 23.11. nicht.

## 📥 Installation & Update

| Kanal | Bezug |
|-------|-------|
| **GitHub Releases** | „Latest" (diese Version) — FOSS-/Standard-APK + `SHA256SUMS.txt` |
| **Obtainium** | zieht automatisch das neueste GitHub-Release |
| **Eigenes F-Droid-Repo** | wöchentlicher Build aus den Stable-Releases |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt; Einreichung folgt |

- **Update-Pfad:** von `0.5.49-beta` (5492) direkt installierbar (5502 > 5492);
  nightly → beta ✅; beta → nightly wäre ein Downgrade (vorher deinstallieren).
- **Prüfsummen:** `SHA256SUMS.txt` im Release, `cosign verify-blob` mit
  `SHA256SUMS.txt.bundle`.
- **FOSS-Hinweis:** Alle Änderungen sind flavor-unabhängig und wirken identisch
  in beiden Builds.

## 🔗 Referenzen

- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Reproduzierbarkeit: [RELEASE.md](../RELEASE.md) → Automatischer CI-Check
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
