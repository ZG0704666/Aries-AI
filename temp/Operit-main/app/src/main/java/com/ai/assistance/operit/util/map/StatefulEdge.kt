package com.ai.assistance.operit.util.map

/**
 * 带状态的边，定义从一个节点状态到另一个节点状态的转换
 * @param from 起始节点ID
 * @param to 目标节点ID
 * @param action 操作描述
 * @param weight 边权重
 * @param conditions 前置条件
 * @param stateTransform 状态转换函数
 * @param parameters 边参数
 * @param metadata 边元数据
 */
data class StatefulEdge(
    val from: String,
    val to: String,
    val action: String,
    val weight: Double = 1.0,
    val conditions: Set<String> = emptySet(),
    val stateTransform: StateTransform = StateTransformIdentity,
    val parameters: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * 检查是否可以使用此边进行状态转换
     */
    fun canTraverse(fromState: NodeState, availableConditions: Set<String>): Boolean {
        // 检查节点匹配
        if (fromState.nodeId != from) return false
        
        // 检查前置条件
        if (!conditions.all { it in availableConditions }) return false
        
        // 检查状态转换条件
        return stateTransform.canApply(fromState)
    }
    
    /**
     * 应用状态转换，生成目标状态
     * @param fromState 起始状态
     * @param availableConditions 可用的外部条件，用于canTraverse检查
     * @param context 运行时上下文，用于模板化状态转换
     * @return 转换后的新状态，如果无法通过则为null
     */
    fun applyTransform(
        fromState: NodeState, 
        availableConditions: Set<String> = emptySet(),
        context: Map<String, Any> = emptyMap()
    ): NodeState? {
        if (!canTraverse(fromState, availableConditions)) return null
        
        val newState = stateTransform.apply(fromState, context)
        return newState?.copy(nodeId = to)
    }
    
    /**
     * 获取边的参数值
     */
    inline fun <reified T> getParameter(key: String): T? {
        return parameters[key] as? T
    }
    
    override fun toString(): String {
        return "StatefulEdge($from -> $to: $action, transform=$stateTransform)"
    }
} 