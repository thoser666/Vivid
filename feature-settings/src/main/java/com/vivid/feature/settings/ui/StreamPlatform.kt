package com.vivid.feature.settings.ui

/**
 * Plattform-Vorlagen für die Stream-Einstellungen.
 *
 * [ingestUrl] ist die Basis-RTMP-Ingest-URL ohne Stream-Key. Beim Anwenden
 * einer Vorlage wird [ingestUrl] als Stream-URL gesetzt und TLS aktiviert,
 * sodass die Konvertierung `rtmp://` → `rtmps://` in buildStreamUrl greift.
 *
 * - Twitch:  rtmps://live.twitch.tv/app (offiziell unterstützt)
 * - YouTube: rtmps://a.rtmp.youtube.com/live2 (offiziell unterstützt)
 * - Kick:    rtmps://live.kick.com/app (nicht offiziell dokumentiert, wird
 *            aber von gängigen Tools wie Restreamer/Stunnel genutzt; falls
 *            der Server TLS ablehnt, kann der Nutzer den Toggle deaktivieren)
 */
enum class StreamPlatform(
    val label: String,
    val ingestUrl: String,
) {
    Twitch("Twitch", "rtmp://live.twitch.tv/app"),
    YouTube("YouTube", "rtmp://a.rtmp.youtube.com/live2"),
    Kick("Kick", "rtmp://live.kick.com/app"),
}
