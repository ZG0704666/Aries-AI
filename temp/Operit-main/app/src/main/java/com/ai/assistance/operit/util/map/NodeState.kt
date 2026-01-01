package com.ai.assistance.operit.util.map

/**
 * 节点状态，表示节点在特定状态下的变异
 * @param nodeId 节点ID
 * @param variables 节点携带的变量状态
 * @param metadata 状态元数据
 */
data class NodeState(
    val nodeId: String,
    private val _variables: Map<String, Any> = emptyMap(),
    private val _metadata: Map<String, Any> = emptyMap()
) {
    // 创建防御性副本以确保不可变性
    val variables: Map<String, Any> = _variables.toMap()
    val metadata: Map<String, Any> = _metadata.toMap()
    /**
     * 获取变量值
     */
    inline fun <reified T> getVariable(key: String): T? {
        return variables[key] as? T
    }
    
    /**
     * 检查是否包含指定变量
     */
    fun hasVariable(key: String): Boolean = key in variables
    
    /**
     * 添加或更新变量
     */
    fun withVariable(key: String, value: Any): NodeState {
        return copy(_variables = this.variables + (key to value))
    }
    
    /**
     * 添加或更新多个变量
     */
    fun withVariables(newVariables: Map<String, Any>): NodeState {
        return copy(_variables = this.variables + newVariables)
    }
    
    /**
     * 移除变量
     */
    fun withoutVariable(key: String): NodeState {
        return copy(_variables = this.variables - key)
    }
    
    /**
     * 检查状态是否兼容（用于路径搜索中的状态合并判断）
     */
    fun isCompatibleWith(other: NodeState): Boolean {
        if (nodeId != other.nodeId) return false
        
        // 检查共同变量是否有冲突
        for ((key, value) in variables) {
            if (key in other.variables && other.variables[key] != value) {
                return false
            }
        }
        return true
    }
    
    /**
     * 合并状态（如果兼容）
     */
    fun mergeWith(other: NodeState): NodeState? {
        if (!isCompatibleWith(other)) return null
        
        return NodeState(
            nodeId = nodeId,
            _variables = this.variables + other.variables,
            _metadata = this.metadata + other.metadata
        )
    }
    
    /**
     * 创建状态的唯一标识符，用于路径搜索去重
     */
    fun getStateKey(): String {
        val sortedVars = this.variables.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$nodeId@[$sortedVars]"
    }
    
    override fun toString(): String {
        val varsStr = if (this.variables.isEmpty()) "" else this.variables.toString()
        return "NodeState($nodeId$varsStr)"
    }
} 