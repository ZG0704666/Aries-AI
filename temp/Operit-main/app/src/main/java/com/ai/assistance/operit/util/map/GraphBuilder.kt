package com.ai.assistance.operit.util.map

/**
 * 图构建器，提供链式API来构建图结构
 */
class GraphBuilder {
    private val graph = Graph()
    
    /**
     * 添加节点
     */
    fun addNode(
        id: String,
        name: String? = null,
        properties: Set<String> = emptySet(),
        metadata: Map<String, Any> = emptyMap()
    ): GraphBuilder {
        val node = Node(id, name, metadata, properties)
        graph.addNode(node)
        return this
    }
    
    /**
     * 添加节点（使用Node对象）
     */
    fun addNode(node: Node): GraphBuilder {
        graph.addNode(node)
        return this
    }
    
    /**
     * 批量添加节点
     */
    fun addNodes(nodes: List<Node>): GraphBuilder {
        nodes.forEach { graph.addNode(it) }
        return this
    }
    
    /**
     * 添加边
     */
    fun addEdge(
        from: String,
        to: String,
        action: String,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet(),
        parameters: Map<String, Any> = emptyMap(),
        metadata: Map<String, Any> = emptyMap()
    ): GraphBuilder {
        val edge = Edge(from, to, action, weight, conditions, parameters, metadata)
        graph.addEdge(edge)
        return this
    }
    
    /**
     * 添加边（使用Edge对象）
     */
    fun addEdge(edge: Edge): GraphBuilder {
        graph.addEdge(edge)
        return this
    }
    
    /**
     * 批量添加边
     */
    fun addEdges(edges: List<Edge>): GraphBuilder {
        edges.forEach { graph.addEdge(it) }
        return this
    }
    
    /**
     * 添加双向边（自动创建往返两条边）
     */
    fun addBidirectionalEdge(
        nodeA: String,
        nodeB: String,
        actionAtoB: String,
        actionBtoA: String = actionAtoB,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet(),
        parameters: Map<String, Any> = emptyMap()
    ): GraphBuilder {
        addEdge(nodeA, nodeB, actionAtoB, weight, conditions, parameters)
        addEdge(nodeB, nodeA, actionBtoA, weight, conditions, parameters)
        return this
    }
    
    /**
     * 连接节点列表，形成路径（A->B->C->D...）
     */
    fun connectSequentially(
        nodeIds: List<String>,
        action: String,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): GraphBuilder {
        for (i in 0 until nodeIds.size - 1) {
            addEdge(nodeIds[i], nodeIds[i + 1], action, weight, conditions)
        }
        return this
    }
    
    /**
     * 创建星型连接（一个中心节点连接到所有其他节点）
     */
    fun connectStar(
        centerNodeId: String,
        leafNodeIds: List<String>,
        action: String,
        weight: Double = 1.0,
        bidirectional: Boolean = true
    ): GraphBuilder {
        leafNodeIds.forEach { leafId ->
            addEdge(centerNodeId, leafId, action, weight)
            if (bidirectional) {
                addEdge(leafId, centerNodeId, action, weight)
            }
        }
        return this
    }
    
    /**
     * 创建完全连接图（每个节点都连接到其他所有节点）
     */
    fun connectComplete(
        nodeIds: List<String>,
        action: String,
        weight: Double = 1.0
    ): GraphBuilder {
        for (i in nodeIds.indices) {
            for (j in nodeIds.indices) {
                if (i != j) {
                    addEdge(nodeIds[i], nodeIds[j], action, weight)
                }
            }
        }
        return this
    }
    
    /**
     * 构建并返回图
     */
    fun build(): Graph = graph
    
    /**
     * 构建并返回带有PathFinder的图搜索器
     */
    fun buildWithFinder(): GraphSearcher {
        return GraphSearcher(graph)
    }
    
    companion object {
        /**
         * 创建新的图构建器
         */
        fun create(): GraphBuilder = GraphBuilder()
        
        /**
         * 从现有图创建构建器
         */
        fun from(existingGraph: Graph): GraphBuilder {
            val builder = GraphBuilder()
            // 复制节点
            existingGraph.getAllNodes().forEach { node ->
                builder.addNode(node)
            }
            // 复制边
            existingGraph.getAllNodes().forEach { node ->
                existingGraph.getOutgoingEdges(node.id).forEach { edge ->
                    builder.addEdge(edge)
                }
            }
            return builder
        }
    }
}

/**
 * 图搜索器，将图和路径搜索器组合在一起
 */
class GraphSearcher(val graph: Graph) {
    val pathFinder = PathFinder(graph)
    
    /**
     * 搜索最短路径
     */
    fun findShortestPath(
        from: String,
        to: String,
        conditions: Set<String> = emptySet()
    ): PathResult {
        return pathFinder.findShortestPath(from, to, conditions)
    }
    
    /**
     * 搜索所有路径
     */
    fun findAllPaths(
        from: String,
        to: String,
        conditions: Set<String> = emptySet(),
        maxDepth: Int = 10
    ): List<Path> {
        return pathFinder.findAllPaths(from, to, conditions, maxDepth)
    }
    
    /**
     * 获取图的统计信息
     */
    fun getStats(): GraphStats = graph.getStats()
} 