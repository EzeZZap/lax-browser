package com.javzzap.laxbrowser.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import com.javzzap.laxbrowser.engine.gecko.MediaState
import com.javzzap.laxbrowser.engine.gecko.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps web audio and video playing while Lax is in the background and shows media controls in
 * the notification shade and on the lock screen. Private-tab media shows no title or artwork.
 */
class MediaPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        val media = appContainer.engine.media
        session = MediaSessionCompat(this, "Lax").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { media.play() }
                override fun onPause() { media.pause() }
                override fun onStop() { media.stop() }
                override fun onSkipToNext() { media.next() }
                override fun onSkipToPrevious() { media.previous() }
            })
            isActive = true
        }
        scope.launch {
            media.state.collectLatest { state ->
                if (state == null) {
                    stopSelf()
                    return@collectLatest
                }
                val artwork = if (state.isPrivate) null else state.artwork?.let { runCatching { it.getBitmap(ARTWORK_SIZE).await() }.getOrNull() }
                show(state, artwork)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> appContainer.engine.media.play()
            ACTION_PAUSE -> appContainer.engine.media.pause()
            ACTION_NEXT -> appContainer.engine.media.next()
            ACTION_PREVIOUS -> appContainer.engine.media.previous()
            ACTION_STOP -> appContainer.engine.media.stop()
        }
        // Android requires startForeground soon after startForegroundService; show something at once.
        if (!foreground) appContainer.engine.media.state.value?.let { show(it, null) } ?: stopSelf()
        return START_NOT_STICKY
    }

    private fun show(state: MediaState, artwork: Bitmap?) {
        val title = if (state.isPrivate) getString(R.string.media_private_title) else state.title?.takeIf { it.isNotBlank() } ?: getString(R.string.media_playing)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, if (state.isPrivate) null else state.artist)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
                .build(),
        )
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
        if (state.canSkip) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(if (state.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )

        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (state.isPrivate) null else state.artist)
            .setLargeIcon(artwork)
            .setContentIntent(open)
            .setDeleteIntent(action(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setVisibility(if (state.isPrivate) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
        var compact = intArrayOf(0)
        if (state.canSkip) {
            builder.addAction(android.R.drawable.ic_media_previous, getString(R.string.media_previous), action(ACTION_PREVIOUS))
            compact = intArrayOf(0, 1, 2)
        }
        if (state.playing) {
            builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.media_pause), action(ACTION_PAUSE))
        } else {
            builder.addAction(android.R.drawable.ic_media_play, getString(R.string.media_play), action(ACTION_PLAY))
        }
        if (state.canSkip) builder.addAction(android.R.drawable.ic_media_next, getString(R.string.media_next), action(ACTION_NEXT))
        builder.setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(*compact))

        val notification = builder.build()
        if (state.playing || !foreground) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            foreground = true
        } else {
            // Paused: the notification can be swiped away and the process may be stopped.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            foreground = false
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun action(name: String) = PendingIntent.getService(
        this, name.hashCode(), Intent(this, MediaPlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.channel_media), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "media"
        private const val NOTIFICATION_ID = 3
        private const val ARTWORK_SIZE = 256
        private const val ACTION_PLAY = "play"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_NEXT = "next"
        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_STOP = "stop"

        /** Starts the service when playback begins; it stops itself when the media session ends. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MediaPlaybackService::class.java)) }
        }
    }
}
