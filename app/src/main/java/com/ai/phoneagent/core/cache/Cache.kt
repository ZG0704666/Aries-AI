package com.ai.phoneagent.core.cache

/**
 * 统一缓存接口。
 *
 * 所有进程内缓存应实现此接口，以便：
 * - 淘汰策略一致（LRU + 容量上限 + TTL）
 * - 可统一监控（大小、命中率）
 * - 可通过 [ComponentCallbacks2] 统一清理
 *
 * @param K 键类型
 * @param V 值类型
 */
interface Cache<K, V> {

    /**
     * 读取条目。
     *
     * - 命中且未过期：刷新访问顺序并返回值。
     * - 命中但已过期：删除条目并返回 null。
     * - 未命中：返回 null。
     *
     * @param key 缓存键
     * @return 命中且未过期时返回值，否则返回 null
     */
    operator fun get(key: K): V?

    /**
     * 写入条目。若容量超限，自动淘汰最久未访问的条目。
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    fun put(key: K, value: V)

    /**
     * 移除指定键的条目。若不存在则无操作。
     *
     * @param key 缓存键
     */
    fun remove(key: K)

    /**
     * 当前条目数。
     *
     * 注意：返回值可能包含尚未触发过期检查的条目（过期条目仅在 [get] 时被清除）。
     *
     * @return 当前条目数
     */
    fun size(): Int

    /**
     * 清空所有条目。
     */
    fun clear()

    /**
     * 返回当前缓存条目的快照。
     *
     * 返回的列表与原缓存互不影响，调用方可安全地迭代而无需额外加锁。
     *
     * @return 当前所有条目的键值对列表
     */
    fun snapshot(): List<Pair<K, V>>
}
