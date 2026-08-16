# 🤖 AI Chat Bot

> **Status:** Engine, Twitch sending, LLM client and stream-lifecycle wiring are **implemented and tested**. The configuration screen inside Vivid is the next milestone — until it lands, the settings documented below are stored per the fields listed in [Configuration](#configuration).

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

## Current status

Implemented & unit-tested:

- `OpenAiCompatibleLlmClient` — OpenAI-compatible `/v1/chat/completions` client (Bearer auth, error handling, configurable model/temperature/max tokens).
- `TwitchBotClient` — authenticated Twitch chat connection (`PASS oauth:<token>`), reads and **sends** messages, PING→PONG keepalive, auto-reconnect with backoff.
- `ChatBotEngine` — reaction engine: ignores the bot's own messages, mentions-only filter, cooldown, per-minute rate limit, rolling prompt history, 500-char reply cap, `ChatBotState` (Disabled/Idle/Thinking).
- `ChatBotController` + `StreamingService` wiring — automatic connect on Go Live, clean shutdown on stream end, instant stop when the bot is disabled in settings.
- Bot settings in `SettingsRepository`/`AppSettings`.

Still open (next milestones):

- **Settings UI** in Vivid's Settings screen (the settings themselves are already persisted).
- **Twitch OAuth browser flow** (until then, paste a chat token — see below).
- Cost budget per hour, media-player control commands, provider presets.

## Prerequisites

1. A **Twitch account for the bot** (can be your main account or a dedicated one).
2. A **chat token** for that account with the scopes `chat:read` and `chat:send`.
3. An **LLM API key** (or a reachable Ollama instance).

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

Until the settings screen ships, these can be pre-seeded for development/testing through the repository APIs (`SettingsRepository.updateChatBotSettings(...)`).

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

- Settings screen for the bot inside Vivid (all fields from [Configuration](#configuration)).
- Twitch OAuth browser flow (no manual token pasting).
- Optional hourly cost budget for the LLM.
- Bot commands / media-player control via MediaSession (Moblin parity row).
- More platforms once Kick/YouTube chat lands.

## Architecture

| File | Responsibility |
|------|----------------|
| `feature-chat/…/ai/LlmClient.kt` | `LlmClient` interface + `OpenAiCompatibleLlmClient` (Ktor) + `LlmConfig` |
| `feature-chat/…/ai/LlmModels.kt` | Chat-Completion request/response DTOs (kotlinx.serialization) |
| `feature-chat/…/twitch/TwitchBotClient.kt` | Authenticated Twitch IRC connection: read + `sendMessage()` |
| `feature-chat/…/bot/ChatBotEngine.kt` | Reaction engine (filters, history, LLM call, send, state) |
| `feature-chat/…/bot/ChatBotConfig.kt` | Settings → engine configuration |
| `feature-chat/…/bot/ChatSender.kt` | Send abstraction (interface) |
| `feature-chat/…/bot/ChatBotController.kt` | Stream-lifecycle → bot wiring |
| `app/…/StreamingService.kt` | Calls `onStreamStarted()` / `onStreamStopped()` |
| `core/…/SettingsRepository.kt` | Persists the bot settings (`chat_bot_*` keys) |
