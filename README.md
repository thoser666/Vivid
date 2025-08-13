Vivid
License: MIT
Android

Vivid is an Android rebuild of the acclaimed open-source Moblin IRL streaming app. It brings professional-grade mobile broadcasting to Android devices, offering:

Multi-Platform Streaming: RTMP/RTMPS, SRT (incl. SRTLA), RIST, WebRTC
Adaptive Bonding: Combine cellular, Wi-Fi, and Ethernet for rock-solid connections
High-Quality Video: H.264/H.265 support, up to 4K @ 60 FPS
OBS Integration: Remote control via OBS WebSocket—scene switching, audio levels, snapshots
Customizable Overlays & Widgets: Alerts, chat, weather, timers, maps, QR codes and more
Deep Linking: vivid:// URL schemes for preconfigured streams and settings
Local Recording: Save MP4 captures alongside your live broadcast
I18n & RTL: Full localization (English, French, German, Spanish, Polish, Chinese, Swedish, etc.)
Secure & Extensible: Hilt DI, Kotlin Coroutines, Room, Retrofit, and Jetpack Compose architecture
Tech Stack
Kotlin · Jetpack Compose · Hilt · Coroutines & Flow
ExoPlayer · CameraX · Retrofit · Room · DataStore
Accompanist (Insets, Permissions, Pager, System UI)
Coil · Material 3 · Navigation-Compose
CI via GitHub Actions (lint, tests, assemble)
Table of Contents
Getting Started
Features
Architecture
Customization & Deep Linking
Localization
Contributing
License
Getting Started
git clone https://github.com/your-username/vivid.git
cd vivid
./gradlew build
Open in Android Studio, run on an emulator or device (minSdk 24), and enjoy.

Features
Stream Anywhere: Broadcast to Twitch, YouTube, Kick, Facebook or any RTMP/SRT ingest.
Network Bonding: Aggregate multiple connections for maximum uptime.
OBS Remote: Use OBS WebSocket to manage scenes, start/stop streams, monitor audio.
Overlay Widgets: Chat, alerts, weather, map, QR code—fully customizable via JSON URLs.
Recording & Effects: Keep an MP4 backup, apply video filters (grayscale, letterbox, etc.).
Deep Links: Preload stream settings via vivid://?{...} for instant setup.
Localization: Switch languages on-the-fly; RTL support built-in.
Architecture
Modular Gradle Setup: app, core, data, domain, and feature modules
Single-Activity Compose: MainActivity hosts NavHost, screens are composables
MVVM + Hilt: ViewModels expose StateFlow, injected via Hilt
Repo Pattern: data module implements domain interfaces
Clean UI Layer: Material 3 theming, dynamic layouts, portrait & landscape modes
Customization & Deep Linking
Leverage deep links to automate your workflow:

vivid://?{"streams":[{"name":"Test","url":"srtla://...","video":{"codec":"H.265"},"obs":{"webSocketUrl":"ws://...","webSocketPassword":"secret"}}]}
Embed custom overlay widgets by supplying JSON definitions in your deep link.

Localization
All user-facing text is resource-driven. Add new locales by creating values-<lang>/strings.xml.
Switch device language or override at runtime via AppCompatDelegate.setApplicationLocales().

Contributing
We welcome contributions! Please follow these guidelines:

Fork the repo and create a topic branch (feature/…, bugfix/…).
Write clear, atomic commits.
Submit a Pull Request with a descriptive title and detailed description.
Ensure CI passes: ./gradlew spotlessApply check assemble.
Review code for security, performance, and accessibility.
See CONTRIBUTING.md for more details.
