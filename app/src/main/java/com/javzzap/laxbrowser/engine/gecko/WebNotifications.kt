package com.javzzap.laxbrowser.engine.gecko

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.javzzap.laxbrowser.MainActivity
import com.javzzap.laxbrowser.R
import com.javzzap.laxbrowser.domain.UrlInput
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate

/**
 * Shows website notifications (the Notification API) as Android notifications while Lax runs.
 * Background web push is not supported: it needs a push service that GeckoView does not provide.
 */
class WebNotifications(context: Context) : WebNotificationDelegate {
    private val appContext = context.applicationContext
    private val shown = HashMap<String, WebNotification>()

    override fun onShowNotification(notification: WebNotification) {
        if (!canPost()) return
        ensureChannel()
        shown[notification.tag] = notification
        val open = Intent(appContext, MainActivity::class.java)
            .setAction(ACTION_CLICK)
            .putExtra(EXTRA_TAG, notification.tag)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            appContext,
            notification.tag.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val site = UrlInput.displayHost(notification.origin)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title?.takeIf { it.isNotBlank() } ?: site)
            .setContentText(notification.text)
            .setSubText(site)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setSilent(notification.silent)
            .setVisibility(if (notification.privateBrowsing) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
        try {
            NotificationManagerCompat.from(appContext).notify(notification.tag, NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            shown.remove(notification.tag)
        }
    }

    override fun onCloseNotification(notification: WebNotification) {
        shown.remove(notification.tag)
        NotificationManagerCompat.from(appContext).cancel(notification.tag, NOTIFICATION_ID)
    }

    /** Called when the user taps a website notification. */
    fun onClicked(tag: String) {
        shown.remove(tag)?.click()
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.channel_site_notifications), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val ACTION_CLICK = "com.javzzap.laxbrowser.WEB_NOTIFICATION_CLICK"
        const val EXTRA_TAG = "web_notification_tag"
        private const val CHANNEL_ID = "site_notifications"
        private const val NOTIFICATION_ID = 1
    }
}
