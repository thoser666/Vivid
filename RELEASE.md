# 🚀 Release Pipeline

Jeder Release durchläuft eine von vier Stufen. Welche Stufe aktiv ist, bestimmt der Stand im [PARITY.md](PARITY.md)-Feature-Tracker.

## Stufenübersicht

| Stufe | Tag-Muster | Auslöser | Zielgruppe |
|-------|-----------|----------|------------|
| `nightly` | `nightly` (rollierend) | Jeder develop-Merge · **täglich 06:00 UTC (Schedule)** · manuell (`workflow_dispatch`) | Entwickler · CI-Tester |
| `alpha` | `vX.Y.Z-alpha` (Patch-Alphas möglich, z. B. `v0.4.2-alpha`) | Manuell via `fastlane release_alpha` | Frühe Tester (Obtainium, kein Pre-Release-Flag nötig) |
| `beta` | `vX.Y.Z-beta` (Patch-Betas möglich, z. B. `v0.5.1-beta`) | Manuell via `fastlane release_beta` (Spiegel von `release_alpha` inkl. Safety-Checks) | Feldtester · Hunde essen ihr eigenes Futter |
| `stable` | `vX.Y.Z` | Manuell via `fastlane release_stable` (TODO) | Play Store · F-Droid · Allgemeinverfügbarkeit |

## 🧭 Beta-Release-Strategie (wann wird ein Beta-Tag gesetzt?)

**Leitfrage:** Gibt es seit dem letzten Beta-Tag **user-facing** Änderungen, die Tester sehen sollen? Nur Doku-/CI-Arbeit rechtfertigt keine neue Beta — die wartet auf das nächste Feature-Bucket (Roadmap-Tabelle unten).

### Patch- vs. Feature-Beta

| Typ | Tag | Wann | Beispiel |
|-----|-----|------|----------|
| **Patch-Beta** | `vX.Y.Z-beta` (Z ≥ 1) | Kleine, fokussierte Änderungen **ohne** neue Features: Privacy-/Verhaltensänderungen, Bugfixes, relevante Dependency-Bumps | `v0.5.1-beta` (18.08.2026): Sentry-Privacy-Härtung + okhttp-Bump |
| **Feature-Beta** | `vX.Y.0-beta` | Ein neues Roadmap-Bucket ist implementiert (siehe Roadmap-Tabelle) | `v0.6.0-beta` = Streaming-Erweiterung (RIST/WHIP/RTMP) |

### Regeln für den Tag

1. **Immer den vollen Tag inkl. Stufen-Suffix übergeben** — die Lane hängt das Suffix nur im Auto-Ableitungs-Pfad an:
   ```
   bundle exec fastlane release_beta version:v0.5.1-beta
   ```
   Ein explizites `version:0.5.1` **ohne** `-beta` erzeugt den **stabilen** Tag `v0.5.1` (versionCode 5014). Passiert am 18.08.2026 — der CI-Run wurde gecancelt und der Tag gelöscht, bevor etwas publiziert wurde. **Nie ohne Suffix aufrufen.**

2. **⚠️ Automatik-Fallstrick `v0.6.0-beta`:** `release_beta` **ohne** `version:` leitet den nächsten Tag als `minor+1` vom höchsten `v*-beta` ab — `v0.5.0-beta` → **`v0.6.0-beta`**. Diesen Namen reserviert die Roadmap aber für das Streaming-Bucket (RIST/WHIP/RTMP), das noch **nicht** implementiert ist. Deshalb: **Patch-Betas immer explizit** mit `version:vX.Y.Z-beta` übergeben.

3. **Gate vor dem Tag:** Änderungen sind grün in Tests + CI (Pre-Push-Gate · Fastlane-CI inkl. Signatur-/Reproduzierbarkeits-Checks) · Release-Notes liegen in `docs/release-notes-vX.Y.Z-beta.md` committet vor · keine bekannten Blocker.

4. **versionCode:** deterministisch aus dem Tag (Schema unten) — `v0.5.1-beta` = 5012, monoton über dem letzten Beta (5002). Liegt der Patch-Beta-Code unter dem letzten Alpha (z. B. 5012 < 6001 von `v0.6.0-alpha`), löst die Lane nur eine **Quer-Track-Warnung** aus (Alpha-Nutzer müssen vorher deinstallieren), kein Abbruch.

### Alpha-Strategie (analog — gleiche Fallstricke)

**Leitfrage:** Gibt es seit dem letzten Alpha **user-facing** Änderungen? Das Alpha ist die frühe Teststufe (Obtainium, kein Pre-Release-Flag) — hier darf die Frequenz höher sein als bei Beta/Stable, aber reine Doku-/CI-Arbeit rechtfertigt auch hier keinen neuen Tag.

| Typ | Tag | Wann | Beispiel |
|-----|-----|------|----------|
| **Patch-Alpha** | `vX.Y.Z-alpha` (Z ≥ 1) | Kleine, fokussierte Änderungen ohne neue Features | `v0.4.1-alpha`, `v0.4.2-alpha` (Bugfix-/Scroll-Releases) |
| **Feature-Alpha** | `vX.Y.0-alpha` | Neues Roadmap-Bucket | `v0.5.0-alpha` (Chat-Bot), `v0.6.0-alpha` (IRC-Ausstieg + Owner-Steuerung) |

`release_alpha` hat **denselben Automatik-Fallstrick** wie `release_beta` (minor+1-Ableitung) und **dieselbe Suffix-Falle** — plus eine Besonderheit:

1. **Suffix-Pflicht:** `bundle exec fastlane release_alpha version:v0.5.1-alpha` — ein explizites `version:0.5.1` **ohne** `-alpha` erzeugt den stabilen Tag `v0.5.1` (identischer Vorfall wie bei Beta am 18.08.2026). **Nie ohne Suffix aufrufen.**

2. **⚠️ Automatik-Fallstrick `minor+1`:** `release_alpha` **ohne** `version:` leitet `v0.6.0-alpha` → **`v0.7.0-alpha`** ab — dabei wird die Roadmap-Nummerierung (Feature-Buckets) übersprungen bzw. die falsche Stufe getroffen. Feature- und Patch-Alphas deshalb **immer explizit** mit `version:vX.Y.Z-alpha` übergeben.

3. **versionCode:** deterministisch aus dem Tag — `v0.5.1-alpha` = 5011. **⚠️ Besonderheit:** `release_alpha` hat (anders als `release_beta`) **keine lokalen Safety-Checks** — keine versionCode-Monotonie-Prüfung, keine Quer-Track-Warnung. Die Monotonie (neuer Code > letzter veröffentlichter) muss vor dem Tag **manuell** geprüft werden.

## 🗺️ Roadmap (Version → Features)

Welche offenen PARITY-Punkte in welcher Version released werden — Zählerstand: ✅ **17** (Stand 2026-08-16, [PARITY.md](PARITY.md)). Reihenfolge nach dem Prinzip: **erst Gate-Pflichten, dann Nutzer-Sichtbares, dann Streaming-Komfort, zuletzt Protokoll-Ausbau & Plattform** — jedes Release bleibt für sich testbar und läuft über die jeweilige Fastlane-Lane.

| Version | Inhalt (PARITY-Punkte) | ✅ nach Release | Gate |
|---------|------------------------|-----------------|------|
| `v0.4.x-alpha` | (läuft bereits — aktuell `v0.4.2-alpha`, enthält den Scroll-Fix) | 17 | alpha-Gate aktiv |
| `v0.5.0-beta` | **Chat & Moderation (4):** Plattform-Chat, Emotes, Moderation/Chat-Bot/TTS, Media-Player-Bot · **erstes Overlay:** Text-/Info-Widgets (Sensor-/GPS-Daten) · **Vivid-Zusatz:** KI-Chat-Bot (LLM-Antworten, bereits in alpha implementiert) | **21** | **Beta-Gate 3/3 erreicht** (≥17 ✅ · Chat-✅ Twitch-Scope · ≥1 Widget ✅) — offen nur noch: Play-Unterlagen + ≥2 Tester · versionCode 5002 (über 0.5.0-alpha/5001) |
| `v0.4.0-beta` | **Overlays & Widgets (5):** Chat-Overlay/Event-Alerts, Text-Widget-Variablen, Karten-Widget, Browser-Widget, Scoreboards | **26** | Beta #2 |
| `v0.5.0-beta` | **Kamera & Video (4):** Color-Spaces/3D-LUTs, Video-Effekte, Externes Zubehör, Photo-Shoot · **Audio (3):** Mic-Verwaltung, Level-Meter/Muting/Sync, Talk-Back · Oura-Ring-Widget | **34** | Beta #3 |
| `v0.6.0-beta` | **Streaming-Erweiterung (5):** RIST, WHIP, RTMP-Pull/Ingest, 4K/HEVC, SRTLA-Bonding · OBS Snapshot/Audio-Levels · Game-Controller, Deep-Linking/Konfig-Import | **42** | Beta #4 |
| `v1.0.0-stable` | I18n (🚧) + Play-Store-Unterlagen (Icon, Screenshots, Listing) | **43 (100 %)** | Stable-Gate (≥90 %) |

> **Pflege:** Bei jeder Änderung an PARITY.md prüfen — neue Features wandern in den nächsten passenden Versions-Bucket; die Grenzen sind flexibel (ein Feature darf vorziehen, wenn es erst das Gate der nächsten Version schließbar macht).

## 📡 Remote-Steuerung (Post-Beta-Plan)

Zielbild: **Screens und Widgets von außen konfigurieren** — der Streamer bleibt am Gerät, während Kamera/Vorschau laufen, und ändert Settings, Widget-Position/-Felder und Stream-Status über eine zweite Oberfläche („alternative zur Konfiguration innerhalb von Vivid“). Basis ist die **bestehende Web-Remote-Control** (PARITY Row 116): Ktor-LAN-Server auf Port 8080 mit Token-Auth, heute `GET /status` + `POST /start|stop`. Der Plan ist bewusst **Post-Beta** (nach dem ersten `v0.5.0-beta`-Tag), damit die lokale Settings-API erst stabil ist.

**Ist-Zustand (heute):**

