package com.ai.assistance.operit.util.map

import java.util.PriorityQueue
import kotlin.system.measureTimeMillis
import com.ai.assistance.operit.util.AppLogger

/**
 * 带状态的路径搜索器，支持节点状态变异和回退搜索
 */
class StatefulPathFinder(private val graph: StatefulGraph) {
    
    /**
     * 搜索从起始状态到目标节点的路径
     * @param startState 起始状态
     * @param targetNodeId 目标节点ID
     * @param targetStatePredicate 目标状态判断函数（可选）
     * @param availableConditions 可用条件集合
     * @param runtimeContext 运行时上下文，用于传递外部数据
     * @param maxDistance 最大搜索距离
     * @param enableBacktrack 是否启用回退功能
     * @return 搜索结果
     */
    fun findPath(
        startState: NodeState,
        targetNodeId: String,
        targetStatePredicate: ((NodeState) -> Boolean)? = null,
        availableConditions: Set<String> = emptySet(),
        runtimeContext: Map<String, Any> = emptyMap(),
        maxDistance: Double = Double.MAX_VALUE,
        enableBacktrack: Boolean = true
    ): StatefulPathResult {
        // 参数验证
        if (!graph.hasNode(startState.nodeId)) {
            return StatefulPathResult.failure("起始节点 '${startState.nodeId}' 不存在")
        }
        if (!graph.hasNode(targetNodeId)) {
            return StatefulPathResult.failure("目标节点 '$targetNodeId' 不存在")
        }
        
        // 如果起始状态已经是目标状态
        if (startState.nodeId == targetNodeId && 
            (targetStatePredicate == null || targetStatePredicate(startState))) {
            val singleStatePath = StatefulPath(listOf(startState), emptyList(), 0.0)
            return StatefulPathResult.success(singleStatePath, "起始状态即为目标状态")
        }
        
        var visitedNodes = 0
        var exploredEdges = 0
        var backtrackCount = 0
        var foundPath: StatefulPath? = null
        
        val searchTime = measureTimeMillis {
            if (enableBacktrack) {
                val result = searchWithBacktrack(
                    startState, 
                    targetNodeId, 
                    targetStatePredicate, 
                    availableConditions, 
                    runtimeContext,
                    maxDistance
                )
                visitedNodes = result.visitedNodes
                exploredEdges = result.exploredEdges
                backtrackCount = result.backtrackCount
                foundPath = result.path
            } else {
                val result = searchWithoutBacktrack(
                    startState, 
                    targetNodeId, 
                    targetStatePredicate, 
                    availableConditions, 
                    runtimeContext,
                    maxDistance
                )
                visitedNodes = result.visitedNodes
                exploredEdges = result.exploredEdges
                foundPath = result.path
            }
        }
        
        val searchStats = SearchStats(visitedNodes, exploredEdges, searchTime, 
            if (enableBacktrack) "Backtrack" else "Dijkstra")
        
        val pathResult = foundPath
        return if (pathResult != null) {
            StatefulPathResult.success(
                pathResult,
                "使用${if (enableBacktrack) "回退" else "Dijkstra"}算法找到路径",
                searchStats,
                backtrackCount
            )
        } else {
            StatefulPathResult.failure(
                "无法找到从 '${startState.nodeId}' 到 '$targetNodeId' 的有效状态路径",
                searchStats = searchStats,
                backtrackCount = backtrackCount
            )
        }
    }
    
    /**
     * 使用回退搜索（深度优先 + 回退）
     */
    private fun searchWithBacktrack(
        startState: NodeState,
        targetNodeId: String,
        targetStatePredicate: ((NodeState) -> Boolean)?,
        availableConditions: Set<String>,
        runtimeContext: Map<String, Any>,
        maxDistance: Double
    ): BacktrackSearchResult {
        var visitedNodes = 0
        var exploredEdges = 0
        var backtrackCount = 0
        
        fun dfs(
            currentState: NodeState,
            currentPath: List<NodeState>,
            currentEdges: List<StatefulEdge>,
            currentWeight: Double,
            visitedStates: MutableSet<String>,
            depth: Int
        ): StatefulPath? {
            visitedNodes++
            AppLogger.d("PathFinder", "DFS visiting: ${currentState.nodeId} with vars ${currentState.variables}, depth: $depth")
            
            // 深度限制
            if (depth > 20) {
                AppLogger.d("PathFinder", "  -> Depth limit exceeded")
                return null
            }
            if (currentWeight > maxDistance) {
                AppLogger.d("PathFinder", "  -> Max distance exceeded")
                return null
            }
            
            // 检查是否到达目标
            if (currentState.nodeId == targetNodeId && 
                (targetStatePredicate == null || targetStatePredicate(currentState))) {
                AppLogger.d("PathFinder", "  -> Target found!")
                return StatefulPath(currentPath, currentEdges, currentWeight)
            }
            
            val stateKey = currentState.getStateKey()
            
            // 检查是否访问过相同状态
            if (stateKey in visitedStates) {
                backtrackCount++
                AppLogger.d("PathFinder", "  -> State already visited, backtracking")
                return null
            }
            
            visitedStates.add(stateKey)
            
            // 探索邻居
            val dynamicConditions = availableConditions + currentState.variables
                .filterValues { it is Boolean && it }
                .keys
            AppLogger.d("PathFinder", "  -> Dynamic conditions: $dynamicConditions")
            val validEdges = graph.getValidOutgoingEdges(currentState, dynamicConditions)
            exploredEdges += validEdges.size
            AppLogger.d("PathFinder", "  -> Found ${validEdges.size} valid edges")
            
            for (edge in validEdges.sortedBy { it.weight }) {
                AppLogger.d("PathFinder", "    - Trying edge: ${edge.action} to ${edge.to}")
                val nextState = edge.applyTransform(currentState, dynamicConditions, runtimeContext)
                if (nextState != null) {
                    val result = dfs(
                        nextState,
                        currentPath + nextState,
                        currentEdges + edge,
                        currentWeight + edge.weight,
                        visitedStates.toMutableSet(), // 创建副本以支持回退
                        depth + 1
                    )
                    if (result != null) {
                        return result
                    }
                }
            }
            
            // 回退
            visitedStates.remove(stateKey)
            return null
        }
        
        val path = dfs(
            startState, 
            listOf(startState), 
            emptyList(), 
            0.0, 
            mutableSetOf(), 
            0
        )
        
        return BacktrackSearchResult(path, visitedNodes, exploredEdges, backtrackCount)
    }
    
