# 🔒 Privacy Policy for Vivid

**Last updated:** 2026-08-09

## Introduction

Vivid is an open-source live streaming app for Android. This policy explains what data the app accesses, how it is used, and your choices.

**The short version:** Vivid does not collect, sell, or share your personal data with any third party. The app streams your camera and microphone to a server **you** configure. Crash reports go to Sentry only if you enable them.

## Data the App Accesses

### Camera & Microphone
- **What:** Live video and audio from your device camera and microphone.
- **Why:** To encode and stream live content to the RTMP/SRT server URL you provide.
- **Where it goes:** Directly to the streaming server **you** configure in Settings. Vivid does not have its own streaming infrastructure — all data goes to your chosen server.
- **Storage:** Stream URLs and keys are stored locally on your device using Android DataStore. They never leave your device except when connecting to the server you specified.

### Network
- **What:** Internet access, network state.
- **Why:** Required for RTMP/SRT streaming, OBS WebSocket connections, and downloading updates.

### Crash Reporting (Sentry) — Opt-In via Settings
- **What:** If enabled, Sentry collects crash stack traces, device info (model, OS version), and in some cases screenshots and view hierarchy at the time of a crash.
- **Why:** To help developers identify and fix bugs.
- **Where it goes:** Sentry.io (project: `vivid`, organization: `privat-jb`). Data is subject to Sentry's [privacy policy](https://sentry.io/privacy/).
- **Your choice:** You can disable crash reporting in the app's Settings at any time.
- **⚠️ Warning:** When enabled, crash screenshots may capture whatever is on your screen at the moment of a crash — including stream previews, chat messages, or credentials. Disable screenshot attachment in Settings if this concerns you.

## Data the App Does NOT Collect

Vivid does **not**:
- Collect analytics or usage data beyond Sentry crash reports
- Use advertising identifiers (AAID) or any ad networks
- Track your location
- Require an account or login
- Access your contacts, SMS, call logs, or storage
- Share data with third parties (except Sentry if crash reporting is enabled)
- Use Firebase, Google Analytics, or any Google tracking services
- Embed telemetry in streaming data

## Data Storage

- **Stream settings** (URLs, keys, OBS WebSocket credentials) are stored locally using Android's DataStore (Preferences).
- **No data** is stored on remote servers by Vivid itself.
- You can clear all app data via Android's system settings at any time.

## Third-Party Services

### Sentry (Crash Reporting)
- **Provider:** Functional Software, Inc. (Sentry.io)
- **Data:** Crash reports, device metadata, optional screenshots/view hierarchy
- **Privacy policy:** https://sentry.io/privacy/
- **Opt-out:** Toggle off in Settings → Crash Reporting

### Streaming Servers
- Vivid connects to the RTMP/SRT server URL **you** provide in Settings.
- Vivid does not control, host, or have access to these servers.
- You are responsible for the privacy practices of the streaming server you use (e.g., YouTube, Twitch, own server).

## Children's Privacy

Vivid is not directed at children under 13. We do not knowingly collect personal data from children.

## Changes to This Policy

This policy may be updated as the app evolves. Changes will be reflected in the [GitHub repository](https://github.com/thoser666/Vivid) and in this document. The "Last updated" date at the top indicates when changes were made.

## Contact

- **Repository:** https://github.com/thoser666/Vivid
- **Issues:** https://github.com/thoser666/Vivid/issues
- **Discussions:** https://github.com/thoser666/Vivid/discussions

---

*This privacy policy was last reviewed on 2026-08-09. It accurately reflects the current state of the Vivid app as of this date.*
