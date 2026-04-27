package com.ai.phoneagent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ai.phoneagent.viewmodel.AutomationViewModel

object AutomationLiveNotification {
    private const val TAG = "AutomationLiveNotification"
    private const val CHANNEL_ID = "automation_live_updates_visible"
    private const val NOTIFICATION_ID = 42026
    private const val MAX_PROGRESS = 100
    private const val MIN_UPDATE_INTERVAL_MS = 900L

    @Volatile private var registered = false
    @Volatile private var active = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var title: String = "Aries AI"
    private var subtitle: String = "自动化执行中"
    private var detail: String = "准备开始"
    private var progress: Int = 0
    private var maxSteps: Int = 1
    private var estimatedTotalSteps: Int = 0
    private var hasEstimatedSteps: Boolean = false
    private var navigateMainOnClick: Boolean = false
    private var lastNotifyElapsedMs: Long = 0L
    private var notifyScheduled = false

    fun initialize(context: Context): Boolean {
        val ctx = context.applicationContext
        appContext = ctx
        createChannel(ctx)
        registered = canUseLiveUpdate(ctx)
        Log.i(TAG, "live update registered=$registered")
        return registered
    }

    fun isRegistered(context: Context? = appContext): Boolean {
        val ctx = context?.applicationContext ?: return registered
        registered = canUseLiveUpdate(ctx)
        return registered
    }

    fun isActive(): Boolean = active

    fun show(
        context: Context,
        title: String,
        subtitle: String,
        maxSteps: Int,
        navigateMainOnClick: Boolean,
    ): Boolean {
        val ctx = context.applicationContext
        appContext = ctx
        createChannel(ctx)
        if (!isRegistered(ctx)) return false

        this.title = title.ifBlank { "Aries AI" }
        this.subtitle = subtitle.ifBlank { "自动化执行中" }
        this.detail = "准备开始"
        this.progress = 0
        this.maxSteps = maxSteps.coerceAtLeast(1)
        this.estimatedTotalSteps = 0
        this.hasEstimatedSteps = false
        this.navigateMainOnClick = navigateMainOnClick
        this.lastNotifyElapsedMs = 0L
        this.notifyScheduled = false
        active = true

        return notifyCurrent(completed = false, immediate = true).also { ok ->
            if (!ok) {
                active = false
            }
        }
    }

    fun updateEstimatedSteps(estimated: Int) {
        if (!active || estimated <= 0 || hasEstimatedSteps) return
        estimatedTotalSteps = estimated
        hasEstimatedSteps = true
    }

    fun updateProgress(step: Int, phaseInStep: Float, maxSteps: Int? = null, subtitle: String? = null) {
        if (!active) return
        if (maxSteps != null && !hasEstimatedSteps) {
            this.maxSteps = maxSteps.coerceAtLeast(1)
        }
        subtitle?.trim()?.takeIf { it.isNotBlank() }?.let {
            this.subtitle = it.take(48)
        }
        val current = calculateProgressPercent(step.coerceAtLeast(0))
        val next = calculateProgressPercent(step.coerceAtLeast(0) + 1)
        val t = phaseInStep.coerceIn(0f, 1f)
        progress = (current + ((next - current) * t)).toInt().coerceIn(0, MAX_PROGRESS)
        notifyCurrent(completed = false)
    }

    fun updateState(title: String? = null, subtitle: String? = null, detail: String? = null) {
        if (!active) return
        title?.trim()?.takeIf { it.isNotBlank() }?.let { this.title = it.take(48) }
        subtitle?.trim()?.takeIf { it.isNotBlank() }?.let { this.subtitle = it.take(64) }
        detail?.trim()?.takeIf { it.isNotBlank() }?.let { this.detail = it.take(96) }
        notifyCurrent(completed = false)
    }

    fun updateFromLogLine(line: String) {
        if (!active) return
        val normalized = simplifyLine(line).trim()
        if (normalized.isBlank()) return
        detail = normalized.take(96)
        parseStep(line)?.let { step ->
            progress = calculateProgressPercent(step).coerceIn(0, MAX_PROGRESS)
        }
        notifyCurrent(completed = false)
    }

    fun complete(message: String) {
        if (!active) return
        title = "已完成"
        subtitle = message.ifBlank { "任务完成" }.take(64)
        detail = "任务完成"
        progress = MAX_PROGRESS
        notifyCurrent(completed = true, immediate = true)
        active = false
    }

    fun hide() {
        val ctx = appContext ?: return
        active = false
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }

    private fun canUseLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.canPostPromotedNotifications() }
            .getOrElse {
                Log.w(TAG, "canPostPromotedNotifications failed", it)
                false
            }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "自动化实时进度",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "显示 Aries AI 自动化任务的实时进度"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        manager.createNotificationChannel(channel)
    }

    private fun notifyCurrent(completed: Boolean, immediate: Boolean = false): Boolean {
        if (!immediate && !shouldNotifyNow(completed)) return true
        val ctx = appContext ?: return false
        val notification = buildNotification(ctx, completed)
        if (!completed &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            !notification.hasPromotableCharacteristics()
        ) {
            Log.w(TAG, "notification does not have promotable characteristics")
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
            lastNotifyElapsedMs = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            Log.w(TAG, "notify failed", it)
            false
        }
    }

    private fun shouldNotifyNow(completed: Boolean): Boolean {
        if (completed) return true
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastNotifyElapsedMs
        if (elapsed >= MIN_UPDATE_INTERVAL_MS) {
            notifyScheduled = false
            return true
        }
        if (!notifyScheduled) {
            notifyScheduled = true
            mainHandler.postDelayed(
                {
                    notifyScheduled = false
                    if (active) {
                        notifyCurrent(completed = false, immediate = true)
                    }
                },
                MIN_UPDATE_INTERVAL_MS - elapsed,
            )
        }
        return false
    }

    private fun buildNotification(context: Context, completed: Boolean): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    )
                    if (navigateMainOnClick) {
                        putExtra(MainActivity.EXTRA_SCROLL_TO_BOTTOM, true)
                        putExtra(MainActivity.EXTRA_SHOW_AUTOMATION_STOP, true)
                    } else {
                        putExtra(AutomationViewModel.EXTRA_FORCE_TOP_ON_ENTRY, true)
                    }
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(!completed)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0)
            .setVibrate(LongArray(0))
            .setProgress(MAX_PROGRESS, progress, false)
            .setRequestPromotedOngoing(!completed)
            .setAutoCancel(completed)
            .setTimeoutAfter(if (completed) 3_000L else 0L)
            .build()
    }

    private fun calculateProgressPercent(step: Int): Int {
        return if (hasEstimatedSteps && estimatedTotalSteps > 0) {
            (step.toFloat() / estimatedTotalSteps * MAX_PROGRESS).toInt().coerceIn(0, MAX_PROGRESS)
        } else {
            val normalized = step.toFloat() / 15f
            val fakeProgress =
                when {
                    normalized < 0.3f -> normalized * 2f
                    normalized < 0.6f -> 0.6f + (normalized - 0.3f) * 0.8f
                    else -> 0.84f + kotlin.math.min((normalized - 0.6f) * 0.15f, 0.06f)
                }
            (fakeProgress * MAX_PROGRESS).toInt().coerceIn(0, 90)
        }
    }

    private fun parseStep(line: String): Int? {
        return Regex("\\[Step\\s+(\\d+)]").find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun simplifyLine(line: String): String {
        return line.trim().replace(Regex("\\[Step\\s+\\d+]\\s*"), "").trim()
    }
}
