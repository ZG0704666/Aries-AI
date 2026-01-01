package com.ai.assistance.operit.util.map

/**
 * 通用图边，表示从一个节点到另一个节点的连接
 * @param from 起始节点ID
 * @param to 目标节点ID
 * @param action 边上的操作描述
 * @param weight 边的权重，用于路径搜索算法（默认为1）
 * @param conditions 边的前置条件，只有满足这些条件才能通过这条边
 * @param parameters 边的参数，存储执行操作时需要的参数
 * @param metadata 边的元数据
 */
data class Edge(
    val from: String,
    val to: String,
    val action: String,
    val weight: Double = 1.0,
    val conditions: Set<String> = emptySet(),
    val parameters: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 检查边是否满足指定的条件集合
     * @param availableConditions 当前可用的条件集合
     * @return 如果所有required条件都满足则返回true
     */
    fun canTraverse(availableConditions: Set<String>): Boolean {
        return conditions.all { it in availableConditions }
    }
    
    /**
     * 获取边的参数值
     */
    inline fun <reified T> getParameter(key: String): T? {
        return parameters[key] as? T
    }
    
    /**
     * 获取边的元数据值
     */
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }
    
    /**
     * 获取边的字符串表示，用于调试
     */
    override fun toString(): String {
        return "Edge(from='$from', to='$to', action='$action', weight=$weight)"
    }
} 