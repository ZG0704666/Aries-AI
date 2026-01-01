package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ApiPreferences private constructor(private val context: Context) {

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

        fun getInstance(context: Context): ApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
        // 动态生成供应商:模型的Token键
        fun getTokenInputKey(providerModel: String) =
                intPreferencesKey("token_input_${providerModel.replace(":", "_")}")

        fun getTokenCachedInputKey(providerModel: String) =
                intPreferencesKey("token_cached_input_${providerModel.replace(":", "_")}")

        fun getTokenOutputKey(providerModel: String) =
                intPreferencesKey("token_output_${providerModel.replace(":", "_")}")

        // 模型定价键
        fun getModelInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_input_price_${providerModel.replace(":", "_")}")

        fun getModelCachedInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_cached_input_price_${providerModel.replace(":", "_")}")

        fun getModelOutputPriceKey(providerModel: String) =
                floatPreferencesKey("model_output_price_${providerModel.replace(":", "_")}")

        // 请求次数统计键
        fun getRequestCountKey(providerModel: String) =
                intPreferencesKey("request_count_${providerModel.replace(":", "_")}")

        // 计费方式键
        fun getBillingModeKey(providerModel: String) =
                stringPreferencesKey("billing_mode_${providerModel.replace(":", "_")}")

        // 按次计费价格键
        fun getPricePerRequestKey(providerModel: String) =
                floatPreferencesKey("price_per_request_${providerModel.replace(":", "_")}")

        val ENABLE_AI_PLANNING = booleanPreferencesKey("enable_ai_planning")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        // Default values
        const val DEFAULT_ENABLE_AI_PLANNING = false
        const val DEFAULT_KEEP_SCREEN_ON = true
        // Keys for Thinking Mode and Thinking Guidance
        val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")
        val ENABLE_THINKING_GUIDANCE = booleanPreferencesKey("enable_thinking_guidance")

        // Key for Memory Attachment
        val ENABLE_MEMORY_QUERY = booleanPreferencesKey("enable_memory_query")

        // Key for Auto Read
        val ENABLE_AUTO_READ = booleanPreferencesKey("enable_auto_read")

        // Key for Tools Enable/Disable
        val ENABLE_TOOLS = booleanPreferencesKey("enable_tools")

        // Key for Disable Stream Output
        val DISABLE_STREAM_OUTPUT = booleanPreferencesKey("disable_stream_output")

        // Custom Prompt Settings
        val CUSTOM_INTRO_PROMPT = stringPreferencesKey("custom_intro_prompt")
        
        // Custom System Prompt Template (Advanced Configuration)
        val CUSTOM_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("custom_system_prompt_template")

        // Keys for Truncation Settings (文件和结果截断配置)
        val MAX_FILE_SIZE_BYTES = intPreferencesKey("max_file_size_bytes")
        val PART_SIZE = intPreferencesKey("part_size")
        val MAX_TEXT_RESULT_LENGTH = intPreferencesKey("max_text_result_length")
        val MAX_HTTP_RESPONSE_LENGTH = intPreferencesKey("max_http_response_length")
        val MAX_IMAGE_HISTORY_USER_TURNS = intPreferencesKey("max_image_history_user_turns")
        val MAX_MEDIA_HISTORY_USER_TURNS = intPreferencesKey("max_media_history_user_turns")

        // Default values for Thinking Mode and Thinking Guidance
        const val DEFAULT_ENABLE_THINKING_MODE = false
        const val DEFAULT_ENABLE_THINKING_GUIDANCE = false

        // Default value for Memory Attachment
        const val DEFAULT_ENABLE_MEMORY_QUERY = true

        // Default value for Auto Read
        const val DEFAULT_ENABLE_AUTO_READ = false

        // Default value for Tools Enable/Disable
        const val DEFAULT_ENABLE_TOOLS = true

        // Default value for Disable Stream Output (default false, meaning stream is enabled by default)
        const val DEFAULT_DISABLE_STREAM_OUTPUT = false

        // Default values for custom prompts
        const val DEFAULT_INTRO_PROMPT = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
        
        // Default system prompt template (empty means use built-in template)
        const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = ""

        // Default values for Truncation Settings (文件和结果截断的默认值)
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 32000  // 文件读取操作的最大字节数限制
        const val DEFAULT_PART_SIZE = 200  // 分段读取文件时，每个部分的行数
        const val DEFAULT_MAX_TEXT_RESULT_LENGTH = 5000  // 通用文本结果的最大字符数限制
        const val DEFAULT_MAX_HTTP_RESPONSE_LENGTH = 5000000  // 网络请求响应的最大字符数限制（5MB）
        const val DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS = 2
        const val DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS = 1

        // 自定义参数存储键
        val CUSTOM_PARAMETERS = stringPreferencesKey("custom_parameters")

        // 自定义请求头存储键
        val CUSTOM_HEADERS = stringPreferencesKey("custom_headers")

        // 默认空的自定义参数列表
        const val DEFAULT_CUSTOM_PARAMETERS = "[]"
        const val DEFAULT_CUSTOM_HEADERS = "{}"

        // API 配置默认值
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "deepseek-chat"
        private const val ENCODED_API_KEY = "c2stNmI4NTYyMjUzNmFjNDhjMDgwYzUwNDhhYjVmNWQxYmQ="
        val DEFAULT_API_KEY: String by lazy { decodeApiKey(ENCODED_API_KEY) }

        private fun decodeApiKey(encodedKey: String): String {
            return try {
                android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP)
                    .toString(Charsets.UTF_8)
            } catch (e: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("ApiPreferences", "Failed to decode API key", e)
                ""
            }
        }
    }
    // Get AI Planning setting as Flow
    val enableAiPlanningFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[ENABLE_AI_PLANNING] ?: DEFAULT_ENABLE_AI_PLANNING
            }

    // Get Keep Screen On setting as Flow
    val keepScreenOnFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON
            }

    // Flow for Thinking Mode
    val enableThinkingModeFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_MODE] ?: DEFAULT_ENABLE_THINKING_MODE
        }

    // Flow for Thinking Guidance
    val enableThinkingGuidanceFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_GUIDANCE] ?: DEFAULT_ENABLE_THINKING_GUIDANCE
            }

    // Flow for Memory Attachment
    val enableMemoryQueryFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_MEMORY_QUERY] ?: DEFAULT_ENABLE_MEMORY_QUERY
        }

    // Flow for Auto Read
    val enableAutoReadFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_AUTO_READ] ?: DEFAULT_ENABLE_AUTO_READ
        }

    // Flow for Tools Enable/Disable
    val enableToolsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_TOOLS] ?: DEFAULT_ENABLE_TOOLS
        }

    // Flow for Disable Stream Output
    val disableStreamOutputFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[DISABLE_STREAM_OUTPUT] ?: DEFAULT_DISABLE_STREAM_OUTPUT
        }

    // Custom Prompt Flows
    val customIntroPromptFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_INTRO_PROMPT] ?: DEFAULT_INTRO_PROMPT
            }



    // Custom System Prompt Template Flow
    val customSystemPromptTemplateFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE
            }

    // Flow for Custom Headers
    val customHeadersFlow: Flow<String> =
        context.apiDataStore.data.map { preferences ->
            preferences[CUSTOM_HEADERS] ?: DEFAULT_CUSTOM_HEADERS
        }

    // Flows for Truncation Settings (文件和结果截断配置的Flow)
    val maxFileSizeBytesFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_FILE_SIZE_BYTES] ?: DEFAULT_MAX_FILE_SIZE_BYTES
        }

    val partSizeFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[PART_SIZE] ?: DEFAULT_PART_SIZE
        }

    val maxTextResultLengthFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_TEXT_RESULT_LENGTH] ?: DEFAULT_MAX_TEXT_RESULT_LENGTH
        }

    val maxHttpResponseLengthFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_HTTP_RESPONSE_LENGTH] ?: DEFAULT_MAX_HTTP_RESPONSE_LENGTH
        }

    val maxImageHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
        }

    val maxMediaHistoryUserTurnsFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }

    // Save AI Planning setting
    suspend fun saveEnableAiPlanning(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_AI_PLANNING] = isEnabled }
    }

    // Save Keep Screen On setting
    suspend fun saveKeepScreenOn(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[KEEP_SCREEN_ON] = isEnabled }
    }

    // Save Thinking Mode setting
    suspend fun saveEnableThinkingMode(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_MODE] = isEnabled }
    }

    // Save Thinking Guidance setting
    suspend fun saveEnableThinkingGuidance(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_GUIDANCE] = isEnabled }
    }

    // Save Memory Attachment setting
    suspend fun saveEnableMemoryQuery(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_MEMORY_QUERY] = isEnabled }
    }

    // Save Auto Read setting
    suspend fun saveEnableAutoRead(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_AUTO_READ] = isEnabled }
    }

    // Save Tools Enable/Disable setting
    suspend fun saveEnableTools(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_TOOLS] = isEnabled }
    }

    // Save Disable Stream Output setting
    suspend fun saveDisableStreamOutput(isDisabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[DISABLE_STREAM_OUTPUT] = isDisabled }
    }

    // 保存自定义请求头
    suspend fun saveCustomHeaders(headersJson: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_HEADERS] = headersJson
        }
    }

    // 读取自定义请求头
    suspend fun getCustomHeaders(): String {
        val preferences = context.apiDataStore.data.first()
        return preferences[CUSTOM_HEADERS] ?: DEFAULT_CUSTOM_HEADERS
    }

    /**
     * 更新指定供应商:模型的token计数
     * @param providerModel 供应商:模型标识符，格式如"DEEPSEEK:deepseek-chat"
     * @param inputTokens 新增的输入token
     * @param outputTokens 新增的输出token
     * @param cachedInputTokens 新增的缓存命中token
     */
    suspend fun updateTokensForProviderModel(
            providerModel: String,
            inputTokens: Int,
            outputTokens: Int,
            cachedInputTokens: Int = 0
    ) {
        context.apiDataStore.edit { preferences ->
            val inputKey = getTokenInputKey(providerModel)
            val cachedInputKey = getTokenCachedInputKey(providerModel)
            val outputKey = getTokenOutputKey(providerModel)

            val currentInputTokens = preferences[inputKey] ?: 0
            val currentCachedInputTokens = preferences[cachedInputKey] ?: 0
            val currentOutputTokens = preferences[outputKey] ?: 0

            preferences[inputKey] = currentInputTokens + inputTokens
            preferences[cachedInputKey] = currentCachedInputTokens + cachedInputTokens
            preferences[outputKey] = currentOutputTokens + outputTokens
        }
    }

    /**
     * 获取指定供应商:模型的输入token数量
     */
    suspend fun getInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的缓存输入token数量
     */
    suspend fun getCachedInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenCachedInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的输出token数量
     */
    suspend fun getOutputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenOutputKey(providerModel)] ?: 0
    }

    /**
     * 获取所有供应商:模型的token统计
     * @return Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>
     */
    suspend fun getAllProviderModelTokens(): Map<String, Triple<Int, Int, Int>> {
        val preferences = context.apiDataStore.data.first()
        val result = mutableMapOf<String, Triple<Int, Int, Int>>()
        
        // 遍历所有preferences，查找token相关的key
        preferences.asMap().forEach { (key, value) ->
            val keyName = key.name
            if (keyName.startsWith("token_input_")) {
                val providerModel = keyName.removePrefix("token_input_").replace("_", ":")
                val inputTokens = value as? Int ?: 0
                val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                    result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                }
            }
        }
        
        return result
    }

    /**
     * 获取所有供应商:模型的token统计的Flow
     * @return Flow<Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>>
     */
    val allProviderModelTokensFlow: Flow<Map<String, Triple<Int, Int, Int>>> =
        context.apiDataStore.data.map { preferences ->
            val result = mutableMapOf<String, Triple<Int, Int, Int>>()
            
            // 遍历所有preferences，查找token相关的key
            preferences.asMap().forEach { (key, value) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_")) {
                    val providerModel = keyName.removePrefix("token_input_").replace("_", ":")
                    val inputTokens = value as? Int ?: 0
                    val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                    val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                    if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                        result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                    }
                }
            }
            
            result
        }

    // Save custom prompts
    suspend fun saveCustomPrompts(introPrompt: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = introPrompt
        }
    }

    // Save custom system prompt template
    suspend fun saveCustomSystemPromptTemplate(template: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    // Reset custom prompts to default values
    suspend fun resetCustomPrompts() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = DEFAULT_INTRO_PROMPT
        }
    }

    // Reset custom system prompt template to default
    suspend fun resetCustomSystemPromptTemplate() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = DEFAULT_SYSTEM_PROMPT_TEMPLATE
        }
    }

    // 重置所有供应商:模型的token计数
    suspend fun resetAllProviderModelTokenCounts() {
        context.apiDataStore.edit { preferences ->
            val keysToRemove = mutableListOf<Preferences.Key<*>>()
            preferences.asMap().forEach { (key, _) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_") || keyName.startsWith("token_output_") || keyName.startsWith("token_cached_input_") || keyName.startsWith("request_count_")) {
                    keysToRemove.add(key)
                }
            }
            keysToRemove.forEach { key ->
                preferences.remove(key)
            }
        }
    }

    // 重置指定供应商:模型的token计数
    suspend fun resetProviderModelTokenCounts(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            preferences[getTokenInputKey(providerModel)] = 0
            preferences[getTokenCachedInputKey(providerModel)] = 0
            preferences[getTokenOutputKey(providerModel)] = 0
            preferences[getRequestCountKey(providerModel)] = 0
        }
    }

    // 获取模型输入价格（每百万tokens的美元价格）
    suspend fun getModelInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型缓存输入价格（每百万tokens的美元价格）
    suspend fun getModelCachedInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelCachedInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型输出价格（每百万tokens的美元价格）
    suspend fun getModelOutputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelOutputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 设置模型输入价格（每百万tokens的美元价格）
    suspend fun setModelInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型缓存输入价格（每百万tokens的美元价格）
    suspend fun setModelCachedInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelCachedInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型输出价格（每百万tokens的美元价格）
    suspend fun setModelOutputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelOutputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // ===== Request Count Statistics 请求次数统计相关方法 =====

    /**
     * 增加指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符，格式如"DEEPSEEK:deepseek-chat"
     */
    suspend fun incrementRequestCountForProviderModel(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            val countKey = getRequestCountKey(providerModel)
            val currentCount = preferences[countKey] ?: 0
            preferences[countKey] = currentCount + 1
        }
    }

    /**
     * 获取指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符
     * @return 请求次数
     */
    suspend fun getRequestCountForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getRequestCountKey(providerModel)] ?: 0
    }

    /**
     * 获取所有供应商:模型的请求次数统计
     * @return Map<供应商:模型, 请求次数>
     */
    suspend fun getAllProviderModelRequestCounts(): Map<String, Int> {
        val preferences = context.apiDataStore.data.first()
        val result = mutableMapOf<String, Int>()
        
        // 遍历所有preferences，查找请求次数相关的key
        preferences.asMap().forEach { (key, value) ->
            val keyName = key.name
            if (keyName.startsWith("request_count_")) {
                val providerModel = keyName.removePrefix("request_count_").replace("_", ":")
                val count = value as? Int ?: 0
                if (count > 0) {
                    result[providerModel] = count
                }
            }
        }
        
        return result
    }

    /**
     * 重置指定供应商:模型的请求次数
     * @param providerModel 供应商:模型标识符
     */
    suspend fun resetProviderModelRequestCount(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            preferences[getRequestCountKey(providerModel)] = 0
        }
    }

    // ===== Billing Mode 计费方式相关方法 =====

    /**
     * 获取指定供应商:模型的计费方式
     * @param providerModel 供应商:模型标识符
     * @return 计费方式，默认为TOKEN
     */
    suspend fun getBillingModeForProviderModel(providerModel: String): com.ai.assistance.operit.data.model.BillingMode {
        val preferences = context.apiDataStore.data.first()
        val modeString = preferences[getBillingModeKey(providerModel)]
        return com.ai.assistance.operit.data.model.BillingMode.fromString(modeString)
    }

    /**
     * 设置指定供应商:模型的计费方式
     * @param providerModel 供应商:模型标识符
     * @param mode 计费方式
     */
    suspend fun setBillingModeForProviderModel(providerModel: String, mode: com.ai.assistance.operit.data.model.BillingMode) {
        context.apiDataStore.edit { preferences ->
            preferences[getBillingModeKey(providerModel)] = mode.name
        }
    }

    // ===== Price Per Request 按次计费价格相关方法 =====

    /**
     * 获取指定供应商:模型的按次计费价格（人民币）
     * @param providerModel 供应商:模型标识符
     * @return 每次请求的价格，默认为0.01元
     */
    suspend fun getPricePerRequestForProviderModel(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getPricePerRequestKey(providerModel)]?.toDouble() ?: 0.01
    }

    /**
     * 设置指定供应商:模型的按次计费价格（人民币）
     * @param providerModel 供应商:模型标识符
     * @param price 每次请求的价格
     */
    suspend fun setPricePerRequestForProviderModel(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getPricePerRequestKey(providerModel)] = price.toFloat()
        }
    }

    // ===== Truncation Settings 截断设置相关方法 =====

    // 保存文件读取最大字节数
    suspend fun saveMaxFileSizeBytes(sizeBytes: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_FILE_SIZE_BYTES] = sizeBytes
        }
    }

    // 保存分段读取的行数
    suspend fun savePartSize(size: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[PART_SIZE] = size
        }
    }

    // 保存文本结果最大长度
    suspend fun saveMaxTextResultLength(length: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_TEXT_RESULT_LENGTH] = length
        }
    }

    // 保存HTTP响应最大长度
    suspend fun saveMaxHttpResponseLength(length: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_HTTP_RESPONSE_LENGTH] = length
        }
    }

    suspend fun saveMaxImageHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = turns
        }
    }

    suspend fun saveMaxMediaHistoryUserTurns(turns: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = turns
        }
    }

    // 获取文件读取最大字节数
    suspend fun getMaxFileSizeBytes(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_FILE_SIZE_BYTES] ?: DEFAULT_MAX_FILE_SIZE_BYTES
    }

    // 获取分段读取的行数
    suspend fun getPartSize(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[PART_SIZE] ?: DEFAULT_PART_SIZE
    }

    // 获取文本结果最大长度
    suspend fun getMaxTextResultLength(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_TEXT_RESULT_LENGTH] ?: DEFAULT_MAX_TEXT_RESULT_LENGTH
    }

    // 获取HTTP响应最大长度
    suspend fun getMaxHttpResponseLength(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_HTTP_RESPONSE_LENGTH] ?: DEFAULT_MAX_HTTP_RESPONSE_LENGTH
    }

    suspend fun getMaxImageHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_IMAGE_HISTORY_USER_TURNS] ?: DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
    }

    suspend fun getMaxMediaHistoryUserTurns(): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[MAX_MEDIA_HISTORY_USER_TURNS] ?: DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
    }

    // 重置截断设置为默认值
    suspend fun resetTruncationSettings() {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_FILE_SIZE_BYTES] = DEFAULT_MAX_FILE_SIZE_BYTES
            preferences[PART_SIZE] = DEFAULT_PART_SIZE
            preferences[MAX_TEXT_RESULT_LENGTH] = DEFAULT_MAX_TEXT_RESULT_LENGTH
            preferences[MAX_HTTP_RESPONSE_LENGTH] = DEFAULT_MAX_HTTP_RESPONSE_LENGTH
            preferences[MAX_IMAGE_HISTORY_USER_TURNS] = DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS
            preferences[MAX_MEDIA_HISTORY_USER_TURNS] = DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS
        }
    }

    // 批量保存所有截断设置
    suspend fun saveTruncationSettings(
        maxFileSizeBytes: Int,
        partSize: Int,
        maxTextResultLength: Int,
        maxHttpResponseLength: Int
    ) {
        context.apiDataStore.edit { preferences ->
            preferences[MAX_FILE_SIZE_BYTES] = maxFileSizeBytes
            preferences[PART_SIZE] = partSize
            preferences[MAX_TEXT_RESULT_LENGTH] = maxTextResultLength
            preferences[MAX_HTTP_RESPONSE_LENGTH] = maxHttpResponseLength
        }
    }
}