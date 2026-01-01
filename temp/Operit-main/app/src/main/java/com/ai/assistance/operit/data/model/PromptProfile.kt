package com.ai.assistance.operit.data.model

/**
 * 模型提示词配置文件数据类
 */
data class PromptProfile(
    val id: String,
    val name: String,
    val introPrompt: String,
    val tonePrompt: String,
    val isActive: Boolean = false,
    val isDefault: Boolean = false
) 