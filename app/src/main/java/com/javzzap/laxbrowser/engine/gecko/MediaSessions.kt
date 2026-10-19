package com.javzzap.laxbrowser.engine.gecko

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.MediaSession

/** The page media that system controls (notification, lock screen) act on. */
data class MediaState(
    val tabId: String,
    val title: String?,
    val artist: String?,
    val artwork: Image?,
    val playing: Boolean,
    val canSkip: Boolean,
    val isPrivate: Boolean,
    /** Set while a video plays fullscreen, with its size, for orientation and picture-in-picture. */
    val fullscreenVideo: Pair<Long, Long>?,
)

/**
 * Tracks GeckoView media sessions. The most recently active session drives the system controls.
 */
class MediaSessions {
    private val mutableState = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = mutableState.asStateFlow()
    private var active: MediaSession? = null
    private var activeTabId: String? = null

    fun play() = active?.play()
    fun pause() = active?.pause()
    fun stop() = active?.stop()
    fun next() = active?.nextTrack()
    fun previous() = active?.previousTrack()

    /** Forgets a tab whose session closed. */
    fun clear(tabId: String) {
        if (activeTabId == tabId) {
            active = null
            activeTabId = null
            mutableState.value = null
        }
    }

    fun delegateFor(tabId: () -> String?, isPrivate: Boolean): MediaSession.Delegate = object : MediaSession.Delegate {
        private fun update(media: MediaSession, transform: (MediaState) -> MediaState) {
            val id = tabId() ?: return
            if (active !== media) return
            mutableState.value = transform(mutableState.value?.takeIf { it.tabId == id } ?: empty(id))
        }

        private fun empty(id: String) = MediaState(id, null, null, null, playing = false, canSkip = false, isPrivate = isPrivate, fullscreenVideo = null)

        override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
            val id = tabId() ?: return
            active = mediaSession
            activeTabId = id
            mutableState.value = empty(id)
        }

        override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
            if (active === mediaSession) {
                active = null
                activeTabId = null
                mutableState.value = null
            }
        }

        override fun onMetadata(session: GeckoSession, mediaSession: MediaSession, meta: MediaSession.Metadata) =
            update(mediaSession) { it.copy(title = meta.title, artist = meta.artist, artwork = meta.artwork) }

        override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, features: Long) =
            update(mediaSession) {
                it.copy(canSkip = (features and (MediaSession.Feature.NEXT_TRACK or MediaSession.Feature.PREVIOUS_TRACK)) != 0L)
            }

        override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
            // A session that starts playing takes over the controls.
            if (active !== mediaSession) onActivated(session, mediaSession)
            update(mediaSession) { it.copy(playing = true) }
        }

        override fun onPause(session: GeckoSession, mediaSession: MediaSession) = update(mediaSession) { it.copy(playing = false) }

        override fun onStop(session: GeckoSession, mediaSession: MediaSession) = update(mediaSession) { it.copy(playing = false) }

        override fun onFullscreen(session: GeckoSession, mediaSession: MediaSession, enabled: Boolean, meta: MediaSession.ElementMetadata?) {
            if (active !== mediaSession) onActivated(session, mediaSession)
            update(mediaSession) { state ->
                state.copy(fullscreenVideo = if (enabled && meta != null && meta.width > 0 && meta.height > 0) meta.width to meta.height else null)
            }
        }
    }
}
