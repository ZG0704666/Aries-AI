package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/** 模型列表获取工具，用于从不同API提供商获取可用模型列表 */
object ModelListFetcher {
    private const val TAG = "ModelListFetcher"

    // 使用更长的超时时间
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

    /**
     * 从API端点URL派生出模型列表URL
     *
     * @param apiEndpoint 完整的API端点URL (如 https://api.openai.com/v1/chat/completions)
     * @param apiProviderType API提供商类型
     * @return 用于获取模型列表的URL
     */
    fun getModelsListUrl(apiEndpoint: String, apiProviderType: ApiProviderType): String {
        AppLogger.d(TAG, "生成模型列表URL，API端点: $apiEndpoint, 提供商类型: $apiProviderType")

        val modelsUrl =
                when (apiProviderType) {
                    ApiProviderType.OPENAI,
                    ApiProviderType.OPENAI_GENERIC -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.ANTHROPIC -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.GOOGLE,
                    ApiProviderType.GEMINI_GENERIC -> {
                        // 对于Gemini API，直接使用提供的端点或默认端点
                        if (apiEndpoint.contains("generativelanguage.googleapis.com")) {
                            // 如果端点已经是模型列表URL，直接使用
                            if (apiEndpoint.endsWith("/models")) {
                                apiEndpoint
                            } else {
                                // 否则构造标准模型列表URL
                                val version = if (apiEndpoint.contains("/v1/")) "v1" else "v1beta"
                                "https://generativelanguage.googleapis.com/$version/models"
                            }
                        } else if (apiEndpoint.contains("aiplatform.googleapis.com") ||
                                        apiEndpoint.contains("vertex")
                        ) {
                            // Vertex AI格式
                            val projectMatch = Regex("projects/([^/]+)").find(apiEndpoint)
                            val locationMatch = Regex("locations/([^/]+)").find(apiEndpoint)

                            if (projectMatch != null && locationMatch != null) {
                                val project = projectMatch.groupValues[1]
                                val location = locationMatch.groupValues[1]
                                "https://$location-aiplatform.googleapis.com/v1/projects/$project/locations/$location/publishers/google/models"
                            } else {
                                "https://generativelanguage.googleapis.com/v1beta/models"
                            }
                        } else {
                            // 默认使用直接API
                            "https://generativelanguage.googleapis.com/v1beta/models"
                        }
                    }
                    ApiProviderType.DEEPSEEK -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.OPENROUTER -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.MOONSHOT -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.SILICONFLOW -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.BAICHUAN -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.INFINIAI -> "${extractBaseUrl(apiEndpoint)}/maas/v1/models"
                    ApiProviderType.ALIPAY_BAILING -> "${extractBaseUrl(apiEndpoint)}/llm/v1/models"
                    ApiProviderType.LMSTUDIO -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    ApiProviderType.PPINFRA -> "${extractBaseUrl(apiEndpoint)}/v1/models"
                    // 其他API提供商可能需要特殊处理
                    else -> "${extractBaseUrl(apiEndpoint)}/v1/models" // 默认尝试OpenAI兼容格式
                }

        AppLogger.d(TAG, "生成的模型列表URL: $modelsUrl")
        return modelsUrl
    }

    /** 从完整URL提取基本URL 例如: https://api.openai.com/v1/chat/completions -> https://api.openai.com */
    private fun extractBaseUrl(fullUrl: String): String {
        return try {
            val url = URL(fullUrl)
            val path = url.path

            // 查找版本路径，例如 /v1, /v2
            val versionPathRegex = Regex("/v\\d+")
            val match = versionPathRegex.find(path)

            if (match != null) {
                // 截取到版本路径之前的部分
                val pathBeforeVersion = path.substring(0, match.range.first)
                val finalUrl = "${url.protocol}://${url.authority}$pathBeforeVersion"
                AppLogger.d(TAG, "从 $fullUrl 提取基本URL: $finalUrl (找到版本路径 ${match.value})")
                finalUrl
            } else {
                // 如果找不到版本路径，则返回原始URL的主机部分，这通常是安全的备选方案
                val finalUrl = "${url.protocol}://${url.authority}"
                AppLogger.d(TAG, "从 $fullUrl 提取基本URL: $finalUrl (未找到版本路径)")
                finalUrl
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "URL解析错误: $e")
            fullUrl
        }
    }