    /**
     * 使用状态感知的Dijkstra搜索（无回退）
     */
    private fun searchWithoutBacktrack(
        startState: NodeState,
        targetNodeId: String,
        targetStatePredicate: ((NodeState) -> Boolean)?,
        availableConditions: Set<String>,
        runtimeContext: Map<String, Any>,
        maxDistance: Double
    ): StandardSearchResult {
        var visitedNodes = 0
        var exploredEdges = 0
        
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, NodeState?>()
        val previousEdge = mutableMapOf<String, StatefulEdge?>()
        val visited = mutableSetOf<String>()
        
        val queue = PriorityQueue<StateDistance> { a, b -> 
            a.distance.compareTo(b.distance) 
        }
        
        val startKey = startState.getStateKey()
        distances[startKey] = 0.0
        queue.offer(StateDistance(startState, 0.0))
        
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val currentState = current.state
            val currentKey = currentState.getStateKey()
            
            if (currentKey in visited) continue
            visited.add(currentKey)
            visitedNodes++
            AppLogger.d("PathFinder", "Dijkstra visiting: ${currentState.nodeId} with vars ${currentState.variables}")
            
            // 检查是否到达目标
            if (currentState.nodeId == targetNodeId &&
                (targetStatePredicate == null || targetStatePredicate(currentState))) {
                AppLogger.d("PathFinder", "  -> Target found!")
                // 重建路径
                val path = reconstructStatefulPath(currentState, previous, previousEdge)
                return StandardSearchResult(path, visitedNodes, exploredEdges)
            }
            
            if (current.distance > maxDistance) continue
            
            // 探索邻居
            val dynamicConditions = availableConditions + currentState.variables
                .filterValues { it is Boolean && it }
                .keys
            AppLogger.d("PathFinder", "  -> Dynamic conditions: $dynamicConditions")
            val validEdges = graph.getValidOutgoingEdges(currentState, dynamicConditions)
            exploredEdges += validEdges.size
            AppLogger.d("PathFinder", "  -> Found ${validEdges.size} valid edges")
            
            for (edge in validEdges) {
                AppLogger.d("PathFinder", "    - Trying edge: ${edge.action} to ${edge.to}")
                val nextState = edge.applyTransform(currentState, dynamicConditions, runtimeContext)
                if (nextState != null) {
                    val nextKey = nextState.getStateKey()
                    val newDistance = current.distance + edge.weight
                    
                    if (nextKey !in visited && 
                        newDistance < (distances[nextKey] ?: Double.MAX_VALUE)) {
                        distances[nextKey] = newDistance
                        previous[nextKey] = currentState
                        previousEdge[nextKey] = edge
                        queue.offer(StateDistance(nextState, newDistance))
                    }
                }
            }
        }
        
        return StandardSearchResult(null, visitedNodes, exploredEdges)
    }
    
    /**
     * 重建状态路径
     */
    private fun reconstructStatefulPath(
        endState: NodeState,
        previous: Map<String, NodeState?>,
        previousEdge: Map<String, StatefulEdge?>
    ): StatefulPath? {
        val endKey = endState.getStateKey()
        if (endKey !in previous) return null
        
        val states = mutableListOf<NodeState>()
        val edges = mutableListOf<StatefulEdge>()
        var current: NodeState? = endState
        
        // 重建状态路径
        while (current != null) {
            states.add(current)
            val currentKey = current.getStateKey()
            current = previous[currentKey]
        }
        states.reverse()
        
        // 重建边路径
        for (i in 1 until states.size) {
            val stateKey = states[i].getStateKey()
            val edge = previousEdge[stateKey]
            if (edge != null) {
                edges.add(edge)
            }
        }
        
        val totalWeight = edges.sumOf { it.weight }
        return StatefulPath(states, edges, totalWeight)
    }
    
    /**
     * 状态距离数据类（用于优先队列）
     */
    private data class StateDistance(
        val state: NodeState,
        val distance: Double
    )
    
    /**
     * 回退搜索结果
     */
    private data class BacktrackSearchResult(
        val path: StatefulPath?,
        val visitedNodes: Int,
        val exploredEdges: Int,
        val backtrackCount: Int
    )
    
    /**
     * 标准搜索结果
     */
    private data class StandardSearchResult(
        val path: StatefulPath?,
        val visitedNodes: Int,
        val exploredEdges: Int
    )
} 