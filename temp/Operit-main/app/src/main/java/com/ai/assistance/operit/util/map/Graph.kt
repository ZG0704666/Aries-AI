package com.ai.assistance.operit.util.map

/**
 * 通用有向图
 * 管理节点和边的集合，提供基本的图操作
 */
class Graph {
    private val nodes = mutableMapOf<String, Node>()
    private val edges = mutableMapOf<String, MutableList<Edge>>()
    private val incomingEdges = mutableMapOf<String, MutableList<Edge>>()
    
    /**
     * 添加节点到图中
     */
    fun addNode(node: Node) {
        nodes[node.id] = node
        if (node.id !in edges) {
            edges[node.id] = mutableListOf()
        }
        if (node.id !in incomingEdges) {
            incomingEdges[node.id] = mutableListOf()
        }
    }
    
    /**
     * 添加边到图中
     */
    fun addEdge(edge: Edge) {
        // 确保起始节点和目标节点都存在
        require(edge.from in nodes) { "起始节点 '${edge.from}' 不存在" }
        require(edge.to in nodes) { "目标节点 '${edge.to}' 不存在" }
        
        edges[edge.from]?.add(edge)
        incomingEdges[edge.to]?.add(edge)
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
     * 获取从指定节点出发的所有边
     */
    fun getOutgoingEdges(nodeId: String): List<Edge> {
        return edges[nodeId] ?: emptyList()
    }
    
    /**
     * 获取指向指定节点的所有边
     */
    fun getIncomingEdges(nodeId: String): List<Edge> {
        return incomingEdges[nodeId] ?: emptyList()
    }
    
    /**
     * 获取从指定节点出发且满足条件的边
     */
    fun getValidOutgoingEdges(nodeId: String, availableConditions: Set<String>): List<Edge> {
        return getOutgoingEdges(nodeId).filter { it.canTraverse(availableConditions) }
    }
    
    /**
     * 检查节点是否存在
     */
    fun hasNode(nodeId: String): Boolean = nodeId in nodes
    
    /**
     * 检查边是否存在
     */
    fun hasEdge(from: String, to: String): Boolean {
        return edges[from]?.any { it.to == to } ?: false
    }
    
    /**
     * 获取图中节点的邻居节点
     */
    fun getNeighbors(nodeId: String): List<String> {
        return getOutgoingEdges(nodeId).map { it.to }
    }
    
    /**
     * 获取图的统计信息
     */
    fun getStats(): GraphStats {
        val totalEdges = edges.values.sumOf { it.size }
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
        edges.clear()
        incomingEdges.clear()
    }
    
    /**
     * 获取图的字符串表示，用于调试
     */
    override fun toString(): String {
        val stats = getStats()
        return "Graph(nodes=${stats.nodeCount}, edges=${stats.edgeCount})"
    }
}

/**
 * 图的统计信息
 */
data class GraphStats(
    val nodeCount: Int,
    val edgeCount: Int,
    val avgOutDegree: Double
) 