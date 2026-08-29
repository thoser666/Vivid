# 🏗️ Architektur-Übersicht

> Technische Übersicht über die Vivid-Architektur.

## Modulstruktur

Vivid folgt einer **Multi-Module-Architektur** mit klaren Abhängigkeiten:

```
app/                    ← Haupt-App (Hilt, Navigation, UI)
├── core/               ← Basis-Logging, Remote-Control, Theme
├── data/               ← DataStore, Settings-Repository
├── domain/             ← Data-Classes (AppSettings, StreamScene)
├── feature-streaming/  ← Streaming-Engine, Kamera, Video-Quellen
├── feature-settings/   ← Settings-Screens, ViewModel
├── feature-chat/       ← Twitch-Chat, Bot, EventSub, Emotes
├── feature-widgets/    ← Text/Info-Widget
└── feature-obs-control/← OBS WebSocket-Integration
```

## Kernkomponenten

### 1. Streaming-Engine (`feature-streaming`)

Zuständig für:
- **Video-Quellen**: Kamera, Screen-Capture, Video-Player
- **Encoder**: RootEncoder (RTMP/SRT)
- **Video-Effekte**: Filter, LUTs, Color-Spaces
- **Kamera-Steuerung**: Zoom, Fokus, Torch, Stabilisierung

### 2. Chat-System (`feature-chat`)

Zuständig für:
- **Twitch-Integration**: EventSub (Lesen), Helix (Senden)
- **KI-Bot**: OpenAI-kompatibles LLM
- **Emotes**: Twitch, BTTV, FFZ, 7TV
- **Badges**: Broadcaster, Mod, Sub

### 3. Settings (`feature-settings`)

Zuständig für:
- **UI**: Kategorien-Übersicht, Sub-Screens
- **ViewModel**: State-Management, Persistenz
- **DataStore**: AppSettings, Theme, Chat-Bot

### 4. Remote-Control (`core`)

Zuständig für:
- **Web-Server**: Ktor (HTTP + WebSocket)
- **Authentifizierung**: Bearer-Token
- **API**: Status, Logs, Steuerung

## Datenfluss

```
User Input → SettingsViewModel → SettingsRepository → DataStore
                ↓
         StreamingEngine ← CameraControls
                ↓
         RootEncoder → Twitch/YouTube/Kick
                ↓
         ChatOverlay ← TwitchEventSubReader
```

## Technologie-Stack

| Bereich | Technologie | Version |
|---------|-------------|---------|
| Sprache | Kotlin | 2.2.20 |
| Build | Android Gradle Plugin | 9.3.1 |
| UI | Jetpack Compose | 2025.09.00 |
| DI | Hilt | 2.59.2 |
| Networking | OkHttp + Ktor | 5.3.2 / 3.5.2 |
| Camera | RootEncoder | 2.7.5 |
| Logging | Timber | 1.11.0 |
| Error Tracking | Sentry | 6.6.0 |

## Build-Varianten

| Variante | Application ID | Sentry | Verwendung |
|----------|----------------|--------|------------|
| `standardDebug` | `com.vivid.debug` | ✅ | Lokale Entwicklung |
| `standardRelease` | `com.vivid` | ✅ | GitHub Releases |
| `fossDebug` | `com.vivid.foss.debug` | ❌ | F-Droid Entwicklung |
| `fossRelease` | `com.vivid.foss` | ❌ | F-Droid Haupt-Repo |

## CI/CD

- **Android CI**: Build + Tests bei jedem Push
- **Fastlane**: Automatisierte Releases (Alpha/Beta/Stable)
- **F-Droid**: Eigener Repo-Server auf GitHub Pages
- **Dependabot**: Automatische Dependency-Updates

## Weiterführende Dokumentation

- [PARITY.md](https://github.com/thoser666/Vivid/blob/develop/PARITY.md) - Feature-Parität mit Moblin
- [RELEASE.md](https://github.com/thoser666/Vivid/blob/develop/RELEASE.md) - Release-Strategie
- [ai-chat-bot.md](../ai-chat-bot.md) - Bot-Architektur
