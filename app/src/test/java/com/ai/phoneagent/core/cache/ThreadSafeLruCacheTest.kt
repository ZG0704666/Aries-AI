package com.ai.phoneagent.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [ThreadSafeLruCache] 单元测试。
 *
 * 覆盖：基本读写、LRU 淘汰、TTL 过期、删除/清空、size、并发安全、accessOrder 顺序更新。
 */
class ThreadSafeLruCacheTest {
    private class TestClock(var now: Long = 0L) : MillisClock {
        override fun nowMillis(): Long = now
    }

    @Test
    fun `put_get_基本读写`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 8)

        cache.put("k1", "v1")

        assertEquals("v1", cache.get("k1"))
        assertNull("未命中的键应返回 null", cache.get("missing"))
    }

    @Test
    fun `容量超限_LRU淘汰`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 3)

        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        cache.put("d", "4") // 容量超限，淘汰最老条目 a

        assertNull("a 应被淘汰", cache.get("a"))
        assertEquals("2", cache.get("b"))
        assertEquals("3", cache.get("c"))
        assertEquals("4", cache.get("d"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `TTL过期_返回null`() {
        val clock = TestClock()
        val cache =
            ThreadSafeLruCache<String, String>(
                maxSize = 8,
                ttlMillis = 100L,
                clock = clock,
            )

        cache.put("k", "v")
        clock.now = 100L

        assertNull("过期条目应返回 null", cache.get("k"))
    }

    @Test
    fun `TTL未过期_返回值`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 8, ttlMillis = 5000L)

        cache.put("k", "v")

        assertEquals("未过期应返回原值", "v", cache.get("k"))
    }

    @Test
    fun `remove_删除条目`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 8)

        cache.put("k", "v")
        assertEquals("v", cache.get("k"))

        cache.remove("k")

        assertNull("删除后应返回 null", cache.get("k"))
        // remove 不存在的键不应抛异常
        cache.remove("not-exist")
    }

    @Test
    fun `clear_清空所有`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 8)

        cache.put("a", "1")
        cache.put("b", "2")
        cache.clear()

        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `size_返回当前数量`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 8)

        assertEquals(0, cache.size())

        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals(2, cache.size())

        cache.remove("a")
        assertEquals(1, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `并发读写_无崩溃`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 64, ttlMillis = 5000L)
        val threadCount = 100
        val executor: ExecutorService = Executors.newFixedThreadPool(16)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    repeat(50) { j ->
                        val key = "key_${(i * 50 + j) % 200}"
                        cache.put(key, "value_$j")
                        cache.get(key)
                        cache.size()
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue("并发应在 30 秒内完成", doneLatch.await(30, TimeUnit.SECONDS))
        executor.shutdown()

        assertTrue("并发期间发生异常: $errors", errors.isEmpty())
        // 至少有部分条目存活（容量 64，最多 200 个不同 key）
        assertTrue("并发后应有存活条目", cache.size() > 0)
    }

    @Test
    fun `accessOrder_访问更新LRU顺序`() {
        val cache = ThreadSafeLruCache<String, String>(maxSize = 3)

        cache.put("A", "1")
        cache.put("B", "2")
        cache.put("C", "3")

        // 访问 A，使其变为最近使用；LRU 顺序变为 B -> C -> A
        assertEquals("1", cache.get("A"))

        // 写入 D，容量超限淘汰最久未访问的 B
        cache.put("D", "4")

        assertNull("B 应被淘汰", cache.get("B"))
        assertEquals("A 应保留", "1", cache.get("A"))
        assertEquals("C 应保留", "3", cache.get("C"))
        assertEquals("D 应保留", "4", cache.get("D"))
    }

    @Test
    fun `snapshot_返回键值对快照`() {
        val cache = ThreadSafeLruCache<String, Int>(maxSize = 8)

        cache.put("a", 1)
        cache.put("b", 2)

        val snapshot = cache.snapshot()

        assertEquals(2, snapshot.size)
        // 验证包含两个键值对
        assertTrue(snapshot.contains("a" to 1))
        assertTrue(snapshot.contains("b" to 2))
        // 快照不应影响原缓存
        assertEquals(2, cache.size())
    }

    @Test
    fun `TTL过期_get后自动删除条目`() {
        val clock = TestClock()
        val cache =
            ThreadSafeLruCache<String, String>(
                maxSize = 8,
                ttlMillis = 100L,
                clock = clock,
            )

        cache.put("k", "v")
        assertEquals(1, cache.size())

        clock.now = 100L
        // 第一次 get 触发过期删除
        assertNull(cache.get("k"))
        // size 反映删除后的状态
        assertEquals(0, cache.size())
    }

    @Test
    fun `同一键重复put_更新值与时间戳`() {
        val clock = TestClock()
        val cache =
            ThreadSafeLruCache<String, String>(
                maxSize = 8,
                ttlMillis = 100L,
                clock = clock,
            )

        cache.put("k", "v1")
        clock.now = 90L
        cache.put("k", "v2")
        clock.now = 150L

        assertEquals("v2", cache.get("k"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `size和snapshot清理过期条目`() {
        val clock = TestClock()
        val cache =
            ThreadSafeLruCache<String, String>(
                maxSize = 8,
                ttlMillis = 100L,
                clock = clock,
            )

        cache.put("expired", "old")
        clock.now = 50L
        cache.put("fresh", "new")
        clock.now = 100L

        assertEquals(1, cache.size())
        assertEquals(listOf("fresh" to "new"), cache.snapshot())
        assertNull(cache.get("expired"))
        assertEquals("new", cache.get("fresh"))
    }

    @Test
    fun `非法参数_抛IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThreadSafeLruCache<String, String>(maxSize = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThreadSafeLruCache<String, String>(maxSize = 8, ttlMillis = 0L)
        }
    }
}
