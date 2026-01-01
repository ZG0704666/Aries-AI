package com.ai.assistance.operit.util.map

/**
 * 通用图节点
 * @param id 节点的唯一标识符
 * @param name 节点名称（可选）
 * @param metadata 节点的元数据，可以存储任意键值对信息
 * @param properties 节点的属性集合，用于路径搜索和验证
 */
data class Node(
    val id: String,
    val name: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val properties: Set<String> = emptySet()
) {
    /**
     * 检查节点是否具有指定属性
     */
    fun hasProperty(property: String): Boolean = property in properties
    
    /**
     * 获取节点的元数据值
     */
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }
    
    /**
     * 获取节点的字符串表示，用于调试
     */
    override fun toString(): String {
        return "Node(id='$id', name='$name', properties=$properties)"
    }
} 