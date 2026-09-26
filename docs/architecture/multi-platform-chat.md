# 💬 Architektur: Merged Multi-Plattform-Chat (Twitch + YouTube + Kick)

> **Status:** Skizze festgelegt; **P0–P2 implementiert (P2: Kick-Lese-Adapter, 2026-09-26); P0 implementiert (2026-09-21, verhaltensneutral — `ChatPlatform`, `ChatMessage.platform`, `session/ChatSession.kt` mit `ChatReader`/`ChatSender`/`ChatSessionConfig.Twitch`, Twitch-Reader/Sender implementieren die Interfaces, Bestands-Suiten unverändert grün)** · **P3-Vorgriff implementiert (2026-09-21, verhaltensneutral — `ChatSessionManager` mit Always-Restart-`setSessions`/`stopAll`/`sendToOrigin` + TWITCH-Multibinding; der `ChatBotController` startet Twitch über den Manager statt direkt)** · **Datum:** 2026-09-21
> **Tracked in:** [PARITY.md, Bucket „Multi-Plattform-Chat"](../../PARITY.md) · **Referenz:** Moblin (Chat für 4 Plattformen)
> **Umfang dieser Skizze:** Lesen + Merged Overlay + Bot-Routing für Twitch, YouTube, Kick. SOOP bewusst zurückgestellt.

---

## 1. Ziel

Ein Chat-Overlay, das Nachrichten aus **mehreren Plattformen gleichzeitig** anzeigt, während der Streamer
multi-streamt (Twitch + YouTube + Kick via RootEncoder). Kernanforderungen:

1. **Ein Overlay, mehrere Quellen** — Nachrichten aller aktiven Plattformen in einem Stream, mit sichtbarer Plattform-Herkunft.
2. **Ein Bot über allen Plattformen** — `ChatBotEngine`/`BotCommandProcessor` arbeiten unverändert auf einem Nachrichten-Flow; Antworten gehen auf die Plattform der auslösenden Nachricht zurück.
3. **Kein Umbau der Bestandskomponenten** — Overlay-VM, Bot-Engine, Alerts und TTS lesen weiterhin `Flow<ChatMessage>`; die Multi-Plattform-Fähigkeit kommt aus einer neuen Schicht darunter.

## 2. Ist-Zustand (verifiziert, Stand dieser Skizze)

| Baustein | Heute | Multi-Plattform-tauglich? |
|---|---|---|
| `ChatMessage` (`model/`) | plattformneutral; `channel: String` vorhanden; nur `emotesTag` ist Twitch-spezifisch | ✅ fast — es fehlt nur ein `platform`-Feld |
| `ChatConnectionState` | `Disconnected / Connecting / Connected(channel)` | ✅ unverändert nutzbar |
| `TwitchChatEventSubReader` | `start(TwitchEventSubConfig)`, `stop()`, `state: StateFlow<ChatConnectionState>`, `messages: Flow<ChatMessage>`, `sharedChatState` | ✅ als Adapter verwendbar; Interface muss formal extrahiert werden |
| `TwitchSendChatClient` | `send(TwitchEventSubConfig, text): SendChatResult` | ✅ als Adapter verwendbar |
| `ChatOverlayViewModel.UiState` | `messages`, `connection`, `alerts` … | ⚠️ `connection` ist Singular — wird zur Map je Plattform |
| DI (`ChatFeatureModule`) | Hilt `SingletonComponent`, `@ChatScope`, OptionalBinding-Muster vorhanden | ✅ Multibinding-Muster passt ins Haus |
| Bot-Engine | arbeitet auf `Flow<ChatMessage>` + `ChatSender` | ✅ kein Umbau, nur Routing-Erweiterung |

## 3. Schichtenarchitektur

```
┌────────────────────────────────────────────────────────────────────┐
│ L5  Settings/OAuth: Plattform-Enable + Kanal je Plattform,         │
│     OAuth-Browser-Flow (PKCE) für Senden                           │
├────────────────────────────────────────────────────────────────────┤
│ L4  Konsumenten (unverändert): ChatOverlay (Merged-Rendering),     │
│     ChatBotEngine, Alerts, TTS — lesen Flow<ChatMessage>           │
├────────────────────────────────────────────────────────────────────┤
│ L3  ChatSessionManager (neu): N parallele Sessions, Merge der      │
│     Messages-Flows, Aggregat-State, Routing für Sender             │
├────────────────────────────────────────────────────────────────────┤
│ L2  Adapter (1 pro Plattform): TwitchReader¹ / YouTubeReader /     │
│     KickReader + Sender-Je-Plattform                               │
├────────────────────────────────────────────────────────────────────┤
│ L1  Interfaces (neu, formal): ChatReader, ChatSender,              │
│     ChatSessionConfig (sealed)                                     │
├────────────────────────────────────────────────────────────────────┤
│ L0  Modell (fast fertig): ChatMessage +platform,                   │
│     ChatConnectionState, ChatAlert                                 │
└────────────────────────────────────────────────────────────────────┘
    ¹ Twitch-Adapter = extrahiertes Interface um den bestehenden
      TwitchChatEventSubReader (verhaltensneutral, Bestandstests als Netz)
```

## 4. Kernentscheidungen (L0–L2)

### 4.1 `ChatPlatform` + `ChatMessage.platform` (L0)

```kotlin
enum class ChatPlatform(val id: String) { TWITCH("twitch"), YOUTUBE("youtube"), KICK("kick") }

// ChatMessage ergänzt (Default hält alle Bestandstests grün):
val platform: ChatPlatform = ChatPlatform.TWITCH
```

**Begründung:** `channel` existiert bereits; der Default `TWITCH` macht die Änderung nicht-brechend
(alle heute erzeugten Nachrichten sind Twitch). Der Bot kennt `platform` damit automatisch —
Per-Viewer-Limits (Cooldown/Cap) funktionieren weiter über `userId`, das je Plattform eindeutig ist
(Twitch-User-ID / YouTube-`channelId` / Kick-User-ID — dokumentiert in `ai-chat-bot.md`).

### 4.2 Interfaces (L1) — non-generic mit sealed Config

```kotlin
interface ChatReader {
    val messages: Flow<ChatMessage>          // Messages mit gesetztem platform
    val alerts: Flow<ChatAlert> get() = emptyFlow()  // Default: Plattform ohne Alert-Quelle (P6)
    val state: StateFlow<ChatConnectionState>
    fun start(config: ChatSessionConfig)
    fun stop()
}

interface ChatSender {
    suspend fun send(config: ChatSessionConfig, text: String): ChatSendResult
}

sealed interface ChatSessionConfig {
    val platform: ChatPlatform
    val channel: String
    data class Twitch(val twitch: TwitchEventSubConfig, override val channel: String) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.TWITCH
    }
    data class Youtube(override val channel: String, val sendToken: GoogleToken?) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.YOUTUBE
    }
    data class Kick(override val channel: String, val sendToken: KickToken?) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.KICK
    }
}

sealed interface ChatSendResult { data object Sent; data class Dropped(val reason: String?); data class Failed(val cause: Throwable) }
```

**Begründung:** Generische Interfaces (`ChatReader<C>`) kollidieren mit Hilt-Multibinding und machen
den `ChatSessionManager` unlesbar. Ein sealed `ChatSessionConfig` hält die Plattform-Details typsicher
und lässt sich exhaustiv matchen. `ChatSendResult` verallgemeinert das `drop_reason`-Verhalten von Helix
(Twitch) als Vertrag für alle Sender.

**DI (Multibinding im Hausmuster):** `Map<ChatPlatform, @JvmSuppressWildcards ChatReader>` via
`@IntoMap` + `@ChatPlatformKey(ChatPlatform.X)`; der `ChatSessionManager` wählt per Enum. Sender
analog. Damit bleibt feature-chat selbst-contained (kein Bindungs-Zwang, Twitch funktioniert solo).

### 4.3 `ChatSessionManager` (L3) — eine Session pro Plattform

```kotlin
@Singleton
class ChatSessionManager @Inject constructor(
    private val readers: Map<ChatPlatform, @JvmSuppressWildcards ChatReader>,
    private val senders: Map<ChatPlatform, @JvmSuppressWildcards ChatSender>,
    @ChatScope scope: CoroutineScope,
) {
    val messages: Flow<ChatMessage>            // merge() aller aktiven Sessions
    val alerts: Flow<ChatAlert>                // merge() der Alerts (ab P6 mehr als Twitch)
    val states: StateFlow<Map<ChatPlatform, ChatConnectionState>>
    val activePlatforms: StateFlow<Set<ChatPlatform>>
    suspend fun setSessions(configs: List<ChatSessionConfig>)  // Diff: Start/Stop je Delta
    suspend fun send(text: String, target: ChatPlatform)
    suspend fun sendToOrigin(text: String, origin: ChatMessage) // Bot-Default
}
```

- **Merge:** `merge()` der Sessions-Flows — keine Sortierung/Interleaving-Logik, das Overlay rendert
  im Eingangszeitpunkt (wie heute). Kein Dedup: Plattform-Identitäten sind nicht join-bar
  (YouTube-Account ≠ Twitch-Account) — **bewusste Nicht-Ziele** in §8.
- **State:** Aggregat-Map statt „irgendein“ Zustand; das Overlay zeigt je Plattform einen
  Verbindungs-Indikator (kleines Icon, dezent — Stil wie der Shared-Chat-Hinweis).
- **Neustart-Regel:** `setSessions()` ist deklarativ (Soll-Zustand); der Manager leitet Start/Stop ab.
  Ein Kanalwechsel auf einer Plattform = Stop+Start nur dieser Session (Twitch-Reconnect-Muster
  existiert bereits im Reader).
  **Implementiert (P3-Vorgriff):** Always-Restart-Vertrag — `setSessions` startet jede angegebene
  Session immer neu (identisch zum heutigen Bot-Start), entfernt gestoppte Plattformen aus der
  Registry; ein equality-basierter No-op-Re-Start für den Settings-Loop folgt mit P3.

## 5. Transporte je Plattform (L2)

| | Twitch (ist) | YouTube | Kick |
|---|---|---|---|
| **Lesen** | EventSub-WebSocket `channel.chat.message` (Scope `user:read:chat`) | **innertube-Polling**: `live_chat_id` aus der Stream-Seite, `continuation`-Token, ~2–5 s Intervall — **anonym ohne Login** (unofficial) | **Pusher-WebSocket** (public), Kanal-Subscription; Ereignis `App\\Events\\ChatMessage` — **anonym ohne Login** |
| **Senden** | Helix `POST /helix/chat/messages` (Scope `user:write:chat`) — vorhanden | Google-OAuth (`youtube.force-ssl`), Data-API `liveChatMessages.insert` | Offizielle **OAuth 2.1 API** (`api.kick.com`) — bevorzugt; Community-GraphQL (`SendChatMessage`) nur als Fallback |
| **Verbindungsmodell** | WebSocket + Reconnect/Backoff (existiert) | HTTP-Polling; Backoff bei Quota/4xx; „Stream endet“ = continuation tot → Disconnected | Pusher-Protokoll: `pusher:connection_established`, Subscription-Ack, Heartbeat (activity timeout) |
| **Risiko** | niedrig (offiziell) | **unofficial** — innertube kann sich jederzeit ändern (§8) | Lesen stabil verbreitet; Senden offiziell seit OAuth 2.1 |

Konsequenz der Risiko-Tabelle: **Lesen zuerst** (P1/P2), Senden je Plattform separat (P4) — dann
ist ein innertube-/Protokoll-Bruch nie ein Overlay-Ausfall, sondern maximal ein Send-Ausfall.

## 6. Konsumenten (L4)

- **Overlay:** `UiState` bekommt `connections: Map<ChatPlatform, ChatConnectionState>` (statt `connection`);
  Nachrichten rendert das Overlay mit **Plattform-Badge** (Icon/Ton je `ChatMessage.platform`), Position
  im Flow wie heute. Alerts bleiben bis Phase P6 Twitch-EventSub-getrieben; plattformübergreifende
  Alerts (YT-Superchat, Kick-Gifts) folgen als eigene Phase **P6** (Abschnitt 9.1).
- **Bot:** liest den Merged-Flow; **Antwort-Routing-Default:** Antwort auf der Plattform der
  auslösenden Nachricht (`sendToOrigin`). Broadcast („alle Plattformen“) wird nicht implizit gemacht —
  falls später gewünscht, als explizites Owner-Kommando/Setting (bewusst nicht in dieser Skizze).
- **TTS/Alerts:** unverändert — sie konsumieren nur `ChatMessage`.

## 7. Settings/OAuth (L5) + Modul-Zuordnung

| Modul | Neu |
|---|---|
| `feature-chat` | L1-Interfaces, L2-Adapter `youtube/` + `kick/` (Twitch bleibt unter `twitch/`), L3 `ChatSessionManager`, Multibindings in `ChatFeatureModule` |
| `feature-settings` | Chat-Sektion: je Plattform Enable-Toggle + Kanal-Feld (+ Send-Credentials-Status); Preset-Verkabelung in `SettingsViewModel` |
| `domain` | `AppSettings`-Felder `chat_<platform>_enabled/_channel` (DataStore, wie alle Chat-Settings) |
| `core` | Token-Store-Verallgemeinerung (bestehendes `TwitchTokenStore`-Muster: `TokenCipher` + DataStore, verschlüsselt, nie ins Log — Konvention `LogRedactor`) |
| `app` | OAuth-Browser-Flow (Custom-Tab, PKCE-State-Validierung) + Deep-Link-Route; Twitch zuerst, dann Google, dann Kick |

## 8. Risiken & Gegenmaßnahmen

| Risiko | Einordnung | Gegenmaßnahme |
|---|---|---|
| innertube-Änderung bricht YT-Lesen | **unofficial API** — Wahrscheinlichkeit real | Nur Lese-Pfad betroffen (Senden getrennt); Adapter isoliert + Contract-Tests mit echten Fixtures; „Degraded“-Zustand statt Crash |
| Kick-Pusher-Protokoll-Drift | niedrig–mittel | Pusher-Protokoll ist öffentlich stabil; Adapter-Contract-Tests; Version-Feld im Handshake tolerant parsen (`ignoreUnknownKeys` — Härtungs-Guard-Muster wie feature-chat) |
| YT-Polling-Drossel/-Quota | mittel | Intervall ≥ 2 s + adaptives Backoff; kein Senden ohne OAuth |
| F-Droid-Konformität | niedrig | Alles pure HTTP/WS (OkHttp/Ktor — im Stack); kein GMS |
| Identitäts-Verwirrung (gleicher Name auf 2 Plattformen) | UX | Plattform-Badge pro Nachricht; **kein** Cross-Platform-Joining (bewusst) |
| Send-Missbrauch/Rate-Limits | mittel | `ChatSendResult.Dropped` + Bot-Rate-Limits (existieren, pro `userId`) greifen je Plattform |
| Bestandsverhalten ändert sich | Regressionsrisiko | P0 ist verhaltensneutral; alle Bestandstests (Reader/VM/Engine 113+) müssen unverändert grün bleiben |

## 9. Phasenplan (jede Phase: Tests + Doku + Gate, Zwei-Commit-Muster)

| Phase | Inhalt | Proof |
|---|---|---|
| **P0** ✅ erledigt (2026-09-21) | L0-Modell (`platform`, Default TWITCH) + L1-Interface-Extraktion, Twitch-Reader/Sender unterordnen | feature-chat-Suite 425 grün (verhaltensneutral): Übergangs-Überladungen statt Signatur-Bruch, alle Bestands-Tests unverändert; `session/ChatSessionP0Test` (6 Verträge: Plattform-Default, Wire-IDs, SendResult-Mapping, Sealed-Grenze, Interface-Dispatch) |
| **P1** ✅ erledigt (2026-09-22) | **YouTube-Adapter (Lesen, anonym)** + Merge im Overlay (Twitch+YT) + Plattform-Badge + Settings (Enable/Kanal-ID, `youtubeChannelId`/`youtubeChatEnabled`) | Contract-Tests `YoutubeChatReaderTest` (10) mit realen innertube-Fixtures (ytInitialData-Bootstrap, get_live_chat-Seiten, Drift-/Offline-Fälle, Sealed-Verträge); feature-chat **447 grün**; Overlay-VM-Test (YouTube-Merge + Start/Stop am Setting); `ChatSessionConfig.Youtube` ergänzt, DI-Multibinding YOUTUBE (Reader + P4-Platzhalter-Sender). `ChatSessionManager`-Merge (P3-Zug) und `connections`-Map im Overlay folgen mit P3-Verkabelung |
| **P2** ✅ erledigt (2026-09-26) | **Kick-Adapter (Lesen, Pusher, anonym)** + Merge im Overlay (Twitch+YT+Kick) + Settings (Enable/Slug, `kickChannel`/`kickChatEnabled`) | Contract-Tests `KickChatReaderTest` (21) mit realen Pusher-Fixtures (Frames inkl. Objekt-`data`, Kanal-Auflösung, Mapping, ISO-Zeit, Ping/Pong am Fake-Socket, Drift-Fälle); feature-chat **471 Tests grün** (2 Bestands-Skips); `ChatSessionConfig.Kick` ergänzt, DI-Multibinding KICK (Reader + P4-Platzhalter-Sender); Plattform-Badge „K“ (grün) vorhanden seit P1 |
| **P3** 🔜 teilweise (P3-Vorgriff implementiert: Manager + Controller-Verkabelung verhaltensneutral; Settings-Diff + Equality-Reset offen) | Settings je Plattform (Enable/Kanal) + `setSessions`-Verkabelung + Bot-Routing (`sendToOrigin`) | Manager-Tests 7/7 grün (`ChatSessionManagerTest`: Always-Restart, Stop entfernter Sessions, Merge, States, Routing); VM-Tests (Settings-Diff → Start/Stop-Aufrufe) folgen mit P3 |
| **P4** | Senden: Twitch (ist), YouTube (Google-OAuth), Kick (OAuth 2.1) — `app`-OAuth-Flow + Token-Store | Sender-Contract-Tests (Fehlerpfade wie `drop_reason`), OAuth-State-PKCE-Tests |
| **P5** | (optional) SOOP-Adapter — nur falls Zielgruppe | — |
| **P6** | **Alerts plattformneutral (Abschnitt 9.1):** sealed `AlertDetail` (Twitch-Status quo verhaltensneutral), YT-Superchat (Ticker-Parser), Kick-Gifts (`SubscriptionEvent`) | Render-Tests je Qualifier (Muster `ChatAlertRowRenderRobolectricTest`); Contract-Tests YT-Ticker + Kick-Event; Bestands-Render-Tests bleiben grün |

**Empfohlener Start:** P0+P1 als erste Landung (größter Mehrwert: YT-Lesen anonym, Overlay merged).

### 9.1 Alert-Roadmap (P6): plattformneutrale Alert-Details (YT-Superchat, Kick-Gifts)

**Auslöser:** Mit P1/P2 kommen Nachrichten von YouTube/Kick ins Overlay — nicht aber deren
Monetarisierungs-Events. Beide Plattformen haben alert-würdige Events, aber andere Feldformen
als Twitch: YT-Superchat ist ein **Betrag + Währung**-Event (kein Sub-Zeitmodell), Kick-Gifts
sind **Zähler + Monate** (kein kumuliertes Gifter-Profil). Das heutige flache `AlertDetail`
(Twitch-Werte wie `tier = "1000"`) passt dafür nicht — es wird **sealed**.

**Modell-Entscheidung — `AlertDetail` wird sealed (L0):**

```kotlin
sealed interface AlertDetail {
    // Twitch-Status quo: exakt die heutigen Felder (tier "1000"/"2000"/"3000",
    // gifterName, viewerCount, count, cumulativeTotal, isAnonymous, months,
    // streakMonths, hypeTrain*) — verhaltensidentisch; die Render-Tests
    // (ChatAlertRowRenderRobolectricTest, 6 Typen inkl. Hype-Train-Ende und
    // anonymer Gifts) sind das Sicherheitsnetz für den Refactor.
    data class Twitch(...) : AlertDetail

    // YouTube: Super Chat / Super Sticker (Data-API-Felder, offiziell:
    // amountMicros, currency, amountDisplayString, tier 1–7, isSuperSticker).
    data class Superchat(
        val amountMicros: Long,      // amountMicros (API liefert String → Long)
        val displayAmount: String,   // amountDisplayString unverändert anzeigen
        val tier: Int,               // YT-Farbtier 1–7 (Farb-Mapping im Overlay)
        val message: String,
        val isSticker: Boolean,
    ) : AlertDetail

    // Kick: verschenkte Subs (Pusher-Event App\Events\SubscriptionEvent,
    // community-dokumentiert — feld-drift-anfällig).
    data class KickGift(
        val count: Int,
        val months: Int,
        val tier: String,
    ) : AlertDetail
}
```

- **`ChatAlertType` bleibt plattformneutral:** Kick-Gifts nutzen das bestehende `GIFT_SUB`,
  neu kommt `SUPERCHAT` hinzu. Farben mappt der `AlertRow` je Plattform (YT-Tier →
  Superchat-Farbpalette, Kick → eigene Gift-Farbe).
- **Konsequenz für L1 (schon eingezeichnet, Abschnitt 4.2):** `ChatReader` bekommt
  `val alerts: Flow<ChatAlert>` mit Interface-Default `emptyFlow()` von Anfang an und der
  `ChatSessionManager` merget Alerts analog zu `messages` — sonst bricht P6 das Interface
  nachträglich.
- **TTL/Regeln unverändert:** 10-s-TTL und max. 3 Banner gelten plattformübergreifend (VM-Logik);
  der Hype-Train-Stay-Banner bleibt Twitch-spezifisch (im Twitch-Detail verankert).
- **Lokalisierung:** `displayAmount` wird unverändert übernommen — **keine** Währungs-Mathematik
  oder Umrechnung im Client; neue Strings/Plurals (Superchat, Kick-Gift) in DE/EN/FR,
  Render-Tests je Qualifier.
- **Quellen/Parsing:** YT — innertube liefert Super Chats als **Ticker-Items**
  (`addLiveChatTickerItemAction`/`addChatTicketItemAction`), nicht als normale Chat-Aktionen →
  eigener Parser-Zweig im YT-Reader (anonym lesbar wie der Chat); Kick —
  `App\Events\SubscriptionEvent` mit Contract-Tests gegen echte Fixtures + `ignoreUnknownKeys`
  (Härtungs-Guard-Muster von feature-chat).
- **Reihenfolge innerhalb P6:** 1) sealed-Refactor verhaltensneutral (nur Twitch, alle
  Bestands-Tests grün), 2) YT-Superchat, 3) Kick-Gifts. Bewusst **nicht** in P6: SOOP-Alerts,
  **KICKs** (Kick-Geschenkwährung, neu seit 09/2026 — eigene Zeile, sobald das Protokoll
  dokumentiert ist), YT-Membership-Events (nur mit OAuth sichtbar, hängt an P4).

