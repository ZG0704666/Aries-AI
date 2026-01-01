package com.ai.assistance.operit.util.map

import java.util.PriorityQueue
import kotlin.system.measureTimeMillis

/**
 * 图的路径搜索器
 * 提供多种路径搜索算法
 */
class PathFinder(private val graph: Graph) {
    
    /**
     * 使用 Dijkstra 算法搜索最短路径
     * @param startNodeId 起始节点ID
     * @param endNodeId 目标节点ID
     * @param availableConditions 当前可用的条件集合，用于边的条件检查
     * @param maxDistance 最大搜索距离（可选限制）
     * @return 路径搜索结果
     */
    fun findShortestPath(
        startNodeId: String,
        endNodeId: String,
        availableConditions: Set<String> = emptySet(),
        maxDistance: Double = Double.MAX_VALUE
    ): PathResult {
        return findPath(startNodeId, endNodeId, availableConditions, maxDistance, "Dijkstra")
    }
    
    /**
     * 使用 A* 算法搜索路径（需要启发式函数）
     * @param startNodeId 起始节点ID
     * @param endNodeId 目标节点ID
     * @param heuristic 启发式函数，估算从任意节点到目标节点的距离
     * @param availableConditions 当前可用的条件集合
     * @param maxDistance 最大搜索距离
     * @return 路径搜索结果
     */
    fun findPathWithHeuristic(
        startNodeId: String,
        endNodeId: String,
        heuristic: (String) -> Double,
        availableConditions: Set<String> = emptySet(),
        maxDistance: Double = Double.MAX_VALUE
    ): PathResult {
        return findPathInternal(
            startNodeId, 
            endNodeId, 
            availableConditions, 
            maxDistance, 
            "A*",
            heuristic
        )
    }
    
    /**
     * 搜索所有可能的路径（有深度限制）
     * @param startNodeId 起始节点ID
     * @param endNodeId 目标节点ID
     * @param availableConditions 当前可用的条件集合
     * @param maxDepth 最大搜索深度
     * @param maxPaths 最大返回路径数量
     * @return 所有找到的路径
     */
    fun findAllPaths(
        startNodeId: String,
        endNodeId: String,
        availableConditions: Set<String> = emptySet(),
        maxDepth: Int = 10,
        maxPaths: Int = 5
    ): List<Path> {
        if (!graph.hasNode(startNodeId) || !graph.hasNode(endNodeId)) {
            return emptyList()
        }
        
        val allPaths = mutableListOf<Path>()
        val visited = mutableSetOf<String>()
        
        findAllPathsRecursive(
            currentNode = startNodeId,
            endNode = endNodeId,
            currentPath = listOf(startNodeId),
            currentEdges = emptyList(),
            currentWeight = 0.0,
            visited = visited,
            availableConditions = availableConditions,
            maxDepth = maxDepth,
            result = allPaths
        )
        
        return allPaths.sortedBy { it.totalWeight }.take(maxPaths)
    }
    
    private fun findPath(
        startNodeId: String,
        endNodeId: String,
        availableConditions: Set<String>,
        maxDistance: Double,
        algorithmName: String
    ): PathResult {
        return findPathInternal(startNodeId, endNodeId, availableConditions, maxDistance, algorithmName)
    }
    
