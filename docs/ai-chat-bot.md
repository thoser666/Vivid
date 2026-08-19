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
TwitchChatEventSubReader ──EventSub WebSocket──► channel.chat.message (read, Scope user:read:chat)
TwitchSendChatClient     ──POST /helix/chat/messages──► send (Scope user:write:chat)
        │ messages
        ▼
ChatBotEngine ──filters: own messages, mentions-only, cooldown, rate limit──►
        │ conversation (system prompt + last 20 messages)
        ▼
OpenAiCompatibleLlmClient ──POST /v1/chat/completions──► OpenAI / Gemini / Groq / DeepSeek / Ollama
        │
        ▼
Reply (≤ 500 chars) ──POST /helix/chat/messages──► Twitch chat

Stream end → ChatBotController.onStreamStopped() → clean disconnect
```

The bot joins the same channel you configured for the chat overlay (`chat_channel`) — it runs independently of the overlay, so you can use either or both.

## Betriebsmodi (der Switch)

In den Einstellungen (**„Chat-Bot & KI“ → „Chat-Bot (KI)“**) gibt es einen **Betriebsmodus-Switch** mit zwei Positionen:

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
| `!start` / `!go-live` · `!stop` / `!end` · `!diag` / `!status` · `!ask <frage>` | **Owner-Befehle — nur der Streamer** (Broadcaster-Badge oder Allow-List `chat_bot_owner_logins`): Stream starten/stoppen, Diagnose mit Empfehlungen, Frage an die **exklusive Owner-KI** (Fallback: die normale Bot-KI) — nur während eines aktiven Streams; Viewer erhalten nur einen Hinweis (Details: [Owner-Steuerung](#owner-steuerung-nur-der-streamer)) |
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
- **Voraussetzung:** Benachrichtigungszugriff für Vivid (Systemeinstellungen → Benachrichtigungen bzw. Sonderzugriff). Ohne diesen Zugriff dürfen Apps fremde Media-Sessions nicht sehen — der Bot antwortet dann mit einem Hinweis statt zu steuern. Im Settings-Screen gibt es dafür den Button **„Benachrichtigungszugriff aktivieren“** (öffnet die Systemeinstellung; Kategorie **„Chat-Bot & KI“**, unten bei „Media-Befehle“).
- Technisch läuft das über einen leeren `MediaNotificationListener` (nur Zugriffs-Marker — er liest **keine** Benachrichtigungen aus) + `MediaSessionManager.getActiveSessions(...)` → `MediaController.TransportControls`.
- Es wird die aktive Session (playing/paused/buffering) bevorzugt, sonst die erste verfügbare.

> 💡 **Tipp:** Im COMMAND-Modus funktioniert der Bot komplett ohne LLM-API-Key — ideal, wenn du nur die Moblin-artigen Chat-Befehle willst.

## Koexistenz mit anderen Bots (z. B. Rivulet)

Läuft neben dem Chat-Bot eines anderen Tools im selben Kanal (z. B. dem geplanten **Rivulet**-Bot, [github.com/thoser666/rivulet](https://github.com/thoser666/rivulet), Meilenstein M9), gibt es drei echte Kollisionspunkte — alle lassen sich in den Einstellungen (**„Chat-Bot & KI“ → „Koexistenz mit anderen Bots“**) entschärfen:

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

## Begrenzungen (pro Viewer + Kosten)

Im Settings-Screen (**„Chat-Bot & KI“ → „Begrenzungen“**) kann der Streamer drei Schutzstufen einstellen — alle mit `0 = aus/unbegrenzt`. Sie greifen **vor Befehlen und vor LLM-Antworten** und funktionieren **plattformneutral** über die `userId` der Plattform (Twitch-User-ID, YouTube-`channelId`, Kick-User-ID) — die Engine liegt ja über allen Plattform-Adaptern.

| Einstellung | Default | Wirkung |
|-------------|---------|---------|
| **Per-Viewer-Cooldown** (Sekunden) | 60 | Nach einer Antwort muss derselbe Viewer warten, bevor der Bot ihm erneut antwortet — schützt vor Einzel-Spammern und deckelt den LLM-Verbrauch pro Person |
| **Max. Antworten pro Viewer** (pro Stream) | 0 (aus) | Total-Cap: jeder Viewer bekommt höchstens N Antworten pro Stream (zählt auch Befehle) |
| **Kosten-Budget: Max. Antworten pro Stunde** | 0 (aus) | Globale Obergrenze aller Bot-Antworten pro Stunde — der direkte LLM-Kosten-Deckel (inkl. Befehle) |

**Moderatoren umgehen die Per-Viewer-Limits** (Cooldown + Cap), damit Mods den Bot jederzeit direkt ansprechen können — das globale Rate-Limit und das Stunden-Budget gelten aber auch für sie. Alle Zähler setzen bei **Stream-Ende/-Start** zurück; das Stunden-Budget rolliert über ein gleitendes 1-Stunden-Fenster.

### Schnellstart: Voreinstellungen

Über den Limit-Feldern gibt es eine **Voreinstellungs-Leiste** („Locker · Balanced · Streng · Eigene“): Ein Tipp füllt Cooldown/Cap/Budget passend vor (danach weiterhin frei anpassbar). Die **zuletzt gewählte Voreinstellung wird gespeichert** (`chat_bot_limit_preset`-Key) und beim **App-Start wiederhergestellt** — eine manuelle Änderung eines Limit-Felds markiert die Auswahl als „Eigene“ (CUSTOM), damit die wiederhergestellte Stufe nicht die Bearbeitung überstimmt. Bei fehlender/„Eigene“-Auswahl fällt die Anzeige auf das Wert-Matching zurück.

| Stufe | Cooldown/Viewer | Cap/Viewer (Stream) | Budget/Stunde | Für |
|-------|-----------------|---------------------|---------------|-----|
| **Locker** | 30 s | 0 (aus) | 0 (aus) | Viel Interaktion, minimaler Schutz |
| **Balanced** | 60 s | 10 | 120 | Standard — guter Spam- und Kosten-Schutz |
| **Streng** | 180 s (3 min) | 5 | 60 | Strikt gegen Einzel-Spammer und LLM-Kosten |

Unter den Feldern zeigt **„Live-Verbrauch“** den aktuellen Zählerstand (nur solange der Bot aktiv ist): **Antworten diese Stunde** (mit Budget-Anzeige `x / y` bzw. „kein Budget“), **Antworten in diesem Stream** und die **Top-Viewer** (Anzeigename + Anzahl). So sieht der Streamer direkt, wie nah er am Kosten-Budget ist — die Zähler setzen bei Stream-Ende zurück.

> **Stufen von locker bis streng:** Alles aus (nur globaler Cooldown + Rate-Limit) → Per-Viewer-Cooldown 60 s → zusätzlich Per-Viewer-Cap → zusätzlich Stunden-Budget. Wer den KI-Teil ganz abschalten will, wählt im Betriebsmodus-Switch **„Bot (wie Moblin)“** — dann beantwortet der Bot nur kostenlose `!`-Befehle, und die Begrenzungen gelten nur noch für die.

## Owner-Steuerung (nur der Streamer)

Die normale Interaktion mit den Viewern läuft über den **Hauptaccount** (den Bot). Zusätzlich gibt es **Owner-Befehle, die nur der Streamer nutzen kann** — sie können Stream starten/stoppen, einen Diagnose-Lauf ausführen und eine **eigene, exklusiv für die Streamer-Befehle erreichbare Owner-KI** fragen (z. B. ein leistungsfähigeres oder persönliches Modell-Konto). Ist **keine eigene Owner-KI** hinterlegt, fallen die Befehle als **Fallback auf die normale Bot-KI** (Viewer-LLM) zurück; nur wenn auch die nicht konfiguriert ist, liefern sie deterministische Antworten (Checkliste bzw. Hinweis). Owner-Befehle sind nur während eines aktiven Streams verfügbar (der Bot läuft nur bei Go-Live).

### Wer ist Owner?

- **Der Kanal-Inhaber automatisch** (Twitch-`broadcaster/1`-Badge wird geparst → `isBroadcaster` auf der Chat-Nachricht).
- **Zusätzlich eingetragene Logins** (`chat_bot_owner_logins`, kommasepariert ohne `@`) — z. B. der **Zweitaccount** des Streamers, wenn er nicht mit dem Kanal-Account im Chat ist.

Viewer, die einen Owner-Befehl tippen, bekommen nur den Hinweis „⚠️ Dieser Befehl ist nur für den Streamer.“ — es wird **nichts** ausgeführt.

### Owner-Befehle (beide Betriebsmodi)

| Befehl | Syntax | Wirkung |
|--------|--------|---------|
| Stream starten | `!start` / `!go-live` | Startet den Stream mit den gespeicherten Einstellungen (primär + optional zweites Ziel) — gleiche Logik wie die Web-Remote-Control |
| Stream stoppen | `!stop` / `!end` | Stoppt den Stream |
| Diagnose | `!diag` / `!status` | **Diagnose-Lauf:** sammelt deterministisch Stream-Status (inkl. Fehlerursache), OBS-Verbindung und Konfigurations-Checks (URL/Key, Multi-Streaming, Chat-Kanal, Bot-Token, **Whisper-Antwortweg (Client-ID + Token)**, Viewer-/Owner-LLM, **Owner-KI-Quelle** — offen, wenn weder Owner- noch Viewer-LLM konfiguriert sind). **Owner-KI exklusiv** → Bewertung + konkrete Empfehlungen; ohne eigene Owner-KI → **Viewer-KI als Fallback**; ohne jede KI → Checkliste direkt im Chat. Die Antwort weist die **KI-Quelle** aus („Auswertung durch: eigene Owner-KI / Viewer-KI (Fallback) / deterministisch“) |
| Owner-KI fragen | `!ask <frage>` | Stellt die Frage an die **Owner-KI** (exklusiv; ohne eigene Owner-KI Fallback auf die Viewer-KI) mit dem aktuellen Stream-Zustand als Kontext (z. B. „!ask warum stockt der Stream?“) |

#### Befehls-Syntax (Referenz)

- **Case-insensitive:** `!START` = `!start`; Befehle können **mitten in der Nachricht** stehen (`@vividbot !diag bitte`).
- **Parameter** gibt es nur bei `!ask`: Alles nach dem Befehlstoken ist die Frage (`!ask warum stockt der Stream?` → Text `warum stockt der Stream?`). `!ask` **ohne** Text antwortet mit „Bitte gib eine Frage an: !ask <frage>“.
- **PREFIX-Scope:** präfixierte Formen `!v!start` / `!v!stop` / `!v!diag` / `!v!ask <frage>` (Präfix `v`) — die generischen `!`-Formen gehören dann dem anderen Bot (Koexistenz-Modus).
- **Verfügbarkeit:** nur während eines aktiven Streams (der Bot verbindet sich nur bei Go-Live) und nur, wenn der Bot selbst konfiguriert und im Chat ist.

#### Owner-Scope (wer darf, wann)

| Absender | `!start` / `!stop` / `!diag` / `!ask` |
|----------|--------------------------------------|
| **Kanal-Inhaber** (Broadcaster-Badge `broadcaster/1`) | ✅ immer Owner — kein Eintrag nötig |
| **Allow-List** (`chat_bot_owner_logins`, z. B. Zweitaccount) | ✅ Owner |
| **Moderatoren** (ohne Owner-Status) | ❌ nur der Hinweis „⚠️ Dieser Befehl ist nur für den Streamer.“ |
| **Viewer** | ❌ nur der Hinweis — keine Aktion, kein LLM-Aufruf |

Der Owner-Scope ist **unabhängig vom Betriebsmodus** (COMMAND/AUTONOMOUS) — Owner-Befehle funktionieren in beiden. Der **Befehlsscope** (ALL/MENTION/PREFIX, siehe [Koexistenz](#koexistenz-mit-anderen-bots-z-b-rivulet)) gilt auch für Owner-Befehle: im MENTION-Scope muss der Owner den Bot ansprechen (`@vividbot !diag`), im PREFIX-Scope die präfixierte Form nutzen.

#### Antwortweg an den Owner

```
Owner (Zweit-/Kanal-Account) ──„!diag“──► Twitch-Chat
        ▼
