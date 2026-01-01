package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.StreamingJsonXmlConverter
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Anthropic Claude API的实现，处理Claude特有的API格式 */
class ClaudeProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.ANTHROPIC,
    private val enableToolCall: Boolean = false // 是否启用Tool Call接口（预留，Claude有原生tool支持）
) : AIService {
    // private val client: OkHttpClient = HttpClientFactory.instance

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val ANTHROPIC_VERSION = "2023-06-01" // Claude API版本

     private val DEFAULT_MAX_TOKENS = 4096

    // 当前活跃的Call对象，用于取消流式传输
    private var activeCall: Call? = null
    private var activeResponse: Response? = null
    @Volatile private var isManuallyCancelled = false

    /**
     * 不可重试的异常，通常由客户端错误（如4xx状态码）引起
     */
    class NonRetriableException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // 添加token计数器
    private val tokenCacheManager = TokenCacheManager()

    // 公开token计数
    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    // 重置token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
    }

    // 取消当前流式传输
    override fun cancelStreaming() {
        isManuallyCancelled = true

        // 1. 强制关闭 Response（这会立即中断流读取操作）
        activeResponse?.let {
            try {
                it.close()
                AppLogger.d("AIService", "已强制关闭Response流")
            } catch (e: Exception) {
                AppLogger.w("AIService", "关闭Response时出错: ${e.message}")
            }
        }
        activeResponse = null

        // 2. 取消 Call
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                AppLogger.d("AIService", "已取消当前流式传输，Call已中断")
            }
        }
        activeCall = null

        AppLogger.d("AIService", "取消标志已设置，流读取将立即被中断")
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

    private fun sanitizeToolCallId(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '_' || ch == '-') {
                sb.append(ch)
            } else {
                sb.append('_')
            }
        }
        var out = sb.toString().replace(Regex("_+"), "_")
        out = out.trim('_')
        return if (out.isEmpty()) "toolu" else out
    }

    private fun stableIdHashPart(raw: String): String {
        val hash = raw.hashCode()
        val positive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
        var base = positive.toString(36)
        base = base.filter { it.isLetterOrDigit() }.lowercase()
        return if (base.isEmpty()) "0" else base
    }

    /**
     * 解析XML格式的tool调用，转换为Claude Tool格式
     * @return Pair<文本内容, tool_use数组>
     */
    private fun parseXmlToolCalls(content: String): Pair<String, JSONArray?> {
        if (!enableToolCall) return Pair(content, null)

        val toolPattern = Regex("<tool\\s+name=\"([^\"]+)\">([\\s\\S]*?)</tool>", RegexOption.MULTILINE)
        val matches = toolPattern.findAll(content)

        if (!matches.any()) {
            return Pair(content, null)
        }

        val toolUses = JSONArray()
        var textContent = content
        var callIndex = 0

        matches.forEach { match ->
            val toolName = match.groupValues[1]
            val toolBody = match.groupValues[2]

            // 解析参数
            val paramPattern = Regex("<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
            val input = JSONObject()

            paramPattern.findAll(toolBody).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = XmlEscaper.unescape(paramMatch.groupValues[2].trim())
                input.put(paramName, paramValue)
            }

            // 构建tool_use对象（Claude格式）
            val toolNamePart = sanitizeToolCallId(toolName)
            val hashPart = stableIdHashPart("${toolName}:${input}")
            val callId = sanitizeToolCallId("toolu_${toolNamePart}_${hashPart}_$callIndex")
            toolUses.put(JSONObject().apply {
                put("type", "tool_use")
                put("id", callId)
                put("name", toolName)
                put("input", input)
            })

            callIndex++
            AppLogger.d("AIService", "XML→ClaudeToolUse: $toolName -> ID: $callId")

            // 从文本内容中移除tool标签
            textContent = textContent.replace(match.value, "")
        }
        
        return Pair(textContent.trim(), toolUses)
    }
    
    /**
     * 解析XML格式的tool_result，转换为Claude Tool Result格式
     * @return Pair<文本内容, tool_result数组>
     */
    private fun parseXmlToolResults(content: String): Pair<String, List<Pair<String, String>>?> {
        if (!enableToolCall) return Pair(content, null)
        
        val resultPattern = Regex("<tool_result[^>]*>([\\s\\S]*?)</tool_result>", RegexOption.MULTILINE)
        val matches = resultPattern.findAll(content)
        
        if (!matches.any()) {
            return Pair(content, null)
        }
        
        val results = mutableListOf<Pair<String, String>>()
        var textContent = content
        var resultIndex = 0
        
        matches.forEach { match ->
            val fullContent = match.groupValues[1].trim()
            val contentPattern = Regex("<content>([\\s\\S]*?)</content>", RegexOption.MULTILINE)
            val contentMatch = contentPattern.find(fullContent)
            val resultContent = if (contentMatch != null) {
                contentMatch.groupValues[1].trim()
            } else {
                fullContent
            }
            
            results.add(Pair("toolu_result_${resultIndex}", resultContent))
            textContent = textContent.replace(match.value, "").trim()
            
            AppLogger.d("AIService", "解析Claude tool_result #$resultIndex, content length=${resultContent.length}")
            resultIndex++
        }
        
        return Pair(textContent.trim(), results)
    }
    
    /**
     * 从ToolPrompt列表构建Claude格式的Tool Definitions
     */
    private fun buildToolDefinitionsForClaude(toolPrompts: List<ToolPrompt>): JSONArray {
        val tools = JSONArray()
        
        for (tool in toolPrompts) {
            tools.put(JSONObject().apply {
                put("name", tool.name)
                // 组合description和details作为完整描述
                val fullDescription = if (tool.details.isNotEmpty()) {
                    "${tool.description}\n${tool.details}"
                } else {
                    tool.description
                }
                put("description", fullDescription)
                
                // 使用结构化参数构建input_schema
                val inputSchema = buildSchemaFromStructured(tool.parametersStructured ?: emptyList())
                put("input_schema", inputSchema)
            })
        }
        
        return tools
    }
    
    /**
     * 从结构化参数构建JSON Schema（Claude格式）
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
     * 构建包含文本和图片的content数组
     */
    private fun buildContentArray(text: String): JSONArray {
        val contentArray = JSONArray()

        val textAfterMediaRemoval = if (MediaLinkParser.hasMediaLinks(text)) {
            AppLogger.w("AIService", "检测到音视频链接，但Claude格式当前仅支持图片，多媒体链接将被移除")
            MediaLinkParser.removeMediaLinks(text).trim()
        } else {
            text
        }
        
        // 检查是否包含图片链接
        if (ImageLinkParser.hasImageLinks(textAfterMediaRemoval)) {
            val imageLinks = ImageLinkParser.extractImageLinks(textAfterMediaRemoval)
            val textWithoutLinks = ImageLinkParser.removeImageLinks(textAfterMediaRemoval).trim()
            
            // 添加图片
            imageLinks.forEach { link ->
                contentArray.put(JSONObject().apply {
                    put("type", "image")
                    put("source", JSONObject().apply {
                        put("type", "base64")
                        put("media_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }
            
            // 添加文本（如果有）
            if (textWithoutLinks.isNotEmpty()) {
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", textWithoutLinks)
                })
            }
        } else {
            // 纯文本消息
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", textAfterMediaRemoval)
            })
        }
        
        return contentArray
    }

    /**
     * 构建Claude的消息体和计算Token的核心逻辑
     */
    private fun buildMessagesAndCountTokens(
            message: String,
            chatHistory: List<Pair<String, String>>,
            preserveThinkInHistory: Boolean
    ): Triple<JSONArray, String?, Int> {
        val messagesArray = JSONArray()

        // 使用TokenCacheManager计算token数量
        val tokenCount = tokenCacheManager.calculateInputTokens(message, chatHistory)

        // 检查当前消息是否已经在历史记录的末尾（避免重复）
        val isMessageInHistory = chatHistory.isNotEmpty() && chatHistory.last().second == message
        
        // 如果消息已在历史中，只处理历史；否则需要处理历史+当前消息
        val effectiveHistory = if (isMessageInHistory) {
            chatHistory
        } else {
            chatHistory + ("user" to message)
        }

        // 提取系统消息
        val systemMessages = effectiveHistory.filter { it.first.equals("system", ignoreCase = true) }
        var systemPrompt: String? = null

        if (systemMessages.isNotEmpty()) {
            systemPrompt = systemMessages.joinToString("\n\n") { it.second }
        }

        // 处理用户和助手消息
        val historyWithoutSystem =
                ChatUtils.mapChatHistoryToStandardRoles(effectiveHistory, extractThinking = preserveThinkInHistory).filter {
                    it.first != "system"
                }
        
        val mergedHistory = mutableListOf<Pair<String, String>>()
        for ((role, content) in historyWithoutSystem) {
            if (mergedHistory.isNotEmpty() && mergedHistory.last().first == role) {
                val lastMessage = mergedHistory.last()
                mergedHistory[mergedHistory.size - 1] =
                    Pair(role, lastMessage.second + "\n" + content)
            } else {
                mergedHistory.add(Pair(role, content))
            }
        }

        // 追踪上一个assistant消息中的tool_use ids，用于匹配tool结果
        val lastToolUseIds = mutableListOf<String>()
        
        // 添加历史消息
        for ((role, content) in mergedHistory) {
            val claudeRole = if (role == "assistant") "assistant" else "user"
            
            // 当启用Tool Call API时，转换XML格式的工具调用
            if (enableToolCall) {
                if (role == "assistant") {
                    // 解析assistant消息中的XML tool calls
                    val (textContent, toolUses) = parseXmlToolCalls(content)
                    
                    val contentArray = JSONArray()
                    // 先添加文本内容
                    if (textContent.isNotEmpty()) {
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", textContent)
                        })
                    }
                    // 再添加tool_use
                    if (toolUses != null && toolUses.length() > 0) {
                        for (i in 0 until toolUses.length()) {
                            contentArray.put(toolUses.getJSONObject(i))
                        }
                        // 记录这些tool_use ids供后续tool_result使用
                        lastToolUseIds.clear()
                        for (i in 0 until toolUses.length()) {
                            lastToolUseIds.add(toolUses.getJSONObject(i).getString("id"))
                        }
                    }
                    
                    val messageObject = JSONObject()
                    messageObject.put("role", claudeRole)
                    messageObject.put("content", contentArray)
                    messagesArray.put(messageObject)
                } else if (role == "user") {
                    // 解析user消息中的XML tool_result
                    val (textContent, toolResults) = parseXmlToolResults(content)
                    
                    val contentArray = JSONArray()
                    // 先添加tool_result（只转换有对应tool_use_id的）
                    if (toolResults != null && toolResults.isNotEmpty() && lastToolUseIds.isNotEmpty()) {
                        // 只转换有对应tool_use_id的tool_result
                        val validCount = minOf(toolResults.size, lastToolUseIds.size)
                        
                        for (index in 0 until validCount) {
                            val (_, resultContent) = toolResults[index]
                            contentArray.put(JSONObject().apply {
                                put("type", "tool_result")
                                put("tool_use_id", lastToolUseIds[index])
                                put("content", resultContent)
                            })
                            AppLogger.d("AIService", "历史XML→ClaudeToolResult: ID=${lastToolUseIds[index]}, content length=${resultContent.length}")
                        }
                        
                        // 如果有多余的tool_result，记录警告
                        if (toolResults.size > validCount) {
                            AppLogger.w("AIService", "发现多余的tool_result: ${toolResults.size} results vs ${lastToolUseIds.size} tool_uses，忽略多余的${toolResults.size - validCount}个")
                        }
                        
                        lastToolUseIds.clear()
                    }
                    // 再添加文本内容
                    if (textContent.isNotEmpty()) {
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", textContent)
                        })
                    } else if (contentArray.length() == 0) {
                        // 如果没有任何内容，保留原始content
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", content)
                        })
                    }
                    
                    val messageObject = JSONObject()
                    messageObject.put("role", claudeRole)
                    messageObject.put("content", contentArray)
                    messagesArray.put(messageObject)
                } else {
                    // system等其他角色正常处理
                    val messageObject = JSONObject()
                    messageObject.put("role", claudeRole)
                    messageObject.put("content", buildContentArray(content))
                    messagesArray.put(messageObject)
                }
            } else {
                // 不启用Tool Call API时，保持原样
                val messageObject = JSONObject()
                messageObject.put("role", claudeRole)
                messageObject.put("content", buildContentArray(content))
                messagesArray.put(messageObject)
            }
        }

        return Triple(messagesArray, systemPrompt, tokenCount)
    }


    override suspend fun calculateInputTokens(
            message: String,
            chatHistory: List<Pair<String, String>>,
            availableTools: List<ToolPrompt>?
    ): Int {
        // 构建工具定义的JSON字符串
        val toolsJson = if (enableToolCall && availableTools != null && availableTools.isNotEmpty()) {
            val tools = buildToolDefinitionsForClaude(availableTools)
            if (tools.length() > 0) tools.toString() else null
        } else {
            null
        }
        // 使用缓存管理器进行快速估算
        return tokenCacheManager.calculateInputTokens(message, chatHistory, toolsJson)
    }

    // 创建Claude API请求体
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>> = emptyList(),
            enableThinking: Boolean,
            stream: Boolean = true,
            availableTools: List<ToolPrompt>? = null,
            preserveThinkInHistory: Boolean = false
    ): RequestBody {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream)

        // 添加已启用的模型参数
        addParameters(jsonObject, modelParameters)

         val maxTokensFromParams = modelParameters
             .firstOrNull { it.apiName == "max_tokens" }
             ?.currentValue
         val maxTokensValue = (maxTokensFromParams as? Number)?.toInt()?.takeIf { it > 0 }
             ?: jsonObject.optInt("max_tokens", 0).takeIf { it > 0 }
             ?: DEFAULT_MAX_TOKENS
         jsonObject.put("max_tokens", maxTokensValue)

        // 添加 Tool Call 工具定义（如果启用且有可用工具）
        var toolsJson: String? = null
        if (enableToolCall && availableTools != null && availableTools.isNotEmpty()) {
            val tools = buildToolDefinitionsForClaude(availableTools)
            if (tools.length() > 0) {
                jsonObject.put("tools", tools)
                toolsJson = tools.toString() // 保存工具定义用于token计算
                AppLogger.d("AIService", "已添加 ${tools.length()} 个 Claude Tool Definitions")
            }
        }

        // 使用TokenCacheManager计算输入token（包含工具定义），并继续使用原有逻辑构建消息体
        tokenCacheManager.calculateInputTokens(message, chatHistory, toolsJson)
        val (messagesArray, systemPrompt, _) = buildMessagesAndCountTokens(message, chatHistory, preserveThinkInHistory)

        jsonObject.put("messages", messagesArray)

        // Claude对系统消息的处理有所不同，它使用system参数
        if (systemPrompt != null) {
            jsonObject.put("system", systemPrompt)
        }

        // 添加extended thinking支持
        if (enableThinking) {
            val thinkingObject = JSONObject()
            thinkingObject.put("type", "enabled")

             val budgetTokensFromParams = modelParameters
                 .firstOrNull { it.apiName == "budget_tokens" }
                 ?.currentValue
             val budgetTokensValue = (budgetTokensFromParams as? Number)?.toInt()?.takeIf { it > 0 }
                 ?: minOf(1024, maxTokensValue)
             thinkingObject.put("budget_tokens", budgetTokensValue)

            jsonObject.put("thinking", thinkingObject)
            AppLogger.d("AIService", "启用Claude的extended thinking功能")
        }

        // 日志输出时省略过长的tools字段
        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        AppLogger.d("AIService", "Claude请求体: ${logJson.toString(4)}")
        return jsonObject.toString().toRequestBody(JSON)
    }

    // 添加模型参数
    private fun addParameters(jsonObject: JSONObject, modelParameters: List<ModelParameter<*>>) {
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            jsonObject.put("temperature", (param.currentValue as Number).toFloat())
                    "top_p" -> jsonObject.put("top_p", (param.currentValue as Number).toFloat())
                    "top_k" -> jsonObject.put("top_k", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            jsonObject.put("max_tokens", (param.currentValue as Number).toInt())
                    "max_tokens_to_sample" ->
                            jsonObject.put(
                                    "max_tokens_to_sample",
                                    (param.currentValue as Number).toInt()
                            )
                    "stop_sequences" -> {
                        // 处理停止序列
                        val stopSequences = param.currentValue as? List<*>
                        if (stopSequences != null) {
                            val stopArray = JSONArray()
                            stopSequences.forEach { stopArray.put(it.toString()) }
                            jsonObject.put("stop_sequences", stopArray)
                        }
                    }
                    // 忽略thinking相关参数，因为它们会在单独的部分处理
                    "thinking",
                    "budget_tokens" -> {
                        // 忽略，在特定部分处理
                    }
                    else -> {
                        // 添加其他Claude特定参数
                        when (param.valueType) {
                            com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                                    jsonObject.put(param.apiName, param.currentValue as Int)
                            com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                                    jsonObject.put(param.apiName, param.currentValue as Float)
                            com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                                    jsonObject.put(param.apiName, param.currentValue as String)
                            com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                                    jsonObject.put(param.apiName, param.currentValue as Boolean)
                            com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                                val raw = param.currentValue.toString().trim()
                                val parsed: Any? = try {
                                    when {
                                        raw.startsWith("{") -> JSONObject(raw)
                                        raw.startsWith("[") -> JSONArray(raw)
                                        else -> null
                                    }
                                } catch (e: Exception) {
                                    AppLogger.w("AIService", "Claude OBJECT参数解析失败: ${param.apiName}", e)
                                    null
                                }
                                if (parsed != null) {
                                    jsonObject.put(param.apiName, parsed)
                                } else {
                                    jsonObject.put(param.apiName, raw)
                                }
                            }
                        }
                    }
                }
                AppLogger.d("AIService", "添加Claude参数 ${param.apiName} = ${param.currentValue}")
            }
        }
    }

    // 创建请求
    private suspend fun createRequest(requestBody: RequestBody): Request {
        val currentApiKey = apiKeyProvider.getApiKey()
        val builder =
                Request.Builder()
                        .url(apiEndpoint)
                        .post(requestBody)
                        .addHeader("x-api-key", currentApiKey)
                        .addHeader("anthropic-version", ANTHROPIC_VERSION)
                        .addHeader("Content-Type", "application/json")

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        val request = builder.build()
        AppLogger.d("AIService", "Claude请求头: \n${request.headers}")
        return request
    }

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
        // 重置token计数
        resetTokenCounts()

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()

        AppLogger.d("AIService", "准备连接到Claude AI服务...")
        while (retryCount < maxRetries) {
            // 在循环开始时检查是否已被取消
            if (isManuallyCancelled) {
                AppLogger.d("AIService", "【Claude】请求被用户取消，停止重试。")
                throw UserCancellationException("请求已被用户取消")
            }
            
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    AppLogger.d("AIService", "【Claude 重试】准备续写请求，已接收内容: ${receivedContent.length}")
                    // Claude 对续写指令可能需要不同的优化，这里使用一个通用的方式
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("assistant" to receivedContent.toString())
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }

                val requestBody = createRequestBody(currentMessage, currentHistory, modelParameters, enableThinking, stream, availableTools, preserveThinkInHistory)
                onTokensUpdated(
                        tokenCacheManager.totalInputTokenCount,
                        tokenCacheManager.cachedInputTokenCount,
                        tokenCacheManager.outputTokenCount
                )
                val request = createRequest(requestBody)

                // 创建Call对象并保存到activeCall中，以便可以取消
                val call = client.newCall(request)
                activeCall = call

                AppLogger.d("AIService", "正在建立连接...")
                val response = call.execute()
                activeResponse = response
                try {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        // 对于4xx这类明确的客户端错误，直接抛出，不进行重试
                        if (response.code in 400..499) {
                            throw NonRetriableException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                        }
                        // 对于5xx等服务端错误，允许重试
                        throw IOException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                    }

                    AppLogger.d("AIService", "连接成功，等待响应...")
                    val responseBody = response.body ?: throw IOException("API响应为空")
                    
                    // 根据stream参数处理响应
                    if (stream) {
                        // 处理流式响应
                        val reader = responseBody.charStream().buffered()
                        
                        // Tool call 流式输出支持
                        var currentToolParser: StreamingJsonXmlConverter? = null
                        var isInToolCall = false
                        var wasCancelled = false

                        try {
                            reader.useLines { lines ->
                                for (line in lines) {
                                    // 如果call已被取消，提前退出
                                    if (activeCall?.isCanceled() == true) {
                                        AppLogger.d("AIService", "流式传输已被取消，提前退出处理")
                                        wasCancelled = true
                                        break
                                    }

                                    if (!line.startsWith("data: ")) continue

                                    val data = line.substring(6).trim()
                                    if (data == "[DONE]") {
                                        break
                                    }

                                    try {
                                        val jsonResponse = JSONObject(data)

                                        val type = jsonResponse.optString("type", "")

                                        when (type) {
                                            "ping" -> {
                                            }
                                            "content_block_start" -> {
                                                if (enableToolCall) {
                                                    val contentBlock = jsonResponse.optJSONObject("content_block")
                                                    if (contentBlock != null && contentBlock.optString("type") == "tool_use") {
                                                        val toolName = contentBlock.optString("name", "")
                                                        if (toolName.isNotEmpty()) {
                                                            val toolStartTag = "\n<tool name=\"$toolName\">"
                                                            emit(toolStartTag)
                                                            receivedContent.append(toolStartTag)

                                                            currentToolParser = StreamingJsonXmlConverter()
                                                            isInToolCall = true

                                                            val input = contentBlock.optJSONObject("input")
                                                            if (input != null) {
                                                                val inputJson = input.toString()
                                                                val events = currentToolParser!!.feed(inputJson)
                                                                events.forEach { event ->
                                                                    when (event) {
                                                                        is StreamingJsonXmlConverter.Event.Tag -> {
                                                                            emit(event.text)
                                                                            receivedContent.append(event.text)
                                                                        }
                                                                        is StreamingJsonXmlConverter.Event.Content -> {
                                                                            emit(event.text)
                                                                            receivedContent.append(event.text)
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            AppLogger.d("AIService", "Claude Tool Use流式转XML: $toolName")
                                                        }
                                                    }
                                                }
                                            }
                                            "content_block_delta" -> {
                                                val delta = jsonResponse.optJSONObject("delta")
                                                if (delta != null) {
                                                    val deltaType = delta.optString("type", "")
                                                    if (deltaType == "text_delta" || delta.has("text")) {
                                                        val content = delta.optString("text", "")
                                                        if (content.isNotEmpty()) {
                                                            tokenCacheManager.addOutputTokens(ChatUtils.estimateTokenCount(content))
                                                            onTokensUpdated(
                                                                tokenCacheManager.totalInputTokenCount,
                                                                tokenCacheManager.cachedInputTokenCount,
                                                                tokenCacheManager.outputTokenCount
                                                            )
                                                            emit(content)
                                                            receivedContent.append(content)
                                                        }
                                                    } else if (enableToolCall && isInToolCall && currentToolParser != null && deltaType == "input_json_delta") {
                                                        val partialJson = delta.optString("partial_json", "")
                                                        if (partialJson.isNotEmpty()) {
                                                            val events = currentToolParser!!.feed(partialJson)
                                                            events.forEach { event ->
                                                                when (event) {
                                                                    is StreamingJsonXmlConverter.Event.Tag -> {
                                                                        emit(event.text)
                                                                        receivedContent.append(event.text)
                                                                    }
                                                                    is StreamingJsonXmlConverter.Event.Content -> {
                                                                        emit(event.text)
                                                                        receivedContent.append(event.text)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            "content_block_stop" -> {
                                                if (isInToolCall && currentToolParser != null) {
                                                    val events = currentToolParser!!.flush()
                                                    events.forEach { event ->
                                                        when (event) {
                                                            is StreamingJsonXmlConverter.Event.Tag -> {
                                                                emit(event.text)
                                                                receivedContent.append(event.text)
                                                            }
                                                            is StreamingJsonXmlConverter.Event.Content -> {
                                                                emit(event.text)
                                                                receivedContent.append(event.text)
                                                            }
                                                        }
                                                    }

                                                    val toolEndTag = "\n</tool>\n"
                                                    emit(toolEndTag)
                                                    receivedContent.append(toolEndTag)

                                                    isInToolCall = false
                                                    currentToolParser = null
                                                }
                                            }
                                            "message_delta" -> {
                                            }
                                            "message_stop" -> {
                                                break
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w("AIService", "JSON解析错误: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            if (isManuallyCancelled) {
                                AppLogger.d("AIService", "【Claude】流式传输已被用户取消，停止后续操作。")
                                throw UserCancellationException("请求已被用户取消", e)
                            }
                            // 捕获IO异常，可能是由于取消Call导致的
                            var wasCancelled = false
                            if (activeCall?.isCanceled() == true) {
                                AppLogger.d("AIService", "流式传输已被取消，处理IO异常")
                                wasCancelled = true
                            } else {
                                 AppLogger.e("AIService", "【Claude】流式读取时发生IO异常，准备重试", e)
                                lastException = e
                                throw e
                            }
                        }
                    } else {
                        // 处理非流式响应
                        val responseText = responseBody.string()
                        AppLogger.d("AIService", "收到完整响应，长度: ${responseText.length}")
                        
                        try {
                            val jsonResponse = JSONObject(responseText)
                            
                            // Claude非流式响应格式
                            val content = jsonResponse.optJSONArray("content")
                            if (content != null && content.length() > 0) {
                                val fullText = StringBuilder()
                                for (i in 0 until content.length()) {
                                    val block = content.getJSONObject(i)
                                    when (block.optString("type")) {
                                        "text" -> {
                                            val text = block.optString("text", "")
                                            if (text.isNotEmpty()) {
                                                fullText.append(text)
                                            }
                                        }
                                        "tool_use" -> {
                                            // 流式转换 tool_use 为 XML
                                            if (enableToolCall) {
                                                val toolName = block.optString("name", "")
                                                if (toolName.isNotEmpty()) {
                                                    fullText.append("\n<tool name=\"$toolName\">")
                                                    
                                                    // 使用 StreamingJsonXmlConverter 流式转换参数
                                                    val input = block.optJSONObject("input")
                                                    if (input != null) {
                                                        val converter = StreamingJsonXmlConverter()
                                                        val inputJson = input.toString()
                                                        val events = converter.feed(inputJson)
                                                        events.forEach { event ->
                                                            when (event) {
                                                                is StreamingJsonXmlConverter.Event.Tag -> fullText.append(event.text)
                                                                is StreamingJsonXmlConverter.Event.Content -> fullText.append(event.text)
                                                            }
                                                        }
                                                        // 刷新剩余内容
                                                        val flushEvents = converter.flush()
                                                        flushEvents.forEach { event ->
                                                            when (event) {
                                                                is StreamingJsonXmlConverter.Event.Tag -> fullText.append(event.text)
                                                                is StreamingJsonXmlConverter.Event.Content -> fullText.append(event.text)
                                                            }
                                                        }
                                                    }
                                                    
                                                    fullText.append("\n</tool>\n")
                                                    AppLogger.d("AIService", "Claude Tool Use流式转XML (非流式): $toolName")
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                val resultText = fullText.toString()
                                if (resultText.isNotEmpty()) {
                                    // 直接发送整个内容块，下游会自己处理
                                    emit(resultText)
                                    receivedContent.append(resultText)
                                    tokenCacheManager.addOutputTokens(ChatUtils.estimateTokenCount(resultText))
                                    onTokensUpdated(
                                            tokenCacheManager.totalInputTokenCount,
                                            tokenCacheManager.cachedInputTokenCount,
                                            tokenCacheManager.outputTokenCount
                                    )
                                }
                            }
                            
                            AppLogger.d("AIService", "【Claude】非流式响应处理完成")
                        } catch (e: Exception) {
                            AppLogger.e("AIService", "【Claude】解析非流式响应失败", e)
                            throw IOException("解析响应失败: ${e.message}", e)
                        }
                    }
                } finally {
                    response.close()
                    AppLogger.d("AIService", "【Claude】关闭响应连接")
                }

                // 清理活跃引用
                activeCall = null
                activeResponse = null

                // 成功处理后，返回
                AppLogger.d("AIService", "【Claude】请求成功完成")
                return@stream
            } catch (e: NonRetriableException) {
                AppLogger.e("AIService", "【Claude】发生不可重试错误", e)
                throw e // 直接抛出，不重试
            } catch (e: SocketTimeoutException) {
                if (isManuallyCancelled) {
                    AppLogger.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    AppLogger.e("AIService", "【Claude】连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                AppLogger.w("AIService", "【Claude】连接超时，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                if (isManuallyCancelled) {
                    AppLogger.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    AppLogger.e("AIService", "【Claude】无法解析主机且达到最大重试次数", e)
                throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                AppLogger.w("AIService", "【Claude】无法解析主机，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                if (isManuallyCancelled) {
                    AppLogger.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if(retryCount >= maxRetries) {
                    AppLogger.e("AIService", "【Claude】达到最大重试次数", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                AppLogger.w("AIService", "【Claude】网络中断，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            }
            catch (e: Exception) {
                if (isManuallyCancelled) {
                    AppLogger.d("AIService", "【Claude】请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                AppLogger.e("AIService", "【Claude】发生未知异常，停止重试", e)
                throw IOException("AI响应获取失败: ${e.message}", e)
            }
        }
        
        // 添加空检查，因为lastException是可空的Exception?类型
        lastException?.let { ex ->
            AppLogger.e("AIService", "【Claude】重试失败，请检查网络连接", ex)
        } ?: AppLogger.e("AIService", "【Claude】重试失败，请检查网络连接")
        // 所有重试都失败
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }

    /**
     * 获取模型列表 注意：此方法直接调用ModelListFetcher获取模型列表
     * @return 模型列表结果
     */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // 调用ModelListFetcher获取模型列表
        return ModelListFetcher.getModelsList(
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = apiEndpoint,
            apiProviderType = ApiProviderType.ANTHROPIC
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
            stream.collect { _ -> }

            Result.success("连接成功！")
        } catch (e: Exception) {
            AppLogger.e("AIService", "连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }
}
