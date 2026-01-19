package com.ai.phoneagent.core.agent

/**
 * Agent配置
 */
data class AgentConfig(
    val maxSteps: Int = 100,                     // 最大执行步数（提高到100，实际由任务决定）
    val stepDelayMs: Long = 160L,                // 每步之间的延迟
    val maxModelRetries: Int = 3,                // 模型调用最大重试次数（提高到3，应对网络不稳定）
    val modelRetryBaseDelayMs: Long = 700L,      // 重试基础延迟
    val maxParseRepairs: Int = 2,                // 解析修复最大次数
    val temperature: Float? = 0.0f,              // 温度参数
    val topP: Float? = 0.85f,                    // top_p参数
    val frequencyPenalty: Float? = 0.2f,         // 频率惩罚
    val maxTokens: Int? = 4500,                  // 最大token数
    val maxContextTokens: Int = 24000,           // 最大上下文token数
    val maxUiTreeChars: Int = 3000,              // UI树最大字符数
    val maxHistoryTurns: Int = 6                 // 最多保留对话轮数
)

/**
 * 步骤结果
 */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: ParsedAgentAction?,
    val thinking: String?,
    val message: String? = null
)

/**
 * 解析的Agent动作
 */
data class ParsedAgentAction(
    val metadata: String,           // "do" 或 "finish"
    val actionName: String?,        // 动作名称（如 tap, swipe）
    val fields: Map<String, String>, // 动作参数
    val raw: String = ""            // 原始响应
)
