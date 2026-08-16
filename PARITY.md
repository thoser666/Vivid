# 🎯 Moblin Feature Parity Tracker

Das **Endziel** von Vivid ist es, **zumindest funktionsgleich zu [Moblin](https://github.com/eerimoq/moblin)** zu sein — jedes Moblin-Feature soll in Vivid, adaptiert an die Android-Plattform, funktionieren.

Dieses Dokument ist die Arbeitsliste hinter dem [Parity-Status in der README](README.md#-parity-status): Es verfolgt **jedes** Moblin-Feature (35 anwendbare) mit Implementierungsstand, verantwortlichem Modul und offenen Tasks.

## Status-Legende

| Symbol | Bedeutung |
|--------|-----------|
| ✅ | Implementiert & getestet |
| 🚧 | In Arbeit (Teile vorhanden) |
| 📋 | Geplant (Roadmap) |
| — | Nicht zutreffend auf Android |

> **Stand:** 2026-08-16 · Aktualisierung: **17/17 ✅ erreicht** — Row 80 „Chat-Bot: Media-Player-Steuerung“ ist als 17. Moblin-Paritäts-Punkt ✅ abgeschlossen (16 → 17); damit ist die **≥17-✅-Bedingung des Beta-Gates erfüllt**. Beta-Gate **2/3 erreicht**: offen sind nur noch das **erste Widget** (Text-/Info-Widgets) + die **Chat-Formalisierung** (Twitch-IRC + Overlay + KI-Bot laufen, Scope auf Twitch oder Event-Alerts) — Referenzstand: Moblin **33.12.0**
>
> **Pflege:** Nach jedem Feature-Commit den Status in der jeweiligen Zeile aktualisieren und das Datum oben anpassen.
>
> 🚦 **Nächster Meilenstein (Beta-Gate):** ≥17 ✅ (**erreicht: 17**) + Chat-✅ + ≥1 Widget — offen: erstes Widget (Text-/Info-Widgets) + Chat-Formalisierung (Twitch läuft). Siehe [RELEASE.md](RELEASE.md#beta--nächster-meilenstein) für alle Stage Gates.

---

## 📊 Übersicht

| Kategorie | ✅ | 🚧 | 📋 | Summe |
|-----------|----|----|----|-------|
| Streaming & Protokolle | 5 | 0 | 4 | 9 |
| Netzwerk-Bonding | 0 | 0 | 1 | 1 |
| OBS-Steuerung | 3 | 0 | 1 | 4 |
| Chat & Moderation | 1 | 2 | 1 | 4 |
| Overlays & Widgets | 0 | 1 | 5 | 6 |
| Kamera & Video | 2 | 0 | 4 | 6 |
| Audio | 0 | 0 | 3 | 3 |
| Remote & Companion | 1 | 0 | 2 | 3 |
| Plattform & Grundlagen | 5 | 1 | 0 | 6 |
| Zusatz-Features (über Parität) | 1 | 0 | 1 | 2 |
| **Gesamt** | **18** | **4** | **22** | **44**† |

† Inkl. 1 n/a-Zeile (Apple-Watch-Companion) und 2 Zusatz-Features über die Moblin-Parität hinaus; anwendbare Moblin-Features: **42**.

---

## 📡 Streaming & Protokolle

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| RTMP | ✅ | `feature-streaming`, `core` | Single-Ingest-Stream über konfigurierte URL/Key |
| SRT | ✅ | `feature-streaming` | SRT über `RootEncoder` 2.7.5; Stream-URL aus Settings |
| RTMPS (TLS) | ✅ | `feature-streaming` | TLS-Ingest via RootEncoder 2.7.5 verifiziert (Bytecode: `tlsEnabled = scheme.endsWith("s")`, Port 443 default, TLSv1.1/1.2); `buildStreamUrl` schreibt `rtmp://` → `rtmps://` und normalisiert Port 1935 → 443; Beweis-Test am echten UrlParser (`RootEncoderRtmpsSupportTest`) |
| Hintergrund-Streaming (Foreground-Service) | ✅ | `app` (`StreamingService`), `feature-streaming` (`StreamingServiceLauncher`, `StreamingViewModel`) | Stream läuft weiter, wenn die App im Hintergrund ist (Home-Taste/Bildschirm aus): Foreground-Service mit `microphone|camera`-Type, persistenter Notification (Status-Aktualisierung, Stop-Aktion), PARTIAL_WAKE_LOCK; Runtime-Permissions (Kamera/Mikro/Notif.) werden beim Go-Live angefordert. **GL-freier Encoderpfad:** `RtmpCamera2` wird über den **Context-Konstruktor** erzeugt (interne `GlStreamInterface`-Pipeline mit eigenem EGL-Context + ForceRenderer, verifiziert an RootEncoder-2.7.5-Bytecode + Maintainer-Doku); die Kamera-Vorschau hängt nur als Surface an (`attachPreview`/`detachPreview`). Dadurch läuft der Stream auch bei **Activity-Zerstörung** (Recents-Wischen, Rotation) weiter — die Vorschau kommt beim nächsten Öffnen automatisch zurück |
| Multi-Streaming (RTMP(S) an mehrere Ziele) | ✅ | `feature-streaming` (`StreamingEngine`), `core`/`domain` (Settings), `app` (`StreamingService`) | Bis zu **2 parallele RTMP(S)-Ziele** (primär + optional sekundär) über RootEncoder `MultiCamera2`: `CameraFactory.create(List<ConnectChecker>)` legt eine Kamera mit je einem ConnectChecker pro Ziel an; `targetStates`-`StateFlow` zeigt den Status je Ziel (bereit/verbinde…/sendet live/fehlgeschlagen); **ein Fehlerziel stoppt nur sein eigenes Ziel** (`stopStream(RTMP, index)`), andere senden weiter; sekundäre URL/Key/TLS in den Settings („Multi-Streaming (optional)“), Validator + Service-Plumbing (`EXTRA_STREAM_URLS`-Liste); Unit-Tests (Engine, Validator, ViewModel, Repository). Architektur auf N>2 erweiterbar (`MAX_STREAM_TARGETS` in der Engine) |
| RIST | 📋 | `core` | Stack-Entscheidung: `librist`-JNI oder SRT-basiert |
| WHIP (WebRTC) | 📋 | `core` | WebRTC-Stack (z. B. `io.github.webrtc-sdk`); WHIP-Client + Sender |
| RTMP-Pull / Ingest (Server-Modus) | 📋 | `core` | Community-Request [#407](https://github.com/eerimoq/moblin/issues/407); Moblin bietet Ingests (RTMP, SRT(LA), RIST, RTSP, WHIP) — Pull-Pfad statt nur Push |
| 4K/60fps, H.264/H.265 (HEVC) | 📋 | `feature-streaming` | Encoder-Presets (CameraX/MediaCodec); HEVC-Fallback-Kette |

## 🔗 Netzwerk-Bonding

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| SRTLA Multi-Network Bonding (WiFi + Mobilfunk, Failover, adaptive Bitrate) | 📋 | `core` | SRTLA-Protokoll (Link-Split/Reassembly) portieren; Statistik pro Verbindung; Gewichtung |

## 🎛️ OBS-Steuerung

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| OBS WebSocket-Connect (ws:// LAN / wss:// Remote, Passwort-Auth) | ✅ | `core` (`OBSWebSocketClient`), `feature-obs-control` | Scheme über `obsUseTls`-Setting; Auth-Handshake getestet. **Offene Tasks (Troubleshooting):** Verbindungsfehler-Ursache im UI anzeigen (OBS nicht erreichbar, Port/Firewall, Netzwerk); Auth-Fehler (falsches/leeres Passwort) als eigener UI-Zustand + Passwort-Reset-Hinweis; ws/wss-Fehlkonfiguration gezielt melden — Doku: [README-FAQ](README.md#-faq--häufige-probleme) |
| Szenen wechseln, Recording/Stream-Start/-Stop | ✅ | `feature-obs-control` | Request-Batch + Typen vorhanden |
| Snapshot / Audio-Levels / Audio-Sync auslesen | 📋 | `feature-obs-control` | Weitere Request-Typen ergänzen |
| OBS-Konfiguration per QR-Code importieren | ✅ | `core` (`ObsQrCodeParser`), `feature-obs-control` | Parser fuer alle OBS-Formate (`obsws://host:port/pw` percent-decoded, `obswebsocket://`, `obswebsocket|[host]:[port]|[pw]`); Import-Feld im Settings-Screen uebernimmt Host/Port/Passwort; Unit-Tests (`ObsQrCodeParserTest`, `SettingsViewModelTest`). **Offen:** Kamera-Scan direkt im UI |

## 💬 Chat & Moderation

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Plattform-Chat (Twitch, Kick, YouTube, SOOP) | 🚧 | `feature-chat` | **Twitch-IRC-Client fertig** (`TwitchChatClient`, `SocketIrcConnection`): anonym (justinfan) + TLS 6697, CAP (tags/commands/membership), JOIN/PING-PONG, IRCv3-Tags-Parser (display-name, color, badges, emotes, tmi-sent-ts, mod/sub), `state`-StateFlow + `messages`-Flow, Auto-Reconnect mit Backoff, Hilt-DI (`@ChatScope`); 14 Unit-Tests. **UI/Overlay ✓** (`ChatOverlay`-Composable + `ChatOverlayViewModel`, siehe Overlays & Widgets). **Offen:** Kick (WebSocket), YouTube (innertube), SOOP; OAuth-Login für Senden/Moderation |
| Emotes (BTTV, FFZ, 7TV) | 📋 | `feature-chat` | Emote-API-Clients + Rendering |
| Moderation (Ban, Timeout, Delete), Chat-Bot, TTS | 🚧 | `feature-chat` | **Chat-Bot-Kommandos implementiert** (`!help`/`!uptime`/`!tts`/`!bot` via `BotCommandProcessor`) inkl. **Chat-TTS-Toggle** (`!tts`, `ChatTtsController` + `AndroidTtsSpeaker`); **offen:** ModActions (Ban/Timeout/Delete) + Bot-Framework-Erweiterungen |
| Chat-Bot: Media-Player-Steuerung (generisch via MediaSession, z. B. Apple Music/Spotify) | ✅ | `feature-chat` (`media`) | Android-Adaption der Apple-Music-Steuerung aus Moblin 33.12.0: `ChatMediaController` steuert den aktiven Media-Player über `MediaSessionManager.getActiveSessions` → `MediaController.TransportControls` (bevorzugt playing/paused/buffering-Session); Kommandos `!song`/`!nowplaying`, `!next`/`!skip`, `!pause`, `!play`, `!prev`/`!previous` (case-insensitive); **Voraussetzung:** Benachrichtigungszugriff via `MediaNotificationListener` (leerer Zugriffs-Marker, liest keine Benachrichtigungen) — ohne Zugriff antwortet der Bot mit einem Hinweis; Button „Benachrichtigungszugriff aktivieren“ im Settings-Screen |

## 🎨 Overlays & Widgets

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Chat-Overlay & Event-Alerts (Follow/Sub/Raid) | 🚧 | `feature-chat` (`ChatOverlay`), `feature-settings`, `feature-streaming` | **Chat-Overlay ✓:** zeigt den Twitch-Chat über der Streaming-Vorschau (anonym gelesen) — Kanal + Toggle in den Settings (`chat_channel`/`chat_overlay_enabled` in `SettingsRepository`), Start/Stop der Verbindung über `ChatOverlayViewModel` (Normalisierung auf Kleinbuchstaben, max. 50 Nachrichten, Status „Verbinde…“/„Chat: <kanal>“), `ChatOverlay`-Composable unten links im StreamingScreen, automatisches Ausblenden bei deaktiviertem Overlay; 8 VM-Tests + 3 Repository-Tests. **Offen:** Event-Alerts (Follow/Sub/Raid) mit Trigger-API; Badges/Emotes im Overlay |
| Text-/Info-Widgets (Zeit, Wetter, Geschwindigkeit, GPS, Höhenmeter) | 📋 | `feature-widgets` | Sensor-/Geo-Daten-Bindungen; Anstieg/Abstieg des Höhenmeters (**neu in Moblin 33.12.0**) |
| Text-Widget-Variablen (GPS-Koordinaten, `{road}`/Route) | 📋 | `feature-widgets` | Community-Feature-Requests [#360](https://github.com/eerimoq/moblin/issues/360) / [#384](https://github.com/eerimoq/moblin/issues/384); Variablen-System im Text-Widget erweitern |
| Karten-Widget | 📋 | `feature-widgets` | Karten-Provider wählen (Maps SDK / Tile-Overlay) |
| Browser-Widget (CSS + JS-API) | 📋 | `feature-widgets` | WebView-Layer + `postMessage`-Bridge |
| Scoreboards (Padel, Golf, Volleyball) | 📋 | `feature-widgets` | Datenmodelle + Renderer; **Open Task:** Golf-Scoreboard ([#326](https://github.com/eerimoq/moblin/issues/326)) — Spielernamen, Loch, Par, Punktestand, Auto-Total, horizontale/vertikale Layouts |

## 📹 Kamera & Video

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Tap-to-Focus, Pinch-Zoom, Stabilisierung | ✅ | `feature-streaming` (`StreamingEngine`, `StreamingPreviewGestures`, `ZoomCalculator`, `CameraStabilizationController`, `RootEncoderCameraControls`) | Komplett auf der Streaming-Kamera (RootEncoder, nicht CameraX): **Tap-to-Focus** (einzelner Tipp), **Pinch-Zoom** (`ScaleGestureDetector`, geclampt auf `getZoomRange()`) und **Zoom-Reset** (Doppeltipp) über `StreamingPreviewGestures` auf der Vorschau; **Video-Stabilisierung** als Toggle (OIS bevorzugt, sonst EIS) mit `stabilizationEnabled`-StateFlow; Engine-API `zoomBy`/`resetZoom`/`tapToFocus`/`toggleStabilization`; Unit-Tests (`ZoomCalculatorTest`, `CameraStabilizationControllerTest`, `RootEncoderCameraControlsTest`, `StreamingEngineTest`) |
| Fokus-Lock (∞) / Autofokus-Toggle | ✅ | `feature-streaming` (`StreamingEngine`, `CameraFocusController`) | Toggle Auto ⇄ Unendlich-Lock gegen Fokus-Hunting ([#377](https://github.com/eerimoq/moblin/issues/377), Drive-/Train-Streams): RootEncoder-API (`disableAutoFocus()` + `setFocusDistance(0)`) über `FocusableCamera`-Adapter; Zustand als `focusMode`-`StateFlow` in der Engine (auch vor dem Go-Live schaltbar); Toggle im StreamingScreen; Unit-Tests (`CameraFocusControllerTest`, `StreamingEngineTest`) |
| Color-Spaces (sRGB, P3, Log) + 3D-LUTs | 📋 | `feature-streaming` | Shader-Pipeline in `OpenGlView` ausbauen |
| Video-Effekte (Graustufen, Letterbox, Sepia, Rauschfilter) | 📋 | `feature-streaming` | OpenGL-Effektkette |
| Externes Zubehör (DJI Osmo, GoPro, Gimbal, UVC) | 📋 | `feature-streaming` | BLE/USB-Integrationen einzeln bewerten |
| Photo-Shoot-Quick-Button (periodisch hochauflösende Fotos) | 📋 | `feature-streaming` | Quick-Button-Aktion; ein sauberes Bild pro Sekunde pro Kamera, Ablage in der Galerie; **neu in Moblin 33.12.0** |

## 🎙️ Audio

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Mic-Verwaltung + Auto-Switch bei externem Mic | 📋 | `feature-streaming` | AudioCapture-Quellen steuern. **Offene Tasks (Troubleshooting):** `RECORD_AUDIO`-Berechtigung vor Stream-Start prüfen + klare Meldung; Mic-Exklusivität erkennen (Android gibt das Mikrofon nur an eine App gleichzeitig) und hinweisen; Bluetooth-Mikrofon-Routing behandeln — Doku: [README-FAQ](README.md#-faq--häufige-probleme) |
| Level-Meter, Muting, Sync-Offset | 📋 | `feature-streaming` | DSP/UI. **Offen:** Fehlgeschlagenes `prepareAudio()`/`prepareVideo()` („Failed to prepare audio/video“) im UI anzeigen statt nur internem State |
| Talk Back (Monitor-Feed) | 📋 | `feature-streaming` | Rückkanal-Routing |

## 🕹️ Remote & Companion

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Web-Remote-Control (LAN-Server, Status/Preview) | ✅ | `core` (`RemoteControlServer`), `feature-streaming` (`StreamingEngineStreamControl`), `app` (`VividApplication`) | Ktor-Server auf Port 8080 mit Token-Auth; `GET /status` (öffentlich), `POST /start|stop` (Bearer-Token aus Settings); Token wird in DataStore persistiert und in den Settings angezeigt. **Android 17 (API 37, targetSdk 37):** `ACCESS_LOCAL_NETWORK`-Runtime-Berechtigung deklariert + im Settings-Screen erfragbar (Server-Neustart nach Erteilung), damit die LAN-Verbindung weiter funktioniert. **Offen:** Video-Preview im Browser, Toggle zum Aktivieren/Deaktivieren, Talkback-Mic-Steuerung (Streamer-Seite; **neu in Moblin 33.12.0**). **Bewusst nicht geplant:** ein separates Begleitprojekt (wie `moblin-assistant`) für Fernzugriff über das Internet oder externe Chat-Bridges — Android kann Twitch/YouTube/Kick direkt in der App anbinden (Chat-Modul 📋), Remote bleibt aufs LAN beschränkt; ein Relay-Server wäre ein eigener Wunsch außerhalb der Moblin-Parität |
| Game-Controller-Support (Zoom, Szenen, Torch) | 📋 | `core` | Android-GameController-API |
| Deep Linking / Konfig-Import (`moblin://`, `.moblinSettings`) | 📋 | `app` | URL-Scheme + Import-Parser |
| Apple-Watch-Companion | — | — | Nicht zutreffend auf Android; Wear-OS-Pendant separat bewerten |

## 🧱 Plattform & Grundlagen

| Feature | Status | Modul | Offene Tasks / Notizen |
|---------|--------|-------|------------------------|
| Persistierte Stream-Einstellungen (URL/Key) | ✅ | `feature-streaming`, `feature-settings`, `core` (`SettingsRepository`) | `streamUrl`/`streamKey` via DataStore; in `startStream()` verdrahtet |
| Go-Live-Selbst-Check (Stream-Config-Validierung) | ✅ | `feature-streaming` (`StreamConfigValidator`), UI (`StreamingScreen`) | Validierung von URL/Scheme/Host/Key vor dem Start; ERROR blockiert, WARNING zeigt Hinweis; Re-Validierung bei Screen-Resume |
| Persistierte OBS-Einstellungen (Host, Port, Passwort, TLS) | ✅ | `core`, `feature-obs-control`, `feature-settings` | DataStore-Key `obs_use_tls` etc.; beide Settings-Screens |
| Auth-/API-Basis (Login, Register) | ✅ | `core` (`VividApi`), `domain` | Ktor-Client mit MockEngine-Tests |
| In-App-Version & Update-Check (About-Screen) | ✅ | `app` (`AboutScreen`), `feature-settings` (Update-Badge), `core` (`UpdateChecker`) | Zeigt installierte Version (versionName/versionCode) in Settings + About, Update-Badge direkt auf dem Settings-Screen, manueller Check in About; folgt den Cross-Track-Regeln aus RELEASE.md (kein Downgrade); Basis für den Obtainium-Update-Test |
| I18n (lokalisierte Strings) | 🚧 | `feature-*` | `strings.xml`-Grundgerüst vorhanden; Übersetzungen laufen über |

## 💡 Zusatz-Features (über Moblin-Parität hinaus)

| Feature | Status | Modul | Offene Tasks / Notizen |
|---------|--------|-------|------------------------|
| Oura-Ring-Gesundheitsdaten im Widget (Sleep, Readiness, HR/HRV) | 📋 | `core` (OAuth2-Client, Repository), `feature-widgets` | Anzeige als Text-/Info-Widget (z. B. Readiness-/Schlaf-Score, Ruhe-HF); **keine BLE-Schnittstelle** → nur Oura-Cloud-API (OAuth2, Browser-Flow), daher aggregierte/verzögerte Werte, kein Live-Puls; Rate-Limits beachten |
| KI-Chat-Bot (automatische Chat-Antworten via LLM, OpenAI-kompatibel) | ✅ | `feature-chat` (`ai`, `bot`, `twitch`), `feature-settings`, `app` (`StreamingService`) | Verbindet sich beim **Go-Live vollautomatisch** (Twitch-Chat mit OAuth-Handshake, `chat:read`+`chat:send`) und fährt bei **Streamende sauber herunter** — Lifecycle via `StreamingService` → `ChatBotController`; Deaktivieren in den Settings stoppt sofort. **Betriebsmodus-Switch** in den Settings („Bot (wie Moblin)“ ↔ „KI autonom“): COMMAND = deterministische `!`-Befehle (`!help`/`!commands`, `!uptime`, `!tts`, `!bot`) via `BotCommandProcessor`, **kein LLM nötig**; AUTONOMOUS = KI entscheidet selbst (inkl. bewusstem Schweigen via `[keine Antwort]`-Marker, Autonomie-Prompt). **Chat-TTS:** `!tts` schaltet das Vorlesen von Chat-Nachrichten um (`ChatTtsController` + `AndroidTtsSpeaker`, überspringt eigene Nachrichten + Befehle, Zustand überlebt Stream-Ende). Bausteine: `TwitchBotClient` (Senden + Lesen), `ChatBotEngine` (Modi, nur-Erwähnung-Modus, Cooldown, Rate-Limit/Min., 500-Zeichen-Limit, Prompt-History), `BotCommandProcessor`, `OpenAiCompatibleLlmClient` (`/v1/chat/completions` → OpenAI, Gemini, Groq, DeepSeek, Ollama im LAN); Bot-Settings in `SettingsRepository`/`AppSettings` (`chat_bot_mode`); 12 neue CommandProcessor-Tests + 8 neue Engine-Modi-Tests + 2 Repository-Tests erweitert — Gesamtsuite feature-chat (76) grün. **Settings-Screen komplett:** alle Bot-Felder im Abschnitt „Chat-Bot (KI)“ editierbar (Bot-Login, Twitch-OAuth-Token + LLM-API-Key als Passwortfelder mit Sichtbarkeits-Toggle, LLM-Basis-URL/Modell/System-Prompt, Cooldown, Mentions-only, Rate-Limit, Modus). **Koexistenz-Modus** (läuft neben dem Bot eines anderen Tools wie Rivulet): `chat_bot_ignore_bots` (andere Bot-Logins komplett ignorieren — keine Befehle, kein LLM-Input, kein TTS-Vorlesen), `chat_bot_command_scope` (ALL/MENTION/PREFIX), `chat_bot_command_prefix` (z. B. `v` → `!v!help`); fremde Befehle außerhalb des Scopes geben kein „Unbekannter Befehl“-Echo (None), damit der andere Bot ungestört antwortet. **Begrenzungen:** Per-Viewer-Cooldown (Default 60 s), Per-Viewer-Cap pro Stream und Stunden-Budget (Kosten-Deckel) — alle `0 = aus`, plattformneutral über `userId`, Moderatoren umgehen die Per-Viewer-Limits, Zähler reset bei Stream-Ende/-Start. **Schnellstart-Voreinstellungen** (Locker 30/0/0 · Balanced 60/10/120 · Streng 180/5/60) füllen die drei Felder mit einem Tipp, „Eigene“ bei abweichenden Werten; die **zuletzt gewählte Stufe wird persistiert** (`chat_bot_limit_preset`, Restore beim App-Start, manuelle Änderung → CUSTOM). **Live-Verbrauch** im Settings-Screen (`ChatBotEngine.usage`): Antworten/Stunde (vs. Budget), Stream-Total und Top-Viewer (Anzeigename + Anzahl). **Offen:** Twitch-OAuth-Browser-Flow. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |

---

## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-16 | — | **Chat-Bot: Preset-Persistenz** (Vivid-Zusatz): `chat_bot_limit_preset`-Key (AppSettings/SettingsRepository/`updateChatBotSettings`), ViewModel speichert die gewählte Stufe (`LOCKER`/`BALANCED`/`STRICT`), manuelle Limit-Änderungen setzen CUSTOM; `ChatBotLimitPreset.selection()` (gespeicherter Preset gewinnt, Fallback auf Wert-Matching) + fromName; 3 neue Preset-Tests + ViewModel-Tests (Wahl setzt Preset, manuelle Änderung → CUSTOM, Save persistiert) + Repository-Tests — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot: Live-Verbrauch im Settings-Screen** (Vivid-Zusatz): `ChatBotUsage`-Data-Klasse (repliesThisHour, hourlyBudget, totalRepliesThisStream, topViewers) + `usage`-StateFlow in der `ChatBotEngine` (aktualisiert bei jeder Antwort, Reset bei Start/Stop, Top-5-Viewer mit Anzeigename); feature-settings hängt jetzt an feature-chat, `SettingsViewModel.botUsage` reicht den Flow durch; Settings-Screen: „Live-Verbrauch“-Block unter den Begrenzungen (x/y-Budget-Anzeige); 3 neue Engine-Tests (Usage, Budget 0, Reset) + 1 ViewModel-Test (Forwarding) — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot: Schnellstart-Voreinstellungen** (Vivid-Zusatz): `ChatBotLimitPreset`-Enum (LOCKER 30/0/0 · BALANCED 60/10/120 · STRICT 180/5/60) + SegmentedButton-Leiste „Locker · Balanced · Streng · Eigene“ im Settings-Screen über den Limit-Feldern; Tipp füllt Cooldown/Cap/Budget (frei anpassbar danach), Matching-Logik zeigt „Eigene“, sobald die Werte keiner Stufe entsprechen; 2 neue ViewModel-/Preset-Tests — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot: Begrenzungen (Per-Viewer + Kosten)** (Vivid-Zusatz): `chat_bot_per_viewer_cooldown_seconds` (Default 60, 0 = aus), `chat_bot_per_viewer_max_replies` (Cap pro Stream), `chat_bot_max_replies_per_hour` (Kosten-Budget) in AppSettings/SettingsRepository/`updateChatBotSettings`; `ChatBotEngine` wendet sie plattformneutral über `userId` an (Cooldown-Map + Cap-Zähler, gleitendes 1-h-Fenster fürs Budget), Moderatoren umgehen Per-Viewer-Limits, Reset bei Start/Stop; Settings-Screen: Abschnitt „Begrenzungen“ (3 numerische Felder); 8 neue Engine-Tests (Cooldown blockiert/läuft ab/pro-Viewer-unabhängig/Mod-Bypass, Cap inkl. Reset, Stunden-Budget inkl. Ablauf) + Repository-/ViewModel-Tests erweitert — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot: Koexistenz-Modus** (Vivid-Zusatz, läuft neben dem Bot eines anderen Tools wie Rivulet ohne Kollisionen): `ChatBotCommandScope`-Enum (ALL = jeder `!`-Befehl · MENTION = nur @-Erwähnung · PREFIX = nur eigenes Präfix `!v!help`) in `AppSettings`/`SettingsRepository` (`chat_bot_command_scope`/`chat_bot_command_prefix`/`chat_bot_ignore_bots`), `BotCommandProcessor` beachtet den Scope (fremde Befehle → `None` statt „Unbekannter Befehl“), `ChatBotEngine` ignoriert Nachrichten aus der Ignore-Liste komplett, `ChatTtsController` liest ignorierte Bots nicht vor; Settings-Screen: Abschnitt „Koexistenz mit anderen Bots“ (Ignore-Feld, Scope-SegmentedButton, Präfix-Feld); 8 neue CommandProcessor-Scope-Tests + 3 Engine-Ignore-Tests + 2 Engine-Scope-Tests + 1 TTS-Ignore-Test + Repository-/ViewModel-Tests erweitert — Gesamtsuite + Lint (feature-chat/core/feature-settings/app) + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot: Media-Player-Steuerung** (Moblin-Parität Row 80 → ✅): `ChatMediaPlayer`-Interface + `ChatMediaController` (steuert den aktiven Media-Player über `MediaSessionManager.getActiveSessions` → `MediaController.TransportControls`, bevorzugt aktive Sessions), `MediaNotificationListener` (leerer NotificationListenerService als Zugriffs-Marker — Voraussetzung Benachrichtigungszugriff), Manifest-Service in der App, DI-Binding; Bot-Befehle `!song`/`!nowplaying`/`!np`, `!next`/`!skip`, `!pause`, `!play`, `!prev`/`!previous` im `BotCommandProcessor` + Engine-Handling mit Zugriffs-Hinweis („Kein Media-Zugriff …“); Settings-Screen: Hinweis + Button „Benachrichtigungszugriff aktivieren“ (öffnet `ACTION_NOTIFICATION_LISTENER_SETTINGS`); 6 neue CommandProcessor-Tests + 8 neue Engine-Media-Tests — Gesamtsuite + Lint (feature-chat/app/feature-settings) + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot-Settings-Screen vervollständigt:** alle restlichen Bot-Felder im Settings-Screen (Abschnitt „Chat-Bot (KI)“): Bot-Login, Twitch-OAuth-Token und LLM-API-Key als `SecretField` (Passwort-Maske + Sichtbarkeits-Toggle), LLM-API-Basis-URL, LLM-Modell, System-Prompt (mehrzeilig), Antwort-Cooldown (Sekunden, numerisch), Mentions-only-Switch, Max. Antworten/min (numerisch); neue ViewModel-Handler mit robustem Int/Long-Parsing (ungültig → 0 = aus/unbegrenzt), Persistenz über den bestehenden `updateChatBotSettings`-Aufruf; 3 neue ViewModel-Tests (komplette Konfiguration, UI-State aller Felder, ungültige Zahlen) — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-TTS-Befehl `!tts`** ergänzt (Moblin-Bot-Parität, Row 79): `ChatTtsSpeaker`-Interface + `AndroidTtsSpeaker` (systemeigene TextToSpeech-Engine, keine Runtime-Berechtigung), `ChatTtsController` (enabled-StateFlow, liest Viewer-Nachrichten aus dem Bot-Flow vor — überspringt eigene Bot-Nachrichten und `!`-Befehle, 200-Zeichen-Cap, Zustand überlebt Stream-Ende), `!tts`-Command im `BotCommandProcessor` (`ToggleTts`-Result), Engine-Toggle mit Chat-Bestätigung („TTS ist jetzt AN/AUS“, beide Modi), Wiring im `ChatBotController` + Hilt-Binding; 9 neue ChatTtsController-Tests + 3 CommandProcessor-Tests + 2 Engine-Toggle-Tests — Gesamtsuite feature-chat (76) grün, Lint + App-Compile grün |
| 2026-08-16 | — | **Chat-Bot-Modus-Switch („Bot wie Moblin“ ↔ „KI autonom“)** ergänzt: `ChatBotMode`-Enum (`COMMAND`/`AUTONOMOUS`) in `AppSettings`/`SettingsRepository` (`chat_bot_mode`-Key, Default AUTONOMOUS), `BotCommandProcessor` (deterministische `!`-Befehle `!help`/`!commands`, `!uptime`, `!bot` — COMMAND-Modus funktioniert **ohne LLM-Schlüssel**, `ChatBotConfig.isReady` mode-abhängig), `ChatBotEngine` (COMMAND: nur Befehle, keine LLM-Aufrufe; AUTONOMOUS: KI entscheidet selbst über jede freigegebene Nachricht, bewusstes Schweigen via `[keine Antwort]`-Marker, Autonomie-Prompt im System-Message; Cooldown + Rate-Limit gelten jetzt für **alle** Antworten inkl. Befehle; `!uptime` nutzt den Stream-Start-Zeitstempel aus `ChatBotController`), Modus-Switch (SegmentedButton) + Aktivieren-Toggle im Settings-Screen mit Persistenz über den Speichern-Button; 12 neue Unit-Tests (BotCommandProcessor) + 8 neue Engine-Modi-Tests + 2 Repository-Tests erweitert — Gesamtsuite feature-chat (62) grün, Lint + App-Compile grün. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |
| 2026-08-16 | — | **KI-Chat-Bot (Vivid-Zusatz-Feature) implementiert:** `OpenAiCompatibleLlmClient` (OpenAI-kompatibles `/v1/chat/completions`, Bearer-Auth, `LlmConfig` pro Aufruf), `TwitchBotClient` (authentifizierte Twitch-Verbindung, `PASS oauth:<token>`, `PRIVMSG`-Senden, PING→PONG, Reconnect), `ChatBotEngine` (nur-Erwähnung-Modus, Cooldown, Rate-Limit, 500-Zeichen-Limit, Prompt-History, ChatBotState), `ChatBotController` + `StreamingService`-Wiring (Auto-Connect bei Go-Live, sauberer Shutdown bei Streamende, Stop bei Deaktivierung); Bot-Settings in `SettingsRepository`/`AppSettings` (Kanal, Login, OAuth-Token, LLM-Endpunkt/-Key/-Modell, System-Prompt, Cooldown, Mentions-Only, Rate-Limit); 22 neue Unit-Tests, Lint + App-Compile grün. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |
| 2026-08-15 | — | **Beta-Gate-Analyse:** 16/17 ✅ erreicht; offene Gate-Bedingungen: ≥1 Widget (`feature-widgets` ist noch Platzhalter) + Chat-✅ formal (Twitch-IRC + Overlay laufen, 25 Tests). Empfehlung: erstes Widget (Text-/Info-Widgets) als 17. ✅ — Plan für den ersten Beta-Build in [RELEASE.md](RELEASE.md#-erster-beta-build-plan) |
| 2026-08-14 | — | **Plattform-Chat (Twitch) begonnen:** Data-Layer des `feature-chat`-Moduls implementiert — `TwitchChatClient` (anonym/justinfan, TLS 6697, CAP tags/commands/membership, JOIN, PING→PONG, Auto-Reconnect mit Backoff, `state`-StateFlow + `messages`-Flow), `SocketIrcConnection` (Socket/TLS mit `IrcConnectionFactory` für Tests), IRCv3-Tags-Parser (`IrcMessageParser`, inkl. Escaping) und `ChatMessage`-Modell; Hilt-DI (`@ChatScope`); 14 Unit-Tests grün, Lint + App-Compile grün. Nächste Schritte: Kick/YouTube-Adapter, OAuth-Login, UI/Overlay |
| 2026-08-14 | — | **Chat-Overlay (Twitch) fertig:** Kanal + Overlay-Toggle in den Settings (`chat_channel`/`chat_overlay_enabled` in `SettingsRepository`/`AppSettings`, gespeichert über `updateChatSettings`, Sektion „Chat-Overlay“ im Settings-Screen); `ChatOverlayViewModel` (startet/stoppt den `TwitchChatClient` anhand der Settings, normalisiert den Kanal, begrenzt auf 50 Nachrichten, leert bei Kanal-/Statuswechsel, reicht den Verbindungsstatus durch) + `ChatOverlay`-Composable (transparente Box unten links über der Vorschau, zeigt die letzten 6 Nachrichten mit Username-Farbe, blendet sich bei deaktiviertem Overlay aus); Overlay im StreamingScreen eingehängt (feature-streaming hängt jetzt an feature-chat); 3 Repository-Tests + 8 VM-Tests grün, Lint (warningsAsErrors) + App-Compile grün. **Offen:** Event-Alerts (Follow/Sub/Raid), Badges/Emotes, Kick/YouTube/SOOP, OAuth |
| 2026-08-14 | — | **RootEncoder auf 2.7.5 aktualisiert** (von 2.6.4): RTMP-Fixes (`onFail` bei fehlgeschlagenem Publish, Crash-Fix bei ungültiger URL, Handshake-Flags), SRT-Ack/Nak/Handshake-Fixes, Ktor-TLS-Fehlerbehandlung + `setTlsHostVerification`, „Java sockets by default"; API kompatibel verifiziert (Context-Konstruktoren `RtmpCamera2`/`MultiCamera2`, `Camera2Base`-Camera-Controls, RTMPS-Parsing via `RootEncoderRtmpsSupportTest`) — alle Tests grün |
| 2026-08-14 | — | **Tap-to-Focus, Pinch-Zoom und Video-Stabilisierung** für die Streaming-Kamera implementiert: `CameraControls`-Vertrag + `RootEncoderCameraControls` (Adapter über RootEncoder `Camera2Base`, wandelt `android.util.Range` → `ZoomRange`), `StreamingPreviewGestures` (Tipp → Tap-to-Focus, Doppeltipp → Zoom-Reset, Pinch → Zoom), `ZoomCalculator` (Clamping auf den Kamera-Zoombereich), `CameraStabilizationController` (OIS bevorzugt, sonst EIS) mit `stabilizationEnabled`-StateFlow und Toggle im StreamingScreen; Engine-API `zoomBy`/`resetZoom`/`tapToFocus`/`toggleStabilization`; Unit-Tests (ZoomCalculator, Controller, Adapter, Engine) |
| 2026-08-13 | — | **Multi-Streaming (bis zu 2 parallele RTMP(S)-Ziele)** implementiert: RootEncoder `MultiCamera2` in der `StreamingEngine` (`CameraFactory.create(List<ConnectChecker>)`, per-Ziel-ConnectChecker, `targetStates`-StateFlow), Status je Ziel im StreamingScreen, ein Fehlerziel stoppt nur sich selbst; sekundäre URL/Key/TLS in den Settings („Multi-Streaming (optional)“), Validator + Service-Plumbing (`EXTRA_STREAM_URLS`); Unit-Tests in allen betroffenen Modulen |
| 2026-08-13 | — | **SDK-Umstellung auf Android 17 (API 37):** `compileSdk`/`targetSdk` 37, `minSdk` 24 unverändert; `ACCESS_LOCAL_NETWORK` deklariert + Runtime-Permission-Flow im Settings-Screen („LAN-Zugriff für Remote-Control erlauben“, Server-Neustart nach Erteilung) für die Web-Remote-Control |
| 2026-08-13 | — | **OBS-Konfiguration per QR-Code importieren** umgesetzt: `ObsQrCodeParser` (Formate `obsws://` inkl. percent-decoded Passwort, `obswebsocket://`, `obswebsocket|[host]:[port]|[pw]`), Import-Feld im OBS-Settings-Screen, 11 Parser- + 4 ViewModel-Tests |
| 2026-08-13 | — | **Fokus-Lock (∞)** / Autofokus-Toggle für die Streaming-Kamera implementiert ([#377](https://github.com/eerimoq/moblin/issues/377)): `CameraFocusController` + `FocusableCamera` (RootEncoder `disableAutoFocus()`/`setFocusDistance(0f)`), `focusMode`-StateFlow in `StreamingEngine`, Toggle im StreamingScreen, Unit-Tests |
| 2026-08-13 | — | Zusatz-Feature (über Moblin-Parität hinaus): Oura-Ring-Gesundheitsdaten im Widget (Oura-Cloud-API, OAuth2; keine BLE-Schnittstelle) ergänzt |
| 2026-08-13 | — | Konkrete Tasks ergänzt: Focus-Lock/manueller Fokus ([#377](https://github.com/eerimoq/moblin/issues/377)) bei Tap-to-Focus, Golf-Scoreboard ([#326](https://github.com/eerimoq/moblin/issues/326)) bei Scoreboards |
| 2026-08-13 | — | Community-Feature-Requests aus dem Moblin-Tracker ergänzt: RTMP-Pull/Ingest ([#407](https://github.com/eerimoq/moblin/issues/407)), Text-Widget-Variablen GPS/`{road}` ([#360](https://github.com/eerimoq/moblin/issues/360) / [#384](https://github.com/eerimoq/moblin/issues/384)) |
| 2026-08-13 | — | Moblin **33.12.0** (2026-07-24) nachgetragen: Chat-Bot-Media-Steuerung (Android-Adaption der Apple-Music-Steuerung via MediaSession), Photo-Shoot-Quick-Button, Höhenmeter (Anstieg/Abstieg) im Text-Widget, Talkback-Mic im Remote-Control |
| 2026-08-12 | — | Foreground-Service für Hintergrund-Streaming (Notification, WakeLock, Runtime-Permissions) implementiert |
| 2026-08-12 | — | RTMPS (TLS-Ingest) verifiziert: RootEncoder 2.6.4 kann nativ TLS; Port-Normalisierung 1935→443 + Beweis-Test |
| 2026-08-12 | — | Go-Live-Selbst-Check (Stream-Config-Validierung, blockierende Fehler + Warnungen) ergänzt |
| 2026-08-11 | — | Web-Remote-Control (LAN-Server mit Token-Auth, Status/Start/Stop) ergänzt |
| 2026-08-11 | — | About-Screen (In-App-Version + Update-Check gegen GitHub-Releases) ergänzt |
| 2026-08-11 | — | Offene Tasks für OBS- & Audio-Troubleshooting ergänzt (README-FAQ) |
| 2026-08-08 | — | Erstversion erstellt (Stand nach `4d8362e`) |

---

*Siehe auch: [README – Parity Status](README.md#-parity-status) · [Moblin (Original)](https://github.com/eerimoq/moblin)*