    /**
     * 获取模型列表
     *
     * @param apiKey API密钥
     * @param apiEndpoint 完整的API端点URL
     * @param apiProviderType API提供商类型
     * @return 模型列表结果
     */
    suspend fun getModelsList(
            apiKey: String,
            apiEndpoint: String,
            apiProviderType: ApiProviderType = ApiProviderType.OPENAI
    ): Result<List<ModelOption>> {
        AppLogger.d(TAG, "开始获取模型列表: 端点=$apiEndpoint, 提供商=${apiProviderType.name}")

        return withContext(Dispatchers.IO) {
            val maxRetries = 2
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount <= maxRetries) {
                try {
                    // 根据提供商类型获取模型列表URL
                    val modelsUrl = getModelsListUrl(EndpointCompleter.completeEndpoint(apiEndpoint), apiProviderType)
                    AppLogger.d(TAG, "准备发送请求到: $modelsUrl, 尝试次数: ${retryCount + 1}/${maxRetries + 1}")

                    val requestBuilder =
                            Request.Builder()
                                    .url(modelsUrl)
                                    .addHeader("Content-Type", "application/json")

                    // 根据不同供应商添加不同的认证头
                    when (apiProviderType) {
                        ApiProviderType.GOOGLE,
                        ApiProviderType.GEMINI_GENERIC -> {
                            // Google Gemini API 使用 API 密钥作为查询参数
                            val urlWithKey =
                                    if (modelsUrl.contains("?")) {
                                        "$modelsUrl&key=$apiKey"
                                    } else {
                                        "$modelsUrl?key=$apiKey"
                                    }
                            AppLogger.d(
                                    TAG,
                                    "添加Google API密钥，完整URL: ${urlWithKey.replace(apiKey, "API_KEY_HIDDEN")}"
                            )
                            requestBuilder.url(urlWithKey)
                        }
                        ApiProviderType.OPENROUTER -> {
                            // OpenRouter需要添加特定请求头
                            AppLogger.d(TAG, "使用Bearer认证方式并添加OpenRouter特定请求头")
                            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                            requestBuilder.addHeader("HTTP-Referer", "ai.assistance.operit")
                            requestBuilder.addHeader("X-Title", "Assistance App")
                        }
                        else -> {
                            // 大多数API使用Bearer认证
                            AppLogger.d(TAG, "使用Bearer认证方式")
                            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                        }
                    }

                    val request = requestBuilder.get().build()

                    AppLogger.d(TAG, "发送HTTP请求: ${request.url}")
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "无错误详情"
                        AppLogger.e(TAG, "API请求失败: 状态码=${response.code}, 错误=$errorBody")
                        return@withContext Result.failure(
                                IOException("API请求失败: ${response.code}, 错误: $errorBody")
                        )
                    }

                    val responseBody = response.body?.string()
                    if (responseBody == null) {
                        AppLogger.e(TAG, "响应体为空")
                        return@withContext Result.failure(IOException("响应体为空"))
                    }

                    AppLogger.d(
                            TAG,
                            "收到响应: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}"
                    )

                    // 根据提供商类型解析响应
                    val modelOptions =
                            try {
                                when (apiProviderType) {
                                    ApiProviderType.OPENAI,
                                    ApiProviderType.OPENAI_GENERIC,
                                    ApiProviderType.DEEPSEEK,
                                    ApiProviderType.MOONSHOT,
                                    ApiProviderType.SILICONFLOW,
                                    ApiProviderType.BAICHUAN,
                                    ApiProviderType.OPENROUTER,
                                    ApiProviderType.INFINIAI,
                                    ApiProviderType.ALIPAY_BAILING,
                                    ApiProviderType.LMSTUDIO,
                                    ApiProviderType.PPINFRA -> parseOpenAIModelResponse(responseBody)
                                    ApiProviderType.ANTHROPIC -> parseAnthropicModelResponse(responseBody)
                                    ApiProviderType.GOOGLE,
                                    ApiProviderType.GEMINI_GENERIC -> parseGoogleModelResponse(responseBody)

                                    // 其他提供商可能需要单独的解析方法
                                    else -> parseOpenAIModelResponse(responseBody) // 默认尝试OpenAI格式
                                }
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "解析响应失败: ${e.message}")
                                return@withContext Result.failure(e)
                            }

                    AppLogger.d(TAG, "成功解析模型列表，共获取 ${modelOptions.size} 个模型")
                    return@withContext Result.success(modelOptions)
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    retryCount++
                    AppLogger.e(TAG, "连接超时: ${e.message}", e)
                    AppLogger.d(TAG, "网络超时，尝试重试 $retryCount/$maxRetries")

                    if (retryCount <= maxRetries) {
                        // 指数退避重试
                        val delayTime = 1000L * retryCount
                        AppLogger.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                } catch (e: IOException) {
                    lastException = e
                    retryCount++
                    AppLogger.e(TAG, "IO异常: ${e.message}", e)
                    AppLogger.d(TAG, "IO异常，尝试重试 $retryCount/$maxRetries")

                    if (retryCount <= maxRetries) {
                        val delayTime = 1000L * retryCount
                        AppLogger.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                } catch (e: UnknownHostException) {
                    AppLogger.e(TAG, "无法连接到服务器，域名解析失败", e)
                    return@withContext Result.failure(IOException("无法连接到服务器，请检查网络连接和API地址是否正确", e))
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    AppLogger.e(TAG, "获取模型列表失败: ${e.message}", e)

                    if (retryCount <= maxRetries) {
                        // 指数退避重试
                        val delayTime = 1000L * retryCount
                        AppLogger.d(TAG, "延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                }
            }

            // 所有重试都失败
            AppLogger.e(TAG, "超过最大重试次数，获取模型列表失败")
            Result.failure(lastException ?: IOException("获取模型列表失败"))
        }
    }

    /** 解析OpenAI格式的模型响应 格式: {"data": [{"id": "model-id", "object": "model", ...}, ...]} */
    private fun parseOpenAIModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)
            if (!jsonObject.has("data")) {
                AppLogger.e(TAG, "OpenAI响应格式错误: 缺少'data'字段")
                throw JSONException("响应格式错误: 缺少'data'字段")
            }

            val dataArray = jsonObject.getJSONArray("data")
            AppLogger.d(TAG, "解析OpenAI格式响应: 发现 ${dataArray.length()} 个模型")

            for (i in 0 until dataArray.length()) {
                val modelObj = dataArray.getJSONObject(i)
                val id = modelObj.getString("id")
                modelList.add(ModelOption(id = id, name = id))
            }
        } catch (e: JSONException) {
            AppLogger.e(TAG, "解析OpenAI格式JSON失败: ${e.message}", e)
            throw e
        }

