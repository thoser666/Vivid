# 📖 Vivid — User Guide

> **TL;DR:** Enter your stream URL + key → tap **Go Live** → done.
> This guide walks through every step in detail.

Vivid is an IRL streaming app for Android (RTMP/SRT/RTMPS) that combines a chat overlay,
an AI bot, and camera controls in one app — inspired by [Moblin](https://github.com/eerimoq/moblin).

- 📥 **Install:** [Releases page](../../releases) or [Obtainium](#1-installation)
- 🤖 **Bot docs:** [docs/ai-chat-bot.md](ai-chat-bot.md)
- 📋 **Roadmap:** [PARITY.md](../PARITY.md) · [RELEASE.md](../RELEASE.md)
- ❓ **Troubleshooting:** [FAQ in the README](../README.md#-faq--häufige-probleme)
- 🇩🇪 **Deutsche Version:** [docs/user-guide.md](user-guide.md) · 🇫🇷 **Version française :** [docs/user-guide.fr.md](user-guide.fr.md)

---

## Table of Contents

1. [Installation](#1-installation)
2. [First Launch & Permissions](#2-first-launch--permissions)
3. [Configure Your Stream](#3-configure-your-stream)
4. [Go Live](#4-go-live)
5. [Camera Controls (Tap-to-Focus, Zoom, Stabilization, Torch)](#5-camera-controls)
6. [Chat Overlay](#6-chat-overlay)
7. [AI Chat Bot](#7-ai-chat-bot)
8. [Control OBS Studio](#8-control-obs-studio)
9. [Web Remote Control (Control the Stream from a Browser)](#9-web-remote-control)
10. [Settings — The Six Categories](#10-settings--the-six-categories)
11. [Updates](#11-updates)

---

## 1. Installation

> Vivid is not on the Play Store (yet). APKs are published as GitHub Releases.

1. Open the [**Releases page**](../../releases)
2. Download **"Latest"** (stable) or a **Nightly/Alpha** (prerelease) — the file is called `app-release.apk`
3. Open the APK in your file manager → Android asks for **"Unknown sources"** → allow
4. **Install** — done

**Alternatively with Obtainium (automatic updates):**
See [README → Automatic updates (Obtainium)](../README.md#-automatic-updates-obtainium).

> 🔒 All official APKs are signed with the same release key — updates install seamlessly on top of each other.

---

## 2. First Launch & Permissions

On first open, Vivid shows the **streaming screen** (live preview). Before you go live for the first time, the app needs three permissions:

| Permission | What for | When asked |
|---|---|---|
| **Camera** | Stream live video from the device | On first **Go Live** |
| **Microphone** | Capture audio/commentary | On first **Go Live** |
| **Notifications** | Foreground service (stream keeps running in background) | On first **Go Live** (Android 13+) |

> **Background streaming:** The stream keeps running when you leave the app (Home button),
> turn off the screen, or swipe the app away from recents — the preview simply reappears
> when you reopen the app. A persistent notification with a **Stop** action shows the stream status.

---

## 3. Configure Your Stream

Open **Settings** (⚙️ top-right in the streaming screen) → **"Streaming & OBS"**.

### 3.1 Pick a Platform Template

Vivid offers templates for the most common platforms:

| Template | Server URL | What you need |
|---|---|---|
| **Twitch** | `rtmp://live.twitch.tv/live/` | Stream key (from [Creator Dashboard](https://dashboard.twitch.tv/) → Settings → Stream) |
| **YouTube** | *[from YouTube Studio]* | Stream URL + Stream key (from [YouTube Studio](https://studio.youtube.com/) → Go Live → Stream) |
| **Kick** | `rtmp://ingest.kick.com/live/` | Stream key (from [Kick Dashboard](https://kick.com/dashboard) → Settings → Stream Settings) |
| **Custom** | *(empty — you enter the URL)* | RTMP(S)/SRT URL + Stream key (e.g. [Owncast](https://owncast.online), Restream.io, etc.) |

### 3.2 Fill in the Fields

1. Tap a **platform template** → the URL is pre-filled
2. Adjust the **Stream URL** if needed (e.g. `rtmps://` for TLS — the **"TLS"** toggle enables encryption)
3. Enter your **Stream Key**
4. **Done** — settings are saved

### 3.3 Multi-Streaming (Two Targets in Parallel)

Optionally, you can configure a **second RTMP target**:
- Enter the secondary URL + key under **"Multi-Streaming (optional)"**
- On **Go Live**, both targets start simultaneously
- Each target shows its own status (ready / connecting… / live / failed)
- If one target fails, only that one stops — the other keeps streaming

> Ideal for streaming to Twitch + YouTube at the same time.

### 3.4 Go-Live Self-Check

Before starting, Vivid validates the configuration and shows clear messages:
- ❌ **Error** (blocks start): No URL, unsupported protocol, no host
- ⚠️ **Warning** (doesn't block): Stream key missing (some platforms don't require one)

---

## 4. Go Live

1. Return to the **streaming screen** (← back arrow top-left if you're in settings)
2. Tap the **Go Live** button (bottom center)
3. Vivid connects — status changes to **"Preparing…"** → **"live"**
4. The status display shows each target individually (for multi-streaming)

### Stop the Stream

- **Stop** button in the streaming screen, **or**
- **Stop** in the notification bar

> The stream stops cleanly — the AI bot (if active) also shuts down automatically.

---

## 5. Camera Controls

Directly on the **streaming preview** (usable **before** going live too):

| Action | How |
|---|---|
| **Tap-to-Focus** | Single tap on the preview → focuses on that spot |
| **Pinch-to-Zoom** | Two fingers pinch in/out → zoom (clamped to camera range) |
| **Reset Zoom** | Double-tap on the preview → zoom = 1.0 |

Three buttons in the top-right of the streaming screen:

| Button | Function |
|---|---|
| **🔦 Torch** | Toggles the flashlight (Torch/Lantern) on/off — also controllable via the `!torch` bot command |
| **Stabilization** | Optical (OIS) preferred, otherwise electronic (EIS) stabilization on/off |
| **Focus Lock** | Auto-focus ⇄ infinity lock (prevents focus hunting in rain/dirt on the lens — ideal for drive/train streams) |

> The buttons act on the **real RootEncoder camera** — not just the preview.

---

## 6. Chat Overlay

The Twitch chat overlay shows your channel's chat **over the streaming preview**.

### 6.1 Enable

1. **Settings** → **"Overlays & Widgets"**
2. Toggle **Chat Overlay** on
3. Enter your **Chat Channel** (your Twitch channel name, e.g. `thoser666`)

### 6.2 What You Need

The overlay reads the chat via **Twitch EventSub** (not IRC). It needs the **bot credentials** (see [AI Chat Bot](#7-ai-chat-bot) → Bot Login + OAuth Token):
- The bot token needs the scope `user:read:chat`
- For event alerts (Follows/Subs): the bot must be a **moderator** in the channel (`moderator:read:followers`) and have `channel:read:subscriptions`

### 6.3 What the Overlay Shows

- The latest chat messages at the bottom-left (with Twitch color per user)
- **Twitch badges** (Broadcaster/Mod/Sub) before the username as CDN images
- **Inline emotes** (Twitch emotes rendered as images inline with text, via Coil)
- **Event alerts** as colored banner lines above the chat:
  - 🟢 Follow · 🟣 Sub · 🔵 Gift-Sub · 🟦 Resub · 🟠 Raid
  - Auto-dismissed after 10 seconds

### 6.4 Test Alert (Before Going Live)

To test the overlay before streaming:
- Type **`!testalert follow`** (or `sub`, `gift`, `resub`, `raid`) in chat (owner-only)
- The alert appears immediately in the overlay

---

## 7. AI Chat Bot

The bot connects automatically on **Go Live** and shuts down when the **stream ends**. The full guide is in [docs/ai-chat-bot.md](ai-chat-bot.md).

### 7.1 Choose a Mode

| Mode | Description |
|---|---|
| **Bot (like Moblin)** | Deterministic `!`-commands (`!help`, `!uptime`, `!tts`, `!bot`) — **no LLM needed** |
| **AI autonomous** | The AI decides whether and how to reply (including staying silent on purpose) |

### 7.2 Setup

**Settings** → **"Chat Bot & AI"**:

1. Enter **Bot Login** (Twitch username of the bot)
2. Enter **Twitch OAuth Token** (scope `user:read:chat` + `user:write:chat`; for moderation `moderator:manage:banned_users`) — password field with visibility toggle
3. Enter **Twitch App Client ID** (for EventSub + Helix)
4. For AI mode: enter **LLM Base URL**, **API Key**, and **Model** (OpenAI-compatible → OpenAI, Gemini, Groq, DeepSeek, Ollama on LAN)

### 7.3 Limits (Cost Protection)

Three configurable limits (all `0` = off):
- **Per-Viewer Cooldown** (default 60 s) — a viewer won't get more than one reply per X seconds
- **Per-Viewer Cap per Stream** — max replies per viewer per stream
- **Hourly Budget** — max replies per hour (cost cap)

**Quick-start presets:** Relaxed (30/0/0) · Balanced (60/10/120) · Strict (180/5/60) — one tap fills all three fields. The last chosen preset is saved and restored on app start.

**Live usage** in the settings screen: replies/hour (vs. budget), stream total, top viewers.

### 7.4 Owner Commands (Streamer Only)

| Command | Effect |
|---|---|
| `!start` / `!go-live` | Start the stream |
| `!stop` / `!end` | Stop the stream |
| `!diag` / `!status` | Diagnostics: stream status, OBS, 11 config checks + AI recommendation |
| `!ask <question>` | Question to the exclusive Owner AI (fallback: viewer AI, otherwise deterministic) |
| `!testalert <type>` | Test alert for the overlay (`follow`/`sub`/`gift`/`resub`/`raid`) |
| `!torch` | Toggle the flashlight (alias: `!lantern`/`!flashlight`) |
| `!ban <user>` | Ban a viewer |
| `!timeout <user> <min?>` | Timeout a viewer (default 5 min) |
| `!delete <count?>` | Delete recent messages |

> Owner = Broadcaster badge **or** allow-list (`chat_bot_owner_logins` in settings).
> Replies are sent via **Whisper** (private) if the toggle is on.
> Viewer commands: `!help`, `!uptime`, `!tts`, `!song`, `!next`, `!pause`, `!bot`.

### 7.5 Coexistence with Other Bots

If another bot runs in the same channel (e.g. Rivulet):
- **`chat_bot_ignore_bots`**: Completely ignore other bot logins
- **Command Scope**: `ALL` (every command), `MENTION` (only `@vividbot !help`), `PREFIX` (only `!v!help` with prefix `v`)
- Foreign commands outside the scope → no "Unknown command" echo (the other bot is undisturbed)

---

## 8. Control OBS Studio

Vivid can control OBS Studio via **WebSocket** (switch scenes, start/stop recording and streaming).

### Setup

1. **In OBS:** Tools → **WebSocket Server Settings** → enable *"WebSocket Server"* (port `4455`)
2. Optionally set a **password**
3. Find the **IP of the OBS machine** (Windows: `ipconfig`, Mac/Linux: `ip addr`) — phone and PC on the **same Wi-Fi**
4. **In Vivid:** Settings → **Streaming & OBS** → OBS section:
   - Host = PC's IP
   - Port = `4455`
   - Password (if set)
   - **TLS toggle** (`wss://` for remote, `ws://` for LAN)
5. Open **OBS Control** (icon top-left in the streaming screen)

> Troubleshooting? See the [OBS FAQ](../README.md#-faq--häufige-probleme).

### Import OBS via QR Code

If your OBS displays a QR code (format `obsws://host:port/pw`), you can import it in the
settings screen — host, port, and password are automatically filled in.

---

## 9. Web Remote Control

Control the stream from any browser on the same Wi-Fi:

1. **Settings** → **Remote & Privacy** → **Web Remote Control**
2. Note the **token** (generated once)
3. Find your **phone's IP** (Android: Settings → Wi-Fi → connected network → details)
4. **Check status** (no token needed):
   ```
   curl http://<phone-ip>:8080/status
   ```
5. **Start/stop the stream** (with token):
   ```
   curl -X POST http://<phone-ip>:8080/start -H "Authorization: Bearer <token>"
   curl -X POST http://<phone-ip>:8080/stop  -H "Authorization: Bearer <token>"
   ```

> 🔒 The server only runs while the app is open. Actions require the token.
> Android 17: If `/status` is unreachable → tap "Allow LAN access for Remote Control" in settings.

---

## 10. Settings — The Six Categories

The settings screen is organized into six categories (like Moblin):

| Category | Contents |
|---|---|
| 🎬 **Streaming & OBS** | Stream URL/key, platform templates (Twitch/YouTube/Kick/Custom), multi-streaming, OBS connection (host/port/password/TLS/QR import) |
| 🎨 **Appearance** | Theme mode (System/Light/Dark/AMOLED) + accent color (6 curated colors, Vivid Green as default) |
| 🧩 **Overlays & Widgets** | Twitch chat overlay (channel + toggle), text/info widget (time/GPS/speed/altitude — each with toggle + runtime permission) |
| 💬 **Chat Bot & AI** | Mode, bot account (login/token/client ID), LLM endpoint/key/model/prompt, cooldown, mentions-only, rate limit, limits + presets, owner access (allow-list + Owner AI), media commands, notification access |
| 🔒 **Remote & Privacy** | Web remote control (token + LAN access), Sentry error reports (opt-out toggle) |
| ℹ️ **About & Updates** | Version, update badge, manual update check (GitHub Releases), release notes |

---

## 11. Updates

### Automatic (Obtainium)

See [README → Automatic updates (Obtainium)](../README.md#-automatic-updates-obtainium).

### Manual

1. **Settings** → **About & Updates**
2. The **update badge** appears automatically (1-hour cache) if a newer version exists
3. Tap **"Check for updates"** → shows the latest GitHub release including release notes
4. Download the APK from the [Releases page](../../releases) and install

> The check never suggests a downgrade (Nightly → Nightly/Alpha/Beta/Stable).

---

## Quick Reference: All Bot Commands

| Command | Who? | Effect |
|---|---|---|
| `!help` / `!commands` / `!hilfe` | All | Show available commands |
| `!uptime` | All | Show stream duration |
| `!tts` | All | Toggle chat text-to-speech on/off |
| `!bot` | All | Show bot info |
| `!song` / `!nowplaying` | All | Current track (media player) |
| `!next` / `!skip` | Owner + Mod | Next track |
| `!pause` | Owner + Mod | Pause playback |
| `!play` | Owner + Mod | Resume playback |
| `!prev` / `!previous` | Owner + Mod | Previous track |
| `!tts` | Owner | Toggle chat text-to-speech |
| `!start` / `!go-live` | Owner | Start the stream |
| `!stop` / `!end` | Owner | Stop the stream |
| `!diag` / `!status` | Owner | Run diagnostics |
| `!ask <question>` | Owner | Ask the Owner AI |
| `!fix` | Owner | Auto-fix issues |
| `!testalert <type>` | Owner | Test alert for the overlay |
| `!torch` | Owner | Toggle the flashlight |
| `!ban <user>` | Owner | Ban a viewer |
| `!timeout <user> <min?>` | Owner | Timeout a viewer |
| `!delete <count?>` | Owner | Delete messages |

> **PREFIX scope** (coexistence): `!v!help`, `!v!uptime`, … (prefix `v` = default).
> Commands are case-insensitive and can appear mid-message (`@vividbot !help`).
