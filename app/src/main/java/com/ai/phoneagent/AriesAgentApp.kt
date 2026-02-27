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
package com.ai.phoneagent

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 全局 Application 入口。
 *
 * **用途**
 * - 承担应用进程启动时的最早初始化：
 *   - Android P(28)+ 下通过 `HiddenApiBypass` 尝试放宽隐藏 API 限制（用于虚拟屏创建等系统服务反射调用）。
 *   - 初始化全局状态（见 [AppState]）。
 *
 * **使用注意事项**
 * - 该初始化必须保持"尽力而为（best effort）"策略：任何失败都不应阻塞 App 启动。
 * - 不要在这里做耗时操作（避免冷启动卡顿）。
 */
class AriesAgentApp : Application() {

    companion object {
        private const val TAG = "AriesAgentApp"

        @JvmStatic
        fun logi(msg: String) {
            android.util.Log.i(TAG, msg)
        }

        @JvmStatic
        fun logw(msg: String, t: Throwable? = null) {
            if (t != null) android.util.Log.w(TAG, msg, t)
            else android.util.Log.w(TAG, msg)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 跟随系统自动切换日夜模式
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // 初始化全局上下文
        AppState.init(this)

        // 初始化 HiddenApiBypass（虚拟屏创建必需）
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                HiddenApiBypass.addHiddenApiExemptions("L")
                logi("HiddenApiBypass initialized successfully")
            }
        } catch (t: Throwable) {
            logw("HiddenApiBypass initialization failed", t)
        }
    }
}

/**
 * 全局状态与跨模块"静态入口"。
 *
 * **用途**
 * - 持有 `applicationContext`（避免各处传递 Context）。
 * - 初始化任务控制器和虚拟屏相关状态。
 *
 * **典型用法**
 * - Kotlin 侧需要 Context 时：`AppState.getAppContext()`
 */
object AppState {

    @Volatile
    private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        AriesAgentApp.logi("AppState initialized")
    }

    @JvmStatic
    fun getAppContext(): Context? = appContext
}

