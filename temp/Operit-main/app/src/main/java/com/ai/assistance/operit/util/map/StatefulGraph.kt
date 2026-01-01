package com.ai.assistance.operit.util.map

/**
 * 带状态的图，支持节点状态变异和回退搜索
 */
class StatefulGraph {
    private val nodes = mutableMapOf<String, Node>()
    private val statefulEdges = mutableMapOf<String, MutableList<StatefulEdge>>()
    
    /**
     * 添加节点
     */
    fun addNode(node: Node) {
        nodes[node.id] = node
        if (node.id !in statefulEdges) {
            statefulEdges[node.id] = mutableListOf()
        }
    }
    
    /**
     * 添加带状态的边
     */
    fun addStatefulEdge(edge: StatefulEdge) {
        require(edge.from in nodes) { "起始节点 '${edge.from}' 不存在" }
        require(edge.to in nodes) { "目标节点 '${edge.to}' 不存在" }
        
        statefulEdges[edge.from]?.add(edge)
    }
    
    /**
     * 获取节点
     */
    fun getNode(nodeId: String): Node? = nodes[nodeId]
    
    /**
     * 获取所有节点
     */
    fun getAllNodes(): Collection<Node> = nodes.values
    
    /**
     * 获取从指定节点出发的所有带状态边
     */
    fun getOutgoingStatefulEdges(nodeId: String): List<StatefulEdge> {
        return statefulEdges[nodeId] ?: emptyList()
    }
    
    /**
     * 获取图中所有状态的边
     */
    fun getAllEdges(): List<StatefulEdge> {
        return statefulEdges.values.flatten()
    }
    
    /**
     * 获取从指定状态出发且可以通过的边
     */
    fun getValidOutgoingEdges(
        fromState: NodeState,
        availableConditions: Set<String> = emptySet()
    ): List<StatefulEdge> {
        // 将外部可用条件与节点当前状态中值为 true 的属性合并
        val dynamicConditions = availableConditions + fromState.variables
            .filterValues { it is Boolean && it }
            .keys
        
        return getOutgoingStatefulEdges(fromState.nodeId)
            .filter { it.canTraverse(fromState, dynamicConditions) }
    }
    
    /**
     * 检查节点是否存在
     */
    fun hasNode(nodeId: String): Boolean = nodeId in nodes
    
    /**
     * 检查是否存在从一个节点到另一个节点的边
     */
    fun hasEdge(from: String, to: String): Boolean {
        return statefulEdges[from]?.any { it.to == to } ?: false
    }
    
    /**
     * 获取图的统计信息
     */
    fun getStats(): GraphStats {
        val totalEdges = statefulEdges.values.sumOf { it.size }
        return GraphStats(
            nodeCount = nodes.size,
            edgeCount = totalEdges,
            avgOutDegree = if (nodes.isNotEmpty()) totalEdges.toDouble() / nodes.size else 0.0
        )
    }
    
    /**
     * 清空图
     */
    fun clear() {
        nodes.clear()
        statefulEdges.clear()
    }
    
    override fun toString(): String {
        val stats = getStats()
        return "StatefulGraph(nodes=${stats.nodeCount}, edges=${stats.edgeCount})"
    }
} 