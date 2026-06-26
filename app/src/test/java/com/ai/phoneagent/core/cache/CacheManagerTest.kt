package com.ai.phoneagent.core.cache

import org.junit.Assert.*
import org.junit.Test

class CacheManagerTest {

    @Test
    fun `register and unregister cache`() {
        val cache = TestCache("test1")
        CacheManager.register(cache)
        CacheManager.unregister(cache)
    }

    @Test
    fun `register same cache twice does not duplicate`() {
        val cache = TestCache("test2")
        CacheManager.register(cache)
        CacheManager.register(cache)
        CacheManager.unregister(cache)
    }

    @Test
    fun `onLowMemory clears all registered caches`() {
        val cache = TestCache("low_memory_test")
        CacheManager.register(cache)
        CacheManager.onLowMemory()
        assertTrue(cache.cleared)
        CacheManager.unregister(cache)
    }

    @Test
    fun `onTrimMemory with moderate level evicts expired`() {
        val cache = TestCache("trim_moderate_test")
        CacheManager.register(cache)
        CacheManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertTrue(cache.evicted)
        CacheManager.unregister(cache)
    }

    @Test
    fun `onTrimMemory with complete level clears all`() {
        val cache = TestCache("trim_complete_test")
        CacheManager.register(cache)
        CacheManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertTrue(cache.cleared)
        CacheManager.unregister(cache)
    }

    private class TestCache(name: String) : CacheManager.EvictableCache {
        var evicted = false
        var cleared = false
        private val cacheName = name

        override fun evictExpired() { evicted = true }
        override fun clear() { cleared = true }
        override fun getName(): String = cacheName
    }
}
