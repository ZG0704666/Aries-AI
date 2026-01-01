package com.ai.assistance.operit.util.map

/**
 * 带状态的图构建器，用于构建支持节点状态变异的图
 */
class StatefulGraphBuilder {
    private val graph = StatefulGraph()
    
    /**
     * 添加节点
     */
    fun addNode(
        id: String,
        name: String? = null,
        properties: Set<String> = emptySet(),
        metadata: Map<String, Any> = emptyMap()
    ): StatefulGraphBuilder {
        val node = Node(id, name, metadata, properties)
        graph.addNode(node)
        return this
    }
    
    /**
     * 添加节点（使用Node对象）
     */
    fun addNode(node: Node): StatefulGraphBuilder {
        graph.addNode(node)
        return this
    }
    
    /**
     * 批量添加节点
     */
    fun addNodes(nodes: List<Node>): StatefulGraphBuilder {
        nodes.forEach { graph.addNode(it) }
        return this
    }
    
    /**
     * 添加带状态转换的边
     */
    fun addStatefulEdge(
        from: String,
        to: String,
        action: String,
        stateTransform: StateTransform = StateTransformIdentity,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet(),
        parameters: Map<String, Any> = emptyMap(),
        metadata: Map<String, Any> = emptyMap()
    ): StatefulGraphBuilder {
        val edge = StatefulEdge(from, to, action, weight, conditions, stateTransform, parameters, metadata)
        graph.addStatefulEdge(edge)
        return this
    }
    
    /**
     * 添加带状态转换的边（使用StatefulEdge对象）
     */
    fun addStatefulEdge(edge: StatefulEdge): StatefulGraphBuilder {
        graph.addStatefulEdge(edge)
        return this
    }
    
    /**
     * 批量添加带状态的边
     */
    fun addStatefulEdges(edges: List<StatefulEdge>): StatefulGraphBuilder {
        edges.forEach { graph.addStatefulEdge(it) }
        return this
    }
    
    /**
     * 添加设置变量的边
     */
    fun addSetVariableEdge(
        from: String,
        to: String,
        action: String,
        variableKey: String,
        variableValue: Any,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val transform = StateTransforms.set(variableKey, variableValue)
        return addStatefulEdge(from, to, action, transform, weight, conditions)
    }
    
    /**
     * 添加设置多个变量的边
     */
    fun addSetVariablesEdge(
        from: String,
        to: String,
        action: String,
        variables: Map<String, Any>,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val transform = StateTransforms.setAll(variables)
        return addStatefulEdge(from, to, action, transform, weight, conditions)
    }
    
    /**
     * 添加移除变量的边
     */
    fun addRemoveVariableEdge(
        from: String,
        to: String,
        action: String,
        variableKey: String,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val transform = StateTransforms.remove(variableKey)
        return addStatefulEdge(from, to, action, transform, weight, conditions)
    }
    
    /**
     * 添加条件设置变量的边
     */
    fun addConditionalSetEdge(
        from: String,
        to: String,
        action: String,
        condition: (NodeState) -> Boolean,
        variableKey: String,
        variableValue: Any,
        weight: Double = 1.0,
        edgeConditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val transform = StateTransforms.conditionalSet(condition, variableKey, variableValue)
        return addStatefulEdge(from, to, action, transform, weight, edgeConditions)
    }
    
    /**
     * 添加计算变量的边
     */
    fun addComputeVariableEdge(
        from: String,
        to: String,
        action: String,
        targetKey: String,
        computation: (NodeState) -> Any?,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val transform = StateTransforms.compute(targetKey, computation)
        return addStatefulEdge(from, to, action, transform, weight, conditions)
    }
    
    /**
     * 添加复合状态转换的边
     */
    fun addCompositeTransformEdge(
        from: String,
        to: String,
        action: String,
        transforms: List<StateTransform>,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        val compositeTransform = StateTransforms.composite(transforms = transforms.toTypedArray())
        return addStatefulEdge(from, to, action, compositeTransform, weight, conditions)
    }
    
