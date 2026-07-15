/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 *
 * Adapted from autoglm_KY-master ImeFocusDeadlockController.kt
 */
package com.ai.phoneagent.vdiso

import android.os.SystemClock
import android.util.Log
import com.ai.phoneagent.ShizukuBridge
import com.ai.phoneagent.VirtualDisplayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 输入法焦点死锁监测与自愈控制器（虚拟隔离模式）。
 *
 * **背景问题**
 * - 在部分 ROM / Android 版本中，VirtualDisplay + IME 弹出时可能出现"焦点来回抢占/输入路由异常"， 导致虚拟屏持续黑屏、触摸/按键无效或输入法无法正常关闭。
 *
 * **策略**
 * - 轮询 `dumpsys window windows`，解析目标 `displayId` 上 IME 是否处于激活状态。
 * - 一旦检测到 IME 激活，进入 FORCE_LOCK：高频执行 `wm set-focused-display <displayId>` 将焦点强制锁定在虚拟屏，直到 IME 消失。
 * - 通过 [Callback] 向上层反馈锁定状态变化。
 *
 * **典型用法**
 * ```kotlin
 * val controller = ImeFocusDeadlockController(workerScope)
 * controller.callback = myCallback
 * controller.start()
 * // ... destroy 时
 * controller.stop()
 * ```
 */
