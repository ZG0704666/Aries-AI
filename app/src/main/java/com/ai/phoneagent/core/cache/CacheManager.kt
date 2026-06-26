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
package com.ai.phoneagent.core.cache

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 统一缓存管理器
 * 监听系统内存压力，触发各缓存模块的清理机制
 */
object CacheManager : ComponentCallbacks2 {

    private const val TAG = "CacheManager"

    /**
     * 可清理的缓存接口
     */
    interface EvictableCache {
        /** 清理过期或低优先级条目 */
        fun evictExpired()
        /** 清理所有缓存（紧急情况） */
        fun clear()
        /** 获取缓存名称 */
        fun getName(): String
    }

    private val registeredCaches = CopyOnWriteArrayList<EvictableCache>()

    /**
     * 注册缓存到统一管理器
     */
    fun register(cache: EvictableCache) {
        if (!registeredCaches.contains(cache)) {
            registeredCaches.add(cache)
            Log.d(TAG, "缓存已注册: ${cache.getName()}")
        }
    }

    /**
     * 注销缓存
     */
    fun unregister(cache: EvictableCache) {
        registeredCaches.remove(cache)
        Log.d(TAG, "缓存已注销: ${cache.getName()}")
    }

    /**
     * 初始化，注册到 Application 的 ComponentCallbacks2
     */
    fun init(context: Context) {
        val app = context.applicationContext
        if (app is Application) {
            app.registerComponentCallbacks(this)
            Log.d(TAG, "CacheManager 已初始化，注册到 Application")
        } else {
            Log.w(TAG, "无法注册 ComponentCallbacks2: applicationContext 不是 Application")
        }
    }

    /**
     * 内存压力中等 - 清理过期条目
     */
    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.d(TAG, "内存压力 (level=$level)，清理过期缓存条目")
                registeredCaches.forEach { cache ->
                    try {
                        cache.evictExpired()
                    } catch (e: Exception) {
                        Log.e(TAG, "清理缓存 ${cache.getName()} 失败: ${e.message}")
                    }
                }
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.d(TAG, "内存压力 (level=$level)，清理所有缓存")
                registeredCaches.forEach { cache ->
                    try {
                        cache.clear()
                    } catch (e: Exception) {
                        Log.e(TAG, "清空缓存 ${cache.getName()} 失败: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // 不处理配置变化
    }

    override fun onLowMemory() {
        Log.d(TAG, "系统低内存，清理所有缓存")
        registeredCaches.forEach { cache ->
            try {
                cache.clear()
            } catch (e: Exception) {
                Log.e(TAG, "清空缓存 ${cache.getName()} 失败: ${e.message}")
            }
        }
    }
}
