# 📱 Vivid
### Android version of the open-source Moblin IRL streaming app

<div align="center">

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)
![GitHub release](https://img.shields.io/github/v/release/thoser666/Vivid?style=for-the-badge)
![GitHub stars](https://img.shields.io/github/stars/thoser666/Vivid?style=for-the-badge)
![CI](https://img.shields.io/github/actions/workflow/status/thoser666/Vivid/android.yml?branch=develop&style=for-the-badge&label=CI)

**Professional IRL streaming for Android — aiming for full feature parity with Moblin**

[📲 Install](#-installation) • [📥 Download APK](../../releases) • [📝 Changelog](CHANGELOG.md) • [📚 Documentation](../../wiki) • [🐛 Report Bug](../../issues) • [💬 Discussions](../../discussions)

</div>

---

## 🎯 Goal: Feature Parity with Moblin

Vivid is an Android implementation of the open-source [Moblin](https://github.com/eerimoq/moblin) IRL streaming app. The **end goal** is to be **at least functionally equivalent to Moblin** — every feature Moblin offers should work in Vivid, adapted to the Android platform.

This README tracks that progress honestly: the [Features](#-features) section marks what is already implemented, what is in progress, and what is still planned. The [Parity Status](#-parity-status) table gives the per-feature status at a glance — the detailed work list lives in [PARITY.md](PARITY.md).

> ⚠️ **Note:** Features marked as *planned* are not shipped yet — don't rely on them for production streaming until they land.

---

## 📲 Installation

> Vivid is not on Google Play (yet) — APKs are published as **GitHub Releases**. Everything below is free and takes about 2 minutes.

<p align="center">
  <a href="docs/install-quickstart.svg">
    <img src="docs/install-quickstart.svg" alt="Vivid install quickstart" width="720">
  </a>
  <br>
  <em>📄 One-page quickstart: <a href="docs/install-quickstart.svg">Installations-Infografik (SVG)</a></em>
</p>

### Step 1: Pick your release channel

| Channel | What you get | Best for |
|---------|--------------|----------|
| 🚀 **Latest / Stable** (`v*` tags) | Tested releases with auto-generated release notes | Daily use |
| 🌙 **Nightly** (prerelease) | Fresh build of every new feature, updated daily | Testers, early adopters |
| 🧪 **Alpha** (`v*-alpha`) | First stage of versioned releases | Previewing upcoming features |

📄 The full versioning strategy (versionName/versionCode, stage criteria) is documented in [RELEASE.md](RELEASE.md). The complete release history (stable, alpha, nightly — automatically mirrored from GitHub Releases) lives in [CHANGELOG.md](CHANGELOG.md).

### Step 2: Download the APK

1. Open the [**Releases**](../../releases) page
2. Click **"Latest"** (stable), or expand the prerelease list for **nightly** / **alpha** builds
3. Download **`app-release.apk`** (ignore `mapping.txt` and `output-metadata.json` — those are for developers only)
4. If your browser warns about the file type, confirm "Download anyway"

### Step 3: Allow installation from unknown sources

Android blocks APKs from outside the Play Store by default. On first launch of the APK:

1. Open the downloaded file in your file manager or notification bar
2. Android shows a dialog like *"Install unknown apps"* → tap **Settings**
3. Allow your browser / file manager to install unknown apps
4. Go back and tap **Install**

> 🔒 **Signing:** All official APKs are signed with the same release key. If Android ever shows *"App not installed"*, you may have a corrupted download — delete the file and download it again.

### Step 4: Grant permissions & go live

After installation, Vivid asks for **camera** and **microphone** permissions when you tap **Go Live** (notifications are requested too on Android 13+ — they power the streaming status notification). Then follow the [Platform Setup Guides](#-platform-setup-guides) to connect Twitch, YouTube, Kick, or your own server.

> 🔋 **Background streaming:** While streaming, a persistent notification („Vivid sendet live“) with a **Stop** action keeps the stream alive when you leave the app or turn the screen off. The encoder runs **independent of the camera preview surface** (GL-free pipeline), so the stream also continues if you swipe Vivid away from the recents list or rotate the device — the preview simply reappears when you reopen the app. Tap the notification's Stop button (or the Stop button in the app) to end the stream.

> ✅ **Go-Live Self-Check:** Before starting, Vivid validates the configured URL and stream key on the streaming screen and shows clear messages (e.g. *"Keine Stream-URL konfiguriert"*, *"Nicht unterstütztes Protokoll"*, missing stream key). Blocking problems prevent the start; warnings are shown but don't block.

### 🔄 Automatic updates (Obtainium)

To keep Vivid up to date automatically, use [**Obtainium**](https://obtainium.imranr.dev/):

1. Install Obtainium from [obtainium.imranr.dev](https://obtainium.imranr.dev/) (or its own GitHub releases)
2. Tap **+** → enter `https://github.com/thoser666/Vivid` as the source
3. It auto-detects GitHub Releases — pick your channel:
   - **Stable only:** track the latest release (disable *"Include pre-releases"*)
   - **Nightly:** enable *"Include pre-releases"* to follow every feature build
4. Tap **Add** — Obtainium now checks for updates and installs them automatically

Because every APK is signed with the same key, updates (including nightly → stable and vice versa) install seamlessly over the previous version.

**🧪 Testing the update flow**

1. Install an **older nightly** APK from the [Releases](../../releases) page (e.g. the previous build)
2. Open **Settings** — the installed version is shown at the bottom, and an „⬆ Update verfügbar: …“ badge appears automatically if a newer build exists
3. Add the app in Obtainium as described above (**Include pre-releases** on for nightly)
4. (Optional) In **Settings → „Über Vivid & Updates“** tap **„Nach Updates suchen“** to see the latest GitHub release
5. In Obtainium tap **Update** — the newer APK installs over the old one (same signing key)
6. Reopen **Settings** — the version number has increased and the badge is gone ✅

The About-screen check follows the same rules as [RELEASE.md](RELEASE.md): it only suggests **newer** versions (nightly → nightly/alpha/beta/stable, never a downgrade).

---

## ✨ Features

### ✅ Implemented

- 🎛️ **OBS WebSocket Control** - Control OBS Studio directly from your phone (switch scenes, start/stop recording and streaming)
- 🌐 **Streaming Pipeline** - CameraX-based live streaming to your configured RTMP/SRT ingest (Twitch, YouTube, Kick, or your own server)
- 🔒 **RTMPS (TLS)** - Encrypted ingest via `rtmps://` (verified against RootEncoder 2.6.4: native TLS handshake, port 443); enabled per-platform or via the TLS toggle, standard port 1935 is auto-normalized to 443
- ✅ **Go-Live Self-Check** - Before starting the stream, Vivid validates the configured URL and stream key (missing/invalid URL, unsupported protocol, missing key) and shows clear, actionable German messages directly on the streaming screen — blocking errors prevent starting, warnings (e.g. missing key) are shown but don't block
- 🔍 **Focus Lock** - Toggle autofocus ⇄ infinity lock on the streaming camera to prevent focus hunting (rain drops, dirt on the windshield) during drive/train streams — Moblin #377; works on the actual RootEncoder camera (not just the preview) and can be set before going live
- ⚙️ **Persisted Stream Settings** - Stream URL/key and OBS connection details are stored and reused across sessions
- 🔄 **In-App Update Check** - Settings shows the installed version + an „Update verfügbar“ badge; the About screen (Settings → „Über Vivid & Updates“) adds a manual check against GitHub Releases — ideal for verifying Obtainium updates. Results are cached for 1 hour (DataStore), so opening Settings does not hammer the GitHub API rate limit; the manual check in About always refreshes and shows the **release notes** of the newest build
- 🕹️ **Web Remote Control** - A small LAN server (port 8080, token-protected) exposes the streaming status via `http://<phone-ip>:8080/status` and allows starting/stopping the stream from any browser in the same network — see [Usage](#-usage)
- 🔋 **Background Streaming (Foreground Service)** - The stream keeps running when the app is in the background (home button, screen off) **and even if the Activity is destroyed** (recents swipe, rotation): a foreground service with a persistent notification (live status + stop action) and a partial wake lock keeps the encoder and camera alive, and the encoder runs on a view-independent GL pipeline (RootEncoder Context-constructor) so it never depends on the camera preview surface
- 🔓 **Open Source** - Completely free and open source

### 🚧 In Progress

- 🌍 **I18n Support** - Localization groundwork is in place; translations are being added

### 📋 Planned (Roadmap to Moblin parity)

- 📡 **Multi-Network Bonding (SRTLA)** - Combine WiFi and mobile data for rock-solid streams
- 💬 **Chat Integration** - Twitch/YouTube/Kick chat with emotes and moderation, plus a chat bot (incl. media player control via MediaSession — Apple Music, Spotify, etc.; adapted from Moblin 33.12.0)
- 🎨 **Configurable Overlays** - Chat, follower/donation alerts, custom graphics and branding; text widgets incl. altitude (ascent/descent), GPS coordinates and road/route variables
- 📹 **High-Quality Streaming** - Up to 4K resolution at 60fps with H.264/AVC and H.265/HEVC
- 🔒 **Extended Protocols** - SRTLA, RIST, and WHIP (WebRTC) — RTMPS is already implemented (see below); RTMP-Pull/ingest server mode (community request #407)
- 🕹️ **Remote & Companion Features** - Web remote control (incl. talkback mic selection), game controller support, deep linking
- 📸 **Photo Shoot Quick Button** - Periodically capture high-resolution clean pictures to the gallery (new in Moblin 33.12.0)

## 📋 Platform Setup Guides

<details>
<summary><strong>🟣 Twitch Setup</strong></summary>

1. Go to [Twitch Creator Dashboard](https://dashboard.twitch.tv/)
2. Navigate to **Settings** → **Stream**
3. Copy your **Stream Key**
4. In Vivid:
   - Server: `rtmp://live.twitch.tv/live/`
   - Stream Key: *[paste your key]*

</details>

<details>
<summary><strong>🔴 YouTube Setup</strong></summary>

1. Open [YouTube Studio](https://studio.youtube.com/)
2. Click **"Go Live"** → **"Stream"**
3. Copy the **Stream URL** and **Stream Key**
4. In Vivid:
   - Server: *[paste stream URL]*
   - Stream Key: *[paste stream key]*

</details>

<details>
<summary><strong>🟢 Kick Setup</strong></summary>

1. Go to [Kick Creator Dashboard](https://kick.com/dashboard)
2. Navigate to **Settings** → **Stream Settings**
3. Copy your **Stream Key**
4. In Vivid:
   - Server: `rtmp://ingest.kick.com/live/`
   - Stream Key: *[paste your key]*

</details>

<details>
<summary><strong>🎛️ OBS Studio Setup (WebSocket-Steuerung)</strong></summary>

<p align="center">
  <a href="docs/obs-setup-quickstart.svg">
    <img src="docs/obs-setup-quickstart.svg" alt="Vivid OBS setup quickstart" width="720">
  </a>
  <br>
  <em>📄 One-page quickstart: <a href="docs/obs-setup-quickstart.svg">OBS-Setup-Infografik (SVG)</a></em>
</p>

1. **In OBS aktivieren:** Extras → **WebSocket-Server-Einstellungen** → *„WebSocket-Server aktivieren“* anhaken
2. **Port & Passwort:** Standard-Port `4455` behalten, optional ein **Passwort** setzen (wenn keins gesetzt ist, das Feld in Vivid leer lassen)
3. **IP ermitteln:** LAN-IP des OBS-Rechners (Windows: `ipconfig`, Mac/Linux: `ip addr`) — Handy und PC müssen im **selben WLAN** sein
4. **In Vivid verbinden:** OBS-Steuerung → ⚙️ Einstellungen → Host = IP, Port = `4455`, Passwort + *„Secure connection (wss://)“* passend zum Setup (LAN = ws://, Remote = wss://)

> 🧪 Probleme beim Verbinden? Siehe die [OBS-FAQ-Einträge](#-faq--häufige-probleme) (Verbindung schlägt fehl, Passwort, ws:// vs. wss://).

</details>

<details>
<summary><strong>📡 SRT Server Setup</strong></summary>

1. Set up your SRT server or use a service provider
2. Get your server IP, port, and stream ID
3. In Vivid:
   - Protocol: **SRT**
   - Server: `srt://[server-ip]:[port]`
   - Stream ID: *[your stream ID]*
   - Configure latency and encryption as needed

</details>

<details>
<summary><strong>🕹️ Web Remote Control (Stream per Browser steuern)</strong></summary>

Vivid startet einen kleinen **LAN-Server** (Port **8080**), über den du den Stream-Status abfragen und den Stream starten/stoppen kannst — praktisch, wenn das Handy als Kamera läuft und du vom Laptop steuern willst:

1. **Handy und Laptop ins selbe WLAN** bringen
2. **Token holen:** In Vivid unter **Einstellungen → Web-Remote-Control** steht dein **Remote-Token** (wird einmalig erzeugt und gespeichert)
3. **IP ermitteln:** LAN-IP des Handys (Android: **Einstellungen → WLAN → Verbundenes Netz → Details**)
4. **Status abfragen (ohne Token):**

   ```bash
   curl http://<handy-ip>:8080/status
   # → {"status":"IDLE"} | {"status":"STREAMING"} | ...
   ```

5. **Stream starten/stoppen (mit Token):**

   ```bash
   curl -X POST http://<handy-ip>:8080/start -H "Authorization: Bearer <dein-token>"
   curl -X POST http://<handy-ip>:8080/stop  -H "Authorization: Bearer <dein-token>"
   ```

> 🔒 Der Server läuft nur, solange die App geöffnet ist, und Aktionen benötigen das Token — im selben WLAN ist die Verbindung unverschlüsselt (wie bei OBS ws://), außerhalb des LAN nicht erreichbar.

> ℹ️ **Android 17 (API 37):** Seit `targetSdk 37` verlangt Android die **„Zugriff auf lokale Netzwerke“-Berechtigung** (`ACCESS_LOCAL_NETWORK`) für LAN-Server. Falls `/status` nicht erreichbar ist, in Vivid unter **Einstellungen → Web-Remote-Control** auf **„LAN-Zugriff für Remote-Control erlauben“** tippen (der Server startet danach automatisch neu).

</details>

---

## ❓ FAQ — Häufige Probleme

<details>
<summary><strong>🔧 „App not installed“ beim Installieren</strong></summary>

Meistens ist der Download beschädigt oder unvollständig:

1. Lösche die heruntergeladene `app-release.apk` aus deinem Download-Ordner
2. Lade sie **neu** von der [Releases-Seite](../../releases) herunter
3. Prüfe, dass die Datei **~7 MB** groß ist (eine deutlich kleinere Datei ist ein fehlgeschlagener Download)
4. Versuche es erneut — wenn der Fehler bleibt, installiere über Obtainium (unten), das die Datei verifiziert herunterlädt

> Falls du Vivid von einer **älteren Version** aktualisierst und der Fehler weiterhin auftritt: Deinstalliere zuerst die alte Version (Achtung: Stream-Einstellungen gehen dabei verloren) und installiere dann neu.

</details>

<details>
<summary><strong>📥 „Unbekannte Quelle nicht erlaubt“ / Installations-Button ist grau</strong></summary>

Android blockiert APKs außerhalb des Play Stores standardmäßig:

1. Beim ersten Installationsversuch erscheint ein Hinweis → tippe auf **Einstellungen**
2. Erlaube dem verwendeten Browser / Dateimanager, **unbekannte Apps zu installieren**
3. Gehe zurück und tippe erneut auf **Installieren**
4. Falls kein Hinweis erscheint: **Einstellungen → Apps → [Browser/Dateimanager] → Unbekannte Apps installieren** → erlauben

</details>

<details>
<summary><strong>🎥 „Kein Bild“ / Kamera bleibt schwarz beim Streaming</strong></summary>

1. Prüfe die **Kamera-Berechtigung**: Einstellungen → Apps → Vivid → Berechtigungen → Kamera = **Erlauben** (nicht „Nur während der Nutzung“ kann bei Hintergrund-Streaming Probleme machen)
2. Teste die Kamera in einer anderen App (z. B. der Standard-Kamera-App) — wenn sie dort auch schwarz ist, liegt es am Gerät
3. Falls du mehrere Kameras hast: Wähle in Vivid die **richtige Kamera** (Front-/Rückkamera) aus
4. Starte den Stream neu (Stop → Go Live)

> ⚠️ Android schließt die Kamera, wenn eine andere App sie belegt (z. B. eine offene Kamera-App oder ein Video-Call). Schließe solche Apps vor dem Streamen.

</details>

<details>
<summary><strong>📶 Verbindungsabbrüche / Stream bricht regelmäßig ab</strong></summary>

1. **Signal prüfen:** Mobiles Internet + WiFi — wechsle notfalls den Netzwerktyp und teste erneut
2. **Ingest-Server wechseln:** Wähle in Vivid einen anderen RTMP/SRT-Server deiner Plattform (z. B. einen näher gelegenen)
3. **Stream-Key prüfen:** Ein falscher oder abgelaufener Stream-Key führt zum Abbruch nach wenigen Sekunden — neu kopieren (auf Twitch wird der Key bei jedem Zurücksetzen ungültig)
4. **OBS-Steuerung deaktivieren:** Wenn du OBS nicht benutzt, entferne die OBS-Verbindungsdaten in den Einstellungen — eine fehlgeschlagene OBS-Verbindung kann den Stream-Start blockieren
5. **Latenz erhöhen:** Bei SRT kann eine höhere Latenz (200–500 ms) instabile Verbindungen glätten

> 📡 **Tipp für unterwegs:** Ein stabilerer Upload als der nötige ist wichtiger als die maximale Auflösung — senke Qualität/Auflösung bei schwachem Signal, statt den Stream abreißen zu lassen.

</details>

<details>
<summary><strong>🔑 Stream startet, aber Plattform zeigt „Kein Signal“ / Fehler im Dashboard</strong></summary>

1. Prüfe, ob **Server-URL und Stream-Key** in Vivid exakt den Werten aus dem Plattform-Dashboard entsprechen (kein Leerzeichen, keine zusätzlichen Zeichen)
2. Vergleiche mit den [Platform Setup Guides](#-platform-setup-guides) oben
3. Twitch: Der Server ist `rtmp://live.twitch.tv/live/` — den **Key** nie mit dem Server verwechseln
4. Teste den Key zuerst im Plattform-Dashboard („Test stream“), bevor du Vivid startest

</details>

<details>
<summary><strong>🎛️ OBS-Steuerung: Verbindung schlägt fehl</strong></summary>

Vivid kann OBS Studio nur steuern, wenn der **WebSocket-Server in OBS aktiv** und Vivid im selben Netzwerk erreichbar ist:

1. **WebSocket-Server aktivieren:** In OBS unter **Extras → WebSocket-Server-Einstellungen** → Haken bei *„WebSocket-Server aktivieren“* setzen. Erst dann lauscht OBS auf Verbindungen.
2. **Host prüfen:** In Vivid die **IP-Adresse des OBS-Rechners** eintragen (z. B. `192.168.1.50`) — `localhost` funktioniert nur, wenn OBS auf demselben Gerät läuft. IP unter Windows mit `ipconfig`, unter macOS/Linux mit `ip addr` herausfinden.
3. **Port prüfen:** Standard ist **4455** — in OBS (WebSocket-Server-Einstellungen) nachsehen, ob ein anderer Port konfiguriert ist, und denselben in Vivid eintragen.
4. **Gleiches Netzwerk:** Handy und OBS-Rechner müssen im **selben WLAN/LAN** sein (bzw. über VPN verbunden) — prüfe, ob z. B. das Handy im Mobilfunknetz hängt.
5. **Firewall:** Die Windows-Firewall muss eingehende Verbindungen auf Port 4455 erlauben (der OBS-Installer legt meist eine Regel an — nach Updates prüfen).
6. **Passwort & TLS:** Stimmen Passwort und die Option *„Secure connection (wss://)“* mit den OBS-Einstellungen überein? Siehe die nächsten beiden FAQ-Einträge.

> 🧪 **Schnelltest:** Öffne im Browser auf dem Handy `ws://<OBS-IP>:4455` — erscheint eine Meldung, dass die Verbindung hergestellt wurde (auch wenn sie danach geschlossen wird), ist OBS erreichbar und das Problem liegt an Passwort/TLS.

</details>

<details>
<summary><strong>🔑 OBS: Passwort vergessen / „Authentifizierung fehlgeschlagen“</strong></summary>

OBS zeigt das WebSocket-Passwort **nie wieder an** — du kannst es nur neu setzen:

1. In OBS: **Extras → WebSocket-Server-Einstellungen** öffnen
2. Haken bei **„Passwort aktivieren“** setzen (falls noch nicht geschehen) und ein **neues Passwort** eingeben
3. **OK** klicken — das neue Passwort gilt sofort
4. In Vivid unter **OBS-Einstellungen** das neue Passwort eintragen und erneut verbinden

> ⚠️ **Kein Passwort in OBS gesetzt?** Dann lasse das Passwort-Feld in Vivid **leer** — ein eingegebenes, falsches Passwort führt zum Abbruch der Verbindung. Umgekehrt: Verlangt OBS ein Passwort und Vivid hat keins, bricht die Verbindung ebenfalls ab (Vivid bricht die Verbindung bewusst ab, statt unauthentifiziert weiterzumachen).

**Tipp gegen vergessene Passwörter:** Verwende einen Passwort-Manager oder ein einheitliches LAN-Passwort — OBS selbst bietet keine „Passwort anzeigen“-Funktion.

</details>

<details>
<summary><strong>🔐 OBS: ws:// oder wss://? (Secure connection)</strong></summary>

Die Option **„Secure connection (wss://)“** in den OBS-Einstellungen von Vivid muss zum OBS-Setup passen:

| Verbindung | Wann verwenden | OBS-Voraussetzung |
|------------|----------------|-------------------|
| **ws://** (Standard, Schalter aus) | OBS im **eigenen LAN/WLAN** | Keine — OBS liefert standardmäßig Klartext-WebSockets auf Port 4455 |
| **wss://** (Schalter an) | OBS **remote über das Internet** | OBS muss TLS konfiguriert haben (Zertifikat) oder ein TLS-Reverse-Proxy (z. B. Caddy/nginx) davor laufen |

1. **Standard-OBS-Setup im LAN = ws://** — also den Schalter **aus** lassen
2. Für Remote-Zugriff (z. B. von unterwegs über Port-Forwarding/VPN) **wss://** aktivieren — **nur** wenn OBS tatsächlich TLS anbietet, sonst schlägt die Verbindung fehl
3. Zeigt OBS im Log „WebSocket server started on ws://…“ bzw. „wss://…“, siehst du direkt, welches Protokoll aktiv ist

> 🔒 **Sicherheit:** Über öffentliches Internet wird **immer wss://** empfohlen — unverschlüsseltes ws:// macht das OBS-Passwort (und damit die Steuerung) für jeden im Netz lesbar. Im heimischen WLAN ist ws:// vertretbar.

</details>

<details>
<summary><strong>🔇 Kein Ton beim Streaming (Audio fehlt)</strong></summary>

Bild läuft, aber Zuschauer hören nichts? Das sind die häufigsten Ursachen:

1. **Mikrofon-Berechtigung prüfen:** Einstellungen → Apps → Vivid → Berechtigungen → **Mikrofon = Erlauben** — ohne sie startet der Audio-Encoder nicht (Vivid zeigt „Failed to prepare audio/video“)
2. **Mikrofon ist belegt:** Android gibt das Mikrofon nur an **eine** App gleichzeitig — schließe andere Apps, die es nutzen (Anrufe, Sprachassistent, andere Streaming-/Aufnahme-Apps), und starte den Stream neu
3. **Bluetooth trennen:** Ist ein BT-Headset/Headset verbunden, nutzt Android dessen Mikrofon — für IRL-Streaming das Bluetooth-Gerät trennen oder in den Bluetooth-Einstellungen aufs Handy-Mikrofon umstellen
4. **Lautstärke:** Prüfe die **Media-Lautstärke** (nicht nur Klingelton) — bei 0 ist auch das Streaming stumm
5. **Neu starten:** Nach Berechtigungs-/Bluetooth-Änderungen hilft ein kompletter App-Neustart (Stream stoppen → App beenden → neu starten)

> 🎧 **Plattformseitig:** Auch im Plattform-Dashboard prüfen, ob der Audiopegel ankommt (z. B. Twitch Stream Manager) — so unterscheidest du ein Handy- von einem Plattform-Problem.

</details>

<details>
<summary><strong>🔄 Updates kommen nicht an (Obtainium)</strong></summary>

1. Prüfe, ob du in Obtainium **Pre-Releases** aktiviert hast (für Nightly-Builds) — Stable-Nutzer bekommen nur `v*`-Releases
2. Tippe in Obtainium auf „Aktualisieren“ (manueller Check), um den letzten Stand abzurufen
3. Alle offiziellen APKs sind mit demselben Schlüssel signiert — ein Update sollte immer installierbar sein; falls „App not installed“ erscheint, siehe FAQ oben

</details>

---

## 🧱 Tech Stack

| Area | Technology | Version |
|------|-----------|---------|
| Language | Kotlin | 2.2.20 |
| Build | Android Gradle Plugin | 9.2.1 |
| SDK | minSdk / compile+target | 24 / 36 |
| UI | Jetpack Compose (BOM) | 2025.09.00 |
| DI | Hilt + KSP | 2.59.2 / 2.3.11 |
| Networking | OkHttp / Ktor | 5.3.2 / 3.5.0 |
| Camera | CameraX | 1.6.1 |
| Media | Media3 (ExoPlayer) | 1.9.0 |
| Serialization | kotlinx.serialization | 1.9.0 |
| Code Analysis | Sentry Gradle Plugin | 6.6.0 |

All versions are centrally defined in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## 📊 Parity Status

Status: ✅ implemented · 🚧 in progress · 📋 planned

> 📋 Full per-feature tracking (responsible module + open tasks) lives in [PARITY.md](PARITY.md).

| Moblin Feature | Vivid | Notes |
|----------------|-------|-------|
| OBS WebSocket Control | ✅ | Scenes, recording, stream start/stop |
| OBS Config via QR Code | ✅ | Import `obsws://` / `obswebsocket` connect info (host, port, password) from the OBS QR code |
| Streaming (RTMP / SRT) | ✅ | Configurable URL/key via settings |
| RTMPS (TLS ingest) | ✅ | `rtmps://` via RootEncoder 2.6.4, port 443, TLS verified |
| Background Streaming (Foreground Service) | ✅ | Stream continues in background: notification + wake lock, stop action |
| Go-Live Self-Check | ✅ | Validates URL/key before starting, clear error messages |
| Focus Lock (∞) | ✅ | Autofocus ⇄ infinity lock toggle on the streaming camera (Moblin #377) |
| Persisted Stream Settings | ✅ | Stream & OBS config across sessions |
| I18n Support | 🚧 | Groundwork in place, translations pending |
| H.264/H.265, up to 4K/60fps | 📋 | Pipeline in place, quality targets planned |
| Multi-Network Bonding (SRTLA) | 📋 | SRTLA algorithm to be ported |
| Chat + Emotes + Moderation | 📋 | `feature-chat` module scaffolded |
| Chat-Bot Media Player Control | 📋 | Generic media control via MediaSession (Apple Music, Spotify, …); Android adaptation of Moblin 33.12.0 |
| Overlays & Widgets | 📋 | `feature-widgets` module scaffolded; text widgets incl. altitude ascent/descent, GPS coords and road variables |
| Audio Tools (levels, muting, talk-back) | 📋 | |
| Extended Protocols (RIST, WHIP) | 📋 | |
| RTMP-Pull / Ingest (Server mode) | 📋 | Community request #407; Moblin offers ingests (RTMP, SRT(LA), RIST, RTSP, WHIP) |
| Web Remote Control | ✅ | LAN server (port 8080) with token auth: status, start/stop |
| Photo Shoot Quick Button | 📋 | Periodic high-res photos to the gallery; new in Moblin 33.12.0 |
| Game Controller Support | 📋 | |
| Deep Linking (`moblin://`) | 📋 | |

---

## 🛠️ Development

### Project Structure

| Module | Purpose |
|--------|---------|
| `app` | Application entry point, navigation, DI wiring |
| `core` | Shared utilities and base classes |
| `domain` | Business logic, models, use cases |
| `data` | Repositories and data sources |
| `feature-streaming` | Streaming pipeline (CameraX, encoding) |
| `feature-chat` | Live chat integration |
| `feature-settings` | App settings |
| `feature-widgets` | Stream overlays and widgets |
| `feature-obs-control` | OBS Studio WebSocket control |

### Building from Source

```bash
# Clone the repository
git clone https://github.com/thoser666/Vivid.git
cd Vivid

# Open in Android Studio
# OR build with Gradle
./gradlew assembleDebug
```

### Running Tests & Lint

```bash
# Unit tests for all modules (236 tests across core, app, feature-*)
./gradlew testDebugUnitTest

# Live check: run the in-app UpdateChecker against the real GitHub releases
# (disabled in the normal test run; CI runs it with GITHUB_TOKEN)
./gradlew :core:testDebugUnitTest --tests "com.vivid.core.update.LiveUpdateCheckTest" -PliveUpdateCheck=true

# Lint & static analysis
./gradlew lintDebug
```

Both run automatically in CI (`.github/workflows/android.yml`) on every push and pull request to `develop`/`master`.

### Release Builds (Fastlane)

Release builds use [Fastlane](https://fastlane.tools) (see [`Gemfile`](Gemfile)); dependencies are pinned in `Gemfile.lock`:

```bash
# Install the Ruby toolchain and pinned gems (fastlane 2.237.0 and friends)
bundle install

# Run the same lanes as CI
bundle exec fastlane test
bundle exec fastlane build_debug
bundle exec fastlane build_release   # requires the signing secrets from CI

# Build the release APK and publish it as a GitHub release
# (requires the gh CLI and GH_TOKEN; tag defaults to the nearest git tag)
bundle exec fastlane release_github

# Create and push an alpha release tag (runs tests, auto-versions)
bundle exec fastlane release_alpha
```

The `android_fastlane.yml` workflow runs these lanes in CI. Two release paths are automated:

- **Every push to `develop`** (i.e. every newly implemented feature) builds the signed release APK and publishes it as a rolling **`nightly` prerelease** with a version derived from the git tag + CI run number. The nightly release is replaced on each build, so it always contains the latest feature build. In addition, a **scheduled build runs daily at 06:00 UTC** (and the workflow can be triggered manually via `gh workflow run android_fastlane.yml --ref develop`), so a fresh nightly exists even without new commits.
- **Pushing a `v*` tag** (e.g. `git tag v0.2.0 && git push origin v0.2.0`) publishes a **stable GitHub release** with auto-generated notes.

Both release the same signed APK; Obtainium users can track the latest release for stable versions or enable *pre-releases* to follow the nightly builds. If a lockfile update is needed (e.g. a security bump), run `bundle update <gem>` and commit the new `Gemfile.lock`.

> 📋 **Release stages & criteria** (nightly → alpha → beta → stable) are documented in [RELEASE.md](RELEASE.md). The [PARITY.md](PARITY.md) tracker determines which stage is active.

### Contributing

We welcome contributions! Join our [Discussions](../../discussions) to pitch an idea, then fork and submit a Pull Request:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📱 Screenshots

*Screenshots will be added with the next release.*

## 🆘 Support & Community

- 📚 **Documentation**: Check our [Wiki](../../wiki) for detailed guides
- 🐛 **Bug Reports**: Found an issue? [Report it here](../../issues)
- 💬 **Discussions**: Join our [community discussions](../../discussions)
- 💡 **Feature Requests**: Have an idea? [Share it with us](../../issues/new?template=feature_request.md)

## 🙏 Acknowledgments

- **[Moblin](https://github.com/eerimoq/moblin)** - The original iOS app that inspired this project
- **Erik Moqvist** - Creator of the original Moblin
- The entire open-source streaming community
- All contributors and beta testers

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔒 Privacy

Vivid does not collect, sell, or share your personal data. Crash reports are sent to Sentry only if you enable them in Settings. See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

## ⭐ Show Your Support

If Vivid helps you with your streaming, please consider:
- ⭐ **Starring** this repository
- 🍴 **Forking** and contributing
- 📢 **Sharing** with other streamers
- 💝 **Supporting** the original Moblin project

---

<div align="center">

**Made with ❤️ for the IRL streaming community**

[⬆ Back to Top](#-vivid)

</div>
