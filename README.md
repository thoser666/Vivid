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

[📱 Download APK](../../releases) • [📚 Documentation](../../wiki) • [🐛 Report Bug](../../issues) • [💬 Discussions](../../discussions)

</div>

---

## 🎯 Goal: Feature Parity with Moblin

Vivid is an Android implementation of the open-source [Moblin](https://github.com/eerimoq/moblin) IRL streaming app. The **end goal** is to be **at least functionally equivalent to Moblin** — every feature Moblin offers should work in Vivid, adapted to the Android platform.

This README tracks that progress honestly: the [Features](#-features) section marks what is already implemented, what is in progress, and what is still planned. The [Parity Status](#-parity-status) table gives the per-feature status at a glance — the detailed work list lives in [PARITY.md](PARITY.md).

> ⚠️ **Note:** Features marked as *planned* are not shipped yet — don't rely on them for production streaming until they land.

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

## 🚀 Quick Start

### Requirements
- Android 7.0 (API level 24) or higher
- Camera and microphone permissions
- Stable internet connection (WiFi + mobile data recommended)

### Installation

1. **Download the APK** from the [Releases](../../releases) page
2. **Enable Unknown Sources** in your Android settings
3. **Install the APK** and grant necessary permissions
4. **Launch Vivid** and start streaming!

> 🔄 **Automatic updates:** APKs are published as [GitHub Releases](../../releases). To receive them automatically, install the [Obtainium](https://obtainium.imranr.dev/) app, add this repository's GitHub releases as a source, and Vivid will update itself whenever a new release is published.

### Basic Setup

1. Open Vivid and tap **"Add Stream"**
2. Choose your platform (Twitch, YouTube, etc.)
3. Enter your stream key/credentials
4. Configure video quality and settings
5. Hit **"Go Live"** and start streaming!

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

## 🔧 Advanced Features

### Multi-Network Bonding (SRTLA) 📋 Planned
Combine multiple internet connections for ultra-stable streams — the SRTLA algorithm will be ported as part of the [Moblin parity goal](#-goal-feature-parity-with-moblin).

### OBS WebSocket Integration ✅ Implemented
Control your OBS Studio setup remotely:
- Switch scenes during your stream
- Start/stop recordings
- Adjust audio levels
- Trigger hotkeys and filters

### Custom Overlays 📋 Planned
Chat overlays, follower/subscriber alerts, donation notifications, and custom graphics will be built on top of the `feature-widgets` module.

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
```

The `android_fastlane.yml` workflow runs these lanes in CI. Two release paths are automated:

- **Every push to `develop`** (i.e. every newly implemented feature) builds the signed release APK and publishes it as a rolling **`nightly` prerelease** with a version derived from the git tag + CI run number. The nightly release is replaced on each build, so it always contains the latest feature build.
- **Pushing a `v*` tag** (e.g. `git tag v0.0.2 && git push origin v0.0.2`) publishes a **stable GitHub release** with auto-generated notes.

Both release the same signed APK; Obtainium users can track the latest release for stable versions or enable *pre-releases* to follow the nightly builds. If a lockfile update is needed (e.g. a security bump), run `bundle update <gem>` and commit the new `Gemfile.lock`.

### Contributing

We welcome contributions! Join our [Discussions](../../discussions) to pitch an idea, then fork and submit a Pull Request:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

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
