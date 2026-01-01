package com.ai.assistance.operit.util.map

/**
 * 表示图中的一条路径
 * @param nodes 路径上的节点ID列表（按顺序）
 * @param edges 路径上的边列表（按顺序）
 * @param totalWeight 路径的总权重
 * @param metadata 路径的元数据
 */
data class Path(
    val nodes: List<String>,
    val edges: List<Edge>,
    val totalWeight: Double,
    val metadata: Map<String, Any> = emptyMap()
) {
    init {
        require(nodes.isNotEmpty()) { "路径不能为空" }
        require(edges.isEmpty() || edges.size == nodes.size - 1) { 
            "边的数量应该比节点数量少1，nodes=${nodes.size}, edges=${edges.size}" 
        }
    }
    
    /**
     * 获取路径的起始节点
     */
    val startNode: String get() = nodes.first()
    
    /**
     * 获取路径的结束节点
     */
    val endNode: String get() = nodes.last()
    
    /**
     * 获取路径的长度（边的数量）
     */
    val length: Int get() = edges.size
    
    /**
     * 检查路径是否只有一个节点（没有边）
     */
    val isSingleNode: Boolean get() = nodes.size == 1
    
    /**
     * 获取路径上指定位置的节点
     */
    fun getNodeAt(index: Int): String? {
        return nodes.getOrNull(index)
    }
    
    /**
     * 获取路径上指定位置的边
     */
    fun getEdgeAt(index: Int): Edge? {
        return edges.getOrNull(index)
    }
    
    /**
     * 获取从指定节点开始的子路径
     */
    fun getSubPathFrom(nodeId: String): Path? {
        val startIndex = nodes.indexOf(nodeId)
        if (startIndex == -1) return null
        
        val subNodes = nodes.drop(startIndex)
        val subEdges = if (startIndex == 0) edges else edges.drop(startIndex)
        val subWeight = subEdges.sumOf { it.weight }
        
        return Path(subNodes, subEdges, subWeight, metadata)
    }
    
    /**
     * 获取到指定节点为止的子路径
     */
    fun getSubPathTo(nodeId: String): Path? {
        val endIndex = nodes.indexOf(nodeId)
        if (endIndex == -1) return null
        
        val subNodes = nodes.take(endIndex + 1)
        val subEdges = edges.take(endIndex)
        val subWeight = subEdges.sumOf { it.weight }
        
        return Path(subNodes, subEdges, subWeight, metadata)
    }
    
    /**
     * 反转路径（注意：这只是逻辑上的反转，不验证边的方向）
     */
    fun reversed(): Path {
        return Path(
            nodes = nodes.reversed(),
            edges = edges.reversed(),
            totalWeight = totalWeight,
            metadata = metadata
        )
    }
    
    override fun toString(): String {
        val nodeStr = nodes.joinToString(" -> ")
        return "Path($nodeStr, weight=$totalWeight)"
    }
}

/**
 * 路径搜索的结果
 * @param success 是否找到路径
 * @param path 找到的路径（如果成功）
 * @param message 结果消息
 * @param alternativePaths 备选路径列表
 * @param searchStats 搜索统计信息
 */
data class PathResult(
    val success: Boolean,
    val path: Path? = null,
    val message: String = "",
    val alternativePaths: List<Path> = emptyList(),
    val searchStats: SearchStats? = null
) {
    companion object {
        fun success(path: Path, message: String = "路径搜索成功", searchStats: SearchStats? = null): PathResult {
            return PathResult(
                success = true,
                path = path,
                message = message,
                searchStats = searchStats
            )
        }
        
        fun failure(message: String, alternativePaths: List<Path> = emptyList(), searchStats: SearchStats? = null): PathResult {
            return PathResult(
                success = false,
                message = message,
                alternativePaths = alternativePaths,
                searchStats = searchStats
            )
        }
    }
}

/**
 * 搜索统计信息
 */
data class SearchStats(
    val visitedNodes: Int,
    val exploredEdges: Int,
    val searchTimeMs: Long,
    val algorithm: String
) 