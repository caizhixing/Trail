package io.github.caizhixing.trail.debug.viewer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import io.github.caizhixing.trail.debug.R

/**
 * Device-side notifier (behavior 5.6): after `Trail.init` a persistent
 * low-importance notification offers the viewer entry. Channels exist since
 * API 26 == minSdk, so no runtime version guard is needed; Android 13+
 * visibility depends on the host app holding POST_NOTIFICATIONS (D-6: the
 * library only declares the permission, it never requests it).
 */
internal object AndroidViewerNotifier : ViewerNotifier {

    override fun showEntryNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            VIEWER_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.trail_viewer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        manager.notify(NOTIFICATION_ID, buildNotification(context))
    }

    private fun buildNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            TrailViewerActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, VIEWER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trail_viewer_notification)
            .setContentTitle(context.getString(R.string.trail_viewer_notification_title))
            .setContentText(context.getString(R.string.trail_viewer_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private const val NOTIFICATION_ID = 5105
    private const val REQUEST_CODE = 0
}
