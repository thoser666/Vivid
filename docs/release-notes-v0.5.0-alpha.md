# 🚀 Release-Notes v0.5.0-alpha

| | |
|---|---|
| **Version** | `0.5.0-alpha` (versionCode `5001`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.0-alpha` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_alpha` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Der KI-Chat-Bot ist da

Seit `v0.4.2-alpha` (15.08.2026) sind **sechs Feature-Commits** mit dem kompletten Chat-Bot-System auf `develop` gelandet — das bisher größte nutzersichtbare Update. Der Bot läuft **direkt auf dem Gerät**, verbindet sich beim Go-Live automatisch mit deinem Twitch-Chat und fährt beim Stream-Ende sauber herunter (eingebunden in den Streaming-Foreground-Service). Kein Dashboard, keine Monatsgebühr — du zahlst nur deinen eigenen LLM-Anbieter.

## ✨ Was neu ist

### 1. Betriebsmodus-Switch („Bot wie Moblin“ ↔ „KI autonom“)

In den Einstellungen (Abschnitt **„Chat-Bot (KI)“**) wählst du den Modus:

| Modus | Verhalten | LLM nötig? |
|---|---|---|
| **„Bot (wie Moblin)“** (`COMMAND`) | Deterministischer Befehls-Bot: reagiert nur auf `!`-Befehle mit festen Antworten — genau wie der Chat-Bot von Moblin | Nein — nur ein Twitch-Chat-Token |
| **„KI autonom“** (`AUTONOMOUS`) | Die KI entscheidet selbst: bekannte `!`-Befehle werden sofort beantwortet, alle übrigen (freigegebenen) Nachrichten bewertet das LLM und entscheidet, ob und wie es antwortet — inklusive bewusstem Schweigen | Ja — OpenAI-kompatibles LLM |

### 2. Kompletter Settings-Screen für den Bot

Alle Konfiguration an einer Stelle: Bot-Konto/Login, Twitch-Chat-Token, LLM-Endpunkt/-Key/-Modell (jeder **OpenAI-kompatible** Endpoint: OpenAI, Google Gemini, Groq, DeepSeek oder lokales Ollama), System-Prompt, Antwort-Cooldown, Mentions-only, Rate-Limit — plus Button „Benachrichtigungszugriff aktivieren“ für die Media-Steuerung.

### 3. Chat-Befehle (Moblin-Parität)

| Befehl | Antwort |
|---|---|
| `!help` / `!commands` | Listet die verfügbaren Befehle |
| `!uptime` | Wie lange der Stream schon läuft |
| `!tts` | **Chat-Text-to-Speech umschalten** (an/aus) — liest Chat-Nachrichten auf dem Gerät des Streamers laut vor |
| `!song` / `!nowplaying` | Aktuellen Titel melden |
| `!next` / `!skip` · `!pause` / `!play` · `!prev` / `!previous` | Media-Player-Steuerung über die **MediaSession** des Geräts (Apple Music, Spotify, YouTube Music, …) |
| `!bot` | Kurzinfo über den Bot |

Befehle sind case-insensitive, dürfen mitten in der Nachricht stehen (`@bot !help`) und werden vom Chat-TTS nicht selbst vorgelesen. Cooldown + Rate-Limit gelten für alle Antworten (Twitch: 20 Nachrichten/30 s).

### 4. Koexistenz mit anderen Bots (z. B. Rivulet)

Läuft ein zweiter Bot (z. B. der geplante Rivulet-Bot, M9) im selben Kanal, lassen sich alle Kollisionspunkte per Settings entschärfen:

- **Befehlsscope** in drei Stufen: `ALL` (Standard, Moblin-Stil) · `MENTION` (nur `@vividbot !help`) · `PREFIX` (nur `!v!help` bei Präfix `v`). Fremde Befehle werden **stumm** ignoriert (kein „Unbekannter Befehl“-Echo), damit der andere Bot ungestört antwortet.
- **Andere Bots ignorieren:** Login(s) eintragen (kommasepariert) → deren Nachrichten werden komplett ignoriert — keine Befehle, kein LLM-Input, **auch nicht vorgelesen** vom Chat-TTS.

### 5. Begrenzungen (pro Viewer + Kosten)

Drei einstellbare Schutzstufen, jede mit `0 = aus`:

| Einstellung | Default | Wirkung |
|---|---|---|
| **Per-Viewer-Cooldown** (Sekunden) | 60 | Derselbe Viewer bekommt erst nach Ablauf wieder eine Antwort — schützt vor Einzel-Spammern und deckelt den LLM-Verbrauch pro Person |
| **Max. Antworten pro Viewer** (pro Stream) | 0 (aus) | Total-Cap pro Person (zählt auch Befehle) |
| **Kosten-Budget: Antworten pro Stunde** | 0 (aus) | Globaler LLM-Kosten-Deckel, gleitendes 1-Stunden-Fenster |

**Moderatoren** umgehen die Per-Viewer-Limits (Cooldown + Cap); das globale Rate-Limit und das Stunden-Budget gelten auch für sie. Alle Zähler setzen bei Stream-Ende/-Start zurück. Alles funktioniert **plattformneutral** über die `userId` der Plattform (Twitch-User-ID, YouTube-`channelId`, Kick-User-ID) — Kick/YouTube hängen später nur noch als Adapter an.

### 6. Schnellstart-Voreinstellungen + Live-Verbrauch

- **Voreinstellungs-Leiste** „Locker · Balanced · Streng · Eigene“ füllt Cooldown/Cap/Budget per Tipp (30/0/0 · 60/10/120 · 180/5/60) — danach weiterhin frei anpassbar. Die **zuletzt gewählte Stufe wird gespeichert** und beim App-Start wiederhergestellt; manuelle Änderungen markieren die Auswahl als „Eigene“.
- **Live-Verbrauch** im Settings-Screen: Antworten diese Stunde (vs. Budget), Antworten in diesem Stream und Top-5-Viewer — das Kosten-Budget ist damit jederzeit beobachtbar.

---

## 🔧 Weitere Änderungen seit v0.4.2-alpha

- **CI-Härtung:** lokales Pre-Push-Gate (Tests + Lint + Secret-Guard vor jedem Push, optionaler Release-Build), Nightly-Publish gegen Orphan-Tags gehärtet (Retry + Rollback + Orphan-Sweep), stabile Release-Publizierung gehärtet (Draft-Delete/Recreate, Tag bleibt), `sweep-orphan-drafts`-Job, Regressionstests für die Publish-Härtung
- **release_beta-Lane** implementiert (Spiegel von `release_alpha`, Stufe `beta`, inkl. Safety-Checks) — bereit für den ersten Beta-Tag, sobald das Beta-Gate erreicht ist
- **Doku:** RELEASE.md (Versionsstrategie, Signing-Secrets, Play-App-Signing, Rollback-Semantik, Pins), PARITY.md (17/17 Moblin-✅, Beta-Gate 2/3), docs/ai-chat-bot.md (komplette Bot-Anleitung), README

## 🧪 Testschwerpunkte für Alpha-Tester

1. **Bot-Konfiguration:** Twitch-Token eintragen, Modus „Bot (wie Moblin)“ → `!help`, `!uptime`, `!tts` testen (TTS togglet, eigene Bot-Nachrichten werden nicht vorgelesen)
2. **KI-Modus:** OpenAI-kompatiblen Endpoint konfigurieren → Chat-Nachricht schreiben, KI-Antwort (≤ 500 Zeichen) prüfen
3. **Media-Steuerung:** Benachrichtigungszugriff erteilen → `!song`, `!next`, `!pause` steuern den aktiven Player
4. **Begrenzungen:** Per-Viewer-Cooldown auf z. B. 30 s → zweite Anfrage desselben Viewers wird blockiert; Live-Verbrauch im Settings-Screen beobachten
5. **Koexistenz:** Befehlsscope `MENTION`/`PREFIX` + Ignore-Liste — generische `!`-Befehle werden stumm ignoriert
6. **Persistenz:** Alle Bot-Settings speichern → App neu starten → Einstellungen inkl. Voreinstellungs-Stufe wiederhergestellt

## ⚠️ Bekannte Einschränkungen

- **Twitch-OAuth-Browserflow** ist noch nicht implementiert — bis dahin wird ein Chat-Token per Paste in den Settings hinterlegt
- **Media-Steuerung** braucht Benachrichtigungszugriff für Vivid (Systemeinstellung); ohne den antwortet der Bot mit einem Hinweis
- **Cross-Track:** Wer von einem Nightly kommt, muss für das Alpha ggf. deinstallieren (Downgrade-Block); alpha → alpha/next ist installierbar

---

*Vollständige Bot-Doku: [docs/ai-chat-bot.md](ai-chat-bot.md) · Feature-Tracking: [PARITY.md](../PARITY.md) · Release-Pipeline: [RELEASE.md](../RELEASE.md)*
