# Vivid v0.5.14

**Veröffentlicht:** 10. September 2026

Erstes Stable-Release nach dem M1-Abschluss: promoted aus v0.5.14-beta (09.09.2026) mit fünf Nutzer-Features aus den offenen Moblin-Gap-Punkten — **Replay-Aufnahme mit Audio-Konfiguration und Thumbnails, Hype-Train-Anzeige, Text-Widget-Template-Editor mit Geocoding-Variablen und Replay als Szenen-Quelle** — plus Bugfixes, Infrastruktur-Härtung und dem Kotlin 2.4.20-Stabil-Update (Schließung von Dependabot-Alert #63).

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, 4K/HEVC, SRTLA-Bonding, adaptive Bitrate etc.) reserviert. Diese Ausbaustufe wurde als Patch in der laufenden `0.5.x`-Linie ausgeliefert — nicht als Feature-Beta `v0.6.0-beta`. Siehe [RELEASE.md](../RELEASE.md) → Roadmap → Nummerierung.

## ✨ Neue Features

### Replays (PARITY-Row „Replays" — vollständig ✅)
- **Replay-Aufnahme als MP4 parallel zum Stream** (Record-to-Disk) mit Aufbewahrungsgrenze
- **Audio-Konfiguration**: Aufnahme mit Ton oder nur Bild (`ReplayAudioMode`), umschaltbar in den Streaming-Einstellungen
- **Thumbnail-Vorschauen** in der Replay-Bibliothek (automatisch aus einem Frame der Aufnahme erzeugt)
- **Replay-Bibliothek**: Liste, Inline-Wiedergabe, Löschen (einzeln/alles) und Teilen über das System-Share-Sheet
- **Replay als Szenen-Quelle**: eine Aufnahme kann als Stream-Videoquelle in **Dauerschleife** gestreamt werden („Als Stream-Quelle verwenden" in der Bibliothek); `StreamScene` speichert die Replay-Datei dauerhaft — Szene anwenden, und das Replay läuft als Quelle

### Twitch Hype Train (EventSub)
- **Hype-Train-Banner im Chat-Overlay**: Level + Fortschritt, live aktualisiert (begin/progress/end über `channel.hype_train.*`)
- Umschaltbar in den Chat-Overlay-Einstellungen; Test-Auslösung per `!testalert hype`

### Text-Widget-Variablen (komplett)
- **Template-Editor in den Einstellungen** (Overlays & Widgets): mehrzeiliges Eingabefeld mit Variable-Chips und Live-Vorschau
- **Neue Geocoding-Variablen** `{road}`, `{city}`, `{country}`: Reverse-Geocoding über den Android-Geocoder mit TTL- und Distanz-Cache (10 min / 500 m) — Geocoding läuft nur, wenn das Template die Variablen auch nutzt

## 🐛 Bugfixes
- **Replay-Bibliothek**: `MediaMetadataRetriever` (Thumbnail-Erzeugung) wurde per `use {}` als `AutoCloseable` behandelt — das existiert erst ab API 29 (minSdk 24) und konnte auf älteren Geräten crashen. Behoben per `try/finally` mit `release()`
- **Layout-Crash #164 (untersucht):** Der gemeldete *„Vertically scrollable component was measured with an infinity maximum height constraints“*-Crash ließ sich im ausgelieferten Stand (Mapping der rückdatierten Builds) nicht reproduzieren. Die verdächtigen Screens sind jetzt durch Regressionstests abgesichert: About-Screen mit verschachtelter Scroll-Container + **sehr langen** Update-Release-Notes, und Replay-Bibliothek mit **500 Einträgen** in der LazyColumn
- Kotlin-Compile-Warnung in der Hype-Train-End-Anzeige behoben (PluralsCandidate-Lint in de/en)
- Fehlender Konstruktor-Parameter in Replay-Bibliothek-Tests (folgte dem Audio/Thumbnail-Umbau)

## 📊 Statistik
- **Commits seit v0.5.13-beta:** 49
- **Neue Tests:** ReplayVideoSource (12), SceneController/StreamingViewModel/ReplayLibrary erweitert, Robolectric-UI-Tests für Bibliothek + Bestätigungsdialog; Geocoder-/Cache-/VM-Tests für die Geocoding-Variablen; Hype-Train-Reader/ViewModel-Tests; **Regressionstests Crash #164** (About-Update-Notes, Replay-Liste mit 500 Einträgen); Pipeline-Selbsttests für Distribution/Checksummen/F-Droid-Metadata (D/H/M-Suiten, laufen in pre-push + CI); **Kotlin-Sync-Guard** (`test_kotlin_sync.sh` K1–K5: Katalog-Synchronität kotlin==jetbrainsKotlinJvm)
- **PARITY-Status:** „Replays" **vollständig ✅**, „Text-Widget-Variablen" **vollständig ✅**, Twitch-Hype-Train ergänzt (Twitch-Integration weiter ausgebaut)

## 🛠️ Release- & Infra-Prozess (sichtbar für Tester)
- **Wöchentliche Stable-Distribution**: Workflow `distribution-stable.yml` (Mo 03:00 UTC + manuell auslösbar) veröffentlicht Stable-Releases mit **beiden Flavor-APKs** (`app-standard-release.apk` **und** dem Sentry-freien `app-foss-release.apk`) plus **`SHA256SUMS.txt`**-Checksummen — Completeness-geschützt und idempotent (ein schon vollständiges Release wird nicht überschrieben)
- **F-Droid-Pflege-Metadata** liegt committed im Repo (`fdroid/config-fdroid-main.yml` + `fdroid/metadata/com.vivid.foss.yml`) — Einreichung für f-droid.org/IzzyOnDroid ist vorbereitet, das eigene F-Droid-Repo läuft weiter auf GitHub Pages (wöchentlich, Mo 04:00 UTC)
- **fdroidserver-Requirements-Closure** vollständig SHA-256-hash-gepinnt (Scorecard PinnedDependencies, inkl. transitive Abhängigkeiten)
- **Doku-Guards**: Bot-Befehls-Katalog als Single Source of Truth (In-App-Hilfe, Handbücher DE/EN/FR und Wiki generieren daraus), Wiki-Sync generiert mehrsprachige Wiki-Seiten
- **Kotlin 2.4.20 (stabil)**: `kotlin` + `jetbrainsKotlinJvm` auf 2.4.20 angehoben (unsafe Deserialization im Kotlin Build Cache, Dependabot-Alert #63 geschlossen; Blocker codeql#22404 seit 08.09. erledigt, KSP bleibt 2.3.11). Neuer **Kotlin-Sync-Guard** (`check_kotlin_sync.sh` + `test_kotlin_sync.sh`, K1–K5) verhindert Drift der beiden Versions-Keys — verdrahtet im Pre-Push-Gate und `android-ci.yml`
- **CodeQL-Kotlin-Wächter**: wöchentlicher Status-Check (`check_codeql_kotlin_support.sh`) — meldet sich nach dem Bump als überflüssig; kann in einer späteren Aufräumrunde samt `automation-codeql-kotlin.yml` entfernt werden
- **Security-Loop-Guard** in der Release-Pipeline (Keystore-Härtung, Signatur-Checks, Reproduzierbarkeits-Vergleich, Sentry-Opt-out-Nachweis)
