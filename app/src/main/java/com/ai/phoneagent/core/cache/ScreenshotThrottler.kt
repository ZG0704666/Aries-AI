package com.ai.phoneagent.core.cache

import android.util.Log

/**
 * 截图节流器
 * 防止短时间内频繁截图，提升性能
 */
class ScreenshotThrottler(
    private val minIntervalMs: Long = 1100L    // 最小间隔时间（1.1秒）
) {
    
    @Volatile
    private var lastScreenshotTime: Long = 0L
    
    /**
     * 检查是否可以进行截图
     * @return true: 可以截图, false: 需要等待
     */
    @Synchronized
    fun canTakeScreenshot(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScreenshot = currentTime - lastScreenshotTime
        
        if (timeSinceLastScreenshot >= minIntervalMs) {
            lastScreenshotTime = currentTime
            Log.d("SCREENSHOT_THROTTLE", "截图允许，间隔: ${timeSinceLastScreenshot}ms")
            return true
        } else {
            val remainingWait = minIntervalMs - timeSinceLastScreenshot
            Log.d("SCREENSHOT_THROTTLE", "截图节流，还需等待: ${remainingWait}ms")
            return false
        }
    }
    
    /**
     * 获取距离下次可截图的剩余时间
     * @return 剩余毫秒数，0表示可以立即截图
     */
    @Synchronized
    fun getRemainingWaitTime(): Long {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScreenshot = currentTime - lastScreenshotTime
        return (minIntervalMs - timeSinceLastScreenshot).coerceAtLeast(0L)
    }
    
    /**
     * 强制重置截图时间（用于特殊场景）
     */
    @Synchronized
    fun reset() {
        lastScreenshotTime = 0L
        Log.d("SCREENSHOT_THROTTLE", "截图节流器已重置")
    }
    
    /**
     * 获取节流器状态
     */
    @Synchronized
    fun getStatus(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScreenshot = currentTime - lastScreenshotTime
        return mapOf(
            "lastScreenshotTime" to lastScreenshotTime,
            "timeSinceLastScreenshot" to timeSinceLastScreenshot,
            "remainingWaitTime" to getRemainingWaitTime(),
            "minIntervalMs" to minIntervalMs
        )
    }
}