class ImeFocusDeadlockController(
        private val scope: CoroutineScope,
        private val pollIntervalMs: Long = 220L,
        private val forceIntervalMs: Long = 90L,
) {

    interface Callback {
        fun onLockStateChanged(locked: Boolean, displayId: Int, detail: String)
    }

    private val TAG = "AriesImeFocusLock"

    @Volatile private var running: Boolean = false
    @Volatile private var locked: Boolean = false

    private var monitorJob: Job? = null
    private var forceLockJob: Job? = null

    var callback: Callback? = null

    fun start() {
        if (running) return
        running = true
        monitorJob = scope.launch { loopMonitor() }
    }

    fun stop() {
        running = false
        monitorJob?.cancel()
        monitorJob = null
        stopForceLock("stop")
    }

    fun isLocked(): Boolean = locked

    // ─── 主循环 ───

    private suspend fun loopMonitor() {
        var lastLogAt = 0L
        while (scope.isActive && running) {
            val did = VirtualDisplayController.getDisplayId() ?: 0
            val t0 = SystemClock.uptimeMillis()
            val shizukuReady =
                    runCatching { ShizukuBridge.pingBinder() && ShizukuBridge.hasPermission() }
                            .getOrDefault(false)

            val ime =
                    if (did > 0 && shizukuReady) {
                        val r = ShizukuBridge.execResult("dumpsys window windows")
                        val text = if (r.exitCode == 0) r.stdoutText() else ""
                        parseImeActive(text, did)
                    } else {
                        ImeParseResult(false, "did=$did shizukuReady=$shizukuReady")
                    }
            val cost = SystemClock.uptimeMillis() - t0

            if (ime.active && !locked) {
                locked = true
                Log.i(TAG, "IME detected → FORCE_LOCK did=$did cost=${cost}ms detail=${ime.detail}")
                callback?.onLockStateChanged(true, did, ime.detail)
                startForceLock(did)
            } else if (!ime.active && locked) {
                locked = false
                Log.i(TAG, "IME gone → exit FORCE_LOCK did=$did cost=${cost}ms")
                stopForceLock("ime_gone")
                callback?.onLockStateChanged(false, did, ime.detail)
            }

            val now = SystemClock.uptimeMillis()
            if (now - lastLogAt >= 1500L) {
                lastLogAt = now
                Log.d(TAG, "tick: did=$did locked=$locked imeActive=${ime.active} cost=${cost}ms")
            }

            delay(if (did <= 0 || !shizukuReady) pollIntervalMs * 2 else pollIntervalMs)
        }
    }

    // ─── 强制锁定焦点 ───

    private fun startForceLock(displayId: Int) {
        if (displayId <= 0) return
        if (forceLockJob?.isActive == true) return

        forceLockJob =
                scope.launch {
                    var n = 0L
                    var lastLogAt = 0L
                    // 持续锁定焦点直到 IME 消失（对齐 autoglm_KY：无上限）
                    while (scope.isActive && running && locked) {
                        val cmd = "wm set-focused-display $displayId"
                        val r = ShizukuBridge.execResult(cmd)
                        n++
                        val now = SystemClock.uptimeMillis()
                        if (now - lastLogAt >= 800L) {
                            lastLogAt = now
                            Log.d(TAG, "forceLock: did=$displayId n=$n exit=${r.exitCode}")
                        }
                        delay(forceIntervalMs)
                    }
                }
    }

    private fun stopForceLock(reason: String) {
        forceLockJob?.cancel()
        forceLockJob = null
        Log.d(TAG, "forceLock stopped: reason=$reason")
    }

    // ─── IME 解析 ───

    private data class ImeParseResult(val active: Boolean, val detail: String)

    private fun parseImeActive(text: String, displayId: Int): ImeParseResult {
        if (text.isEmpty()) return ImeParseResult(false, "empty dumpsys")

        var inImeBlock = false
        var imeTitle: String? = null
        var hasSurface = false
        var viewVisibility: Int? = null
        var displayMatched = false

        fun isWindowHeader(line: String) =
                line.contains("Window{") || line.trimStart().startsWith("Window #")
        fun isImeHeader(line: String): Boolean {
            if (!isWindowHeader(line)) return false
            val l = line.lowercase()
            return l.contains("inputmethod") || l.contains("input method")
        }
        fun matchDisplay(line: String) =
                line.contains("displayId=$displayId") ||
                        line.contains("mDisplayId=$displayId") ||
                        line.contains("displayId $displayId") ||
                        line.contains("displayId=$displayId,") ||
                        line.contains("displayId=$displayId ")

        fun parseIntValue(line: String, key: String): Int? {
            val idx = line.indexOf(key)
            if (idx < 0) return null
            var i = idx + key.length
            while (i < line.length && line[i] == ' ') i++
            if (i + 1 < line.length && line[i] == '0' && (line[i + 1] == 'x' || line[i + 1] == 'X')
            ) {
                i += 2
                val hex = StringBuilder()
                while (i < line.length) {
                    val c = line[i]
                    val isHex = c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F')
                    if (!isHex) break
                    hex.append(c)
                    i++
                }
                return if (hex.isEmpty()) null else hex.toString().toIntOrNull(16)
            }
            val sb = StringBuilder()
            while (i < line.length) {
                val c = line[i]
                if (c == '-' || c.isDigit()) {
                    sb.append(c)
                    i++
                } else break
            }
            return if (sb.isEmpty()) null else sb.toString().toIntOrNull()
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (isImeHeader(line)) {
                inImeBlock = true
                imeTitle = line.take(160)
                hasSurface = false
                viewVisibility = null
                displayMatched = false
            } else if (inImeBlock && isWindowHeader(line) && !isImeHeader(line)) {
                inImeBlock = false
            }
            if (!inImeBlock) continue

            if (line.contains("mHasSurface=true") || line.contains("hasSurface=true"))
                    hasSurface = true
            parseIntValue(line, "mViewVisibility=")?.let { viewVisibility = it }
            if (matchDisplay(line)) displayMatched = true

            val notGone = viewVisibility != 8
            if (hasSurface && displayMatched && notGone) {
                return ImeParseResult(
                        true,
                        "title=${imeTitle ?: ""} surface=$hasSurface display=$displayMatched viewVis=$viewVisibility"
                )
            }
        }
        return ImeParseResult(
                false,
                "hasSurface=$hasSurface viewVis=$viewVisibility displayMatched=$displayMatched"
        )
    }
}