| Endpunkt | Auth | Zweck |
|---|---|---|
| `GET /status` | öffentlich | Stream-Status als JSON |
| `POST /start` · `POST /stop` | Bearer-Token | Stream mit gespeicherten Einstellungen starten/stoppen |

> Token liegt in DataStore (`RemoteControlTokenStore`), wird in den Settings angezeigt; Android 17 (`targetSdk 37`) braucht die `ACCESS_LOCAL_NETWORK`-Runtime-Berechtigung (im Settings-Screen erfragbar, Server-Neustart nach Erteilung).

**Option A — Config-Endpunkte + Web-UI (empfohlen, kein zweites APK):**

Der bestehende Server wird um **Konfigurations-Endpunkte** erweitert (gleiche Token-Auth, LAN-only) und liefert eine **schlanke Web-UI als statische Assets** (PWA-fähig, im Browser auf dem Zweitgerät):

| Neuer Endpunkt | Zweck |
|---|---|
| `GET /settings` | komplette Einstellungen als JSON (ohne Secrets — API-Key/Tokens nur maskiert) |
| `PUT /settings` (Teil-Update, JSON-Patch-artig) | z. B. `{ "widgetEnabled": true, "widgetShowSpeed": false }`, `{ "streamUrl": … }` |
| `GET /widgets` | aktuelle Widget-Konfiguration (Position, sichtbare Felder) |
| `PUT /widgets` | Widget-Felder/Position ändern |
| `GET /status` · `POST /start` · `POST /stop` | (existiert bereits) |

- **Single Source of Truth bleibt das lokale DataStore:** Der Server schreibt ausschließlich über `SettingsRepository` (validierte Werte) — kein paralleler Config-Speicher.
- **Web-UI-Scope v1:** Status-Karte, Start/Stop, Settings-Formular (Stream, OBS, Chat, Chat-Bot, Widgets), Widget-Preview-Platzhalter; PWA-fähig (Offline-Startseite, installierbar) — kein Play-Listing, keine zweite Signierung, keine separate Distribution nötig.
- **Aufwand:** klein–mittel (Server-Endpunkte + eine HTML/JS-Seite; die Domain-Logik liegt bereits in `SettingsRepository`).

**Option B — Companion-APK (eigenes Projekt, später):**

Eine native **„vivid-companion“**-App (eigenes Repo/Modul, getrennt versioniert, eigene Signierung + Distribution) nutzt **dieselbe LAN-API** aus Option A und bietet native UI, Live-Vorschau und später Wear-OS. **Wann sinnvoll:** erst wenn die Web-UI an Grenzen stößt (Live-Preview-Latenz, Offline-Use, Haptik) — die API-Stabilität von Option A ist die Voraussetzung, nicht umgekehrt.

**Option C — Wear-OS-Tile (langfristig):** Moblin-Row 119 („Wear-OS-Pendant separat bewerten“) — Start/Stop + Status vom Handgelenk; setzt die stabile Remote-API (Option A/B) voraus.

**Sicherheits-Prinzipien (gelten für alle Optionen):**

1. **LAN-only, kein Internet-Relay** — bewusst kein Fernzugriff über das Internet (siehe PARITY Row 116); ein Relay-Server wäre ein eigener Wunsch außerhalb der Moblin-Parität.
2. **Token-Auth für jeden Schreib-Endpunkt** (Bearer-Token aus DataStore); `GET /status` bleibt öffentlich (nur Status, keine Secrets).
3. **Keine Secrets im Response:** API-Key, OAuth-Token, Stream-Key, Passwörter werden nie als Klartext ausgeliefert (nur maskiert/„gesetzt“-Flag); Schreibzugriffe auf Secret-Felder nur mit neuem Wert.
4. **Validierung serverseitig** (gleiche Regeln wie der Settings-Screen: numerische Felder, URL-Formate) — fehlerhafte Puts lehnt der Server ab, bevor DataStore berührt wird.
5. **Änderungen sofort wirksam** (Flows in `SettingsRepository`), Konflikte mit gleichzeitiger In-App-Bearbeitung: Letzter-Schreiber-gewinnt (bewusst einfach; ein Lock wäre Over-Engineering fürs LAN).

**Reihenfolge im Release-Plan:** Option A frühestens nach dem ersten Beta-Tag (`v0.5.0-beta`), ideal als Bestandteil von Beta #2/#3 (`v0.4.0-beta`-Bucket „Overlays & Widgets“ passt inhaltlich); Option B erst nach stabiler Option-A-API; Wear-OS (Option C) danach separat bewerten.

## 📦 Vertriebskanäle: Play vs. F-Droid vs. IzzyOnDroid (Entscheidung)

Vergleich der Voraussetzungen für den ersten Upload (Stand 08/2026, Vivid ist MIT-lizenziert + hat reproduzierbare Builds im CI):

| Kriterium | Google Play | F-Droid (Hauptrepo) | IzzyOnDroid | Eigener F-Droid-Repo-Server |
|---|---|---|---|---|
| **Kosten** | **25 $** einmalig | 0 $ | 0 $ | 0 $ (Hosting z. B. GitHub Pages) |
| **Open-Source-Pflicht** | nein | **ja** (Vivid: ✅ MIT) | ja | nein (nur Empfehlung) |
| **Wartezeit bis Upload** | Kontoverifizierung **2–5 Werktage** | Review-MR (Wochen möglich) | Review (Tage–Wochen) | **sofort** |
| **Signing** | eigener Upload-Key + Play App Signing | **F-Droid-Key** (deine Signatur zählt dort nicht) | deren Key | **dein Release-Key bleibt** |
| **Updates** | sofort nach Upload | Neubau aus Source, **Tage–Wochen Lag** | Tage–Wochen Lag | **sofort** (eigene CI) |
| **Tester-Pflicht** | alpha ≥2 · production **12 Tester/14 Tage** | keine | keine | keine |
| **Antifeature/Policy** | Play-Formulare (Data Safety, Content Rating) | Sentry = **Tracking**-Antifeature, Twitch/Kick/YT = **NonFreeNet** | dito | keine (eigene Kuratierung) |
| **Reichweite** | **größte** | FOSS-Nische | FOSS-Nische | nur wer die URL kennt |
| **Beta-Programm** | Closed/Open Testing | nein | nein | nein |
| **Reproduzierbare Builds** | nicht gefordert (Bonus) | **stark bevorzugt** (Vivid: ✅ im CI) | bevorzugt | optional |

**Entscheidung (Roadmap):**

1. **Primär: Google Play** — größte Reichweite und das einzige Beta-Tester-Programm; die 12-Tester/14-Tage-Regel betrifft nur **production**, alpha/beta-Upload nicht. Der 25-$-Aufwand + Konto-Verifizierung ist der Einstieg (P0).
2. **Bereits aktiv: GitHub Releases + Obtainium** — der bestehende Beta-Kanal, unabhängig von Play.
3. **Post-Beta, optional: eigener F-Droid-Repo-Server** — gleiche APKs mit **deinem** Release-Key, sofortige Updates, kein Review, keine Antifeature-Listung (eigene Kuratierung). Ideal für das FOSS-Publikum; Sentry-Off-Variante nicht zwingend nötig, aber möglich.
4. **Später (etablierte App): F-Droid-Hauptrepo** — setzt eine **Sentry-freie Build-Variante** voraus (sonst Tracking-Antifeature) und akzeptiert, dass F-Droid selbst signiert (die eigene Release-Signatur-Pipeline gilt dort nicht). IzzyOnDroid ist ein leichterer Zwischenschritt mit denselben Einschränkungen.

**Konsequenz für die Roadmap:** Play bleibt der Gate-Pfad (v1.0-stable „Play-Store-Unterlagen“); F-Droid ist ein **Zusatzkanal** nach dem ersten Play-Upload, kein Ersatz für die Beta-Tester-Auslieferung.

## 🧪 Erster Beta-Build (Plan)

Das Beta-Gate ist erreicht, wenn **alle drei** Bedingungen erfüllt sind (Stand 2026-08-17: **0/3 offen — alle drei erfüllt**):

| Bedingung | Status | Offen |
|-----------|--------|-------|
| ≥17 ✅ in PARITY.md | ✅ **17/17** (Row 80 „Media-Player-Steuerung“ als 17. ✅) | — |
| Chat-✅ | ✅ **Twitch-Scope** (PARITY Row 77): Twitch (EventSub/Helix) + Chat-Overlay + **KI-Chat-Bot** laufen | Post-Beta: Kick/YouTube/SOOP, OAuth-Login (Senden/Moderation) |
| ≥1 Widget | ✅ **1/5 begonnen** — Text-/Info-Widget (Zeit/GPS/Geschwindigkeit) läuft (PARITY Row 87, 14 Tests) | Wetter (externer Dienst), Höhenmeter |

**📋 Checkliste vor dem Beta-Tag (Stand 2026-08-17):**

**Release-Voraussetzungen (GitHub/Obtainium-Kanal):**

- [x] Komplette Test-Suite grün (CI + Pre-Push-Gate) — läuft bei jedem Push
- [x] Lint (warningsAsErrors) grün — Teil des Pre-Push-Gates
- [x] Signing-Secrets hinterlegt (`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`, Abschnitt „🔑 Signing-Secrets“) — Nightlies/Alpha werden bereits damit signiert
- [x] Settings persistent über App-Neustarts (DataStore-basiert)
- [ ] **Keine bekannten Showstopper-Bugs** — letzter manueller Durchlauf auf dem Zielgerät (s. u. Smoke-Tests)

**Play-Unterlagen (nur nötig, wenn der Beta-Tag auch in die Play Console geht — für den reinen GitHub-/Obtainium-Beta nicht blockierend):**

- [x] Privacy Policy fertig — [PRIVACY.md](PRIVACY.md)
- [x] App-Icon 512×512 PNG → `fastlane/metadata/android/images/icon.png` (vorhanden, 512×512, RGBA)
- [ ] Screenshots (mind. 2, 16:9 oder 9:16) → `fastlane/metadata/android/images/phoneScreenshots/` (CI-Gate `scripts/check_play_metadata.sh` ist **grün** — 2 Platzhalter 1080×1920 vorhanden; vor einem öffentlichen Listing durch echte App-Screenshots ersetzen)
- [ ] Content Rating Questionnaire (wird in der Play Console ausgefüllt)
- [ ] Data Safety Section (welche Daten sammelt die App? — Play Console)
- [ ] `UPLOAD_*`- + `PLAY_JSON_KEY_*`-Secrets hinterlegt (nur für Play-Upload; Anleitung: Abschnitt „🔑 Secrets für den ersten Play-Upload vorbereiten“)

