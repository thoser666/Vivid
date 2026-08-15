# 🚀 Release Pipeline

Jeder Release durchläuft eine von vier Stufen. Welche Stufe aktiv ist, bestimmt der Stand im [PARITY.md](PARITY.md)-Feature-Tracker.

## Stufenübersicht

| Stufe | Tag-Muster | Auslöser | Zielgruppe |
|-------|-----------|----------|------------|
| `nightly` | `nightly` (rollierend) | Jeder develop-Merge · **täglich 06:00 UTC (Schedule)** · manuell (`workflow_dispatch`) | Entwickler · CI-Tester |
| `alpha` | `vX.Y.0-alpha` | Manuell via `fastlane release_alpha` | Frühe Tester (Obtainium, kein Pre-Release-Flag nötig) |
| `beta` | `vX.Y.0-beta` | Manuell via `fastlane release_beta` (TODO) | Feldtester · Hunde essen ihr eigenes Futter |
| `stable` | `vX.Y.Z` | Manuell via `fastlane release_stable` (TODO) | Play Store · F-Droid · Allgemeinverfügbarkeit |

## 🗺️ Roadmap (Version → Features)

Welche offenen PARITY-Punkte in welcher Version released werden — Zählerstand: ✅ **16** (Stand 2026-08-14, [PARITY.md](PARITY.md)). Reihenfolge nach dem Prinzip: **erst Gate-Pflichten, dann Nutzer-Sichtbares, dann Streaming-Komfort, zuletzt Protokoll-Ausbau & Plattform** — jedes Release bleibt für sich testbar und läuft über die jeweilige Fastlane-Lane.

| Version | Inhalt (PARITY-Punkte) | ✅ nach Release | Gate |
|---------|------------------------|-----------------|------|
| `v0.2.0-alpha` | (läuft bereits) | 16 | alpha-Gate aktiv |
| `v0.3.0-beta` | **Chat & Moderation (4):** Plattform-Chat, Emotes, Moderation/Chat-Bot/TTS, Media-Player-Bot · **erstes Overlay:** Text-/Info-Widgets (Sensor-/GPS-Daten) | **21** | **Beta-Gate erreicht** (Chat + ≥1 Widget + ≥17 ✅) |
| `v0.4.0-beta` | **Overlays & Widgets (5):** Chat-Overlay/Event-Alerts, Text-Widget-Variablen, Karten-Widget, Browser-Widget, Scoreboards | **26** | Beta #2 |
| `v0.5.0-beta` | **Kamera & Video (4):** Color-Spaces/3D-LUTs, Video-Effekte, Externes Zubehör, Photo-Shoot · **Audio (3):** Mic-Verwaltung, Level-Meter/Muting/Sync, Talk-Back · Oura-Ring-Widget | **34** | Beta #3 |
| `v0.6.0-beta` | **Streaming-Erweiterung (5):** RIST, WHIP, RTMP-Pull/Ingest, 4K/HEVC, SRTLA-Bonding · OBS Snapshot/Audio-Levels · Game-Controller, Deep-Linking/Konfig-Import | **42** | Beta #4 |
| `v1.0.0-stable` | I18n (🚧) + Play-Store-Unterlagen (Icon, Screenshots, Listing) | **43 (100 %)** | Stable-Gate (≥90 %) |

> **Pflege:** Bei jeder Änderung an PARITY.md prüfen — neue Features wandern in den nächsten passenden Versions-Bucket; die Grenzen sind flexibel (ein Feature darf vorziehen, wenn es erst das Gate der nächsten Version schließbar macht).

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
#    - release.keystore + Env-Variablen (Signing — identisch zu CI, siehe unten)
#    - gradle/gradle-daemon-jvm.properties (JVM-Pinning, sonst anderer JDK-Fallback)

# 3) versionName/versionCode aus dem veröffentlichten Release lesen:
#    - aus dem Release-Titel:  "Vivid nightly (0.2.0-nightly.78)" → name=0.2.0-nightly.78
#    - versionCode aus output-metadata.json des Releases ("versionCode": 78)
#      oder: aapt dump badging app-release.apk | grep version

# 4) Bauen mit exakt diesen Parametern + Keystore-Env:
#    (KEYSTORE_PATH zeigt lokal auf die Datei; in CI erzeugt der Decode-Step sie)
KEYSTORE_PATH=/pfad/zum/release.keystore \
KEYSTORE_PASSWORD=<store-password> \
KEY_ALIAS=<key-alias> \
KEY_PASSWORD=<key-password> \
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

**Signatur-Check:** Zusätzlich zum Hash-Vergleich verifiziert der Job die **Signatur des veröffentlichten APK** gegen den Release-Key: Der SHA-256-Fingerprint des APK-Signers (`apksigner verify --print-certs`) wird mit dem Zertifikat aus dem dekodierten `release.keystore` (`keytool -list -v`) verglichen. Damit wird hart sichergestellt, dass das veröffentlichte Nightly **nicht mit dem Debug-Key** signiert ist — ein Debug-signiertes APK würde Obtainium-Updates (Signatur-Mismatch) brechen. Auch dieser Check macht den Workflow bei Abweichung rot.

**AAB (Bundle):** Sollte die Release-Lane künftig zusätzlich zum APK ein `app-release.aab` publizieren, wird es automatisch mitgeprüft (der Download-Step erkennt das AAB im Release und aktiviert die AAB-Checks nur dann): ① **Signatur** per `jarsigner -verify` (Integrität) + `keytool -printcert -jarfile` (Zertifikat gegen Release-Key), ② **Reproduzierbarkeit** durch frischen `:app:bundleRelease`-Build mit denselben versionName/versionCode-Parametern und Hash-Vergleich. Die AAB-Determinismustests lokal bestätigen: Zwei vollständig frische `bundleRelease`-Builds desselben Commits sind **bit-identisch** (Vorsicht: ein mit `--rerun-tasks` gemischter Vergleich gegen einen zwischenzeitlich veralteten Task-Cache täuscht Unterschiede vor — nur frische Builds vergleichen).


