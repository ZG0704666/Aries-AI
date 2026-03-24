package com.ai.phoneagent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
)

@Serializable
data class ToolParameter(
    val name: String,
    val value: String,
)
