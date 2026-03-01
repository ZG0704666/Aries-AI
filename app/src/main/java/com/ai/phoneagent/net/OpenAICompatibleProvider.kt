package com.ai.phoneagent.net

import android.util.Base64
import com.ai.phoneagent.data.AttachmentInfo
import com.ai.phoneagent.helper.ChatMarkupRegex
import com.ai.phoneagent.helper.MediaLinkParser
import com.ai.phoneagent.helper.StreamingJsonXmlConverter
import com.ai.phoneagent.helper.XmlEscaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File

/**
 * OpenAI 兼容的 Provider 实现
 * 
 * 支持 OpenAI API 以及所有兼容的第三方 API（如 DeepSeek、Moonshot 等）
 */
class OpenAICompatibleProvider(
    private val apiKey: String,
    private val baseUrl: String,
    override val modelName: String,
    override val supportsVision: Boolean = false,
    override val supportsAudio: Boolean = false,
    override val supportsVideo: Boolean = false,
    override val enableToolCall: Boolean = false
) : AIProvider {
    
    override val providerName: String = "OpenAI"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val JSON = "application/json".toMediaType()
    
    override suspend fun sendMessageStream(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildRequestBody(messages)
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("API request failed: ${response.code} - $errorBody")
            }
            
            val reader = response.body?.charStream()?.buffered()
            reader?.use { processStreamingResponse(it, onChunk) }
            
            onComplete()
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    /**
     * 构建请求体
     */
    private fun buildRequestBody(messages: List<ChatMessage>): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true)
        
        val messagesArray = JSONArray()
        messages.forEach { message ->
            val messageObj = JSONObject()
            messageObj.put("role", message.role)
            
            // 处理附件
            if (message.attachments.isNotEmpty()) {
                val content = buildContentWithAttachments(message.content, message.attachments)
                messageObj.put("content", content)
            } else {
                messageObj.put("content", message.content)
            }
            
            messagesArray.put(messageObj)
        }
        
        jsonObject.put("messages", messagesArray)
        
        return jsonObject.toString().toRequestBody(JSON)
    }
    
    /**
     * 处理流式响应
     */
    private suspend fun processStreamingResponse(
        reader: BufferedReader,
        onChunk: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var line: String?
        val converter = StreamingJsonXmlConverter()
        var isInToolCall = false
        
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            
            if (!currentLine.startsWith("data: ")) continue
            
            val data = currentLine.substring(6).trim()
            if (data == "[DONE]") break
            if (data.isBlank()) continue
            
            try {
                val jsonResponse = JSONObject(data)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) continue
                
                val choice = choices.getJSONObject(0)
                val delta = choice.optJSONObject("delta") ?: continue
                
                // 处理Tool Call
                val toolCalls = delta.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0 && enableToolCall) {
                    processToolCallsDelta(toolCalls, converter, onChunk)
                    isInToolCall = true
                    continue
                }
                
                // 处理普通内容
                val content = delta.optString("content", "")
                if (content.isNotEmpty()) {
                    if (isInToolCall) {
                        // 关闭Tool Call
                        val events = converter.flush()
                        events.forEach { event ->
                            when (event) {
                                is StreamingJsonXmlConverter.Event.Tag -> onChunk(event.text)
                                is StreamingJsonXmlConverter.Event.Content -> onChunk(event.text)
                            }
                        }
                        onChunk("\n</tool>\n")
                        isInToolCall = false
                    }
                    onChunk(content)
                }
            } catch (e: Exception) {
                // 忽略解析错误
            }
        }
        
        // 确保Tool Call被关闭
        if (isInToolCall) {
            val events = converter.flush()
            events.forEach { event ->
                when (event) {
                    is StreamingJsonXmlConverter.Event.Tag -> onChunk(event.text)
                    is StreamingJsonXmlConverter.Event.Content -> onChunk(event.text)
                }
            }
            onChunk("\n</tool>\n")
        }
    }
    
    /**
     * 处理Tool Call增量数据
     */
    private fun processToolCallsDelta(
        toolCalls: JSONArray,
        converter: StreamingJsonXmlConverter,
        onChunk: (String) -> Unit
    ) {
        for (i in 0 until toolCalls.length()) {
            val toolCall = toolCalls.getJSONObject(i)
            val function = toolCall.optJSONObject("function") ?: continue
            
            val name = function.optString("name", "")
            if (name.isNotEmpty()) {
                onChunk("\n<tool name=\"$name\">")
            }
            
            val arguments = function.optString("arguments", "")
            if (arguments.isNotEmpty()) {
                val events = converter.feed(arguments)
                events.forEach { event ->
                    when (event) {
                        is StreamingJsonXmlConverter.Event.Tag -> onChunk(event.text)
                        is StreamingJsonXmlConverter.Event.Content -> onChunk(event.text)
                    }
                }
            }
        }
    }
    
    override fun buildContentWithAttachments(
        text: String,
        attachments: List<AttachmentInfo>
    ): Any {
        // 检查是否有多媒体内容
        val hasImages = attachments.any { it.mimeType.startsWith("image/") }
        val hasMedia = attachments.any { 
            it.mimeType.startsWith("audio/") || it.mimeType.startsWith("video/")
        }
        
        if (!hasImages && !hasMedia) {
            // 纯文本，将附件信息添加到文本中
            val textWithAttachments = buildString {
                append(text)
                attachments.forEach { attachment ->
                    append("\n\n")
                    if (attachment.content.isNotEmpty()) {
                        append("[附件: ${attachment.fileName}]\n")
                        append(attachment.content)
                    } else {
                        append("[附件: ${attachment.fileName}, 类型: ${attachment.mimeType}, 大小: ${attachment.fileSize}]")
                    }
                }
            }
            return textWithAttachments
        }
        
        // 构建多模态content数组
        val contentArray = JSONArray()
        
        // 添加图片
        if (supportsVision) {
            attachments.filter { it.mimeType.startsWith("image/") }.forEach { attachment ->
                try {
                    val base64 = readFileAsBase64(attachment.filePath)
                    if (base64 != null) {
                        contentArray.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:${attachment.mimeType};base64,$base64")
                            })
                        })
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }
        
        // 添加音频
        if (supportsAudio) {
            attachments.filter { it.mimeType.startsWith("audio/") }.forEach { attachment ->
                try {
                    val base64 = readFileAsBase64(attachment.filePath)
                    if (base64 != null) {
                        contentArray.put(JSONObject().apply {
                            put("type", "input_audio")
                            put("input_audio", JSONObject().apply {
                                put("data", base64)
                                put("format", getAudioFormat(attachment.mimeType))
                            })
                        })
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }
        
        // 添加文本
        val textWithInlineAttachments = buildString {
            append(text)
            attachments.filter { it.content.isNotEmpty() }.forEach { attachment ->
                append("\n\n[附件: ${attachment.fileName}]\n")
                append(attachment.content)
            }
        }
        
        if (textWithInlineAttachments.isNotEmpty()) {
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", textWithInlineAttachments)
            })
        }
        
        return contentArray
    }
    
    override fun parseXmlToolCalls(content: String): Pair<String, Any?> {
        if (!enableToolCall) return Pair(content, null)
        
        val matches = ChatMarkupRegex.toolCallPattern.findAll(content)
        if (!matches.any()) return Pair(content, null)
        
        val toolCalls = JSONArray()
        var textContent = content
        var callIndex = 0
        
        matches.forEach { match ->
            val toolName = match.groupValues[1]
            val toolBody = match.groupValues[2]
            
            // 解析参数
            val params = JSONObject()
            ChatMarkupRegex.toolParamPattern.findAll(toolBody).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
                params.put(paramName, paramValue)
            }
            
            // 构建OpenAI格式的tool_call
            toolCalls.put(JSONObject().apply {
                put("id", "call_${toolName}_${callIndex}")
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", params.toString())
                })
            })
            
            callIndex++
            textContent = textContent.replace(match.value, "")
        }
        
        return Pair(textContent.trim(), toolCalls)
    }
    
    override fun parseXmlToolResults(content: String): Pair<String, Any?> {
        if (!enableToolCall) return Pair(content, null)
        
        val matches = ChatMarkupRegex.toolResultAnyPattern.findAll(content)
        if (!matches.any()) return Pair(content, null)
        
        val results = mutableListOf<Pair<String, String>>()
        var textContent = content
        var resultIndex = 0
        
        matches.forEach { match ->
            val fullContent = match.groupValues[1].trim()
            val contentMatch = ChatMarkupRegex.contentTag.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }
            
            results.add(Pair("call_result_$resultIndex", resultContent))
            textContent = textContent.replace(match.value, "").trim()
            resultIndex++
        }
        
        return Pair(textContent, results)
    }
    
    /**
     * 读取文件为Base64
     */
    private fun readFileAsBase64(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取音频格式
     */
    private fun getAudioFormat(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/ogg" -> "ogg"
            "audio/webm" -> "webm"
            else -> mimeType.substringAfter("/", "wav")
        }
    }
}