## 🔒 CI-Härtung: Pinned Actions (Supply-Chain)

Alle Drittanbieter-Actions in `.github/workflows/` sind auf **immutable Commit-SHAs** gepinnt 
statt auf bewegliche Tags. Ein kompromittierter oder umgeschriebener Tag kann damit keinen Lauf mehr 
stillschweigend vergiften; Updates passieren ausschließlich bewusst per PR. Die Provenienz steht als 
Kommentar (`# v5`)— dieser Kommentar ist Pflicht, damit Dependabot/`actions-updater` die Version erkennen.

### Pins (Stand: 15.08.2026, Commit `2212593`)

| Action | Pin (SHA) | Version | Verwendung |
|--------|-----------|---------|------------|
| `actions/checkout` | `fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09` | v5 | alle Workflows |
| `actions/setup-java` | `b6effb05e454b25005698d916606bdc6ffcbf961` | v5 | alle Workflows (JDK 17) |
| `actions/cache` | `caa296126883cff596d87d8935842f9db880ef25` | v5 | android_fastlane (Gradle-Cache) |
| `actions/upload-artifact` | `b7c566a772e6b6bfb58ed0dc250532a479d7789f` | v6 | alle Workflows (Artefakte) |
| `ruby/setup-ruby` | `95ef2b042f9d7a56d8268cba8559e2842e2ad01b` | v1.321.0 | android_fastlane (Ruby 3.3) |

**Verifikation (15.08.2026):** Alle Tags sind leichtgewichtig; die gepinnten SHAs sind exakt die 
Commits, auf die die Tags aktuell zeigen (`git ls-remote <repo> refs/tags/<tag>`). CI bestätigt die Pins 
(Lauf `31871320171`: Secret Guard/Build mit gepinnten Actions grün). Vorsicht bei **annotierten Tags**: 
`ls-remote` liefert dann die Tag-Objekt-SHA, die GitHub Actions als `uses:`-Pin nicht akzeptiert— beim 
Bump immer den gepeelten Commit `refs/tags/<tag>^{}` verwenden.

### Update-Fahrplan

1. **Empfohlen: Dependabot für `github-actions`** (`.github/dependabot.yml`):
   ```yaml
   version: 2
   updates:
     - package-ecosystem: "github-actions"
       directory: "/"
       schedule:
         interval: "weekly"
   ```
   Dependabot liest die `# vX`-Kommentare und zieht bei einem Update SHA + Kommentar gemeinsam per PR.
2. **Alternativ `actions-updater` (github/actions-updater)** oder manuell: SHA ersetzen und den 
   `# vX`-Kommentar auf die neue Version setzen. Neue SHA ermitteln per
   `git ls-remote <repo> refs/tags/<tag>^{}` (gepeelter Commit, siehe oben).
3. **Nach jedem Bump:** die komplette CI-Runde abwarten (android.yml + android_fastlane.yml)— ein Pin 
   gilt erst als sicher, wenn Secret-Guard, Tests, Release- und Verify-Jobs grün sind. Den 
   `# vX`-Kommentar nie ohne SHA-Wechsel ändern (sonst stimmt die Provenienz nicht mehr).
4. **Grundregel:** nie einen Tag als `uses:`-Referenz einführen— Tags sind beweglich; nur 
   SHA-Pins mit Provenienz-Kommentar sind zulässig.

### 🚧 Ausstehend: Kotlin-Update auf 2.4.20 (stabil)

