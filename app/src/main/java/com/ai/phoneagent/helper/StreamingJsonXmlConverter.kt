package com.ai.phoneagent.helper

import org.json.JSONArray
import org.json.JSONObject

/**
 * 流式JSON到XML转换器
 * 
 * 用于将增量的JSON片段实时转换为XML格式
 * 主要用于Tool Call参数的流式转换
 * 
 * 示例：
 * JSON: {"name":"value","count":123}
 * XML:  <param name="name">value</param>
 *       <param name="count">123</param>
 */
class StreamingJsonXmlConverter {
    
    /**
     * 转换事件
     */
    sealed class Event {
        data class Tag(val text: String) : Event()
        data class Content(val text: String) : Event()
    }
    
    private val buffer = StringBuilder()
    private var inString = false
    private var escapeNext = false
    private val stack = mutableListOf<StackItem>()
    private var currentKey: String? = null
    private var depth = 0
    
    private sealed class StackItem {
        object Object : StackItem()
        object Array : StackItem()
        data class Key(val name: String) : StackItem()
    }
    
    /**
     * 喂入JSON片段
     */
    fun feed(jsonChunk: String): List<Event> {
        val events = mutableListOf<Event>()
        
        jsonChunk.forEach { char ->
            when {
                escapeNext -> {
                    buffer.append(char)
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    escapeNext = true
                    buffer.append(char)
                }
                char == '"' -> {
                    if (inString) {
                        // 字符串结束
                        inString = false
                        handleStringEnd(events)
                    } else {
                        // 字符串开始
                        inString = true
                        buffer.clear()
                    }
                }
                inString -> {
                    buffer.append(char)
                }
                char == '{' -> {
                    depth++
                    stack.add(StackItem.Object)
                }
                char == '[' -> {
                    depth++
                    stack.add(StackItem.Array)
                }
                char == '}' -> {
                    depth--
                    if (stack.lastOrNull() is StackItem.Object) {
                        stack.removeLastOrNull()
                    }
                }
                char == ']' -> {
                    depth--
                    if (stack.lastOrNull() is StackItem.Array) {
                        stack.removeLastOrNull()
                    }
                }
                char == ':' -> {
                    // Key-value分隔符，等待value
                }
                char == ',' -> {
                    // 下一个键值对
                    currentKey = null
                }
            }
        }
        
        return events
    }
    
    /**
     * 处理字符串结束
     */
    private fun handleStringEnd(events: MutableList<Event>) {
        val value = buffer.toString()
        buffer.clear()
        
        if (currentKey == null) {
            // 这是一个key
            currentKey = value
            events.add(Event.Tag("<param name=\"$value\">"))
            stack.add(StackItem.Key(value))
        } else {
            // 这是一个value
            val escapedValue = escapeXml(value)
            events.add(Event.Content(escapedValue))
            events.add(Event.Tag("</param>"))
            
            // 移除key
            if (stack.lastOrNull() is StackItem.Key) {
                stack.removeLastOrNull()
            }
            currentKey = null
        }
    }
    
    /**
     * 刷新未完成的内容
     */
    fun flush(): List<Event> {
        val events = mutableListOf<Event>()
        
        // 如果还有未完成的字符串
        if (inString && buffer.isNotEmpty()) {
            val value = buffer.toString()
            val escapedValue = escapeXml(value)
            events.add(Event.Content(escapedValue))
            buffer.clear()
        }
        
        // 关闭所有未关闭的标签
        while (stack.isNotEmpty()) {
            when (val item = stack.removeLastOrNull()) {
                is StackItem.Key -> {
                    events.add(Event.Tag("</param>"))
                }
                else -> {}
            }
        }
        
        return events
    }
    
    /**
     * 检查是否有未完成的参数
     */
    fun hasUnfinishedParam(): Boolean {
        return stack.any { it is StackItem.Key }
    }
    
    /**
     * XML转义
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        buffer.clear()
        inString = false
        escapeNext = false
        stack.clear()
        currentKey = null
        depth = 0
    }
}

/**
 * XML转义工具
 */
object XmlEscaper {
    fun escape(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    fun unescape(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}

/**
 * Tool Call相关的正则表达式
 */
object ChatMarkupRegex {
    // 匹配 <tool name="xxx">...</tool>
    val toolCallPattern = """<tool\s+name="([^"]+)">(.+?)</tool>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    
    // 匹配 <param name="xxx">value</param>
    val toolParamPattern = """<param\s+name="([^"]+)">(.+?)</param>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    
    // 匹配 <tool_result>...</tool_result>
    val toolResultAnyPattern = """<tool_result[^>]*>(.+?)</tool_result>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    
    // 匹配带name属性的 <tool_result name="xxx">...</tool_result>
    val toolResultWithNameAnyPattern = """<tool_result\s+name="([^"]+)"[^>]*>(.+?)</tool_result>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    
    // 匹配 <content>...</content>
    val contentTag = """<content>(.+?)</content>""".toRegex(RegexOption.DOT_MATCHES_ALL)
}
