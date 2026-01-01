package com.ai.assistance.operit.util.map

/**
 * 状态转换器，定义如何从一个状态转换到另一个状态
 */
interface StateTransform {

    /**
     * 检查是否可以应用此转换
     */
    fun canApply(state: NodeState): Boolean = true

    /**
     * 应用转换
     * @param state 当前状态
     * @param context 运行时提供的上下文，可用于模板渲染等
     * @return 转换后的新状态，如果无法应用则为null
     */
    fun apply(state: NodeState, context: Map<String, Any> = emptyMap()): NodeState?
}

/**
 * 恒等转换（保持状态不变）
 */
object StateTransformIdentity : StateTransform {
    override fun canApply(state: NodeState): Boolean = true
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState = state
    override fun toString(): String = "StateTransformIdentity"
}

/**
 * 设置变量
 */
data class StateTransformSetVariable(
    val key: String,
    val value: Any
) : StateTransform {
    override fun canApply(state: NodeState): Boolean = true
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState {
        val resolvedValue = if (value is String) resolveTemplate(value, context) else value
        return state.withVariable(key, resolvedValue)
    }
    override fun toString(): String = "StateTransform.Set(key=$key, value=$value)"
}

/**
 * 设置多个变量
 */
data class StateTransformSetVariables(
    val variables: Map<String, Any>
) : StateTransform {
    override fun canApply(state: NodeState): Boolean = true
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState {
        val resolvedVariables = variables.mapValues { (_, value) ->
            if (value is String) resolveTemplate(value, context) else value
        }
        return state.withVariables(resolvedVariables)
    }
    override fun toString(): String = "StateTransform.SetAll(variables=$variables)"
}

/**
 * 移除变量
 */
data class StateTransformRemoveVariable(
    val key: String
) : StateTransform {
    override fun canApply(state: NodeState): Boolean = true
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState {
        return state.withoutVariable(key)
    }
    override fun toString(): String = "StateTransform.Remove(key=$key)"
}

/**
 * 条件变量设置（只有满足条件才设置）
 */
data class StateTransformConditionalSet(
    val condition: (NodeState) -> Boolean,
    val key: String,
    val value: Any
) : StateTransform {
    override fun canApply(state: NodeState): Boolean = condition(state)
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState? {
        if (!canApply(state)) return null
        val resolvedValue = if (value is String) resolveTemplate(value, context) else value
        return state.withVariable(key, resolvedValue)
    }
    override fun toString(): String = "StateTransform.ConditionalSet(key=$key, value=$value)"
}

/**
 * 变量计算转换
 */
data class StateTransformComputeVariable(
    val targetKey: String,
    val computation: (NodeState) -> Any?
) : StateTransform {
    override fun canApply(state: NodeState): Boolean = true
    override fun apply(state: NodeState, context: Map<String, Any>): NodeState? {
        val result = computation(state) ?: return null
        return state.withVariable(targetKey, result)
    }
    override fun toString(): String = "StateTransform.Compute(targetKey=$targetKey)"
}

/**
 * 组合多个转换
 */
data class StateTransformComposite(
    val transforms: List<StateTransform>
) : StateTransform {
    override fun canApply(state: NodeState): Boolean {
        // 在 canApply 阶段，我们无法提供完整的 context，因此这里只做基本检查。
        // 真正的带参数转换发生在 apply 阶段，所以这里假设一个空上下文进行检查。
        var currentState = state
        for (transform in transforms) {
            if (!transform.canApply(currentState)) return false
            val newState = transform.apply(currentState, emptyMap()) ?: return false
            currentState = newState
        }
        return true
    }

    override fun apply(state: NodeState, context: Map<String, Any>): NodeState? {
        var currentState = state
        for (transform in transforms) {
            currentState = transform.apply(currentState, context) ?: return null
        }
        return currentState
    }
    override fun toString(): String = "StateTransform.Composite(transforms=${transforms.joinToString(" -> ")})"
}

object StateTransforms {
    /**
     * 创建设置变量的转换
     */
    fun set(key: String, value: Any): StateTransform = StateTransformSetVariable(key, value)

    /**
     * 创建设置多个变量的转换
     */
    fun setAll(variables: Map<String, Any>): StateTransform = StateTransformSetVariables(variables)

    /**
     * 创建移除变量的转换
     */
    fun remove(key: String): StateTransform = StateTransformRemoveVariable(key)

    /**
     * 创建条件设置的转换
     */
    fun conditionalSet(
        condition: (NodeState) -> Boolean,
        key: String,
        value: Any
    ): StateTransform = StateTransformConditionalSet(condition, key, value)

    /**
     * 创建计算变量的转换
     */
    fun compute(
        targetKey: String,
        computation: (NodeState) -> Any?
    ): StateTransform = StateTransformComputeVariable(targetKey, computation)

    /**
     * 组合多个转换
     */
    fun composite(vararg transforms: StateTransform): StateTransform =
        StateTransformComposite(transforms.toList())
}

/**
 * 解析字符串模板，例如 "你好, {{user_name}}!"
 * @param template 模板字符串
 * @param context 包含替换值的Map
 * @return 解析后的字符串
 */
internal fun resolveTemplate(template: String, context: Map<String, Any>): String {
    if (!template.contains("{{")) return template // 优化：如果模板中没有插值标记，直接返回
    var result = template
    // 正则表达式匹配 {{key}} 格式
    val regex = "\\{\\{(.+?)}}".toRegex()
    result = regex.replace(result) { matchResult ->
        val key = matchResult.groupValues[1].trim()
        context[key]?.toString() ?: matchResult.value // 如果 context 中没有，则保留原样
    }
    return result
} 