# 🚀 Release Pipeline

Jeder Release durchläuft eine von vier Stufen. Welche Stufe aktiv ist, bestimmt der Stand im [PARITY.md](PARITY.md)-Feature-Tracker.

## Stufenübersicht

| Stufe | Tag-Muster | Auslöser | Zielgruppe |
|-------|-----------|----------|------------|
| `nightly` | `nightly` (rollierend) | Jeder develop-Merge | Entwickler · CI-Tester |
| `alpha` | `vX.Y.0-alpha` | Manuell via `fastlane release_alpha` | Frühe Tester (Obtainium, kein Pre-Release-Flag nötig) |
| `beta` | `vX.Y.0-beta` | Manuell via `fastlane release_beta` (TODO) | Feldtester · Hunde essen ihr eigenes Futter |
| `stable` | `vX.Y.Z` | Manuell via `fastlane release_stable` (TODO) | Play Store · F-Droid · Allgemeinverfügbarkeit |

## 📐 Versionsstrategie (versionName & versionCode)

### versionName

| Release | versionName | Beispiel |
|---------|-------------|----------|
| nightly | `<letzter-v*-Tag>-nightly.<Run>` | `1.0-nightly.74` → nach `v0.2.0-alpha`: `0.2.0-nightly.75` |
| alpha / beta / rc | `<Version>-<Stufe>` | `0.2.0-alpha`, `0.2.0-beta` |
| stable | `<Version>` | `0.2.0`, `1.0.0` |

Die nightly-Basis kommt aus `git describe --tags --match 'v*'` — **nur Version-Tags** (`v*`), nie die rollierenden nightly-Tags. Ohne `v*`-Tag fällt sie auf `1.0` zurück.

### versionCode

**Versionierte Releases (alpha/beta/rc/stable)** — deterministisch aus dem Tag abgeleitet:

```
versionCode = major·1.000.000 + minor·1.000 + patch·10 + Stufe
Stufe: alpha=1, beta=2, rc=3, stable=4
```

| Tag | versionCode |
|-----|-------------|
| `v0.2.0-alpha` | 2001 |
| `v0.2.0-beta` | 2002 |
| `v0.2.0` | 2004 |
| `v0.3.0-alpha` | 3001 |
| `v1.0.0` | 1.000.004 |

Warum **nicht** `GITHUB_RUN_NUMBER`: Der gleiche Tag müsste immer dieselbe APK erzeugen (reproducible builds, siehe `android.includeDependencyInfoInApks=false`). Eine Run-Nummer wäre bei jedem Rebuild anders → andere APK → Reproduzierbarkeit kaputt. Der Tag-abgeleitete Code ist monoton für jede realistische Release-Reihenfolge (aufsteigendes SemVer).

**Nightly** — monoton steigende CI-Run-Nummer (kein `% 100000`-Wrap mehr):

```
versionCode = GITHUB_RUN_NUMBER
```

### ⚠️ Cross-Track-Verhalten

| Update-Pfad | Ergebnis |
|-------------|----------|
| nightly → nightly | ✅ immer installierbar (Run-Nummer steigt) |
| nightly → alpha/beta/stable | ✅ installierbar (Version-Code ist größer) |
| alpha → nächste alpha / beta / stable | ✅ installierbar (Version-Code steigt) |
| alpha/beta/stable → nightly | ❌ **Downgrade** — vorher deinstallieren |

Der letzte Fall ist Absicht: Ein „älteres" Nightly darf ein veröffentlichtes Release nicht still überschreiben. Wer vom Release zurück auf nightly will, deinstalliert und installiert neu.

## 🔁 Reproducible Builds