> 🎯 **Master-Checkliste (Reihenfolge + Zeitaufwand):** Abschnitt **„✅ Play-Vorbereitung: Priorisierte Abhakliste“** unten — alle Play-Schritte als P0–P2 mit genauen Schritten, Aufwand und kritischem Pfad.

**📸 Screenshots für den Play-Upload erzeugen (Anleitung):**

**Anforderungen** (Play Console + CI-Gate `scripts/check_play_metadata.sh`):
- Format: JPEG oder **24-bit PNG ohne Alpha** · min. 320 px · max. 3840 px · Seitenverhältnis **exakt 16:9 oder 9:16** (±1 %)
- **Mind. 2 Bilder** in `fastlane/metadata/android/images/phoneScreenshots/`, Namenskonvention für `supply`: `<index>_<locale>.png` → `1_en-US.png`, `2_en-US.png`
- Empfohlen: **1080×1920 (9:16, Hochformat)** — die aktuellen Platzhalter haben exakt dieses Format und werden beim Erzeugen der echten Screenshots **ersetzt** (gleicher Dateiname)

**Option A — Android-Emulator (empfohlen: echte App-Inhalte):**

Wichtig: Viele Geräte haben 19.5:9-/20:9-Displays — deren Native-Capture ist **kein** 9:16 und schlägt im Gate fehl. Das **Pixel-2-Profil ist exakt 1080×1920 (9:16)** und damit der einfachste Weg. Schritte (Windows/Git-Bash; SDK-Pfad ggf. an dein `%LOCALAPPDATA%\Android\Sdk` anpassen):

```bash
SDK="/c/Users/steff/AppData/Local/Android/Sdk"
AVDM="$SDK/cmdline-tools/latest/bin/avdmanager.bat"

# 1) AVD mit 9:16-Display anlegen (System-Image android-35 ist lokal vorhanden)
"$AVDM" create avd -n vivid_play -k "system-images;android-35;google_apis_playstore;x86_64" --device "pixel_2"

# 2) Emulator starten (ohne Fenster reicht für Screenshots)
"$SDK/emulator/emulator.exe" -avd vivid_play -no-snapshot -no-audio -no-boot-anim -no-window &
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'

# 3) Debug-APK bauen, installieren, starten (Debug-Variante: com.vivid.debug)
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Berechtigungen VORAB erteilen, damit keine Dialoge die Screenshots stören
adb shell pm grant com.vivid.debug android.permission.CAMERA
adb shell pm grant com.vivid.debug android.permission.RECORD_AUDIO
adb shell pm grant com.vivid.debug android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.vivid.debug android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.vivid.debug android.permission.POST_NOTIFICATIONS
adb shell am start -n com.vivid.debug/.irlbroadcaster.MainActivity

# 4) Ziel-Screens aufnehmen — Helfer: tippt ein UI-Element über seinen
#    content-desc (aus uiautomator dump; findet auch Compose-Elemente)
tap_desc() { # $1 = content-desc
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  b=$(grep -o "content-desc=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" /tmp/ui.xml \
      | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
  [ -z "$b" ] && { echo "content-desc \"$1\" nicht gefunden — Screen manuell öffnen"; return 1; }
  x1=$(echo "$b" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/')
  y1=$(echo "$b" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/')
  x2=$(echo "$b" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/')
  y2=$(echo "$b" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/')
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 2
}

# Screenshot 1 — „Live Stream“ (Start-Screen): direkt nach dem Start
adb exec-out screencap -p > fastlane/metadata/android/images/phoneScreenshots/1_en-US.png

# Screenshot 2 — Einstellungen (Top-Bar-Zahnrad, content-desc "Open Settings")
tap_desc "Open Settings"
adb exec-out screencap -p > fastlane/metadata/android/images/phoneScreenshots/2_en-US.png

# Screenshot 3 (optional) — OBS-Steuerung (content-desc "Open OBS Control")
adb shell input keyevent KEYCODE_BACK   # zurück zum Live-Stream
sleep 1
tap_desc "Open OBS Control"
adb exec-out screencap -p > fastlane/metadata/android/images/phoneScreenshots/3_en-US.png

# 5) Gate prüfen
bash scripts/check_play_metadata.sh   # Exit 0 = „Play-Metadaten vollständig.“
```

**Gewünschte Screens** (Routen aus `MainActivity.kt`, Navigation per `content-desc` aus `StreamingScreen.kt`):

| # | Screen | Route | Navigieren | Empfohlener Inhalt |
|---|---|---|---|---|
| 1 | Live Stream | `streaming_route` (Start) | App-Start | Haupt-Screen: Kamera-Vorschau, Go-Live, Streaming-Status |
| 2 | Einstellungen | `settings_route` | `tap_desc "Open Settings"` (Top-Bar-Zahnrad) | Chat-Bot-Bereich (Owner-KI, Limits/Cooldown), Streaming-Ziele |
| 3 (opt.) | OBS-Steuerung | `obs_control` | `tap_desc "Open OBS Control"` | OBS-Remote-UI (WebSocket-Status) |
| 4 (opt.) | Über | `about_route` | Settings → unten „About“ | Version, Links |

Hinweise:
- Die **ersten zwei** ersetzen die Platzhalter (`1_en-US.png`, `2_en-US.png`); weitere (`3_en-US.png`, …) sind optional und wandern automatisch mit ins Play-Listing
- `uiautomator dump` findet Compose-Elemente über ihre Accessibility-`content-desc` — die Top-Bar-Buttons tragen die descs `Open Settings` / `Open OBS Control`
- Ohne echten Stream zeigt der Live-Screen Config-/Status-Hinweise statt laufender Übertragung — fürs Listing ok; ein Screenshot mit **laufendem Stream** ist ideal, braucht aber echte Twitch-Zugangsdaten in den Settings

> ⚠️ **Kein 9:16-Display verfügbar?** Dann das Capture mit einem Bildeditor auf 9:16 **beschneiden** (mittig) und auf **1080×1920** exportieren (PNG ohne Alpha) — z. B. GIMP: Werkzeug „Zuschneiden“ mit Seitenverhältnis 9:16, dann „Bild skalieren“ auf 1080×1920. Alternativ ein anderes 9:16-Profil verwenden (z. B. `nexus_5x` = 1080×1920).

**Option A2 — Automatisiert per UI-Test (fastlane screengrab, reproduzierbar):**

Die Lane **`capture_play_screenshots`** erzeugt die zwei Screenshots per Compose-UI-Test (`app/src/androidTest/.../PlayScreenshotsTest.kt`): Live-Stream-Hauptscreen + Einstellungen (Navigation über die `content-desc` „Open Settings“). Voraussetzung: ein laufender Emulator/Gerät (z. B. der Pixel-2-AVD aus Option A). Ablauf:

```bash
bundle exec fastlane capture_play_screenshots
```

Was die Lane tut: ① `clean assembleDebug assembleDebugAndroidTest`, ② `screengrab`-Action (installiert APKs, instrumentiert **nur** `PlayScreenshotsTest`, zieht die PNGs vom Gerät nach `fastlane/screenshots/en-US/images/phoneScreenshots/`), ③ kopiert sie als `1_en-US.png`/`2_en-US.png` in `fastlane/metadata/android/images/phoneScreenshots/` und prüft den Metadaten-Gate. Screenshot-Namen: `1_live_stream` (Live Stream), `2_settings` (Einstellungen).

**Setup (einmalig, bereits im Repo):** `tools.fastlane:screengrab:2.1.1` als `androidTestImplementation`, Debug-Manifest mit den Screengrab-Permissions (`app/src/debug/AndroidManifest.xml`), `UiAutomatorScreenshotStrategy` im Test (sonst schwarze Screenshots bei Compose), `LocaleTestRule` für die Locale-Umschaltung. Bewusst **kein** Screengrab-Gradle-Plugin (mit AGP 9 nicht kompatibel) — die Action arbeitet library-only. Beim ersten Lauf ggf. `WRITE_EXTERNAL_STORAGE`-Warnung ignorieren (nur API ≤ 18 relevant); der Emulator muss gebootet sein, sonst bricht die Action mit „no connected devices“ ab.

**Option B — fastlane FrameIT (nur für Marketing/Optik, NICHT direkt fürs Play-Listing):**

- FrameIT rahmt Screenshots in Geräte-Frames und unterstützt seit 2.0 auch Android (`fastlane frameit android`; Frames werden beim ersten Lauf heruntergeladen, benötigt ImageMagick). Unterstützte Geräte u. a.: Pixel 3/3 XL, Galaxy S8/S9, Nexus 5X.
- **Achtung:** Der Rahmen ändert das Seitenverhältnis des Ergebnisbilds → ein gerahmtes Bild erfüllt **weder** Play noch den Gate (16:9/9:16). Fürs Play-Listing deshalb **ungerahmte** Captures (Option A) verwenden; gerahmte Bilder nur für Website/QA — oder das gerahmte Ergebnis anschließend zurück auf 9:16 beschneiden.
- Minimal-Beispiel im Screenshot-Ordner:
  ```bash
  cd fastlane/metadata/android/images/phoneScreenshots
  fastlane frameit android      # nutzt Framefile.json, falls vorhanden
  ```

**Nach dem Ersetzen:** `bash scripts/check_play_metadata.sh` muss Exit 0 liefern, damit der `publish-play`-Job durch den Metadaten-Gate kommt — die Screenshots wandern beim Upload (`supply(metadata_path: …)`) automatisch mit ins Play-Listing.

**Tester-Freigabe (Pflicht für den Beta-Tag):**

