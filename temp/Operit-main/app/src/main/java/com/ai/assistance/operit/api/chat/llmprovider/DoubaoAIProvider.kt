package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 针对豆包（Doubao）模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`thinking`参数。
 */
class DoubaoAIProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: com.ai.assistance.operit.data.model.ApiProviderType = com.ai.assistance.operit.data.model.ApiProviderType.DOUBAO,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenAIProvider(
        apiEndpoint = apiEndpoint,
        apiKeyProvider = apiKeyProvider,
        modelName = modelName,
        client = client,
        customHeaders = customHeaders,
        providerType = providerType,
        supportsVision = supportsVision,
        supportsAudio = supportsAudio,
        supportsVideo = supportsVideo,
        enableToolCall = enableToolCall
    ) {

    /**
     * 重写创建请求体的方法，以支持豆包的`thinking`参数。
     */
    override fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        // 首先，调用父类的实现来获取一个标准的OpenAI格式的请求体JSON对象
        val baseRequestBodyJson = super.createRequestBodyInternal(message, chatHistory, modelParameters, stream, availableTools, preserveThinkInHistory)
        val jsonObject = JSONObject(baseRequestBodyJson)

        // 如果启用了思考模式，则为豆包模型添加特定的`thinking`参数
        if (enableThinking) {
            jsonObject.put("thinking", true)
            AppLogger.d("DoubaoAIProvider", "已为豆包模型启用“思考模式”。")
        }

        // 记录最终的请求体（省略过长的tools字段）
        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        // 使用更新后的JSONObject创建新的RequestBody
        return jsonObject.toString().toRequestBody(JSON)
    }
}
