package com.vivid.feature.chat.model

/**
 * Typ eines Chat-Overlay-Alerts (Follow/Sub/Gift-Sub/Resub/Raid) — die
 * Events, die Twitch über EventSub als eigene Subscription-Typen liefert
 * (`channel.follow`, `channel.subscribe`, `channel.subscription.gift`,
 * `channel.subscription.message`, `channel.raid`).
 */
enum class ChatAlertType {
    FOLLOW,
    SUBSCRIBE,
    GIFT_SUB,
    RESUB,
    RAID,
}

/**
 * Ein Event-Alert für das Chat-Overlay (z. B. „X folgt jetzt!“,
 * „Y abonniert (Tier 1)“, „Raid von Z mit 12 Viewern“).
 *
 * Der [detail]-Teil hält die strukturierten Zusatzdaten (nicht lokalisiert) —
 * die Anzeige-Texte setzt das Overlay selbst aus den String-Ressourcen
 * zusammen.
 *
 * @param id Eindeutige ID (Event-ID bzw. lokal generiert) — Grundlage für die
 *   TTL-Entfernung im ViewModel (ein Alert wird nach Ablauf genau einmal
 *   entfernt, Duplikate kollidieren nicht).
 * @param type [ChatAlertType] des Events.
 * @param displayName Anzeigename des Users (Follower/Subscriber/Raider).
 * @param timestamp Zeitpunkt des Events (Epochen-Millis).
 * @param detail Typ-spezifische Zusatzdaten (siehe [AlertDetail]).
 */
data class ChatAlert(
    val id: String,
    val type: ChatAlertType,
    val displayName: String,
    val timestamp: Long,
    val detail: AlertDetail = AlertDetail(),
)

/**
 * Strukturierte (nicht lokalisierte) Zusatzdaten eines Alerts. Die
 * Lokalisierung passiert ausschließlich im Overlay-Composable.
 *
 * @param tier Sub-Tier als Twitch-Wert (`1000`/`2000`/`3000` → Tier 1/2/3).
 * @param gifterName Anzeigename des Giftgebers (bei Geschenk-Subs).
 * @param viewerCount Raid-Größe (Viewer-Anzahl).
 * @param count Anzahl verschenkter Subs im Gift-Event (`total`).
 * @param cumulativeTotal Lebenszeit-Gesamtsumme des Giftgebers
 *   (`cumulative_total`; bei anonymen Gifts liefert Twitch null → 0).
 * @param isAnonymous Gift anonym verschenkt (Twitch liefert dann leere
 *   User-Felder — das Overlay zeigt den lokalisierten „Anonym“-Namen).
 * @param months Kumulierte Sub-Monate beim Resub (`cumulative_months`).
 * @param streakMonths Aktuelle Monats-Serie (`streak_months`; 0 wenn keine).
 */
data class AlertDetail(
    val tier: String = "",
    val gifterName: String = "",
    val viewerCount: Int = 0,
    val count: Int = 0,
    val cumulativeTotal: Int = 0,
    val isAnonymous: Boolean = false,
    val months: Int = 0,
    val streakMonths: Int = 0,
)
