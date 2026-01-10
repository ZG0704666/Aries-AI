package com.ai.phoneagent.helper

/**
 * DeepSeek 风格流式解析器
 * 
 * 负责解析 AI 输出流，正确分离思考过程和回答内容。
 * 支持多种思考标记格式：
 * - <think>...</think>
 * - <思考>...</思考>
 * - <思考：...> 或 <思考:...>
 * - 【思考开始】...【思考结束】
 * - reasoning_content 字段
 */
class DeepSeekStreamParser {

    enum class ChunkType {
        THINKING,   // 思考内容
        ANSWER,     // 回答内容
        CONTROL     // 控制信号
    }

    private fun findAnswerStart(content: String): TagMatch? {
        var best: TagMatch? = null
        for (tag in ANSWER_START_TAGS) {
            val idx = content.indexOf(tag)
            if (idx != -1 && (best == null || idx < best.startIndex)) {
                best = TagMatch(idx, idx + tag.length, tag, false)
            }
        }
        return best
    }

    private fun findAnswerEnd(content: String): TagMatch? {
        var best: TagMatch? = null
        for (tag in ANSWER_END_TAGS) {
            val idx = content.indexOf(tag)
            if (idx != -1 && (best == null || idx < best.startIndex)) {
                best = TagMatch(idx, idx + tag.length, tag, false)
            }
        }
        return best
    }

    data class ParsedChunk(
        val type: ChunkType,
        val content: String
    )

    enum class ParseState {
        IDLE,                // 初始状态
        IN_THINKING,         // 思考中（通用）
        IN_THINKING_ANGLE,   // <思考：...> 格式的思考（以 > 结束）
        IN_ANSWER            // 回答中
    }

    private var currentState = ParseState.IDLE
    private val buffer = StringBuilder()
    private var hasReceivedReasoning = false

    private val ANSWER_START_TAGS = listOf(
        "【回答开始】",
        "【回答】",
    )

    private val ANSWER_END_TAGS = listOf(
        "【回答结束】",
    )

    fun reset() {
        currentState = ParseState.IDLE
        buffer.clear()
        hasReceivedReasoning = false
    }

    /**
     * 处理来自 reasoning_content 字段的增量
     */
    fun processReasoningDelta(delta: String): List<ParsedChunk> {
        if (delta.isEmpty()) return emptyList()
        
        hasReceivedReasoning = true
        if (currentState == ParseState.IDLE) {
            currentState = ParseState.IN_THINKING
        }
        
        return listOf(ParsedChunk(ChunkType.THINKING, delta))
    }

    /**
     * 处理来自 content 字段的增量
     */
    fun processContentDelta(delta: String): List<ParsedChunk> {
        if (delta.isEmpty()) return emptyList()
        
        buffer.append(delta)
        return parseBuffer()
    }

