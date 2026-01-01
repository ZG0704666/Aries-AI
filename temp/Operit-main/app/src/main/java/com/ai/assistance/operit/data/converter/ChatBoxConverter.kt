package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * ChatBox 格式转换器
 * 支持 ChatBox 桌面应用的导出格式
 * 
 * ChatBox 导出格式参考：
 * {
 *   "sessions": [
 *     {
 *       "id": "session-id",
 *       "name": "会话名称",
 *       "messages": [
 *         {
 *           "id": "msg-id",
 *           "role": "user" | "assistant",
 *           "content": "消息内容",
 *           "createdAt": 1234567890
 *         }
 *       ],
 *       "createdAt": 1234567890,
 *       "model": "gpt-4"
 *     }
 *   ]
 * }
 */
class ChatBoxConverter : ChatFormatConverter {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override fun convert(content: String): List<ChatHistory> {
        return try {
            val rootElement = json.parseToJsonElement(content)
            
            when (rootElement) {
                is JsonObject -> parseObjectFormat(rootElement)
                is JsonArray -> parseArrayFormat(rootElement)
                else -> throw ConversionException("不支持的 ChatBox 格式")
            }
        } catch (e: Exception) {
            throw ConversionException("解析 ChatBox 格式失败: ${e.message}", e)
        }
    }
    
    override fun getSupportedFormat(): ChatFormat = ChatFormat.CHATBOX
    
    /**
     * 解析对象格式
     */
    private fun parseObjectFormat(obj: JsonObject): List<ChatHistory> {
        return when {
            // 包含 sessions 数组
            obj.containsKey("sessions") -> {
                val sessions = obj["sessions"]
                if (sessions is JsonArray) {
                    sessions.mapNotNull { 
                        if (it is JsonObject) parseSession(it) else null 
                    }
                } else {
                    emptyList()
                }
            }
            // 单个 session 对象
            obj.containsKey("messages") || obj.containsKey("name") -> {
                val session = parseSession(obj)
                if (session != null) listOf(session) else emptyList()
            }
            else -> emptyList()
        }
    }
    
    /**
     * 解析数组格式（多个 session）
     */
    private fun parseArrayFormat(array: JsonArray): List<ChatHistory> {
        return array.mapNotNull { 
            if (it is JsonObject) parseSession(it) else null 
        }
    }
    
    /**
     * 解析单个 session
     */
    private fun parseSession(sessionObj: JsonObject): ChatHistory? {
        try {
            // 基准时间戳
            val baseTimestamp = System.currentTimeMillis()
            
            // 提取消息列表
            val messagesElement = sessionObj["messages"] ?: return null
            if (messagesElement !is JsonArray || messagesElement.isEmpty()) {
                return null
            }
            
            val messages = messagesElement.mapIndexedNotNull { index, element ->
                if (element is JsonObject) {
                    parseMessage(element, baseTimestamp, index)
                } else {
                    null
                }
            }
            
            if (messages.isEmpty()) {
                return null
            }
            
            // 提取会话信息
            val title = sessionObj["name"]?.jsonPrimitive?.contentOrNull
                ?: sessionObj["title"]?.jsonPrimitive?.contentOrNull
                ?: "Imported from ChatBox"
            
            val id = sessionObj["id"]?.jsonPrimitive?.contentOrNull
                ?: UUID.randomUUID().toString()
            
            // 提取创建时间
            val createdAt = sessionObj["createdAt"]?.jsonPrimitive?.longOrNull?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: LocalDateTime.now()
            
            val updatedAt = sessionObj["updatedAt"]?.jsonPrimitive?.longOrNull?.let {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it),
                    ZoneId.systemDefault()
                )
            } ?: createdAt
            
            return ChatHistory(
                id = id,
                title = title,
                messages = messages,
                createdAt = createdAt,
                updatedAt = updatedAt,
                group = "从 ChatBox 导入"
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 解析单条消息
     */
    private fun parseMessage(msgObj: JsonObject, baseTimestamp: Long, index: Int): ChatMessage? {
        try {
            val role = msgObj["role"]?.jsonPrimitive?.contentOrNull ?: return null
            val content = msgObj["content"]?.jsonPrimitive?.contentOrNull ?: return null
            
            if (content.isBlank()) return null
            
            // 规范化角色
            val sender = normalizeRole(role)
            
            // 提取时间戳（如果有的话，否则使用递增时间戳）
            val timestamp = msgObj["createdAt"]?.jsonPrimitive?.longOrNull
                ?: msgObj["timestamp"]?.jsonPrimitive?.longOrNull
                ?: (baseTimestamp + (index * 100L))
            
            // 提取模型信息
            val modelName = msgObj["model"]?.jsonPrimitive?.contentOrNull
                ?: msgObj["modelName"]?.jsonPrimitive?.contentOrNull
                ?: "chatbox"
            
            val provider = msgObj["provider"]?.jsonPrimitive?.contentOrNull
                ?: "ChatBox"
            
            return ChatMessage(
                sender = sender,
                content = content,
                timestamp = timestamp,
                provider = provider,
                modelName = modelName
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 规范化角色名称
     */
    private fun normalizeRole(role: String): String {
        return when (role.lowercase()) {
            "user", "human", "用户" -> "user"
            "assistant", "ai", "bot", "model", "助手" -> "ai"
            "system", "系统" -> "user"
            else -> "user"
        }
    }
}
