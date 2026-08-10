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

[📲 Install](#-installation) • [📥 Download APK](../../releases) • [📚 Documentation](../../wiki) • [🐛 Report Bug](../../issues) • [💬 Discussions](../../discussions)

</div>

---

## 🎯 Goal: Feature Parity with Moblin

Vivid is an Android implementation of the open-source [Moblin](https://github.com/eerimoq/moblin) IRL streaming app. The **end goal** is to be **at least functionally equivalent to Moblin** — every feature Moblin offers should work in Vivid, adapted to the Android platform.

This README tracks that progress honestly: the [Features](#-features) section marks what is already implemented, what is in progress, and what is still planned. The [Parity Status](#-parity-status) table gives the per-feature status at a glance — the detailed work list lives in [PARITY.md](PARITY.md).

> ⚠️ **Note:** Features marked as *planned* are not shipped yet — don't rely on them for production streaming until they land.

---

## 📲 Installation

> Vivid is not on Google Play (yet) — APKs are published as **GitHub Releases**. Everything below is free and takes about 2 minutes.

### Step 1: Pick your release channel

| Channel | What you get | Best for |
|---------|--------------|----------|
| 🚀 **Latest / Stable** (`v*` tags) | Tested releases with auto-generated release notes | Daily use |
| 🌙 **Nightly** (prerelease) | Fresh build of every new feature, updated daily | Testers, early adopters |
| 🧪 **Alpha** (`v*-alpha`) | First stage of versioned releases | Previewing upcoming features |

📄 The full versioning strategy (versionName/versionCode, stage criteria) is documented in [RELEASE.md](RELEASE.md).

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

After installation, Vivid asks for **camera** and **microphone** permissions (needed for streaming). Then follow the [Platform Setup Guides](#-platform-setup-guides) to connect Twitch, YouTube, Kick, or your own server.

### 🔄 Automatic updates (Obtainium)

To keep Vivid up to date automatically, use [**Obtainium**](https://obtainium.imranr.dev/):

1. Install Obtainium from [obtainium.imranr.dev](https://obtainium.imranr.dev/) (or its own GitHub releases)
2. Tap **+** → enter `https://github.com/thoser666/Vivid` as the source
3. It auto-detects GitHub Releases — pick your channel:
   - **Stable only:** track the latest release (disable *"Include pre-releases"*)
   - **Nightly:** enable *"Include pre-releases"* to follow every feature build
4. Tap **Add** — Obtainium now checks for updates and installs them automatically

Because every APK is signed with the same key, updates (including nightly → stable and vice versa) install seamlessly over the previous version.

---

## ✨ Features

### ✅ Implemented

- 🎛️ **OBS WebSocket Control** - Control OBS Studio directly from your phone (switch scenes, start/stop recording and streaming)
- 🌐 **Streaming Pipeline** - CameraX-based live streaming to your configured RTMP/SRT ingest (Twitch, YouTube, Kick, or your own server)
- ⚙️ **Persisted Stream Settings** - Stream URL/key and OBS connection details are stored and reused across sessions
- 🔓 **Open Source** - Completely free and open source

### 🚧 In Progress

- 🌍 **I18n Support** - Localization groundwork is in place; translations are being added

### 📋 Planned (Roadmap to Moblin parity)

- 📡 **Multi-Network Bonding (SRTLA)** - Combine WiFi and mobile data for rock-solid streams
- 💬 **Chat Integration** - Twitch/YouTube/Kick chat with emotes and moderation
- 🎨 **Configurable Overlays** - Chat, follower/donation alerts, custom graphics and branding
- 📹 **High-Quality Streaming** - Up to 4K resolution at 60fps with H.264/AVC and H.265/HEVC
- 🔒 **Extended Protocols** - RTMPS, SRTLA, RIST, and WHIP (WebRTC)
- 🕹️ **Remote & Companion Features** - Web remote control, game controller support, deep linking

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
<summary><strong>📡 SRT Server Setup</strong></summary>

1. Set up your SRT server or use a service provider
2. Get your server IP, port, and stream ID
3. In Vivid:
   - Protocol: **SRT**
   - Server: `srt://[server-ip]:[port]`
   - Stream ID: *[your stream ID]*
   - Configure latency and encryption as needed

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
| Streaming (RTMP / SRT) | ✅ | Configurable URL/key via settings |
| Persisted Stream Settings | ✅ | Stream & OBS config across sessions |
| I18n Support | 🚧 | Groundwork in place, translations pending |
| H.264/H.265, up to 4K/60fps | 📋 | Pipeline in place, quality targets planned |
| Multi-Network Bonding (SRTLA) | 📋 | SRTLA algorithm to be ported |
| Chat + Emotes + Moderation | 📋 | `feature-chat` module scaffolded |
| Overlays & Widgets | 📋 | `feature-widgets` module scaffolded |
| Audio Tools (levels, muting, talk-back) | 📋 | |
| Extended Protocols (RTMPS, RIST, WHIP) | 📋 | |
| Web Remote Control | 📋 | |
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
# Unit tests for all modules (69 tests across core, domain, feature-*)
./gradlew testDebugUnitTest

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

- **Every push to `develop`** (i.e. every newly implemented feature) builds the signed release APK and publishes it as a rolling **`nightly` prerelease** with a version derived from the git tag + CI run number. The nightly release is replaced on each build, so it always contains the latest feature build.
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