- [ ] **≥2 manuelle Tester** mit Obtainium (Pre-Releases aktiviert) in der Tester-Liste
- [ ] Jeder Tester bestätigt **„kein Crash in 15 Minuten“** mit Smoke-Test: Go-Live (Kamera + Mikro), Multi-Streaming (2. Ziel), Chat-Overlay (Twitch-Kanal), Text-/Info-Widget (Zeit/GPS/Geschwindigkeit), OBS-WebSocket-Remote, Settings-Persistenz nach App-Neustart, **Chat-Bot-Owner-Befehle** (s. u.)

**🤖 Bot-Befehle (Referenz für den Smoke-Test):**

| Befehl | Wer | Zweck |
|--------|-----|-------|
| `!help` · `!uptime` · `!tts` · `!song` · `!next` · `!pause` · `!play` · `!prev` · `!bot` | alle (Viewer) | Standard-Befehle — vollständige Referenz: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |
| `!start` / `!go-live` · `!stop` / `!end` · `!diag` / `!status` · `!ask <frage>` | **nur der Streamer** (Broadcaster-Badge oder Allow-List `chat_bot_owner_logins`) | Stream starten/stoppen · Diagnose mit Empfehlungen · Frage an die **exklusive Owner-KI** (Fallback: normale Bot-KI) — nur während eines aktiven Streams; Viewer erhalten nur einen Hinweis |

> **Erst wenn alle `[x]`:** `bundle exec fastlane release_beta` → Tag `v0.5.0-beta` → CI baut signiert, veröffentlicht als GitHub-Release (kein Pre-Release-Status-Limit nötig — Beta-Tags werden wie Alpha veröffentlicht) und läuft durch Signatur-/Reproduzierbarkeits-Checks.

**Ablauf nach Erreichen des Gates** — Status 2026-08-17: **Schritte 1–3 erledigt** (Tag `v0.5.0-beta` gesetzt und als GitHub-Release veröffentlicht):

1. ✅ `fastlane release_beta` implementiert (2026-08-15) — Spiegel von `release_alpha` mit Stufe `beta` inkl. Safety-Checks (Tag-Existenz, versionCode-Monotonie in der beta-Track, Quer-Track-Downgrade-Warnung).
2. ✅ Tag `v0.5.0-beta` erzeugt + gepusht (17.08.2026) → CI baut/publiziert automatisch (Tag-Trigger `v*` ist aktiv; Signatur- und Reproduzierbarkeits-Check laufen mit). Die Lane leitet den ersten Beta-Tag **automatisch vom höchsten `v*`-Tag ab** (Stufe auf `beta`): aktuell `v0.5.0-alpha` → `v0.5.0-beta`.
3. ✅ Metadaten: `versionName = 0.5.0-beta`, `versionCode = 5002` (deterministisch aus dem Tag: `major*1_000_000 + minor*1_000 + patch*10 + Stufe`, beta = 2 → `0.5.0-beta` = 5002 — **direkt über** `0.5.0-alpha` (5001), kein Downgrade für Bestandsnutzer).
4. QA-Rollout an Feldtester (Obtainium, Pre-Releases aktiviert); Smoke-Tests: Go-Live, Multi-Streaming, Chat-Overlay, OBS-Remote, Settings-Persistenz, Chat-Bot-Owner-Befehle (`!start`/`!stop`/`!diag`/`!ask`).
5. Cross-Track beachten: **beta → nightly ist ein Downgrade** (deinstallieren); nightly → beta ist installierbar.

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

### Rollback-Semantik bei Publish-Fehlern (Nightly vs. Stable)

Die `publish_release`-Lane (`fastlane/Fastfile`) wendet bei fehlgeschlagenem `gh release create` **bewusst unterschiedliche** Rollback-Regeln an — je nach Release-Typ:

| Aspekt | Nightly (`nightly-*`) | Stable (`v*`) |
|---|---|---|
| Tag-Herkunft | Wegwerf-Artefakt, vom Workflow selbst erzeugt (`nightly-<Timestamp>`) | Versionsmarker, **absichtlich** von der Release-Lane (`release_alpha`/`release_beta`) gepusht |
| Retry | 3 Versuche + 5 s Pause (transiente Netz-/API-Fehler) | 3 Versuche + 5 s Pause (Spiegel des Nightly) |
| Bei endgültigem Fehler | **Tag + Rest-Release löschen** → kein Orphan bleibt zurück | **Nur Rest-(Draft-)Release löschen, der Tag bleibt** — er wird für Re-Runs und Versionierung benötigt |
| Re-Run nach Fehler | nächster Run erzeugt einen frischen Tag | Workflow läuft erneut auf demselben Tag → Release wird neu erstellt |
| „Exists“-Check | — | akzeptiert nur **vollständige** Releases (published + APK-Asset); Draft/ohne-APK wird gelöscht und neu erstellt — verhindert stilles „Skipping“ ohne veröffentlichte APK |

**Selbstheilung (Sweep):** Der Job **`sweep-orphan-drafts`** (`android_fastlane.yml`, täglich 06:00 UTC + manuell per `workflow_dispatch`) meldet verwaiste Draft-Releases auf `v*`-Tags — Reste abgebrochener Stable-Runs, deren Rollback bei hartem Runner-Kill nicht mehr laufen konnte. Er **meldet nur, löscht nie** (ein menschlich angelegter Draft kann legitim sein) und färbt den Workflow bei Funden rot. Verwaiste `nightly-*`-Tags ohne Release räumt dagegen der Orphan-Tag-Sweep direkt im Publish-Schritt auf.

**Regressionstest (publish_release-Härtung):** Der Job **„Self-Test publish_release (Hardening, Mock-gh)“** in `android_fastlane.yml` (bei jedem Push/PR/Schedule) führt die gehärtete stabile Publizierung lokal gegen einen **Mock-gh** aus (`scripts/test_publish_release_hardening.sh`): Der Harness extrahiert die echte `publish_release`-Lane aus `fastlane/Fastfile` (kein Code dupliziert) und prüft alle Härtungs-Szenarien — frischer Tag, „exists“-Skip, Draft-Delete+Recreate, Retry (3 Versuche) und Rollback (nur Rest-Release, der Tag bleibt) — plus S7: das Command-Log des Harness und ein statischer Source-Guard beweisen, dass der stabile Pfad **nie** eine Tag-Löschung ausführt. Kein GitHub-Zugriff nötig (nur plain Ruby, kein Bundler/Fastlane); die anderen CI-Jobs werden nicht berührt. Lokal wiederholbar: `bash scripts/test_publish_release_hardening.sh` (Exit 0 = grün).

**Lokales Pre-Push-Gate:** `scripts/pre-push.sh` führt **vor jedem Push die gleichen Checks wie die CI** aus — Unit-Tests aller Module (`./gradlew testDebugUnitTest`), Lint (`./gradlew lintDebug`, warningsAsErrors), den **Sentry-Opt-out-Mapping-Check** (siehe unten) und den Secret-Guard. Damit landen Fehler wie der verpasste `:feature-settings`-Test (nur compiliert, nie getestet → roter CI-Lauf) gar nicht erst auf dem Remote. **Optionaler Release-Build:** `PRE_PUSH_RELEASE=1` lädt zusätzlich **beide** Release-Kanäle `./gradlew assembleRelease bundlePlayRelease` — das durchläuft R8/ProGuard + Resource-Shrinking lokal und fängt so Signatur-/ProGuard-Probleme, bevor sie die CI erreichen (Signierung fällt ohne `KEYSTORE_PATH`/`UPLOAD_*`-Secrets auf den Debug-Keystore zurück — gleiches Verhalten wie die CI ohne Secrets; der Dry-Run mit `PRE_PUSH_RELEASE=1` listet beide Builds und den Check). Als **Git-Hook** einmalig installieren: `bash scripts/install-git-hooks.sh` (schreibt `.git/hooks/pre-push` → ruft das Skript bei jedem `git push` auf; Hook liegt lokal in `.git/`, ist also nicht versioniert); manuell: `bash scripts/pre-push.sh`; `--dry-run` zeigt die Checks nur an; Lint überspringbar mit `PRE_PUSH_SKIP_LINT=1`. Bewusstes Umgehen nur über `git push --no-verify`. Der CI-Job „Test pre-push gate script“ (android.yml) prüft den Dry-Run (`scripts/test_pre_push.sh`) bei jedem Lauf.

**Sentry-Opt-out-Mapping-Check (R8-Nachweis, beide Kanäle):** `scripts/check_sentry_optout_mapping.sh` beweist per R8-Mapping, dass die **beforeSend-Opt-out-Logik** („Fehlerberichte senden“-Toggle → `null` statt Event) im Release-Build tatsächlich enthalten ist — C1 Mapping existiert, C2 frisch (mtime vs. Opt-out-Quellen), C3 Fabrik `sentryBeforeSendCallback` inlined, C4 Lambda `sentryBeforeSendCallback$lambda$0` vorhanden, C5 Lambda **in `io.sentry.SentryClient` inlined** (Logik im SDK-Aufruf-Pfad), C6 Fassade `SentryOptOutKt` als `R8$$REMOVED$$CLASS` (vollständig inlined, nicht nur umbenannt). Ohne Argumente prüft er **beide** Kanäle (`mapping/release` APK + `mapping/playRelease` AAB), mit Pfad-Argument nur die genannten. **Einbindungen:** ① Pre-Push-Gate — automatisch bei frischen Mappings, **Pflicht** nach `PRE_PUSH_RELEASE=1` (beide Builds laufen dann direkt davor); ② **Fastlane-CI** — Step „Verify Sentry opt-out logic in playRelease mapping“ im `test-publish-play`-Job (jeder Push/PR, AAB-Kanal) und Step „Verify Sentry opt-out logic in release mapping“ im `verify-repro`-Job (nach jedem Nightly, APK-Kanal), jeweils ohne Extra-Build-Zeit auf den ohnehin erzeugten Mappings; ③ Selbsttest `scripts/test_sentry_optout_mapping.sh` (9 Fälle, Stub-Mapping offline) als CI-Step „Test sentry optout mapping check“ in android.yml. Lokal wiederholbar: `bash scripts/check_sentry_optout_mapping.sh` (Exit 0 = beide Kanäle nachgewiesen).


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


