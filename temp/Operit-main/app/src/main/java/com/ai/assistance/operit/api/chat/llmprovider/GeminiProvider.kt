package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.StreamingJsonXmlConverter
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.stream
import android.net.Uri
import android.os.Environment
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser

/** Google Gemini API的实现 支持标准Gemini接口流式传输 */
class GeminiProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.GOOGLE,
    private val enableGoogleSearch: Boolean = false,
    private val enableToolCall: Boolean = false // 是否启用Tool Call接口（预留，Gemini有原生tool支持）
) : AIService {
    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEBUG = true // 开启调试日志
    }

    // HTTP客户端
    // private val client: OkHttpClient = HttpClientFactory.instance

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 活跃请求，用于取消流式请求
    private var activeCall: Call? = null
    private var activeResponse: Response? = null
    @Volatile private var isManuallyCancelled = false

    /**
     * 不可重试的异常，通常由客户端错误（如4xx状态码）引起
     */
    class NonRetriableException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // Token计数
    private val tokenCacheManager = TokenCacheManager()
    
    // 思考状态跟踪
    private var isInThinkingMode = false

    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    // 取消当前流式传输
    override fun cancelStreaming() {
        isManuallyCancelled = true

        // 1. 强制关闭 Response（这会立即中断流读取操作）
        activeResponse?.let {
            try {
                it.close()
                AppLogger.d(TAG, "已强制关闭Response流")
            } catch (e: Exception) {
                AppLogger.w(TAG, "关闭Response时出错: ${e.message}")
            }
        }
        activeResponse = null

        // 2. 取消 Call
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                AppLogger.d(TAG, "已取消当前流式传输，Call已中断")
            }
        }
        activeCall = null

        AppLogger.d(TAG, "取消标志已设置，流读取将立即被中断")
    }

    // 重置Token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
        isInThinkingMode = false
    }

    override suspend fun calculateInputTokens(
            message: String,
            chatHistory: List<Pair<String, String>>,
            availableTools: List<ToolPrompt>?
    ): Int {
        // 构建工具定义的JSON字符串
        val toolsJson = buildToolsJson(availableTools)
        return tokenCacheManager.calculateInputTokens(message, chatHistory, toolsJson)
    }
    
    /**
     * 构建工具定义的JSON字符串，用于token计算
     */
    private fun buildToolsJson(availableTools: List<ToolPrompt>?): String? {
        if (!enableToolCall || availableTools == null || availableTools.isEmpty()) {
            return if (enableGoogleSearch) {
                // 只有 Google Search
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }.toString()
            } else {
                null
            }
        }
        
        val tools = JSONArray()
        
        // 添加 Function Calling 工具
        val functionDeclarations = buildToolDefinitionsForGemini(availableTools)
        if (functionDeclarations.length() > 0) {
            tools.put(JSONObject().apply {
                put("function_declarations", functionDeclarations)
            })
        }
        
        // 添加 Google Search grounding 工具（如果启用）
        if (enableGoogleSearch) {
            tools.put(JSONObject().apply {
                put("googleSearch", JSONObject())
            })
        }
        
        return if (tools.length() > 0) tools.toString() else null
    }

    // ==================== Tool Call 支持 ====================
    
    /**
     * XML转义/反转义工具
     */
    private object XmlEscaper {
        fun escape(text: String): String {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")
        }
        
        fun unescape(text: String): String {
            return text.replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&amp;", "&")
        }
    }
    
    /**
     * 解析XML格式的tool调用，转换为Gemini FunctionCall格式
     * @return Pair<文本内容, functionCall对象>
     */
    private fun parseXmlToolCalls(content: String): Pair<String, JSONObject?> {
        if (!enableToolCall) return Pair(content, null)
        
        val toolPattern = Regex("<tool\\s+name=\"([^\"]+)\">([\\s\\S]*?)</tool>", RegexOption.MULTILINE)
        val match = toolPattern.find(content) // Gemini 一次只能调用一个工具
        
        if (match == null) {
            return Pair(content, null)
        }
        
        val toolName = match.groupValues[1]
        val toolBody = match.groupValues[2]
        
        // 解析参数
        val paramPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
        val args = JSONObject()
        
        paramPattern.findAll(toolBody).forEach { paramMatch ->
            val paramName = paramMatch.groupValues[1]
            val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
            args.put(paramName, paramValue)
        }
        
        // 构建functionCall对象（Gemini格式）
        val functionCall = JSONObject().apply {
            put("name", toolName)
            put("args", args)
        }
        
        AppLogger.d(TAG, "XML→GeminiFunctionCall: $toolName")
        
        // 从文本内容中移除tool标签
        val textContent = content.replace(match.value, "").trim()
        
        return Pair(textContent, functionCall)
    }
    
    /**
     * 解析XML格式的tool_result，转换为Gemini FunctionResponse格式
     * @return Pair<文本内容, functionResponse对象列表>
     */
    private fun parseXmlToolResults(content: String): Pair<String, List<JSONObject>?> {
        if (!enableToolCall) return Pair(content, null)
        
        val resultPattern = Regex("<tool_result[^>]*name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</tool_result>", RegexOption.MULTILINE)
        val matches = resultPattern.findAll(content)
        
        if (!matches.any()) {
            return Pair(content, null)
        }
        
        val functionResponses = mutableListOf<JSONObject>()
        var textContent = content
        
        matches.forEach { match ->
            val toolName = match.groupValues[1]
            val fullContent = match.groupValues[2].trim()
            val contentPattern = Regex("<content>([\\s\\S]*?)</content>", RegexOption.MULTILINE)
            val contentMatch = contentPattern.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }
            
            // 构建functionResponse对象（Gemini格式）
            val functionResponse = JSONObject().apply {
                put("name", toolName)
                put("response", JSONObject().apply {
                    put("result", resultContent)
                })
            }
            
            functionResponses.add(functionResponse)
            AppLogger.d(TAG, "解析Gemini functionResponse: $toolName, content length=${resultContent.length}")
            
            textContent = textContent.replace(match.value, "").trim()
        }
        
        return Pair(textContent, functionResponses)
    }
    
    /**
     * 从ToolPrompt列表构建Gemini格式的Function Declarations
     */
    private fun buildToolDefinitionsForGemini(toolPrompts: List<ToolPrompt>): JSONArray {
        val functionDeclarations = JSONArray()
        
        for (tool in toolPrompts) {
            functionDeclarations.put(JSONObject().apply {
                put("name", tool.name)
                // 组合description和details作为完整描述
                val fullDescription = if (tool.details.isNotEmpty()) {
                    "${tool.description}\n${tool.details}"
                } else {
                    tool.description
                }
                put("description", fullDescription)
                
                // 使用结构化参数构建schema
                val parametersSchema = buildSchemaFromStructured(tool.parametersStructured ?: emptyList())
                put("parameters", parametersSchema)
            })
        }
        
        return functionDeclarations
    }
    
    /**
     * 从结构化参数构建JSON Schema（Gemini格式）
     */
    private fun buildSchemaFromStructured(params: List<com.ai.assistance.operit.data.model.ToolParameterSchema>): JSONObject {
        val schema = JSONObject().apply {
            put("type", "object")
        }
        
        val properties = JSONObject()
        val required = JSONArray()
        
        for (param in params) {
            properties.put(param.name, JSONObject().apply {
                put("type", param.type)
                put("description", param.description)
                if (param.default != null) {
                    put("default", param.default)
                }
            })
            
            if (param.required) {
                required.put(param.name)
            }
        }
        
        schema.put("properties", properties)
        if (required.length() > 0) {
            schema.put("required", required)
        }
        
        return schema
    }
    
    /**
     * 构建包含文本和图片的parts数组
     */
    private fun buildPartsArray(text: String): JSONArray {
        val partsArray = JSONArray()

        val hasImages = ImageLinkParser.hasImageLinks(text)
        val hasMedia = MediaLinkParser.hasMediaLinks(text)

        if (hasImages || hasMedia) {
            val imageLinks = if (hasImages) ImageLinkParser.extractImageLinks(text) else emptyList()
            val mediaLinks = if (hasMedia) MediaLinkParser.extractMediaLinks(text) else emptyList()

            var textWithoutLinks = text
            if (hasImages) {
                textWithoutLinks = ImageLinkParser.removeImageLinks(textWithoutLinks)
            }
            if (hasMedia) {
                textWithoutLinks = MediaLinkParser.removeMediaLinks(textWithoutLinks)
            }
            textWithoutLinks = textWithoutLinks.trim()

            // 添加媒体（音频/视频）
            mediaLinks.forEach { link ->
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }

            // 添加图片
            imageLinks.forEach { link ->
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }

            // 添加文本（如果有）
            if (textWithoutLinks.isNotEmpty()) {
                partsArray.put(JSONObject().apply {
                    put("text", textWithoutLinks)
                })
            }
        } else {
            // 纯文本消息
            partsArray.put(JSONObject().apply {
                put("text", text)
            })
        }
        
        return partsArray
    }

    private fun buildContentsAndCountTokens(
            message: String,
            chatHistory: List<Pair<String, String>>,
            toolsJson: String? = null,
            preserveThinkInHistory: Boolean = false
    ): Pair<Pair<JSONArray, JSONObject?>, Int> {
        val contentsArray = JSONArray()
        var systemInstruction: JSONObject? = null

        // 使用TokenCacheManager计算token数量
        val tokenCount = tokenCacheManager.calculateInputTokens(message, chatHistory, toolsJson)

        // 检查当前消息是否已经在历史记录的末尾（避免重复）
        val isMessageInHistory = chatHistory.isNotEmpty() && chatHistory.last().second == message
        
        // 如果消息已在历史中，只处理历史；否则需要处理历史+当前消息
        val effectiveHistory = if (isMessageInHistory) {
            chatHistory
        } else {
            chatHistory + ("user" to message)
        }

        val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(effectiveHistory, extractThinking = preserveThinkInHistory)

        // Find and process system message first
        val systemMessage = standardizedHistory.find { it.first == "system" }
        if (systemMessage != null) {
            val systemContent = systemMessage.second
            logDebug("发现系统消息: ${systemContent.take(50)}...")

            systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemContent) })
                })
            }
        }

        // Process the rest of the history
        val historyWithoutSystem = standardizedHistory.filter { it.first != "system" }
        val mergedHistory = mutableListOf<Pair<String, String>>()
        for ((role, content) in historyWithoutSystem) {
            if (mergedHistory.isNotEmpty() && mergedHistory.last().first == role) {
                val lastMessage = mergedHistory.last()
                mergedHistory[mergedHistory.size - 1] =
                    Pair(role, lastMessage.second + "\n" + content)
                logDebug("合并连续的 $role 消息")
            } else {
                mergedHistory.add(Pair(role, content))
            }
        }

        for ((role, content) in mergedHistory) {
            val geminiRole = if (role == "assistant") "model" else role
            
            // 当启用Tool Call API时，转换XML格式的工具调用
            if (enableToolCall) {
                if (role == "assistant") {
                    // 解析assistant消息中的XML tool calls
                    val (textContent, functionCall) = parseXmlToolCalls(content)
                    
                    val partsArray = JSONArray()
                    // 先添加文本内容
                    if (textContent.isNotEmpty()) {
                        val messageParts = buildPartsArray(textContent)
                        for (i in 0 until messageParts.length()) {
                            partsArray.put(messageParts.getJSONObject(i))
                        }
                    }
                    // 再添加functionCall
                    if (functionCall != null) {
                        partsArray.put(JSONObject().apply {
                            put("functionCall", functionCall)
                        })
                        logDebug("历史XML→GeminiFunctionCall: ${functionCall.optString("name")}")
                    }
                    
                    val contentObject = JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", partsArray)
                    }
                    contentsArray.put(contentObject)
                } else if (role == "user") {
                    // 解析user消息中的XML tool_result
                    val (textContent, functionResponses) = parseXmlToolResults(content)
                    
                    val partsArray = JSONArray()
                    // 先添加所有functionResponse
                    if (functionResponses != null && functionResponses.isNotEmpty()) {
                        functionResponses.forEach { functionResponse ->
                            partsArray.put(JSONObject().apply {
                                put("functionResponse", functionResponse)
                            })
                            logDebug("历史XML→GeminiFunctionResponse: ${functionResponse.optString("name")}")
                        }
                    }
                    // 再添加文本内容
                    if (textContent.isNotEmpty()) {
                        val messageParts = buildPartsArray(textContent)
                        for (i in 0 until messageParts.length()) {
                            partsArray.put(messageParts.getJSONObject(i))
                        }
                    }
                    
                    // 如果没有任何内容，保留原始content
                    if (partsArray.length() == 0) {
                        partsArray.put(JSONObject().apply {
                            put("text", content)
                        })
                    }
                    
                    val contentObject = JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", partsArray)
                    }
                    contentsArray.put(contentObject)
                } else {
                    // system等其他角色正常处理
                    val contentObject = JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", buildPartsArray(content))
                    }
                    contentsArray.put(contentObject)
                }
            } else {
                // 不启用Tool Call API时，保持原样
                val contentObject = JSONObject().apply {
                    put("role", geminiRole)
                    put("parts", buildPartsArray(content))
                }
                contentsArray.put(contentObject)
            }
        }

        return Pair(Pair(contentsArray, systemInstruction), tokenCount)
    }

    // 工具函数：分块打印大型文本日志
    private fun logLargeString(tag: String, message: String, prefix: String = "") {
        // 设置单次日志输出的最大长度（Android日志上限约为4000字符）
        val maxLogSize = 3000

        // 如果消息长度超过限制，分块打印
        if (message.length > maxLogSize) {
            // 计算需要分多少块打印
            val chunkCount = message.length / maxLogSize + 1

            for (i in 0 until chunkCount) {
                val start = i * maxLogSize
                val end = minOf((i + 1) * maxLogSize, message.length)
                val chunkMessage = message.substring(start, end)

                // 打印带有编号的日志
                AppLogger.d(tag, "$prefix Part ${i+1}/$chunkCount: $chunkMessage")
            }
        } else {
            // 消息长度在限制之内，直接打印
            AppLogger.d(tag, "$prefix$message")
        }
    }

    private fun sanitizeImageDataForLogging(json: JSONObject): JSONObject {
        fun sanitizeObject(obj: JSONObject) {
            fun sanitizeArray(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val value = arr.get(i)
                    when (value) {
                        is JSONObject -> sanitizeObject(value)
                        is JSONArray -> sanitizeArray(value)
                        is String -> {
                            if (value.startsWith("data:") && value.contains(";base64,")) {
                                arr.put(i, "[image base64 omitted, length=${value.length}]")
                            }
                        }
                    }
                }
            }

            val maybeMimeType = obj.optString("mime_type", obj.optString("mimeType", ""))
            if (maybeMimeType.startsWith("image/", ignoreCase = true) && obj.has("data")) {
                val dataValue = obj.opt("data")
                if (dataValue is String) {
                    obj.put("data", "[image base64 omitted, length=${dataValue.length}]")
                }
            }

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.get(key)
                when (value) {
                    is JSONObject -> sanitizeObject(value)
                    is JSONArray -> sanitizeArray(value)
                    is String -> {
                        if (value.startsWith("data:") && value.contains(";base64,")) {
                            obj.put(key, "[image base64 omitted, length=${value.length}]")
                        }
                    }
                }
            }
        }

        sanitizeObject(json)
        return json
    }

     private fun getOutputImagesDir(): File {
         val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
         return File(downloadsDir, "Operit/output images")
     }

     private fun fileExtensionForImageMime(mimeType: String): String {
         return when (mimeType.lowercase().substringBefore(';')) {
             "image/png" -> "png"
             "image/jpeg", "image/jpg" -> "jpg"
             "image/webp" -> "webp"
             "image/gif" -> "gif"
             else -> "png"
         }
     }

     private fun writeOutputImage(bytes: ByteArray, mimeType: String, prefix: String): Uri? {
         return try {
             val dir = getOutputImagesDir()
             if (!dir.exists()) {
                 dir.mkdirs()
             }
             val ext = fileExtensionForImageMime(mimeType)
             val fileName = "${prefix}_${System.currentTimeMillis()}.$ext"
             val outFile = File(dir, fileName)
             FileOutputStream(outFile).use { it.write(bytes) }
             Uri.fromFile(outFile)
         } catch (e: Exception) {
             logError("保存输出图片失败", e)
             null
         }
     }

    // 日志辅助方法
    private fun logDebug(message: String) {
        if (DEBUG) {
            AppLogger.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            AppLogger.e(TAG, message, throwable)
        } else {
            AppLogger.e(TAG, message)
        }
    }

    /** 发送消息到Gemini API */
    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isManuallyCancelled = false
        val requestId = System.currentTimeMillis().toString()
        // 重置输出token计数（保留输入历史缓存）
        tokenCacheManager.addOutputTokens(-tokenCacheManager.outputTokenCount)
        isInThinkingMode = false
        
        onTokensUpdated(
                tokenCacheManager.totalInputTokenCount,
                tokenCacheManager.cachedInputTokenCount,
                tokenCacheManager.outputTokenCount
        )

        AppLogger.d(TAG, "发送消息到Gemini API, 模型: $modelName")

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()

        // 捕获stream collector的引用
        val streamCollector = this

        // 状态更新函数 - 在Stream中我们使用emit来传递连接状态
        val emitConnectionStatus: (String) -> Unit = { status ->
            // 这里可以根据需要处理连接状态，例如记录日志
            logDebug("连接状态: $status")
        }

        emitConnectionStatus("连接到Gemini服务...")

        while (retryCount < maxRetries) {
            // 在循环开始时检查是否已被取消
            if (isManuallyCancelled) {
                logError("请求被用户取消，停止重试。")
                throw UserCancellationException("请求已被用户取消")
            }
            
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    AppLogger.d(TAG, "【Gemini 重试】准备续写请求，已接收内容长度: ${receivedContent.length}")
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("model" to receivedContent.toString()) // Gemini uses 'model' role for assistant
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }

                val requestBody = createRequestBody(currentMessage, currentHistory, modelParameters, enableThinking, availableTools, preserveThinkInHistory)
                onTokensUpdated(
                        tokenCacheManager.totalInputTokenCount,
                        tokenCacheManager.cachedInputTokenCount,
                        tokenCacheManager.outputTokenCount
                )
                val request = createRequest(requestBody, stream, requestId) // 根据stream参数决定使用流式还是非流式

                val call = client.newCall(request)
                activeCall = call

                emitConnectionStatus("建立连接中...")

                val startTime = System.currentTimeMillis()
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = call.execute()
                    activeResponse = response
                    try {
                        val duration = System.currentTimeMillis() - startTime
                        AppLogger.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                        emitConnectionStatus("连接成功，处理响应...")

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "无错误详情"
                            logError("API请求失败: ${response.code}, $errorBody")
                            // 对于4xx这类明确的客户端错误，直接抛出，不进行重试
                            if (response.code in 400..499) {
                                throw NonRetriableException("API请求失败: ${response.code}, $errorBody")
                            }
                            // 对于5xx等服务端错误，允许重试
                            throw IOException("API请求失败: ${response.code}, $errorBody")
                        }

                        // 根据stream参数处理响应
                        if (stream) {
                            // 处理流式响应
                            processStreamingResponse(response, streamCollector, requestId, onTokensUpdated, receivedContent)
                        } else {
                            // 处理非流式响应并转换为Stream
                            processNonStreamingResponse(response, streamCollector, requestId, onTokensUpdated, receivedContent)
                        }
                    } finally {
                        response.close()
                        AppLogger.d(TAG, "关闭响应连接")
                    }
                }

                // 清理活跃引用
                activeCall = null
                activeResponse = null
                return@stream
            } catch (e: NonRetriableException) {
                logError("发生不可重试错误", e)
                throw e // 直接抛出，不重试
            } catch (e: SocketTimeoutException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                logError("连接超时，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("无法解析主机且达到最大重试次数", e)
                    throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                logError("无法解析主机，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                // 捕获所有其他IO异常，包括流读取中断
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("达到最大重试次数后仍然失败", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                logError("IO异常，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: Exception) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                logError("发送消息时发生未知异常，不进行重试", e)
                throw IOException("AI响应获取失败: ${e.message}", e)
            }
        }

        logError("重试${maxRetries}次后仍然失败", lastException)
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }

    /** 创建请求体 */
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            availableTools: List<ToolPrompt>? = null,
            preserveThinkInHistory: Boolean = false
    ): RequestBody {
        val json = JSONObject()

        // 添加工具定义
        val tools = JSONArray()
        
        // 添加 Function Calling 工具（如果启用且有可用工具）
        if (enableToolCall && availableTools != null && availableTools.isNotEmpty()) {
            val functionDeclarations = buildToolDefinitionsForGemini(availableTools)
            if (functionDeclarations.length() > 0) {
                tools.put(JSONObject().apply {
                    put("function_declarations", functionDeclarations)
                })
                logDebug("已添加 ${functionDeclarations.length()} 个 Function Declarations")
            }
        }
        
        // 添加 Google Search grounding 工具（如果启用）
        if (enableGoogleSearch) {
            tools.put(JSONObject().apply {
                put("googleSearch", JSONObject())
            })
            logDebug("已启用 Google Search Grounding")
        }
        
        // 将 tools 添加到请求中，并保存用于token计算
        val toolsJson = if (tools.length() > 0) {
            json.put("tools", tools)
            tools.toString()
        } else {
            null
        }

        val (contentsResult, _) = buildContentsAndCountTokens(message, chatHistory, toolsJson, preserveThinkInHistory)
        val (contentsArray, systemInstruction) = contentsResult

        if (systemInstruction != null) {
            json.put("systemInstruction", systemInstruction)
        }
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 如果启用了思考模式，则为Gemini模型添加特定的`thinkingConfig`参数
        if (enableThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("includeThoughts", true)
            generationConfig.put("thinkingConfig", thinkingConfig)
            logDebug("已为Gemini模型启用“思考模式”。")
        }

        // 添加模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            generationConfig.put(
                                    "temperature",
                                    (param.currentValue as Number).toFloat()
                            )
                    "top_p" ->
                            generationConfig.put("topP", (param.currentValue as Number).toFloat())
                    "top_k" -> generationConfig.put("topK", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            generationConfig.put(
                                    "maxOutputTokens",
                                    (param.currentValue as Number).toInt()
                            )
                    else -> {
                        when (param.valueType) {
                            com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                                val raw = param.currentValue.toString().trim()
                                val parsed: Any? = try {
                                    when {
                                        raw.startsWith("{") -> JSONObject(raw)
                                        raw.startsWith("[") -> JSONArray(raw)
                                        else -> null
                                    }
                                } catch (e: Exception) {
                                    logError("Gemini OBJECT参数解析失败: ${param.apiName}", e)
                                    null
                                }
                                if (param.category == ParameterCategory.OTHER) {
                                    if (parsed != null) {
                                        json.put(param.apiName, parsed)
                                    } else {
                                        json.put(param.apiName, raw)
                                    }
                                } else {
                                    if (parsed != null) {
                                        generationConfig.put(param.apiName, parsed)
                                    } else {
                                        generationConfig.put(param.apiName, raw)
                                    }
                                }
                            }
                            else -> generationConfig.put(param.apiName, param.currentValue)
                        }
                    }
                }
            }
        }

        json.put("generationConfig", generationConfig)

        val jsonString = json.toString()
        // 使用分块日志函数记录请求体（省略过长的tools字段）
        val logJson = JSONObject(jsonString)
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        sanitizeImageDataForLogging(logJson)
        logLargeString(TAG, logJson.toString(4), "请求体JSON: ")

        return jsonString.toRequestBody(JSON)
    }

    /** 创建HTTP请求 */
    private suspend fun createRequest(
            requestBody: RequestBody,
            isStreaming: Boolean,
            requestId: String
    ): Request {
        // 确定请求URL
        val baseUrl = determineBaseUrl(apiEndpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val requestUrl = "$baseUrl/v1beta/models/$modelName:$method"

        AppLogger.d(TAG, "请求URL: $requestUrl")

        // 创建Request Builder
        val builder = Request.Builder()

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        // 添加API密钥
        val currentApiKey = apiKeyProvider.getApiKey()
        val finalUrl =
                if (requestUrl.contains("?")) {
                    "$requestUrl&key=$currentApiKey"
                } else {
                    "$requestUrl?key=$currentApiKey"
                }

        val request = builder.url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

        logLargeString(TAG, "请求头: \n${request.headers}")
        return request
    }

    /** 确定基础URL */
    private fun determineBaseUrl(endpoint: String): String {
        return try {
            val url = URL(endpoint)
            val port = if (url.port != -1) ":${url.port}" else ""
            "${url.protocol}://${url.host}${port}"
        } catch (e: Exception) {
            logError("解析API端点失败", e)
            "https://generativelanguage.googleapis.com"
        }
    }

    /** 处理API流式响应 */
    private suspend fun processStreamingResponse(
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder
    ) {
        AppLogger.d(TAG, "开始处理响应流")
        val responseBody = response.body ?: throw IOException("响应为空")
        val reader = responseBody.charStream().buffered()

        // 注意：不再使用fullContent累积所有内容
        var lineCount = 0
        var dataCount = 0
        var jsonCount = 0
        var contentCount = 0

        // 恢复JSON累积逻辑，用于处理分段JSON
        val completeJsonBuilder = StringBuilder()
        var isCollectingJson = false
        var jsonDepth = 0
        var jsonStartSymbol = ' ' // 记录JSON是以 { 还是 [ 开始的

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    // 检查是否已取消
                    if (activeCall?.isCanceled() == true) {
                        return@forEach
                    }

                    // 处理SSE数据
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        dataCount++

                        // 跳过结束标记
                        if (data == "[DONE]") {
                            logDebug("收到流结束标记 [DONE]")
                            return@forEach
                        }

                        try {
                            // 立即解析每个SSE数据行的JSON
                            val json = JSONObject(data)
                            jsonCount++

                            val content = extractContentFromJson(json, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("提取SSE内容，长度: ${content.length}")
                                receivedContent.append(content)

                                // 只发送新增的内容
                                streamCollector.emit(content)
                            }
                        } catch (e: Exception) {
                            logError("解析SSE响应数据失败: ${e.message}", e)
                        }
                    } else if (line.trim().isNotEmpty()) {
                        // 处理可能分段的JSON数据
                        val trimmedLine = line.trim()

                        // 检查是否开始收集JSON
                        if (!isCollectingJson &&
                                        (trimmedLine.startsWith("{") || trimmedLine.startsWith("["))
                        ) {
                            isCollectingJson = true
                            jsonDepth = 0
                            completeJsonBuilder.clear()
                            jsonStartSymbol = trimmedLine[0]
                            logDebug("开始收集JSON，起始符号: $jsonStartSymbol")
                        }

                        if (isCollectingJson) {
                            completeJsonBuilder.append(trimmedLine)

                            // 更新JSON深度
                            for (char in trimmedLine) {
                                if (char == '{' || char == '[') jsonDepth++
                                if (char == '}' || char == ']') jsonDepth--
                            }

                            // 尝试作为完整JSON解析
                            val possibleComplete = completeJsonBuilder.toString()
                            try {
                                if (jsonDepth == 0) {
                                    logDebug("尝试解析完整JSON: ${possibleComplete.take(50)}...")
                                    val jsonContent =
                                            if (jsonStartSymbol == '[') {
                                                JSONArray(possibleComplete)
                                            } else {
                                                JSONObject(possibleComplete)
                                            }

                                    // 解析成功，处理内容
                                    logDebug("成功解析完整JSON，长度: ${possibleComplete.length}")

                                    when (jsonContent) {
                                        is JSONArray -> {
                                            // 处理JSON数组
                                            for (i in 0 until jsonContent.length()) {
                                                val jsonObject = jsonContent.optJSONObject(i)
                                                if (jsonObject != null) {
                                                    jsonCount++
                                                    val content =
                                                            extractContentFromJson(
                                                                    jsonObject,
                                                                    requestId,
                                                                    onTokensUpdated
                                                            )
                                                    if (content.isNotEmpty()) {
                                                        contentCount++
                                                        logDebug(
                                                                "从JSON数组[$i]提取内容，长度: ${content.length}"
                                                        )
                                                        receivedContent.append(content)

                                                        // 只发送这个单独对象产生的内容
                                                        streamCollector.emit(content)
                                                    }
                                                }
                                            }
                                        }
                                        is JSONObject -> {
                                            // 处理JSON对象
                                            jsonCount++
                                            val content =
                                                    extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                                            if (content.isNotEmpty()) {
                                                contentCount++
                                                logDebug("从JSON对象提取内容，长度: ${content.length}")
                                                receivedContent.append(content)

                                                // 只发送新提取的内容
                                                streamCollector.emit(content)
                                            }
                                        }
                                    }

                                    // 解析成功后重置收集器
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            } catch (e: Exception) {
                                // JSON尚未完整，继续收集
                                if (jsonDepth > 0) {
                                    // 仍在收集，这是预期的
                                    logDebug("继续收集JSON，当前深度: $jsonDepth")
                                } else {
                                    // 深度为0但解析失败，可能是无效JSON
                                    logError("JSON解析失败: ${e.message}", e)
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            }
                        }
                    }
                }
            }

            AppLogger.d(TAG, "响应处理完成: 共${lineCount}行, ${jsonCount}个JSON块, 提取${contentCount}个内容块")

            // 检查是否还有未解析完的JSON
            if (isCollectingJson && completeJsonBuilder.isNotEmpty()) {
                try {
                    val finalJson = completeJsonBuilder.toString()
                    AppLogger.d(TAG, "处理最终收集的JSON，长度: ${finalJson.length}")

                    val jsonContent =
                            if (jsonStartSymbol == '[') {
                                JSONArray(finalJson)
                            } else {
                                JSONObject(finalJson)
                            }
                    // 处理内容
                    when (jsonContent) {
                        is JSONArray -> {
                            for (i in 0 until jsonContent.length()) {
                                val jsonObject = jsonContent.optJSONObject(i) ?: continue
                                jsonCount++
                                val content = extractContentFromJson(jsonObject, requestId, onTokensUpdated)
                                if (content.isNotEmpty()) {
                                    contentCount++
                                    logDebug("从最终JSON数组[$i]提取内容，长度: ${content.length}")
                                    receivedContent.append(content)
                                    streamCollector.emit(content)
                                }
                            }
                        }
                        is JSONObject -> {
                            jsonCount++
                            val content = extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("从最终JSON对象提取内容，长度: ${content.length}")
                                receivedContent.append(content)
                                streamCollector.emit(content)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logError("解析最终收集的JSON失败: ${e.message}", e)
                }
            }

            // 确保思考模式正确结束
            if (isInThinkingMode) {
                logDebug("流结束时仍在思考模式，添加结束标签")
                streamCollector.emit("</think>")
                isInThinkingMode = false
            }
            
            // 确保至少发送一次内容
            if (contentCount == 0) {
                logDebug("未检测到内容，发送空格")
                streamCollector.emit(" ")
            }
        } catch (e: Exception) {
            logError("处理响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 处理API非流式响应 */
    private suspend fun processNonStreamingResponse(
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder
    ) {
        AppLogger.d(TAG, "开始处理非流式响应")
        val responseBody = response.body ?: throw IOException("响应为空")
        
        try {
            val responseText = responseBody.string()
            logDebug("收到完整响应，长度: ${responseText.length}")
            
            // 解析JSON响应
            val json = JSONObject(responseText)
            
            // 提取内容
            val content = extractContentFromJson(json, requestId, onTokensUpdated)
            
            if (content.isNotEmpty()) {
                receivedContent.append(content)
                
                // 直接发送整个内容块，下游会自己处理
                streamCollector.emit(content)
                
                logDebug("非流式响应处理完成，总长度: ${content.length}")
            } else {
                logDebug("未检测到内容，发送空格")
                streamCollector.emit(" ")
            }
            
            // 确保思考模式正确结束
            if (isInThinkingMode) {
                logDebug("非流式响应结束时仍在思考模式，添加结束标签")
                streamCollector.emit("</think>")
                isInThinkingMode = false
            }
        } catch (e: Exception) {
            logError("处理非流式响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private suspend fun extractContentFromJson(
        json: JSONObject,
        requestId: String,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ): String {
        val contentBuilder = StringBuilder()
        val searchSourcesBuilder = StringBuilder()

        try {
            // 检查是否有错误信息
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = error.optString("message", "未知错误")
                logError("API返回错误: $errorMsg")
                return "" // 有错误时返回空字符串
            }

            // 提取候选项
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                logDebug("未找到候选项")
                return ""
            }

            // 处理第一个candidate
            val candidate = candidates.getJSONObject(0)
            
            // 提取 Google Search grounding metadata（搜索来源信息）
            if (enableGoogleSearch) {
                val groundingMetadata = candidate.optJSONObject("groundingMetadata")
                if (groundingMetadata != null) {
                    // 提取搜索查询
                    val webSearchQueries = groundingMetadata.optJSONArray("webSearchQueries")
                    if (webSearchQueries != null && webSearchQueries.length() > 0) {
                        searchSourcesBuilder.append("\n<search>\n\n")
                        searchSourcesBuilder.append("**🔍 Google 搜索来源：**\n\n")
                        
                        for (i in 0 until webSearchQueries.length()) {
                            val query = webSearchQueries.optString(i)
                            searchSourcesBuilder.append("- 查询：`${query}`\n")
                            logDebug("搜索查询 [$i]: $query")
                        }
                        
                        // 提取搜索结果的URL来源
                        val groundingSupports = groundingMetadata.optJSONArray("groundingSupports")
                        if (groundingSupports != null && groundingSupports.length() > 0) {
                            searchSourcesBuilder.append("\n**📄 参考来源：**\n\n")
                            
                            for (i in 0 until groundingSupports.length()) {
                                val support = groundingSupports.getJSONObject(i)
                                val segment = support.optJSONObject("segment")
                                val groundingChunkIndices = support.optJSONArray("groundingChunkIndices")
                                
                                // 如果有chunk indices，提取对应的URL
                                if (groundingChunkIndices != null) {
                                    for (j in 0 until groundingChunkIndices.length()) {
                                        val chunkIndex = groundingChunkIndices.getInt(j)
                                        val retrievalMetadata = groundingMetadata.optJSONObject("retrievalMetadata")
                                        if (retrievalMetadata != null) {
                                            val webDynamicRetrievalScore = retrievalMetadata.optDouble("webDynamicRetrievalScore", -1.0)
                                            if (webDynamicRetrievalScore > 0) {
                                                logDebug("搜索动态检索分数: $webDynamicRetrievalScore")
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 提取 grounding chunks（包含URL）
                            val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                            if (groundingChunks != null && groundingChunks.length() > 0) {
                                for (i in 0 until groundingChunks.length()) {
                                    val chunk = groundingChunks.getJSONObject(i)
                                    val web = chunk.optJSONObject("web")
                                    if (web != null) {
                                        val uri = web.optString("uri", "")
                                        val title = web.optString("title", "")
                                        if (uri.isNotEmpty()) {
                                            if (title.isNotEmpty()) {
                                                searchSourcesBuilder.append("${i + 1}. [${title}](${uri})\n")
                                            } else {
                                                searchSourcesBuilder.append("${i + 1}. <${uri}>\n")
                                            }
                                            logDebug("搜索来源 [$i]: $title - $uri")
                                        }
                                    }
                                }
                            }
                        }
                        
                        searchSourcesBuilder.append("\n</search>\n\n")
                    }
                }
            }

            // 检查finish_reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                logDebug("收到完成原因: $finishReason")
            }

            // 提取content对象
            val content = candidate.optJSONObject("content")
            if (content == null) {
                logDebug("未找到content对象")
                return ""
            }

            // 提取parts数组
            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                logDebug("未找到parts数组或为空")
                return ""
            }

            // 遍历parts，提取text内容和functionCall
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                val isThought = part.optBoolean("thought", false)
                val functionCall = part.optJSONObject("functionCall")

                 val inlineData = part.optJSONObject("inline_data") ?: part.optJSONObject("inlineData")
                 if (inlineData != null) {
                     val mimeType = inlineData.optString("mime_type", inlineData.optString("mimeType", ""))
                     val b64 = inlineData.optString("data", "")
                     if (mimeType.startsWith("image/", ignoreCase = true) && b64.isNotEmpty()) {
                         if (isInThinkingMode) {
                             contentBuilder.append("</think>")
                             isInThinkingMode = false
                         }
                         val bytes = try {
                             Base64.decode(b64, Base64.DEFAULT)
                         } catch (_: Exception) {
                             null
                         }
                         if (bytes != null && bytes.isNotEmpty()) {
                             val uri = writeOutputImage(bytes, mimeType, "gemini_image_$i")
                             if (uri != null) {
                                 contentBuilder.append("\n![gemini_image_$i](${uri})\n")
                             }
                         }
                         continue
                     }
                 }

                // 处理 functionCall（流式转换为XML）
                if (functionCall != null && enableToolCall) {
                    val toolName = functionCall.optString("name", "")
                    if (toolName.isNotEmpty()) {
                        // 工具调用必须在思考模式之外，如果当前在思考中，先关闭
                        if (isInThinkingMode) {
                            contentBuilder.append("</think>")
                            isInThinkingMode = false
                            logDebug("检测到工具调用，提前结束思考模式")
                        }
                        
                        // 输出工具开始标签
                        contentBuilder.append("\n<tool name=\"$toolName\">")
                        
                        // 使用 StreamingJsonXmlConverter 流式转换参数
                        val args = functionCall.optJSONObject("args")
                        if (args != null) {
                            val converter = StreamingJsonXmlConverter()
                            val argsJson = args.toString()
                            val events = converter.feed(argsJson)
                            events.forEach { event ->
                                when (event) {
                                    is StreamingJsonXmlConverter.Event.Tag -> contentBuilder.append(event.text)
                                    is StreamingJsonXmlConverter.Event.Content -> contentBuilder.append(event.text)
                                }
                            }
                            // 刷新剩余内容
                            val flushEvents = converter.flush()
                            flushEvents.forEach { event ->
                                when (event) {
                                    is StreamingJsonXmlConverter.Event.Tag -> contentBuilder.append(event.text)
                                    is StreamingJsonXmlConverter.Event.Content -> contentBuilder.append(event.text)
                                }
                            }
                        }
                        
                        // 输出工具结束标签
                        contentBuilder.append("\n</tool>\n")
                        logDebug("Gemini FunctionCall流式转XML: $toolName")
                    }
                }

                if (text.isNotEmpty()) {
                    // 处理思考模式状态切换
                    if (isThought && !isInThinkingMode) {
                        // 开始思考模式
                        contentBuilder.append("<think>")
                        isInThinkingMode = true
                        logDebug("开始思考模式")
                    } else if (!isThought && isInThinkingMode) {
                        // 结束思考模式
                        contentBuilder.append("</think>")
                        isInThinkingMode = false
                        logDebug("结束思考模式")
                    }
                    
                    // 添加文本内容
                    contentBuilder.append(text)
                    
                    if (isThought) {
                        logDebug("提取思考内容，长度=${text.length}")
                    } else {
                        logDebug("提取文本，长度=${text.length}")
                    }

                    // 估算token
                    val tokens = ChatUtils.estimateTokenCount(text)
                    tokenCacheManager.addOutputTokens(tokens)
                    onTokensUpdated(
                            tokenCacheManager.totalInputTokenCount,
                            tokenCacheManager.cachedInputTokenCount,
                            tokenCacheManager.outputTokenCount
                    )
                }
            }

            // 提取实际的token使用数据
            val usageMetadata = json.optJSONObject("usageMetadata")
            if (usageMetadata != null) {
                val promptTokenCount = usageMetadata.optInt("promptTokenCount", 0)
                val cachedContentTokenCount = usageMetadata.optInt("cachedContentTokenCount", 0)
                val candidatesTokenCount = usageMetadata.optInt("candidatesTokenCount", 0)
                
                if (promptTokenCount > 0) {
                    // 更新实际的token计数
                    val actualInputTokens = promptTokenCount - cachedContentTokenCount
                    tokenCacheManager.updateActualTokens(actualInputTokens, cachedContentTokenCount)
                    
                    logDebug("API实际Token使用: 输入=$actualInputTokens, 缓存=$cachedContentTokenCount, 输出=$candidatesTokenCount")
                    
                    // 更新回调，使用实际的token统计
                    onTokensUpdated(
                        promptTokenCount,
                        cachedContentTokenCount,
                        candidatesTokenCount
                    )
                }
            }

            // 将搜索来源拼接到内容最前面
            val finalContent = if (searchSourcesBuilder.isNotEmpty()) {
                searchSourcesBuilder.toString() + contentBuilder.toString()
            } else {
                contentBuilder.toString()
            }
            
            return finalContent
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            return ""
        }
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = apiEndpoint,
            apiProviderType = ApiProviderType.GOOGLE
        )
    }

    override suspend fun testConnection(): Result<String> {
        return try {
            // 通过发送一条短消息来测试完整的连接、认证和API端点。
            // 这比getModelsList更可靠，因为它直接命中了聊天API。
            // 提供一个通用的系统提示，以防止某些需要它的模型出现错误。
            val testHistory = listOf("system" to "You are a helpful assistant.")
            val stream = sendMessage("Hi", testHistory, emptyList(), false, onTokensUpdated = { _, _, _ -> }, onNonFatalError = {})

            // 消耗流以确保连接有效。
            // 对 "Hi" 的响应应该很短，所以这会很快完成。
            var hasReceivedData = false
            stream.collect {
                hasReceivedData = true
            }

            // 某些情况下，即使连接成功，也可能不会返回任何数据（例如，如果模型只处理了提示而没有生成响应）。
            // 因此，只要不抛出异常，我们就认为连接成功。
            Result.success("连接成功！")
        } catch (e: Exception) {
            logError("连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }
}
