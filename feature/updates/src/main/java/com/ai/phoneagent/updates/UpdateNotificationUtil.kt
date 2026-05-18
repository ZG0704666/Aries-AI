package com.ai.phoneagent.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ai.phoneagent.feature.updates.R

object UpdateNotificationUtil {

    const val EXTRA_SHOW_UPDATE_DIALOG = "extra_show_update_dialog"

    private const val MAIN_ACTIVITY_CLASS = "com.ai.phoneagent.MainActivity"
    private const val CHANNEL_ID = "update_channel"
    private const val NOTIFICATION_ID = 3101

    fun notifyNewVersion(context: Context, entry: ReleaseEntry): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        createChannel(context)

        val intent =
            Intent()
                .setClassName(context, MAIN_ACTIVITY_CLASS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SHOW_UPDATE_DIALOG, true)

        val pi =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notif =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_update)
                .setContentTitle(context.getString(R.string.about_update_found_format, entry.versionTag))
                .setContentText(context.getString(R.string.m3t_updates_view_release))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        return true
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.enableVibration(false)
        channel.setSound(null, null)
        channel.setShowBadge(true)
        manager.createNotificationChannel(channel)
    }
}