### 🔑 Secrets für den ersten Play-Upload vorbereiten (UPLOAD_* + PLAY_JSON_KEY_*)

Konkreter End-to-End-Ablauf, um **alle nötigen Secrets** für den ersten echten `publish_play`-Lauf (alpha-Track) zu beschaffen und in GitHub zu hinterlegen. Ergebnis: `gh secret list` zeigt die fünf Secrets aus Schritt D, und der Testplan unten ist 1:1 ausführbar.

> 🛠 **Ausführbares Skript:** [scripts/prepare_play_secrets.sh](../scripts/prepare_play_secrets.sh) setzt Schritte B–E praktisch um — Keystore lokalisieren/erzeugen (`--generate`), base64-kodieren, `upload_cert.pem` exportieren, alle sechs Secrets per `gh` setzen (`--set`) und verifizieren (Guard + `gh secret list` + Fingerprint). Werte werden nur aus Dateien/stdin gelesen, nie ins Log geschrieben. Ohne `--set` druckt es die auszuführenden `gh secret set`-Befehle.
>
> ✅ **Metadaten-Check:** [scripts/check_play_metadata.sh](../scripts/check_play_metadata.sh) prüft die Play-Metadaten-Vollständigkeit (Icon 512×512, ≥2 Screenshots 16:9/9:16, Store-Listing + Changelog pro Locale). Läuft als harter Gate im `publish-play`-Job (android_fastlane.yml) direkt vor dem Upload; der Selbsttest ([scripts/test_play_metadata.sh](../scripts/test_play_metadata.sh), 1 Positiv- + 5 Negativfälle) läuft bei jedem Push in android.yml. Lokal: `bash scripts/check_play_metadata.sh` (Exit 0 = vollständig).

**Schritt A — Play-Console: Konto registrieren & App anlegen (einmalig, ~30 min):**