    private fun parseBuffer(): List<ParsedChunk> {
        val results = mutableListOf<ParsedChunk>()
        var content = buffer.toString()
        
        while (content.isNotEmpty()) {
            when (currentState) {
                ParseState.IDLE -> {
                    val thinkStart = findThinkingStart(content)
                    val answerStart = findAnswerStart(content)
                    if (thinkStart != null && (answerStart == null || thinkStart.startIndex <= answerStart.startIndex)) {
                        if (thinkStart.startIndex > 0) {
                            val before = content.substring(0, thinkStart.startIndex)
                            if (before.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.ANSWER, before))
                            }
                        }
                        // 根据标签类型决定状态
                        currentState = if (thinkStart.isAngleFormat) {
                            ParseState.IN_THINKING_ANGLE
                        } else {
                            ParseState.IN_THINKING
                        }
                        content = content.substring(thinkStart.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }

                    if (answerStart != null) {
                        if (answerStart.startIndex > 0) {
                            val before = content.substring(0, answerStart.startIndex)
                            if (before.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.ANSWER, before))
                            }
                        }
                        results.add(ParsedChunk(ChunkType.CONTROL, "ANSWER_START"))
                        currentState = ParseState.IN_ANSWER
                        content = content.substring(answerStart.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }
                    
                    if (isPotentialTagStart(content) || isPotentialAnswerStart(content) || isPotentialAnswerEnd(content)) {
                        break
                    }
                    
                    // 如果收到过 reasoning，content 默认是回答
                    if (hasReceivedReasoning) {
                        results.add(ParsedChunk(ChunkType.CONTROL, "ANSWER_START"))
                    }
                    currentState = ParseState.IN_ANSWER
                    results.add(ParsedChunk(ChunkType.ANSWER, content))
                    buffer.clear()
                    return results
                }
                
                ParseState.IN_THINKING -> {
                    val thinkEnd = findThinkingEnd(content, isAngleFormat = false)
                    if (thinkEnd != null) {
                        if (thinkEnd.startIndex > 0) {
                            val thinkContent = content.substring(0, thinkEnd.startIndex)
                            if (thinkContent.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.THINKING, thinkContent))
                            }
                        }
                        results.add(ParsedChunk(ChunkType.CONTROL, "THINKING_END"))
                        currentState = ParseState.IN_ANSWER
                        content = content.substring(thinkEnd.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }
                    
                    if (isPotentialTagEnd(content) || isPotentialAnswerStart(content)) {
                        val safeLen = (content.length - 12).coerceAtLeast(0)
                        if (safeLen > 0) {
                            results.add(ParsedChunk(ChunkType.THINKING, content.substring(0, safeLen)))
                            buffer.clear()
                            buffer.append(content.substring(safeLen))
                        }
                        break
                    }
                    
                    if (content.isNotBlank()) {
                        results.add(ParsedChunk(ChunkType.THINKING, content))
                    }
                    buffer.clear()
                    break
                }
                
