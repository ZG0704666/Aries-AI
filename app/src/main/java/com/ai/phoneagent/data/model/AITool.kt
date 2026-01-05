package com.ai.phoneagent.data.model

data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
)

data class ToolParameter(
    val name: String,
    val value: String,
)
