package com.ai.assistance.operit.util.map

/**
 * 带状态的路径，记录路径上每个节点的状态变化
 * @param states 路径上的状态序列
 * @param edges 路径上的边序列
 * @param totalWeight 路径总权重
 * @param metadata 路径元数据
 */
data class StatefulPath(
    val states: List<NodeState>,
    val edges: List<StatefulEdge>,
    val totalWeight: Double,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(states.isNotEmpty()) { "状态路径不能为空" }
        require(edges.isEmpty() || edges.size == states.size - 1) { 
            "边的数量应该比状态数量少1，states=${states.size}, edges=${edges.size}" 
        }
    }
    
    /**
     * 获取路径的起始状态
     */
    val startState: NodeState get() = states.first()
    
    /**
     * 获取路径的结束状态
     */
    val endState: NodeState get() = states.last()
    
    /**
     * 获取路径长度（边的数量）
     */
    val length: Int get() = edges.size
    
    /**
     * 检查路径是否只有一个状态
     */
    val isSingleState: Boolean get() = states.size == 1
    
    /**
     * 获取纯节点路径（不包含状态）
     */
    val nodeIds: List<String> get() = states.map { it.nodeId }
    
    /**
     * 获取指定位置的状态
     */
    fun getStateAt(index: Int): NodeState? = states.getOrNull(index)
    
    /**
     * 获取指定位置的边
     */
    fun getEdgeAt(index: Int): StatefulEdge? = edges.getOrNull(index)
    
    /**
     * 获取从指定状态开始的子路径
     */
    fun getSubPathFrom(stateIndex: Int): StatefulPath? {
        if (stateIndex < 0 || stateIndex >= states.size) return null
        
        val subStates = states.drop(stateIndex)
        val subEdges = if (stateIndex == 0) edges else edges.drop(stateIndex)
        val subWeight = subEdges.sumOf { it.weight }
        
        return StatefulPath(subStates, subEdges, subWeight, metadata)
    }
    
    /**
     * 获取到指定状态为止的子路径
     */
    fun getSubPathTo(stateIndex: Int): StatefulPath? {
        if (stateIndex < 0 || stateIndex >= states.size) return null
        
        val subStates = states.take(stateIndex + 1)
        val subEdges = edges.take(stateIndex)
        val subWeight = subEdges.sumOf { it.weight }
        
        return StatefulPath(subStates, subEdges, subWeight, metadata)
    }
    
    /**
     * 查找特定节点的所有状态变异
     */
    fun findStatesForNode(nodeId: String): List<Pair<Int, NodeState>> {
        return states.withIndex()
            .filter { it.value.nodeId == nodeId }
            .map { it.index to it.value }
    }
    
    /**
     * 检查路径中是否存在状态冲突（同一节点的不兼容状态）
     */
    fun hasStateConflicts(): Boolean {
        val nodeStates = mutableMapOf<String, MutableList<NodeState>>()
        
        for (state in states) {
            nodeStates.getOrPut(state.nodeId) { mutableListOf() }.add(state)
        }
        
        for ((_, statesForNode) in nodeStates) {
            if (statesForNode.size > 1) {
                // 检查是否所有状态都兼容
                for (i in statesForNode.indices) {
                    for (j in i + 1 until statesForNode.size) {
                        if (!statesForNode[i].isCompatibleWith(statesForNode[j])) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }
    
    /**
     * 转换为简单路径（去除状态信息）
     */
    fun toSimplePath(): Path {
        val simpleEdges = edges.map { edge ->
            Edge(
                from = edge.from,
                to = edge.to,
                action = edge.action,
                weight = edge.weight,
                conditions = edge.conditions,
                parameters = edge.parameters,
                metadata = edge.metadata
            )
        }
        return Path(nodeIds, simpleEdges, totalWeight, metadata)
    }
    
    /**
     * 验证路径的状态转换是否有效
     */
    fun isValid(context: Map<String, Any> = emptyMap()): Boolean {
        for (i in edges.indices) {
            val currentState = states[i]
            val edge = edges[i]
            val nextState = states[i + 1]
            
            // This is a more robust check than re-calling canTraverse without full context
            val expectedNextState = edge.applyTransform(currentState, edge.conditions, context)
            if (expectedNextState != nextState) {
                return false
            }
        }
        return true
    }

    /**
     * 创建一个包含新起始状态的新路径实例。
     */
    fun withNewStartState(newStartState: NodeState): StatefulPath {
        if (states.isEmpty()) return this
        val newStates = listOf(newStartState) + states.drop(1)
        return this.copy(states = newStates)
    }
    
    override fun toString(): String {
        val stateStr = states.map { it.nodeId }.joinToString(" -> ")
        return "StatefulPath($stateStr, weight=$totalWeight)"
    }
}

/**
 * 状态路径搜索结果
 */
data class StatefulPathResult(
    val success: Boolean,
    val path: StatefulPath? = null,
    val message: String = "",
    val alternativePaths: List<StatefulPath> = emptyList(),
    val searchStats: SearchStats? = null,
    val backtrackCount: Int = 0
) {
    companion object {
        fun success(
            path: StatefulPath, 
            message: String = "状态路径搜索成功", 
            searchStats: SearchStats? = null,
            backtrackCount: Int = 0
        ): StatefulPathResult {
            return StatefulPathResult(
                success = true,
                path = path,
                message = message,
                searchStats = searchStats,
                backtrackCount = backtrackCount
            )
        }
        
        fun failure(
            message: String, 
            alternativePaths: List<StatefulPath> = emptyList(), 
            searchStats: SearchStats? = null,
            backtrackCount: Int = 0
        ): StatefulPathResult {
            return StatefulPathResult(
                success = false,
                message = message,
                alternativePaths = alternativePaths,
                searchStats = searchStats,
                backtrackCount = backtrackCount
            )
        }
    }
} 