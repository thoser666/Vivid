# 🚀 Release-Notes v0.5.5-beta

| | |
|---|---|
| **Version** | `0.5.5-beta` (versionCode `5052`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.5-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Chat-Overlay komplett — Badges, Emotes und Event-Alerts

**Seit v0.5.4-beta ist die Chat-Overlay-Zeile vollständig abgeschlossen — das Overlay zeigt den Chat jetzt so, wie er in Twitch selbst aussieht:**

### 1. 🎭 Twitch-Badges (Broadcaster/Mod/Sub)

Vor jedem Username erscheinen die **Twitch-Badges** als CDN-Bilder (global + kanalspezifisch, via Helix Chat-Badges-API): Broadcaster, Moderator, Subscriber u. a. — Unbekannte Badges werden sauber übersprungen, bei Fehlern läuft das Overlay ohne Bilder weiter.

### 2. 🔔 Event-Alerts (Follow/Sub/Gift-Sub/Resub/Raid)

Das Overlay zeigt jetzt **Ereignis-Banner** über dem Chat — farbig je Typ (Follow grün, Sub lila, Gift-Sub cyan, Resub hellblau, Raid orange), automatisch ausgeblendet nach 10 Sekunden, max. 3 gleichzeitig:

- **Follows**, **Subscriptions** (inkl. Tier), **Sub-Geschenke** (auch anonym, mit kumulativer Gesamtsumme), **Resubs** (mit Monats-Serie) und **Raids** (mit Viewer-Anzahl, lokalisiert)
- Basieren auf denselben **EventSub-Subscriptions** wie der Chat — kein zusätzlicher Verbindungsaufbau
- **Hinweis:** Für Follows muss der Bot **Moderator im Kanal** sein (Scope `moderator:read:followers`), für Subs/Gifts/Resubs der Scope `channel:read:subscriptions`. Fehlt ein Recht, fällt nur der jeweilige Typ aus — Chat und Reconnect laufen weiter.

### 3. 🧪 Test-Alert per Chat-Befehl: `!testalert`

Neuer **Owner-Befehl** `!testalert follow|sub|gift|resub|raid` — der Streamer kann vor dem Go-Live einen synthetischen Alert auslösen und das Overlay-Rendering prüfen, **ohne** echte Follows/Subs/Raids abzuwarten. Antwort kommt (wenn konfiguriert) als **Whisper**, sonst im Chat.

### 4. 🛡️ Owner-Moderation: `!ban` / `!timeout` / `!delete`

Der Chat-Bot kann jetzt **moderieren** — ausschließlich für den Streamer (Owner-Gate, wie bei `!start`/`!diag`):

- `!ban <user>` · `!timeout <user> [minuten]` · `!delete <user>` — direkt über die **Twitch-Helix-API** (kein IRC)
- Ausführungs-Bestätigung mit Ziel und Ergebnis (z. B. „User X wurde für 10 min getimeoutet“), Fehler klar gemeldet (Unbekannter User, fehlender Mod-Status)

### 5. 🔍 `!diag` erweitert: Check „Event-Alerts konfiguriert“

Die Owner-Diagnose prüft jetzt **11 statt 10 Punkte** — neu: ob Kanal, Bot-Login, Bot-Token und Twitch-App-Client-ID gesetzt sind (Voraussetzungen der EventSub-Alert-Subscriptions). Der Check weist ehrlich aus, dass **Mod-Status und Scopes von Twitch nicht per API verifizierbar** sind und fehlende Rechte nur den jeweiligen Typ ausfallen lassen.

---

## ✨ Was sonst noch in diesem Build steckt

### 6. CI-Härtung & Qualität

- **PARITY-Log-Guard gehärtet:** `check_parity_log.sh --check-exists` prüft nicht mehr nur das Hash-Format, sondern dass jeder referenzierte Commit **existiert und ein Vorfahre von HEAD** ist — der Rebase-Orphan-Vorfall (Hash existierte im Reflog, war aber kein Vorfahre mehr) ist damit strukturell ausgeschlossen; CI-Checkout läuft jetzt mit `fetch-depth: 0`
- **Badge-Stale-Guard:** In-flight Badge-Loads eines vorherigen Kanals können die Badge-Map nach Kanalwechsel/Stopp nicht mehr überschreiben (asynchroner Race behoben)
- **Regressionstests:** publish_release-Harness um das Szenario „stabiler Pfad löscht nie einen Tag“ erweitert; Event-Alert-Suite mit Gift/Resub-Tests (inkl. anonymes Gift)

---

## 🧪 Was Tester validieren sollten

1. **Event-Alerts:** Chat-Overlay aktiv → als Streamer `!testalert follow` im Chat senden (bzw. per Whisper) → Banner erscheint oben über dem Chat und blendet sich nach ~10 s aus; dasselbe für `sub`, `gift`, `resub`, `raid` (Farben/Texte prüfen).
2. **Badges:** Chat-Overlay im eigenen Kanal öffnen → Broadcaster/Mod/Sub-Badges erscheinen vor den Usernamen; beim Kanalwechsel verschwinden die alten Badges sofort.
3. **Moderation:** Als Owner `!timeout <testuser> 1` im Chat senden → der Bot bestätigt per Whisper/Chat; prüfen, dass der User wirklich getimeoutet wurde (Twitch-Seite) und `!delete <testuser>` eine Nachricht entfernt.
4. **`!diag`:** `!diag` aufrufen → neuer Punkt „Event-Alerts konfiguriert“ zeigt `ok` (wenn Kanal/Login/Token/Client-ID gesetzt) bzw. benennt die fehlende Einstellung.
5. **Regression:** Streaming, OBS-Steuerung, Chat-Bot-Owner-Befehle (`!start`/`!stop`/`!ask`), Dark Mode und Widgets wie gewohnt — der Umbau betrifft Chat-Overlay und Bot-Kommandos.

## 🔧 Technisch (für Entwickler)

- `feature-chat`: `TwitchBadgeClient` (Helix Chat-Badges global + Kanal, Lookup `set_id/version_id`), `ChatBadge`-Modell; `TwitchChatEventSubReader` subscribt zusätzlich `channel.follow` v2 (`moderator_user_id` = Bot), `channel.subscribe` v1, `channel.subscription.gift` v1, `channel.subscription.message` v1 und `channel.raid` v1 — 6 Subscriptions pro Session, best-effort; Dispatch über `metadata.subscription_type`; `ChatAlert`/`AlertDetail` (Tier/Giftgeber/Viewer/Anzahl/Kumulativ/anonym/Monate/Serie), Overlay rendert max. 3 Alerts mit 10-s-TTL und Clear bei Kanalwechsel; `triggerTestAlert(type)` + Owner-Befehl `!testalert` über `ChatAlertTrigger`-Interface
- `feature-chat` Bot: Owner-Moderation `!ban/!timeout/!delete` via Helix (`/helix/moderation/bans` + `/helix/chat/messages`-Delete) — `ChatModeration`-Interface, Owner-Gate, Whisper-Bestätigung; `!diag`-Check in `AppChatStreamControl` (11 Checks)
- Tests: feature-chat-Suite **232 grün** (Reader: 6 Subscriptions + Gift/Resub inkl. anonym + Trigger; VM: Cap/TTL/Clear; Processor + Engine für `!testalert` und Moderation); App-Modul-Suite mit 7 neuen Diagnose-Tests
- CI: `scripts/check_parity_log.sh --check-exists` + `scripts/test_parity_log.sh` (P5/P6), `fetch-depth: 0` im Build-Job
