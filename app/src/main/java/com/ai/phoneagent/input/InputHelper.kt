/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * Licensed under AGPL-3.0. See LICENSE for details.
 *
 * Adapted from autoglm_KY-master InputHelper.kt
 */
package com.ai.phoneagent.input

import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 输入注入辅助工具（Shizuku + IInputManager，队列化触摸）。
 *
 * **用途**
 * - 通过 Shizuku 包装的 `input` 系统服务 binder 获取 `IInputManager`，反射调用 `injectInputEvent`。
 * - 在后台 `HandlerThread` 上串行处理触摸注入请求，避免主线程阻塞。
 * - 支持虚拟隔离模式下的 display 维度注入：
 * - 在必要时 best-effort 调用 `vdiso.ShizukuVirtualDisplayEngine.ensureFocusedDisplay(displayId)`
 * ```
 *     降低"虚拟屏黑屏/输入路由异常"的概率。
 * ```
 * **典型用法**
 * - `InputHelper.enqueueTouch(displayId, downTime, MotionEvent.ACTION_DOWN, x, y, ensureFocus =
 * true)`
 *
 * **引用路径（常见）**
 * - `VirtualScreenPreviewOverlay`：预览窗触摸层注入。
 *
 * **使用注意事项**
 * - 依赖 Shizuku 权限：未授权时注入会失败（内部为 best-effort）。
 * - Move 事件会做合并/去抖（只保留最新 move），以减少高频注入带来的卡顿。
 */
class InputHelper {

    private val TAG = "AriesInputHelper"
    private val MODE_ASYNC = 0

    private data class TouchCmd(
            val displayId: Int,
            val downTime: Long,
            val action: Int,
            val x: Int,
            val y: Int,
            val ensureFocus: Boolean,
    )

    @Volatile private var thread: HandlerThread? = null

    @Volatile private var handler: Handler? = null

    @Volatile private var inputInjectLastLogMs: Long = 0L

    @Volatile private var lastEnsureVirtualFocusAtMs: Long = 0L

    private class InputManagerAccess(
            val iInputManager: Any,
            val injectInputEvent: java.lang.reflect.Method,
            val setDisplayId: java.lang.reflect.Method?,
    )

    @Volatile private var access: InputManagerAccess? = null

    @Volatile private var loggedInjectSignature: Boolean = false

    private val touchPointerPropertiesTL = ThreadLocal<Array<PointerProperties>>()
    private val touchPointerCoordsTL = ThreadLocal<Array<PointerCoords>>()

    private fun ensureThreadStarted() {
        if (handler != null) return
        val t = HandlerThread("AriesInputInjectionThread")
        t.start()
        thread = t
        handler =
                Handler(t.looper) { msg ->
                    val cmd = msg.obj as? TouchCmd
                    if (cmd != null) {
                        runCatching { handleTouch(cmd) }
                    }
                    true
                }
    }

    fun enqueueTouch(
            displayId: Int,
            downTime: Long,
            action: Int,
            x: Int,
            y: Int,
            ensureFocus: Boolean,
    ) {
        if (displayId <= 0) return
        ensureThreadStarted()
        val h = handler ?: return

        val MSG_MOVE = 2
        val MSG_OTHER = 1
        val what = if (action == MotionEvent.ACTION_MOVE) MSG_MOVE else MSG_OTHER
        val msg =
                h.obtainMessage(
                        what,
                        TouchCmd(
                                displayId = displayId,
                                downTime = downTime,
                                action = action,
                                x = x,
                                y = y,
                                ensureFocus = ensureFocus,
                        )
                )

        if (what == MSG_MOVE) {
            // MOVE 事件合并去抖：丢弃过期 MOVE，只保留最新
            h.removeMessages(MSG_MOVE)
            h.sendMessage(msg)
        } else {
            // DOWN/UP 事件优先处理
            h.sendMessageAtFrontOfQueue(msg)
        }
    }

