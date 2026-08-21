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

> **Stand:** 2026-08-20 · Aktualisierung: **Beta-Gate 3/3 erreicht** — (1) **17/17 ✅** (Row 80 „Chat-Bot: Media-Player-Steuerung“ ist der 17. Moblin-✅), (2) **Chat-✅** mit Twitch-Scope (Row 77: Twitch-IRC + Overlay + KI-Bot; Kick/YouTube/SOOP/OAuth = Post-Beta-Roadmap), (3) **≥1 Widget ✅** (Row 87: Text-/Info-Widget Zeit/GPS/Geschwindigkeit). Beta-Releases veröffentlicht: `v0.5.0-beta` … `v0.5.4-beta` (zuletzt 20.08.2026: Dark-Mode Stufe 2, Höhenmeter, Custom-Plattform, **I18n 3 Sprachen**, **Moderation (Ban/Timeout/Delete)**) — Referenzstand: Moblin **33.12.0**
>
> **Pflege:** Nach jedem Feature-Commit den Status in der jeweiligen Zeile aktualisieren und das Datum oben anpassen.
>
> 🚦 **Nächster Meilenstein (Beta):** Beta-Gate **3/3 erreicht** — alle drei Bedingungen formal erfüllt (≥17 ✅, Chat-✅ Twitch-Scope, ≥1 Widget ✅). Offen für den Beta-Build nur noch: Play-Unterlagen + ≥2 manuelle Tester (siehe [RELEASE.md](RELEASE.md#beta---nächster-meilenstein)).

---

## 📊 Übersicht

| Kategorie | ✅ | 🚧 | 📋 | Summe |
|-----------|----|----|----|-------|
| Streaming & Protokolle | 5 | 0 | 4 | 9 |
| Netzwerk-Bonding | 0 | 0 | 1 | 1 |
| OBS-Steuerung | 3 | 0 | 1 | 4 |
| Chat & Moderation | 3 | 0 | 1 | 4 |
| Overlays & Widgets | 0 | 2 | 4 | 6 |
| Kamera & Video | 2 | 0 | 4 | 6 |
| Audio | 0 | 0 | 3 | 3 |
| Remote & Companion | 1 | 0 | 2 | 3 |
| Plattform & Grundlagen | 6 | 0 | 0 | 6 |
| Zusatz-Features (über Parität) | 2 | 0 | 1 | 3 |
| **Gesamt** | **22** | **2** | **21** | **45**† |

† Inkl. 1 n/a-Zeile (Apple-Watch-Companion) und 3 Zusatz-Features über die Moblin-Parität hinaus; anwendbare Moblin-Features: **42**.

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
| Plattform-Chat (Twitch, Kick, YouTube, SOOP) | ✅ Twitch-Scope | `feature-chat` | **Twitch vollständig (IRC-Ausstieg ✅):** `TwitchChatEventSubReader` (EventSub-WebSocket `channel.chat.message`, Scope `user:read:chat`; Badges/Emotes/Farbe/Zeitstempel aus dem Event, `session_welcome`→Helix-Subscribe, `session_reconnect`+Backoff) + `TwitchSendChatClient` (Helix `POST /helix/chat/messages`, Scope `user:write:chat`, `drop_reason`-Auswertung). IRC komplett entfernt (`TwitchChatClient`, `TwitchBotClient`, `IrcConnection`/Parser, Tests, DI-Binding). Overlay und Bot lesen über den Reader, Senden über die Helix-API; das Overlay braucht jetzt die Bot-Zugangsdaten (kein anonymes `justinfan`-Lesen mehr). **UI/Overlay ✓** (`ChatOverlay` + `ChatOverlayViewModel`), **KI-Chat-Bot ✓** (Modus-Switch, `!tts`, Media, Koexistenz, Begrenzungen). **Scope-Entscheidung (Beta-Gate):** „Twitch-Chat“ gilt als ✅ — Lesen (EventSub) + Senden (Helix), Overlay und Bot laufen. **Post-Beta-Roadmap:** [Bucket „Multi-Plattform-Chat“](#-roadmap-bucket-multi-plattform-chat-kick-youtube-soop) — Kick (WebSocket), YouTube (innertube), SOOP, OAuth-Login für Senden/Moderation |
| Emotes (BTTV, FFZ, 7TV) | 📋 | `feature-chat` | Emote-API-Clients + Rendering |
| Moderation (Ban, Timeout, Delete), Chat-Bot, TTS | ✅ | `feature-chat` | **Vollständig:** Chat-Bot-Kommandos (`!help`/`!uptime`/`!tts`/`!bot` via `BotCommandProcessor`) inkl. **Chat-TTS-Toggle** (`!tts`, `ChatTtsController` + `AndroidTtsSpeaker`); **Moderation `!ban`/`!timeout`/`!delete` (Owner-only)** über `TwitchModerationClient` (Helix `POST /helix/moderation/bans` + `DELETE /helix/moderation/chat`, Scopes `moderator:manage:banned_users`/`moderator:manage:chat_messages`, der Bot muss Moderator im Kanal sein) + `ChatModeration`-Interface in der Engine (Owner-Gate, Rate-Limit, privater Antwortweg); `!delete` löscht die letzten N vom Bot gesehenen Nachrichten (50-ID-Ringpuffer) |
| Chat-Bot: Media-Player-Steuerung (generisch via MediaSession, z. B. Apple Music/Spotify) | ✅ | `feature-chat` (`media`) | Android-Adaption der Apple-Music-Steuerung aus Moblin 33.12.0: `ChatMediaController` steuert den aktiven Media-Player über `MediaSessionManager.getActiveSessions` → `MediaController.TransportControls` (bevorzugt playing/paused/buffering-Session); Kommandos `!song`/`!nowplaying`, `!next`/`!skip`, `!pause`, `!play`, `!prev`/`!previous` (case-insensitive); **Voraussetzung:** Benachrichtigungszugriff via `MediaNotificationListener` (leerer Zugriffs-Marker, liest keine Benachrichtigungen) — ohne Zugriff antwortet der Bot mit einem Hinweis; Button „Benachrichtigungszugriff aktivieren“ im Settings-Screen |

### 💬 Roadmap-Bucket: Multi-Plattform-Chat (Kick, YouTube, SOOP)

**Referenz (Moblin):** Chat-Einbindung für **Twitch, Kick, YouTube und SOOP** — alle vier Plattformen lesen und senden im Moblin-Chat.

**Ist-Zustand in Vivid:** Twitch ist vollständig (EventSub lesen + Helix senden, kein IRC — Zeile oben, ✅ Twitch-Scope). Das `ChatMessage`-Modell ist **plattformneutral** (nur `emotesTag` ist Twitch-spezifisch), Overlay (`ChatOverlayViewModel`), `ChatBotEngine` und `BotCommandProcessor` arbeiten ausschließlich auf `Flow<ChatMessage>` + `ChatConnectionState` — ein Adapter pro Plattform dockt **ohne Umbau** an. Dieses Bucket ist die **Post-Beta-Roadmap** (Beta-Gate-Entscheidung: Twitch-Scope = Chat-✅).

**Adapter-Interface (Zielbild):**

| Interface | Vertrag | Erfüllt heute durch |
|-----------|---------|---------------------|
| `ChatReader` | `messages: Flow<ChatMessage>`, `state: StateFlow<ChatConnectionState>`, `start(channel)`, `stop()` | `TwitchChatEventSubReader` (Refactoring: Interface extrahieren, Verhalten unverändert) |
| `ChatSender` | `send(channel, text): SendResult` | `TwitchSendChatClient` (Helix) |
| `ChatSessionManager` | eine aktive Session pro Kanal/Plattform, parallele Sessions für Multi-Streaming | — (neu) |

**Modul-Zuordnung:**

| Modul | Verantwortung |
|-------|---------------|
| `feature-chat` | `ChatReader`/`ChatSender`-Interfaces + Adapter `kick`/`youtube`/`soop`, `ChatSessionManager` |
| `feature-settings` | Plattform-Auswahl + Kanal-Feld je Plattform (Sektion „Chat-Overlay“ erweitern), OAuth-Einleitung |
| `core` | Token-/Session-Speicher (DataStore), OAuth-Callback-Verarbeitung |
| `app` | OAuth-Browser-Flow (Custom-Tab/`ACTION_VIEW`), Deep-Link-Route für den Callback |

**Offene Tasks (priorisiert):**

1. **Interface-Refactoring:** `ChatReader`/`ChatSender` extrahieren, `TwitchChatEventSubReader` unterordnen — Verhaltens-neutral, Bestandstests als Sicherheitsnetz.
2. **YouTube-Adapter (größte Zielgruppe, schnellster Overlay-Mehrwert):** innertube-API — `live_chat_id` aus der Video-Seite, Polling mit `continuation`-Token (kein WebSocket); **anonymes Lesen ohne Login** möglich → Overlay sofort nutzbar; Senden via Google-OAuth (`youtube.force-ssl`).
3. **Kick-Adapter:** WebSocket über das **Pusher-Protokoll** (`chat.v1.<channel>`-Subscription); Senden über GraphQL-Mutation `SendChatMessage` (Community-API — Auth-Cookie/Token sicher in DataStore, nie ins Log).
4. **SOOP-Adapter:** niedrigste Priorität, proprietäres Protokoll — erst nach YT/Kick, falls Zielgruppe relevant.
5. **OAuth-Browser-Flow:** Twitch zuerst (PKCE, Scopes `user:write:chat` + Moderation) — ersetzt den manuellen Token-Eintrag; danach Google für YouTube. Deep-Link-Route für den Callback.
6. **Multi-Plattform parallel:** eine `ChatSessionManager`-Session pro Kanal/Plattform (Twitch + YouTube gleichzeitig beim Multi-Streaming); Bot aggregiert die Nachrichten-Streams, Antwort geht auf die Plattform des Kanals (Sender je Session).
7. **Settings:** Plattform-SegmentedButton + Kanal-Feld pro Plattform; Overlay/Bot lesen die aktive Session statt fest Twitch.
8. **Tests:** Adapter-**Contract-Tests** (gemeinsame Fixtures für alle Reader — Statusübergänge, Reconnect, Nachrichten-Parsing), Mock-Server für Pusher/innertube, Sender-Fehlerpfade (`drop_reason`-Analogon).

**Abgrenzung:** Twitch selbst bleibt auf der Zeile oben (✅ Twitch-Scope) — dieses Bucket erweitert nur um die **weiteren Plattformen**. **Emotes (BTTV/FFZ/7TV)** und **Moderation (Ban/Timeout/Delete)** sind eigene Zeilen (📋 / 🚧) mit eigener Roadmap; der **Twitch-OAuth-Flow** (Task 5) überschneidet sich mit der Moderation-Zeile, weil Senden + Moderation dieselben Scopes brauchen.

## 🎨 Overlays & Widgets

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Chat-Overlay & Event-Alerts (Follow/Sub/Raid) | 🚧 | `feature-chat` (`ChatOverlay`), `feature-settings`, `feature-streaming` | **Chat-Overlay ✓:** zeigt den Twitch-Chat über der Streaming-Vorschau (anonym gelesen) — Kanal + Toggle in den Settings (`chat_channel`/`chat_overlay_enabled` in `SettingsRepository`), Start/Stop der Verbindung über `ChatOverlayViewModel` (Normalisierung auf Kleinbuchstaben, max. 50 Nachrichten, Status „Verbinde…“/„Chat: <kanal>“), `ChatOverlay`-Composable unten links im StreamingScreen, automatisches Ausblenden bei deaktiviertem Overlay; 8 VM-Tests + 3 Repository-Tests. **Inline-Emotes ✓:** strukturierte Twitch-Emote-Fragment-Parsing (`InlineEmote`-Modell + `parseFromEmotesTag`), Coil-Integration (25MB Disk-Cache) und `FlowRow`-Rendering im `ChatOverlay` — Emotes werden inline neben dem Text als CDN-Bilder gerendert; 8 Parser-Tests + 7 Overlay-Segment-Tests. **Offen:** Badges im Overlay, Event-Alerts (Follow/Sub/Raid) mit Trigger-API |
| Text-/Info-Widgets (Zeit, Wetter, Geschwindigkeit, GPS, Höhenmeter) | 🚧 | `feature-widgets` | **Zeit + GPS-Koordinaten + Geschwindigkeit ✅** (`TextInfoWidget`-Overlay rechts unten über der Vorschau, `TextInfoWidgetViewModel` mit Sekunden-Ticker + `LocationProvider`/LocationManager, Settings-Toggles `widget_enabled`/`_show_time`/`_show_location`/`_show_speed` inkl. Runtime-Permission; 14 Unit-Tests). **Offen:** Wetter (externer Dienst), Höhenmeter (Anstieg/Abstieg, **neu in Moblin 33.12.0**) |
| Text-Widget-Variablen (GPS-Koordinaten, `{road}`/Route) | 📋 | `feature-widgets` | Community-Feature-Requests [#360](https://github.com/eerimoq/moblin/issues/360) / [#384](https://github.com/eerimoq/moblin/issues/384); Variablen-System im Text-Widget erweitern |
| Karten-Widget | 📋 | `feature-widgets` | Karten-Provider wählen (Maps SDK / Tile-Overlay) |
| Browser-Widget (CSS + JS-API) | 📋 | `feature-widgets` | WebView-Layer + `postMessage`-Bridge |
| Scoreboards (Padel, Golf, Volleyball) | 📋 | `feature-widgets` | Datenmodelle + Renderer; **Open Task:** Golf-Scoreboard ([#326](https://github.com/eerimoq/moblin/issues/326)) — Spielernamen, Loch, Par, Punktestand, Auto-Total, horizontale/vertikale Layouts |

## 📹 Kamera & Video

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Tap-to-Focus, Pinch-Zoom, Stabilisierung | ✅ | `feature-streaming` (`StreamingEngine`, `StreamingPreviewGestures`, `ZoomCalculator`, `CameraStabilizationController`, `RootEncoderCameraControls`) | Komplett auf der Streaming-Kamera (RootEncoder, nicht CameraX): **Tap-to-Focus** (einzelner Tipp), **Pinch-Zoom** (`ScaleGestureDetector`, geclampt auf `getZoomRange()`) und **Zoom-Reset** (Doppeltipp) über `StreamingPreviewGestures` auf der Vorschau; **Video-Stabilisierung** als Toggle (OIS bevorzugt, sonst EIS) mit `stabilizationEnabled`-StateFlow; Engine-API `zoomBy`/`resetZoom`/`tapToFocus`/`toggleStabilization`; Unit-Tests (`ZoomCalculatorTest`, `CameraStabilizationControllerTest`, `RootEncoderCameraControlsTest`, `StreamingEngineTest`) |
| Fokus-Lock (∞) / Autofokus-Toggle | ✅ | `feature-streaming` (`StreamingEngine`, `CameraFocusController`) | Toggle Auto ⇄ Unendlich-Lock gegen Fokus-Hunting ([#377](https://github.com/eerimoq/moblin/issues/377), Drive-/Train-Streams): RootEncoder-API (`disableAutoFocus()` + `setFocusDistance(0)`) über `FocusableCamera`-Adapter; Zustand als `focusMode`-`StateFlow` in der Engine (auch vor dem Go-Live schaltbar); Toggle im StreamingScreen; Unit-Tests (`CameraFocusControllerTest`, `StreamingEngineTest`) |
| Color-Spaces (sRGB, P3, Log) + 3D-LUTs | 📋 | `feature-streaming` (Preview + Encode-Pfad), `core` (LUT/Persistenz), `feature-settings` (UI) | Roadmap-Bucket „Color Pipeline“ — [Details & Tasks unten](#-roadmap-bucket-color-spaces--3d-luts) |
| Video-Effekte (Graustufen, Letterbox, Sepia, Rauschfilter) | 📋 | `feature-streaming` | OpenGL-Effektkette |
| Externes Zubehör (DJI Osmo, GoPro, Gimbal, UVC) | 📋 | `feature-streaming` | BLE/USB-Integrationen einzeln bewerten |
| Photo-Shoot-Quick-Button (periodisch hochauflösende Fotos) | 📋 | `feature-streaming` | Quick-Button-Aktion; ein sauberes Bild pro Sekunde pro Kamera, Ablage in der Galerie; **neu in Moblin 33.12.0** |

### 🎨 Roadmap-Bucket: Color-Spaces + 3D-LUTs

**Referenz (Moblin):** Farbraum-Auswahl **sRGB / P3 D65 / Apple Log** (nur auf Geräten, die den Farbraum unterstützen) + **3D-LUT-Effekte** — gebündelt und eigene Importe. Moblin unterstützt nur **PNG-3D-LUTs** (Hald-CLUT); `.cube`-Dateien müssen vorher zu PNG konvertiert werden (Anleitung in der Moblin-Doku). Einstellung in Moblin: *Settings → Scenes → Graphics*.

**Ist-Zustand in Vivid:** Die Kamera-Vorschau ist ein plain `SurfaceView`, das von der internen GL-Pipeline von RootEncoder (`MultiCamera2`) gerendert wird (`attachPreview` in `StreamingEngine`); es gibt **keine eigene Shader-Stufe** — weder Vorschau noch Encoder-Pfad sind color-graded. RootEncoder bringt aber bereits die Echtzeit-GL-Filter-API mit (`setFilter`/`GlFilter` auf `Camera2Base`/`MultiCamera2`, 30 eingebaute Filter, eigene `GlFilter`-Shader möglich) — **das ist der Integrationspunkt** für beide Pfade (Preview-only oder „in den Stream baken“).

**Modul-Zuordnung:**

| Modul | Verantwortung |
|-------|---------------|
| `feature-streaming` | GL-Filter-Integration in `StreamingEngine` (Preview + Encoder-Pfad), Farbraum-/LUT-Shader als `GlFilter` |
| `core` | `.cube`-Parser, Hald-CLUT-PNG-Decoder, Farbraum-Matrizen, `SettingsRepository` (`color_space`, `lut_id`, `lut_strength`) |
| `feature-settings` | Neue Sektion „Farbe“: Farbraum-SegmentedButton, LUT-Auswahl (gebündelt/importiert) + Stärke-Slider |
| `app` | Gebündelte Beispiel-LUTs als Assets |

**Offene Tasks (priorisiert):**

1. **Architektur-PoC:** `setFilter`/`GlFilter` im `MultiCamera2`-Pfad verifizieren — funktioniert der Filter auch auf den Encoder (Stream) oder nur auf die Preview? PoC mit einfachem Shader (z. B. Sättigung/Weißabgleich), Test auf 2 Geräten (Flaggschiff + Midrange).
2. **Farbraum-Auswahl:** sRGB (Default) / P3 / Log als `GlFilter`-Shader (Konvertierungs-Matrizen); Gerätefähigkeit via `Display.isWideColorGamut()` abfragen, nicht unterstützte Räume ausblenden; Log ohne passende LUT als „roh/entsättigt“ kennzeichnen.
3. **LUT-Engine:** `.cube`-Parser (33³/65³, 3D-Lookup) + **Hald-CLUT-PNG** (Moblin-kompatibel, 64³-Näherung); LUT als 3D-Textur (RGBA8 oder RGB10_A2) mit **trilinearer Interpolation** im Fragment-Shader; Stärke-Mix (0–100 %) zwischen Original und LUT-Ergebnis.
4. **Bundled LUTs + Import:** 1–2 Beispiel-LUTs (z. B. „Neutral“, „Log → Rec.709“-Konvertierung) in den Assets; eigener Import per Storage-Access-Framework (Datei-Picker für `.cube`/`.png`), LUT-Persistenz im App-internen Speicher.
5. **Settings-UI + Persistenz:** Sektion „Farbe“ (Farbraum, LUT, Stärke) in die Settings-Menüstruktur (Kategorie „Overlays & Widgets“ oder eigene Kachel); Live-Vorschau des Filters im Settings-Screen.
6. **Performanz:** Fragment-Shader-Kosten bei 1080p60 auf Midrange-Geräten messen (Frame-Drops, Encoder-Latenz); ggf. LUT-Auflösung reduzieren oder Shader vereinfachen.
7. **Tests:** `.cube`-/Hald-PNG-Parser (Format-Fehler, Größen), Farbraum-Matrizen (sRGB↔P3, Log→Rec.709-Näherung gegen Referenzwerte), Settings-Persistenz, Shader-Smoke-Test (Preview rendert ohne Artefakte).

**Abgrenzung:** Nicht verwechseln mit dem **UI-Farbschemata**-Zusatz-Feature (Stufe 1 ✅, [Zeile im Zusatz-Abschnitt](#-zusatz-features-über-moblin-parität-hinaus)) — das ist das App-Theme (Dark/Light), hier geht es um den **Video-Farbraum** des Streams.

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
| I18n (lokalisierte Strings) | ✅ | `feature-settings`, `feature-obs-control`, `feature-streaming`, `app`, `core` (je eigene `res/values` + `values-en`) | **Vollständige Externalisierung:** alle UI-Literale (`Text("…")`, `contentDescription`, `label`/`title`, Validierungsmeldungen `StreamConfigValidator`, Notification-Texte, Update-Check-Fehler, Enum-Anzeigenamen als `@StringRes`-IDs) liegen in Modul-`strings.xml` — Deutsch (Default) + Englisch (`values-en`) vollständig; Nicht-Lokalisierung dokumentiert (Bot-/!diag-Texte, Logs, technische Exception-Details, Version-Suffixe). **CI-Gates aktiv:** Externalisierungs-Gate (`git grep 'Text("')` = 0 Treffer), values↔values-en-Vollständigkeits-Check, `stream_url_hint`-Inhalts-Guard (beide Sprachen) — Skripte `scripts/check_i18n.sh` + Selbsttest `scripts/test_check_i18n.sh` (5 Fixtures) in Pre-Push + CI. Plan: [docs/i18n-plan.md](docs/i18n-plan.md) |

## 💡 Zusatz-Features (über Moblin-Parität hinaus)

| Feature | Status | Modul | Offene Tasks / Notizen |
|---------|--------|-------|------------------------|
| UI-Farbschemata (Dark/Light folgt dem System, Vivid-Palette, User-Toggle + Akzentfarbe) | ✅ | `app` (`ui/theme/Theme.kt`), `feature-settings` (Kategorie „Darstellung“), `domain`/`core` (Settings) | **Stufe 2 fertig (20.08.):** User-Toggle **System/Hell/Dunkel/AMOLED** (`ThemeMode`-Enum, AMOLED = rein-schwarze Flächen für OLED) + **6 kuratierte Akzentfarben** (`AccentColor`, M3-TonalSpot-Paletten — Vivid-Grün bleibt der exakte Stufe-1-Standard). Neue Settings-Kategorie **„Darstellung“**: Design-Modus-SegmentedButton + Farb-Swatches; Persistenz via `SettingsRepository` (`theme_mode`/`theme_accent`, Defaults SYSTEM/VIVID_GREEN), `VividTheme` liest die Settings in der `MainActivity` live (wirkt sofort, kein Neustart). **Stufe 1 (18.08.):** Vivid-Grün-Palette (Seed `#3DDC84`) ersetzt die Template-Farben, `VividTheme` folgte dem System. Abgrenzung: **nicht** zu verwechseln mit dem Moblin-Paritäts-Feature „Color-Spaces (sRGB/P3/Log) + 3D-LUTs“ (Video-Farbraum, Zeile oben — eigenes Roadmap-Bucket) |
| Oura-Ring-Gesundheitsdaten im Widget (Sleep, Readiness, HR/HRV) | 📋 | `core` (OAuth2-Client, Repository), `feature-widgets` | Anzeige als Text-/Info-Widget (z. B. Readiness-/Schlaf-Score, Ruhe-HF); **keine BLE-Schnittstelle** → nur Oura-Cloud-API (OAuth2, Browser-Flow), daher aggregierte/verzögerte Werte, kein Live-Puls; Rate-Limits beachten |
| KI-Chat-Bot (automatische Chat-Antworten via LLM, OpenAI-kompatibel) | ✅ | `feature-chat` (`ai`, `bot`, `twitch`), `feature-settings`, `app` (`StreamingService`) | Verbindet sich beim **Go-Live vollautomatisch** (Twitch-Chat via EventSub-Reader + Helix-Send, Scopes `user:read:chat`+`user:write:chat`) und fährt bei **Streamende sauber herunter** — Lifecycle via `StreamingService` → `ChatBotController`; Deaktivieren in den Settings stoppt sofort. **Betriebsmodus-Switch** in den Settings („Bot (wie Moblin)“ ↔ „KI autonom“): COMMAND = deterministische `!`-Befehle (`!help`/`!commands`, `!uptime`, `!tts`, `!bot`) via `BotCommandProcessor`, **kein LLM nötig**; AUTONOMOUS = KI entscheidet selbst (inkl. bewusstem Schweigen via `[keine Antwort]`-Marker, Autonomie-Prompt). **Chat-TTS:** `!tts` schaltet das Vorlesen von Chat-Nachrichten um (`ChatTtsController` + `AndroidTtsSpeaker`, überspringt eigene Nachrichten + Befehle, Zustand überlebt Stream-Ende). Bausteine: `TwitchChatEventSubReader` (Lesen via EventSub) + `TwitchSendChatClient` (Senden via Helix), `ChatBotEngine` (Modi, nur-Erwähnung-Modus, Cooldown, Rate-Limit/Min., 500-Zeichen-Limit, Prompt-History), `BotCommandProcessor`, `OpenAiCompatibleLlmClient` (`/v1/chat/completions` → OpenAI, Gemini, Groq, DeepSeek, Ollama im LAN); Bot-Settings in `SettingsRepository`/`AppSettings` (`chat_bot_mode`); 12 neue CommandProcessor-Tests + 8 neue Engine-Modi-Tests + 2 Repository-Tests erweitert — Gesamtsuite feature-chat (76) grün. **Settings-Screen komplett:** alle Bot-Felder im Abschnitt „Chat-Bot (KI)“ editierbar (Bot-Login, Twitch-OAuth-Token + LLM-API-Key als Passwortfelder mit Sichtbarkeits-Toggle, LLM-Basis-URL/Modell/System-Prompt, Cooldown, Mentions-only, Rate-Limit, Modus). **Koexistenz-Modus** (läuft neben dem Bot eines anderen Tools wie Rivulet): `chat_bot_ignore_bots` (andere Bot-Logins komplett ignorieren — keine Befehle, kein LLM-Input, kein TTS-Vorlesen), `chat_bot_command_scope` (ALL/MENTION/PREFIX), `chat_bot_command_prefix` (z. B. `v` → `!v!help`); fremde Befehle außerhalb des Scopes geben kein „Unbekannter Befehl“-Echo (None), damit der andere Bot ungestört antwortet. **Begrenzungen:** Per-Viewer-Cooldown (Default 60 s), Per-Viewer-Cap pro Stream und Stunden-Budget (Kosten-Deckel) — alle `0 = aus`, plattformneutral über `userId`, Moderatoren umgehen die Per-Viewer-Limits, Zähler reset bei Stream-Ende/-Start. **Schnellstart-Voreinstellungen** (Locker 30/0/0 · Balanced 60/10/120 · Streng 180/5/60) füllen die drei Felder mit einem Tipp, „Eigene“ bei abweichenden Werten; die **zuletzt gewählte Stufe wird persistiert** (`chat_bot_limit_preset`, Restore beim App-Start, manuelle Änderung → CUSTOM). **Live-Verbrauch** im Settings-Screen (`ChatBotEngine.usage`): Antworten/Stunde (vs. Budget), Stream-Total und Top-Viewer (Anzeigename + Anzahl). **Owner-Steuerung (nur der Streamer):** Owner-Gate = Broadcaster-Badge (`isBroadcaster` aus `broadcaster/1`) + Allow-List (`chat_bot_owner_logins`); Owner-Befehle `!start`/`!go-live`, `!stop`/`!end`, `!diag`/`!status` und `!ask <frage>` in `BotCommandProcessor` + Engine (funktionieren in beiden Modi); `ChatStreamControl`-Interface + `StreamDiagnostics` (Status, OBS, 9 Konfigurations-Checks) in feature-chat (entkoppelt, `@BindsOptionalOf`), echte Implementierung `AppChatStreamControl` in der App (delegiert Start/Stopp an `StreamControl`, liest `StreamingEngine`/`StreamingRepository`/`SettingsRepository`); separate **Owner-KI** (`chat_bot_owner_llm_base_url`/`_api_key`/`_model`, exklusiv für Streamer-Befehle) für `!ask` + Diagnose-Empfehlungen (Fallback: Viewer-KI, sonst deterministische Checkliste); Owner umgehen Cooldown/Per-Viewer-Limits, globales Rate-Limit + Stunden-Budget gelten weiter; Settings-Screen: Abschnitt „Owner-Zugriff (nur Streamer)“; Nicht-Owner bekommen nur einen Hinweis, keine Aktion. **Offen:** Twitch-OAuth-Browser-Flow. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |

---

## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-20 | `cee9141` | **I18n-Externalisierung abgeschlossen ✅** (PARITY-Plattform & Grundlagen): alle ~110+ UI-Literale in 4 Modulen + Validierungs-/Notification-/Update-Fehlertexte auf Modul-`strings.xml` (Deutsch Default + **vollständiges Englisch** `values-en` in app/core/feature-settings/feature-obs-control/feature-streaming) umgestellt — Enum-Anzeigenamen (StreamPlatform, ChatBotLimitPreset, OwnerLlmSource, ThemeMode/AccentColor/ChatBotMode/Scope), `SettingsCategories` (titleRes/subtitleRes), `StreamConfigValidator` (messageRes/prefixRes/formatArgs, `errorMessage` durch `configIssues`-Ressourcen ersetzt), `UpdateCheckResult.Error` (messageRes+formatArgs, core-Ressourcen), `StreamingServiceSupport` (notificationTitleRes/TextRes, Service löst per getString auf), `StreamingScreen`-Status/Target-Labels; Nicht-Lokalisierung explizit dokumentiert (Bot-/!diag-Texte, Exception-Details, technische Fallbacks, Version-Suffixe). **CI-Gates:** `scripts/check_i18n.sh` (Externalisierungs-Gate `Text("…")` = 0 Treffer in src/main, values↔values-en-Vollständigkeit, stream_url_hint-Inhalts-Guard in beiden Sprachen) + Selbsttest `scripts/test_check_i18n.sh` (5 Fixtures) — verdrahtet in Pre-Push (`pre-push.sh` + `test_pre_push.sh`) und android.yml (guard-secrets-Job). **Zähler:** Plattform & Grundlagen 5/1/0 → 6/0/0, Gesamt 20/4/21 → **21/3/21** — der letzte offene In-Progress-Punkt ist geschlossen. Alle Modul-Tests (app/core/feature-settings/feature-obs-control/feature-streaming) grün |
| 2026-08-17 | `e5dab62` | **Chat-Bot: Owner-Steuerung (nur der Streamer)** (Vivid-Zusatz): Owner-Gate = `isBroadcaster`-Parsing (`broadcaster/1`-Badge in `TwitchBotClient`/`TwitchChatClient`) + `chat_bot_owner_logins`-Allow-List; Owner-Befehle `!start`/`!go-live`, `!stop`/`!end`, `!diag`/`!status`, `!ask <frage>` in `BotCommandProcessor`/`ChatBotEngine` (beide Modi, Owner umgehen Cooldown/Per-Viewer-Limits, globales Rate-Limit bleibt, Nicht-Owner nur Hinweis); separate Owner-KI (`chat_bot_owner_llm_base_url`/`_api_key`/`_model`) für `!ask` + `!diag`-Empfehlungen mit deterministischem Fallback; `ChatStreamControl`-Interface + `StreamDiagnostics`/`DiagnosticCheck` (Status, OBS, 9 Checks) in feature-chat (entkoppelt via `@BindsOptionalOf`), `AppChatStreamControl` + Hilt-Binding in der App; Settings-Screen „Owner-Zugriff (nur Streamer)“; Tests: 2 Twitch-Parse + 3 Config + 6 Processor + 11 Engine (Gate, Allow-List, Broadcaster, COMMAND-Modus, Cooldown-Bypass, Rate-Limit-Schutz, diag mit/ohne Owner-KI + Fallback, ask mit/ohne Konfiguration + leer) + Repository (Defaults/Roundtrip) + ViewModel (Input, Save) — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | `e4674f5` | **Chat-Bot: Preset-Persistenz** (Vivid-Zusatz): `chat_bot_limit_preset`-Key (AppSettings/SettingsRepository/`updateChatBotSettings`), ViewModel speichert die gewählte Stufe (`LOCKER`/`BALANCED`/`STRICT`), manuelle Limit-Änderungen setzen CUSTOM; `ChatBotLimitPreset.selection()` (gespeicherter Preset gewinnt, Fallback auf Wert-Matching) + fromName; 3 neue Preset-Tests + ViewModel-Tests (Wahl setzt Preset, manuelle Änderung → CUSTOM, Save persistiert) + Repository-Tests — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | `e4674f5` | **Chat-Bot: Live-Verbrauch im Settings-Screen** (Vivid-Zusatz): `ChatBotUsage`-Data-Klasse (repliesThisHour, hourlyBudget, totalRepliesThisStream, topViewers) + `usage`-StateFlow in der `ChatBotEngine` (aktualisiert bei jeder Antwort, Reset bei Start/Stop, Top-5-Viewer mit Anzeigename); feature-settings hängt jetzt an feature-chat, `SettingsViewModel.botUsage` reicht den Flow durch; Settings-Screen: „Live-Verbrauch“-Block unter den Begrenzungen (x/y-Budget-Anzeige); 3 neue Engine-Tests (Usage, Budget 0, Reset) + 1 ViewModel-Test (Forwarding) — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | `e4674f5` | **Chat-Bot: Schnellstart-Voreinstellungen** (Vivid-Zusatz): `ChatBotLimitPreset`-Enum (LOCKER 30/0/0 · BALANCED 60/10/120 · STRICT 180/5/60) + SegmentedButton-Leiste „Locker · Balanced · Streng · Eigene“ im Settings-Screen über den Limit-Feldern; Tipp füllt Cooldown/Cap/Budget (frei anpassbar danach), Matching-Logik zeigt „Eigene“, sobald die Werte keiner Stufe entsprechen; 2 neue ViewModel-/Preset-Tests — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-17 | `a9171cb` | **Chat-Formalisierung (Beta-Gate 3/3 erreicht):** Plattform-Chat (Row 77) formal auf ✅ gesetzt mit **Twitch-Scope-Entscheidung** — Twitch-Chat (Lesen anonym, Overlay, KI-Bot) gilt fürs Beta-Gate als erfüllt; **Kick/YouTube/SOOP + OAuth-Login (Senden/Moderation) explizit als Post-Beta-Roadmap** dokumentiert (RELEASE.md Roadmap + Gate-Tabelle). Damit sind alle drei Beta-Gate-Bedingungen erfüllt: ≥17 ✅, Chat-✅, ≥1 Widget ✅ — nächster Schritt: erster Beta-Tag `v0.5.0-beta` |
| 2026-08-17 | `cdb3d8c` | **Text-/Info-Widget (Moblin-Parität Row 87, erster Widget-Punkt ✅)** — `feature-widgets` von Platzhalter zu lauffähigem Overlay: `LocationProvider`-Interface + `AndroidLocationProvider` (LocationManager, kalter `callbackFlow` mit `getLastKnownLocation` + `requestLocationUpdates`, 2-s-Intervall, Permission-Guard) in `core` + Hilt-Binding, `ACCESS_FINE_LOCATION` im Manifest; `TextInfoWidgetViewModel` (Settings-Collect, überschreibbarer Sekunden-Ticker für Uhrzeit/Datum, Location-Combine nur wenn aktiv), `TextInfoWidget`-Composable (semi-transparente Box rechts unten über der Vorschau, Uhrzeit groß + Datum, 📍 GPS, 🚗 km/h, fragt die Location-Permission bei Bedarf an), im StreamingScreen eingehängt (feature-streaming → feature-widgets); Settings-Screen: Abschnitt „Text-/Info-Widget“ (4 Toggles: an/aus + Zeit/GPS/Geschwindigkeit, Keys `widget_enabled`/`widget_show_time`/`widget_show_location`/`widget_show_speed`); `WidgetFormatters` (pure, testbar: 24-h-Zeit, Datum, Koordinaten mit Himmelsrichtung, m/s → km/h deutsch); 2 Repository- + 7 Formatter- + 7 VM- + 1 Settings-ViewModel-Test — Gesamtsuite + Lint + App-Compile grün. **Offen:** Wetter (externer Dienst), Höhenmeter |
| 2026-08-16 | `6bd4833` | **Chat-Bot: Begrenzungen (Per-Viewer + Kosten)** (Vivid-Zusatz): `chat_bot_per_viewer_cooldown_seconds` (Default 60, 0 = aus), `chat_bot_per_viewer_max_replies` (Cap pro Stream), `chat_bot_max_replies_per_hour` (Kosten-Budget) in AppSettings/SettingsRepository/`updateChatBotSettings`; `ChatBotEngine` wendet sie plattformneutral über `userId` an (Cooldown-Map + Cap-Zähler, gleitendes 1-h-Fenster fürs Budget), Moderatoren umgehen Per-Viewer-Limits, Reset bei Start/Stop; Settings-Screen: Abschnitt „Begrenzungen“ (3 numerische Felder); 8 neue Engine-Tests (Cooldown blockiert/läuft ab/pro-Viewer-unabhängig/Mod-Bypass, Cap inkl. Reset, Stunden-Budget inkl. Ablauf) + Repository-/ViewModel-Tests erweitert — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-20 | `5957f82` | **UI-Farbschemata (Vivid-Zusatz, Stufe 2 — fertig ✅):** User-Toggle **System/Hell/Dunkel/AMOLED** + kuratierte **Akzentfarben** als neue Settings-Kategorie **„Darstellung“** — `ThemeMode`/`AccentColor` (domain, `fromName`-Fallback SYSTEM/VIVID_GREEN), AppSettings-Felder `themeMode`/`themeAccent`, `SettingsRepository` (`theme_mode`/`theme_accent`-Keys + `updateThemeSettings`, äußerer Combine wegen 5-Flow-Limit), `Theme.kt` mit `accentPalettes` (6 Akzente, M3-TonalSpot/Chroma 40 aus den material-color-utilities — Vivid-Grün = exakt die Stufe-1-Werte) + `VividAmoledColorScheme` (reine Schwarz-Flächen) + erweiterte `VividTheme(darkTheme, amoled, accent)`, `MainActivity` injiziert das Repository und wendet die Settings live an (Route `settings_appearance`), SettingsCategories + `SettingsAppearanceScreen` (Modus-SegmentedButton + Farb-Swatches mit Auswahl-Ring). Tests: `ThemeModeTest` (6), `AccentColorTest` (4), Repository (3 neue: Defaults/Speichern/Unknown-Fallback), ViewModel (3 neue: Laden/Handler/Speichern), `SettingsCategoriesTest` (6 Kategorien), `VividThemeTest` (6 neue: Map-Vollständigkeit, Grün-Identität, distinct Primaries, Neutrale unberührt, AMOLED-Schwarz, AMOLED+Akzent) — Domain/Core/feature-settings/App-Suiten + Lint grün |
| 2026-08-19 | `a3af8a5` | **Chat-Overlay: Inline-Twitch-Emotes** implementiert — `InlineEmote`-Datenmodell mit IRC-`emotesTag`-Parser (`parseFromEmotesTag`), Twitch-CDN-URL (`static-cdn.jtvnw.net`), strukturierte EventSub-Fragment-Parsing in `TwitchChatEventSubReader` (extrahiert `inlineEmotes` aus `ChatMessageEvent.message.fragments`), Coil-Integration (`coil-compose` in `libs.versions.toml` + `feature-chat/build.gradle.kts`, 25MB Disk-Cache in `VividApplication`), `ChatOverlay`-Composable mit `FlowRow`-Rendering (Textsegmente + inline `AsyncImage` pro Emote, Skalierung 14sp); `ChatMessage.inlineEmotes`-Feld ergänzt (Default `emptyList()`); 8 neue `InlineEmoteTest` + 7 neue `ChatOverlayParsingTest` — Gesamtsuite feature-chat grün, Lint grün |
| 2026-08-18 | `cfb14ef` | **Post-Beta-Roadmap-Bucket „Multi-Plattform-Chat (Kick, YouTube, SOOP)“ skizziert** (Chat & Moderation, unter Zeile 77): Referenz aus Moblin (Chat für alle 4 Plattformen) gegen den Ist-Zustand in Vivid (Twitch ✅ Twitch-Scope, `ChatMessage` plattformneutral — Adapter docken ohne Umbau an Overlay/Bot); **Adapter-Interface-Zielbild** (`ChatReader`/`ChatSender`/`ChatSessionManager`, heute erfüllt durch `TwitchChatEventSubReader`/`TwitchSendChatClient`), Modul-Zuordnung (`feature-chat`/`feature-settings`/`core`/`app`), 8 priorisierte offene Tasks (Interface-Refactoring → YouTube innertube mit anonymem Lesen → Kick Pusher-WebSocket + GraphQL → SOOP → OAuth-PKCE → parallele Sessions beim Multi-Streaming → Settings → Contract-Tests); Abgrenzung zu Emotes-/Moderation-Zeilen und Twitch-OAuth-Scope-Überschneidung dokumentiert |
| 2026-08-18 | `e34656b` | **Chat-Integration formal geschlossen (README-Abgleich):** README „In Progress“ → „✅ Implemented“ mit Twitch-Scope-Notiz (EventSub/Helix, kein IRC; Kick/YouTube/SOOP/OAuth = Post-Beta-Roadmap, konsistent zu Zeile 77); Overlay-Bullet auf EventSub-Lesen korrigiert (nicht mehr „anonym“); **I18n-Plan angelegt** ([docs/i18n-plan.md](docs/i18n-plan.md)): Ist-Messung ≈74 hartkodierte Literale in 4 Modulen, Modul-für-Modul-Vorgehen, Sprachstrategie (de-Default + en-Pflicht + fr-bestehend), Nicht-Lokalisierungs-Liste (Bot-Texte), CI-Gates; **Zähler-Korrektur:** Zusatz-Features 1/2/1 → 1/1/1 (Gesamt 19/5/21/45) — der Theme-Commit hatte 🚧 doppelt gezählt |
| 2026-08-18 | `a3e2bba` | **Roadmap-Bucket „Color-Spaces + 3D-LUTs“ skizziert** (Kamera & Video, Zeile 99): Referenz aus Moblin (sRGB/P3 D65/Apple Log geräteabhängig, PNG-3D-LUTs nach Hald-CLUT, Settings → Scenes → Graphics) gegen den Ist-Zustand in Vivid (plain `SurfaceView` über RootEncoder-GL, keine eigene Shader-Stufe; Integrationspunkt = RootEncoder `setFilter`/`GlFilter`) — Modul-Zuordnung (`feature-streaming`/`core`/`feature-settings`/`app`) + 7 priorisierte offene Tasks (PoC → Farbraum → LUT-Engine → Bundled/Import → Settings-UI → Performanz → Tests); Abgrenzung zum UI-Farbschemata-Zusatz dokumentiert |
| 2026-08-18 | `6a8c6d4` | **UI-Farbschemata (Vivid-Zusatz, Stufe 1):** Vivid-Grün-Palette (Material-3, Seed `#3DDC84`) ersetzt die Template-Farben (Purple80/Pink80) und `VividTheme` folgt jetzt dem System (`isSystemInDarkTheme()`) statt hartkodiert Light — `app/src/main/java/com/vivid/irlbroadcaster/ui/theme/Theme.kt`; Paletten intern testbar gemacht, `VividThemeTest` (5 Tests: Light/Dark-Palette, Seed-Akzent, System-Dark-Wahl) — App-Suite grün. Stufe 2 offen: User-Toggle (System/Hell/Dunkel/AMOLED) + Akzentfarbe als Settings-Kategorie „Darstellung“ |
| 2026-08-16 | `d541780` | **Chat-Bot: Koexistenz-Modus** (Vivid-Zusatz, läuft neben dem Bot eines anderen Tools wie Rivulet ohne Kollisionen): `ChatBotCommandScope`-Enum (ALL = jeder `!`-Befehl · MENTION = nur @-Erwähnung · PREFIX = nur eigenes Präfix `!v!help`) in `AppSettings`/`SettingsRepository` (`chat_bot_command_scope`/`chat_bot_command_prefix`/`chat_bot_ignore_bots`), `BotCommandProcessor` beachtet den Scope (fremde Befehle → `None` statt „Unbekannter Befehl“), `ChatBotEngine` ignoriert Nachrichten aus der Ignore-Liste komplett, `ChatTtsController` liest ignorierte Bots nicht vor; Settings-Screen: Abschnitt „Koexistenz mit anderen Bots“ (Ignore-Feld, Scope-SegmentedButton, Präfix-Feld); 8 neue CommandProcessor-Scope-Tests + 3 Engine-Ignore-Tests + 2 Engine-Scope-Tests + 1 TTS-Ignore-Test + Repository-/ViewModel-Tests erweitert — Gesamtsuite + Lint (feature-chat/core/feature-settings/app) + App-Compile grün |
| 2026-08-16 | `121d8b2` | **Chat-Bot: Media-Player-Steuerung** (Moblin-Parität Row 80 → ✅): `ChatMediaPlayer`-Interface + `ChatMediaController` (steuert den aktiven Media-Player über `MediaSessionManager.getActiveSessions` → `MediaController.TransportControls`, bevorzugt aktive Sessions), `MediaNotificationListener` (leerer NotificationListenerService als Zugriffs-Marker — Voraussetzung Benachrichtigungszugriff), Manifest-Service in der App, DI-Binding; Bot-Befehle `!song`/`!nowplaying`/`!np`, `!next`/`!skip`, `!pause`, `!play`, `!prev`/`!previous` im `BotCommandProcessor` + Engine-Handling mit Zugriffs-Hinweis („Kein Media-Zugriff …“); Settings-Screen: Hinweis + Button „Benachrichtigungszugriff aktivieren“ (öffnet `ACTION_NOTIFICATION_LISTENER_SETTINGS`); 6 neue CommandProcessor-Tests + 8 neue Engine-Media-Tests — Gesamtsuite + Lint (feature-chat/app/feature-settings) + App-Compile grün |
| 2026-08-16 | `121d8b2` | **Chat-Bot-Settings-Screen vervollständigt:** alle restlichen Bot-Felder im Settings-Screen (Abschnitt „Chat-Bot (KI)“): Bot-Login, Twitch-OAuth-Token und LLM-API-Key als `SecretField` (Passwort-Maske + Sichtbarkeits-Toggle), LLM-API-Basis-URL, LLM-Modell, System-Prompt (mehrzeilig), Antwort-Cooldown (Sekunden, numerisch), Mentions-only-Switch, Max. Antworten/min (numerisch); neue ViewModel-Handler mit robustem Int/Long-Parsing (ungültig → 0 = aus/unbegrenzt), Persistenz über den bestehenden `updateChatBotSettings`-Aufruf; 3 neue ViewModel-Tests (komplette Konfiguration, UI-State aller Felder, ungültige Zahlen) — Gesamtsuite + Lint + App-Compile grün |
| 2026-08-16 | `121d8b2` | **Chat-TTS-Befehl `!tts`** ergänzt (Moblin-Bot-Parität, Row 79): `ChatTtsSpeaker`-Interface + `AndroidTtsSpeaker` (systemeigene TextToSpeech-Engine, keine Runtime-Berechtigung), `ChatTtsController` (enabled-StateFlow, liest Viewer-Nachrichten aus dem Bot-Flow vor — überspringt eigene Bot-Nachrichten und `!`-Befehle, 200-Zeichen-Cap, Zustand überlebt Stream-Ende), `!tts`-Command im `BotCommandProcessor` (`ToggleTts`-Result), Engine-Toggle mit Chat-Bestätigung („TTS ist jetzt AN/AUS“, beide Modi), Wiring im `ChatBotController` + Hilt-Binding; 9 neue ChatTtsController-Tests + 3 CommandProcessor-Tests + 2 Engine-Toggle-Tests — Gesamtsuite feature-chat (76) grün, Lint + App-Compile grün |
| 2026-08-16 | `0037e2a` | **Chat-Bot-Modus-Switch („Bot wie Moblin“ ↔ „KI autonom“)** ergänzt: `ChatBotMode`-Enum (`COMMAND`/`AUTONOMOUS`) in `AppSettings`/`SettingsRepository` (`chat_bot_mode`-Key, Default AUTONOMOUS), `BotCommandProcessor` (deterministische `!`-Befehle `!help`/`!commands`, `!uptime`, `!bot` — COMMAND-Modus funktioniert **ohne LLM-Schlüssel**, `ChatBotConfig.isReady` mode-abhängig), `ChatBotEngine` (COMMAND: nur Befehle, keine LLM-Aufrufe; AUTONOMOUS: KI entscheidet selbst über jede freigegebene Nachricht, bewusstes Schweigen via `[keine Antwort]`-Marker, Autonomie-Prompt im System-Message; Cooldown + Rate-Limit gelten jetzt für **alle** Antworten inkl. Befehle; `!uptime` nutzt den Stream-Start-Zeitstempel aus `ChatBotController`), Modus-Switch (SegmentedButton) + Aktivieren-Toggle im Settings-Screen mit Persistenz über den Speichern-Button; 12 neue Unit-Tests (BotCommandProcessor) + 8 neue Engine-Modi-Tests + 2 Repository-Tests erweitert — Gesamtsuite feature-chat (62) grün, Lint + App-Compile grün. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |
| 2026-08-16 | `0eb100d` | **KI-Chat-Bot (Vivid-Zusatz-Feature) implementiert:** `OpenAiCompatibleLlmClient` (OpenAI-kompatibles `/v1/chat/completions`, Bearer-Auth, `LlmConfig` pro Aufruf), `TwitchBotClient` (authentifizierte Twitch-Verbindung, `PASS oauth:<token>`, `PRIVMSG`-Senden, PING→PONG, Reconnect), `ChatBotEngine` (nur-Erwähnung-Modus, Cooldown, Rate-Limit, 500-Zeichen-Limit, Prompt-History, ChatBotState), `ChatBotController` + `StreamingService`-Wiring (Auto-Connect bei Go-Live, sauberer Shutdown bei Streamende, Stop bei Deaktivierung); Bot-Settings in `SettingsRepository`/`AppSettings` (Kanal, Login, OAuth-Token, LLM-Endpunkt/-Key/-Modell, System-Prompt, Cooldown, Mentions-Only, Rate-Limit); 22 neue Unit-Tests, Lint + App-Compile grün. Anleitung: [docs/ai-chat-bot.md](docs/ai-chat-bot.md) |
| 2026-08-15 | `f705896` | **Beta-Gate-Analyse:** 16/17 ✅ erreicht; offene Gate-Bedingungen: ≥1 Widget (`feature-widgets` ist noch Platzhalter) + Chat-✅ formal (Twitch-IRC + Overlay laufen, 25 Tests). Empfehlung: erstes Widget (Text-/Info-Widgets) als 17. ✅ — Plan für den ersten Beta-Build in [RELEASE.md](RELEASE.md#-erster-beta-build-plan) |
| 2026-08-14 | `ab3f73e` | **Plattform-Chat (Twitch) begonnen:** Data-Layer des `feature-chat`-Moduls implementiert — `TwitchChatClient` (anonym/justinfan, TLS 6697, CAP tags/commands/membership, JOIN, PING→PONG, Auto-Reconnect mit Backoff, `state`-StateFlow + `messages`-Flow), `SocketIrcConnection` (Socket/TLS mit `IrcConnectionFactory` für Tests), IRCv3-Tags-Parser (`IrcMessageParser`, inkl. Escaping) und `ChatMessage`-Modell; Hilt-DI (`@ChatScope`); 14 Unit-Tests grün, Lint + App-Compile grün. Nächste Schritte: Kick/YouTube-Adapter, OAuth-Login, UI/Overlay |
| 2026-08-14 | `a49a3b2` | **Chat-Overlay (Twitch) fertig:** Kanal + Overlay-Toggle in den Settings (`chat_channel`/`chat_overlay_enabled` in `SettingsRepository`/`AppSettings`, gespeichert über `updateChatSettings`, Sektion „Chat-Overlay“ im Settings-Screen); `ChatOverlayViewModel` (startet/stoppt den `TwitchChatClient` anhand der Settings, normalisiert den Kanal, begrenzt auf 50 Nachrichten, leert bei Kanal-/Statuswechsel, reicht den Verbindungsstatus durch) + `ChatOverlay`-Composable (transparente Box unten links über der Vorschau, zeigt die letzten 6 Nachrichten mit Username-Farbe, blendet sich bei deaktiviertem Overlay aus); Overlay im StreamingScreen eingehängt (feature-streaming hängt jetzt an feature-chat); 3 Repository-Tests + 8 VM-Tests grün, Lint (warningsAsErrors) + App-Compile grün. **Offen:** Event-Alerts (Follow/Sub/Raid), Badges/Emotes, Kick/YouTube/SOOP, OAuth |
| 2026-08-14 | `8af67fb` | **RootEncoder auf 2.7.5 aktualisiert** (von 2.6.4): RTMP-Fixes (`onFail` bei fehlgeschlagenem Publish, Crash-Fix bei ungültiger URL, Handshake-Flags), SRT-Ack/Nak/Handshake-Fixes, Ktor-TLS-Fehlerbehandlung + `setTlsHostVerification`, „Java sockets by default"; API kompatibel verifiziert (Context-Konstruktoren `RtmpCamera2`/`MultiCamera2`, `Camera2Base`-Camera-Controls, RTMPS-Parsing via `RootEncoderRtmpsSupportTest`) — alle Tests grün |
| 2026-08-14 | `4628805` | **Tap-to-Focus, Pinch-Zoom und Video-Stabilisierung** für die Streaming-Kamera implementiert: `CameraControls`-Vertrag + `RootEncoderCameraControls` (Adapter über RootEncoder `Camera2Base`, wandelt `android.util.Range` → `ZoomRange`), `StreamingPreviewGestures` (Tipp → Tap-to-Focus, Doppeltipp → Zoom-Reset, Pinch → Zoom), `ZoomCalculator` (Clamping auf den Kamera-Zoombereich), `CameraStabilizationController` (OIS bevorzugt, sonst EIS) mit `stabilizationEnabled`-StateFlow und Toggle im StreamingScreen; Engine-API `zoomBy`/`resetZoom`/`tapToFocus`/`toggleStabilization`; Unit-Tests (ZoomCalculator, Controller, Adapter, Engine) |
| 2026-08-13 | `1c36b16` | **Multi-Streaming (bis zu 2 parallele RTMP(S)-Ziele)** implementiert: RootEncoder `MultiCamera2` in der `StreamingEngine` (`CameraFactory.create(List<ConnectChecker>)`, per-Ziel-ConnectChecker, `targetStates`-StateFlow), Status je Ziel im StreamingScreen, ein Fehlerziel stoppt nur sich selbst; sekundäre URL/Key/TLS in den Settings („Multi-Streaming (optional)“), Validator + Service-Plumbing (`EXTRA_STREAM_URLS`); Unit-Tests in allen betroffenen Modulen |
| 2026-08-13 | `de31b83` | **SDK-Umstellung auf Android 17 (API 37):** `compileSdk`/`targetSdk` 37, `minSdk` 24 unverändert; `ACCESS_LOCAL_NETWORK` deklariert + Runtime-Permission-Flow im Settings-Screen („LAN-Zugriff für Remote-Control erlauben“, Server-Neustart nach Erteilung) für die Web-Remote-Control |
| 2026-08-13 | `1fb17d4` | **OBS-Konfiguration per QR-Code importieren** umgesetzt: `ObsQrCodeParser` (Formate `obsws://` inkl. percent-decoded Passwort, `obswebsocket://`, `obswebsocket|[host]:[port]|[pw]`), Import-Feld im OBS-Settings-Screen, 11 Parser- + 4 ViewModel-Tests |
| 2026-08-13 | `78728d8` | **Fokus-Lock (∞)** / Autofokus-Toggle für die Streaming-Kamera implementiert ([#377](https://github.com/eerimoq/moblin/issues/377)): `CameraFocusController` + `FocusableCamera` (RootEncoder `disableAutoFocus()`/`setFocusDistance(0f)`), `focusMode`-StateFlow in `StreamingEngine`, Toggle im StreamingScreen, Unit-Tests |
| 2026-08-13 | `5d9cb4a` | Zusatz-Feature (über Moblin-Parität hinaus): Oura-Ring-Gesundheitsdaten im Widget (Oura-Cloud-API, OAuth2; keine BLE-Schnittstelle) ergänzt |
| 2026-08-13 | `5d9cb4a` | Konkrete Tasks ergänzt: Focus-Lock/manueller Fokus ([#377](https://github.com/eerimoq/moblin/issues/377)) bei Tap-to-Focus, Golf-Scoreboard ([#326](https://github.com/eerimoq/moblin/issues/326)) bei Scoreboards |
| 2026-08-13 | `5d9cb4a` | Community-Feature-Requests aus dem Moblin-Tracker ergänzt: RTMP-Pull/Ingest ([#407](https://github.com/eerimoq/moblin/issues/407)), Text-Widget-Variablen GPS/`{road}` ([#360](https://github.com/eerimoq/moblin/issues/360) / [#384](https://github.com/eerimoq/moblin/issues/384)) |
| 2026-08-13 | `58febc7` | Moblin **33.12.0** (2026-07-24) nachgetragen: Chat-Bot-Media-Steuerung (Android-Adaption der Apple-Music-Steuerung via MediaSession), Photo-Shoot-Quick-Button, Höhenmeter (Anstieg/Abstieg) im Text-Widget, Talkback-Mic im Remote-Control |
| 2026-08-12 | `c5a71fe` | Foreground-Service für Hintergrund-Streaming (Notification, WakeLock, Runtime-Permissions) implementiert |
| 2026-08-12 | `d4ba87a` | RTMPS (TLS-Ingest) verifiziert: RootEncoder 2.6.4 kann nativ TLS; Port-Normalisierung 1935→443 + Beweis-Test |
| 2026-08-12 | `8a65a88` | Go-Live-Selbst-Check (Stream-Config-Validierung, blockierende Fehler + Warnungen) ergänzt |
| 2026-08-11 | `5abb11d` | Web-Remote-Control (LAN-Server mit Token-Auth, Status/Start/Stop) ergänzt |
| 2026-08-11 | `c4857e7` | About-Screen (In-App-Version + Update-Check gegen GitHub-Releases) ergänzt |
| 2026-08-11 | `8110b59` | Offene Tasks für OBS- & Audio-Troubleshooting ergänzt (README-FAQ) |
| 2026-08-08 | `af7169f` | Erstversion erstellt (Stand nach `4d8362e`) |

---

## 🔍 Zuordnung der Log-Commit-Hashes (Rekonstruktion)

Die Commit-Spalte wurde am 2026-08-20 **rückwirkend aus der Git-Historie
rekonstruiert** (vorher Platzhalter “—”). Methode: für jeden Eintrag
`git log -S "<Eintrag>" -- PARITY.md` — der Hash ist der Commit, der den
Eintrag **tatsächlich eingebracht** hat (per Diff verifiziert). Bei
Einträgen, deren Phrase in mehreren Commits vorkommt (Status-Zeilen,
Roadmap-Buckets, spätere Nennungen), wurde per Diff geprüft, welcher
Commit die Log-Zeile hinzufügte:

| Datum | Commit | Eintrag | Kandidaten (Phrase in PARITY.md) | Vermerk |
|-------|--------|---------|----------------------------------|---------|
| 2026-08-17 | `a9171cb` | Chat-Formalisierung | `a9171cb`, `f705896` | `f705896` (Beta-Gate-Status) nennt die Formalisierung nur im Status; `a9171cb` fügt die Log-Zeile hinzu |
| 2026-08-17 | `cdb3d8c` | Text-/Info-Widget | `a9171cb`, `f705896`, `5d9cb4a`, `af7169f` | nur `cdb3d8c` fügt die Log-Zeile hinzu; die anderen erwähnen das Widget nur (Status/Log-Text) |
| 2026-08-20 | `5957f82` | UI-Farbschemata Stufe 2 | `5957f82`, `a3e2bba`, `6a8c6d4` | Stufe 2: `5957f82`; Stufe 1 (`6a8c6d4`) ist eigener Eintrag, `a3e2bba` nennt die Phrase nur |
| 2026-08-18 | `6a8c6d4` | UI-Farbschemata Stufe 1 | `5957f82`, `a3e2bba`, `6a8c6d4` | s. o. |
| 2026-08-19 | `a3af8a5` | Chat-Overlay Inline-Emotes | `a3af8a5`, `cfb14ef`, `f705896`, `a49a3b2`, `af7169f` | nur `a3af8a5` fügt die Zeile hinzu; die anderen nennen “Chat-Overlay” in Status-/Log-Zeilen |
| 2026-08-14 | `a49a3b2` | Chat-Overlay (Twitch) fertig | `a3af8a5`, `cfb14ef`, `f705896`, `a49a3b2`, `af7169f` | s. o. (Einführung durch `a49a3b2`) |
| 2026-08-16 | `0eb100d` | KI-Chat-Bot implementiert | `a9171cb`, `9f2219c`, `0eb100d` | nur `0eb100d` fügt die Log-Zeile hinzu |
| 2026-08-14 | `ab3f73e` | Plattform-Chat (Twitch) begonnen | `cfb14ef`, `a9171cb`, `618dda8`, `af7169f` | Einführung durch `ab3f73e`; `618dda8` = spätere IRC-Referenz-Bereinigung |
| 2026-08-14 | `4628805` | Tap-to-Focus, Pinch-Zoom, Stabilisierung | `a49a3b2`, `4628805` | `a49a3b2` nennt die Phrase nur im Overlay-Log-Text; `4628805` fügt die Zeile hinzu |
| 2026-08-13 | `78728d8` | Fokus-Lock | `4628805`, `78728d8` | `4628805` erwähnt Fokus-Lock nur im Task-Text; `78728d8` fügt die Zeile hinzu |
| 2026-08-13 | `1c36b16` | Multi-Streaming | `cfb14ef`, `1c36b16`, `af7169f` | nur `1c36b16` fügt die Log-Zeile hinzu |
| 2026-08-13 | `1fb17d4` | OBS-QR-Code-Import | `1fb17d4`, `af7169f` | `af7169f` (Erstversion) hat nur die Status-Zeile, `1fb17d4` die Log-Zeile |
| 2026-08-13 | `5d9cb4a` | Oura-Ring-Gesundheitsdaten im Widget | `e34656b`, `a3e2bba`, `9f2219c`, `0eb100d`, `5d9cb4a` | die anderen nennen das Widget nur (Chat/Remote/Widget-Kontext); `5d9cb4a` fügt die Log-Zeile hinzu |
| 2026-08-12 | `c5a71fe` | Foreground-Service | `58febc7`, `c5a71fe` | `58febc7` nennt Hintergrund-Streaming nur im 33.12.0-Eintrag |
| 2026-08-12 | `8a65a88` | Go-Live-Selbst-Check | `d4ba87a`, `8a65a88` | `d4ba87a` erwähnt den Check nur in der RTMPS-Zeile |
| 2026-08-11 | `5abb11d` | Web-Remote-Control | `ab3f73e`, `618dda8`, `de31b83`, `8a65a88`, `5abb11d`, `af7169f` | nur `5abb11d` fügt die Zeile hinzu; die anderen nennen Remote-Control nur |
| 2026-08-11 | `c4857e7` | About-Screen | `5abb11d`, `c4857e7` | `5abb11d` nennt den About-Screen nur im Remote-Control-Eintrag |

**Eindeutig zugeordnet (23 Einträge, genau ein Kandidat):** `cee9141`,
`e5dab62`, `e4674f5` (3×), `6bd4833`, `cfb14ef`, `e34656b`, `a3e2bba`,
`d541780`, `121d8b2` (3×), `0037e2a`, `f705896`, `8af67fb`, `de31b83`,
`5d9cb4a` (2×), `58febc7`, `d4ba87a`, `8110b59`, `af7169f`.

---

---

*Siehe auch: [README – Parity Status](README.md#-parity-status) · [Moblin (Original)](https://github.com/eerimoq/moblin)*
