/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 *
 * Adapted from autoglm_KY-master WelcomeActivity.kt
 */
package com.ai.phoneagent

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager

/**
 * 虚拟屏欢迎/兜底 Activity — 焦点隔离模式。
 *
 * **用途**
 * - 在虚拟隔离模式创建新的 VirtualDisplay 后，在目标 display 上启动该 Activity 作为"可见的兜底界面"。
 * - **核心功能**：拦截虚拟屏接收到的所有物理按键，并立即恢复焦点到主屏，实现完全的输入隔离
 *
 * **焦点隔离策略**:
 * 1. 设置窗口为 `FLAG_NOT_FOCUSABLE` — 告诉系统这个 Activity 不应该获得焦点
 * 2. 但仍然创建 Window（避免虚拟屏黑屏）
 * 3. 拦截所有 KeyEvent（backPressed, home 等）并立即恢复焦点到主屏
 * 4. 这样虚拟屏虽然有可见内容，但系统的硬件按键路由会被及时打断
 *
 * **使用注意事项**
 * - `taskAffinity` 设为 `${applicationId}.virtual`，独立于主 Activity 的任务栈。
 * - `dispatchKeyEvent()` 覆盖了 `onBackPressed()`，更早地拦截按键
 */
class WelcomeActivity : Activity() {

    private val TAG = "WelcomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome_virtual)

        // ─── 关键：设置窗口不可获得焦点 ───
        // 这告诉系统 WindowManager：这个 Activity 是"背景装饰"，不应参与焦点竞争
        // 但由于我们设置了 TYPE_APPLICATION，窗口仍然可见
        runCatching {
            val attrs = window?.attributes
            if (attrs != null) {
                // FLAG_NOT_FOCUSABLE = 0x00000008：此窗口不能获得焦点
                // FLAG_NOT_TOUCHABLE = 0x00000010：此窗口不接收触摸事件（可选）
                // 这两个 flag 叠加使用，会让 Activity 彻底"透明"给输入系统
                attrs.flags =
                        attrs.flags or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                window?.attributes = attrs
                Log.i(TAG, "Window attributes set: NOT_FOCUSABLE | NOT_TOUCHABLE")
            }
        }

        // 立即恢复焦点到主屏
        scheduleFocusRestore()
    }

    /**
     * 拦截所有按键事件，立即恢复焦点到主屏。 这是防止虚拟屏接收用户按键的最后防线。
     *
     * @return true 表示此 Activity 处理了事件（但实际上我们不消费，而是转交给主屏）
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            // 检测到有按键事件进入虚拟屏 Activity → 立即恢复焦点到主屏
            Log.d(
                    TAG,
                    "Key event detected on virtual display: ${event.keyCode}, restoring focus to main"
            )
            restoreFocusToMainDisplay()
        }
        // 不消费事件，让其继续传递（给到虚拟屏上的其他 Window）
        return super.dispatchKeyEvent(event)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // 返回键被拦截，吞掉。但 dispatchKeyEvent 已经处理了焦点恢复。
        // (保留这个方法以防 dispatchKeyEvent 被绕过)
        Log.d(TAG, "Back pressed on virtual display, ignored")
        return
    }

    /** 立即恢复焦点到主屏（display 0）。 通过 Shizuku + Input Manager 的反射调用实现。 */
    private fun restoreFocusToMainDisplay() {
        runCatching { VirtualDisplayController.restoreFocusToDefaultDisplayNow() }.onFailure {
            Log.e(TAG, "Failed to restore focus to main display", it)
        }
    }

    /** 延迟恢复焦点到主屏（备用机制）。 当 Activity 创建时，系统的自动焦点切换可能还在进行中。 延迟执行可以确保在 Activity Window 完全创建后再恢复焦点。 */
    private fun scheduleFocusRestore() {
        window?.decorView?.postDelayed(
                {
                    Log.d(TAG, "Scheduled focus restore executing")
                    restoreFocusToMainDisplay()
                },
                500
        )
    }
}
