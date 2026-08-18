# 🔒 Privacy Policy for Vivid

**Last updated:** 2026-08-18

## Introduction

Vivid is an open-source live streaming app for Android. This policy explains what data the app accesses, how it is used, and your choices.

**The short version:** Vivid has no servers of its own. Your camera/microphone stream goes directly to a streaming server **you** configure, chat messages are sent to **Twitch** and — if you enable the AI chatbot — to a **language-model provider you configure**. Crash reports are sent to **Sentry** automatically. Vivid does not sell or share personal data with advertisers, and it does not require an account.

## Data the App Accesses

### Camera & Microphone
- **What:** Live video and audio from your device camera and microphone.
- **Why:** To encode and stream live content to the RTMP/SRT server URL you provide.
- **Where it goes:** Directly to the streaming server **you** configure in Settings. Vivid does not operate streaming infrastructure — all stream data goes to your chosen server (e.g. Twitch, YouTube, Kick, or your own server). The stream keeps running via a foreground service while the app is in the background.
- **Storage:** Stream URLs and keys are stored locally on your device (Android DataStore). They only leave your device when the app connects to the server you specified.

### Location (GPS) — Info Widget
- **What:** GPS coordinates and speed, read while the „Time/GPS/Speed“ info widget is active.
- **Why:** To display position and speed as an overlay on the stream preview.
- **Where it goes:** The overlay is part of the video preview — **if you enable the widget while streaming, the coordinates and speed are part of what gets streamed** to your configured server. Vivid itself never stores or transmits location data to any Vivid-operated service.
- **Your choice:** Disable the widget in the widget settings to stop location access.

### Network & LAN
- **What:** Internet access, network state, and (on Android 17+) access to the local network.
- **Why:** Required for RTMP/SRT streaming, Twitch chat, OBS WebSocket connections, and the optional web-based remote control that runs on your local network.
- **Where it goes:** The web remote control is served **only on your local network** (no internet exposure) and is intended for devices you control (e.g. a computer running OBS).

## Chat & AI Chatbot

If you enable the chat features, the app connects to **Twitch** using a token **you** provide:

- **Channel chat:** Chat messages from your channel are read via Twitch (Helix/EventSub) so the bot can reply. If the bot is in autonomous mode, the last ~20 messages are kept **in memory only** while the bot runs and are sent to the **language-model endpoint you configure** (Settings → Chat Bot).
- **LLM endpoints:** The bot calls an OpenAI-compatible API whose **base URL, API key and model you configure yourself** (e.g. OpenAI, Gemini, Groq, DeepSeek, or a local model such as Ollama). Chat messages and your configured system prompt are transmitted **to that provider**. Choose a provider whose privacy practices you accept; a local/self-hosted model keeps the data on your network.
- **Owner commands (`!ask`/`!diag`/`!start`/`!stop`):** Only the channel owner (or logins you explicitly whitelist) can trigger these. Replies are sent **privately via Twitch whisper** (not public chat) when whisper is enabled.
- **Tokens & credentials:** Twitch OAuth tokens, LLM API keys and all chat settings are stored **locally on your device** (DataStore). They are never uploaded anywhere except to the services you configured (Twitch, your LLM provider).
- **Notification access:** If you enable media control commands (`!song`/`!next`/`!pause`), the app requests notification-listener access. The listener is only a permission marker so the bot can control media sessions — it does **not** read your notifications.

## Crash Reporting (Sentry)

- **What:** When the app crashes, Sentry collects a stack trace, device information (model, OS version), and the view hierarchy at the moment of the crash (layout properties only — no text contents). **Screenshots are NOT captured**: the screen can contain stream previews, chat messages, or visible credentials. User-interaction breadcrumbs (clicks, swipes, scrolls) and performance traces are collected as well.
- **Why:** To help developers identify and fix bugs.
- **Where it goes:** Sentry.io (project `vivid`). Data is subject to Sentry's [privacy policy](https://sentry.io/privacy/).
- **Your choice:** Crash reporting is **enabled by default**, but you can switch it off at any time in **Settings → Datenschutz & Fehlerberichte → „Fehlerberichte senden (Sentry)“**. When disabled, **no events are sent to Sentry at all**. Device-identifying data (IP address, device name) is **not collected** (`sendDefaultPii` is disabled).
- **CI note:** Release builds upload deobfuscation (ProGuard mapping) files to Sentry when a Sentry auth token is present in the build environment. No personal data is contained in these mapping files.

## Data the App Does NOT Collect

Vivid does **not**:
- Use advertising identifiers (AAID) or any ad networks
- Require an account or login (you configure tokens yourself)
- Access your contacts, SMS, call logs, or general storage
- Sell or share data with third parties for advertising
- Use Firebase, Google Analytics, or any Google tracking services
- Embed telemetry in streaming data

## Data Storage

- **Settings & tokens** (stream URLs/keys, Twitch OAuth, LLM keys, bot configuration) are stored locally using Android's DataStore (Preferences).
- **Chat history** is kept in memory only while the bot runs and is not persisted.
- **No data** is stored on remote servers operated by Vivid itself.
- Android may include app data in automatic cloud backups (system-level backup). You can clear all app data or disable backup via Android's system settings at any time.

## Third-Party Services

| Service | Purpose | Data sent | Opt-out |
|---|---|---|---|
| **Sentry** (Functional Software, Inc.) | Crash & performance monitoring | Crash reports, device metadata, screenshots/view hierarchy | No in-app toggle yet — see above |
| **Twitch** (Twitch Interactive) | Chat bot, whispers | Chat messages, your bot token, channel name | Disable the chat bot in Settings |
| **Your configured LLM provider** (e.g. OpenAI, Groq, local Ollama) | AI chat replies | Chat messages, system prompt, your API key | Disable autonomous mode or clear the keys in Settings |
| **Your streaming server** (e.g. Twitch, YouTube, Kick, own RTMP/SRT server) | Live streaming | Camera/microphone stream | Stop the stream |

- Vivid does not control, host, or have access to these third-party servers beyond what you configure.
- You are responsible for the privacy practices of the streaming server and LLM provider you choose.

## Children's Privacy

Vivid is not directed at children under 13 and is intended for adult streamers. We do not knowingly collect personal data from children.

## Changes to This Policy

This policy may be updated as the app evolves. Changes will be reflected in the [GitHub repository](https://github.com/thoser666/Vivid) and in this document. The "Last updated" date at the top indicates when changes were made.

## Contact

- **Repository:** https://github.com/thoser666/Vivid
- **Issues:** https://github.com/thoser666/Vivid/issues
- **Discussions:** https://github.com/thoser666/Vivid/discussions

---

*This privacy policy was last reviewed on 2026-08-18. It reflects the current state of the Vivid app, including the AI chatbot, GPS info widget, LAN remote control, and Sentry crash reporting with an in-app opt-out toggle (default: on).*