    /**
     * 添加双向状态转换边
     */
    fun addBidirectionalStatefulEdge(
        nodeA: String,
        nodeB: String,
        actionAtoB: String,
        actionBtoA: String = actionAtoB,
        transformAtoB: StateTransform = StateTransformIdentity,
        transformBtoA: StateTransform = StateTransformIdentity,
        weight: Double = 1.0,
        conditions: Set<String> = emptySet()
    ): StatefulGraphBuilder {
        addStatefulEdge(nodeA, nodeB, actionAtoB, transformAtoB, weight, conditions)
        addStatefulEdge(nodeB, nodeA, actionBtoA, transformBtoA, weight, conditions)
        return this
    }
    
    /**
     * 创建变量传递链（A->B->C...，每个边都传递指定变量）
     */
    fun createVariableChain(
        nodeIds: List<String>,
        action: String,
        variableKey: String,
        initialValue: Any,
        weight: Double = 1.0
    ): StatefulGraphBuilder {
        for (i in 0 until nodeIds.size - 1) {
            val transform = if (i == 0) {
                StateTransforms.set(variableKey, initialValue)
            } else {
                StateTransformIdentity  // 保持变量传递
            }
            addStatefulEdge(nodeIds[i], nodeIds[i + 1], action, transform, weight)
        }
        return this
    }
    
    /**
     * 创建变量累加链（每个节点都会对指定变量进行累加）
     */
    fun createAccumulatorChain(
        nodeIds: List<String>,
        action: String,
        variableKey: String,
        increment: Int = 1,
        weight: Double = 1.0
    ): StatefulGraphBuilder {
        for (i in 0 until nodeIds.size - 1) {
            val transform = StateTransforms.compute(variableKey) { state ->
                val current = state.getVariable<Int>(variableKey) ?: 0
                current + increment
            }
            addStatefulEdge(nodeIds[i], nodeIds[i + 1], action, transform, weight)
        }
        return this
    }
    
    /**
     * 构建并返回带状态的图
     */
    fun build(): StatefulGraph = graph
    
    /**
     * 构建并返回带有状态路径搜索器的图搜索器
     */
    fun buildWithFinder(): StatefulGraphSearcher {
        return StatefulGraphSearcher(graph)
    }
    
    companion object {
        /**
         * 创建新的带状态图构建器
         */
        fun create(): StatefulGraphBuilder = StatefulGraphBuilder()
    }
}

/**
 * 带状态的图搜索器，组合图和路径搜索器
 */
class StatefulGraphSearcher(val graph: StatefulGraph) {
    val pathFinder = StatefulPathFinder(graph)
    
    /**
     * 搜索状态路径
     */
    fun findPath(
        startState: NodeState,
        targetNodeId: String,
        targetStatePredicate: ((NodeState) -> Boolean)? = null,
        conditions: Set<String> = emptySet(),
        enableBacktrack: Boolean = true
    ): StatefulPathResult {
        return pathFinder.findPath(
            startState, 
            targetNodeId, 
            targetStatePredicate, 
            conditions, 
            enableBacktrack = enableBacktrack
        )
    }
    
    /**
     * 简化版本：搜索到指定节点的路径
     */
    fun findPathTo(
        startNodeId: String,
        targetNodeId: String,
        startVariables: Map<String, Any> = emptyMap(),
        conditions: Set<String> = emptySet()
    ): StatefulPathResult {
        val startState = NodeState(startNodeId, startVariables)
        return findPath(startState, targetNodeId, conditions = conditions)
    }
    
    /**
     * 搜索到具有指定变量值的节点的路径
     */
    fun findPathToVariableState(
        startNodeId: String,
        targetNodeId: String,
        targetVariable: String,
        targetValue: Any,
        startVariables: Map<String, Any> = emptyMap(),
        conditions: Set<String> = emptySet()
    ): StatefulPathResult {
        val startState = NodeState(startNodeId, startVariables)
        val predicate: (NodeState) -> Boolean = { state ->
            state.getVariable<Any>(targetVariable) == targetValue
        }
        return findPath(startState, targetNodeId, predicate, conditions)
    }
    
    /**
     * 获取图的统计信息
     */
    fun getStats(): GraphStats = graph.getStats()
} 