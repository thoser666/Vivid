package com.vivid.feature.chat.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steuert den aktuell aktiven Media-Player (Apple Music, Spotify, YouTube
 * Music, …) über [MediaController] — Android-Adaption der Apple-Music-
 * Steuerung aus Moblin 33.12.0 (Moblin-Paritätszeile Row 80).
 *
 * Fremde Media-Sessions sind nur abrufbar, wenn der Nutzer der App
 * **Benachrichtigungszugriff** gewährt hat ([MediaNotificationListener]).
 * Ohne diesen Zugriff liefert [hasAccess] false und alle Aktionen sind
 * No-ops — der Chat-Bot antwortet dann mit einem Hinweis statt zu steuern.
 */
@Singleton
class ChatMediaController @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatMediaPlayer {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)

    override fun hasAccess(): Boolean =
        runCatching { mediaSessionManager.getActiveSessions(listenerComponent) }.isSuccess

    override fun nowPlaying(): String? {
        val metadata = currentController()?.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        if (title.isEmpty()) return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().trim()
        return if (artist.isNotEmpty()) "$title – $artist" else title
    }

    override fun play() {
        currentController()?.transportControls?.play()
    }

    override fun pause() {
        currentController()?.transportControls?.pause()
    }

    override fun skipToNext() {
        currentController()?.transportControls?.skipToNext()
    }

    override fun skipToPrevious() {
        currentController()?.transportControls?.skipToPrevious()
    }

    /** Bevorzugt eine aktive (play/pause/buffering) Session, sonst die erste. */
    private fun currentController(): MediaController? {
        if (!hasAccess()) return null
        val controllers = runCatching { mediaSessionManager.getActiveSessions(listenerComponent) }
            .getOrElse { emptyList() }
        if (controllers.isEmpty()) return null
        return controllers.firstOrNull { it.playbackState?.state in ACTIVE_STATES }
            ?: controllers.firstOrNull()
    }

    companion object {
        private val ACTIVE_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_BUFFERING,
        )
    }
}
