package com.ai.assistance.operit.data.exporter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.format.DateTimeFormatter

/**
 * 纯文本格式导出器
 */
object TextExporter {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 导出单个对话为纯文本
     */
    fun exportSingle(chatHistory: ChatHistory): String {
        val sb = StringBuilder()
        
        // 标题
        sb.appendLine("=" .repeat(60))
        sb.appendLine(chatHistory.title.center(60))
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        
        // 元信息
        sb.appendLine("创建时间: ${chatHistory.createdAt.format(dateFormatter)}")
        sb.appendLine("更新时间: ${chatHistory.updatedAt.format(dateFormatter)}")
        if (chatHistory.group != null) {
            sb.appendLine("分组: ${chatHistory.group}")
        }
        sb.appendLine("消息数: ${chatHistory.messages.size}")
        sb.appendLine()
        sb.appendLine("-".repeat(60))
        sb.appendLine()
        
        // 消息内容
        for ((index, message) in chatHistory.messages.withIndex()) {
            if (index > 0) {
                sb.appendLine()
            }
            appendMessage(sb, message)
        }
        
        sb.appendLine()
        sb.appendLine("=".repeat(60))
        
        return sb.toString()
    }
    
    /**
     * 导出多个对话为纯文本
     */
    fun exportMultiple(chatHistories: List<ChatHistory>): String {
        val sb = StringBuilder()
        
        // 总览信息
        sb.appendLine("=" .repeat(60))
        sb.appendLine("聊天记录导出".center(60))
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine("导出时间: ${java.time.LocalDateTime.now().format(dateFormatter)}")
        sb.appendLine("对话数量: ${chatHistories.size}")
        sb.appendLine("总消息数: ${chatHistories.sumOf { it.messages.size }}")
        sb.appendLine()
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        sb.appendLine()
        
        for ((index, chatHistory) in chatHistories.withIndex()) {
            if (index > 0) {
                sb.appendLine()
                sb.appendLine()
            }
            
            sb.append(exportSingle(chatHistory))
        }
        
        sb.appendLine()
        sb.appendLine()
        sb.appendLine("导出完成 - Operit AI Assistant")
        
        return sb.toString()
    }
    
    /**
     * 添加单条消息
     */
    private fun appendMessage(sb: StringBuilder, message: ChatMessage) {
        val roleIcon = if (message.sender == "user") "👤" else "🤖"
        val roleText = if (message.sender == "user") "用户" else "助手"
        
        sb.appendLine("[$roleIcon $roleText]")
        
        if (message.modelName.isNotEmpty() && message.modelName != "markdown" && message.modelName != "unknown") {
            sb.appendLine("(模型: ${message.modelName})")
        }
        
        sb.appendLine()
        sb.appendLine(message.content)
        sb.appendLine()
        sb.appendLine("-".repeat(60))
    }
    
    /**
     * 字符串居中扩展函数
     */
    private fun String.center(width: Int): String {
        if (this.length >= width) return this
        val padding = width - this.length
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return " ".repeat(leftPad) + this + " ".repeat(rightPad)
    }
}
