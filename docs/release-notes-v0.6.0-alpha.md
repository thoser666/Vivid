# 🚀 Release-Notes v0.6.0-alpha

| | |
|---|---|
| **Version** | `0.6.0-alpha` (versionCode `6001`, deterministisch aus dem Tag) |
| **Tag** | `v0.6.0-alpha` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_alpha` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Der Chat-Bot verlässt IRC und bekommt eine echte Streamer-Steuerung

Seit `v0.5.0-alpha` (16.08.2026) sind **sieben Feature-Commits** auf `develop` gelandet. Der größte Umbau: Der komplette Twitch-Chat läuft jetzt **ohne IRC** — Lesen über Twitch-EventSub, Senden über die Helix-API (der von Twitch empfohlene Weg). Parallel dazu ist die **Owner-Steuerung** gewachsen: Der Streamer kann den Bot per `!start`/`!stop`/`!diag`/`!ask` steuern, Befehle **privat per Whisper** schicken und bekommt eine **eigene, exklusive KI** für seine Fragen — mit Fallback auf die normale Bot-KI.

## ⚠️ Wichtiger Hinweis: Token-Scopes haben sich geändert

Nach dem IRC-Ausstieg akzeptiert Twitch nur noch die neuen Scopes. **Ein alter Chat-Token funktioniert nicht mehr** — bitte neu erzeugen:

| Zweck | Alt (IRC) | Neu (EventSub/Helix) |
|---|---|---|
| Chat lesen | `chat:read` | `user:read:chat` |
| Chat senden | `chat:edit` | `user:write:chat` |
| Private Owner-Antworten | — | `user:manage:whispers` |

Setup-Anleitung: [RELEASE.md](RELEASE.md) → Abschnitt „🤖 Chat-Bot: Twitch-Token & Client-ID (Setup)".

## ✨ Was neu ist

### 1. Chat komplett auf EventSub/Helix (IRC-Ausstieg)

- **Lesen** über den neuen `TwitchChatEventSubReader` (EventSub-WebSocket `channel.chat.message` v1): Badges, Emotes, Farbe und Zeitstempel kommen direkt aus dem Event; Reconnect mit Backoff + automatischem Re-Subscribe.
- **Senden** über den neuen `TwitchSendChatClient` (`POST /helix/chat/messages`): präzise Fehlerdetails statt IRC-NOTICE (Slow-Mode, Bann, …), User-ID-Cache, 500-Zeichen-Limit.
- **IRC komplett entfernt** (`TwitchChatClient`, `TwitchBotClient`, `IrcConnection`/Parser + Tests). Ein Nebeneffekt: Das **Chat-Overlay braucht jetzt die Bot-Zugangsdaten** (Token + Client-ID) — anonymes Lesen wie früher ist mit EventSub nicht mehr möglich. Ohne Credentials zeigt das Overlay einen Konfigurations-Hinweis.
- Der Bot bleibt bei Send-Fehlern am Leben: Schlägt eine Antwort fehl (z. B. Rate-Limit), wird das geloggt statt die Bot-Coroutine zu beenden.

### 2. Owner-Steuerung — nur der Streamer (Vivid-Zusatz)

Die vier Owner-Befehle sind **nur für den Streamer** erreichbar (Broadcaster-Badge **oder** eigene Allow-List, z. B. für einen Zweitaccount):

| Befehl | Funktion |
|---|---|
| `!start` / `!go-live` | Stream starten (liest alle Stream-Settings aus den App-Einstellungen) |
| `!stop` / `!end` | Stream stoppen |
| `!diag` / `!status` | Diagnose-Lauf: Stream-Status, OBS-Verbindung, **9 Konfigurations-Checks** — mit Bewertung + Empfehlungen durch die Owner-KI |
| `!ask <frage>` | Beliebige Frage mit aktuellem Stream-Zustand als Kontext |

**Privater Antwortweg:** Owner-Antworten kommen als **Twitch-Whisper** direkt an den Streamer statt in den öffentlichen Chat (Diagnose und Empfehlungen bleiben privat). Toggle in den Settings; ohne Client-ID/Token fällt der Bot auf öffentliche Antworten zurück. `!diag` prüft den Antwortweg selbst (Check „Whisper (privater Antwortweg)").

**Whisper-Empfang per EventSub:** Der Streamer kann dem Bot auch **privat** Befehle schicken (`user.whisper.message`-Abos, Reconnect-Support) — z. B. `!diag` vom Zweitaccount ohne, dass jemand im Chat etwas sieht.

### 3. Eigene Owner-KI mit Fallback

Die Owner-Befehle nutzen eine **separate, exklusiv für Streamer-Befehle konfigurierbare KI** (`chat_bot_owner_llm_*`): eigener Endpunkt, eigener Key, eigenes Modell — getrennt von der Viewer-KI, damit die Streamer-Fragen nicht auf den „öffentlichen" Bot-Account gehen.

- **Fallback-Kette:** eigene Owner-KI → normale Bot-KI (Viewer-LLM) → deterministische Checkliste. Nur wenn gar keine KI konfiguriert ist, antwortet `!diag` ohne KI.
- **Transparenz:** Die `!diag`-Antwort weist die Quelle aus („Auswertung durch: eigene Owner-KI / Viewer-KI (Fallback) / deterministisch"); der Settings-Screen zeigt die aktive Quelle live an.
- **Neuer Diagnose-Check „Owner-KI-Quelle"** — offen, wenn weder Owner- noch Viewer-LLM konfiguriert ist.

### 4. Text-/Info-Widget (erstes Beta-Gate-Widget)

Das erste Overlay-Widget ist da (Zeit / GPS / Geschwindigkeit) — konfigurierbar über die Settings, eingehängt ins Streaming-Overlay. Damit ist das Beta-Gate-Kriterium „≥1 Widget" erfüllt.

### 5. Beta-Versionierung + Beta-Gate 3/3

- `release_beta`-Lane ist bereit; der erste Beta-Tag wird automatisch aus dem höchsten `v*`-Tag abgeleitet.
- **Hinweis zur Reihenfolge:** Da mit diesem Release ein neues Alpha (`v0.6.0-alpha`, 6001) erscheint, würde der **erste Beta-Tag auf `v0.6.0-beta` (6002)** rutschen (Ableitung aus dem höchsten Tag). Wer direkt auf Beta will, kann stattdessen den ersten Beta-Tag setzen und dieses Alpha überspringen — Entscheidung liegt beim Maintainer.

---

## 🔧 Weitere Änderungen seit v0.5.0-alpha

- **Diagnose erweitert:** 9 statt 8 Konfigurations-Checks (neu: „Owner-KI-Quelle"; „Whisper (privater Antwortweg)" prüft Client-ID + Token)
- **Doku:** docs/ai-chat-bot.md (EventSub/Helix-How-it-works, Owner-Befehle, Whisper-Setup, Troubleshooting), RELEASE.md (Token-Setup-Anleitung, Beta-Build-Checkliste, Secrets-Ablauf Play-Upload), PARITY.md (Beta-Gate 3/3)
- **Aufräumen:** veraltete IRC-Scope-Referenzen (`chat:read`/`chat:edit`) aus Kommentaren und Settings-Label entfernt

## 🧪 Testschwerpunkte für Alpha-Tester

1. **Neuer Token:** Twitch-Token mit `user:read:chat` + `user:write:chat` (+ `user:manage:whispers` für Owner-Whispers) erzeugen und in den Settings hinterlegen — Chat-Overlay **und** Bot müssen Verbindung aufbauen (vorher ging das Overlay anonym)
2. **Owner-Befehle:** Zweitaccount in die Owner-Allow-List → `!diag` vom Zweitaccount → private Whisper-Antwort mit „Auswertung durch: …"; `!ask`-Frage → Owner-KI-Antwort
3. **Owner-KI-Fallback:** Owner-LLM-Felder leer lassen → `!diag` nutzt die Viewer-KI (Quelle „Viewer-KI (Fallback)"); alles leer (COMMAND-Modus) → deterministische Checkliste
4. **Whisper-Empfang:** Owner schickt dem Bot privat `!diag` — Antwort kommt als Whisper zurück, nichts erscheint im Chat
5. **Send-Fehler-Toleranz:** Bot im Slow-Mode-Kanal → nach einem abgelehnten Send antwortet der Bot auf die nächste Nachricht weiter
6. **Widget:** Text-/Info-Widget aktivieren → Zeit/GPS/Geschwindigkeit im Overlay

## ⚠️ Bekannte Einschränkungen

- **Twitch-OAuth-Browserflow** noch nicht implementiert — Token per Paste in den Settings (Setup-Anleitung in RELEASE.md)
- **Chat-Overlay ohne Credentials:** zeigt einen Konfigurations-Hinweis statt zu verbinden (kein anonymes Lesen mehr)
- **Kick/YouTube/SOOP** weiterhin Post-Beta-Roadmap; Twitch-OAuth-Login (Senden/Moderation) ebenfalls
- **Cross-Track:** Wer von einem Nightly kommt, muss für das Alpha ggf. deinstallieren (Downgrade-Block); alpha → alpha/next ist installierbar

---

*Vollständige Bot-Doku: [docs/ai-chat-bot.md](ai-chat-bot.md) · Feature-Tracking: [PARITY.md](../PARITY.md) · Release-Pipeline: [RELEASE.md](../RELEASE.md)*
