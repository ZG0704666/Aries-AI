package com.ai.assistance.operit.data.converter

import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.util.UUID

/**
 * 通用 JSON 格式转换器
 * 支持标准的 role-content 格式
 */
class GenericJsonConverter : ChatFormatConverter {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override fun convert(content: String): List<ChatHistory> {
        return try {
            val element = json.parseToJsonElement(content)
            
            when (element) {
                is JsonArray -> parseArrayFormat(element)
                is JsonObject -> parseObjectFormat(element)
                else -> throw ConversionException("不支持的 JSON 格式")
            }
        } catch (e: Exception) {
            throw ConversionException("解析通用 JSON 格式失败: ${e.message}", e)
        }
    }
    
    override fun getSupportedFormat(): ChatFormat = ChatFormat.GENERIC_JSON
    
    /**
     * 解析数组格式
     * 可能是对话数组或消息数组
     */
    private fun parseArrayFormat(array: JsonArray): List<ChatHistory> {
        if (array.isEmpty()) {
            return emptyList()
        }
        
        val firstElement = array.first()
        
        return if (firstElement is JsonObject) {
            // 判断是对话数组还是消息数组
            if (isConversationObject(firstElement)) {
                // 对话数组
                array.mapNotNull { 
                    if (it is JsonObject) parseConversationObject(it) else null 
                }
            } else if (isMessageObject(firstElement)) {
                // 消息数组 - 创建单个对话
                val baseTimestamp = System.currentTimeMillis()
                val messages = array.mapIndexedNotNull { index, element ->
                    if (element is JsonObject) parseMessageObject(element, baseTimestamp, index) else null 
                }
                if (messages.isNotEmpty()) {
                    listOf(ChatHistory(
                        id = UUID.randomUUID().toString(),
                        title = "Imported Conversation",
                        messages = messages,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        group = "从通用格式导入"
                    ))
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * 解析对象格式
     */
    private fun parseObjectFormat(obj: JsonObject): List<ChatHistory> {
        // 可能是单个对话对象，或包含 conversations 字段的对象
        if (obj.containsKey("conversations")) {
            val conversations = obj["conversations"]
            if (conversations is JsonArray) {
                return parseArrayFormat(conversations)
            }
        }
        
        // 单个对话对象
        val history = parseConversationObject(obj)
        return if (history != null) listOf(history) else emptyList()
    }
    
    /**
     * 判断是否为对话对象
     */
    private fun isConversationObject(obj: JsonObject): Boolean {
        return obj.containsKey("messages") || 
               obj.containsKey("title") ||
               (obj.containsKey("role") && obj.containsKey("content"))
    }
    
    /**
     * 判断是否为消息对象
     */
    private fun isMessageObject(obj: JsonObject): Boolean {
        return obj.containsKey("role") && obj.containsKey("content")
    }
    
    /**
     * 解析对话对象
     */
    private fun parseConversationObject(obj: JsonObject): ChatHistory? {
        try {
            // 基准时间戳
            val baseTimestamp = System.currentTimeMillis()
            
            // 提取消息列表
            val messages = when {
                obj.containsKey("messages") -> {
                    val messagesElement = obj["messages"]
                    if (messagesElement is JsonArray) {
                        messagesElement.mapIndexedNotNull { index, element ->
                            if (element is JsonObject) parseMessageObject(element, baseTimestamp, index) else null 
                        }
                    } else {
                        emptyList()
                    }
                }
                // 如果对象本身就是一条消息
                obj.containsKey("role") && obj.containsKey("content") -> {
                    val msg = parseMessageObject(obj, baseTimestamp, 0)
                    if (msg != null) listOf(msg) else emptyList()
                }
                else -> emptyList()
            }
            
            if (messages.isEmpty()) {
                return null
            }
            
            // 提取其他字段
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Imported Conversation"
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
            
            return ChatHistory(
                id = id,
                title = title,
                messages = messages,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                group = "从通用格式导入"
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * 解析消息对象
     */
    private fun parseMessageObject(obj: JsonObject, baseTimestamp: Long, index: Int): ChatMessage? {
        try {
            val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return null
            val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return null
            
            if (content.isBlank()) return null
            
            val sender = normalizeRole(role)
            // 如果有时间戳就用，否则使用递增的时间戳（间隔 100ms）
            val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull 
                ?: (baseTimestamp + (index * 100L))
            
            val modelName = obj["model"]?.jsonPrimitive?.contentOrNull
                ?: obj["modelName"]?.jsonPrimitive?.contentOrNull
                ?: "unknown"
            
            val provider = obj["provider"]?.jsonPrimitive?.contentOrNull ?: "imported"
            
            return ChatMessage(
                sender = sender,
                content = content,
                timestamp = timestamp,
                modelName = modelName,
                provider = provider
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
            "system", "系统" -> "user" // 系统消息转为用户消息
            else -> "user"
        }
    }
}
