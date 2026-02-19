/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.parser

import com.ai.phoneagent.core.agent.ParsedAgentAction

/**
 * 动作解析器 - 单一职责
 * 
 * 负责解析模型输出的文本，提取动作信息
 * 支持 Open-AutoGLM 格式：<think> 和 <answer> XML 标签
 */
class ActionParser {
    
    /**
     * 解析Agent动作
     */
    fun parse(raw: String): ParsedAgentAction {
        val original = raw.trim()
        
        // 检测截断迹象
        val hasTruncationSign = original.contains("\uFFFD") ||
            original.endsWith("…") ||
            original.endsWith("...") ||
            (original.contains("我需要") && !original.contains("do(") && !original.contains("finish("))
        
        // 检测长文本无动作的情况
        if (original.contains("text=\"") && original.count { it == '=' } > 10 && 
            !original.contains("do(") && !original.contains("finish(")) {
            return ParsedAgentAction("unknown", null, emptyMap(), original.take(200))
        }
        
        // 截断检测
        if (hasTruncationSign && !original.contains("do(") && !original.contains("finish(")) {
            return ParsedAgentAction("unknown", null, emptyMap(), "输出被截断，未包含动作")
        }
        
        // 尝试从 <answer> 标签中提取动作
        val answerFromTag = extractFromAnswerTag(original)
        if (answerFromTag != null) {
            return parseActionFromString(answerFromTag)
        }
        
        // 尝试从【回答开始】【回答结束】标签提取
        val answerFromChinese = extractFromChineseTag(original, "回答")
        if (answerFromChinese != null) {
            return parseActionFromString(answerFromChinese)
        }
        
        // 查找动作开始位置（兜底）
        val finishIndex = original.lastIndexOf("finish(")
        val doIndex = original.lastIndexOf("do(")
        val startIndex = when {
            finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex)
            finishIndex >= 0 -> finishIndex
            doIndex >= 0 -> doIndex
            else -> -1
        }
        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original

