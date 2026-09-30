# 🎙️ WHIP-Spike: WebRTC-Ingest für Vivid

> **Status:** Spike-Analyse vom 2026-09-30 — Umsetzung: **nicht bewusst begonnen, hier wird nur analysiert**. Empfehlung: **GO** — Start mit dem 1–2-tägigen P0-Spike (Abschnitt 7), Gesamtumfang P0–P3 ≈ **8–13 Arbeitstage**. Diese Datei ist die Entscheidungsgrundlage für die PARITY-Zeile „WHIP (WebRTC)" im [Streaming-Bucket](../PARITY.md#-streaming--protokolle) (v0.6.0-beta-Roadmap-Bucket, blockiert durch den [Roadmap-Reservierungs-Guard](../scripts/test_roadmap_reservation.sh), solange das Bucket nicht komplett ✅ ist).

---

## 1. Fazit vorab

| Untersuchungsfrage | Ergebnis |
|---|---|
| SDK verfügbar & gepflegt? | ✅ `io.github.webrtc-sdk:android:150.7871.01` (Maven Central, veröffentlicht 31.08.2026); vier Meilenstein-Generationen in 17 Monaten (125 im März 2025 → 137 → 144 → 150) — aktive Pflege |
| API-Integration aus Kotlin? | ✅ Reines Java-API (`org.webrtc.*`), keine Kopplung an die Kotlin-Compiler-Version; Callback-Stil (`SdpObserver`) wird mit dünnen suspend-Wrappern geglättet |
| APK-/AAB-Größe? | ⚠️ AAR 49,1 MB (4 ABIs); App-Bundle liefert je Gerät nur eine ABI → **+12,3 MB auf arm64**; Universal-APK (F-Droid baut bislang universell) → **+49 MB** — ein bewusster Entscheidungspunkt (Abschnitt 4.2) |
| minSdk? | ✅ AAR-Manifest verlangt `minSdkVersion 21` — Vivid minSdk 24, kein Konflikt |
| CodeQL-Verträglichkeit? | ✅ unkritisch: AAR = fertiges Bytecode + Native-Binary, kein Extractor-Input, kein neuer Build-Schritt; die Kotlin-2.4.20-Bundle-Blockade ([github/codeql#22404](https://github.com/github/codeql/issues/22404)) ist unberührt (Abschnitt 4.5) |
| FOSS/F-Droid? | ✅ mit Fußnote: Lizenz BSD-3 (POM) bzw. MIT (Repo-LICENSE), Maven Central allowlisted, keine Closed-Source-Pflicht; der Vorbehalt „nicht aus Repo-Quellen gebaut" wird dokumentiert (Abschnitt 4.3) |
| Signalisierung ohne SDK? | ✅ WHIP ist reines HTTP (POST/PATCH/DELETE) — der im `core` bereits etablierte Ktor-CIO-Stack (OBS-Muster, #226) reicht vollständig; das WebRTC-SDK wird **nur** für die Media-Pipeline gebraucht |
| Größtes technisches Risiko | RootEncoder besitzt die Kamera-/GL-Pipeline exklusiv — WHIP-MVP deshalb als **eigenes Einzel-Ziel** (kein paralleles RTMP+WHIP im ersten Schritt), Frame-Relay als spätere Erweiterung (Abschnitt 6) |

**Empfehlung:** WHIP ist als erstes v0.6.0-beta-Bucket-Item umsetzbar. Kein Blocker gefunden; die drei echten Kosten sind APK-Größe (F-Droid), die Single-Target-Einschränkung des MVP und die Abhängigkeit von einem Third-Party-Prebuild. Alle drei sind beherrschbar und in Abschnitt 8 mit Gegenmaßnahmen bewertet.

---

## 2. Hintergrund & Zielbild

Moblin unterstützt WHIP-Ingest (Neben SRT/RTMP/RIST) — Vivid trackt die Parität-Zeile „WHIP (WebRTC)" (📋, Modul `core`). WHIP (WebRTC-HTTP Ingestion Protocol, **RFC 9725**, veröffentlicht 2025) ersetzt die signaling-lastigen WebRTC-Workflows durch einen dünnen HTTP-Contract:

- **Latenz:** sub-sekündige bis ~2 s End-to-End-Latenz (typisch 0,5–2 s) gegenüber RTMP (3–10+ s) — für IRL-Interaktion (Chat-Reaktionen, Remote-Gäste, Event-Rückkanäle) der entscheidende Unterschied.
- **Ziellandschaft (real verfügbar 2026):** Cloudflare Stream (WHIP-Ingest), Owncast, MediaMTX, OBS Studio (WHIP-Server seit 30.x), Restream & diverse Cloud-Plattformen; YouTube hat WHIP-Ingest beta-seitig angeboten. Twitch Enhanced Broadcasting nutzt WHIP-basierte Technologien (dort aber mit proprietärer Gegenstelle — bewusst nicht als MVP-Ziel versprochen).
- **Abgrenzung WHEP:** WHEP (Wiedergabe/Pull) ist ein separates Spezifikations-Tracking und **nicht** Teil dieses Spikes — möglicher später Mehrwert (z. B. externer Monitor/Pull-Preview) wird dokumentiert, nicht gebaut.

Zielbild in Vivid: Ein WHIP-Ziel (Endpoint-URL + optionaler Bearer-Token) ist im Streaming-Screen wie RTMP/SRT wählbar; der Go-Live-Self-Check validiert die URL; der Foreground-Service hält die Session; der Ziel-Status zeigt „Verbinde…/Live/fehlgeschlagen" wie bei den bestehenden Zielen.

---

## 3. WHIP-Protokoll (RFC 9725) — was der Client leisten muss

Der komplette Signalisierungs-Contract aus Client-Sicht (Quelle: [RFC 9725](https://www.rfc-editor.org/info/rfc9725/)):

| # | Schritt | Detail | Pflicht? |
|---|---------|--------|----------|
| 1 | `POST` SDP-Offer an Endpoint-URL | `Content-Type: application/sdp`; Antwort `201 Created` + `Location`-Header (**Session-URI**, Basis für alle Folgerequests) + Body = SDP-Answer (`application/sdp`, Server dürfen `text/plain` senden — Client akzeptiert beides) | ✅ Kern |
| 2 | `DELETE` auf Session-URI | Beendet die Session sauber (`200`); Server-seitiges Timeout (fehlende Medien) bleibt möglich → Client behandelt 404 tolerant | ✅ Kern |
| 3 | `PATCH` auf Session-URI (Trickle-ICE) | Body `application/trickle-ice-sdpfrag`, optimistische Nebenläufigkeit über `ETag` (aus der POST-Antwort) + `If-Match`; `412 Precondition Failed` bei Konflikt | ✅ wenn Trickle-ICE genutzt wird |
| 4 | Redirects | `307/308` auf dem POST **müssen** gefolgt werden (methode-erhaltend); für PATCH/DELETE nicht erforderlich | ✅ |
| 5 | Auth | `Authorization: Bearer <token>` (optional, server-abhängig); `401/403` verständlich melden | ⬜ optional |
| 6 | ICE-Server-Konfiguration | Server kann STUN/TURN via `Link`-Header (`rel="ice-server"`) anbieten — Client-seitig optional auswerten; ohne TURN scheitern symmetrische NAT-Fälle (server-abhängig) | ⬜ optional |
| 7 | Rate-Limit | `429` mit `Retry-After` respektieren, nicht sofort retrien | ✅ Robustheit |

**Konsequenz für die Architektur:** Die Schritte 1–7 sind **reines HTTP** — genau das Muster, das Vivid mit dem OBS-WebSocket-Client (Ktor CIO, injizierter `HttpClient`, StateFlows, fehlertoleranter Vertrag) bereits zweimal beherrscht (OBS-Steuerung, Twitch-EventSub). Das WebRTC-SDK kommt ausschließlich für Schritt „Media": `PeerConnection` mit sendonly-Transceivern, SDP-Offer erzeugen, Answer anwenden, Kandidaten senden, Hardware-Encoder nutzen.

**Vanilla-ICE-Vereinfachung:** Ein Client darf die Kandidatensammlung abwarten („gathering complete") und die vollständige SDP in Schritt 1 senden — dann entfällt PATCH zunächst ganz. Real-world-Endpoints (Cloudflare, OBS-Server) arbeiten aber mit Trickle-ICE + PATCH; deshalb ist PATCH ab P2 eingeplant (Abschnitt 7), für den P0-Spike reicht Vanilla-ICE.

---

## 4. SDK-Untersuchung: `io.github.webrtc-sdk:android`

### 4.1 Koordinaten & Versionen

- **Artifact:** `io.github.webrtc-sdk:android` — Maven Central, aktuelle Version **150.7871.01** (veröffentlicht 31.08.2026, `maven-metadata` lastUpdated 2026-09-05). Versionsschema: `<libwebrtc-Meilenstein>.<Chromium-Build>.<Patch>` (z. B. 150.7871.01 = libwebrtc M150, Chromium-Build 7871).
- **Versionshistorie (verifiziert an repo1.maven.org):** 114.x (2023) → 125.x (März 2025) → 137.x → 144.x → 150.x (Aug 2026) — vier Meilenstein-Generationen in 17 Monaten, nach einer längeren Pause 2023/24 deutlich beschleunigte Pflege.
- **Varianten (README des [webrtc-sdk/android](https://github.com/webrtc-sdk/android)):**
  - `android` — Original-Package `org.webrtc` (Empfehlung für Vivid: kollisionsfrei, siehe unten)
  - `android-prefixed` — Package nach `livekit.org.webrtc` umgezogen (für Projekte mit einem zweiten WebRTC-Stack)
  - `android-prefixed-stripped` — Software-Codecs entfernt, größenoptimiert (nur prefixed)
- **POM:** null transitive Abhängigkeiten (verifiziert) — keine versteckte Supply-Chain unter dem AAR.
- **Alternativen (bewertet):** `io.getstream:stream-webrtc-android:1.3.9` (Stream, aktiv, identisches `org.webrtc`-API, ebenfalls Prebuilt-AAR, gute zweit Quelle); `org.webrtc:google-webrtc:1.0.32006` (toter JCenter-Erbe, keine Security-Fixes — ausgeschlossen); libwebrtc selbst bauen (~16 GB Checkout, Linux-CI, Wochen-Aufwand — nur als letzter Ausweg). Fazit: webrtc-sdk als Primärkandidat, getstream als dokumentierter Fallback — beide mit gleichem Java-API, ein SDK-Wechsel bleibt billig, solange die Media-Bridge hinter einem Interface lebt (Abschnitt 5).

### 4.2 AAR-Anatomie & APK-Impact (empirisch gemessen am 30.09.2026)

Direkt aus `android-150.7871.01.aar` (49.147.033 B gesamt):

| Eintrag | Größe (B) | Größe (MiB) |
|---|---|---|
| `jni/arm64-v8a/libjingle_peerconnection_so.so` | 12.287.312 | ~11,7 |
| `jni/armeabi-v7a/libjingle_peerconnection_so.so` | 6.809.404 | ~6,5 |
| `jni/x86/libjingle_peerconnection_so.so` | 12.818.648 | ~12,2 |
| `jni/x86_64/libjingle_peerconnection_so.so` | 16.166.352 | ~15,4 |
| `classes.jar` (Java-API) | 1.063.812 | ~1,0 |
| `AndroidManifest.xml` | 629 | — |

**APK-Impact-Szenarien:**

| Distribution | Mechanismus | Delta |
|---|---|---|
| Play (App Bundle) | AAB liefert je Gerät **nur eine ABI** → arm64-Geräte laden die arm64-`.so` + classes | **≈ +13 MB** Downloadgröße |
| F-Droid (Universal-APK) | eine APK mit allen 4 ABIs (Vivid baut bislang ohne ABI-Splits) | **≈ +49 MB** |

Das ist der größte echte Kompromiss des Spikes. Bewertung: 49 MB sind für eine Streaming-App mit Media3/Compose-Fundament erheblich, aber nicht außergewöhnlich (VLC/Termux-Klasse-Apps tragen ähnliche Native-Größen). **Entscheidungspunkte für P0/P3:** (a) ABI-Splits bzw. `abiFilters` für den F-Droid-Build evaluieren (arm64 + armv7 decken praktisch alle Geräte ab → ≈ +19 MB statt +49), (b) die `prefixed-stripped`-Variante messen (Software-Codecs raus — für den Sendepfad mit Hardware-Encoding plausibel verzichtbar), (c) Akzeptieren mit dokumentierter Begründung. Alles drei ist P0-messbar in <1 h.

### 4.3 Lizenz & FOSS/F-Droid

- **Lizenzen:** POM deklariert **BSD 3-Clause**; die LICENSE im GitHub-Repo ist **MIT** (Copyright 2023 „WebRTC SDKs" — die Packaging-Hülle); libwebrtc selbst ist BSD-3 mit PATENTS-Grant (WebRTC Project Authors). Beide Lizenzfamilien sind OSI-approved und F-Droid-kompatibel; die POM/README-Diskrepanz ist kosmetisch (Packaging-Repo vs. Projekt-Repro), wird aber im Third-Party-Vermerk festgehalten.
- **F-Droid-Präzedenz (Nov 2025, offizielles Forum):** Pre-compiled AARs von Maven Central sind zulässig — Maven Central steht auf der F-Droid-Allowlist; die Moderation verweist auf die Inclusion Policy („Though we tried to build everything from source…") und den Blogpost *„Maven Central is not as free as it looks"*: Erlaubnis ≠ Garantie, dass das Binary aus dem geposteten Quellcode gebaut wurde.
- **Einordnung für Vivid:** Der `foss`-Flavor kann WHIP **vollständig** enthalten — anders als Sentry (Closed-Source) entsteht keine Closed-Source-Pflicht, es gibt keinen Flavor-Entzug. Der ehrliche Vorbehalt: RootEncoder (heutiger Media-Stack) kommt über JitPack **aus Quellen gebaut**, das WebRTC-AAR ist ein **Fremd-Prebuild**. Gegenmaßnahmen: (1) nur Allowlist-Repo (Maven Central, GPG-signiert), (2) Interface-Ader zwischen Vivid-Code und SDK (Abschnitt 5) hält einen späteren Austausch/prebuilt-Verlust billig, (3) Vorbehalt in der F-Droid-Metadaten-/Release-Doku benennen, (4) mittelfristig beobachten, ob getstream/webrtc-sdk attestation-basierte Nachweise (SLSA/SBOM) publizieren.
- **Hausfrage aus [docs/vision.md](vision.md) (5-Fragen-Check):** „Läuft es im `foss`-Flavor ohne Closed-Source-Pflicht?" → **Ja** (BSD/MIT, Binary frei, keine proprietäre Gegenstelle). Der vollständige Check steht in Abschnitt 9.

### 4.4 Kotlin-Interoperabilität & minSdk

- **Reines Java-API** (`org.webrtc.PeerConnectionFactory`, `PeerConnection`, `SessionDescription`, `Camera2Capturer`, `EglBase`, …) — aus Kotlin ohne Annotation-Processor, ohne KSP, ohne Compiler-Plugin nutzbar. Keine Kopplung an die Kotlin-Version → **kein Interplay mit der CodeQL-Kotlin-Blockade** (die den *Compiler* pinnt, nicht Dependency-Bytecode).
- Callback-Stil (`SdpObserver`, `StatsObserver`) wird mit ~30 Zeilen suspend-Wrappern koroutinenfreundlich geglättet (Vorbild: die `stream-webrtc-android-ktx`-Extensions zeigen den Idiom-Umfang).
- **minSdk:** AAR-Manifest `minSdkVersion 21`, `targetSdkVersion 23` (Library-typisch, irrelevant für die App) — Vivid minSdk 24 ✅.
- **Package-Kollisionen:** `org.webrtc` wird im Vivid-Codebase **nicht** referenziert (verifiziert) — die plain-Variante ist gefahrlos; falls künftig ein zweiter WebRTC-Stack dazukommt, weicht Vivid auf `android-prefixed` aus (nur Dependencies-Zeile ändert sich, weil die Media-Bridge hinter dem Interface gekapselt ist).
- **Camera-Path-Hinweis:** Für das MVP nutzt WHIP die Kamera über den SDK-eigenen `Camera2Capturer` (eigener Capture-Pfad neben RootEncoder) — siehe Abschnitt 6 zur Abgrenzung.

### 4.5 CodeQL-Verträglichkeit

Struktur-Check gegen [.github/workflows/security-codeql.yml](../.github/workflows/security-codeql.yml): init → Kotlin-Pin (2.4.20 → 2.4.10, Bundle-2.27.0-Blockade) → `./gradlew :app:assembleDebug` (Trace) → analyze.

- Der `java-kotlin`-Extractor arbeitet auf **übersetztem First-Party-Quelltext**. Ein AAR trägt nichts zu diesem Input bei: `classes.jar` ist Dependency-Bytecode (wird nicht extrahiert), die `.so`-Binaries sind für den Extractor intransparent.
- Kein neuer Build-Schritt, kein neuer Compiler, kein neues KSP-Plugin → der Pin-Step (Kotlin 2.4.10 für den Trace-Build) bleibt byte-identisch wirksam; der [K1-Vertrag](../scripts/test_codeql_kotlin.sh) (Same-Release über alle codeql-action-Pins) sieht keine neue Action.
- **Nebenpfad Snyk:** `io.github.webrtc-sdk:android` erscheint künftig als eine Zeile in den Gradle-Manifesten (Snyk test --all-projects); bekannte CVE-Datenbankeinträge für das Artifact sind nicht aufgetreten. Das SARIF-Kategorien-Fixup (unique per project, `dea457b3`) ist unabhängig von der Projektanzahl korrekt.
- Rest-Risiko: praktisch null. Falls Snyk ein Pseudo-Finding auf das Prebuild meldet, ist der etablierte Suppressions-Pfad ([docs/security-suppressions.md](security-suppressions.md)) die dokumentierte Heimat.

### 4.6 Weitere Verträglichkeitspunkte

| Punkt | Bewertung |
|---|---|
| **NSC/CLEARTEXT (#226-Präzedenz)** | ✅ WHIP-Endpoints sind `https://` (Signalisierung über den NSC-regulierten Stack ist gerade *gewollt* und erlaubt). Die Media-Verbindung läuft als SRTP/UDP über eigene Sockets — außerhalb der NSC, gleiche Klasse wie RTMP/SRT. Ein `http://`-WHIP-Endpoint wird bewusst von der NSC blockiert → Fehlermeldung statt Feature-Löchern. |
| **R8/ProGuard** | ⚠️→✅ Release-Build minifiziert (`proguard-android-optimize`); libwebrtc-AARs brauchen typischerweise Keep-Regeln für die JNI-Callback-Klassen (`org.webrtc.**`). Ob das AAR Consumer-Rules mitbringt, wird in P0 geprüft; Worst Case: eine Hand Zeilen in `app/proguard-rules.pro`. |
| **16-KB-Page-Alignment (Play-Anforderung, Android 15+)** | ⚠️ P0-Messpunkt: `readelf -l` auf der arm64-`.so` (LOAD-Segment-Alignment). Moderne Prebuilds (M144/M150) sind 16-KB-gebacken, aber das wird nicht behauptet, sondern gemessen. Falls unaligned: nur Play-Richtlinien-Risiko, F-Droid unberührt; Fix = SDK-Bump. |
| **Build-Zeit** | AAR-Download ~49 MB (einmalig, Gradle-Cache) + kein KSP/Apt-Aufwand — messbar, aber im Rauschen. |
| **Unit-Tests (JVM)** | ✅ `core`-Tests laufen JVM-only; das SDK bleibt hinter dem Interface, sodass die WHIP-Contract-Tests ohne Native-Binary laufen (wie die OBS-E2E-Tests gegen den Ktor-Testserver). |

---

## 5. WHIPClient-Skizze in `core` (neben dem OBS-Muster)

Architektur-Entscheidung des Spikes: **zwei Bausteine, zwei Module.**

1. `core` → `WHIPClient`: der komplette RFC-9725-HTTP-Contract (POST/201+Location/PATCH+ETag/DELETE/Redirects/Bearer/429) — **ohne jegliche SDK-Abhängigkeit**, kloniert bewusst die Vertragsmuster des `OBSWebSocketClient` (`core/src/main/java/com/vivid/core/network/obs/OBSWebSocketClient.kt`): injizierter `HttpClient` (Ktor CIO aus der bestehenden `KtorClientFactory`), `@Singleton`, Generation-Counter gegen veraltete Rücksetzungen, StateFlow-Zustand, fehlertoleranter Vertrag (kein Throw an den Aufrufer), explizites `shutdown()` für Test-Hosts.
2. `feature-streaming` → `WHIPStreamRoute` (Media-Bridge): das Einzige, was `org.webrtc.*` importiert — `PeerConnectionFactory`-Setup, Offer-Erzeugung, Answer-Anwendung, Kandidaten-Weitergabe an `WHIPClient.patch(...)`, Kamera via `Camera2Capturer`. Testbar über ein `WebRTCPublisher`-Interface, dessen JVM-Test-Double die Media-Seite simuliert.

> **Skizze (nicht kompiliert — Vertrags- und Zustandsentwurf):**

```kotlin
// core/src/main/java/com/vivid/core/network/whip/WHIPClient.kt — SKIZZE (P1)
package com.vivid.core.network.whip

/** Session-Zustand — analog OBSWebSocketClient.isConnected, aber mit Failure-Taxonomie. */
sealed interface WHIPState {
    data object Idle : WHIPState
    data class Connecting(val endpoint: String) : WHIPState
    data class Live(val resourceUrl: String) : WHIPState          // 201 + Location
    data class Failed(val reason: WHIPFailure) : WHIPState        // 401/403/429/Netz/412-Loop
    data object Closed : WHIPState
}

enum class WHIPFailure { UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, REDIRECT_LOOP, NETWORK, PROTOCOL }

/** Ergebnis der Veröffentlichung: Session-URI + ETag (Basis für PATCH/DELETE). */
data class WHIPResource(val sessionUrl: String, val etag: String?, val answerSdp: String)

@Singleton
class WHIPClient @Inject constructor(
    private val httpClient: HttpClient,   // Ktor CIO — von der bestehenden KtorClientFactory injiziert
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicInteger(0)               // OBS-Muster: veraltete Responses ignorieren
    private val _state = MutableStateFlow<WHIPState>(WHIPState.Idle)
    val state: StateFlow<WHIPState> = _state.asStateFlow()

    /**
     * RFC 9725 §4.4: POST mit SDP-Offer (application/sdp) an den Endpoint;
     * 201 + Location-Header = Session-URI, Body = Answer (application/sdp oder
     * text/plain-Fallback). Folgt methode-erhaltend 307/308 (§4.8).
     */
    suspend fun publish(config: WHIPConfig, sdpOffer: String): WHIPResource? { /* … */ }

    /** RFC 9725 §4.6: Trickle-ICE via PATCH + If-Match(ETag); 412 → genau ein Refresh-Retry. */
    suspend fun patchCandidates(resource: WHIPResource, sdpFragment: String): Boolean { /* … */ }

    /** RFC 9725 §4.5: DELETE auf die Session-URI; 404 tolerant (Session bereits weg). */
    suspend fun terminate(resource: WHIPResource) { /* … */ }

    /** Test-/Host-Teardown — Vertrag wie OBSWebSocketClient.shutdown(). */
    internal fun shutdown() { scope.cancel() }
}

// feature-streaming — Media-Bridge (SDK-Abhängigkeit lebt AUSSCHLIESSLICH hier) — SKIZZE (P1)
class WHIPStreamRoute(
    private val whip: WHIPClient,
    private val publisher: WebRTCPublisher,   // Interface: echte org.webrtc-Implementierung injizierbar
) {
    /** Kamera → PeerConnection(sendonly) → Offer → WHIP publish → Answer anwenden → Live. */
    suspend fun start(endpoint: String, bearer: String?) { /* … */ }
    suspend fun stop() { /* … */ }
}
```

**Warum diese Trennung wichtig ist:**
- Die `core`-Suite bleibt JVM-schnell und SDK-frei — die komplette Protokoll-Logik ist mit der **bereits vorhandenen** Testinfrastruktur testbar: `ktor-server-test-host` + `ktor-server-websockets` liegen bereits in `core/build.gradle.kts` (die OBS-E2E-Tests nutzen denselben Server). Contract-Test-Katalog: 201+Location/Answer, `text/plain`-Fallback, Redirect 307 (methode-erhaltend), PATCH+ETag/412-Conflict, DELETE/404-tolerant, 401/403-Taxonomie, 429+Retry-After, Link-Header-ICE-Server-Parsing. Hauslehte aus #226 beachten: Tests koroutinenfrei halten (blockierendes Polling + `@Timeout(30)`), Teardown schließt Scope/HttpClient explizit.
- Ein SDK-Wechsel (webrtc-sdk ↔ getstream ↔ selbst gebaut) berührt nur `feature-streaming`, nicht Protokoll oder Tests.
- Die 49-MB-Abhängigkeit hängt am App-Modul-Graph, nicht am `core`-JVM-Testpfad.

---

## 6. Integration in Engine, Validator & Settings

- **Validator:** `StreamConfigValidator.SUPPORTED_SCHEMES` (heute `rtmp`, `rtmps`, `srt`) um `whip` erweitern; `whip://host/path` wird intern auf `https://host/path` normiert; eigener Fehlerzweig `stream_error_bad_scheme` (Strings de/en/fr/ru erweitern — der i18n-Guard zwingt die Vollständigkeit ohnehin an). Der Go-Live-Self-Check bekommt einen WHIP-Zweig (Endpoint-Erreichbarkeit, optional Bearer-Probe), analog zum RTMPS-Support-Nachweis als Bytecode-/Vertragstest.
- **Settings:** neues Feld „WHIP-Endpoint (URL)" + optionales „Bearer-Token" in der Streaming-Kategorie; bewusst **kein** Stream-Key (WHIP kennt keinen) — UI-Copy unterscheidet das explizit, damit Nutzer nicht den RTMP-Key ins Bearer-Feld eintragen.
- **Engine-Routing:** `StreamingEngine.startStream(urls)` routet je Ziel nach Schema — `rtmp(s)://`/`srt://` → RootEncoder (unverändert), `whip(s)://` → `WHIPStreamRoute`. **MVP-Einschränkung (bewusst):** WHIP ist ein **Einzel-Ziel**: RootEncoder `MultiCamera2` besitzt die Kamera-/GL-Pipeline exklusiv; dieselben Frames gleichzeitig in eine WebRTC-Track zu speisen erfordert einen EGLSurface-Relay/Frame-Distributor — realistischer P3+-Aufwand, kein MVP. Paralleles `RTMP + WHIP` wird also zuerst **nicht** unterstützt und in der UI/Doc so kommuniziert; der Ziel-Status (`targetStates`) bekommt einen WHIP-Zustandspfad über denselben `ConnectChecker`-analogen Callback.
- **Foreground-Service:** `StreamingService` hält zusätzlich die WHIP-Session (Wake-Lock-Vertrag identisch); Reconnect-Logik (Session neu aufbauen, Endpoint-Redirects respektieren) ist P2.
- **Bot/UI-Extras:** kein Bot-Befehl im MVP (kein `!whip`-Bedarf erkennbar); Ziel-Status-Anzeige folgt dem bestehenden Muster („Verbinde…/Live/fehlgeschlagen").

---

## 7. Aufwandsschätzung (Phasenplan)

| Phase | Umfang | Schätzung | Exit-Kriterium |
|---|---|---|---|
| **P0 — Spike/Prototyp** (Feature-Branch, nicht develop) | SDK-Dependency; Minimal-`PeerConnection` + WHIP-POST/DELETE gegen lokalen [MediaMTX](https://github.com/bluenviron/mediamtx) (docker, WHIP-Endpoint out-of-the-box); Messung: APK-/AAB-Delta je Variante (plain/prefixed-stripped), 16-KB-Alignment, R8-Verhalten (mit/ohne Keep-Rules), Build-Zeit-Delta | **1–2 Tage** | Browser (WHEP-Player des MediaMTX) empfängt den Test-Stream vom Gerät; alle Messwerte in dieser Datei nachgetragen → **Go/No-Go** |
| **P1 — Prototyp → Feature** | `WHIPClient` (core) + Contract-Tests (Katalog Abschnitt 5); `WHIPStreamRoute` + `WebRTCPublisher`-Interface; Validator/Settings/UI/i18n; Engine-Routing Single-Target; Ziel-Status | **3–5 Tage** | Go-Live auf ein WHIP-Ziel vom Gerät (MediaMTX + Cloudflare-Endpoint), feature-streaming/core-Suiten grün |
| **P2 — Härtung** | PATCH/Trickle-ICE + ETag-Conflict-Retry; Reconnect/Session-Refresh; Foreground-Service-Vertrag; Bearer-Auth-Fehlerpfade; Nightly-Flag (Staged-Rollout); Snyk/CodeQL-Lauf beobachten | **2–3 Tage** | Nightly mit Flag; 30-min-Stabilität gegen MediaMTX; Security-Suite grün |
| **P3 — Produktionsreife** | ABI-/Varianten-Entscheidung umsetzen (F-Droid-Metadaten); Multi-Target-Abgrenzung dokumentieren (oder Frame-Relay-PoC, bewusst separat); Release-Notes; **PARITY-Zeile 54 📋 → ✅** | **2–3 Tage** | Bucket-Zeile 54 ✅; Feature-Flag aus, Produktivpfad |
| **Gesamt** | | **≈ 8–13 AT** | |

Kalibrierung gegen Hauswerte: Das OBS-WebSocket-Bucket (#226-Umstellung + 30 E2E-Tests) und die Kick-Adapter-Runde (P2, 21 Contract-Tests) sind die richtigen Größenordnungs-Nachbarn — WHIP liegt zwischen beiden (Protokoll trivialer als OBS-Auth, Media-Seite neu).

---

## 8. Risiken & Entscheidungspunkte

| Risiko | Schwere | Gegenmaßnahme |
|---|---|---|
| F-Droid Universal-APK wächst um ~49 MB | mittel (Nutzererfahrung) | P0 misst Varianten; Optionen ABI-Splits (+~19 MB) / stripped-Variante / akzeptieren+dokumentieren |
| Prebuild-Verfügbarkeit & Lag (Third-Party-Team) | mittel (langfristig) | Interface-Ader in `feature-streaming`; getstream als dokumentierter Zweitbezug; kein Lock-in durch Prefixed-Wechsel |
| 16-KB-Page-Alignment (Play) | niedrig–mittel | P0-Messpunkt (`readelf`); Fix = SDK-Bump, F-Droid unbeeinträchtigt |
| R8-Crash durch fehlende Keep-Rules | niedrig | P0-Messpunkt; Worst Case wenige Zeilen in `proguard-rules.pro` |
| TURN-abhängige NAT-Fälle scheitern | mittel (Realität) | Link-Header-ICE-Server auswerten (P2) + optionale TURN-Creds in Settings; Dokumentation „WHIP-Ziele ohne TURN brauchen erreichbare UDP-Path" |
| RootEncoder-Kamerapfad: kein paralleles RTMP+WHIP im MVP | niedrig (Erwartung) | bewusste MVP-Abgrenzung (Abschnitt 6); Frame-Relay als separat geplante Erweiterung |
| CodeQL/Kotlin-Blockade | **keins** | Abschnitt 4.5 — AAR ist kein Extractor-Input; Pin-Vertrag unberührt |
| Snyk-Pseudo-Finding auf das Prebuild | niedrig | etablierter Suppressions-Pfad mit Begründung + Review-Datum |

**Bewusste Nicht-Ziele des MVP:** WHEP (Wiedergabe), Multi-Target (RTMP+WHIP parallel), RIST/SRTLA-Interplay, Screen-Capture-Quelle über WHIP (RootEncoder-Pfad kollidiert ebenso — quellneutral erst nach dem Frame-Relay).

---

## 9. Vision-Check (5-Fragen-Check aus docs/vision.md)

| # | Frage | Ergebnis |
|---|-------|----------|
| 1 | Streamer-Nutzen (Bild/Audio/Chat/Zuverlässigkeit) | ✅ sub-2-s-Latenz eröffnet interaktive IRL-Formate, die RTMP-Verzögerung verhindert |
| 2 | Mobile-Realität (Akku, Mobilfunk, einhändig) | ✅ libwebrtc nutzt Hardware-Codecs; UDP-Pfad ist bewährt (SRT/VoIP-Klasse); Mehrgewicht im APK ist der Kostenpunkt, nicht im Betrieb |
| 3 | Parität/Ökosystem | ✅ direkte Moblin-Parität (PARITY Row 54) + schließt moderne Ziele (Cloudflare/Owncast/MediaMTX) an |
| 4 | Wartbarkeit (kleines Team, keine proprietären Abhängigkeiten) | ✅ mit Fußnote — Prebuild statt Eigenbau ist hier gerade die wartbarere Wahl; Interface-Ader hält den Austausch billig |
| 5 | FOSS-Kompatibilität (`foss`-Flavor ohne Closed-Source-Pflicht) | ✅ BSD/MIT, Maven-Central-Allowlist, keine proprietäre Gegenstelle; Transparenz-Vorbehalt dokumentiert (Abschnitt 4.3) |

→ **Aufnehmen** (5/5, mit den dokumentierten Fußnoten): passt in das bestehende v0.6.0-beta-Bucket als Zeile 54.

---

## 10. Quellen (abgerufen 30.09.2026)

- Maven Central: [io.github.webrtc-sdk:android — Verzeichnis 150.7871.01](https://repo1.maven.org/maven2/io/github/webrtc-sdk/android/150.7871.01/) (AAR-/POM-Maße, BSD-3-Lizenzangabe, null Dependencies) · [maven-metadata.xml](https://repo1.maven.org/maven2/io/github/webrtc-sdk/android/maven-metadata.xml) (Versionshistorie)
- [webrtc-sdk/android (GitHub)](https://github.com/webrtc-sdk/android) — Varianten, README · [LICENSE](https://github.com/webrtc-sdk/android/blob/main/LICENSE) (MIT)
- [RFC 9725 — WebRTC-HTTP Ingestion Protocol (WHIP)](https://www.rfc-editor.org/info/rfc9725/)
- [F-Droid-Forum: Policy clarification — pre-compiled AAR libraries](https://forum.f-droid.org/t/policy-clarification-can-applications-integrate-pre-compiled-aar-libraries/33624) (Nov 2025) + verlinkte [Inclusion Policy](https://f-droid.org/en/docs/Inclusion%20Policy/) und *„Maven Central is not as free as it looks"*
- [GetStream/webrtc-android](https://github.com/GetStream/webrtc-android) — Alternative `io.getstream:stream-webrtc-android`, API-/KTX-Einordnung
- [github/codeql#22404](https://github.com/github/codeql/issues/22404) — Kotlin-Bundle-Blockade (Kontext: unberührt, siehe Abschnitt 4.5)
- Intern verifiziert: `core` (OBSWebSocketClient/KtorClientFactory/core build), `gradle/libs.versions.toml` (minSdk 24, RootEncoder 2.7.5), `StreamConfigValidator` (SUPPORTED_SCHEMES), `security-codeql.yml`, PARITY-Zeilen 53–63, `scripts/test_roadmap_reservation.sh`
