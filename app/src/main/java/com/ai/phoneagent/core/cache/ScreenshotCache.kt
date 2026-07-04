package com.ai.phoneagent.core.cache

import android.util.Log
import com.ai.phoneagent.BuildConfig

/**
 * 截图缓存管理器
 * 实现LRU缓存策略，避免短时间内重复截图
 */
class ScreenshotCache(
    private val maxSize: Int = 3,
    private val ttlMs: Long = 2000L
) {

    private val cache: ThreadSafeLruCache<String, Any> = ThreadSafeLruCache(maxSize, ttlMs)

    /**
     * 获取缓存的截图
     * @param key 缓存键（通常基于屏幕状态）
     * @return 有效的截图数据，如果不存在或已过期则返回null
     */
    fun get(key: String): Any? {
        val value = cache.get(key)
        if (value == null) {
            return null
        }
        if (BuildConfig.DEBUG) Log.d("SCREENSHOT_CACHE", "缓存命中: $key")
        return value
    }

    /**
     * 存储截图到缓存
     * @param key 缓存键
     * @param screenshot 截图数据
     */
    fun put(key: String, screenshot: Any) {
        cache.put(key, screenshot)
        if (BuildConfig.DEBUG) Log.d("SCREENSHOT_CACHE", "缓存存储: $key, 总数: ${cache.size()}")
    }

    /**
     * 清除所有缓存
     */
    fun clear() {
        cache.clear()
        if (BuildConfig.DEBUG) Log.d("SCREENSHOT_CACHE", "缓存已清空")
    }

    /**
     * 移除过期条目
     */
    fun evictExpired() {
        val removed = cache.evictExpired()
        if (removed > 0 && BuildConfig.DEBUG) {
            Log.d("SCREENSHOT_CACHE", "清理过期缓存: $removed 条")
        }
    }

    /**
     * 获取缓存统计信息
     */
    fun getStats(): Map<String, Int> {
        return mapOf(
            "size" to cache.size(),
            "maxSize" to maxSize
        )
    }

    /**
     * 生成缓存键
     * 基于应用包名和窗口变化时间戳生成唯一键
     */
    fun generateKey(packageName: String, windowEventTime: Long): String {
        val timeSlot = (windowEventTime / 500) * 500
        return "${packageName}_${timeSlot}"
    }
}
