package com.ai.phoneagent.core.utils

/**
 * 思考标签常量
 * 统一管理思考/回答内容的开始和结束标签
 */
object ThinkingTags {

    // ========== 思考开始标签 ==========
    val THINKING_START_TAGS = listOf(
        "<think>",
        "<思考>",
        "<思考：",
        "<思考:",
        "【思考开始】",
        "【思考】"
    )

    // ========== 思考结束标签 ==========
    val THINKING_END_TAGS = listOf(
        "</think>",
        "</思考>",
        "【思考结束】",
        "【回答】",
        "【回答开始】"
    )

    // ========== 思考结束标签(角格式用) ==========
    val THINKING_END_ANGLE = ">"

    // ========== 回答开始标签 ==========
    val ANSWER_START_TAGS = listOf(
        "【回答开始】",
        "【回答】"
    )

    // ========== 回答结束标签 ==========
    val ANSWER_END_TAGS = listOf(
        "【回答结束】"
    )

    // ========== 所有思考相关标签（用于清理） ==========
    val ALL_THINKING_TAGS = listOf(
        "<think>",
        "</<think>>",
        "<思考>",
        "</思考>",
        "<思考：",
        "<思考:",
        "【思考开始】",
        "【思考】",
        "【思考结束】"
    )

    // ========== 所有回答相关标签（用于清理） ==========
    val ALL_ANSWER_TAGS = listOf(
        "【回答开始】",
        "【回答】",
        "【回答结束】"
    )

    // ========== 潜在标签前缀（用于流式解析时的边界判断） ==========
    val POTENTIAL_THINKING_START_PREFIXES = listOf(
        "<", "<t", "<th", "<thi", "<thin", "<think",
        "<思", "<思考", "<思考：", "<思考:",
        "【", "【思", "【思考", "【思考开", "【思考开始"
    )

    val POTENTIAL_THINKING_END_PREFIXES = listOf(
        "<", "</", "</t", "</th", "</thi", "</thin", "</think",
        "</思", "</思考",
        "【", "【思", "【思考", "【思考结", "【思考结束", "【回", "【回答"
    )

    val POTENTIAL_ANSWER_START_PREFIXES = listOf(
        "【", "【回", "【回答", "【回答开", "【回答开始"
    )

    val POTENTIAL_ANSWER_END_PREFIXES = listOf(
        "【", "【回", "【回答", "【回答结", "【回答结束"
    )

    /**
     * 检查字符串是否以潜在思考开始标签结尾
     */
    fun isPotentialThinkingStart(content: String): Boolean {
        return POTENTIAL_THINKING_START_PREFIXES.any { content.endsWith(it) }
    }

    /**
     * 检查字符串是否以潜在思考结束标签结尾
     */
    fun isPotentialThinkingEnd(content: String): Boolean {
        return POTENTIAL_THINKING_END_PREFIXES.any { content.endsWith(it) }
    }

    /**
     * 检查字符串是否以潜在回答开始标签结尾
     */
    fun isPotentialAnswerStart(content: String): Boolean {
        return POTENTIAL_ANSWER_START_PREFIXES.any { content.endsWith(it) }
    }

    /**
     * 检查字符串是否以潜在回答结束标签结尾
     */
    fun isPotentialAnswerEnd(content: String): Boolean {
        return POTENTIAL_ANSWER_END_PREFIXES.any { content.endsWith(it) }
    }
}
