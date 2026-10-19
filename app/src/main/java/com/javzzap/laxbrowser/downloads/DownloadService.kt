package com.javzzap.laxbrowser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps Lax running while files download, so a download continues when the user leaves the app
 * or Android would otherwise stop it. Shows an ongoing notification with progress and Cancel;
 * stops itself when no download is left. Private downloads show no file name.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        val downloader = appContainer.downloader
        scope.launch {
            downloader.active.collect { active ->
                if (active.isEmpty()) {
                    stop()
                } else {
                    show(active.values.toList())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { appContainer.downloader.cancel(it) }
            ACTION_CANCEL_ALL -> appContainer.downloader.active.value.keys.forEach { appContainer.downloader.cancel(it) }
        }
        val active = appContainer.downloader.active.value
        if (active.isEmpty()) stop() else show(active.values.toList())
        return START_NOT_STICKY
    }

    private fun show(active: List<ActiveDownload>) {
        val notification = build(active)
        if (!foreground) {
            // If Android refuses a foreground service (for example background limits), the
            // download still runs as long as the app does; only the notification is plain.
            foreground = try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
                )
                true
            } catch (e: Exception) {
                Log.w(TAG, "Downloads continue without a foreground service", e)
                false
            }
        }
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
    }

    private fun build(active: List<ActiveDownload>): Notification {
        ensureChannel()
        val single = active.singleOrNull()
        val total = active.sumOf { it.totalBytes ?: 0L }.takeIf { active.all { download -> download.totalBytes != null } && it > 0 }
        val done = active.sumOf { it.downloadedBytes }
        val title = when {
            active.isEmpty() -> getString(R.string.download_in_progress)
            single == null -> resources.getQuantityString(R.plurals.downloads_in_progress, active.size, active.size)
            single.isPrivate -> getString(R.string.download_private_title)
            else -> single.fileName
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = if (single != null) {
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, single.id)
        } else {
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.download_in_progress))
            .setProgress(PROGRESS_MAX, total?.let { ((done * PROGRESS_MAX) / it).toInt().coerceIn(0, PROGRESS_MAX) } ?: 0, total == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setVisibility(if (active.any { it.isPrivate }) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(if (single != null) R.string.download_cancel else R.string.download_cancel_all),
                PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .build()
    }

    private fun stop() {
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        } else {
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** The same channel as the "download complete" notifications. */
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4
        private const val PROGRESS_MAX = 100
        private const val ACTION_CANCEL = "cancel"
        private const val ACTION_CANCEL_ALL = "cancel_all"
        private const val EXTRA_ID = "id"

        private const val TAG = "DownloadService"

        /**
         * Downloads start from something the user did, so the app is in the foreground and a
         * plain start is allowed; the service then moves itself to the foreground. (A
         * startForegroundService call would crash the app if that move were refused.)
         */
        fun start(context: Context) {
            runCatching { context.startService(Intent(context, DownloadService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start the download service", it) }
        }
    }
}
