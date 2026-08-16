# 🤖 AI Chat Bot

> **Status:** Engine, Twitch sending, LLM client, stream-lifecycle wiring, the mode switch („Bot wie Moblin“ ↔ „KI entscheidet selbst“) **and the full settings screen** (bot account, Twitch token, LLM endpoint/key/model, system prompt, cooldown, mentions-only, rate limit) are **implemented and tested**. Still open: the Twitch OAuth browser flow (until then, paste a chat token — see below) and the optional cost budget.

Vivid ships a **fully automated, in-app chat bot** (the idea is borrowed from cloud services like [Stream Chat AI](https://streamchatai.com), but it runs right on your phone — no dashboard, no monthly fee, you only pay your own LLM provider):

- It **connects to your Twitch chat automatically when you go live** and **shuts down cleanly when the stream ends** (wired into the streaming foreground service).
- It **answers viewers** through an LLM of your choice — any **OpenAI-compatible** endpoint works (OpenAI, Google Gemini, Groq, DeepSeek, or a **local Ollama** server).
- It is **calm by default**: mentions-only mode, a reply cooldown, a per-minute rate limit, and a hard 500-character cap on every reply.

---

## How it works

```
Go Live (StreamingService)
        │  ChatBotController.onStreamStarted()
        ▼
TwitchBotClient ──JOIN #channel (OAuth token)──► Twitch chat (read + send)
        │ messages
        ▼
ChatBotEngine ──filters: own messages, mentions-only, cooldown, rate limit──►
        │ conversation (system prompt + last 20 messages)
        ▼
OpenAiCompatibleLlmClient ──POST /v1/chat/completions──► OpenAI / Gemini / Groq / DeepSeek / Ollama
        │
        ▼
Reply (≤ 500 chars) ──PRIVMSG──► Twitch chat

Stream end → ChatBotController.onStreamStopped() → clean disconnect
```

The bot joins the same channel you configured for the chat overlay (`chat_channel`) — it runs independently of the overlay, so you can use either or both.

## Betriebsmodi (der Switch)

In den Einstellungen (Abschnitt **„Chat-Bot (KI)“**) gibt es einen **Betriebsmodus-Switch** mit zwei Positionen:

| Modus | Verhalten | LLM nötig? |
|-------|-----------|------------|
| **„Bot (wie Moblin)“** (`COMMAND`) | Deterministischer Befehls-Bot: reagiert **nur** auf `!`-Befehle (`!help`, `!uptime`, `!bot`) mit festen Antworten — genau wie der Chat-Bot von [Moblin](https://github.com/eerimoq/moblin). Nicht-Befehle werden ignoriert. | **Nein** — nur ein Twitch-Chat-Token reicht |
| **„KI autonom“** (`AUTONOMOUS`) | Die KI entscheidet selbst: Bekannte `!`-Befehle werden weiterhin sofort beantwortet, alle übrigen (freigegebenen) Nachrichten bewertet das LLM und entscheidet, ob und wie es antwortet — **inklusive bewusstem Schweigen** (Signal: `[keine Antwort]`). | Ja — OpenAI-kompatibles LLM |

**Eingebaute Befehle (beide Modi):**

| Befehl | Antwort |
|--------|---------|
| `!help` / `!commands` | Listet die verfügbaren Befehle |
| `!uptime` | Wie lange der Stream schon läuft (aus dem Stream-Start-Zeitstempel) |
| `!tts` | **Chat-Text-to-Speech umschalten** (an/aus) — liest Chat-Nachrichten auf dem Gerät des Streamers laut vor |
| `!song` / `!nowplaying` | Aktuellen Titel melden („Aktuell läuft: Titel – Interpret“) |
| `!next` / `!skip` | Zum nächsten Titel springen |
| `!pause` / `!play` | Wiedergabe pausieren / fortsetzen |
| `!prev` / `!previous` | Zum vorherigen Titel springen |
| `!bot` | Kurzinfo über den Bot |
| `!<unbekannt>` | COMMAND: Hinweis „Unbekannter Befehl … — Tipp: !help“ · AUTONOMOUS: die KI entscheidet |

Befehle sind **case-insensitive** und können mitten in der Nachricht stehen (`@bot !help`). Cooldown und Rate-Limit gelten für **alle** Antworten — auch für Befehle (schützt vor Spam, Twitch begrenzt 20 Nachrichten/30 s).

#### Chat-Text-to-Speech (`!tts`)

Wie beim Bot von [Moblin](https://github.com/eerimoq/moblin) kann der Chat das Vorlesen umschalten: Jeder schreibt `!tts` und der Bot bestätigt im Chat („TTS ist jetzt AN/AUS“). Wenn TTS an ist, liest das Gerät des Streamers eingehende Chat-Nachrichten laut vor (Android-TextToSpeech, keine Runtime-Berechtigung nötig).

- Es werden **Viewer-Nachrichten** vorgelesen („Name: Text“), **eigene Bot-Nachrichten und `!`-Befehle** werden übersprungen (der Bot liest also weder seine eigene Bestätigung noch den `!tts`-Toggle selbst vor).
- Vorgelesen wird nur, **solange der Bot aktiv ist** (der `!tts`-Befehl setzt voraus, dass der Bot im Chat ist).
- Der An/Aus-Zustand **überlebt Stream-Ende/-Start** — TTS bleibt an, bis `!tts` es wieder ausschaltet.
- Die vorgelesene Nachricht ist auf 200 Zeichen begrenzt. Ist auf dem Gerät keine TTS-Engine installiert, passiert nichts (kein Crash).

#### Media-Player-Steuerung (`!song` / `!next` / `!pause` / `!play` / `!prev`)

Wie beim Bot von [Moblin](https://github.com/eerimoq/moblin) (Row 80, Android-Adaption der Apple-Music-Steuerung) steuert der Chat den aktiven Musik-Player (Apple Music, Spotify, YouTube Music, …) über die **MediaSession** des Geräts:

- **`!song`** meldet den aktuellen Titel („Aktuell läuft: …“), **`!next`/`!prev`** springen weiter/zurück, **`!pause`/`!play`** pausieren/fortsetzen — alle case-insensitive.
- **Voraussetzung:** Benachrichtigungszugriff für Vivid (Systemeinstellungen → Benachrichtigungen bzw. Sonderzugriff). Ohne diesen Zugriff dürfen Apps fremde Media-Sessions nicht sehen — der Bot antwortet dann mit einem Hinweis statt zu steuern. Im Settings-Screen gibt es dafür den Button **„Benachrichtigungszugriff aktivieren“** (öffnet die Systemeinstellung).
- Technisch läuft das über einen leeren `MediaNotificationListener` (nur Zugriffs-Marker — er liest **keine** Benachrichtigungen aus) + `MediaSessionManager.getActiveSessions(...)` → `MediaController.TransportControls`.
- Es wird die aktive Session (playing/paused/buffering) bevorzugt, sonst die erste verfügbare.

> 💡 **Tipp:** Im COMMAND-Modus funktioniert der Bot komplett ohne LLM-API-Key — ideal, wenn du nur die Moblin-artigen Chat-Befehle willst.

## Koexistenz mit anderen Bots (z. B. Rivulet)

Läuft neben dem Chat-Bot eines anderen Tools im selben Kanal (z. B. dem geplanten **Rivulet**-Bot, [github.com/thoser666/rivulet](https://github.com/thoser666/rivulet), Meilenstein M9), gibt es drei echte Kollisionspunkte — alle lassen sich in den Einstellungen (Abschnitt **„Chat-Bot (KI)“ → „Koexistenz mit anderen Bots“**) entschärfen:

| Problem | Lösung in Vivid |
|---------|-----------------|
| **Doppelte Antworten**: Beide Bots beantworten `!help`, `!uptime`, `!song` … | **Befehlsscope** eingrenzen — Vivid antwortet nur auf `@vividbot`-Erwähnungen oder ein eigenes Präfix (`!v!help`), generische `!`-Befehle gehören dem anderen Bot |
| **Doppelte Aktionen**: `!tts`/`!pause`/`!play` werden von beiden ausgeführt (TTS doppelt geflippt, Songs doppelt geskippt) | gleicher Befehlsscope — Vivid führt nur adressierte/präfixierte Aktionen aus |
| **Bot-zu-Bot-Geräusch**: Vivids KI-Modus antwortet auf Ansagen des anderen Bots („Stream gestartet …“) | **Andere Bots ignorieren**: Login(s) des anderen Bots eintragen — deren Nachrichten werden komplett ignoriert (keine Befehle, kein LLM-Input, **auch nicht vorgelesen** vom Chat-TTS) |

### Befehlsscope (drei Stufen)

| Scope | Verhalten | Beispiel |
|-------|-----------|----------|
| **Alle !-Befehle** (`ALL`, Standard) | Jeder `!`-Befehl wird beantwortet — Moblin-Stil, heutiges Verhalten | `!help` → Antwort |
| **Nur Erwähnung** (`MENTION`) | Nur Befehle, die den Bot direkt ansprechen | `@vividbot !help` → Antwort · `!help` → ignoriert |
| **Eigenes Präfix** (`PREFIX`) | Nur Befehle mit eigenem Präfix; generische Befehle bleiben dem anderen Bot (werden als „nicht für mich“ ignoriert, nicht als unbekannt gemeldet) | `!v!help` → Antwort (Präfix `v`) · `!help` → ignoriert |

Fremde Befehle außerhalb des Scopes liefern **kein** „Unbekannter Befehl“-Echo — sie werden als `None` behandelt, damit der andere Bot ungestört antworten kann.

### Empfohlene Einrichtung für die Koexistenz mit Rivulet

1. **Getrennte Bot-Konten** verwenden (Vivid-Bot ≠ Rivulet-Bot ≠ Streamer-Konto) — sonst teilen sich beide das Twitch-Nachrichten-Limit (20 Nachrichten/30 s) und die Antworten sind nicht unterscheidbar.
2. Den **Login des Rivulet-Bots** unter „Andere Bots ignorieren“ eintragen (kommasepariert, ohne `@`).
3. **Befehlsscope** wählen: Entweder Rivulet übernimmt die generischen `!`-Befehle und Vivid nutzt `@vividbot` (MENTION) oder ein eigenes Präfix (PREFIX, z. B. `v` → `!v!help`) — oder umgekehrt, je nachdem welcher Bot die generischen Befehle besitzen soll.
4. `!help` zeigt im PREFIX-Modus automatisch die präfixierten Befehle (`!v!help · !v!uptime · …`).

> Die Einstellungen werden mit **„Speichern“** persistiert (`chat_bot_ignore_bots`, `chat_bot_command_scope`, `chat_bot_command_prefix` in den App-Settings).

## Current status

Implemented & unit-tested:

- `OpenAiCompatibleLlmClient` — OpenAI-compatible `/v1/chat/completions` client (Bearer auth, error handling, configurable model/temperature/max tokens).
- `TwitchBotClient` — authenticated Twitch chat connection (`PASS oauth:<token>`), reads and **sends** messages, PING→PONG keepalive, auto-reconnect with backoff.
- `ChatBotEngine` — reaction engine: ignores the bot's own messages, mentions-only filter, cooldown, per-minute rate limit, rolling prompt history, 500-char reply cap, `ChatBotState` (Disabled/Idle/Thinking).
- `ChatBotController` + `StreamingService` wiring — automatic connect on Go Live, clean shutdown on stream end, instant stop when the bot is disabled in settings. Tracks the stream start timestamp for `!uptime`, wires the chat TTS to the message stream.
- **Chat-TTS (`!tts`)** — `ChatTtsController` (enabled state, speaks viewer messages, skips own messages and commands) + `AndroidTtsSpeaker` (system TextToSpeech engine).
- **Media-Player-Steuerung (`!song`/`!next`/`!pause`/`!play`/`!prev`)** — `ChatMediaController` (MediaController über aktive MediaSessions) + `MediaNotificationListener` (Benachrichtigungszugriff als Voraussetzung).
- **Mode switch** in Vivid's Settings screen („Bot (wie Moblin)“ ↔ „KI autonom“) incl. enable toggle — see [Betriebsmodi (der Switch)](#betriebsmodi-der-switch).
- Bot settings in `SettingsRepository`/`AppSettings` (`chat_bot_mode` key).

Still open (next milestones):

- **Twitch OAuth browser flow** (until then, paste a chat token — see below).
- Cost budget per hour, provider presets.

## Prerequisites

1. A **Twitch account for the bot** (can be your main account or a dedicated one).
2. A **chat token** for that account with the scopes `chat:read` and `chat:send`.
3. An **LLM API key** (or a reachable Ollama instance) — only needed in „KI autonom“ mode; the „Bot wie Moblin“ command mode works without one.

## 1. Getting a Twitch chat token

> ⚠️ Only generate tokens for bots you control. A chat token grants access to **read and send messages as that account**.

You need a token for a **bot account** (not a user account), i.e. an account that isn't heavily rate-limited on chat.

1. Create your bot account at <https://www.twitch.tv> and confirm the email.
2. Generate an access token with the `chat:read` and `chat:send` scopes using the official Twitch token generator: <https://twitchtokengenerator.com> → pick the **Chat Read/Write** (or *Chat Bot*) scope set → authorize with the **bot account**.
   - If you prefer the raw OAuth flow: authorize against `https://id.twitch.tv/oauth2/authorize` with `response_type=token` and `scope=chat:read chat:send`.
3. Copy the token (it starts with `oauth:` in some generators — the `oauth:` prefix is optional, Vivid strips it automatically).
4. Note the **login name** of the bot account (lowercase, no `@`) — that's the `Chat bot login`.

> 🔒 The token is stored only on-device in Vivid's private app storage (same as your stream key / OBS password). If it ever leaks, revoke it in your Twitch settings and generate a new one.

## 2. Choosing an LLM provider

The bot speaks the **OpenAI Chat Completions** API. Point it at the *base URL* of any compatible provider. `POST <baseUrl>/v1/chat/completions` with a `Bearer` API key.

| Provider | Base URL | Notes |
|----------|----------|-------|
| **OpenAI** | `https://api.openai.com` | Default. Works with `gpt-4o-mini`, `gpt-4o`, … |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai` | OpenAI-compatible endpoint; use a Gemini API key and a model like `gemini-2.0-flash` |
| **Groq** | `https://api.groq.com/openai` | Very fast + cheap; models like `llama-3.3-70b-versatile` |
| **DeepSeek** | `https://api.deepseek.com` | Cheap; model `deepseek-chat` |
| **Ollama (local)** | `http://<host>:11434` | 100 % private, no internet needed; make sure the phone can reach the host on your LAN and the server runs with `OLLAMA_HOST` reachable |

Model names are provider-specific — check your provider's docs. A good starting point is a small, cheap model (`gpt-4o-mini`, `llama-3.3-70b-versatile`, …).

## 3. Configuration

All bot settings live in the app settings. Fields (DataStore keys):

| Setting | Key | Example / default |
|---------|-----|-------------------|
| Enabled | `chat_bot_enabled` | `false` (default) |
| Channel | `chat_channel` | the channel you stream to (shared with the chat overlay) |
| Bot login | `chat_bot_login` | `mychannelbot` (lowercase, no `@`) |
| OAuth token | `chat_bot_oauth_token` | the token from step 1 |
| API base URL | `chat_bot_api_base_url` | `https://api.openai.com` (default) |
| API key | `chat_bot_api_key` | `sk-…` |
| Model | `chat_bot_model` | `gpt-4o-mini` (default) |
| System prompt | `chat_bot_system_prompt` | e.g. `You are the friendly chat bot of my IRL stream. Answer in the stream's language, stay short and helpful.` |
| Reply cooldown | `chat_bot_reply_cooldown_seconds` | `8` (default) |
| Mentions only | `chat_bot_mentions_only` | `true` (default) — replies only when the bot is addressed |
| Max replies/min | `chat_bot_max_replies_per_minute` | `10` (default) |
| **Modus** | `chat_bot_mode` | `AUTONOMOUS` (default) — `COMMAND` = „Bot wie Moblin“ (nur `!`-Befehle, kein LLM nötig) |

Alle Felder sind direkt im **Settings-Screen** (Abschnitt **„Chat-Bot (KI)“**) editierbar und werden mit „Speichern“ persistiert. Token und API-Key sind als Passwortfelder mit Sichtbarkeits-Toggle (Auge) hinterlegt.

## Behavior & safeguards

- **Auto-connect / auto-shutdown** — the bot follows the stream lifecycle: connect on Go Live, clean disconnect on stop (even remote/`StreamingState.Idle` stops and process restarts).
- **Mentions-only** — with the default `mentionsOnly = true`, the bot only reacts to messages containing its login or `!bot`. Turn it off if you want it to answer every message (keep an eye on the rate limit).
- **Cooldown** — at least `replyCooldownSeconds` between two replies (default 8 s).
- **Rate limit** — at most `maxRepliesPerMinute` replies per rolling minute (default 10) — Twitch's global IRC rate limit is 20 messages/30 s; the default stays well below it.
- **Reply cap** — every reply is trimmed to 500 characters and line breaks are collapsed (Twitch's message limit).
- **No echo** — the bot ignores messages it sent itself.
- **LLM errors** — a failed request is logged (no message is sent) and the bot stays available; it never crashes the stream.

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Bot never joins | `chat_bot_enabled` off, or `chat_channel`/`chat_bot_login`/`chat_bot_oauth_token` empty → fill all three |
| `Login authentication failed` in chat | Token invalid, expired, revoked, or generated for a **different** account than `chat_bot_login` |
| Bot connects but can't send | Token lacks the `chat:send` scope |
| No replies to mentions | `mentionsOnly` on and the message doesn't contain the bot login or `!bot`; or cooldown/rate limit hit |
| `LLM-Anfrage fehlgeschlagen` | Wrong base URL, invalid API key, model name not available on the provider, or no network access (Ollama: phone must reach the host) |

## Roadmap

- ✅ **Mode switch** in Vivid's settings („Bot wie Moblin“ ↔ „KI autonom“) — done.
- Full bot settings screen inside Vivid (remaining fields from [Configuration](#configuration): Twitch token, LLM credentials …).
- ✅ **Media-player control** (`!song`/`!next`/`!pause`/`!play`/`!prev` via MediaSession, Moblin parity row) — done (needs notification access).
- Twitch OAuth browser flow (no manual token pasting).
- Optional hourly cost budget for the LLM.
- More platforms once Kick/YouTube chat lands.

## Architecture

| File | Responsibility |
|------|----------------|
| `feature-chat/…/ai/LlmClient.kt` | `LlmClient` interface + `OpenAiCompatibleLlmClient` (Ktor) + `LlmConfig` |
| `feature-chat/…/ai/LlmModels.kt` | Chat-Completion request/response DTOs (kotlinx.serialization) |
| `feature-chat/…/twitch/TwitchBotClient.kt` | Authenticated Twitch IRC connection: read + `sendMessage()` |
| `feature-chat/…/bot/ChatBotEngine.kt` | Reaction engine (mode switch, filters, history, LLM call, send, state) |
| `feature-chat/…/bot/BotCommandProcessor.kt` | Deterministic `!`-commands („Bot wie Moblin“, no LLM) — incl. `!tts` |
| `feature-chat/…/bot/ChatTtsController.kt` | Chat-TTS: enabled state, speaks viewer messages, `toggle()` for `!tts` |
| `feature-chat/…/bot/AndroidTtsSpeaker.kt` | Android TextToSpeech implementation of the speaker interface |
| `feature-chat/…/media/ChatMediaController.kt` | Media-Player-Steuerung (MediaController über aktive MediaSessions) |
| `feature-chat/…/media/MediaNotificationListener.kt` | Notification-Listener (Zugriffs-Marker für Media-Session-Steuerung) |
| `feature-chat/…/bot/ChatBotConfig.kt` | Settings → engine configuration (incl. mode) |
| `feature-chat/…/bot/ChatSender.kt` | Send abstraction (interface) |
| `feature-chat/…/bot/ChatBotController.kt` | Stream-lifecycle → bot wiring |
| `app/…/StreamingService.kt` | Calls `onStreamStarted()` / `onStreamStopped()` |
| `core/…/SettingsRepository.kt` | Persists the bot settings (`chat_bot_*` keys) |