**A.1 — Entwicklerkonto registrieren** (https://play.google.com/console → „Weiter zu Play Console“ → Anmeldung mit dem Google-Konto, das dauerhaft zum Projekt gehören soll):

| Formularfeld | Wert für Vivid | Hinweis |
|---|---|---|
| **Land/Region** | dein Land | **danach nur schwer änderbar** — bestimmt Steuer & Zahlungsabwicklung |
| **Kontotyp** | **Persönlich** | „Organisation“ bräuchte D-U-N-S-Nummer + Firmendaten |
| **Entwicklername** (öffentlich) | z. B. `thoser666` | erscheint im Store unter der App |
| **Kontakt-E-Mail** (öffentlich) | deine E-Mail | wird auch Kontakt im Store-Listing (P1) |
| **Website** (optional) | z. B. https://github.com/thoser666/Vivid | optional, aber gut fürs Listing |
| **Telefonnummer** | deine Handynummer | SMS-Verifizierung |
| **Physische Adresse** | Straße, PLZ, Ort, Land | nur für Verifikation/Steuer, **nicht öffentlich** |
| **Zahlungsmethode** | Kredit-/Debitkarte | einmalige **25 $** Registrierungsgebühr |
| **Identitätsverifizierung** | Ausweis/Pass | Verifizierung **2–5 Werktage** |
| **Zustimmung** | Google Play Developer Distribution Agreement | Pflicht zum Abschluss |

⚠️ **Production-Frist:** persönliche Konten (ab 13.11.2023) erhalten Production-Zugang erst nach **Closed Test mit ≥12 Testern über 14 Tage** — der **alpha/beta-Upload ist davon NICHT betroffen**.

**A.2 — App anlegen** (Play Console → „Alle Apps“ → **„App erstellen“**):

| Formularfeld | Wert für Vivid | Hinweis |
|---|---|---|
| **App-Name** | `Vivid` | max. 30 Zeichen, öffentlich sichtbar |
| **Standardsprache** | **Englisch (Vereinigte Staaten)** — `en-US` | Metadaten-Vorlagen liegen in `en-US` + `de-DE` vor (`fastlane/metadata/android/`) |
| **App oder Spiel** | **App** | |
| **Kostenlos oder kostenpflichtig** | **Kostenlos** | |

Nach dem Anlegen: Die App ist **automatisch in Play App Signing eingeschrieben** (App-Signing-Key generiert Google, Variante A). Der `internal`-Test-Track existiert automatisch; `alpha`/`beta`/`production` legst du später an (Schritt 8 im Abschnitt „📋 Play-Listing …“).

**A.3 — applicationId notieren:** `com.vivid` aus `app/build.gradle.kts` — muss exakt zum hochgeladenen AAB passen (sonst `403` im supply-Step). Nicht verwechseln: `com.vivid.debug` ist nur die Debug-Variante.

**Schritt B — Upload-Key erzeugen & in der Play Console registrieren:**

1. Upload-Keystore erzeugen + base64-kodieren — **exakt** nach Abschnitt **„🔐 Play-Upload-Keystore erzeugen & UPLOAD_*-Secrets hinterlegen“** oben (Schritte 1–2)
2. Öffentliches Zertifikat exportieren (enthält **keinen** privaten Schlüssel):
   ```bash
   keytool -export -rfc -keystore upload-keystore.jks -alias upload -file upload_cert.pem
   ```
3. Play Console → **Setup → App-Integrität → App-Signierung** → unter „Upload-Key-Zertifikat“ **„Exportieren und Upload-Key hochladen“** → `upload_cert.pem` wählen. App-Signing-Key: **Google-generiert lassen** (Variante A)

**Schritt C — Service-Account + JSON-Key erzeugen (die Play-API-Credentials):**

> Die `publish_play`-Lane braucht ein **Google-Dienstkonto** mit Play-Zugriff — daraus entsteht die JSON-Key-Datei, die später `PLAY_JSON_KEY_DATA` wird. Die UI-Namen beziehen sich auf die deutsche Play Console (englisch in Klammern).

1. Play Console → **Setup → API-Zugang** → unter „Google Play Developer API“ auf **„Google Cloud Platform-Konsole verknüpfen“** (bzw. „API-Zugang einrichten“) → öffnet die Google Cloud Console mit dem Play-Projekt
2. In der Google Cloud Console: **APIs & Dienste → Bibliothek** → nach **„Android Publisher API“** suchen → **aktivieren**
3. **APIs & Dienste → Anmeldedaten → Anmeldedaten erstellen → Dienstkonto** → Name z. B. `vivid-play-publisher`, Rolle „Keine Rolle“, „Fertig“
4. Dienstkonto öffnen → Reiter **„Schlüssel“** → **„Neuen Schlüssel hinzufügen“ → JSON** → lädt z. B. `vivid-play-publisher.json` herunter (enthält `client_email`, `private_key`, `project_id` — **nie committen**, siehe Guard unten)
5. Zurück in der Play Console → **Setup → API-Zugang** → beim Dienstkonto **„Zugriff gewähren“** → Rolle **„Releasemanager“** (engl. „Release manager“) — die **minimale** Rolle für `supply`-Uploads (nur „Releases verwalten“, keine Finanz-/Nutzerrechte)
6. Kurzer Smoke-Check der Datei: `head -5 vivid-play-publisher.json` → `client_email` muss auf die Play-Console-App verweisen (gleiche E-Mail wie unter API-Zugang)

**Schritt D — GitHub-Secrets hinterlegen (5 Secrets, exakte Befehle):**

Repo → **Settings → Secrets and variables → Actions → New repository secret**, oder komfortabel per `gh` (Werte werden interaktiv bzw. via Pipeline eingelesen):

```bash
gh secret set UPLOAD_KEYSTORE_BASE64        # ← base64-Zeile aus Schritt B.1 (eine Zeile, ohne Umbruch)
gh secret set UPLOAD_KEYSTORE_PASSWORD      # ← Store-Passwort des Upload-Keystores
gh secret set UPLOAD_KEY_ALIAS              # ← upload
gh secret set UPLOAD_KEY_PASSWORD           # ← Key-Passwort (bei jks ggf. ≠ Store-Passwort)
gh secret set PLAY_JSON_KEY_DATA            # ← kompletten Inhalt von vivid-play-publisher.json einfügen (mehrzeilig OK)
gh secret list                              # Kontrolle: alle fünf Namen sichtbar (Werte nie)
```

> **`PLAY_JSON_KEY_FILE` vs. `PLAY_JSON_KEY_DATA`:** Für **CI** immer **`PLAY_JSON_KEY_DATA`** — die Lane übergibt den Inhalt direkt an `supply(json_key:)`. `PLAY_JSON_KEY_FILE` (Pfad) ist nur für **lokale** Läufe gedacht (dort kann fastlane die Datei lesen); im CI würde der Pfad auf dem Runner nicht existieren. Genau **eines** von beiden reicht — sind beide leer, bricht die Lane im Step 1/6 ab (auch ohne `dry_run`).

**Schritt E — Verifikation (bevor irgendetwas hochgeladen wird):**

1. `bash scripts/guard_secrets.sh` → `✅ [guard] Keine Keystores oder Klartext-Secrets gefunden.` (Guard blockt u. a. `upload-keystore.jks`, `upload_cert.pem`, `play-credentials.json`, `fastlane/.env.play` und `*.b64`)
2. `gh secret list` zeigt **alle fünf** Namen (Schritt D) — fehlt einer, schlägt der CI-Job bereits bei „Decode Upload Keystore“ fehl (bekanntes Fehlerbild, siehe Testplan)
3. **Dry-Run** per `workflow_dispatch` mit `-f dry_run=true` (Testplan Schritt 3) → baut + verifiziert, verbraucht **keinen** `version_code` und berührt Play nicht
4. Erst wenn der Dry-Run grün ist: echter Upload (Testplan Schritt 3 ohne `dry_run`)**💡 Reihenfolge-Zusammenfassung:** A (Konto+App) → B (Keystore+Console) → C (Service-Account+JSON) → D (Secrets) → E (Guard+Dry-Run) → **Listing ausfüllen (unten)** → Testplan Schritt 3–6 (echter Upload).

### 📋 Play-Listing in der Play Console anlegen (App-Content + Store-Listing)

Der `supply`-Upload (`publish_play`) lädt nur APK/AAB + Metadaten (Titel, Beschreibung, Changelog, Screenshots, Icon) hoch. **Content Rating, Data Safety, Zielgruppe, Anzeigen- und App-Zugriff-Erklärung sind reine Console-Formulare** — supply kann sie nicht setzen. Der Upload in einen Test-Track klappt auch ohne sie, aber das **„Ausrollen“ (Roll out) eines Releases bleibt gesperrt**, bis die Pflichtfelder ausgefüllt sind — genau daran hängt ein erster Lauf typischerweise fest. Einmalig in der Play Console ausfüllen (Reihenfolge wie die Console-Checkliste):

1. **Haupt-Store-Listing:**
   - Titel, Kurz- und Langbeschreibung: werden von `supply` aus `fastlane/metadata/android/{en-US,de-DE}/` hochgeladen (bereits vorhanden) — in der Console nur prüfen
   - **Kontakt-E-Mail** (Pflicht) und **Datenschutzerklärung-URL** (Pflicht): **fertig gehostet unter https://thoser666.github.io/Vivid/privacy/** (GitHub-Pages-Workflow, automatisch aktualisiert bei PRIVACY.md-Änderungen)
   - **Feature-Grafik** (1024×500) optional; App-Symbol + Screenshots lädt `supply` automatisch aus `images/` mit hoch
2. **App-Content → App-Zugriff:** „Alle Funktionen ohne Login“ — Vivid hat kein eigenes Konto (Twitch-/LLM-Tokens liegen lokal in den Settings)
3. **App-Content → Anzeigen:** „Nein“ — kein Ad-SDK im Projekt (verifiziert: kein `ads`/`billing`-Dependency)
4. **App-Content → Content-Rating (IARC-Fragebogen):** selbsterklärende Fragen; für Vivid realistisch: Live-Streaming mit **nutzergenerierten Inhalten** (Chat) + **Standort teilen** → ehrliches Ergebnis **16+** (nicht „jung“). Antwortvorlage direkt unten („IARC-Antwortvorlage für Vivid“). **Fehlt das Rating, blockiert Google das Ausrollen** („Content rating fehlt“)

   **IARC-Antwortvorlage für Vivid (Stand 08/2026):**

   | Fragekategorie | Antwort | Begründung für Vivid |
   |---|---|---|
   | Gewalt (realistisch/cartoon) | **Nein** | keine Gewaltinhalte in der App selbst |
   | Sexualität & Nacktheit | **Nein** | keine sexualisierten Inhalte in der App |
   | Sprache (Kraftausdrücke/vulgär) | **Nein** | die App generiert keine; UGC-Chat kann sie enthalten (siehe UGC-Zeile) |
   | Glücksspiel (Echtgeld/simuliert/3D) | **Nein** | kein Glücksspiel, keine Lootboxen |
   | Alkohol, Tabak, Drogen | **Nein** | keine Referenzen |
   | **Nutzergenerierte Inhalte (UGC)** | **Ja** | Live-Stream + Twitch-Chat sind Nutzerinhalte, **nicht gefiltert/modieriert** |
   | **Standort teilen** | **Ja** | GPS-Widget zeigt Koordinaten im gestreamten Overlay → für Zuschauer sichtbar |
   | **Persönliche Daten teilen** | **Ja** | öffentlicher Chat + Stream können persönliche Infos enthalten |
   | **Chat/Kommunikation zwischen Nutzern** | **Ja** | Twitch-Chat (Overlay + Bot) |
   | **Internetzugang** | **Ja** | Streaming, Chat, LLM-Endpunkte |
   | In-App-Käufe / digitale Käufe | **Nein** | keine IAP, App ist kostenlos |
   | Zufallsgegenstände | **Nein** | keine |

   **Erwartetes Rating:** durch **ungefilterte UGC + Standort-Teilen** liegt das ehrliche Ergebnis bei **16+** (z. B. PEGI 16 / USK 16; regionale Abweichungen möglich) — Referenz: Streaming-Apps mit Live-UGC werden von IARC als „reif“ eingestuft. **Entscheidungshebel:** Falls die UGC-Frage „können Inhalte enthalten, die allein ein höheres Rating auslösen“ mit Ja beantwortet wird (Live-Streams können real beliebiges zeigen), steigt das Rating weiter (Richtung 18+). Wer das geringer hält, muss die Streams tatsächlich selbst moderieren/filtern — die Antwort muss wahr sein (Review).
5. **App-Content → Zielgruppe und Inhalte:** „Nein“ (nicht kinderorientiert — Vivid richtet sich an Streamer, 18+)
6. **App-Content → Data Safety:** ehrlich nach Manifest/Berechtigungen ausfüllen — **abgestimmt mit [PRIVACY.md](PRIVACY.md)** (Stand 18.08.2026):
   - **Erhoben:**
     - **Kamera-/Mikrofon-Inhalte** — werden live zum **selbst konfigurierten** Streaming-Server übertragen (RTMP/SRT); nur während der Stream läuft
     - **Standort (präzise, GPS)** — nur solange das **Text-/Info-Widget** (Zeit/GPS/Geschwindigkeit) aktiv ist; Koordinaten/Geschwindigkeit sind Teil des gestreamten Overlays, wenn parallel gestreamt wird („auf Nutzeraktion“)
     - **Nutzerinhalte (Chat)** — Twitch-Chat wird gelesen/gesendet (Helix/EventSub); bei **aktivem KI-Bot** gehen Chat-Kontext + System-Prompt an den **vom Streamer selbst konfigurierten LLM-Endpunkt** (OpenAI-kompatibel: OpenAI/Gemini/Groq/DeepSeek oder lokal Ollama)
     - **Crash-/Fehlerdaten + Gerätekennung (Sentry)** — **automatisch, aber Opt-out-Toggle in den Settings** (Default: an): DSN im Manifest, **keine Crash-Screenshots** (attach-screenshot=false), keine IP-/Gerätename-Erhebung (sendDefaultPii=false), View-Hierarchy nur Layout — siehe PRIVACY.md
     - **Testvorgehen (Toggle-Unterdrückung):** Die beforeSend-Opt-out-Logik ist als **pure Funktion** (`applySentryOptOut`) und als **Callback-Fabrik** (`sentryBeforeSendCallback`) in `app/.../irlbroadcaster/SentryOptOut.kt` extrahiert — `VividApplication` registriert exakt diese Fabrik. `SentryOptOutTest` (läuft in `:app:testDebugUnitTest`, jeder Push/PR über Pre-Push-Gate + CI „Run Tests“) prüft die **echte Verkabelung** mit einem realen `io.sentry.SentryEvent` + `Hint`: Toggle an → gleiche Event-Instanz wird durchgereicht (`assertSame`), Toggle aus → `beforeSend` liefert **null** (Event verworfen, kein Versand), und der Toggle wird **live pro Event** gelesen (Umschalten zur Laufzeit wirkt sofort, kein Init-Snapshot).
   - **Nicht erhoben:** Konten, E-Mails, Finanzdaten, Einkäufe, Health-Daten, Kontakte, Kalender, Web-Verlauf, Dateien
   - **Keine Datenverkäufe** — kein eigenes Backend (nur vom Nutzer konfigurierte Endpunkte: Twitch, LLM, OBS-lokal)
   - **Antworttabelle für das Formular (Datentyp → Erhoben? → Zweck → Geteilt? → Verschlüsselt? → Löschung/Kontrolle):**

     | Formular-Datentyp | Erhoben? | Zweck | Geteilt? | Verschlüsselt? | Löschung/Kontrolle |
     |---|---|---|---|---|---|
     | **Standort — präzise** | ✅ Ja (nur bei aktivem GPS-Widget) | App-Funktionalität (Overlay) | ✅ Ja — Teil des Streams an den **selbst konfigurierten** Server (für Zuschauer sichtbar) | Stream: abhängig vom Server (RTMPS/SRT = TLS, RTMP = unverschlüsselt) | Widget aus = keine Erfassung; kein Vivid-Server-Speicher |
     | **Fotos & Videos / Audiodateien (Kamera/Mikro)** | ✅ Ja (live, nur während Stream) | App-Funktionalität (Streaming) | ✅ Ja — an den **selbst konfigurierten** Streaming-Server | API/Steuerung TLS; Stream abhängig vom Server-Protokoll | nur lokal während des Streams; kein Vivid-Speicher |
     | **Nachrichten/Chat (Nutzerinhalte)** | ✅ Ja (Twitch-Chat lesen/senden) | App-Funktionalität (Bot/Overlay) | ✅ Ja — an Twitch; bei aktivem KI-Bot an den **selbst konfigurierten** LLM-Endpunkt | ✅ TLS | Bot abschaltbar; History nur in-memory |
     | **App-Aktivität (Interaktionen, Settings)** | ✅ Ja (lokal) | App-Funktionalität | ❌ Nein (lokal im DataStore) | n/a (lokal) | App-Daten im System löschbar |
     | **App-Info & Leistung (Crash-/Diagnosedaten)** | ✅ Ja — automatisch, **Opt-out in Settings** (Default: an) | Analyse/Fehlerbehebung | ✅ Ja — an Sentry | ✅ TLS | **Toggle „Fehlerberichte senden“** in den Settings (keine Screenshots) |
     | **Geräte-/andere Kennungen (Gerätemodell, OS)** | ✅ Ja (via Sentry; **keine IP**, kein Gerätename — sendDefaultPii=false) | Analyse/Fehlerbehebung | ✅ Ja — an Sentry | ✅ TLS | Toggle in den Settings |
     | Konten, E-Mail, Telefon, Finanzen, Health, Kontakte, Kalender, Web-Verlauf, Dateien | ❌ **Nein** | — | ❌ | — | — |

   - **Globale Formular-Abschlussfragen:** „Verkauf von Nutzerdaten“ → **Nein** · „Weitergabe für Werbung“ → **Nein** · „Nutzer können Löschung beantragen“ → ehrlich: für lokale Daten via App-Daten löschen, für Sentry-Daten über den Sentry-Data-Request (kein In-App-Weg) — im Formular entsprechend **Nein/teilweise** angeben, konsistent zur PRIVACY.md
   - Hinweis: **Internal-Test-Tracks sind von Data Safety befreit** (Google-Hilfe) — für alpha/beta/production trotzdem ausfüllen, sonst blockiert das Ausrollen
7. **News-Apps / Regierungs-Apps:** nicht zutreffend → überspringen
8. **Testing-Track sicherstellen:** `supply(track: …)` braucht einen **existierenden Track** — Default-Namen: `internal` (bis 100 Testpersonen, ohne Freigabe), `alpha`/`beta` (geschlossener Test mit Tester-Liste), `production`. Für den ersten Upload: Track in der Console anlegen, bevor `publish_play` mit `track=alpha` läuft (sonst Fehler „track not found“)
9. **Rollout:** Nach dem Upload in der Console → Release → **„Ausrollen“** — Google listet dort exakt die noch fehlenden Pflichtfelder (meist genau Content Rating / Data Safety); die Punkte 1–8 schließen diese Lücken

### ✅ Play-Vorbereitung: Priorisierte Abhakliste (mit Zeitaufwand)

Master-Checkliste für den Weg zum ersten Play-Upload — **Reihenfolge = kritischer Pfad**, Aufwand pro Schritt (einmalig, realistisch). Details stehen in den verlinkten Abschnitten; der **Testplan darunter** ist die Ausführung.

> 🐙 **Tracking:** Der Fortschritt wird in [Issue #116](https://github.com/thoser666/Vivid/issues/116) abgehakt (19 abhakbare Task-Liste, P0–P2) — die Checkliste hier ist die Quelle, das Issue das Live-Tracking.

> **Gesamt: ~4–5 h Hands-on** (einmalig) + **2–5 Werktage Kontoverifizierung, danach 1 h–7 Tage Tester-Freigabe** (Production zusätzlich: 12-Tester/14-Tage-Closed-Test). Der GitHub-/Obtainium-Beta ist von **keinem** dieser Punkte abhängig — die betreffen nur den Play-Kanal.
>
> 📏 **Messwerte (Stand 17.08.2026, lokal/CI gemessen):** keytool 4096-bit-Keystore ≈ **2 s** · `prepare_play_secrets.sh` (Dry-Run) < 1 min · `guard_secrets.sh` ≈ **14 s** · CI-Selbsttest `publish_play (dry_run)` ≈ **6 min** (Build+Signaturverifikation) · Android-CI „Build & Test“ ≈ **6,5 min** · Fastlane-Nightly-Gesamtlauf ≈ **19 min**. Die „~min“-Angaben unten sind damit kalibriert — nur die manuellen Console-Schritte (Konto, Formulare, Tester) dominieren den Gesamtaufwand.

**P0 — Blockierend (ohne diese Schritte kann `publish_play` nie laufen) · ~1,5–2 h:**

- [ ] **Play-Entwicklerkonto** (~30 min + **2–5 Werktage Identitäts-/Zahlungsverifikation**): play.google.com/console → einmalige Registrierung (**25 $**, Kredit-/Debitkarte, Telefonnummer, Ausweis) → Abschnitt „🔑 Secrets …“, Schritt A.1 — **zeitkritisch, zuerst erledigen**. ⚠️ **Production-Frist:** persönliche Konten (ab 13.11.2023) brauchen vor Production-Zugang einen **Closed Test mit ≥12 Testern über 14 Tage** — der **alpha/beta-Upload ist davon NICHT betroffen**
- [ ] **App „Vivid“ anlegen** (~10 min): `applicationId` muss exakt **`com.vivid`** sein (sonst 403 im Upload); neue Apps sind automatisch in Play App Signing eingeschrieben (App-Signing-Key: Google-generiert, Variante A)
- [ ] **Upload-Keystore erzeugen + sichern** (~5 min: Erzeugung ≈ 2 s, der Rest ist Backup): `bash scripts/prepare_play_secrets.sh --generate` oder manuell nach Abschnitt „🔐 Play-Upload-Keystore erzeugen …“ — **BACKUP Pflicht** (Keystore-Verlust = Upload für immer verloren, siehe Widerruf-Risiko)
- [ ] **`upload_cert.pem` in der Console registrieren** (~5 min): Setup → App-Integrität → App-Signierung → „Exportieren und Upload-Key hochladen“
- [ ] **Service-Account + JSON-Key** (~30 min): Setup → API-Zugang → GCP-Dienstkonto → Android Publisher API aktivieren → Rolle **„Releasemanager“** → JSON herunterladen (Schritt C) — niemals committen
- [ ] **6 GitHub-Secrets setzen** (~5 min: `--set` erledigt alle sechs in <1 min, Rest ist Passwort-Eingabe): `bash scripts/prepare_play_secrets.sh --set` → `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `PLAY_JSON_KEY_DATA` (+ optional `PLAY_JSON_KEY_FILE`) — Werte nur aus Dateien/stdin, nie ins Log
- [ ] **Verifikation** (~5 min: Guard ≈ 14 s, Rest ist `gh secret list` + Fingerprint): `bash scripts/guard_secrets.sh` grün + `gh secret list` zeigt alle Namen + SHA-256-Fingerprint-Abgleich (Schritt E)
- [ ] **Dry-Run grün** (~10 min CI — gemessen ≈ 6 min für Build+Verifikation im CI-Selbsttest): `workflow_dispatch` mit `dry_run=true` → baut + signaturverifiziert gegen den Upload-Key, verbraucht **keinen** versionCode und berührt Play nicht

**P1 — Fürs „Ausrollen“ (Release bleibt sonst in der Console gesperrt) · ~2 h** — kann **parallel** zu P0 laufen:

- [ ] **Echte Screenshots statt Platzhalter** (~45–60 min): Option A (Emulator, Anleitung oben) oder automatisiert `bundle exec fastlane capture_play_screenshots` → ≥2 × 16:9/9:16, `check_play_metadata.sh` bleibt grün
- [x] **PRIVACY.md unter öffentlicher HTTPS-URL hosten** — ✅ **fertig:** https://thoser666.github.io/Vivid/privacy/ (GitHub-Pages-Workflow `.github/workflows/pages.yml`, aktualisiert sich automatisch bei PRIVACY.md-Änderungen)
- [ ] **Kontakt-E-Mail** im Store-Listing eintragen (Pflichtfeld, bleibt offen)
- [ ] **App-Zugriff**: „Alle Funktionen ohne Login“ (~5 min)
- [ ] **Anzeigen**: „Nein“ (~2 min; kein Ad-SDK im Projekt, verifiziert)
- [ ] **Content Rating (IARC-Fragebogen)** (~15–20 min, **Antwortvorlage oben bei Schritt 4**): Live-Streaming + nutzergenerierter Chat + Standort teilen → ehrlich **16+**; fehlt es, blockiert Google das Ausrollen
- [ ] **Zielgruppe**: nicht kinderorientiert (~2 min)
- [ ] **Data Safety** (~15–30 min, **Vorlage in Schritt 6 oben**, abgestimmt mit [PRIVACY.md](PRIVACY.md)): Kamera/Mikro, **Standort präzise** (GPS-Widget, nur bei aktivem Widget), **Chat/LLM** (Twitch + selbst konfigurierter LLM-Endpunkt), **Sentry automatisch mit Opt-out-Toggle** (Default: an, keine Screenshots); keine Datenverkäufe — Internal-Test-Tracks sind befreit, alpha/beta/production nicht
- [ ] **Track `alpha` anlegen** (~5 min): Play Console → Testing → Alpha — `supply(track: alpha)` bricht sonst mit „track not found“

**P2 — Auslieferung an Tester · ~30 min + Wartezeit:**

- [ ] **≥2 Tester einladen** (~10 min + **1 h–7 Tage Wartezeit**): Play Console → Testing → **Alpha** → Tester-Liste → **E-Mail-Adressen eintragen reicht** (kein Google-Konto-Link/keine Google-Group nötig — Groups sind nur für Organisations-Konten). **Wichtig:** Jede Adresse muss mit einem **Google-Konto** verknüpft sein (nicht zwingend Gmail), und die Tester müssen die Einladung **ANNEHMEN („opt in“)** — per Einladungs-E-Mail oder über den geteilten **Opt-in-Link** („Join on the web“, in der Track-Ansicht kopierbar); erst nach Annahme sehen sie die App. Bis 200 Listen à 2.000 Testpersonen; **Tipp:** eigene zweite Gmail-Adresse als erster Tester nutzen
- [ ] **Erster echter Upload** (~10 min CI — Build+Signatur ≈ 6 min, Upload ≈ 1–2 min): Testplan Schritt 3–6 ohne `dry_run`, `version_code` **explizit** setzen (z. B. `1`) — ein vergebener Code ist in Play für immer belegt
- [ ] **Smoke-Test bestätigen lassen**: „kein Crash in 15 Minuten“ pro Tester → Voraussetzung fürs spätere Ausrollen

**Kritischer Pfad:** P0 → P2 (erster Upload in den Alpha-Track); P1 ist für den **reinen Upload** nicht nötig, aber **zwingend fürs Ausrollen** („Go live“) — parallel erledigen. **Reihenfolge-Tipp:** Mit dem Play-Konto starten (Identitäts-/Zahlungsverifikation dauert 2–5 Werktage) und parallel die echten Screenshots aufnehmen — das ist der einzige P1-Punkt mit echtem Tooling-Aufwand.

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

> 💡 **Erst trocken testen:** Mit `-f dry_run=true` baut der Job das AAB und verifiziert die Signatur gegen den Upload-Key, **ohne** etwas hochzuladen — ideal für den ersten Lauf (keine Play-Auswirkung, kein `version_code` verbraucht). Der Metadaten-Gate läuft **auch im Dry-Run** (die zwei Platzhalter-Screenshots erfüllen ihn bereits). Erst wenn Step 4/6 grün ist, den echten Upload (ohne `dry_run`) ausführen.
> 🔁 **Automatischer CI-Selbsttest (ohne Play-Zugang):** Der Job **„Self-Test publish_play (dry_run)“** in `android_fastlane.yml` führt die Lane bei jedem Push/PR mit einem **lokal erzeugten Wegwerf-Keystore** aus (`scripts/test_publish_play_dryrun.sh`): `keytool` erzeugt den Test-Keystore, die Lane baut `bundlePlayRelease` und verifiziert die AAB-Signatur per `keytool` gegen den Test-Key — ganz ohne `UPLOAD_*`-Secrets und ohne Play-Zugang. Zusätzlich erzwingt ein Negativtest, dass `publish_play` **ohne** `dry_run` und ohne `PLAY_JSON_KEY_*` am Credential-Guard scheitert (kein Upload-Pfad ohne Play-Zugang). Damit ist die Lane dauerhaft regressionstestbar, **bevor** die echten Secrets existieren. Lokal jederzeit wiederholbar: `bash scripts/test_publish_play_dryrun.sh`. Der **Metadaten-Gate** ist davon getrennt abgedeckt: `test_play_metadata.sh` (android.yml, jeder Push) prüft das Check-Skript selbst, der echte Gate läuft im `publish-play`-Job — zusammen decken sie alle Schritte des Jobs ab (Gate → Keystore → Signatur → Upload).

**Schritt 4 — CI-Lauf beobachten** (`gh run watch <run-id>`), erwartete Reihenfolge im `publish-play`-Job (Job-Schritte **vor** der Lane + Fastfile-Schritte):

| Job-Step | Erwartung |
|---|---|
| Check Play metadata completeness | `✅ [play-metadata] Play-Metadaten vollständig.` (Icon 512×512, ≥2 Screenshots 16:9/9:16, Store-Listing + Changelog pro Locale) — Gate läuft **vor** dem Keystore: bricht bei Lücken ab, bevor Secrets verbraucht werden |
| Decode Upload Keystore | `UPLOAD_KEYSTORE_BASE64` dekodiert → `upload-keystore.jks`, `UPLOAD_KEYSTORE_PATH` als Env gesetzt |
| Step 1/6 Credential-Checks | `UPLOAD_KEYSTORE_*` + Play-Credentials vorhanden → weiter |
| Step 2/6 Version | `Play upload: <version> (<code>) -> track alpha` |
| Step 3/6 Build | `bundlePlayRelease` erfolgreich; AAB-Pfad + Größe geloggt |
| Step 4/6 Signatur | `AAB signer SHA-256` == `Upload key SHA-256` → `✅ AAB signature verified against UPLOAD key` |
| Step 5/6 Upload | `supply` meldet Erfolg (kein 401/403) |
| Step 6/6 | `✅ Uploaded <version> to Play track alpha` |

Fehlerbilder (bewusst **harte Abbrüche** — bei CI-Fail hat Play keinerlei Änderung):
- `❌ [play-metadata] …` → Metadaten unvollständig (Icon/Screenshots/Locale/Changelog) → Job bricht **vor** „Decode Upload Keystore“ ab — kein Secret-Verbrauch, kein Upload
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



## 🤖 Chat-Bot: Twitch-Token & Client-ID (Setup)

Damit der KI-Chat-Bot im Kanal antwortet und **Owner-Befehle privat per Whisper** sendet/empfängt (`!start`/`!stop`/`!diag`/`!ask`), braucht das **Bot-Konto** einen User-Access-Token mit den richtigen Scopes und eine **Twitch-App-Client-ID**. Beide Werte liegen **nur in den App-Einstellungen** des Streamers — sie sind bewusst **keine** GitHub-Secrets (der Secret-Guard-Check verhindert, dass sie je ins Repo gelangen). Details zu Limits & Verhalten: `docs/ai-chat-bot.md`.

**1. Bot-Token mit den Chat-Scopes erzeugen** (einmalig, dann App-einstellen):

Seit dem **IRC-Ausstieg** liest Vivid den Chat über Twitch-EventSub (`channel.chat.message`, Scope `user:read:chat`) und sendet über die Helix-API (`POST /helix/chat/messages`, Scope `user:write:chat`) — die alten IRC-Scopes `chat:read`/`chat:edit` sind nicht mehr gültig. Hinzu kommt `user:manage:whispers` für die privaten Owner-Antworten.

- Optional: eigenes Twitch-Konto für den Bot registrieren (gleiche App-Regeln wie jedes Konto).
- Quick-Weg: **twitchtokengenerator.com** → „Custom Scope“ → folgende Scopes anfordern:
  `user:read:chat` `user:write:chat` `user:manage:whispers`
  (`user:read:chat` = Chat lesen via EventSub (ersetzt `chat:read`), `user:write:chat` = Chat senden via Helix (ersetzt `chat:edit`); `user:manage:whispers` deckt **Senden** und zugleich den **EventSub-Empfang** `user.whisper.message` ab — Twitch akzeptiert dort auch `user:read:whispers`.)
- Oder offizieller OAuth-Flow (eigene Twitch-App nötig, siehe Schritt 2):
  ```
  https://id.twitch.tv/oauth2/authorize?client_id=<CLIENT_ID>&redirect_uri=<REDIRECT>&response_type=token&scope=user:read:chat%20user:write:chat%20user:manage:whispers
  ```
- **Auch das Chat-Overlay braucht diese Credentials:** Es liest über denselben EventSub-Reader (kein anonymes IRC mehr) — ohne Bot-Login/Token/Client-ID zeigt das Overlay einen Konfigurations-Hinweis.
- **Wichtig für Whispers:** Das Bot-Konto braucht eine **verifizierte Telefonnummer** (Twitch-Pflicht), und der Empfänger (der Streamer) darf „Block Whispers from Strangers“ nicht aktiv haben — sonst antwortet der Bot öffentlich in den Chat zurück.

**2. Twitch-App-Client-ID besorgen:**

- [dev.twitch.tv/console/apps](https://dev.twitch.tv/console/apps) → **Register Your Application** (Name z. B. „Vivid Bot“, OAuth-Redirect passend zum Token-Generator) → **Client-ID** kopieren.

**3. In den App-Settings hinterlegen** (Settings → Chat-Bot → **Owner-Zugriff**):

- [ ] `Bot-Login (Twitch, ohne @)` — Login des Bot-Kontos
- [ ] `Twitch-OAuth-Token` — Token aus Schritt 1 (mit `user:manage:whispers`)
- [ ] `Twitch-App-Client-ID (für Whisper)` — Client-ID aus Schritt 2
- [ ] `Antworten privat senden (Whisper)` — **an** lassen (Standard); ohne Client-ID/Scope fällt die Antwort automatisch öffentlich in den Chat
- [ ] Falls der Bot von einem **Zweitaccount** gesteuert wird: dessen Login in `Owner-Logins (Allow-List)` eintragen (der Kanal-Inhaber ist automatisch Owner)

**4. GitHub (optional, nur für CI-Live-Tests):** Der Laufzeit-Bot braucht **kein** GitHub-Secret. Wer den Whisper-Weg in CI prüfen will, hinterlegt die Werte als Repository-Secrets (`TWITCH_BOT_TOKEN`, `TWITCH_CLIENT_ID`) — nie als Datei im Repo; `scripts/guard_secrets.sh` (CI-Job) bricht sonst ab.

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
→ Pusht einen `vX.Y.Z-alpha`-Tag → CI baut signiert und veröffentlicht als GitHub-Prerelease

### `beta` — 🚦 **Nächster Meilenstein**

> **🧠 Status (2026-08-17): Beta-Tag gesetzt** — `v0.5.0-beta` (5002) wurde am 17.08.2026 per `fastlane release_beta` erzeugt, gepusht und von der CI als GitHub-Release veröffentlicht (GitHub-/Obtainium-Kanal live). Offen für den **Play-Upload**: Play-Unterlagen (Screenshots, Content Rating, Data Safety, `UPLOAD_*`-+`PLAY_JSON_KEY_*`-Secrets) und ≥2 Tester-Freigaben.
>
> **📋 Google Play: Vor dem ersten Beta-Release benötigst du:**
> - Google Play Developer Account ($25 einmalig)
> - App-Signing-Key (bereits vorhanden: `release.keystore` im CI)
> - ~~Privacy Policy~~ → **erledigt:** [PRIVACY.md](PRIVACY.md) — live unter https://thoser666.github.io/Vivid/privacy/
> - App-Icon (512×512, PNG)
> - Screenshots (mind. 2, 16:9 oder 9:16)
> - Store Listing (Kurzbeschreibung, Langbeschreibung)
> - Content Rating Questionnaire
> - Data Safety Section (welche Daten sammelt die App?)
>
> → **Vorlagen bereit:** `fastlane/metadata/android/en-US/` und `de-DE/` mit Titel, Kurz-/Langbeschreibung und Changelogs. Privacy Policy fertig ([PRIVACY.md](PRIVACY.md)), App-Icon (512×512, PNG) vorhanden. Noch ausfüllen:
>   - Screenshots (mind. 2, 16:9 oder 9:16) → `fastlane/metadata/android/images/phoneScreenshots/`
>   - Content Rating Questionnaire (wird in der Play Console ausgefüllt)
>   - Data Safety Section (welche Daten sammelt die App?)

**Kriterien (alle müssen erfüllt sein):**
- [x] ≥17 ✅ Feature-Parität in PARITY.md (17/42 ≈ 40 %)
- [x] Chat implementiert & getestet (Twitch-Scope ✅, PARITY Row 77; Kick/YouTube/OAuth Post-Beta)
- [x] Mindestens ein Overlay/Widget funktioniert (Text-/Info-Widget Zeit/GPS/Geschwindigkeit ✅, PARITY Row 87)
- [ ] Kamera-Vorschau stabil (🚧 in PARITY.md)
- [x] Settings persistent über App-Neustarts (DataStore-basiert, `SettingsRepository`)
- [ ] Keine bekannten Showstopper-Bugs
- [ ] ≥2 manuelle Tester haben bestätigt: „kein Crash in 15 Minuten"
- [ ] Google-Play-Unterlagen vorbereitet (s. o.)

**Befehl:**
```sh
bundle exec fastlane release_beta
```

### `stable` — 🏁 **Endziel**

> **🧠 Erinnerung: Sobald diese Kriterien erfüllt sind → `release_stable`-Lane schreiben und v1.0.0 taggen.**

**Kriterien (alle müssen erfüllt sein):**
- [ ] ≥90 % Feature-Parität (≈39 von 43, 43 = 100 %)
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
