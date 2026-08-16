package com.vivid.feature.chat.media

import android.service.notification.NotificationListenerService

/**
 * Benachrichtigungs-Listener, der der App den **Benachrichtigungszugriff**
 * verschafft. Damit darf [android.media.session.MediaSessionManager.getActiveSessions]
 * alle aktiven Media-Sessions zurückgeben — auch die fremder Apps (Apple Music,
 * Spotify, YouTube Music …) — die der Chat-Bot über [ChatMediaController]
 * steuert (!song/!next/!pause/!play/!prev).
 *
 * Der Listener selbst liest **keine** Benachrichtigungen aus — er ist nur der
 * Zugriffs-Marker für den Media-Session-Zugriff (bewusst keine
 * onNotificationPosted-Implementierung).
 */
class MediaNotificationListener : NotificationListenerService()