    private fun handleTouch(cmd: TouchCmd) {
        if (cmd.displayId <= 0) return

        // 完全隔离模式：不切换系统焦点，所有触摸通过 displayId 直接注入到目标 display。
        // 焦点始终保持在主屏（display 0），由 VirtualDisplayController 的周期性焦点强制执行保障。

        val startNs = System.nanoTime()
        val ev =
                buildSingleTouchEvent(
                        displayId = cmd.displayId,
                        downTime = cmd.downTime,
                        x = cmd.x.toFloat(),
                        y = cmd.y.toFloat(),
                        action = cmd.action,
                )
        try {
            injectInputEventAsync(ev)
        } finally {
            ev.recycle()
        }

        val endNs = System.nanoTime()
        val nowMs = SystemClock.uptimeMillis()

        if (nowMs - inputInjectLastLogMs >= 1000L) {
            inputInjectLastLogMs = nowMs
            val injectMs = (endNs - startNs) / 1_000_000.0
            Log.i(
                    TAG,
                    "inject: action=${cmd.action} ms=$injectMs display=${cmd.displayId} xy=${cmd.x},${cmd.y}"
            )
        }
    }

    private fun buildSingleTouchEvent(
            displayId: Int,
            downTime: Long,
            x: Float,
            y: Float,
            action: Int,
    ): MotionEvent {
        val now = SystemClock.uptimeMillis()

        val props =
                touchPointerPropertiesTL.get()
                        ?: arrayOf(
                                        PointerProperties().apply {
                                            id = 0
                                            toolType = MotionEvent.TOOL_TYPE_FINGER
                                        }
                                )
                                .also { touchPointerPropertiesTL.set(it) }

        val coords =
                touchPointerCoordsTL.get()
                        ?: arrayOf(
                                        PointerCoords().apply {
                                            pressure = 1f
                                            size = 1f
                                        }
                                )
                                .also { touchPointerCoordsTL.set(it) }

        coords[0].x = x
        coords[0].y = y

        val ev =
                MotionEvent.obtain(
                        downTime,
                        now,
                        action,
                        1,
                        props,
                        coords,
                        0,
                        0,
                        1f,
                        1f,
                        0,
                        0,
                        InputDevice.SOURCE_TOUCHSCREEN,
                        0,
                )

        applyDisplayIdBestEffort(ev, displayId)
        return ev
    }

    private fun getRawService(name: String): IBinder {
        val sm = Class.forName("android.os.ServiceManager")
        val m = sm.getMethod("getService", String::class.java)
        return (m.invoke(null, name) as? IBinder)
                ?: throw IllegalStateException("Service not found: $name")
    }

    private fun getIInputManagerAccess(): InputManagerAccess {
        val existing = access
        if (existing != null) return existing

        val binder = ShizukuBinderWrapper(getRawService("input"))
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val asInterface = stub.getMethod("asInterface", IBinder::class.java)
        val iim =
                asInterface.invoke(null, binder)
                        ?: throw IllegalStateException("IInputManager.asInterface returned null")

        val inject =
                iim.javaClass
                        .methods
                        .asSequence()
                        .filter { it.name == "injectInputEvent" }
                        .filter { it.returnType == Boolean::class.javaPrimitiveType }
                        .filter { m ->
                            val p = m.parameterTypes
                            p.size == 2 &&
                                    InputEvent::class.java.isAssignableFrom(p[0]) &&
                                    p[1] == Int::class.javaPrimitiveType
                        }
                        .firstOrNull()
                        ?: throw NoSuchMethodException(
                                "IInputManager.injectInputEvent(InputEvent,int)"
                        )

        val setDisplayId =
                InputEvent::class.java.methods.firstOrNull { m ->
                    m.name == "setDisplayId" &&
                            m.parameterTypes.size == 1 &&
                            m.parameterTypes[0] == Int::class.javaPrimitiveType
                }

        if (!loggedInjectSignature) {
            loggedInjectSignature = true
            runCatching { Log.i(TAG, "Using ${inject.toGenericString()}") }
        }

        return InputManagerAccess(iim, inject, setDisplayId).also { access = it }
    }

    private fun applyDisplayIdBestEffort(ev: InputEvent, displayId: Int) {
        if (displayId <= 0) return
        val m = getIInputManagerAccess().setDisplayId ?: return
        runCatching { m.invoke(ev, displayId) }
    }

    private fun injectInputEventAsync(ev: InputEvent) {
        val acc = getIInputManagerAccess()
        runCatching { acc.injectInputEvent.invoke(acc.iInputManager, ev, MODE_ASYNC) }
    }

}