**Status: ✅ aktiv & verifiziert** — derselbe Commit + dieselben Versionsparameter + derselbe Keystore ergeben eine **bit-identische APK** (SHA-256 bestätigt, z. B. `82fe7538…` für CI vs. lokalen Rebuild von `0431d67`). Grundlage für F-Droid-Kompatibilität ([F-Droid Reproducible Builds](https://f-droid.org/en/docs/Reproducible_Builds/)).

### Flag & Root Cause

`gradle.properties`:
```properties
android.includeDependencyInfoInApks=false
```
AGP 9.2.1 bettet standardmäßig einen „SDK-Dependency-Data"-Block (ID `0x504B4453` „SDKP") in den APK-Signing-Block ein — ein Protobuf, der deflate-komprimiert und **pro Build mit einem Zufallsschlüssel verschlüsselt** wird. Dadurch war jede APK anders (ca. 12 KB Zufallsbytes im Signing-Block). Das Flag deaktiviert den Block. Trade-off: Play Console verliert die automatische Dependency-Insights-Anzeige.

Weitere Deterministismus-Quellen (bereits abgedeckt):
- Fixe Zip-Timestamps (1981) und deterministische Eintrags-Reihenfolge durch AGP
- Deterministische Signatur: RSA-PKCS1v1.5 (gleicher Key ⇒ gleiche Signatur; `apksigner` zweimal auf derselben APK → identisch)
- `local_root_path` wird portabel als `$PROJECT_DIR` geschrieben (CI == lokal)
- JDK/OS-unabhängig (verifiziert: temurin 17/Ubuntu vs. Oracle 22/Windows)

### Verifikation (manuell — jederzeit wiederholbar)

Die Prozedur ist identisch mit dem automatischen CI-Check (`verify-reproducibility`) und lässt sich für jedes beliebige Release nachvollziehen:

```sh
# 1) Commit auschecken — frischer Clone, NICHT `git worktree`
#    (AGP erkennt in einem Worktree kein VCS → version-control-info weicht ab)
git clone <repo-url> repo-check && cd repo-check
git checkout <commit-sha>          # aus dem Release-Titel / version-control-info

# 2) Lokale Build-Artefakte kopieren (nie committet):
#    - local.properties (SDK-Pfad)
#    - keystore.properties + vivid-streaming-app.jks (Signing, falls nicht CI)
#    - gradle/gradle-daemon-jvm.properties (JVM-Pinning, sonst anderer JDK-Fallback)

# 3) versionName/versionCode aus dem veröffentlichten Release lesen:
#    - aus dem Release-Titel:  "Vivid nightly (0.2.0-nightly.78)" → name=0.2.0-nightly.78
#    - versionCode aus output-metadata.json des Releases ("versionCode": 78)
#      oder: aapt dump badging app-release.apk | grep version

# 4) Bauen mit exakt diesen Parametern + Keystore-Env:
KEYSTORE_PATH=... KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
  ./gradlew :app:assembleRelease -PversionName=<name> -PversionCode=<code> \
    --stacktrace --no-daemon

# 5) Hash-Vergleich — alle drei Artefakte müssen bit-identisch sein:
sha256sum app/build/outputs/apk/release/app-release.apk        <veröffentlichtes>/app-release.apk
sha256sum app/build/outputs/mapping/release/mapping.txt        <veröffentlichtes>/mapping.txt
sha256sum app/build/outputs/apk/release/output-metadata.json  <veröffentlichtes>/output-metadata.json

# Erwartetes Ergebnis: identische Hashes für alle drei Dateien.
```

**Wichtige Fallstricke** (alle in der Praxis gefunden):
- **`git worktree` statt Clone** → `version-control-info.textproto` weicht ab (einziger Unterschied, alles andere identisch). Immer frisch klonen.
- **Anderer Keystore** → andere Signatur → anderer Hash. Der Release-Key (`release.keystore` als CI-Secret) muss verwendet werden.
- **Andere `-PversionName`/`-PversionCode`** → andere APK und anderes `output-metadata.json`. Werte exakt aus dem Release übernehmen.

### Automatischer CI-Check

Der Job **`verify-reproducibility`** in `android_fastlane.yml` vergleicht nach jedem nightly-Publish die veröffentlichten Artefakte automatisch mit einem frischen Build desselben Commits (liest versionName/versionCode aus dem Release, prüft die eingebettete Git-Revision, Hash-Vergleich). **Alle drei Artefakte sind deterministisch und werden verglichen:** `app-release.apk`, `mapping.txt` (ProGuard/R8) und `output-metadata.json` — die Nightly-Releases enthalten daher neben dem APK auch Mapping und Metadaten (für Deobfuskation und Reproduzierbarkeits-Check). Schlägt ein Vergleich fehl, wird der Workflow rot.

## ⚠️ Stage Gates

### `nightly` → laufend
- Kriterium: CI grün → APK published
- Keine manuelle Prüfung

### `alpha` → ab jetzt aktiv
**Kriterien (alle müssen erfüllt sein):**
- [ ] Alle Unit-Tests grün (`bundle exec fastlane test`)
- [ ] RTMP-Streaming funktioniert (✅ in PARITY.md)
- [ ] SRT-Streaming funktioniert (✅ in PARITY.md)
- [ ] OBS-WebSocket-Steuerung getestet (✅ in PARITY.md)
- [ ] Kein Crash in 5-minütigem manuellen Test
- [ ] Auf `develop`-Branch

**Befehl:**
```sh
bundle exec fastlane release_alpha
```
→ Pusht `v0.2.0-alpha`-Tag → CI baut signiert und veröffentlicht als GitHub-Prerelease

### `beta` — 🚦 **Nächster Meilenstein**

> **🧠 Erinnerung: Sobald diese Kriterien erfüllt sind → `release_beta`-Lane schreiben und ersten Beta-Tag setzen.**
>
> **📋 Google Play: Vor dem ersten Beta-Release benötigst du:**
> - Google Play Developer Account ($25 einmalig)
> - App-Signing-Key (bereits vorhanden: `release.keystore` im CI)
> - ~~Privacy Policy~~ → **erledigt:** [PRIVACY.md](PRIVACY.md)
> - App-Icon (512×512, PNG)
> - Screenshots (mind. 2, 16:9 oder 9:16)
> - Store Listing (Kurzbeschreibung, Langbeschreibung)
> - Content Rating Questionnaire
> - Data Safety Section (welche Daten sammelt die App?)
>
> → **Vorlagen bereit:** `fastlane/metadata/android/en-US/` und `de-DE/` mit Titel, Kurz-/Langbeschreibung und Changelogs. Privacy Policy fertig ([PRIVACY.md](PRIVACY.md)). Noch ausfüllen:
>   - App-Icon (512×512, PNG) → `fastlane/metadata/android/images/icon.png`
>   - Screenshots (mind. 2, 16:9 oder 9:16) → `fastlane/metadata/android/images/phoneScreenshots/`
>   - Content Rating Questionnaire (wird in der Play Console ausgefüllt)
>   - Data Safety Section (welche Daten sammelt die App?)

**Kriterien (alle müssen erfüllt sein):**
- [ ] ≥50 % Feature-Parität (aktuell 7/33 ≈ 21 %, Ziel: ≥17 Features)
- [ ] Chat implementiert & getestet (📋 in PARITY.md)
- [ ] Mindestens ein Overlay/Widget funktioniert (📋 in PARITY.md)
- [ ] Kamera-Vorschau stabil (🚧 in PARITY.md)
- [ ] Settings persistent über App-Neustarts
- [ ] Keine bekannten Showstopper-Bugs
- [ ] ≥2 manuelle Tester haben bestätigt: „kein Crash in 15 Minuten"
- [ ] Google-Play-Unterlagen vorbereitet (s. o.)

**Befehl (TODO, noch zu implementieren):**
```sh
bundle exec fastlane release_beta
```

### `stable` — 🏁 **Endziel**

> **🧠 Erinnerung: Sobald diese Kriterien erfüllt sind → `release_stable`-Lane schreiben und v1.0.0 taggen.**

**Kriterien (alle müssen erfüllt sein):**
- [ ] ≥90 % Feature-Parität (≈30 von 33)
- [ ] Alle entwickelten Features in PARITY.md auf ✅
- [ ] Vollständige CI-Test-Suite (Unit + UI + Integration)
- [ ] Performance-Test bestanden (Streaming-Latenz <2 s, App-Start <1 s)
- [ ] Accessibility-Baseline (min. TalkBack-fähig)
- [ ] Privacy Policy live
- [ ] Play Store Listing vollständig
- [ ] F-Droid-Metadaten vorbereitet

**Befehl (TODO, noch zu implementieren):**
```sh
bundle exec fastlane release_stable
```

---

> **Pflege:** Dieses Dokument bei jeder Änderung an PARITY.md prüfen — wenn ein Gate erreicht ist, die entsprechende Lane implementieren und taggen.