        // 按照模型名称排序
        return modelList.sortedBy { it.id }
    }

    /** 解析Anthropic格式的模型响应 */
    private fun parseAnthropicModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)
            if (!jsonObject.has("models")) {
                AppLogger.e(TAG, "Anthropic响应格式错误: 缺少'models'字段")
                throw JSONException("响应格式错误: 缺少'models'字段")
            }

            val modelsArray = jsonObject.getJSONArray("models")
            AppLogger.d(TAG, "解析Anthropic格式响应: 发现 ${modelsArray.length()} 个模型")

            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val id = modelObj.getString("name")
                val displayName = modelObj.optString("display_name", id)
                modelList.add(ModelOption(id = id, name = displayName))
            }
        } catch (e: JSONException) {
            AppLogger.e(TAG, "解析Anthropic模型JSON失败: ${e.message}", e)
            throw e
        }

        return modelList.sortedBy { it.id }
    }

    /**
     * 解析Google Gemini API格式的模型响应 Gemini API有两种格式:
     * 1. 直接API格式: {"models": [{model对象}, ...]}
     * 2. Vertex AI格式: {"models" 或 "publisher_models": [{model对象}, ...]}
     */
    private fun parseGoogleModelResponse(jsonResponse: String): List<ModelOption> {
        val modelList = mutableListOf<ModelOption>()

        try {
            val jsonObject = JSONObject(jsonResponse)

            // 检查是否包含"models"字段（Gemini API格式）
            if (jsonObject.has("models")) {
                val modelsArray = jsonObject.getJSONArray("models")
                AppLogger.d(TAG, "解析Google Gemini API格式响应: 发现 ${modelsArray.length()} 个模型")

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val id = modelObj.getString("name").split("/").last()
                    val displayName = modelObj.optString("displayName", id)
                    val baseModelId = modelObj.optString("baseModelId", "")

                    // 只添加支持generateContent的模型，通过检查supportedGenerationMethods字段
                    val supportedMethods =
                            try {
                                if (modelObj.has("supportedGenerationMethods")) {
                                    val methods =
                                            modelObj.getJSONArray("supportedGenerationMethods")
                                    val methodsList = mutableListOf<String>()
                                    for (j in 0 until methods.length()) {
                                        methodsList.add(methods.getString(j))
                                    }
                                    methodsList
                                } else {
                                    listOf("generateContent") // 假设支持
                                }
                            } catch (e: Exception) {
                                listOf("generateContent") // 出错时默认支持
                            }

                    if (supportedMethods.contains("generateContent")) {
                        // 使用基本模型ID作为下拉列表中的选项
                        val finalId = if (baseModelId.isNotEmpty()) baseModelId else id
                        modelList.add(ModelOption(id = finalId, name = displayName))
                    }
                }
            }
            // 检查Vertex AI格式
            else if (jsonObject.has("models") || jsonObject.has("publisher_models")) {
                val modelsArray =
                        if (jsonObject.has("models")) {
                            jsonObject.getJSONArray("models")
                        } else {
                            jsonObject.getJSONArray("publisher_models")
                        }

                AppLogger.d(TAG, "解析Vertex AI格式响应: 发现 ${modelsArray.length()} 个模型")

                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val fullName = modelObj.getString("name")
                    val id = fullName.split("/").last()
                    val displayName = modelObj.optString("displayName", id)

                    // 过滤只添加Gemini模型
                    if (id.contains("gemini")) {
                        modelList.add(ModelOption(id = id, name = displayName))
                    }
                }
            } else {
                AppLogger.e(TAG, "Google响应格式错误: 未找到'models'字段")
                throw JSONException("响应格式错误: 未找到'models'字段")
            }
        } catch (e: JSONException) {
            AppLogger.e(TAG, "解析Google模型JSON失败: ${e.message}", e)
            throw e
        }

        return modelList.sortedBy { it.id }
    }

    /**
     * 获取本地MNN模型列表
     * 从固定目录读取已下载的MNN模型文件夹
     */
    suspend fun getMnnLocalModels(context: Context): Result<List<ModelOption>> {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Operit/models/mnn"
                )
                
                AppLogger.d(TAG, "读取MNN模型目录: ${modelsDir.absolutePath}")
                
                if (!modelsDir.exists()) {
                    AppLogger.w(TAG, "MNN模型目录不存在")
                    return@withContext Result.success(emptyList())
                }
                
                // 遍历所有模型文件夹
                val models = modelsDir.listFiles { file -> 
                    file.isDirectory
                }?.mapNotNull { folder ->
                    // 在文件夹中查找 llm.mnn 主文件
                    val mnnFile = File(folder, "llm.mnn")
                    val mnnWeightFile = File(folder, "llm.mnn.weight")
                    
                    if (mnnFile.exists()) {
                        // 计算文件夹总大小
                        val totalSize = folder.listFiles()?.sumOf { it.length() } ?: 0L
                        
                        AppLogger.d(TAG, "找到MNN模型: ${folder.name}, 主文件: ${mnnFile.exists()}, 权重文件: ${mnnWeightFile.exists()}, 总大小: ${formatFileSize(totalSize)}")
                        
                        ModelOption(
                            id = folder.name,  // 使用文件夹名称作为ID（与其他提供商保持一致）
                            name = "${folder.name} (${formatFileSize(totalSize)})"
                        )
                    } else {
                        AppLogger.w(TAG, "文件夹 ${folder.name} 中未找到 llm.mnn 文件")
                        null
                    }
                }?.sortedBy { it.name } ?: emptyList()
                
                AppLogger.d(TAG, "找到 ${models.size} 个可用的MNN模型")
                Result.success(models)
            } catch (e: Exception) {
                AppLogger.e(TAG, "读取MNN模型列表失败", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${sizeBytes / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
