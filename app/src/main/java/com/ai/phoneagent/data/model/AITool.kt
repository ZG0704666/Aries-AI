package com.ai.phoneagent.data.model

import kotlinx.serialization.Serializable

/** AI 工具定义：名称与参数列表。 */
@Serializable
data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
)

/** 工具参数键值对。 */
@Serializable
data class ToolParameter(
    val name: String,
    val value: String,
)