    private fun findPathInternal(
        startNodeId: String,
        endNodeId: String,
        availableConditions: Set<String>,
        maxDistance: Double,
        algorithmName: String,
        heuristic: ((String) -> Double)? = null
    ): PathResult {
        // 参数验证
        if (!graph.hasNode(startNodeId)) {
            return PathResult.failure("起始节点 '$startNodeId' 不存在")
        }
        if (!graph.hasNode(endNodeId)) {
            return PathResult.failure("目标节点 '$endNodeId' 不存在")
        }
        if (startNodeId == endNodeId) {
            val singleNodePath = Path(listOf(startNodeId), emptyList(), 0.0)
            return PathResult.success(singleNodePath, "起始节点和目标节点相同")
        }
        
        var visitedNodes = 0
        var exploredEdges = 0
        
        // 将这些变量声明在外部以便后续使用
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String?>()
        val previousEdge = mutableMapOf<String, Edge?>()
        val visited = mutableSetOf<String>()
        
        val searchTime = measureTimeMillis {
            // 优先队列：按照 f(n) = g(n) + h(n) 排序（Dijkstra时h(n)=0）
            val queue = PriorityQueue<NodeDistance> { a, b -> 
                a.totalDistance.compareTo(b.totalDistance) 
            }
            
            // 初始化
            distances[startNodeId] = 0.0
            queue.offer(NodeDistance(
                nodeId = startNodeId, 
                distance = 0.0,
                totalDistance = heuristic?.invoke(startNodeId) ?: 0.0
            ))
            
            while (queue.isNotEmpty()) {
                val current = queue.poll()
                val currentNodeId = current.nodeId
                
                if (currentNodeId in visited) continue
                visited.add(currentNodeId)
                visitedNodes++
                
                // 找到目标节点
                if (currentNodeId == endNodeId) {
                    break
                }
                
                // 超过最大距离限制
                if (current.distance > maxDistance) {
                    continue
                }
                
                // 探索邻居节点
                val validEdges = graph.getValidOutgoingEdges(currentNodeId, availableConditions)
                exploredEdges += validEdges.size
                
                for (edge in validEdges) {
                    val neighborId = edge.to
                    val newDistance = current.distance + edge.weight
                    
                    if (neighborId !in visited && newDistance < (distances[neighborId] ?: Double.MAX_VALUE)) {
                        distances[neighborId] = newDistance
                        previous[neighborId] = currentNodeId
                        previousEdge[neighborId] = edge
                        
                        val totalDistance = newDistance + (heuristic?.invoke(neighborId) ?: 0.0)
                        queue.offer(NodeDistance(neighborId, newDistance, totalDistance))
                    }
                }
            }
        }
        
        val searchStats = SearchStats(visitedNodes, exploredEdges, searchTime, algorithmName)
        
        // 重建路径
        val path = reconstructPath(startNodeId, endNodeId, previous, previousEdge)
        return if (path != null) {
            PathResult.success(path, "使用 $algorithmName 算法找到路径", searchStats)
        } else {
            PathResult.failure("无法找到从 '$startNodeId' 到 '$endNodeId' 的路径", searchStats = searchStats)
        }
    }
    
    private fun findAllPathsRecursive(
        currentNode: String,
        endNode: String,
        currentPath: List<String>,
        currentEdges: List<Edge>,
        currentWeight: Double,
        visited: MutableSet<String>,
        availableConditions: Set<String>,
        maxDepth: Int,
        result: MutableList<Path>
    ) {
        if (currentPath.size > maxDepth) return
        if (result.size >= 10) return // 限制最大路径数量防止过度搜索
        
        if (currentNode == endNode) {
            result.add(Path(currentPath, currentEdges, currentWeight))
            return
        }
        
        visited.add(currentNode)
        
        val validEdges = graph.getValidOutgoingEdges(currentNode, availableConditions)
        for (edge in validEdges) {
            val nextNode = edge.to
            if (nextNode !in visited) {
                findAllPathsRecursive(
                    nextNode,
                    endNode,
                    currentPath + nextNode,
                    currentEdges + edge,
                    currentWeight + edge.weight,
                    visited.toMutableSet(), // 创建副本避免回溯问题
                    availableConditions,
                    maxDepth,
                    result
                )
            }
        }
        
        visited.remove(currentNode)
    }
    
    private fun reconstructPath(
        startNodeId: String,
        endNodeId: String,
        previous: Map<String, String?>,
        previousEdge: Map<String, Edge?>
    ): Path? {
        if (endNodeId !in previous) return null
        
        val nodes = mutableListOf<String>()
        val edges = mutableListOf<Edge>()
        var current: String? = endNodeId
        
        // 重建节点路径
        while (current != null) {
            nodes.add(current)
            current = previous[current]
        }
        nodes.reverse()
        
        // 重建边路径
        for (i in 1 until nodes.size) {
            val edge = previousEdge[nodes[i]]
            if (edge != null) {
                edges.add(edge)
            }
        }
        
        val totalWeight = edges.sumOf { it.weight }
        return Path(nodes, edges, totalWeight)
    }
    
    /**
     * 用于优先队列的节点距离数据类
     */
    private data class NodeDistance(
        val nodeId: String,
        val distance: Double, // g(n) - 从起点到当前节点的实际距离
        val totalDistance: Double // f(n) = g(n) + h(n) - 总估算距离
    )
} 