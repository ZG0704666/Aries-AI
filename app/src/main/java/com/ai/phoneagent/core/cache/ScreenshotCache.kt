package com.ai.phoneagent.core.cache

import android.util.Log

/**
 * 截图缓存管理器
 * 实现LRU缓存策略，避免短时间内重复截图
 */
class ScreenshotCache(
    private val maxSize: Int = 3,           // 最大缓存条目数
    private val ttlMs: Long = 2000L         // 缓存过期时间（2秒）
) {
    
    private data class CacheEntry(
        val screenshot: Any,                // 截图数据（PhoneAgentAccessibilityService.ScreenshotData）
        val timestamp: Long                 // 创建时间戳
    )
    
    // LRU缓存实现
    private val cache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > maxSize
        }
    }
    
    /**
     * 获取缓存的截图
     * @param key 缓存键（通常基于屏幕状态）
     * @return 有效的截图数据，如果不存在或已过期则返回null
     */
    @Synchronized
    fun get(key: String): Any? {
        val entry = cache[key] ?: return null
        
        // 检查是否过期
        val currentTime = System.currentTimeMillis()
        if (currentTime - entry.timestamp > ttlMs) {
            cache.remove(key)
            Log.d("SCREENSHOT_CACHE", "缓存过期移除: $key")
            return null
        }
        
        Log.d("SCREENSHOT_CACHE", "缓存命中: $key")
        return entry.screenshot
    }
    
    /**
     * 存储截图到缓存
     * @param key 缓存键
     * @param screenshot 截图数据
     */
    @Synchronized
    fun put(key: String, screenshot: Any) {
        val currentTime = System.currentTimeMillis()
        cache[key] = CacheEntry(screenshot, currentTime)
        Log.d("SCREENSHOT_CACHE", "缓存存储: $key, 总数: ${cache.size}")
    }
    
    /**
     * 清除所有缓存
     */
    @Synchronized
    fun clear() {
        cache.clear()
        Log.d("SCREENSHOT_CACHE", "缓存已清空")
    }
    
    /**
     * 移除过期条目
     */
    @Synchronized
    fun evictExpired() {
        val currentTime = System.currentTimeMillis()
        val iterator = cache.iterator()
        var removedCount = 0
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > ttlMs) {
                iterator.remove()
                removedCount++
            }
        }
        
        if (removedCount > 0) {
            Log.d("SCREENSHOT_CACHE", "清理过期缓存: $removedCount 条")
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    @Synchronized
    fun getStats(): Map<String, Int> {
        return mapOf(
            "size" to cache.size,
            "maxSize" to maxSize
        )
    }
    
    /**
     * 生成缓存键
     * 基于应用包名和窗口变化时间戳生成唯一键
     */
    fun generateKey(packageName: String, windowEventTime: Long): String {
        // 将时间戳按500ms分组，减少缓存键的变化频率
        val timeSlot = (windowEventTime / 500) * 500
        return "${packageName}_${timeSlot}"
    }
}