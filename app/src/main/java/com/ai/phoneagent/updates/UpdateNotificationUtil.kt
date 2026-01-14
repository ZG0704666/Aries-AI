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
import com.ai.phoneagent.AboutActivity
import com.ai.phoneagent.R

object UpdateNotificationUtil {

    const val EXTRA_SHOW_UPDATE_DIALOG = "extra_show_update_dialog"

    private const val CHANNEL_ID = "update_channel"
    private const val CHANNEL_NAME = "应用更新"
    private const val NOTIFICATION_ID = 3101

    fun notifyNewVersion(context: Context, entry: ReleaseEntry): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        createChannel(context)

        val intent =
            Intent(context, AboutActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SHOW_UPDATE_DIALOG, true)

        val pi =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val notif =
            NotificationCompat.Builder(context, CHANNEL_ID)
                // 使用最新应用图标作为通知小图标，确保与启动图一致
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("发现新版本 ${entry.versionTag}")
                .setContentText("点击查看下载链接")
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
        // Use lower importance to reduce interruption; disable vibration for update notices
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.enableVibration(false)
        channel.setSound(null, null)
        channel.setShowBadge(true)
        manager.createNotificationChannel(channel)
    }
}