                ParseState.IN_THINKING_ANGLE -> {
                    // <思考：...> 格式，以 > 结束
                    val endIdx = content.indexOf('>')
                    if (endIdx != -1) {
                        if (endIdx > 0) {
                            val thinkContent = content.substring(0, endIdx)
                            if (thinkContent.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.THINKING, thinkContent))
                            }
                        }
                        results.add(ParsedChunk(ChunkType.CONTROL, "THINKING_END"))
                        currentState = ParseState.IN_ANSWER
                        content = content.substring(endIdx + 1)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }
                    
                    // 全部作为思考内容
                    if (content.isNotBlank()) {
                        results.add(ParsedChunk(ChunkType.THINKING, content))
                    }
                    buffer.clear()
                    break
                }
                
                ParseState.IN_ANSWER -> {
                    val answerStart = findAnswerStart(content)
                    if (answerStart != null) {
                        if (answerStart.startIndex > 0) {
                            val before = content.substring(0, answerStart.startIndex)
                            if (before.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.ANSWER, before))
                            }
                        }
                        results.add(ParsedChunk(ChunkType.CONTROL, "ANSWER_START"))
                        content = content.substring(answerStart.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }

                    val answerEnd = findAnswerEnd(content)
                    if (answerEnd != null) {
                        if (answerEnd.startIndex > 0) {
                            val before = content.substring(0, answerEnd.startIndex)
                            if (before.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.ANSWER, before))
                            }
                        }
                        content = content.substring(answerEnd.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }

                    val thinkStart = findThinkingStart(content)
                    if (thinkStart != null) {
                        if (thinkStart.startIndex > 0) {
                            val before = content.substring(0, thinkStart.startIndex)
                            if (before.isNotBlank()) {
                                results.add(ParsedChunk(ChunkType.ANSWER, before))
                            }
                        }
                        currentState = if (thinkStart.isAngleFormat) {
                            ParseState.IN_THINKING_ANGLE
                        } else {
                            ParseState.IN_THINKING
                        }
                        content = content.substring(thinkStart.endIndex)
                        buffer.clear()
                        buffer.append(content)
                        continue
                    }
                    
                    if (isPotentialTagStart(content) || isPotentialAnswerStart(content) || isPotentialAnswerEnd(content)) {
                        val safeLen = (content.length - 10).coerceAtLeast(0)
                        if (safeLen > 0) {
                            results.add(ParsedChunk(ChunkType.ANSWER, content.substring(0, safeLen)))
                            buffer.clear()
                            buffer.append(content.substring(safeLen))
                        }
                        break
                    }
                    
                    if (content.isNotBlank()) {
                        results.add(ParsedChunk(ChunkType.ANSWER, content))
                    }
                    buffer.clear()
                    break
                }
            }
        }
        
        return results
    }

    private fun findThinkingStart(content: String): TagMatch? {
        // 按优先级检查各种开始标记
        val patterns = listOf(
            Triple("<think>", "<think>".length, false),
            Triple("<思考>", "<思考>".length, false),
            Triple("<思考：", "<思考：".length, true),  // angle format
            Triple("<思考:", "<思考:".length, true),   // angle format (半角冒号)
            Triple("【思考开始】", "【思考开始】".length, false),
            Triple("【思考】", "【思考】".length, false)
        )

        var best: TagMatch? = null
        for ((tag, len, isAngle) in patterns) {
            val idx = content.indexOf(tag)
            if (idx != -1 && (best == null || idx < best.startIndex)) {
                best = TagMatch(idx, idx + len, tag, isAngle)
            }
        }
        return best
    }

    private fun findThinkingEnd(content: String, isAngleFormat: Boolean): TagMatch? {
        if (isAngleFormat) {
            // 对于 <思考：...> 格式，结束标记是 >
            val idx = content.indexOf('>')
            if (idx != -1) {
                return TagMatch(idx, idx + 1, ">", false)
            }
            return null
        }
        
        val patterns = listOf(
            "</think>" to "</think>".length,
            "</思考>" to "</思考>".length,
            "【思考结束】" to "【思考结束】".length,
            "【回答】" to "【回答】".length,
            "【回答开始】" to "【回答开始】".length
        )

        var best: TagMatch? = null
        for ((tag, len) in patterns) {
            val idx = content.indexOf(tag)
            if (idx != -1 && (best == null || idx < best.startIndex)) {
                best = TagMatch(idx, idx + len, tag, false)
            }
        }
        return best
    }

    private fun isPotentialTagStart(content: String): Boolean {
        val potentials = listOf(
            "<", "<t", "<th", "<thi", "<thin", "<think",
            "<思", "<思考", "<思考：", "<思考:",
            "【", "【思", "【思考", "【思考开", "【思考开始"
        )
        return potentials.any { content.endsWith(it) }
    }

    private fun isPotentialAnswerStart(content: String): Boolean {
        val potentials = listOf(
            "【", "【回", "【回答", "【回答开", "【回答开始"
        )
        return potentials.any { content.endsWith(it) }
    }

    private fun isPotentialAnswerEnd(content: String): Boolean {
        val potentials = listOf(
            "【", "【回", "【回答", "【回答结", "【回答结束"
        )
        return potentials.any { content.endsWith(it) }
    }

    private fun isPotentialTagEnd(content: String): Boolean {
        val potentials = listOf(
            "<", "</", "</t", "</th", "</thi", "</thin", "</think",
            "</思", "</思考",
            "【", "【思", "【思考", "【思考结", "【思考结束", "【回", "【回答"
        )
        return potentials.any { content.endsWith(it) }
    }

    fun flush(): List<ParsedChunk> {
        val results = mutableListOf<ParsedChunk>()
        val remaining = buffer.toString()
        if (remaining.isNotBlank()) {
            when (currentState) {
                ParseState.IN_THINKING, ParseState.IN_THINKING_ANGLE -> {
                    results.add(ParsedChunk(ChunkType.THINKING, remaining))
                }
                else -> {
                    results.add(ParsedChunk(ChunkType.ANSWER, remaining))
                }
            }
        }
        buffer.clear()
        return results
    }

    fun isInThinkingPhase(): Boolean = currentState in listOf(
        ParseState.IN_THINKING, 
        ParseState.IN_THINKING_ANGLE, 
        ParseState.IDLE
    )

    private data class TagMatch(
        val startIndex: Int, 
        val endIndex: Int, 
        val tag: String,
        val isAngleFormat: Boolean
    )
}