TwitchChatEventSubReader (liest Nachrichten, EventSub)
        ▼
ChatBotEngine → ist Owner? ──nein──► „⚠️ nur für den Streamer“ (öffentlich, keine Aktion)
        │ ja
        ▼
!start / !stop  → ChatStreamControl.start()/stop() → Bestätigung
!diag            → StreamDiagnostics (Status, OBS, Checks)
        ├─ ohne Owner-KI ──► Viewer-KI als Fallback (Fact-Sheet)
        │                    └─ auch ohne jede KI ──► deterministische Checkliste
        └─ mit Owner-KI ───► Owner-LLM (Fact-Sheet) ──Empfehlungen
!ask <frage>     → Owner-LLM (Fact-Sheet + Frage) ──Antwort
        │
        ▼  (Antwortweg, Standard: privat)
TwitchWhisperClient (Helix-API: Login → User-ID, POST /helix/whispers)
        │ erfolgreich (HTTP 204)
        ▼
Whisper an den Owner-Login (privat, nicht im Chat)
        │ Fehler (Client-ID fehlt, Scope fehlt, Empfänger blockt, …)
        ▼
Fallback: öffentliche Antwort (Send-Chat-API) in den Kanal + Bot-Log-Hinweis
```

**Wo die Antwort landet (Standard: Whisper):** Owner-Antworten kommen per **Twitch-Whisper** direkt an den Owner-Login statt in den öffentlichen Chat — Diagnose, Empfehlungen und Fragen sind damit nur für den Streamer sichtbar. Umsetzung über die **Helix-Whisper-API** (`POST /helix/whispers`) — der frühere IRC-Weg (`/w` via PRIVMSG) ist von Twitch seit Februar 2023 abgeschaltet. Voraussetzungen:

- Der **OAuth-Token des Bot-Kontos** braucht den Scope **`user:manage:whispers`** (zusätzlich zu `user:read:chat`/`user:write:chat`).
- Die **Twitch-App-Client-ID** ist in den Einstellungen hinterlegt (`chat_bot_twitch_client_id`) — Pflicht-Header für alle Helix-Aufrufe.
- Das Sender-Konto braucht eine **verifizierte Telefonnummer** (Twitch-Anforderung).

Twitch-Limits: 40 eindeutige Empfänger/Tag, 3 Whispers/s, 100/min, 500 Zeichen für die erste Nachricht an einen User (der Bot kürzt automatisch auf 500), und Twitch kann Whispers **still verwerfen** (auch bei HTTP 204). Blockt der Empfänger Whispers von Fremden (`Block Whispers from Strangers`), antwortet Twitch mit 400 — der Bot **fällt dann öffentlich in den Chat zurück** (Zuverlässigkeit der Bestätigung schlägt Privatsphäre im Fehlerfall) und loggt den Grund. Der ganze Weg lässt sich im Settings-Screen abschalten (**„Chat-Bot & KI“ → „Owner-Zugriff (nur Streamer)“**, `chat_bot_owner_whisper_replies`); ohne Client-ID/Scope geht die Antwort automatisch öffentlich.

#### Whisper-Empfang (EventSub) — private Befehle an den Bot

Der Streamer kann dem Bot auch **privat** Befehle schicken: Der Bot verbindet sich bei Go-Live mit einer **zweiten EventSub-WebSocket-Verbindung** (zusätzlich zum Chat-Reader) und subscribt `user.whisper.message` für die Bot-User-ID (`TwitchEventSubClient`). Eingehende Whispers landen als `ChatMessage` mit `isWhisper = true` in der Engine — es gelten dieselben Voraussetzungen wie beim Senden (Bot-Token mit Scope `user:manage:whispers`, Twitch-App-Client-ID).

- **Nur Owner-Befehle:** Whispers werden nie in die Viewer-/LLM-Pfade eingespeist. `!start`/`!stop`/`!diag`/`!ask` werden verarbeitet, alle anderen Nachrichten (auch `!help` von Viewern) werden ignoriert — Whispers sind kein Viewer-Kanal.
- **Owner-Erkennung:** Whisper vom **Kanal-Login** (Absender-Login == Kanalname) gilt automatisch als Broadcaster (Twitch verifiziert die Absender-Identität); zusätzlich greift die Allow-List `chat_bot_owner_logins` (z. B. Zweitaccount). Nicht-Owner bekommen nur den Hinweis **als Whisper zurück**.
- **Antwort bleibt privat:** Antworten auf Whispers gehen immer als Whisper an den Absender — auch wenn der Whisper fehlschlägt, wird **nie öffentlich** geantwortet (kein Leaken der privaten Interaktion in den Kanal).
- **Reconnect:** Beim `session_reconnect` übernimmt die neue WebSocket-Session die Subscriptions automatisch (kein Re-Subscribe); bei hartem Verbindungsverlust wird nach dem Reconnect neu subscribt (Backoff wie beim Chat-Reader).

### Owner-KI (optional, exklusiv)

`!ask` und die Empfehlungen von `!diag` laufen über einen **eigenen LLM-Endpunkt** (`chat_bot_owner_llm_base_url` / `_api_key` / `_model`) — unabhängig vom Viewer-LLM, damit der Streamer z. B. ein leistungsfähigeres oder persönliches Modell-Konto nutzen kann. Dieser Endpunkt ist **exklusiv für die Streamer-Befehle erreichbar** (Viewer-Nachrichten erreichen ihn nie). **Fallback:** Ist keine eigene Owner-KI hinterlegt, nutzen die Owner-Befehle die **normale Bot-KI** (Viewer-LLM) mit dem Owner-Kontext (Fact-Sheet); nur wenn auch die nicht konfiguriert ist (z. B. COMMAND-Modus), liefert `!diag` die deterministische Checkliste und `!ask` einen Konfigurations-Hinweis.

### Limits & Sicherheit

- **Owner-Gate:** Nur Broadcaster + Allow-List — Viewer können weder Aktionen auslösen noch die Owner-KI fragen.
- **Kein Viewer-Cooldown/-Cap für Owner:** Owner-Befehle umgehen Cooldown und Per-Viewer-Limits (sofortige Antwort).
- **Globales Rate-Limit gilt weiter** (Kosten-Schutz); Owner-Antworten zählen auch ins Stunden-Budget.
- **Fehler-Resilienz:** Schlägt die Owner-KI fehl, kommt bei `!diag` trotzdem die deterministische Checkliste durch.
- **Privater Antwortweg:** Owner-Antworten gehen per Twitch-Whisper (Helix-API) statt in den öffentlichen Chat — Standard an, per Setting abschaltbar; Fehler (fehlende Client-ID, fehlender Scope, blockierter Empfänger) fallen öffentlich mit Log-Hinweis zurück.
- **Entkoppelt:** `feature-chat` hängt nicht an `feature-streaming` — die Owner-Steuerung läuft über das `ChatStreamControl`-Interface (`@BindsOptionalOf` in feature-chat, echte Implementierung `AppChatStreamControl` in der App).

## Current status

Implemented & unit-tested:

- `OpenAiCompatibleLlmClient` — OpenAI-compatible `/v1/chat/completions` client (Bearer auth, error handling, configurable model/temperature/max tokens).
- `TwitchChatEventSubReader` — EventSub-WebSocket-Reader für `channel.chat.message` (Scope `user:read:chat`): `session_welcome` → Helix-Subscribe, `notification` → `ChatMessage` (Badges/Emotes/Farbe/Zeitstempel), `session_reconnect` + Backoff-Reconnect. Ersetzt den IRC-Leser für Overlay **und** Bot.
- `TwitchSendChatClient` — Helix-Send-Client (`POST /helix/chat/messages`, Scope `user:write:chat`): User-ID-Auflösung mit Cache, 500-Zeichen-Limit, `drop_reason`-Auswertung statt IRC-NOTICE. Ersetzt das IRC-`PRIVMSG`-Senden.
- `ChatBotEngine` — reaction engine: ignores the bot's own messages, mentions-only filter, cooldown, per-minute rate limit, rolling prompt history, 500-char reply cap, `ChatBotState` (Disabled/Idle/Thinking).
- `ChatBotController` + `StreamingService` wiring — automatic connect on Go Live, clean shutdown on stream end, instant stop when the bot is disabled in settings. Tracks the stream start timestamp for `!uptime`, wires the chat TTS to the message stream.
- **Chat-TTS (`!tts`)** — `ChatTtsController` (enabled state, speaks viewer messages, skips own messages and commands) + `AndroidTtsSpeaker` (system TextToSpeech engine).
- **Media-Player-Steuerung (`!song`/`!next`/`!pause`/`!play`/`!prev`)** — `ChatMediaController` (MediaController über aktive MediaSessions) + `MediaNotificationListener` (Benachrichtigungszugriff als Voraussetzung).
- **Mode switch** in Vivid's Settings screen („Bot (wie Moblin)“ ↔ „KI autonom“) incl. enable toggle — see [Betriebsmodi (der Switch)](#betriebsmodi-der-switch).
- **Per-Viewer-Cooldown, Per-Viewer-Cap und Stunden-Budget** — configurable limits in the settings screen („Begrenzungen“), platform-neutral keyed on `userId`, moderators bypass the per-viewer limits; counters reset on stream start/stop.
- Bot settings in `SettingsRepository`/`AppSettings` (`chat_bot_mode` key).

Still open (next milestones):

- **Twitch OAuth browser flow** (until then, paste a chat token — see below).
- Provider presets (one-tap LLM provider config).

## Prerequisites

1. A **Twitch account for the bot** (can be your main account or a dedicated one).
2. A **chat token** for that account with the scopes `user:read:chat` (lesen, EventSub), `user:write:chat` (senden, Helix) und — nur wenn du die Owner-Whisper-Antworten nutzt — `user:manage:whispers`.
3. An **LLM API key** (or a reachable Ollama instance) — only needed in „KI autonom“ mode; the „Bot wie Moblin“ command mode works without one.

## 1. Getting a Twitch chat token

> ⚠️ Only generate tokens for bots you control. A chat token grants access to **read and send messages as that account**.

You need a token for a **bot account** (not a user account), i.e. an account that isn't heavily rate-limited on chat.

1. Create your bot account at <https://www.twitch.tv> and confirm the email.
2. Generate an access token with the `user:read:chat`, `user:write:chat` and `user:manage:whispers` scopes using the official Twitch token generator: <https://twitchtokengenerator.com> → pick the **Chat Read/Write** (or *Chat Bot*) scope set → authorize with the **bot account**.
   - If you prefer the raw OAuth flow: authorize against `https://id.twitch.tv/oauth2/authorize` with `response_type=token` and `scope=user:read:chat user:write:chat user:manage:whispers`.
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
| Per-viewer cooldown | `chat_bot_per_viewer_cooldown_seconds` | `60` (default, `0` = aus) |
| Max replies/viewer | `chat_bot_per_viewer_max_replies` | `0` (default = unbegrenzt) — Cap pro Stream |
| Hourly budget | `chat_bot_max_replies_per_hour` | `0` (default = unbegrenzt) — LLM-Kosten-Deckel |
| Ignore other bots | `chat_bot_ignore_bots` | `rivuletbot` (kommasepariert, ohne `@`) |
| Command scope | `chat_bot_command_scope` | `ALL` (default) / `MENTION` / `PREFIX` |
| Command prefix | `chat_bot_command_prefix` | `v` → `!v!help` (nur bei `PREFIX`) |
| Limit preset | `chat_bot_limit_preset` | `CUSTOM` (default) — zuletzt gewählte Stufe (`LOCKER`/`BALANCED`/`STRICT`), wird beim Start wiederhergestellt |
| Owner logins | `chat_bot_owner_logins` | `` (default) — kommasepariert, ohne `@`; zusätzlich zum Broadcaster |
| Owner-LLM base URL | `chat_bot_owner_llm_base_url` | `` (default) — eigener Endpunkt für `!ask`/`!diag` |
| Owner-LLM API key | `chat_bot_owner_llm_api_key` | `` (default) |
| Owner-LLM model | `chat_bot_owner_llm_model` | `` (default) |

