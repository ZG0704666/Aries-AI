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

import android.graphics.BitmapFactory
import android.util.Base64
import com.ai.phoneagent.PhoneAgentAccessibilityService
import com.ai.phoneagent.ShizukuBridge
import com.ai.phoneagent.VirtualDisplayController
import com.ai.phoneagent.core.config.AgentConfiguration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 截图优化管理器 - 整合缓存+节流+压缩+虚拟屏支持
 *
 * 提供统一的截图获取接口，集成缓存和节流功能 支持虚拟屏模式（后台隔离执行）
 */
class ScreenshotManager(private val config: AgentConfiguration = AgentConfiguration.DEFAULT) {
    private val cache =
            ScreenshotCache(
                    maxSize = config.screenshotCacheMaxSize,
                    ttlMs = config.screenshotCacheTtlMs
            )
    private val throttler =
            ScreenshotThrottler(minIntervalMs = config.screenshotThrottleMinIntervalMs)
    private val mutex = Mutex()

    /**
     * 优化的截图获取方法
     * 1. 检查是否启用虚拟屏模式，如是则使用虚拟屏截图
     * 2. 检查节流器，防止频繁截图
     * 3. 检查缓存，避免重复截图
     * 4. 执行截图并压缩优化
     */
    suspend fun getOptimizedScreenshot(
            service: PhoneAgentAccessibilityService?
    ): PhoneAgentAccessibilityService.ScreenshotData? {
        // 优先检查虚拟屏模式
        if (config.useBackgroundVirtualDisplay) {
            return getVirtualDisplayScreenshot()
        }

        // Shizuku 交互路径使用实时截图：
        // 1) 不使用缓存（避免 service 为空/事件不更新时出现陈旧截图）
        // 2) 不使用节流（避免无缓存时被节流导致截图空档）
        if (config.useShizukuInteraction) {
            return getShizukuScreenshot()
        }

        // 检查节流器
        if (!throttler.canTakeScreenshot()) {
            val remainingWait = throttler.getRemainingWaitTime()
            if (remainingWait > 0 && config.enableScreenshotCache) {
                // 尝试从缓存获取
                val cached = getFromCache(service)
                if (cached != null) return cached
            }
            return null
        }

        // 检查缓存
        if (config.enableScreenshotCache) {
            val cached = getFromCache(service)
            if (cached != null) {
                throttler.reset() // 使用缓存时也重置节流时间
                return cached
            }
        }

        // 执行截图
        val screenshot =
                if (config.useShizukuInteraction) {
                    getShizukuScreenshot()
                } else {
                    service?.tryCaptureScreenshotBase64()
                }
        if (screenshot != null && config.enableScreenshotCache) {
                putToCache(service, screenshot)
            cache.evictExpired()
        }

        return screenshot
    }

    /** 获取虚拟屏截图 */
    private suspend fun getVirtualDisplayScreenshot(): PhoneAgentAccessibilityService.ScreenshotData? {
        return ScreenshotOverlayGuard.withOverlaysHidden(hideDelayMs = 0L) {
            try {
                val b64 = VirtualDisplayController.screenshotPngBase64NonBlack()
                if (b64.isNotEmpty()) {
                    // 使用虚拟屏最新内容尺寸（与参考实现一致）
                    val (vw, vh) = VirtualDisplayController.getContentSizeBestEffort()
                    PhoneAgentAccessibilityService.ScreenshotData(
                            base64Png = b64,
                            width = vw,
                            height = vh,
                            mimeType = "image/png",
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 通过 Shizuku 截图（仅在 Shizuku 模式下使用） */
    private suspend fun getShizukuScreenshot(): PhoneAgentAccessibilityService.ScreenshotData? {
        return ScreenshotOverlayGuard.withOverlaysHidden(config.screenshotOverlayHideDelayMs) {
            if (!ShizukuBridge.isShizukuAvailable()) return@withOverlaysHidden null

            val pngBytes = ShizukuBridge.execBytes("screencap -p")
            if (pngBytes.isEmpty()) return@withOverlaysHidden null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size, options)
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return@withOverlaysHidden null

            val base64Png = runCatching { Base64.encodeToString(pngBytes, Base64.NO_WRAP) }.getOrNull()
            if (base64Png.isNullOrBlank()) return@withOverlaysHidden null

            PhoneAgentAccessibilityService.ScreenshotData(
                    width = width,
                    height = height,
                    base64Png = base64Png,
                    mimeType = "image/png",
            )
        }
    }

    /** 从缓存获取截图 */
    private fun getFromCache(
            service: PhoneAgentAccessibilityService?
    ): PhoneAgentAccessibilityService.ScreenshotData? {
        if (!config.enableScreenshotCache) return null
        val cacheKey = resolveCacheKey(service) ?: return null

        @Suppress("UNCHECKED_CAST")
        return cache.get(cacheKey) as? PhoneAgentAccessibilityService.ScreenshotData
    }

    /** 存储截图到缓存 */
    private fun putToCache(
            service: PhoneAgentAccessibilityService?,
            screenshot: PhoneAgentAccessibilityService.ScreenshotData
    ) {
        if (!config.enableScreenshotCache) return
        val cacheKey = resolveCacheKey(service) ?: return

        cache.put(cacheKey, screenshot)
    }

    private fun resolveCacheKey(service: PhoneAgentAccessibilityService?): String? {
        // Shizuku 交互路径下，窗口事件/前台包名可能缺失或长期不更新，禁用缓存避免陈旧截图。
        if (config.useShizukuInteraction) return null

        val svc = service ?: return null
        val currentApp = svc.currentAppPackage().takeIf { it.isNotBlank() } ?: return null
        val windowEventTime = svc.lastWindowEventTime().takeIf { it > 0L } ?: return null
        return cache.generateKey(currentApp, windowEventTime)
    }

    /** 清理截图缓存（在任务开始/结束时调用） */
    suspend fun clear() {
        mutex.withLock {
            if (config.enableScreenshotCache) {
                cache.clear()
            }
            if (config.enableScreenshotThrottle) {
                throttler.reset()
            }
        }
    }

    /** 获取缓存状态 */
    fun getCacheStatus(): Map<String, Any> {
        return mapOf(
                "cacheStats" to cache.getStats(),
                "throttleStatus" to throttler.getStatus(),
                "config" to
                        mapOf(
                                "cacheEnabled" to config.enableScreenshotCache,
                                "throttleEnabled" to config.enableScreenshotThrottle
                        )
        )
    }
}

