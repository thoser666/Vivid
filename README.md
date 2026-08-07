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

**Professional IRL streaming for Android with all the power of Moblin**

[📱 Download APK](../../releases) • [📚 Documentation](../../wiki) • [🐛 Report Bug](../../issues) • [💬 Discussions](../../discussions)

</div>

---

## ✨ Features

- 🌐 **Multi-Platform Streaming** - Stream to Twitch, YouTube, Facebook, Kick, or your own SRT server
- 📡 **Multi-Network Bonding** - Combine WiFi and mobile data using SRTLA technology for rock-solid streams
- 🎛️ **OBS WebSocket Control** - Control OBS Studio directly from your phone
- 🎨 **Configurable Overlays** - Customize your stream with chat, followers, donations, and more
- 🌍 **Full I18n Support** - Use Vivid in your language
- 📹 **High-Quality Streaming** - Up to 4K resolution at 60fps with H.264/AVC and H.265/HEVC support
- 🔒 **Secure Protocols** - RTMP, RTMPS, SRT, and SRTLA support
- 🔓 **Open Source** - Completely free and open source

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

### Multi-Network Bonding (SRTLA)
Combine multiple internet connections for ultra-stable streams:
- Enable both WiFi and mobile data
- Configure SRTLA server settings
- Adjust network weight distribution
- Monitor connection health in real-time

### OBS WebSocket Integration
Control your OBS Studio setup remotely:
- Switch scenes during your stream
- Start/stop recordings
- Adjust audio levels
- Trigger hotkeys and filters

### Custom Overlays
Personalize your stream with:
- Real-time chat integration
- Follower/subscriber alerts
- Donation notifications
- Custom graphics and branding

## 📊 Comparison

| Feature | Vivid | Other Android Apps |
|---------|-------|--------------------|
| Multi-Network Bonding | ✅ | ❌ |
| OBS WebSocket Control | ✅ | ❌ |
| 4K/60fps Streaming | ✅ | ⚠️ Limited |
| H.265/HEVC Support | ✅ | ❌ |
| Configurable Overlays | ✅ | ⚠️ Basic |
| Open Source | ✅ | ❌ |
| Multi-Protocol (RTMP/SRT) | ✅ | ⚠️ RTMP only |
| I18n Support | ✅ | ❌ |

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
# Unit tests for all modules (63 tests across core, domain, feature-*)
./gradlew testDebugUnitTest

# Lint & static analysis
./gradlew lintDebug
```

Both run automatically in CI (`.github/workflows/android.yml`) on every push and pull request to `develop`/`master`. Fastlane-based release builds (`.github/workflows/android_fastlane.yml`) run tests, debug, and signed release builds.

### Contributing

We welcome contributions! Join our [Discussions](../../discussions) to pitch an idea, then fork and submit a Pull Request:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📱 Screenshots

<div align="center">

| Main Interface | Streaming Setup | Advanced Settings |
|----------------|-----------------|-------------------|
| ![Main](screenshots/main.png) | ![Setup](screenshots/setup.png) | ![Settings](screenshots/settings.png) |

*Screenshots will be added with the next release*

</div>

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
