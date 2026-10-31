/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.speech

import android.content.Context
import android.util.Log

/**
 * 进程级共享的按住说话识别器所有者（修复 R-07：双模型所有者双份 native 内存）。
 *
 * 背景：此前 `MainActivity` 与 `AutomationViewModel` 各自创建
 * [SherpaSpeechRecognizer]，导致同一进程可能驻留两份 native 模型内存。
 *
 * 现在的契约：
 * - 整个进程最多只有一个 [SherpaSpeechRecognizer] 实例；
 * - 多路 UI（主页输入栏、自动化悬浮任务等）通过 [attach] 各自持有一票引用；
 *   实例由注册表创建，`initialize()` 仍由各调用方在自己的协程里触发（幂等，
 *   内部 CAS + 互斥锁，并发/重复调用安全）；
 * - 任何一方销毁都调用 [release]；引用计数归零时确定性 close（排入会话执行器
 *   队列，与在途会话串行，见 [SherpaSpeechRecognizer.shutdown]）；
 * - 失败的实例由调用方按需 [attach] 重试：仅当引用计数归零并释放后，
 *   下一次 attach 才会创建全新实例；计数未归零时沿用现有实例进行现场重试。
 *
 * 线程模型：所有注册表状态仅在单一 JVM 锁内读写，attach/release 可在主线程
 * 直接调用（release 内部的 close 走会话队列，不阻塞调用方）。
 */
object SherpaSpeechRegistry {

    private const val TAG = "SherpaSpeechRegistry"

    @Volatile
    private var recognizer: SherpaSpeechRecognizer? = null

    /** 引用计数：每一路 UI 生命周期持有一票；仅在本对象锁内读写。 */
    private var refCount = 0

    /**
     * 获取（或首次创建）共享识别器并增加引用计数。
     *
     * 不会在本方法内触发模型初始化：调用方在自己的协程中对返回实例调用
     * [SherpaSpeechRecognizer.initialize]（幂等）。
     */
    @Synchronized
    fun attach(context: Context): SherpaSpeechRecognizer {
        var rec = recognizer
        if (rec == null) {
            rec = SherpaSpeechRecognizer(context.applicationContext)
            recognizer = rec
            Log.d(TAG, "Created shared SherpaSpeechRecognizer")
        }
        refCount += 1
        return rec
    }

    /** 当前共享实例（可能未初始化或正在初始化）；无人 attach 或已释放时为 null。 */
    @Synchronized
    fun current(): SherpaSpeechRecognizer? = recognizer

    /**
     * 归还一票引用；计数归零时确定性释放 native 资源（幂等）。
     */
    @Synchronized
    fun release() {
        val rec = recognizer ?: return
        if (refCount > 0) refCount -= 1
        if (refCount == 0) {
            recognizer = null
            rec.shutdown()
            Log.d(TAG, "Released shared SherpaSpeechRecognizer (refCount=0)")
        }
    }
}
