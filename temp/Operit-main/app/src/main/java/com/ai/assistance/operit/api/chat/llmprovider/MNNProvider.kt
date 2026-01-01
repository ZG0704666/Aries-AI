package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.mnn.MNNLlmSession
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * MNN本地推理引擎的AI服务实现
 * 使用 MNN 官方 LLM 引擎进行实际推理
 */
class MNNProvider(
    private val context: Context,
    private val modelName: String,  // 模型文件夹名称（如 "Qwen2-1.5B-Instruct-MNN"）
    private val forwardType: Int,
    private val threadCount: Int,
    private val providerType: ApiProviderType = ApiProviderType.MNN,
    private val enableToolCall: Boolean = false // 是否启用Tool Call接口（本地推理暂未实现）
) : AIService {

    companion object {
        private const val TAG = "MNNProvider"
        
        /**
         * 根据模型名称获取模型目录路径
         */
        fun getModelDir(_context: Context, modelName: String): String {
            val modelsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/mnn"
            )
            return File(modelsDir, modelName).absolutePath
        }
    }

    // MNN LLM Session 实例
    private var llmSession: MNNLlmSession? = null

    // Token计数
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0
    private var _cachedInputTokenCount = 0

    @Volatile
    private var isCancelled = false

    override val inputTokenCount: Int
        get() = _inputTokenCount

    override val outputTokenCount: Int
        get() = _outputTokenCount

    override val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
        _cachedInputTokenCount = 0
    }

    override fun cancelStreaming() {
        isCancelled = true
        
        // 调用底层 native 取消方法，立即中断推理
        llmSession?.cancel()
        
        AppLogger.d(TAG, "已取消MNN推理（已通知底层中断）")
    }

    /**
     * 初始化 MNN LLM 模型
     */
    private suspend fun initModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (llmSession == null) {
                AppLogger.d(TAG, "初始化MNN LLM模型: $modelName")
                
                // 获取模型目录
                val modelDir = getModelDir(context, modelName)
                AppLogger.d(TAG, "模型目录: $modelDir")
                
                // 检查目录是否存在
                val modelDirFile = File(modelDir)
                if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                    return@withContext Result.failure(
                        Exception("模型目录不存在: $modelDir\n请确保模型已下载")
                    )
                }

                // 检查配置文件是否存在
                val configFile = File(modelDir, "llm_config.json")
                if (!configFile.exists()) {
                    return@withContext Result.failure(
                        Exception("配置文件不存在: ${configFile.absolutePath}\n请确保模型完整下载")
                    )
                }

                // 将 forwardType 映射到 backend_type 字符串
                val backendType = when (forwardType) {
                    0 -> "cpu"
                    3 -> "opencl"
                    4 -> "auto"
                    6 -> "opengl"
                    7 -> "vulkan"
                    else -> {
                        AppLogger.w(TAG, "未知的 forwardType: $forwardType，使用默认 CPU")
                        "cpu"
                    }
                }
                
                AppLogger.d(TAG, "创建MNN LLM会话，后端: $backendType, 线程数: $threadCount")
                
                // Vulkan/OpenCL 后端需要 normal 内存模式以避免 Clone error
                // CPU 后端可以使用 low 内存模式
                val memoryMode = if (backendType in listOf("vulkan", "opencl", "opengl")) {
                    "normal"
                } else {
                    "low"
                }
                
                AppLogger.d(TAG, "内存模式: $memoryMode (后端: $backendType)")
                
                // 创建缓存目录（用于存放 mnn_cachefile.bin 等临时文件）
                val cacheDir = File(context.cacheDir, "mnn_cache")
                if (!cacheDir.exists()) {
                    val created = cacheDir.mkdirs()
                    AppLogger.d(TAG, "创建MNN缓存目录: $created")
                }
                AppLogger.d(TAG, "MNN缓存目录: ${cacheDir.absolutePath}")
                AppLogger.d(TAG, "缓存目录存在: ${cacheDir.exists()}, 可写: ${cacheDir.canWrite()}")
                
                // 创建 LLM Session（配置必须在创建时传入！）
                llmSession = MNNLlmSession.create(
                    modelDir = modelDir,
                    backendType = backendType,
                    threadNum = threadCount,
                    precision = "low",      // 使用低精度以提升性能
                    memory = memoryMode,    // 根据后端选择内存模式
                    tmpPath = cacheDir.absolutePath  // 指定缓存目录
                )
                
                if (llmSession == null) {
                    return@withContext Result.failure(
                        Exception("无法创建MNN LLM会话，请检查模型文件和配置")
                    )
                }

                AppLogger.i(TAG, "MNN LLM模型初始化成功，后端: $backendType")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "初始化MNN LLM模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM Session 的实际 tokenizer 计算 Token 数
     */
    private suspend fun countTokens(text: String): Int = withContext(Dispatchers.IO) {
        try {
            val session = llmSession ?: return@withContext estimateTokens(text)
            session.tokenize(text).size
        } catch (e: Exception) {
            AppLogger.w(TAG, "Token计数失败，使用估算", e)
            estimateTokens(text)
        }
    }

    /**
     * 估算Token数（备用方法，假设平均4个字符为1个token）
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * 构建完整的提示词（包含历史记录）
     */
    private fun buildPrompt(
        message: String,
        chatHistory: List<Pair<String, String>>
    ): String {
        val promptBuilder = StringBuilder()
        
        // 添加历史记录
        for ((role, content) in chatHistory) {
            when (role.lowercase()) {
                "user" -> promptBuilder.append("用户: $content\n")
                "assistant" -> promptBuilder.append("助手: $content\n")
                "system" -> promptBuilder.append("系统: $content\n")
                else -> promptBuilder.append("$role: $content\n")
            }
        }
        
        // 添加当前消息
        promptBuilder.append("用户: $message\n")
        promptBuilder.append("助手: ")
        
        return promptBuilder.toString()
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
        isCancelled = false

        try {
            // 初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                emit("错误: ${initResult.exceptionOrNull()?.message ?: "未知错误"}")
                return@stream
            }

            val session = llmSession ?: run {
                emit("错误: LLM会话未初始化")
                return@stream
            }

            // 设置 thinking 模式（仅对支持的模型有效，如 Qwen3）
            try {
                session.setThinkingMode(enableThinking)
                AppLogger.d(TAG, "Thinking mode set to: $enableThinking")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to set thinking mode (model may not support it): ${e.message}")
            }

            // 应用模型参数（采样参数）
            applyModelParameters(session, modelParameters)

            // 如果消息为空，不添加到历史记录
            if (message.isBlank()) {
                AppLogger.d(TAG, "消息为空，跳过处理")
                return@stream
            }

            // 构建历史记录（添加当前消息）
            val fullHistory = chatHistory.toMutableList().apply {
                add("user" to message)
            }
            
            // 估算输入token计数（用于显示）
            val estimatedPrompt = buildPrompt(message, chatHistory)
            _inputTokenCount = countTokens(estimatedPrompt)
            onTokensUpdated(_inputTokenCount, 0, 0)

            AppLogger.d(TAG, "开始MNN LLM推理，历史消息数: ${fullHistory.size}, thinking模式: $enableThinking")

            // 从模型参数中获取 max_tokens（如果有的话）
            val maxTokens = modelParameters
                .find { it.name == "max_tokens" }
                ?.let { (it.currentValue as? Number)?.toInt() }
                ?: -1  // -1 表示使用默认值

            // 使用流式生成（传递历史记录，让LLM内部应用chat template）
            var outputTokenCount = 0
            val success = session.generateStream(fullHistory, maxTokens) { token ->
                if (isCancelled) {
                    false  // 停止生成
                } else {
                    // 更新输出token计数（估算）
                    outputTokenCount += 1
                    _outputTokenCount = outputTokenCount
                    
                    // 发送 token
                    runBlocking { emit(token) }
                    
                    // 更新token统计（在IO线程中异步执行）
                    kotlin.runCatching {
                        kotlinx.coroutines.runBlocking {
                            onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                        }
                    }
                    
                    true  // 继续生成
                }
            }

            if (!success && !isCancelled) {
                emit("\n\n[推理过程出现错误]")
            }

            AppLogger.i(TAG, "MNN LLM推理完成，输出token数: $_outputTokenCount")

        } catch (e: Exception) {
            AppLogger.e(TAG, "发送消息时出错", e)
            emit("错误: ${e.message}")
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查模型名称
            if (modelName.isEmpty()) {
                return@withContext Result.failure(Exception("未配置模型名称"))
            }

            // 获取模型目录
            val modelDir = getModelDir(context, modelName)
            val modelDirFile = File(modelDir)
            
            if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                return@withContext Result.failure(
                    Exception("模型目录不存在: $modelDir\n请先下载模型")
                )
            }

            // 计算模型总大小
            val totalSize = modelDirFile.listFiles()?.sumOf { it.length() } ?: 0L
            
            // 检查关键文件是否存在
            val modelFile = File(modelDir, "llm.mnn")
            val weightFile = File(modelDir, "llm.mnn.weight")
            val configFile = File(modelDir, "llm_config.json")
            val tokenizerFile = File(modelDir, "tokenizer.txt")
            
            val fileStatus = buildString {
                appendLine("文件状态:")
                appendLine("- llm.mnn: ${if (modelFile.exists()) "✓" else "✗"}")
                appendLine("- llm.mnn.weight: ${if (weightFile.exists()) "✓" else "✗"}")
                appendLine("- llm_config.json: ${if (configFile.exists()) "✓" else "✗"}")
                appendLine("- tokenizer.txt: ${if (tokenizerFile.exists()) "✓" else "✗"}")
            }

            // 尝试初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                return@withContext Result.failure(
                    initResult.exceptionOrNull() ?: Exception("模型初始化失败")
                )
            }

            Result.success("MNN LLM模型连接成功！\n\n模型: $modelName\n目录: $modelDir\n总大小: ${formatFileSize(totalSize)}\n\n$fileStatus")
        } catch (e: Exception) {
            AppLogger.e(TAG, "测试连接失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 应用模型参数到 MNN Session
     * MNN 支持的采样参数：temperature, topP, topK, minP, penalty, tfsZ, typical, nGram 等
     * 
     * 参数映射说明：
     * - temperature: 温度参数，控制输出随机性
     * - top_p -> topP: Top-P 采样（核采样）
     * - top_k -> topK: Top-K 采样
     * - min_p -> minP: Min-P 采样
     * - repetition_penalty/presence_penalty/frequency_penalty -> penalty: 重复惩罚
     * - 自定义参数: 直接传递（如 tfsZ, typical, nGram 等）
     */
    private fun applyModelParameters(session: MNNLlmSession, parameters: List<ModelParameter<*>>) {
        try {
            // 构建配置 JSON（只包含启用的参数）
            val configMap = mutableMapOf<String, Any>()
            
            parameters.filter { it.isEnabled }.forEach { param ->
                when (param.apiName.lowercase()) {
                    "temperature" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["temperature"] = it
                        }
                    }
                    "top_p", "topp" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["topP"] = it  // MNN 使用 topP 而不是 top_p
                        }
                    }
                    "top_k", "topk" -> {
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["topK"] = it  // MNN 使用 topK 而不是 top_k
                        }
                    }
                    "min_p", "minp" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["minP"] = it
                        }
                    }
                    "presence_penalty", "frequency_penalty", "repetition_penalty" -> {
                        // MNN 使用统一的 penalty 参数
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["penalty"] = it
                        }
                    }
                    "max_tokens", "max_new_tokens" -> {
                        // max_tokens 在 generateStream 中单独处理，这里不设置
                        // 但 MNN 也支持 maxNewTokens 配置
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["max_new_tokens"] = it
                        }
                    }
                    // MNN 高级采样参数
                    "tfsz", "tfs_z" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["tfsZ"] = it
                        }
                    }
                    "typical" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["typical"] = it
                        }
                    }
                    "n_gram", "ngram" -> {
                        (param.currentValue as? Number)?.toInt()?.let { 
                            configMap["n_gram"] = it
                        }
                    }
                    "ngram_factor" -> {
                        (param.currentValue as? Number)?.toFloat()?.let { 
                            configMap["ngram_factor"] = it
                        }
                    }
                    else -> {
                        // 对于自定义参数，尝试直接传递
                        if (param.isCustom) {
                            when (param.valueType) {
                                ParameterValueType.INT -> {
                                    (param.currentValue as? Number)?.toInt()?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.FLOAT -> {
                                    (param.currentValue as? Number)?.toFloat()?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.BOOLEAN -> {
                                    (param.currentValue as? Boolean)?.let {
                                        configMap[param.apiName] = it
                                    }
                                }
                                ParameterValueType.STRING -> {
                                    configMap[param.apiName] = param.currentValue.toString()
                                }
                                ParameterValueType.OBJECT -> {
                                    val raw = param.currentValue.toString().trim()
                                    val parsed: Any? = try {
                                        when {
                                            raw.startsWith("{") -> JSONObject(raw)
                                            raw.startsWith("[") -> JSONArray(raw)
                                            else -> null
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.w(TAG, "自定义OBJECT参数解析失败: ${param.apiName}", e)
                                        null
                                    }
                                    if (parsed != null) {
                                        configMap[param.apiName] = parsed
                                    } else {
                                        // 解析失败时回退为字符串，避免崩溃
                                        configMap[param.apiName] = raw
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (configMap.isNotEmpty()) {
                // 将 Map 转换为 JSON 字符串
                val configJson = buildString {
                    append("{")
                    configMap.entries.forEachIndexed { index, entry ->
                        if (index > 0) append(",")
                        append("\"${entry.key}\":")
                        when (val value = entry.value) {
                            is JSONObject -> append(value.toString())
                            is JSONArray -> append(value.toString())
                            is String -> append("\"$value\"")
                            is Number -> append(value)
                            is Boolean -> append(value)
                            else -> append("\"$value\"")
                        }
                    }
                    append("}")
                }
                
                AppLogger.d(TAG, "应用模型参数: $configJson")
                val success = session.setConfig(configJson)
                if (!success) {
                    AppLogger.w(TAG, "部分模型参数设置失败")
                }
            } else {
                AppLogger.d(TAG, "没有启用的模型参数需要应用")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "应用模型参数时出错", e)
        }
    }
    
    /**
     * 应用硬件后端配置（backend_type 和 thread_num）
     * 将用户在 UI 中选择的 forwardType 和 threadCount 应用到 MNN Session
     * 
     * forwardType 映射:
     * - 0 -> "cpu"
     * - 3 -> "opencl" 
     * - 4 -> "auto"
     * - 6 -> "opengl"
     * - 7 -> "vulkan"
     */
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.2f KB", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override suspend fun calculateInputTokens(
        message: String,
        chatHistory: List<Pair<String, String>>,
        availableTools: List<ToolPrompt>?
    ): Int {
        // MNN本地模型暂不支持工具调用，忽略availableTools参数
        val prompt = buildPrompt(message, chatHistory)
        return countTokens(prompt)
    }

    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // MNN使用本地模型，从固定目录读取已下载的模型
        return ModelListFetcher.getMnnLocalModels(context)
    }

    /**
     * 释放资源
     * 释放MNN模型占用的native内存和相关资源
     */
    override fun release() {
        try {
            llmSession?.release()
            llmSession = null
            AppLogger.d(TAG, "MNN LLM资源已释放")
        } catch (e: Exception) {
            AppLogger.e(TAG, "释放资源时出错", e)
        }
    }
}