Der direkte Dependabot-Alert `kotlin-gradle-plugin` (unsafe Deserialization im Kotlin Build Cache, Dependabot #63) ist mit `tolerable_risk` dismissed. Die erste gepatchte Version ist **2.4.20-Beta1**; die **stabile 2.4.20** erscheint laut [Kotlin-Release-Fahrplan](https://kotlinlang.org/docs/releases.html) im **September 2026**. Bis dahin bleibt der Alert dismissed (Build-Tooling-only, kein App-Runtime-Risiko).

**Beim Update dann:**
- `kotlin` und `jetbrainsKotlinJvm` in `gradle/libs.versions.toml` auf `2.4.20` anheben — Compose-Compiler und Serialization alignen automatisch (`version.ref = "kotlin"`).
- **KSP** (`ksp-version = "2.3.11"`) auf die zu Kotlin 2.4.20 passende Version heben (KSP folgt der Kotlin-Version).
- Voller Testlauf Pflicht (CI-Mirror): `./gradlew testDebugUnitTest` + `lintDebug` — danach verifizieren, dass Dependabot den Alert #63 automatisch schließt.
- Dependabot (gradle, weekly) öffnet den Update-PR automatisch, sobald 2.4.20 stabil auf Maven Central ist.

## 🔑 Signing-Secrets (CI)

Alle Releases werden mit **einem einzigen Release-Key** signiert — derselbe Keystore in CI, lokal und für jede spätere Play-Console-/F-Droid-Signierung. Die Secrets liegen als **GitHub-Repository-Secrets** (Settings → Secrets and variables → Actions), nie im Repo.

### Tatsächlich verwendete Secrets

| Secret | Inhalt | Verwendung |
|--------|--------|------------|
| `KEYSTORE_BASE64` | Der komplette Release-Keystore, base64-kodiert | Decode-Step in allen Release-/Verify-Jobs (`base64 -di` → `release.keystore` auf dem Runner; schlägt laut fehl, wenn leer — kein stilles Debug-Signing) |
| `KEYSTORE_PASSWORD` | Store-Passwort des Keystores | `app/build.gradle.kts` (`signingConfigs.release`) · `keytool` beim Signatur-Check |
| `KEY_ALIAS` | Alias des Signaturschlüssels | `app/build.gradle.kts` · `keytool -list -v -alias` beim Signatur-Check |
| `KEY_PASSWORD` | Passwort des Schlüssels (i. d. R. = Store-Passwort) | `app/build.gradle.kts` (`keyPassword`) |

> **Achtung:** `KEYSTORE_PATH` ist **kein Secret**, sondern wird im CI vom Decode-Step als Env-Variable gesetzt (`${{ github.workspace }}/release.keystore`); lokal zeigt sie auf die eigene Keystore-Datei. `app/build.gradle.kts` liest die vier Variablen ausschließlich über `System.getenv(...)` — kein `keystore.properties`-Fallback.

### Verifikations-Ablauf (jeder Release)

1. **Decode Keystore** (build-release, publish-release, verify-reproducibility): `KEYSTORE_BASE64` → Datei, `KEYSTORE_PATH` exportiert. Fehlt das Secret → **Abbruch** (statt Debug-Signing).
2. **Build/Sign** (`assembleRelease` / `bundleRelease`): Gradle signiert mit dem Release-Key aus den vier Env-Variablen.
3. **Signatur-Check** (verify-reproducibility, nach dem Publish):
   - APK: `apksigner verify --print-certs` → SHA-256 des Signers **vs.** `keytool -list -v` des Keystores → muss identisch sein (Debug-Key ⇒ roter Workflow)
   - AAB (falls publiziert): `jarsigner -verify` + `keytool -printcert -jarfile` → derselbe Vergleich
4. **Reproduzierbarkeits-Check**: frischer Rebuild desselben Commits mit denselben Version-Parametern → Hash-Vergleich (siehe oben). Anderer Keystore ⇒ andere Signatur ⇒ anderer Hash ⇒ roter Workflow.

### Referenz-Fingerprint (Release-Key)

Der Signatur-Check im `verify-reproducibility`-Job muss bei **jedem** Lauf exakt diesen SHA-256-Fingerprint liefern — für `Key SHA-256:` (Keystore-Zertifikat) **und** `APK Signer SHA-256:` (veröffentlichtes APK), beide identisch:

```
SHA-256: b31b8119bd065cdb7a51ad2ee7f71f17f1eb154e2a7c3007de644b4c14d6a85e
```

- **Verifiziert in CI-Lauf `31782286611`** (Commit `580f79d`, Workflow „Android CI with Fastlane“, grün) — `Key SHA-256:` und `APK Signer SHA-256:` stimmen überein
- **Abweichung = roter Workflow:** Weicht ein künftiger Lauf ab (anderer Keystore, Debug-Key, vertauschte/leere Secrets), sofort prüfen, welcher Keystore in `KEYSTORE_BASE64` hinterlegt ist
- **Lokale Gegenprüfung:** `keytool -list -v -keystore <release.keystore> -alias <KEY_ALIAS> -storepass '<KEYSTORE_PASSWORD>' | grep -i sha256` — der SHA-256-Wert der Zertifikatskette muss identisch sein

### Veraltete Namen (entfernt)

Frühere Setups nutzten abweichende Namen — `KEYALIAS`, `STOREFILE`, `SIGNING_*` (z. B. `SIGNING_STORE_FILE`/`SIGNING_KEY_ALIAS`) sowie eine lokale `keystore.properties`. Diese sind **vollständig entfernt**; einzige Quelle der Wahrheit sind die vier Secrets oben. Die lokale `keystore.properties` (untracked) ist ein Legacy-Überbleibsel aus dem alten Setup und wird vom Build nicht mehr gelesen — sie kann gelöscht werden.

### 🔐 Release-Keystore erzeugen & Secrets hinterlegen (Erstinstallation)

> **Hast du bereits einen Keystore?** Dieses Projekt nutzt bereits einen Release-Key (die vier Secrets sind hinterlegt). Diese Anleitung beschreibt die **einmalige Ersteinrichtung** — sie dient zum Nachvollziehen und für ein frisches Setup. Wichtig: Der Keystore ist **einzige** Signaturquelle für CI, lokal und spätere Stores; ein Wechsel bricht alle bestehenden Installationen (siehe Widerruf-Risiko unten).

**Voraussetzung:** JDK 17 (liefert `keytool`; im CI bereits vorhanden).

**Schritt 1 — Keystore erzeugen** (einmalig, auf einem sicheren Rechner):

```bash
keytool -genkeypair -v   -keystore release.keystore   -alias vivid   -keyalg RSA -keysize 4096   -validity 10000   -sigalg SHA256withRSA   -storepass '<STORE-PASSWORT>'   -keypass '<KEY-PASSWORT>'   -dname "CN=Vivid, OU=Mobile, O=Vivid, L=Berlin, S=Berlin, C=DE"
```

Empfehlungen:
- **`-keysize 4096`** + **`-sigalg SHA256withRSA`** als Minimum; `-validity 10000` Tage ≈ 27 Jahre (Google Play verlangt Schlüssel, die mindestens bis nach 2033 gültig sind)
- `storepass` und `keypass` **identisch** setzen (ein starkes Passwort, mind. 20 Zeichen, Zufallsgenerator — z. B. `openssl rand -base64 32`): modernes `keytool` (JDK 9+) erzeugt standardmäßig **PKCS12**-Keystores, in denen `-keypass` ignoriert wird (der Schlüssel ist mit dem Store-Passwort geschützt) — unterschiedliche Passwörter lassen den CI-Signatur-Check mit „bad key during decryption“ scheitern. Wer bewusst getrennte Key-Passwörter will, muss die Datei explizit als JKS anlegen: `-storetype jks`.
- Der Alias (`vivid`) wird dauerhaft als `KEY_ALIAS` hinterlegt — späteres Umbenennen bricht die Signierung

**Schritt 2 — Keystore base64-kodieren**

Linux / Git-Bash:

```bash
base64 -w 0 release.keystore > release.keystore.b64
```

macOS: `base64 -b 0 < release.keystore > release.keystore.b64` · Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Content release.keystore.b64 -NoNewline`

Die `.b64`-Datei ist **eine einzige lange Zeile** — genau dieser Wert wird `KEYSTORE_BASE64`. (Die Datei ist via `.gitignore` (`*.b64`) abgesichert — niemals committen.)

**Schritt 3 — Secrets in GitHub hinterlegen**

Repo → **Settings → Secrets and variables → Actions → New repository secret**, viermal:

| Secret | Wert |
|--------|------|
| `KEYSTORE_BASE64` | die base64-Zeile aus Schritt 2 (eine Zeile, ohne Zeilenumbrüche) |
| `KEYSTORE_PASSWORD` | das Store-Passwort |
| `KEY_ALIAS` | der Alias (`vivid`) |
| `KEY_PASSWORD` | das Key-Passwort |

> ⚠️ Nach dem Anlegen ist ein Secret-Wert **nie wieder sichtbar** (nur ersetzbar) — Wert vorher in die Backups übernehmen. GitHub speichert Secrets verschlüsselt und maskiert sie in Logs.

**Schritt 4 — Verifizieren**

1. Lokal den Fingerprint ermitteln und notieren:
   ```bash
   echo "$KEYSTORE_BASE64" | base64 -di > /tmp/release.keystore
   keytool -list -v -keystore /tmp/release.keystore -storepass '<STORE-PASSWORT>' | grep -A1 'SHA256'
   ```
2. `bash scripts/guard_secrets.sh` läuft ohne Verstoß (kein Klartext-Secret im Repo), dann einen nightly-Push (oder `workflow_dispatch`) triggern — der **Signatur-Check** im `verify-reproducibility`-Job vergleicht den APK-Signer automatisch gegen den Keystore und muss denselben Fingerprint zeigen (`✅ Signatur verifiziert`).

**💾 Backup-Pflicht (nicht optional)**

Der Keystore ist **die einzige Möglichkeit, Updates zu signieren**. Geht er verloren, können **alle installierten Geräte nie wieder aktualisiert werden** (Signatur-Mismatch → „App not installed“). Bei GitHub-/Obtainium-Installationen gibt es **keinen Key-Rollover** wie bei Google Play — ein verlorener Keystore ist endgültig.

- Mindestens **3 Kopien an getrennten Orten**: verschlüsselter USB-Stick, Passwort-Manager (Bitwarden/KeePass), ausgedrucktes Papier im Safe
- **Backup testen**: jede Kopie dekodieren und den Fingerprint mit dem Original vergleichen (Schritt 4.1)
- Passwörter gehören in den Passwort-Manager, **nicht** in Chats oder E-Mails

**🔄 Recovery aus `KEYSTORE_BASE64`**: Solange das GitHub-Secret existiert, ist der Keystore nicht verloren — `echo "$KEYSTORE_BASE64" | base64 -di > release.keystore` rekonstruiert ihn exakt (danach Fingerprint prüfen!).

**⚠️ Widerruf-Risiko (Kompromittierung)**

Ein APK-Signaturschlüssel ist **unwiderruflich mit der App-Identität verbunden** — leaken Keystore **oder** Passwörter (Repo-Breach, Secret-Exposure), kann ein Angreifer **eigene APKs mit eurer Signatur** bauen; diese installieren sich **über** die bestehende App (gleiche Signatur ⇒ Android akzeptiert das „Update“):

1. **Sofort**: Das kompromittierte GitHub-Secret ersetzen (neuer Name → Workflows anpassen → altes Secret löschen)
2. **Keystore selbst kompromittiert?** Neuen Keystore erzeugen (Schritte 1–3) und als neues Signing aktivieren — **bewusst** als Migrations-Release planen, denn ein Signatur-Wechsel erfordert auf allen Geräten **Neuinstallation** (App-Daten gehen verloren)
3. **Vor dem ersten öffentlichen Release** ist ein Neu-Erzeugen gefahrlos (einfach die 4 Secrets überschreiben); **danach** ist der Key praktisch unersetzbar — **Verlust** bedeutet keine Updates mehr, **Leak** erlaubt Update-Hijacking. Bei Leak-Verdacht: **vor** dem nächsten öffentlichen Release einen neuen Key erzeugen, danach nur noch mit Nutzer-Kommunikation
4. Später in der Play Console: der **Upload-Key** kann über „Play App Signing“ gewechselt werden; der **App-Signing-Key** bleibt der Keystore-Key (Einrichtung Schritt für Schritt: siehe Abschnitt **„Google Play App Signing“** unten)
5. Vorsorge: `KEYSTORE_BASE64` nur für die Actions-Berechtigung freigeben — wer das Secret lesen kann, kann mit eurer Identität signieren

### 🛡️ Google Play App Signing (Play-Kanal, später einrichten)

Dieser Abschnitt beschreibt, **wie** der Play-Kanal mit einem Upload-Key eingerichtet wird — relevant ab dem `beta`/`stable`-Meilenstein (siehe Stage Gates). Vivid wird eine **neue** Play-App sein und damit automatisch in Play App Signing eingeschrieben („new app“-Variante der Google-Doku).

**Prinzip — zwei Schlüssel:**

| Schlüssel | Wer hält ihn | Zweck |
|-----------|--------------|-------|
| **Upload-Key** | du (`.jks`/`.keystore`, RSA ≥ 2048) | signiert das hochgeladene AAB; Google verifiziert damit deine Identität. **Verlust/Leak ⇒ in der Play Console zurücksetzbar** („Request upload key reset“) |
| **App-Signing-Key** | Google (öffentliches Zertifikat `.pem`/`.der`, RSA 4096) | signiert die finalen APKs für die Geräte. **Kann nicht zurückgesetzt werden** — Google verwahrt ihn in KMS |

> **Konsequenz für Vivid:** Die **finalen Play-APKs** tragen die Google-Signatur, die **GitHub-/Obtainium-APKs** den hiesigen Release-Key — das sind **unterschiedliche Signaturen**. Ein Nutzer, der über Play installiert, kann nicht per Obtainium updaten (und umgekehrt), ohne die App neu zu installieren. Wenn **eine** Identität über alle Kanäle gewünscht ist, siehe „Variante B“ unten.

### 🔐 Play-Upload-Keystore erzeugen & UPLOAD_*-Secrets hinterlegen (Einrichtung)

> Dieser Keystore ist **getrennt** vom Release-Key (GitHub/Obtainium): Er signiert ausschließlich die Play-AABs. Verlust ist unkritischer als beim Release-Key — der Upload-Key kann in der Play Console zurückgesetzt werden („Request upload key reset“, ohne App-Ausfall). Trotzdem gilt: Backup-Pflicht wie beim Release-Key (siehe unten).

**Voraussetzung:** JDK 17 (liefert `keytool`; im CI bereits vorhanden).

**Schritt 1 — Upload-Keystore erzeugen** (einmalig, auf einem sicheren Rechner):

```bash
keytool -genkeypair -v   -keystore upload-keystore.jks   -alias upload   -keyalg RSA -keysize 4096   -validity 10000   -sigalg SHA256withRSA   -storepass '<UPLOAD-STORE-PASSWORT>'   -keypass '<UPLOAD-KEY-PASSWORT>'   -dname "CN=Vivid Play Upload, O=Vivid, C=DE"
```

Empfehlungen wie beim Release-Key: `-keysize 4096` + `-sigalg SHA256withRSA`; `storepass` und `keypass` **identisch** setzen (ein starkes Passwort, mind. 20 Zeichen — PKCS12-Default von keytool ignoriert `-keypass`, siehe Release-Key-Guide; getrennte Key-Passwörter nur mit `-storetype jks`); der Alias (`upload`) wird dauerhaft als `UPLOAD_KEY_ALIAS` hinterlegt — späteres Umbenennen bricht die Signierung.

**Schritt 2 — Keystore base64-kodieren**

Linux / Git-Bash:

```bash
base64 -w 0 upload-keystore.jks > upload-keystore.jks.b64
```

macOS: `base64 -b 0 < upload-keystore.jks > upload-keystore.jks.b64` · Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload-keystore.jks")) | Set-Content upload-keystore.jks.b64 -NoNewline`

Die `.b64`-Datei ist **eine einzige lange Zeile** — genau dieser Wert wird `UPLOAD_KEYSTORE_BASE64`. (Die Datei ist via `.gitignore` (`*.b64`) abgesichert — niemals committen; zusätzlich blockt der Secret-Guard `upload-keystore.jks`, `upload_cert.pem` und `fastlane/.env.play`.)

**Schritt 3 — Secrets in GitHub hinterlegen**

Repo → **Settings → Secrets and variables → Actions → New repository secret**, viermal:

| Secret | Wert |
|--------|------|
| `UPLOAD_KEYSTORE_BASE64` | die base64-Zeile aus Schritt 2 (eine Zeile, ohne Zeilenumbrüche) |
| `UPLOAD_KEYSTORE_PASSWORD` | das Store-Passwort des Upload-Keystores |
| `UPLOAD_KEY_ALIAS` | der Alias (`upload`) |
| `UPLOAD_KEY_PASSWORD` | das Key-Passwort |

> Für den eigentlichen Upload braucht die `publish_play`-Lane zusätzlich die **Play-Service-Account-Credentials**: `PLAY_JSON_KEY_FILE` (Pfad zur JSON-Key-Datei) **oder** `PLAY_JSON_KEY_DATA` (JSON-Inhalt) — Anleitung in der Play Console (Setup → API-Zugang → Google Play Developer API → Service-Konto → JSON-Key). Beides sind Secrets und gehören **nie** ins Repo (Guard: `play-credentials.json` / `fastlane/.env.play`).

**Schritt 4 — Verifizieren**

1. Lokal den Fingerprint ermitteln und notieren:
   ```bash
   echo "$UPLOAD_KEYSTORE_BASE64" | base64 -di > /tmp/upload-keystore.jks
   keytool -list -v -keystore /tmp/upload-keystore.jks -storepass '<UPLOAD-STORE-PASSWORT>' | grep -A1 'SHA256'
   ```
2. `bash scripts/guard_secrets.sh` läuft ohne Verstoß (kein Klartext-Secret im Repo), dann `publish-play` per `workflow_dispatch` triggern — die Lane bricht laut ab, wenn der AAB-Fingerprint nicht dem Upload-Key entspricht (`✅ AAB signature verified against UPLOAD key`).

**💾 Backup-Pflicht (nicht optional):** Wie beim Release-Key mindestens **3 Kopien an getrennten Orten** (verschlüsselter USB-Stick, Passwort-Manager, Safe) und jede Kopie testweise dekodieren + Fingerprint prüfen (Schritt 4.1). Anders als der Release-Key ist der Upload-Key bei Verlust **ersetzbar** — aber nur mit etwas Aufwand in der Play Console, deshalb nicht leichtfertig behandeln.

**🔄 Recovery:** Keystore jederzeit aus `UPLOAD_KEYSTORE_BASE64` rekonstruierbar (`echo "$UPLOAD_KEYSTORE_BASE64" | base64 -di > upload-keystore.jks`). Bei Kompromittierung: neuen Key erzeugen (Schritt 1), PEM exportieren (`keytool -export -rfc … -file upload_cert.pem`) und in der Play Console zurücksetzen.

**⚠️ Widerruf-Risiko:** Leak des Upload-Keystores **oder** der Passwörter erlaubt es, eigene AABs mit eurer Upload-Identität in Play hochzuladen (Release-Hijacking). Sofort handeln: Secret ersetzen + „Request upload key reset“. **Strikt vom Release-Key trennen** — eigene Secrets, nie `KEYSTORE_*` wiederverwenden; nur so bleiben die beiden Kanäle (Play vs. GitHub/Obtainium) unabhängig und ein Play-Vorfall trifft nicht den Release-Key.

**Variante A — dedizierter Upload-Key (Google-Empfehlung, getrennte Keys)**

**Schritt 1 — Upload-Key erzeugen** (einmalig; vollständige Anleitung inkl. base64-Kodierung, GitHub-Secrets und Verifikation siehe Abschnitt **„🔐 Play-Upload-Keystore erzeugen & UPLOAD_*-Secrets hinterlegen“** oben):

```bash
keytool -genkeypair -v   -keystore upload-keystore.jks   -alias upload   -keyalg RSA -keysize 4096   -validity 10000   -sigalg SHA256withRSA   -storepass '<UPLOAD-STORE-PASSWORT>'   -keypass '<UPLOAD-KEY-PASSWORT>'   -dname "CN=Vivid Play Upload, O=Vivid, C=DE"
```

**Schritt 2 — Peer-Zertifikat (öffentliches Zertifikat) exportieren:**

```bash
keytool -export -rfc -keystore upload-keystore.jks -alias upload -file upload_cert.pem
```

Das `upload_cert.pem` enthält **nur das öffentliche Zertifikat** (keinen privaten Schlüssel) — genau diese Datei wird in der Play Console hochgeladen.

**Schritt 3 — Play Console:**

1. App in der Play Console anlegen → wird automatisch in Play App Signing eingeschrieben (quantum-ready, Google-generierte Keys)
2. **Protected with Play → Play Store protection → Manage Play app signing** (bzw. Release → Setup → App integrity → App signing)
3. Unter **Upload key certificate** das `upload_cert.pem` hochladen (bzw. „Export and upload your upload key“)
4. **App signing key: Google-generiert lassen** (RSA 4096) — **nicht** den Vivid-Release-Key dorthin legen (sonst verliert der GitHub-/Obtainium-Kanal die Unabhängigkeit)

**Schritt 4 — AAB mit dem Upload-Key signieren & hochladen (CI):**

Die Fastlane-/CI-Seite braucht eine **zweite, getrennte** Signing-Konfiguration — nie dieselben Secrets wie der Release-Key. In `app/build.gradle.kts`:

```kotlin
// identisch zum bestehenden release-Block (app/build.gradle.kts), nur UPLOAD_*-Env
android {
    signingConfigs {
        create("upload") {
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_PATH")
            val keystorePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
            val keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")

            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
}
```

CI-Secrets (getrennt von den Release-Secrets): `UPLOAD_KEYSTORE_BASE64` / `UPLOAD_KEYSTORE_PASSWORD` / `UPLOAD_KEY_ALIAS` / `UPLOAD_KEY_PASSWORD`. Der `publish-play`-Job (nur per `workflow_dispatch`) dekodiert den Keystore, baut `bundlePlayRelease` mit der Upload-Signing-Config und lädt das AAB über die `publish_play`-Lane hoch.

**Schritt 5 — Verifikation:**

1. Lokal: `keytool -printcert -jarfile app-release.aab` → zeigt das **Upload-Key**-Zertifikat (SHA-256)
2. Play Console (App-signing-Seite): Der **Upload key certificate**-Fingerprint muss mit dem lokalen übereinstimmen; der **App signing key**-Fingerprint ist der von Google generierte
3. **API-Provider** (Firebase, Google Maps, OAuth, …): dort den **App-Signing-Key**-Fingerprint (SHA-1/SHA-256 von Google) registrieren — **nicht** den Upload-Key, denn Google signiert die finalen APKs. Bei Android App Links zusätzlich in `assetlinks.json`

**Variante B — gleicher Key auf allen Kanälen**

Wenn Nutzer zwischen Play und Obtainium wechseln können sollen (eine Identität), wird der **bestehende Release-Key** als App-Signing-Key an Google übertragen („Provide a copy of your app signing key“). Die Play Console liefert auf der Seite das **PEPK-Tool** und den exakten Befehl mit deinem Verschlüsselungs-Key:

```bash
java -jar pepk.jar --keystore=release.keystore --alias=vivid   --output=encrypted.zip --encryption-key=<Play-Console-Public-Key> --include-cert
```

Das `encrypted.zip` (nur für Google lesbar verschlüsselt) wird hochgeladen; Google nutzt den Release-Key als App-Signing-Key. **Danach signieren Play- und GitHub-APKs identisch** → Cross-Kanal-Updates funktionieren. Nachteil: Google hält eine Kopie des Release-Keys — ein Play-seitiger Vorfall betrifft dann auch den GitHub-Kanal (Google empfiehlt deshalb getrennte Keys).

**🔄 Recovery & Reset (Upload-Key verloren/kompromittiert):**

1. Neuen Upload-Key erzeugen (Schritt 1) und PEM exportieren (Schritt 2)
2. Play Console → **Manage Play app signing → Upload Key Certificate → Request upload key reset** → Grund + `upload_cert.pem` hochladen
3. Der Wechsel passiert **ohne App-Ausfall** und ohne Neuinstallationen — anders als beim Release-Key für GitHub/Obtainium


### 🧪 Testplan: Erster Play-Upload (alpha-Track)

Konkreter, ausführbarer Ablauf für den **ersten** Upload in die Play Console (Track `alpha`) — Ziel: ein mit dem Upload-Key signiertes AAB hochladen und als Alpha-Release verifizieren, ohne `beta`/`stable` zu berühren (siehe Stage Gates).

**📋 Checkliste vor dem Start (alles abhaken):**

- [ ] **Play Console:** App ist angelegt und automatisch in Play App Signing eingeschrieben (App-Signing-Key: Google-generiert, Variante A)
- [ ] **Upload-Key:** `upload-keystore.jks` erzeugt und gesichert (Abschnitt „🔐 Play-Upload-Keystore erzeugen …“)
- [ ] **`upload_cert.pem`** in der Play Console als Upload-Key-Zertifikat registriert
- [ ] **Service-Account:** Play Console → Setup → API-Zugang → Google Play Developer API → Service-Konto mit Rolle **„Releasemanager“** → JSON-Key heruntergeladen
- [ ] **Alle vier UPLOAD_*-Secrets** in GitHub (Settings → Secrets and variables → Actions):
      - [ ] `UPLOAD_KEYSTORE_BASE64` — base64-Zeile des Keystores (eine Zeile)
      - [ ] `UPLOAD_KEYSTORE_PASSWORD` — Store-Passwort
      - [ ] `UPLOAD_KEY_ALIAS` — der Alias (`upload`)
      - [ ] `UPLOAD_KEY_PASSWORD` — Key-Passwort
- [ ] **Play-Credentials** (genau eines von beiden):
      - [ ] `PLAY_JSON_KEY_FILE` — Pfad zur Service-Account-JSON **oder**
      - [ ] `PLAY_JSON_KEY_DATA` — JSON-Inhalt (beides nie leer, nie ins Repo)
- [ ] **Kein Leak:** `bash scripts/guard_secrets.sh` → `✅ [guard] Keine Keystores oder Klartext-Secrets gefunden.`

**🎯 Ablauf (Schritt für Schritt):**

**Schritt 1 — Secrets-Verfügbarkeit prüfen** (nur Namen, nie Werte):

```bash
gh secret list
# Erwartet: UPLOAD_KEYSTORE_BASE64, UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS,
# UPLOAD_KEY_PASSWORD sowie PLAY_JSON_KEY_FILE ODER PLAY_JSON_KEY_DATA
```

**Schritt 2 — Guard + lokale Gegenprobe des Fingerprints:**

```bash
bash scripts/guard_secrets.sh   # Exit 0
echo "$UPLOAD_KEYSTORE_BASE64" | base64 -di > /tmp/upload-keystore.jks
keytool -list -v -keystore /tmp/upload-keystore.jks -storepass '<UPLOAD-STORE-PASSWORT>' | grep -A1 'SHA256'
# SHA-256-Fingerprint notieren — muss identisch sein mit CI-Log (Step 4/6) und Play Console
```

**Schritt 3 — Play-Upload triggern (alpha-Track):**

```bash
gh workflow run android_fastlane.yml --ref develop   -f track=alpha   -f version=<versionName>   -f version_code=<versionCode>
```

> ⚠️ **versionCode-Regel:** In Play **pro App global eindeutig** — ein hochgeladener Code ist für immer belegt (kann nicht erneut hochgeladen werden). Für den ersten Upload **explizit setzen** (z. B. `1`); ohne `-f version_code` leitet die `publish_play`-Lane ihn aus dem letzten `v*`-Tag ab.

> 💡 **Erst trocken testen:** Mit `-f dry_run=true` baut der Job das AAB und verifiziert die Signatur gegen den Upload-Key, **ohne** etwas hochzuladen — ideal für den ersten Lauf (keine Play-Auswirkung, kein `version_code` verbraucht). Erst wenn Step 4/6 grün ist, den echten Upload (ohne `dry_run`) ausführen.
> 🔁 **Automatischer CI-Selbsttest (ohne Play-Zugang):** Der Job **„Self-Test publish_play (dry_run)“** in `android_fastlane.yml` führt die Lane bei jedem Push/PR mit einem **lokal erzeugten Wegwerf-Keystore** aus (`scripts/test_publish_play_dryrun.sh`): `keytool` erzeugt den Test-Keystore, die Lane baut `bundlePlayRelease` und verifiziert die AAB-Signatur per `keytool` gegen den Test-Key — ganz ohne `UPLOAD_*`-Secrets und ohne Play-Zugang. Zusätzlich erzwingt ein Negativtest, dass `publish_play` **ohne** `dry_run` und ohne `PLAY_JSON_KEY_*` am Credential-Guard scheitert (kein Upload-Pfad ohne Play-Zugang). Damit ist die Lane dauerhaft regressionstestbar, **bevor** die echten Secrets existieren. Lokal jederzeit wiederholbar: `bash scripts/test_publish_play_dryrun.sh`.

**Schritt 4 — CI-Lauf beobachten** (`gh run watch <run-id>`), erwartete Reihenfolge im `publish-play`-Job:

| Fastfile-Step | Erwartung |
|---|---|
| Step 1/6 Credential-Checks | `UPLOAD_KEYSTORE_*` + Play-Credentials vorhanden → weiter |
| Step 2/6 Version | `Play upload: <version> (<code>) -> track alpha` |
| Step 3/6 Build | `bundlePlayRelease` erfolgreich; AAB-Pfad + Größe geloggt |
| Step 4/6 Signatur | `AAB signer SHA-256` == `Upload key SHA-256` → `✅ AAB signature verified against UPLOAD key` |
| Step 5/6 Upload | `supply` meldet Erfolg (kein 401/403) |
| Step 6/6 | `✅ Uploaded <version> to Play track alpha` |

Fehlerbilder (bewusst **harte Abbrüche** — bei CI-Fail hat Play keinerlei Änderung):
- `UPLOAD_KEYSTORE_BASE64 fehlt` → Decode-Step bricht ab (Secret fehlt/Name falsch)
- `Signature mismatch` → AAB wurde nicht mit dem Upload-Key signiert (falsches Secret/Keystore) → **nichts** hochgeladen
- `Authentication failed`/`403` im supply-Step → Service-Account fehlt, falsche Rolle („Releasemanager“) oder falscher Paketname (`applicationId`)

**Schritt 5 — Play Console verifizieren:**

1. Play Console → **Release-Übersicht (Alpha)** → neuer Release vorhanden, Status „Ready to roll out“ (bzw. „In review“ durch App-Prüfung)
2. **Setup → App-Integrität → App-Signierung**: „Upload key certificate“-Fingerprint == notierter Wert (Schritt 2); „App signing key“ == Google-generiert (Variante A)
3. **Nicht** „Go live“ drücken — der Testrelease bleibt im Alpha-Kanal

**Schritt 6 — Nachbereitung:**

- [ ] Play-alpha-Link an einen Tester schicken → Installation + Smoke-Test des Builds
- [ ] Alpha-Testrelease **verwerfen** oder als Basis für `beta` weiterverwenden (bewusste Entscheidung, siehe Stage Gate `alpha`)
- [ ] Nutzt die App APIs (Firebase/Maps/OAuth): den **App-Signing-Key**-Fingerprint aus der Play Console bei den Anbietern hinterlegen — **nicht** den Upload-Key (Google signiert die finalen APKs)

**⛔ Abbruch/Rollback:** Bis zur Veröffentlichung („Go live“) ist jeder Upload in der Play Console **verwerfbar** — ein falscher Testrelease lässt sich ohne Auswirkung löschen. Ein CI-Fehlschlag (Secrets/Signatur) berührt Play nie, weil die Lane **vor** dem Upload hart abbricht. Einzige dauerhafte Folge: ein vergebener `version_code` ist in Play belegt → nächster Versuch braucht einen neuen Code.



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
- [ ] ≥50 % Feature-Parität (aktuell 9/34 ≈ 26 %, Ziel: ≥17 Features)
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

## 🩺 Pipeline-Historie & Run-Cleanup

### Störfall 2026-08-11: `workflows: write` legte die Pipeline lahm

| Zeit (UTC) | Commit | Run | Ergebnis |
|------------|--------|-----|----------|
| 04:10 | `89e0e0e` | `31457612856` | ❌ Tag-Push-Race („refusing to allow a GitHub App to create or update workflow … without workflows permission“) — Auslöser der Kette |
| 04:14 | `f94f2a9` | `31457872240` | ✅ success (nightly.93) |
| 04:34 | `5e3e81a` | ~~`31458910287`~~ | ❌ **0s-Validierungsfehler** („This run likely failed because of a workflow file issue“) — `workflows: write` eingeführt, **Run gelöscht** |
| 05:10 | `87c6562` | ~~`31460799068`~~ | ❌ 0s-Validierungsfehler (gleiche Ursache), **Run gelöscht** |
| 10:58 | `c4857e7` | ~~`31484583065`~~ | ❌ 0s-Validierungsfehler (gleiche Ursache), **Run gelöscht** |
| 11:12 | `2456457` | `31485577814` | ✅ success (nightly.97) — Fix: `workflows: write` entfernt, nightly-Tag zeigt auf `origin/develop` |

**Auswirkung:** Zwischen 04:34 und 11:12 UTC wurden **keine Nightlies veröffentlicht** (Run-Nummern 94–96 existieren nicht; Sprung 93 → 97). Die drei 0s-Failures enthalten keinerlei Jobs oder Logs und wurden per `gh run delete` entfernt. Der `89e0e0e`-Lauf blieb bewusst erhalten — sein Log dokumentiert den ursprünglichen Tag-Push-Fehler.

**Lehre (wichtig!):** Push-getriggerte Workflows dürfen dem `GITHUB_TOKEN` **niemals `workflows: write`** geben — GitHub lehnt solche Runs bei der Validierung ab (Schutz vor Token-Eskalation). Tag-Pushes auf Commits, deren Baum sich in `.github/workflows` von `develop` unterscheidet, brauchen diese Berechtigung daher nicht: Der nightly-Tag zeigt stattdessen immer auf den frisch gefetchten `origin/develop` (siehe `Fastfile`, `publish_release`-Lane) — so entsteht nie ein Workflow-Diff.

### Aufräumen fehlgeschlagener Runs

```sh
# Run-IDs ermitteln (nur die gewünschte Workflow-Datei)
gh run list --workflow=android_fastlane.yml --limit 20

# Einzelnen Run löschen (irreversibel — Logs & Artefakte gehen verloren)
gh run delete <run-id>
```

> **Pflege:** Dieses Dokument bei jeder Änderung an PARITY.md prüfen — wenn ein Gate erreicht ist, die entsprechende Lane implementieren und taggen.
