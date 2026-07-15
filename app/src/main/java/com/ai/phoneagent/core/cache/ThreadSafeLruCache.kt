package com.ai.phoneagent.core.cache

import java.util.LinkedHashMap

/**
 * 线程安全的 LRU 缓存，支持 TTL 过期与容量上限。
 *
 * 内部基于 [LinkedHashMap]（accessOrder=true）实现 LRU 淘汰策略：
 * 每次 [get] 命中都会刷新该条目的访问顺序，使最久未访问的条目优先被淘汰。
 * 所有公开方法通过 [synchronized] 块保护，可在多线程环境下安全使用，
 * 无需调用方额外加锁。
 *
 * TTL 语义：[put] 时记录时间戳，[get] 时若已过期则视为不存在并删除条目。
 * 若不需要过期，可将 [ttlMillis] 设为 [Long.MAX_VALUE]。
 *
 * @param K 键类型
 * @param V 值类型
 * @property maxSize 最大条目数，超出后淘汰最久未访问的条目
 * @property ttlMillis 单条目存活毫秒数，默认 30 分钟
 */
class ThreadSafeLruCache<K, V>(
    private val maxSize: Int,
    private val ttlMillis: Long = 30 * 60 * 1000L,
    private val clock: MillisClock = MillisClock.SYSTEM,
) : Cache<K, V> {

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
        require(ttlMillis > 0) { "ttlMillis must be positive, was $ttlMillis" }
    }

    /** 内部带时间戳的值包装。 */
    private data class TimedValue<V>(val value: V, val createdAt: Long)

    /**
     * accessOrder=true 的 LinkedHashMap：每次 [get] 命中会将被访问条目移到链表尾部，
     * 链表头部即为最久未访问条目，[MutableMap.removeEldestEntry] 在 [put] 后触发淘汰。
     */
    private val internal: MutableMap<K, TimedValue<V>> =
        object : LinkedHashMap<K, TimedValue<V>>(maxSize + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, TimedValue<V>>?): Boolean {
                return size > maxSize
            }
        }

    /** 同步锁对象，所有公开方法都通过此锁串行化。 */
    private val lock = Any()

    private fun evictExpiredLocked(now: Long): Int {
        val iterator = internal.entries.iterator()
        var removed = 0
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAt >= ttlMillis) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    /**
     * 读取条目。
     *
     * - 命中且未过期：刷新访问顺序（accessOrder=true 生效）并返回值。
     * - 命中但已过期：删除条目并返回 null。
     * - 未命中：返回 null。
     *
     * @param key 缓存键
     * @return 命中且未过期时返回值，否则返回 null
     */
    override operator fun get(key: K): V? {
        synchronized(lock) {
            val entry = internal[key] ?: return null
            if (clock.nowMillis() - entry.createdAt >= ttlMillis) {
                internal.remove(key)
                return null
            }
            return entry.value
        }
    }

    /**
     * 写入条目。若容量超限，自动淘汰最久未访问的条目。
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    override fun put(key: K, value: V) {
        synchronized(lock) {
            internal[key] = TimedValue(value, clock.nowMillis())
        }
    }

    /**
     * 移除指定键的条目。若不存在则无操作。
     *
     * @param key 缓存键
     */
    override fun remove(key: K) {
        synchronized(lock) {
            internal.remove(key)
        }
    }

    /**
     * 当前条目数。
     *
     * @return 当前条目数
     */
    override fun size(): Int {
        synchronized(lock) {
            evictExpiredLocked(clock.nowMillis())
            return internal.size
        }
    }

    /**
     * 清空所有条目。
     */
    override fun clear() {
        synchronized(lock) {
            internal.clear()
        }
    }

    /**
     * 返回当前缓存条目的快照（[K] 到 [V] 的键值对列表）。
     *
     * 该方法在锁内一次性拷贝所有条目，返回的列表与原缓存互不影响，
     * 调用方可安全地迭代、过滤而无需额外加锁。
     *
     * 返回顺序为 LRU 访问顺序（最久未访问在前）。
     *
     * @return 当前所有条目的键值对列表
     */
    override fun snapshot(): List<Pair<K, V>> {
        synchronized(lock) {
            evictExpiredLocked(clock.nowMillis())
            return internal.entries.map { it.key to it.value.value }
        }
    }

    /**
     * 主动清理所有已过期条目。
     *
     * 与 [get] 的惰性清理不同，此方法遍历全部条目并移除已过期的。
     * 适用于内存压力回调（[ComponentCallbacks2]）或定期清理场景。
     *
     * @return 被清理的条目数
     */
    fun evictExpired(): Int {
        synchronized(lock) {
            return evictExpiredLocked(clock.nowMillis())
        }
    }
}
