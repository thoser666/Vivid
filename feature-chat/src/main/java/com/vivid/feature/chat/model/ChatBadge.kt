package com.vivid.feature.chat.model

/**
 * Eine Twitch-Badge (z. B. Broadcaster, Moderator, Subscriber) mit CDN-Bild-URL
 * für die Anzeige im Chat-Overlay.
 *
 * Der Key im Badge-Lookup ist das IRC-Format `"set_id/version_id"` (z. B.
 * `broadcaster/1`, `subscriber/6`, `moderator/1`) — genau das Format, in dem
 * [ChatMessage.badges] die Badges einer Nachricht trägt.
 *
 * @param setId Badge-Set (z. B. `broadcaster`, `moderator`, `subscriber`).
 * @param versionId Versions-ID innerhalb des Sets (z. B. Monate beim Sub).
 * @param title Anzeigename des Badges (z. B. „Subscriber (6 months)“) — wird
 *   als Content-Description genutzt (Accessibility).
 * @param imageUrl CDN-URL des Badge-Bilds (2x-Auflösung, für ~18 dp scharf).
 */
data class ChatBadge(
    val setId: String,
    val versionId: String,
    val title: String,
    val imageUrl: String,
) {
    /** Lookup-Key im IRC-Format, wie in [ChatMessage.badges]. */
    val key: String
        get() = "$setId/$versionId"
}
