package com.ai.assistance.operit.util

/**
 * 流式 JSON 到 XML 的转换器
 * 专门用于将 Tool Call 的 arguments JSON 流增量转换为 XML 格式
 * 例如: {"arg1": "val"} -> <param name="arg1">val</param>
 */
class StreamingJsonXmlConverter {

    /**
     * XML 流事件
     */
    sealed class Event {
        data class Tag(val text: String) : Event()
        data class Content(val text: String) : Event()
    }

    private enum class State {
        WAIT_BRACE,      // 等待起始 {
        WAIT_KEY_QUOTE,  // 等待 Key 的起始 "
        READ_KEY,        // 读取 Key 内容
        WAIT_COLON,      // 等待 :
        WAIT_VALUE,      // 等待 Value
        READ_STRING,     // 读取 String Value
        READ_PRIMITIVE,  // 读取 Primitive Value
        ESCAPE,          // 转义字符处理
        UNICODE_ESCAPE,  // Unicode 转义
        WAIT_COMMA       // 等待 , 或 }
    }

    private var state = State.WAIT_BRACE
    private val buffer = StringBuilder()
    private var unicodeCount = 0

    /**
     * 处理 JSON 块并返回 XML 事件列表
     */
    fun feed(chunk: String): List<Event> {
        val events = mutableListOf<Event>()

        for (c in chunk) {
            when (state) {
                State.WAIT_BRACE -> if (c == '{') state = State.WAIT_KEY_QUOTE
                State.WAIT_KEY_QUOTE -> {
                    if (c == '"') {
                        state = State.READ_KEY
                        buffer.setLength(0)
                    } else if (c == '}') {
                        // 对象结束
                    }
                }
                State.READ_KEY -> {
                    if (c == '"') {
                        events.add(Event.Tag("\n  <param name=\"${buffer}\">"))
                        state = State.WAIT_COLON
                    } else {
                        if (c != '\\') buffer.append(c)
                    }
                }
                State.WAIT_COLON -> if (c == ':') state = State.WAIT_VALUE
                State.WAIT_VALUE -> {
                    if (!c.isWhitespace()) {
                        if (c == '"') {
                            state = State.READ_STRING
                        } else {
                            state = State.READ_PRIMITIVE
                            buffer.setLength(0)
                            buffer.append(c)
                        }
                    }
                }
                State.READ_STRING -> {
                    if (c == '"') {
                        state = State.WAIT_COMMA
                        events.add(Event.Tag("</param>"))
                    } else if (c == '\\') {
                        state = State.ESCAPE
                    } else {
                        events.add(Event.Content(escapeXml(c.toString())))
                    }
                }
                State.ESCAPE -> {
                    if (c == 'u') {
                        state = State.UNICODE_ESCAPE
                        unicodeCount = 0
                        buffer.setLength(0)
                    } else {
                        val unescaped = when (c) {
                            'n' -> "\n"
                            'r' -> "\r"
                            't' -> "\t"
                            'b' -> "\b"
                            'f' -> "\u000c"
                            '\"' -> "\""
                            '\\' -> "\\"
                            '/' -> "/"
                            else -> c.toString()
                        }
                        events.add(Event.Content(escapeXml(unescaped)))
                        state = State.READ_STRING
                    }
                }
                State.UNICODE_ESCAPE -> {
                    buffer.append(c)
                    unicodeCount++
                    if (unicodeCount == 4) {
                        try {
                            val code = buffer.toString().toInt(16)
                            events.add(Event.Content(escapeXml(code.toChar().toString())))
                        } catch (_: Exception) { }
                        state = State.READ_STRING
                    }
                }
                State.READ_PRIMITIVE -> {
                    if (c == ',' || c == '}' || c.isWhitespace()) {
                        events.add(Event.Content(escapeXml(buffer.toString())))
                        events.add(Event.Tag("</param>"))
                        
                        if (c == ',') state = State.WAIT_KEY_QUOTE
                        else if (c == '}') state = State.WAIT_BRACE
                        else state = State.WAIT_COMMA
                    } else {
                        buffer.append(c)
                    }
                }
                State.WAIT_COMMA -> {
                    if (c == ',') state = State.WAIT_KEY_QUOTE
                    else if (c == '}') state = State.WAIT_BRACE
                }
            }
        }
        return events
    }

    /**
     * 刷新缓冲区，处理剩余的原始值
     */
    fun flush(): List<Event> {
        val events = mutableListOf<Event>()
        if (state == State.READ_PRIMITIVE && buffer.isNotEmpty()) {
            events.add(Event.Content(escapeXml(buffer.toString())))
            events.add(Event.Tag("</param>"))
        }
        return events
    }

    /**
     * XML 转义辅助函数
     */
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}