Alle Felder sind direkt im **Settings-Screen** (Kategorie **„Chat-Bot & KI“**, Abschnitte „Bot-Konto & LLM“, „Bot-Verhalten“, „Begrenzungen“) editierbar und werden mit „Speichern“ persistiert. Token und API-Key sind als Passwortfelder mit Sichtbarkeits-Toggle (Auge) hinterlegt.

## Behavior & safeguards

- **Auto-connect / auto-shutdown** — the bot follows the stream lifecycle: connect on Go Live, clean disconnect on stop (even remote/`StreamingState.Idle` stops and process restarts).
- **Mentions-only** — with the default `mentionsOnly = true`, the bot only reacts to messages containing its login or `!bot`. Turn it off if you want it to answer every message (keep an eye on the rate limit).
- **Cooldown** — at least `replyCooldownSeconds` between two replies (default 8 s).
- **Rate limit** — at most `maxRepliesPerMinute` replies per rolling minute (default 10) — Twitch's Send-Chat-API limit is 20 messages/20 s; the default stays well below it.
- **Per-viewer cooldown** — a viewer must wait `perViewerCooldownMillis` after a bot reply before the bot answers them again (default 60 s, `0` = off); moderators bypass it. Platform-neutral via the user id.
- **Per-viewer cap** — at most `perViewerMaxReplies` replies per viewer per stream (`0` = unlimited); moderators bypass it.
- **Hourly budget** — at most `maxRepliesPerHour` replies per rolling hour across all viewers (`0` = unlimited) — the LLM cost ceiling.
- **Reply cap** — every reply is trimmed to 500 characters and line breaks are collapsed (Twitch's message limit).
- **No echo** — the bot ignores messages it sent itself.
- **LLM errors** — a failed request is logged (no message is sent) and the bot stays available; it never crashes the stream.

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Bot never joins | `chat_bot_enabled` off, or `chat_channel`/`chat_bot_login`/`chat_bot_oauth_token` empty → fill all three |
| `Login authentication failed` in chat | Token invalid, expired, revoked, or generated for a **different** account than `chat_bot_login` |
| Bot connects but can't send | Token lacks the `user:write:chat` scope (Senden läuft seit dem IRC-Ausstieg über `POST /helix/chat/messages`) |
| Bot liest nichts / Overlay zeigt „nicht konfiguriert“ | Token lacks the `user:read:chat` scope → die EventSub-Subscription wird von Twitch mit 403 abgelehnt; Token neu erzeugen (Scopes s. o.) |
| No replies to mentions | `mentionsOnly` on and the message doesn't contain the bot login or `!bot`; or cooldown/rate limit hit |
| `LLM-Anfrage fehlgeschlagen` | Wrong base URL, invalid API key, model name not available on the provider, or no network access (Ollama: phone must reach the host) |

## Roadmap

- ✅ **Mode switch** in Vivid's settings („Bot wie Moblin“ ↔ „KI autonom“) — done.
- ✅ **Full bot settings screen** inside Vivid (all fields from the configuration: Twitch token, LLM credentials, limits, coexistence) — done.
- ✅ **Media-player control** (`!song`/`!next`/`!pause`/`!play`/`!prev` via MediaSession, Moblin parity row) — done (needs notification access).
- ✅ **Per-viewer cooldown, per-viewer cap and hourly cost budget** — done (see [Begrenzungen](#begrenzungen-pro-viewer--kosten)).
- ✅ **Owner-Steuerung (nur der Streamer)** — `!start`/`!stop`/`!diag`/`!ask` mit Owner-Gate (Broadcaster + Allow-List) und separater Owner-KI (see [Owner-Steuerung](#owner-steuerung-nur-der-streamer)).
- ✅ **Chat komplett auf Helix/EventSub (IRC-Ausstieg)** — Lesen über EventSub `channel.chat.message` (`TwitchChatEventSubReader`), Senden über `POST /helix/chat/messages` (`TwitchSendChatClient`); IRC-Clients, `IrcConnection` und Parser entfernt. Konsequenz: Overlay und Bot brauchen den Bot-Token (kein anonymes Lesen mehr) — Details: [Umgesetzt unten](#umgesetzt-chat-komplett-auf-helixeventsub-irc-ausstieg).
- Twitch OAuth browser flow (no manual token pasting).
- More platforms once Kick/YouTube chat lands.

## Umgesetzt: Chat komplett auf Helix/EventSub (IRC-Ausstieg)

**✅ Umgesetzt (IRC-Ausstieg):** Twitch empfiehlt die Migration offiziell ([Migrating from IRC](https://dev.twitch.tv/docs/chat/irc-migration/)); IRC ist im [Product Lifecycle](https://dev.twitch.tv/docs/product-lifecycle/) weiterhin „Active“, wird hier aber nicht mehr genutzt. **Alle IRC-Stellen sind entfernt:** `TwitchChatClient` (anonymes Overlay) und `TwitchBotClient` (Bot, lesen+senden) wurden durch `TwitchChatEventSubReader` (EventSub `channel.chat.message`) und `TwitchSendChatClient` (Helix `POST /helix/chat/messages`) ersetzt; `IrcConnection`/`IrcMessageParser` sind gelöscht. Whispers liefen bereits auf Helix/EventSub.

| Funktion | Heute (IRC) | Ziel (Helix/EventSub) |
|----------|-------------|----------------------|
| Chat lesen | `PRIVMSG`, Scope `chat:read` | EventSub `channel.chat.message`, Scope `user:read:chat` |
| Chat senden | `PRIVMSG`, Scope `chat:edit` | `POST /helix/chat/messages`, Scope `user:write:chat` |
| Badges | `badges`-Tag („broadcaster/1“, …) | `event.badges[]` (`set_id`/`id`/`info`) |
| Emotes | `emotes`-Tag (`id:start-end`) | `message.fragments[]` (emote-Objekte) |
| Mod/Sub/Broadcaster | `mod=1`, `subscriber=1`, Badge | Badge-`set_id`s `moderator`/`subscriber`/`broadcaster` |
| Chat-Farbe | `color`-Tag | `event.color` |
| Zeitstempel | `tmi-sent-ts` | `message_timestamp` |
| Keepalive | `PING`/`PONG` | WebSocket-Ping-Frames + `session_keepalive` (OkHttp automatisch) |
| Reconnect | `RECONNECT` | `session_reconnect` (bereits im `TwitchEventSubClient`) |
| JOIN/PART/NAMES | (wird nicht genutzt) | entfällt — Get Chatters nur für Mods/Broadcaster |

**Umgesetzte Entscheidungen:**

1. **Anonymes Overlay entfällt** — EventSub erfordert einen authentifizierten User-Token. Das Chat-Overlay nutzt jetzt die **Bot-Zugangsdaten** (Login + OAuth-Token + Client-ID aus den Einstellungen); ohne konfigurierte Credentials zeigt das Overlay einen Hinweis statt zu verbinden.
2. **User-IDs aufgelöst** — Subscription-Condition und Send-Chat brauchen `broadcaster_user_id`/`sender_id` (Auflösung via `GET /helix/users`; `resolveUserId` mit Cache).
3. **Scopes gewechselt** — `chat:read`/`chat:edit` → `user:read:chat`/`user:write:chat` (User-Access-Token; `user:bot`/`channel:bot` sind nur bei App-Access-Tokens nötig). Doku, Setup-Anleitung (RELEASE.md) und Troubleshooting sind angepasst.
4. **Emotes-Format gemappt** — `ChatMessage.emotesTag` wird aus den EventSub-Fragments auf das IRC-Format (`id:start-end`) abgebildet (kompatibel mit der Engine; das Overlay rendert weiterhin nur Text).
5. **Senden mit Fehlerdetails** — `drop_reason` im Response (Slow-Mode, verifizierte E-Mail, Bann, …) statt IRC-NOTICE; Rate-Limit (~20/20 s pro User) ist für den Bot (Cooldown ≥ 8 s) unkritisch.
6. **EventSub-Limits passend** — max. 3 WebSocket-Verbindungen mit Subscriptions; genutzt werden 2 (Chat + Whisper) für 1 Kanal — unkritisch.

**Migrationsplan (1 Feature-Bündel) — abgeschlossen:**

1. ✅ `TwitchChatEventSubReader` — `channel.chat.message`-Subscription + Event→`ChatMessage`-Mapping (Badges/Emotes/Farbe/Zeitstempel) — Muster: `TwitchEventSubClient`.
2. ✅ `TwitchSendChatClient` — `POST /helix/chat/messages` mit `broadcaster_id`/`sender_id` + `drop_reason`-Auswertung — Muster: `TwitchWhisperClient`.
3. ✅ Overlay + Bot auf den EventSub-Reader umgestellt (Token-Fluss: Bot-Zugangsdaten, s. o.); Bot-Senden über `TwitchSendChatClient`.
4. ✅ `IrcConnection`, `IrcMessageParser`, `TwitchChatClient`, `TwitchBotClient` und die IRC-Handshakes entfernt (auch Tests + DI-Binding).
5. ✅ Scopes/Doku/Tests aktualisiert; IRC-Tests durch EventSub-/Helix-Tests ersetzt.

## Architecture

| File | Responsibility |
|------|----------------|
| `feature-chat/…/ai/LlmClient.kt` | `LlmClient` interface + `OpenAiCompatibleLlmClient` (Ktor) + `LlmConfig` |
| `feature-chat/…/ai/LlmModels.kt` | Chat-Completion request/response DTOs (kotlinx.serialization) |
| `feature-chat/…/twitch/TwitchChatEventSubReader.kt` | EventSub-Reader `channel.chat.message` (lesen, Scope `user:read:chat`) |
| `feature-chat/…/twitch/TwitchSendChatClient.kt` | Helix-Send-Client `POST /helix/chat/messages` (senden, Scope `user:write:chat`, inkl. `drop_reason`) |
| `feature-chat/…/bot/ChatBotEngine.kt` | Reaction engine (mode switch, filters, history, LLM call, send, state) |
| `feature-chat/…/bot/BotCommandProcessor.kt` | Deterministic `!`-commands („Bot wie Moblin“, no LLM) — incl. `!tts` |
| `feature-chat/…/bot/ChatTtsController.kt` | Chat-TTS: enabled state, speaks viewer messages, `toggle()` for `!tts` |
| `feature-chat/…/bot/AndroidTtsSpeaker.kt` | Android TextToSpeech implementation of the speaker interface |
| `feature-chat/…/media/ChatMediaController.kt` | Media-Player-Steuerung (MediaController über aktive MediaSessions) |
| `feature-chat/…/media/MediaNotificationListener.kt` | Notification-Listener (Zugriffs-Marker für Media-Session-Steuerung) |
| `feature-chat/…/bot/ChatBotConfig.kt` | Settings → engine configuration (incl. mode, owner logins, owner LLM) |
| `feature-chat/…/bot/ChatStreamControl.kt` | `ChatStreamControl` interface + `StreamDiagnostics` (Owner-Steuerung, entkoppelt) |
| `app/…/AppChatStreamControl.kt` | App-Implementierung der Owner-Steuerung (Stream-Start/Stopp + Diagnose) + Hilt-Binding |
| `feature-chat/…/bot/ChatSender.kt` | Send abstraction (interface) |
| `feature-chat/…/bot/ChatBotController.kt` | Stream-lifecycle → bot wiring |
| `app/…/StreamingService.kt` | Calls `onStreamStarted()` / `onStreamStopped()` |
| `core/…/SettingsRepository.kt` | Persists the bot settings (`chat_bot_*` keys) |