        return parseActionFromString(trimmed)
    }
    
    /**
     * 从 <answer> XML 标签中提取动作
     */
    private fun extractFromAnswerTag(text: String): String? {
        val pattern = Regex("""<answer>\s*(.*?)\s*</answer>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }
    
    /**
     * 从【回答开始】【回答结束】标签提取
     */
    private fun extractFromChineseTag(text: String, tagName: String): String? {
        val startTag = "【${tagName}开始】"
        val endTag = "【${tagName}结束】"
        val startIdx = text.indexOf(startTag)
        val endIdx = text.indexOf(endTag)
        
        if (startIdx >= 0 && endIdx >= 0 && endIdx > startIdx) {
            return text.substring(startIdx + startTag.length, endIdx).trim()
        }
        return null
    }
    
    /**
     * 从字符串中解析动作
     */
    private fun parseActionFromString(content: String): ParsedAgentAction {
        val trimmed = content.trim()
        
        // 解析 finish
        if (trimmed.startsWith("finish")) {
            val messageRegex = Regex(
                """finish\s*\(\s*message\s*=\s*"(.*)"""",
                RegexOption.DOT_MATCHES_ALL
            )
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction("finish", null, mapOf("message" to message), trimmed)
        }
        
        // 检查是否为 do 动作
        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction("unknown", null, emptyMap(), trimmed.take(200))
        }
        
        // 找到参数区域的括号
        val openParenIndex = trimmed.indexOf('(')
        if (openParenIndex < 0) {
            return ParsedAgentAction("unknown", null, emptyMap(), "do命令不完整")
        }
        
        // 找到匹配的闭括号
        var parenCount = 0
        var closeParenIndex = -1
        for (i in openParenIndex until trimmed.length) {
            when (trimmed[i]) {
                '(' -> parenCount++
                ')' -> {
                    parenCount--
                    if (parenCount == 0) {
                        closeParenIndex = i
                        break
                    }
                }
            }
        }
        
        val inner = if (closeParenIndex > openParenIndex) {
            trimmed.substring(openParenIndex + 1, closeParenIndex).trim()
        } else {
            trimmed.substring(openParenIndex + 1).trim().trimEnd(')', ',', ' ')
        }
        
        // 解析参数
        val fields = parseParameters(inner)
        
        // 获取 action 参数
        if (fields.containsKey("action")) {
            return ParsedAgentAction("do", fields["action"], fields, trimmed)
        }

        return ParsedAgentAction("unknown", null, emptyMap(), trimmed.take(200))
    }
    
    /**
     * 解析参数字符串
     */
    private fun parseParameters(paramsStr: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|"(.*?)"|'([^']*)'|([^,)]+))""")
        regex.findAll(paramsStr).forEach { m ->
            val key = m.groupValues[1].trim()
            if (key.isBlank()) return@forEach
            val value = m.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
            val lowerKey = key.lowercase()
            if (!fields.containsKey(lowerKey)) {
                fields[lowerKey] = value
            }
        }
        return fields
    }
    
    /**
     * 从思考和回答中提取动作 - 兼容旧格式
     */
    fun parseWithThinking(content: String): Pair<String?, String> {
        val full = content.trim()
        
        // 尝试 Open-AutoGLM 格式：<think> 和 <answer>
        val thinkFromTag = extractFromThinkTag(full)
        val answerFromTag = extractFromAnswerTag(full)
        if (answerFromTag != null) {
            return thinkFromTag to answerFromTag
        }
        
        // 尝试 Aries 思考格式
        val ariesResult = extractAriesThinkingAndAnswer(full)
        if (!ariesResult.second.isNullOrBlank()) {
            return ariesResult
        }
        
        // 尝试 XML 标签格式
        val thinkTag = extractTagContent(full, "think")
        val answerTag = extractTagContent(full, "answer")
        if (answerTag != null) {
            return thinkTag to answerTag
        }
        
        // 尝试简单格式
        val finishMarker = "finish(message="
        val doMarker = "do(action="
        val finishIndex = full.indexOf(finishMarker)
        if (finishIndex >= 0) {
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null }
            val action = full.substring(finishIndex).trim()
            return thinking to action
        }
        val doIndex = full.indexOf(doMarker)
        if (doIndex >= 0) {
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null }
            val action = full.substring(doIndex).trim()
            return thinking to action
        }
        
        return null to full
    }
    
    /**
     * 从 <think> 标签提取思考
     */
    private fun extractFromThinkTag(text: String): String? {
        val pattern = Regex("""<think>\s*(.*?)\s*</think>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
    }
    
    /**
     * 提取 Aries 格式的思考和回答
     */
    private fun extractAriesThinkingAndAnswer(text: String): Pair<String?, String> {
        val thinkStartTag = "【思考开始】"
        val thinkEndTag = "【思考结束】"
        val answerStartTags = listOf("【回答开始】", "【回答】")
        val answerEndTag = "【回答结束】"

        val thinkStartIdx = text.indexOf(thinkStartTag)
        val thinkEndIdx = text.indexOf(thinkEndTag)

        val answerStartMatch = answerStartTags
            .mapNotNull { tag ->
                val idx = text.indexOf(tag)
                if (idx >= 0) idx to tag else null
            }
            .minByOrNull { it.first }
        val answerEndIdx = text.indexOf(answerEndTag)

        val thinking: String? = if (thinkStartIdx >= 0) {
            val start = thinkStartIdx + thinkStartTag.length
            val end = when {
                thinkEndIdx >= start -> thinkEndIdx
                answerStartMatch != null && answerStartMatch.first >= start -> answerStartMatch.first
                else -> text.length
            }
            text.substring(start, end).trim().ifBlank { null }
        } else {
            null
        }

        val answer: String = if (answerStartMatch != null) {
            val start = answerStartMatch.first + answerStartMatch.second.length
            val end = if (answerEndIdx >= start) answerEndIdx else text.length
            text.substring(start, end).trim()
        } else {
            ""
        }

        return thinking to answer
    }
    
    /**
     * 提取标签内容
     */
    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }
    
    /**
     * 解析预估步骤数
     */
    fun parseEstimatedSteps(thinking: String): Int {
        // 显式模式
        val explicitPatterns = listOf(
            Regex("""(?:需要|大约|共|总共|预计)\s*(\d+)\s*(?:步|个步骤|个操作)"""),
            Regex("""(\d+)\s*(?:步|个步骤|个操作)(?:完成|即可|就能)"""),
        )
        for (pattern in explicitPatterns) {
            val match = pattern.find(thinking)
            if (match != null) {
                val num = match.groupValues.getOrNull(1)?.toIntOrNull()
                if (num != null && num in 2..20) return num
            }
        }
        
        // "我需要" 模式
        val needPattern = Regex("""我需要[：:]\s*([\s\S]*?)(?:首先|然后|接下来|现在|$)""")
        val needMatch = needPattern.find(thinking)
        if (needMatch != null) {
            val needContent = needMatch.groupValues.getOrNull(1).orEmpty()
            val numbers = Regex("""(\d+)\s*[\.、）\)：:]""")
                .findAll(needContent)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
            if (numbers.isNotEmpty()) {
                val maxStep = numbers.maxOrNull() ?: 0
                if (maxStep in 2..20) return maxStep
            }
        }
        
        // 编号步骤模式
        val numberedSteps = Regex("""(?:^|\n|\s|，|。|；)(\d+)\s*[\.、）\)：:]|第(\d+)步""")
            .findAll(thinking)
            .mapNotNull { 
                it.groupValues.getOrNull(1)?.toIntOrNull() 
                    ?: it.groupValues.getOrNull(2)?.toIntOrNull() 
            }
            .toList()
        
        if (numberedSteps.isNotEmpty()) {
            val maxStep = numberedSteps.maxOrNull() ?: 0
            if (maxStep in 2..20) return maxStep
        }
        
        // 动作关键词计数
        val actionKeywords = listOf("点击", "输入", "滑动", "打开", "选择", "返回", "等待", "启动", "查询", "修改")
        val actionCount = actionKeywords.sumOf { keyword -> 
            thinking.split(keyword).size - 1 
        }
        if (actionCount >= 2) {
            return actionCount.coerceIn(2, 15)
        }
        
        return 0
    }
}
