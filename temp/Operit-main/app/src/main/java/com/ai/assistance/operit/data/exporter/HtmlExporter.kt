package com.ai.assistance.operit.data.exporter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.format.DateTimeFormatter

/**
 * HTML 格式导出器
 */
object HtmlExporter {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 导出单个对话为 HTML
     */
    fun exportSingle(chatHistory: ChatHistory): String {
        val sb = StringBuilder()
        
        appendHtmlHeader(sb, chatHistory.title)
        appendChatContent(sb, chatHistory)
        appendHtmlFooter(sb)
        
        return sb.toString()
    }
    
    /**
     * 导出多个对话为 HTML
     */
    fun exportMultiple(chatHistories: List<ChatHistory>): String {
        val sb = StringBuilder()
        
        appendHtmlHeader(sb, "聊天记录导出")
        
        sb.appendLine("<div class=\"export-info\">")
        sb.appendLine("  <h1>聊天记录导出</h1>")
        sb.appendLine("  <p><strong>导出时间:</strong> ${java.time.LocalDateTime.now().format(dateFormatter)}</p>")
        sb.appendLine("  <p><strong>对话数量:</strong> ${chatHistories.size}</p>")
        sb.appendLine("  <p><strong>总消息数:</strong> ${chatHistories.sumOf { it.messages.size }}</p>")
        sb.appendLine("</div>")
        sb.appendLine("<hr>")
        
        for ((index, chatHistory) in chatHistories.withIndex()) {
            if (index > 0) {
                sb.appendLine("<hr class=\"conversation-divider\">")
            }
            appendChatContent(sb, chatHistory)
        }
        
        appendHtmlFooter(sb)
        
        return sb.toString()
    }
    
    /**
     * 添加 HTML 头部
     */
    private fun appendHtmlHeader(sb: StringBuilder, title: String) {
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"zh-CN\">")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"UTF-8\">")
        sb.appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.appendLine("  <title>$title</title>")
        sb.appendLine("  <style>")
        sb.appendLine(getCss())
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")
        sb.appendLine("<div class=\"container\">")
    }
    
    /**
     * 添加对话内容
     */
    private fun appendChatContent(sb: StringBuilder, chatHistory: ChatHistory) {
        sb.appendLine("<div class=\"conversation\">")
        sb.appendLine("  <div class=\"conversation-header\">")
        sb.appendLine("    <h2>${escapeHtml(chatHistory.title)}</h2>")
        sb.appendLine("    <div class=\"metadata\">")
        sb.appendLine("      <span><strong>创建:</strong> ${chatHistory.createdAt.format(dateFormatter)}</span>")
        sb.appendLine("      <span><strong>更新:</strong> ${chatHistory.updatedAt.format(dateFormatter)}</span>")
        if (chatHistory.group != null) {
            sb.appendLine("      <span><strong>分组:</strong> ${escapeHtml(chatHistory.group)}</span>")
        }
        sb.appendLine("      <span><strong>消息数:</strong> ${chatHistory.messages.size}</span>")
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")
        sb.appendLine("  <div class=\"messages\">")
        
        for (message in chatHistory.messages) {
            appendMessageHtml(sb, message)
        }
        
        sb.appendLine("  </div>")
        sb.appendLine("</div>")
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessageHtml(sb: StringBuilder, message: ChatMessage) {
        val messageClass = if (message.sender == "user") "user" else "assistant"
        val icon = if (message.sender == "user") "👤" else "🤖"
        val role = if (message.sender == "user") "User" else "Assistant"
        
        sb.appendLine("    <div class=\"message $messageClass\">")
        sb.appendLine("      <div class=\"message-header\">")
        sb.appendLine("        <span class=\"role\">$icon $role</span>")
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            sb.appendLine("        <span class=\"model\">${escapeHtml(message.modelName)}</span>")
        }
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"message-content\">")
        sb.appendLine("        ${formatContent(message.content)}")
        sb.appendLine("      </div>")
        sb.appendLine("    </div>")
    }
    
    /**
     * 添加 HTML 尾部
     */
    private fun appendHtmlFooter(sb: StringBuilder) {
        sb.appendLine("</div>")
        sb.appendLine("<footer>")
        sb.appendLine("  <p>导出自 Operit AI Assistant</p>")
        sb.appendLine("</footer>")
        sb.appendLine("</body>")
        sb.appendLine("</html>")
    }
    
    /**
     * 获取 CSS 样式
     */
    private fun getCss(): String {
        return """
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', sans-serif;
            background: #f5f5f5;
            color: #333;
            line-height: 1.6;
        }
        .container {
            max-width: 900px;
            margin: 0 auto;
            padding: 20px;
            background: white;
            min-height: 100vh;
        }
        .export-info {
            background: #f8f9fa;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
        }
        .export-info h1 {
            margin-bottom: 15px;
            color: #2c3e50;
        }
        .export-info p {
            margin: 5px 0;
        }
        .conversation {
            margin-bottom: 40px;
        }
        .conversation-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            border-radius: 8px 8px 0 0;
        }
        .conversation-header h2 {
            margin-bottom: 10px;
        }
        .metadata {
            display: flex;
            flex-wrap: wrap;
            gap: 15px;
            font-size: 14px;
            opacity: 0.9;
        }
        .messages {
            background: #fafafa;
            padding: 20px;
            border-radius: 0 0 8px 8px;
        }
        .message {
            background: white;
            margin-bottom: 15px;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .message.user {
            border-left: 4px solid #667eea;
        }
        .message.assistant {
            border-left: 4px solid #764ba2;
        }
        .message-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
            padding-bottom: 8px;
            border-bottom: 1px solid #eee;
        }
        .role {
            font-weight: 600;
            font-size: 16px;
        }
        .model {
            font-size: 12px;
            color: #666;
            background: #f0f0f0;
            padding: 3px 8px;
            border-radius: 4px;
        }
        .message-content {
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        .conversation-divider {
            border: none;
            border-top: 2px dashed #ddd;
            margin: 40px 0;
        }
        hr {
            border: none;
            border-top: 1px solid #eee;
            margin: 20px 0;
        }
        footer {
            text-align: center;
            padding: 20px;
            color: #666;
            font-size: 14px;
        }
        code {
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: 'Courier New', monospace;
        }
        pre {
            background: #f4f4f4;
            padding: 10px;
            border-radius: 5px;
            overflow-x: auto;
            margin: 10px 0;
        }
        """.trimIndent()
    }
    
    /**
     * 转义 HTML 特殊字符
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * 格式化内容（保留换行，转义HTML）
     */
    private fun formatContent(content: String): String {
        // 简单的代码块检测和格式化
        val lines = content.lines()
        val sb = StringBuilder()
        var inCodeBlock = false
        
        for (line in lines) {
            when {
                line.trim().startsWith("```") -> {
                    if (inCodeBlock) {
                        sb.append("</code></pre>")
                        inCodeBlock = false
                    } else {
                        sb.append("<pre><code>")
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    sb.append(escapeHtml(line)).append("\n")
                }
                else -> {
                    sb.append(escapeHtml(line)).append("<br>")
                }
            }
        }
        
        if (inCodeBlock) {
            sb.append("</code></pre>")
        }
        
        return sb.toString()
    }
}