## 10. Offene Fragen (Entscheidungen während der Implementierung)

1. **Bot-Broadcast:** Soll ein Owner-Kommando (z. B. `!all <text>`) auf allen Plattformen antworten dürfen? (Skizze: nein, nur Origin — bewusst konservativ.)
2. **Kick-Senden:** Offizielle OAuth 2.1 ab P4 — akzeptieren wir den Custom-Tab-Flow dafür, oder bleibt Kick zunächst read-only? (Skizze: OAuth, aber P4-freiwillig.)
3. **Overlay-Größe bei 3 aktiven Plattformen:** aktuelle Nachrichten-Begrenzung (UiState) ggf. je Plattform fair-teilen statt global kappen.
4. **Superchat-Anzeige (P6):** nur `displayAmount` übernehmen (Skizze) oder zusätzlich Sortierung/Highlight nach Betrag? Das Overlay hebt Zahlungen heute nicht hervor — Konservativ-Default: reine Anzeige.

## 11. Verweise

- PARITY.md: Bucket „Multi-Plattform-Chat (Kick, YouTube, SOOP)“ (Adapter-Zielbild, Modul-Zuordnung, 8 Tasks — diese Skizze konkretisiert Tasks 1, 2, 3, 6, 7, 8)
- docs/ai-chat-bot.md (Bot-Architektur, plattformneutrale Limits), docs/architecture/overview.md (Modulstruktur)
- Konventionen: Token-Verschlüsselung (`TokenCipher`/`LogRedactor`), JSON-Härtung (`ignoreUnknownKeys` + Guard), Contract-Test-Muster (`TwitchChatEventSubReaderTest`)
