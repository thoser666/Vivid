# Vivid — IRL Streaming App

**Mobile IRL-Streaming-App** — Android-Umsetzung von [Moblin](https://github.com/eerimoq/moblin) mit Twitch-Chat-Overlay, KI-Chat-Bot, OBS-Steuerung und Kamera-Profi-Features.

[📖 Bedienungsanleitung (DE)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.md) ·
[🇬🇧 User Guide (EN)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.en.md) ·
[🇫🇷 Guide utilisateur (FR)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.fr.md) ·
[📦 README](https://github.com/thoser666/Vivid#readme) ·
[🔒 Datenschutz](https://thoser666.github.io/Vivid/privacy/) ·
[🤖 Bot-Doku](https://github.com/thoser666/Vivid/blob/develop/docs/ai-chat-bot.md)

---

## ✨ Features

### 📡 Streaming
- **RTMP / SRT / RTMPS** — frei konfigurierbare Ingest-URL + Stream-Key (Twitch, YouTube, Kick, Owncast oder beliebige Custom-Ziele)
- **Multi-Streaming** — gleichzeitig auf zwei Ziele senden
- **Go-Live-Self-Check** — validiert URL und Key vor dem Start und zeigt verständliche Fehlermeldungen
- **Background-Streaming** — Foreground-Service mit Benachrichtigung, Stream läuft im Hintergrund weiter

### 📷 Kamera
- **Tap-to-Focus**, **Pinch-Zoom**, **Zoom-Reset** (Doppeltipp auf die Vorschau)
- **Fokus-Lock (∞)** gegen Fokus-Hunting, **Bildstabilisierung** (OIS/EIS), **Taschenlampe** (auch per `!torch`-Bot-Befehl)

### 💬 Twitch-Chat & Overlay
- Chat via **EventSub + Helix** (kein IRC), Chat-Overlay über der Live-Vorschau
- **Inline-Emotes**, **Badges**, **Event-Alerts** (Follow/Sub/Raid/Sub-Gift/Resub — Probe per `!testalert`)
- **Moderation** — `!ban`, `!timeout`, `!delete`

### 🤖 KI-Chat-Bot
- OpenAI-kompatible LLM-Provider, verbindet sich automatisch beim Go Live
- **Owner-Modus** — private Befehle `!start`/`!stop`/`!diag`/`!ask` (Whisper-Antworten, separate Owner-KI möglich)
- Begrenzungen (Cooldown/Cap/Kosten-Budget), Presets „Locker / Balanced / Streng", TTS-Steuerung, Media-Player-Kommandos

### 🎛️ Steuerung & Widgets
- **OBS-Steuerung** per WebSocket (+ QR-Import), **Web-Remote-Control** im LAN
- **Widgets** (Zeit/GPS/Geschwindigkeit), **Darstellung**: Hell/Dunkel/AMOLED + Akzentfarben, **I18n** in de/en/fr

---

## 🔗 Nützliche Links

### 📚 Dokumentation

| Link | Inhalt |
|---|---|
| [README](https://github.com/thoser666/Vivid#readme) | Features, Setup-Guides, FAQ, Roadmap |
| [Bedienungsanleitung (DE)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.md) | Schritt für Schritt durch die App |
| [User Guide (EN)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.en.md) | English version of the user guide |
| [Guide utilisateur (FR)](https://github.com/thoser666/Vivid/blob/develop/docs/user-guide.fr.md) | Version française du guide de l'utilisateur |

### 🤖 Features

| Link | Inhalt |
|---|---|
| [AI-Chat-Bot-Doku](https://github.com/thoser666/Vivid/blob/develop/docs/ai-chat-bot.md) | Bot-Modi, Owner-Befehle, Koexistenz |
| [Architektur-Übersicht](https://github.com/thoser666/Vivid/blob/develop/docs/architecture/overview.md) | Technische Modulstruktur |

### 🎓 Tutorials

| Link | Inhalt |
|---|---|
| [Ersten Stream starten](https://github.com/thoser666/Vivid/blob/develop/docs/tutorials/first-stream.md) | Schritt-für-Schritt zum ersten IRL-Stream |
| [Chat-Overlay einrichten](https://github.com/thoser666/Vivid/blob/develop/docs/tutorials/chat-overlay-setup.md) | Twitch-Chat in der Vorschau anzeigen |
| [OBS-Integration](https://github.com/thoser666/Vivid/blob/develop/docs/tutorials/obs-integration.md) | OBS Studio aus Vivid steuern |

### 🔧 Hilfe

| Link | Inhalt |
|---|---|
| [FAQ: Häufige Probleme](https://github.com/thoser666/Vivid/blob/develop/docs/faq/common-issues.md) | Antworten auf häufige Fragen |
| [Troubleshooting: Stream-Probleme](https://github.com/thoser666/Vivid/blob/develop/docs/troubleshooting/stream-issues.md) | Stream-Probleme lösen |
| [Datenschutzerklärung](https://thoser666.github.io/Vivid/privacy/) | Privacy Policy (Play-Store-Pflicht) |
| [Issue-Tracker](https://github.com/thoser666/Vivid/issues) | Bugs melden, Features vorschlagen |

---

*Vivid ist Open Source — [Quellcode auf GitHub](https://github.com/thoser666/Vivid).*
