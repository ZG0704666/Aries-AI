package com.ai.phoneagent.core.cache

import android.os.SystemClock
import android.util.Log
import com.ai.phoneagent.AppState
import com.ai.phoneagent.AutomationOverlay
import com.ai.phoneagent.FloatingChatService
import com.ai.phoneagent.ui.UIAutomationProgressOverlay
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

/**
 * 截图期间统一隐藏所有悬浮窗，并在截图结束后恢复。
 *
 * 采用引用计数，支持并发截图请求安全嵌套。
 */
object ScreenshotOverlayGuard {
    private const val TAG = "ScreenshotOverlayGuard"
    private const val STUCK_HIDE_TIMEOUT_MS = 10_000L
    private val hideCounter = AtomicInteger(0)
    @Volatile private var firstHideAtMs: Long = 0L
    @Volatile private var restoreAutomationOverlay = false
    @Volatile private var restoreProgressOverlay = false
    @Volatile private var restoreFloatingOverlay = false

    suspend fun <T> withOverlaysHidden(
            hideDelayMs: Long = 80L,
            block: suspend () -> T
    ): T {
        var hideStarted = false
        val firstHide =
                runCatching {
                            hideStarted = true
                            beginHide()
                        }
                        .getOrElse {
                            Log.e(TAG, "beginHide failed: ${it.message}", it)
                            false
                        }
        return try {
            if (firstHide && hideDelayMs > 0L) {
                delay(hideDelayMs)
            }
            block()
        } finally {
            if (hideStarted) {
                runCatching { endHide() }
                        .onFailure { Log.e(TAG, "endHide failed: ${it.message}", it) }
            }
        }
    }

    private fun beginHide(): Boolean {
        recoverIfStuck()

        val count = hideCounter.incrementAndGet()
        if (count != 1) return false
        firstHideAtMs = SystemClock.elapsedRealtime()

        restoreAutomationOverlay = false
        restoreProgressOverlay = false
        restoreFloatingOverlay = false

        if (AutomationOverlay.isShowing()) {
            restoreAutomationOverlay = true
            runCatching { AutomationOverlay.temporaryHide() }
                    .onFailure {
                        restoreAutomationOverlay = false
                        Log.w(TAG, "temporaryHide automation overlay failed: ${it.message}", it)
                    }
        }

        AppState.getAppContext()?.let { appCtx ->
            val progress = UIAutomationProgressOverlay.getInstance(appCtx)
            if (progress.isShowing()) {
                restoreProgressOverlay = true
                runCatching { progress.temporaryHideForScreenshot() }
                        .onFailure {
                            restoreProgressOverlay = false
                            Log.w(TAG, "temporaryHide progress overlay failed: ${it.message}", it)
                        }
            }
        }

        if (FloatingChatService.isRunning()) {
            restoreFloatingOverlay = true
            runCatching { FloatingChatService.temporaryHideForScreenshot() }
                    .onFailure {
                        restoreFloatingOverlay = false
                        Log.w(TAG, "temporaryHide floating overlay failed: ${it.message}", it)
                    }
        }

        return restoreAutomationOverlay ||
                restoreProgressOverlay ||
                restoreFloatingOverlay
    }

    private fun endHide() {
        val count = hideCounter.decrementAndGet()
        if (count > 0) return

        if (count < 0) {
            Log.w(TAG, "hideCounter underflow detected: $count")
        }
        hideCounter.set(0)
        firstHideAtMs = 0L

        restoreAllTrackedOverlays()
        clearRestoreFlags()
    }

    private fun recoverIfStuck() {
        val currentCount = hideCounter.get()
        if (currentCount <= 0) return

        val startedAt = firstHideAtMs
        if (startedAt <= 0L) return

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (elapsed < STUCK_HIDE_TIMEOUT_MS) return

        Log.w(TAG, "Detected stuck hide state (count=$currentCount, elapsed=${elapsed}ms), forcing restore")
        hideCounter.set(0)
        firstHideAtMs = 0L
        restoreAllTrackedOverlays()
        clearRestoreFlags()
    }

    private fun restoreAllTrackedOverlays() {
        if (restoreFloatingOverlay) {
            runCatching { FloatingChatService.restoreVisibilityAfterScreenshot() }
                    .onFailure {
                        Log.w(TAG, "restore floating overlay failed: ${it.message}", it)
                    }
        }
        AppState.getAppContext()?.let { appCtx ->
            if (restoreProgressOverlay) {
                runCatching {
                            UIAutomationProgressOverlay.getInstance(appCtx)
                                    .restoreVisibilityAfterScreenshot()
                        }
                        .onFailure {
                            Log.w(TAG, "restore progress overlay failed: ${it.message}", it)
                        }
            }
        }
        if (restoreAutomationOverlay) {
            runCatching { AutomationOverlay.restoreVisibility() }
                    .onFailure { Log.w(TAG, "restore automation overlay failed: ${it.message}", it) }
        }
    }

    private fun clearRestoreFlags() {
        restoreAutomationOverlay = false
        restoreProgressOverlay = false
        restoreFloatingOverlay = false
    }
}
