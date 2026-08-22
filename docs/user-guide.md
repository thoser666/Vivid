# 📖 Vivid — Bedienungsanleitung

> **Kurzversion:** Stream-URL + Key eintragen → **Go Live** tippen → fertig.
> Diese Anleitung geht jeden Schritt im Detail durch.

Vivid ist eine IRL-Streaming-App für Android (RTMP/SRT/RTMPS), die Chat-Overlay,
KI-Bot und Kamera-Steuerung in einer App vereint — inspiriert von [Moblin](https://github.com/eerimoq/moblin).

- 📥 **Installieren:** [Releases-Seite](../../releases) oder [Obtainium](#1-installation)
- 🤖 **Bot-Doku:** [docs/ai-chat-bot.md](ai-chat-bot.md)
- 📋 **Roadmap:** [PARITY.md](../PARITY.md) · [RELEASE.md](../RELEASE.md)
- ❓ **Probleme:** [FAQ in der README](../README.md#-faq--häufige-probleme)

---

## Inhaltsverzeichnis

1. [Installation](#1-installation)
2. [Erststart & Berechtigungen](#2-erststart--berechtigungen)
3. [Stream konfigurieren](#3-stream-konfigurieren)
4. [Go Live](#4-go-live)
5. [Kamera-Steuerung (Tap-to-Focus, Zoom, Stabilisierung, Taschenlampe)](#5-kamera-steuerung)
6. [Chat-Overlay](#6-chat-overlay)
7. [KI-Chat-Bot](#7-ki-chat-bot)
8. [OBS Studio steuern](#8-obs-studio-steuern)
9. [Web-Remote-Control (Stream per Browser steuern)](#9-web-remote-control)
10. [Einstellungen — Die sechs Kategorien](#10-einstellungen--die-sechs-kategorien)
11. [Updates](#11-updates)

---

## 1. Installation

> Vivid ist noch nicht im Play Store. APKs werden als GitHub-Releases veröffentlicht.

1. Öffne die [**Releases-Seite**](../../releases)
2. Lade **„Latest"** (stabil) oder ein **Nightly/Alpha** (Prerelease) herunter — die Datei heißt `app-release.apk`
3. Öffne die APK im Dateimanager → Android fragt nach **„Unbekannte Quellen"** → erlauben
4. **Installieren** — fertig

**Alternativ mit Obtainium (automatische Updates):**
Siehe [README → Automatic updates (Obtainium)](../README.md#-automatic-updates-obtainium).

> 🔒 Alle offiziellen APKs sind mit demselben Release-Key signiert — Updates installieren nahtlos übereinander.

---

## 2. Erststart & Berechtigungen

Beim ersten Öffnen zeigt Vivid den **Streaming-Screen** (Live-Vorschau). Bevor du zum ersten Mal **Go Live** gehst, braucht die App drei Berechtigungen:

| Berechtigung | Wofür | Wann gefragt |
|---|---|---|
| **Kamera** | Live-Bild vom Gerät streamen | Beim ersten **Go Live** |
| **Mikrofon** | Audio/Commentary aufnehmen | Beim ersten **Go Live** |
| **Benachrichtigungen** | Foreground-Service (Stream läuft weiter im Hintergrund) | Beim ersten **Go Live** (ab Android 13) |

> **Hintergrund-Streaming:** Der Stream läuft weiter, wenn du die App verlässt (Home-Taste),
> den Bildschirm ausschaltest oder die App aus der Übersicht wischst — die Vorschau
> kommt beim erneuten Öffnen automatisch zurück. Eine persistente Notification mit
> **Stop**-Aktion zeigt den Stream-Status.

---

## 3. Stream konfigurieren

Öffne die **Einstellungen** (⚙️ oben rechts im Streaming-Screen) → **„Streaming & OBS"**.

### 3.1 Plattform-Vorlage wählen

Vivid bietet Vorlagen für die gängigsten Plattformen:

| Vorlage | Server-URL | Was du brauchst |
|---|---|---|
| **Twitch** | `rtmp://live.twitch.tv/live/` | Stream-Key (aus [Creator Dashboard](https://dashboard.twitch.tv/) → Settings → Stream) |
| **YouTube** | *[aus YouTube Studio]* | Stream-URL + Stream-Key (aus [YouTube Studio](https://studio.youtube.com/) → Go Live → Stream) |
| **Kick** | `rtmp://ingest.kick.com/live/` | Stream-Key (aus [Kick Dashboard](https://kick.com/dashboard) → Settings → Stream Settings) |
| **Benutzerdefiniert** | *(leer — du trägst die URL ein)* | RTMP(S)/SRT-URL + Stream-Key (z. B. [Owncast](https://owncast.online), Restream.io, etc.) |

### 3.2 Felder ausfüllen

1. **Plattform-Vorlage** antippen → die URL wird vorausgefüllt
2. **Stream-URL** ggf. anpassen (z. B. `rtmps://` für TLS — das **„TLS"**-Toggle aktiviert Verschlüsselung)
3. **Stream-Key** eintragen
4. **Fertig** — die Einstellungen werden gespeichert

### 3.3 Multi-Streaming (zwei Ziele parallel)

Optional kannst du einen **zweiten RTMP-Zielort** konfigurieren:
- In **„Multi-Streaming (optional)"** die sekundäre URL + Key eintragen
- Beim **Go Live** starten beide Ziele gleichzeitig
- Jedes Ziel zeigt seinen eigenen Status (bereit / verbinde… / sendet live / fehlgeschlagen)
- Fällt ein Ziel aus, stoppt nur dieses — das andere streamt weiter

> Ideal für gleichzeitiges Streamen zu Twitch + YouTube.

### 3.4 Go-Live-Self-Check

Vor dem Start prüft Vivid die Konfiguration und zeigt klare Meldungen:
- ❌ **Fehler** (blockiert den Start): Keine URL, ungültiges Protokoll, kein Host
- ⚠️ **Warnung** (blockiert nicht): Stream-Key fehlt (manche Plattformen brauchen keinen)

---

## 4. Go Live

1. Zurück zum **Streaming-Screen** (← Pfeil oben links, falls du in den Einstellungen bist)
2. **Go Live**-Button tippen (unten Mitte)
3. Vivid verbindet sich — Status wechselt zu **„Bereite vor…"** → **„sendet live"**
4. Die Status-Anzeige zeigt jeden Zielort einzeln (bei Multi-Streaming)

### Stream beenden

- **Stop**-Button im Streaming-Screen, **oder**
- **Stop** in der Notification-Leiste

> Der Stream stoppt sauber — der KI-Bot (falls aktiv) fährt ebenfalls automatisch herunter.

---

## 5. Kamera-Steuerung

Direkt auf der **Streaming-Vorschau** (auch **vor** dem Go Live benutzbar):

| Aktion | Wie |
|---|---|
| **Tap-to-Focus** | Einzelner Tipp auf die Vorschau → fokussiert auf diese Stelle |
| **Pinch-Zoom** | Zwei Finger aufziehen/zusammenziehen → Zoom (geclampt auf den Kamera-Bereich) |
| **Zoom zurücksetzen** | Doppeltipp auf die Vorschau → Zoom = 1.0 |

Oben rechts im Streaming-Screen gibt es drei Buttons:

| Button | Funktion |
|---|---|
| **🔦 Taschenlampe** | Schaltet die Taschenlampe (Torch/Lantern) an/aus — auch als `!torch`-Bot-Befehl vom Streamer steuerbar |
| **Stabilisierung** | Optische (OIS) bevorzugt, sonst digitale (EIS) Bildstabilisierung an/aus |
| **Fokus-Lock** | Auto-Focus ⇄ Unendlich-Lock (gegen Fokus-Hunting bei Regen/Schmutz auf der Linse — ideal für Drive-/Train-Streams) |

> Die Buttons wirken auf die **echte RootEncoder-Kamera** — nicht nur auf die Vorschau.

---

## 6. Chat-Overlay

Das Twitch-Chat-Overlay zeigt den Chat deines Kanals **über der Streaming-Vorschau**.

### 6.1 Aktivieren

1. **Einstellungen** → **„Overlays & Widgets"**
2. **Chat-Overlay** einschalten
3. **Chat-Kanal** eintragen (dein Twitch-Kanalname, z. B. `thoser666`)

### 6.2 Was du brauchst

Das Overlay liest den Chat über **Twitch EventSub** (nicht IRC). Dafür braucht es die
**Bot-Zugangsdaten** (siehe [KI-Chat-Bot](#7-ki-chat-bot) → Bot-Login + OAuth-Token):
- Der Bot-Token muss den Scope `user:read:chat` enthalten
- Für Event-Alerts (Follows/Subs): Bot muss **Moderator** im Kanal sein (`moderator:read:followers`)
  und `channel:read:subscriptions` besitzen

### 6.3 Was das Overlay zeigt

- Die letzten Chat-Nachrichten unten links (mit Twitch-Farbe pro User)
- **Twitch-Badges** (Broadcaster/Mod/Sub) vor dem Username als CDN-Bilder
- **Inline-Emotes** (Twitch-Emotes als Bilder direkt im Text, via Coil)
- **Event-Alerts** als farbige Zeilen über den Nachrichten:
  - 🟢 Follow · 🟣 Sub · 🔵 Gift-Sub · 🟦 Resub · 🟠 Raid
  - Automatisch ausgeblendet nach 10 Sekunden

### 6.4 Test-Alert (vor dem Go Live)

Um das Overlay vor dem Stream zu testen:
- **`!testalert follow`** (oder `sub`, `gift`, `resub`, `raid`) im Chat tippen (Owner-only)
- Der Alert erscheint sofort im Overlay

---

## 7. KI-Chat-Bot

Der Bot verbindet sich automatisch beim **Go Live** mit dem Twitch-Chat und fährt bei
**Streamende** herunter. Die vollständige Anleitung steht in [docs/ai-chat-bot.md](ai-chat-bot.md).

### 7.1 Modus wählen

| Modus | Beschreibung |
|---|---|
| **Bot (wie Moblin)** | Deterministische `!`-Befehle (`!help`, `!uptime`, `!tts`, `!bot`) — **kein LLM nötig** |
| **KI autonom** | Die KI entscheidet selbst, ob und wie sie antwortet (inkl. bewusstem Schweigen) |

### 7.2 Einrichtung

**Einstellungen** → **„Chat-Bot & KI"**:

1. **Bot-Login** (Twitch-Username des Bots) eintragen
2. **Twitch-OAuth-Token** (Scope `user:read:chat` + `user:write:chat`; für Moderation `moderator:manage:banned_users`) — als Passwortfeld mit Sichtbarkeits-Toggle
3. **Twitch-App-Client-ID** (für EventSub + Helix)
4. Für den KI-Modus: **LLM-Basis-URL**, **API-Key** und **Modell** eintragen (OpenAI-kompatibel → OpenAI, Gemini, Groq, DeepSeek, Ollama im LAN)

### 7.3 Begrenzungen (Kosten-Schutz)

Drei einstellbare Limits (alle `0` = aus):
- **Per-Viewer-Cooldown** (Default 60 s) — ein Viewer bekommt nicht öfter als X Sekunden eine Antwort
- **Per-Viewer-Cap pro Stream** — max. Antworten pro Viewer pro Stream
- **Stunden-Budget** — max. Antworten pro Stunde (Kosten-Deckel)

**Schnellstart-Presets:** Locker (30/0/0) · Balanced (60/10/120) · Streng (180/5/60) — ein Tipper füllt alle drei Felder. Die zuletzt gewählte Stufe wird gespeichert und beim App-Start wiederhergestellt.

**Live-Verbrauch** im Settings-Screen: Antworten/Stunde (vs. Budget), Stream-Total, Top-Viewer.

### 7.4 Owner-Befehle (nur der Streamer)

| Befehl | Wirkung |
|---|---|
| `!start` / `!go-live` | Stream starten |
| `!stop` / `!end` | Stream stoppen |
| `!diag` / `!status` | Diagnose-Lauf: Stream-Status, OBS, 11 Konfigurations-Checks + KI-Empfehlung |
| `!ask <frage>` | Frage an die exklusive Owner-KI (Fallback: Viewer-KI, sonst deterministisch) |
| `!testalert <type>` | Test-Alert für das Overlay (`follow`/`sub`/`gift`/`resub`/`raid`) |
| `!torch` | Taschenlampe umschalten (Alias: `!lantern`/`!flashlight`) |
| `!ban <user>` | Viewer verbannen |
| `!timeout <user> <min?>` | Viewer timeouten (Default 5 Min) |
| `!delete <count?>` | Letzte Nachrichten löschen |

> Owner = Broadcaster-Badge **oder** Allow-List (`chat_bot_owner_logins` in den Settings).
> Antworten gehen per **Whisper** (privat), wenn der Toggle aktiv ist.
> Viewer-Befehle: `!help`, `!uptime`, `!tts`, `!song`, `!next`, `!pause`, `!bot`.

### 7.5 Koexistenz mit anderen Bots

Läuft ein anderer Bot im selben Kanal (z. B. Rivulet):
- **`chat_bot_ignore_bots`**: Andere Bot-Logins komplett ignorieren
- **Command-Scope**: `ALL` (jeder Befehl), `MENTION` (nur `@vividbot !help`), `PREFIX` (nur `!v!help` mit Präfix `v`)
- Fremde Befehle außerhalb des Scopes → kein „Unbekannter Befehl"-Echo (der andere Bot ungestört)

---

## 8. OBS Studio steuern

Vivid kann OBS Studio über **WebSocket** steuern (Szenen wechseln, Recording/Stream starten/stoppen).

### Einrichtung

1. **In OBS:** Extras → **WebSocket-Server-Einstellungen** → *„WebSocket-Server aktivieren"* anhaken (Port `4455`)
2. Optional **Passwort** setzen
3. **IP des OBS-Rechners** ermitteln (Windows: `ipconfig`, Mac/Linux: `ip addr`) — Handy und PC im **selben WLAN**
4. **In Vivid:** Einstellungen → **Streaming & OBS** → OBS-Bereich:
   - Host = IP des PCs
   - Port = `4455`
   - Passwort (falls gesetzt)
   - **TLS-Toggle** (`wss://` für Remote, `ws://` für LAN)
5. **OBS-Steuerung** öffnen (Icon oben links im Streaming-Screen)

> Probleme? Siehe [OBS-FAQ](../README.md#-faq--häufige-probleme).

### OBS per QR-Code importieren

Falls dein OBS einen QR-Code anzeigt (Format `obsws://host:port/pw`), kannst du ihn im
Einstellungen-Screen importieren — Host, Port und Passwort werden automatisch übernommen.

---

## 9. Web-Remote-Control

Steuere den Stream von jedem Browser im selben WLAN:

1. **Einstellungen** → **Remote & Datenschutz** → **Web-Remote-Control**
2. **Token** notieren (wird einmalig erzeugt)
3. **Handy-IP** ermitteln (Android: Einstellungen → WLAN → Verbundenes Netz → Details)
4. **Status abfragen** (ohne Token):
   ```
   curl http://<handy-ip>:8080/status
   ```
5. **Stream starten/stoppen** (mit Token):
   ```
   curl -X POST http://<handy-ip>:8080/start -H "Authorization: Bearer <token>"
   curl -X POST http://<handy-ip>:8080/stop  -H "Authorization: Bearer <token>"
   ```

> 🔒 Der Server läuft nur, solange die App geöffnet ist. Aktionen brauchen das Token.
> Android 17: Falls `/status` nicht erreichbar ist → „LAN-Zugriff für Remote-Control erlauben" in den Settings.

---

## 10. Einstellungen — Die sechs Kategorien

Der Einstellungen-Screen ist in sechs Kategorien gegliedert (wie Moblin):

| Kategorie | Inhalt |
|---|---|
| 🎬 **Streaming & OBS** | Stream-URL/-Key, Plattform-Vorlagen (Twitch/YouTube/Kick/Custom), Multi-Streaming, OBS-Verbindung (Host/Port/Passwort/TLS/QR-Import) |
| 🎨 **Darstellung** | Design-Modus (System/Hell/Dunkel/AMOLED) + Akzentfarbe (6 kuratierte Farben, Vivid-Grün als Standard) |
| 🧩 **Overlays & Widgets** | Twitch-Chat-Overlay (Kanal + Toggle), Text-/Info-Widget (Zeit/GPS/Geschwindigkeit/Höhenmeter — je mit Toggle + Runtime-Permission) |
| 💬 **Chat-Bot & KI** | Betriebsmodus, Bot-Konto (Login/Token/Client-ID), LLM-Endpunkt/Key/Modell/Prompt, Cooldown, Mentions-only, Rate-Limit, Limits + Presets, Owner-Zugriff (Allow-List + Owner-KI), Media-Befehle, Benachrichtigungszugriff |
| 🔒 **Remote & Datenschutz** | Web-Remote-Control (Token + LAN-Zugriff), Sentry-Fehlerberichte (Opt-out-Toggle) |
| ℹ️ **Über & Updates** | Version, Update-Badge, manuelle Update-Suche (GitHub Releases), Release-Notes |

---

## 11. Updates

### Automatisch (Obtainium)

Siehe [README → Automatic updates (Obtainium)](../README.md#-automatic-updates-obtainium).

### Manuell

1. **Einstellungen** → **Über & Updates**
2. Das **Update-Badge** erscheint automatisch (1-h-Cache), falls eine neuere Version existiert
3. **„Nach Updates suchen"** tippen → zeigt die neueste GitHub-Release inkl. Release-Notes
4. APK von der [Releases-Seite](../../releases) herunterladen und installieren

> Der Check schlägt niemals ein Downgrade vor (Nightly → Nightly/Alpha/Beta/Stable).

---

## Quick-Reference: Alle Bot-Befehle

| Befehl | Wer? | Wirkung |
|---|---|---|
| `!help` / `!commands` / `!hilfe` | Alle | Verfügbare Befehle anzeigen |
| `!uptime` | Alle | Stream-Dauer anzeigen |
| `!tts` | Alle | Chat-Vorlesen (Text-to-Speech) an/aus |
| `!bot` | Alle | Bot-Info anzeigen |
| `!song` / `!nowplaying` | Alle | Aktueller Titel (Media-Player) |
| `!next` / `!skip` | Alle | Nächster Titel |
| `!pause` | Alle | Wiedergabe pausieren |
| `!play` | Alle | Wiedergabe fortsetzen |
| `!prev` / `!previous` | Alle | Vorheriger Titel |
| `!start` / `!go-live` | Owner | Stream starten |
| `!stop` / `!end` | Owner | Stream stoppen |
| `!diag` / `!status` | Owner | Diagnose-Lauf |
| `!ask <frage>` | Owner | Frage an die Owner-KI |
| `!testalert <type>` | Owner | Test-Alert für das Overlay |
| `!torch` | Owner | Taschenlampe umschalten |
| `!ban <user>` | Owner | Viewer verbannen |
| `!timeout <user> <min?>` | Owner | Viewer timeouten |
| `!delete <count?>` | Owner | Nachrichten löschen |

> **PREFIX-Scope** (Koexistenz): `!v!help`, `!v!uptime`, … (Präfix `v` = Standard).
> Befehle sind case-insensitive und können mitten in der Nachricht stehen (`@vividbot !help`).
