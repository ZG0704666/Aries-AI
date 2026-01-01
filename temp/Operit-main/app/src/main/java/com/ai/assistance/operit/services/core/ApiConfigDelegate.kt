package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigDefaults
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 委托类，负责管理用户偏好配置和API密钥 */
class ApiConfigDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val onConfigChanged: (EnhancedAIService) -> Unit
) {
    companion object {
        private const val TAG = "ApiConfigDelegate"
    }

    // Preferences
    private val apiPreferences = ApiPreferences.getInstance(context)
    private val modelConfigManager = ModelConfigManager(context)
    private val functionalConfigManager = FunctionalConfigManager(context)

    // State flows
    private val _isConfigured = MutableStateFlow(true) // 默认已配置
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _enableAiPlanning = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AI_PLANNING)
    val enableAiPlanning: StateFlow<Boolean> = _enableAiPlanning.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(ApiPreferences.DEFAULT_KEEP_SCREEN_ON)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _enableThinkingMode = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_THINKING_MODE)
    val enableThinkingMode: StateFlow<Boolean> = _enableThinkingMode.asStateFlow()

    private val _enableThinkingGuidance =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_THINKING_GUIDANCE)
    val enableThinkingGuidance: StateFlow<Boolean> = _enableThinkingGuidance.asStateFlow()

    private val _enableMemoryQuery =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_MEMORY_QUERY)
    val enableMemoryQuery: StateFlow<Boolean> = _enableMemoryQuery.asStateFlow()

    private val _enableAutoRead =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AUTO_READ)
    val enableAutoRead: StateFlow<Boolean> = _enableAutoRead.asStateFlow()

    private val _contextLength = MutableStateFlow(ModelConfigDefaults.DEFAULT_CONTEXT_LENGTH)
    val baseContextLength: StateFlow<Float> = _contextLength.asStateFlow()
    private val _maxContextLength =
            MutableStateFlow(ModelConfigDefaults.DEFAULT_MAX_CONTEXT_LENGTH)
    val maxContextLengthSetting: StateFlow<Float> = _maxContextLength.asStateFlow()
    private val _enableMaxContextMode =
            MutableStateFlow(ModelConfigDefaults.DEFAULT_ENABLE_MAX_CONTEXT_MODE)
    val enableMaxContextMode: StateFlow<Boolean> = _enableMaxContextMode.asStateFlow()

    val contextLength: StateFlow<Float> = combine(
        _enableMaxContextMode,
        _contextLength,
        _maxContextLength
    ) { isMaxMode, normalLength, maxLength ->
        if (isMaxMode) maxLength else normalLength
    }.stateIn(
            coroutineScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            ModelConfigDefaults.DEFAULT_CONTEXT_LENGTH
    )

    private val _summaryTokenThreshold =
            MutableStateFlow(ModelConfigDefaults.DEFAULT_SUMMARY_TOKEN_THRESHOLD)
    val summaryTokenThreshold: StateFlow<Float> = _summaryTokenThreshold.asStateFlow()

    private val _enableSummary = MutableStateFlow(ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY)
    val enableSummary: StateFlow<Boolean> = _enableSummary.asStateFlow()

    private val _enableSummaryByMessageCount =
            MutableStateFlow(ModelConfigDefaults.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT)
    val enableSummaryByMessageCount: StateFlow<Boolean> = _enableSummaryByMessageCount.asStateFlow()

    private val _summaryMessageCountThreshold =
            MutableStateFlow(ModelConfigDefaults.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD)
    val summaryMessageCountThreshold: StateFlow<Int> = _summaryMessageCountThreshold.asStateFlow()

    private val _enableTools = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_TOOLS)
    val enableTools: StateFlow<Boolean> = _enableTools.asStateFlow()

    private val _disableStreamOutput = MutableStateFlow(ApiPreferences.DEFAULT_DISABLE_STREAM_OUTPUT)
    val disableStreamOutput: StateFlow<Boolean> = _disableStreamOutput.asStateFlow()

    // 为了兼容现有代码，添加API密钥状态流
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiEndpoint = MutableStateFlow("")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _modelName = MutableStateFlow("")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _apiProviderType = MutableStateFlow(ApiProviderType.DEEPSEEK)
    val apiProviderType: StateFlow<ApiProviderType> = _apiProviderType.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _activeConfigId =
            MutableStateFlow(FunctionalConfigManager.DEFAULT_CONFIG_ID)
    val activeConfigId: StateFlow<String> = _activeConfigId.asStateFlow()

    init {
        coroutineScope.launch {
            try {
                modelConfigManager.initializeIfNeeded()
                functionalConfigManager.initializeIfNeeded()

                functionalConfigManager.functionConfigMappingFlow.collect { mapping ->
                    val chatConfigId =
                            mapping[FunctionType.CHAT] ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
                    _activeConfigId.value = chatConfigId
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "初始化功能配置映射时出错", e)
            }
        }

        coroutineScope.launch {
            try {
                modelConfigManager.initializeIfNeeded()

                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                _activeConfigId
                        .flatMapLatest { configId ->
                            modelConfigManager.getModelConfigFlow(configId)
                        }
                        .collect { config ->
                            updateStateFromConfig(config)
                            _isInitialized.value = true
                        }
            } catch (e: Exception) {
                AppLogger.e(TAG, "收集模型配置时出错", e)
                _isInitialized.value = true
            }
        }

        // 加载用户偏好设置
        initializeSettingsCollection()

        // 异步创建AI服务实例，避免在主线程上执行阻塞操作
        coroutineScope.launch(Dispatchers.IO) {
            AppLogger.d(TAG, "开始在后台线程创建EnhancedAIService")
            val enhancedAiService = EnhancedAIService.getInstance(context)
            AppLogger.d(TAG, "EnhancedAIService创建完成")
            withContext(Dispatchers.Main) {
                onConfigChanged(enhancedAiService)
            }
        }
    }

    private fun updateStateFromConfig(config: ModelConfigData) {
        _apiKey.value = config.apiKey
        _apiEndpoint.value = config.apiEndpoint
        _modelName.value = config.modelName
        _apiProviderType.value = config.apiProviderType
        _contextLength.value = config.contextLength
        _maxContextLength.value = config.maxContextLength
        _enableMaxContextMode.value = config.enableMaxContextMode
        _summaryTokenThreshold.value = config.summaryTokenThreshold
        _enableSummary.value = config.enableSummary
        _enableSummaryByMessageCount.value = config.enableSummaryByMessageCount
        _summaryMessageCountThreshold.value = config.summaryMessageCountThreshold
    }

    private fun initializeSettingsCollection() {
        // Collect AI planning setting
        coroutineScope.launch {
            apiPreferences.enableAiPlanningFlow.collect { enableAiPlanningValue ->
                _enableAiPlanning.value = enableAiPlanningValue
            }
        }

        // Collect thinking mode setting
        coroutineScope.launch {
            apiPreferences.enableThinkingModeFlow.collect { enabled ->
                _enableThinkingMode.value = enabled
            }
        }

        // Collect thinking guidance setting
        coroutineScope.launch {
            apiPreferences.enableThinkingGuidanceFlow.collect { enabled ->
                _enableThinkingGuidance.value = enabled
            }
        }

        // Collect memory attachment setting
        coroutineScope.launch {
            apiPreferences.enableMemoryQueryFlow.collect { enabled ->
                _enableMemoryQuery.value = enabled
            }
        }

        // Collect auto read setting
        coroutineScope.launch {
            apiPreferences.enableAutoReadFlow.collect { enabled ->
                _enableAutoRead.value = enabled
            }
        }

        // Collect keep screen on setting
        coroutineScope.launch {
            apiPreferences.keepScreenOnFlow.collect { enabled ->
                _keepScreenOn.value = enabled
            }
        }

        // Collect enable tools setting
        coroutineScope.launch {
            apiPreferences.enableToolsFlow.collect { enabled ->
                _enableTools.value = enabled
            }
        }

        // Collect disable stream output setting
        coroutineScope.launch {
            apiPreferences.disableStreamOutputFlow.collect { disabled ->
                _disableStreamOutput.value = disabled
            }
        }
    }

    /**
     * 使用默认配置继续
     * @return 总是返回true，因为无需特定配置
     */
    fun useDefaultConfig(): Boolean {
        // 异步创建服务，避免阻塞
        coroutineScope.launch(Dispatchers.IO) {
            AppLogger.d(TAG, "使用默认配置初始化服务")
            val enhancedAiService = EnhancedAIService.getInstance(context)
            withContext(Dispatchers.Main) {
                // 通知ViewModel配置已更改
                onConfigChanged(enhancedAiService)
            }
        }
        return true
    }

    /** 更新API密钥 */
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    /** 更新API端点 */
    fun updateApiEndpoint(endpoint: String) {
        _apiEndpoint.value = endpoint
    }

    /** 更新模型名称 */
    fun updateModelName(modelName: String) {
        _modelName.value = modelName
    }

    /** 更新API提供商类型 */
    fun updateApiProviderType(providerType: ApiProviderType) {
        _apiProviderType.value = providerType
    }

    /** 保存API设置 */
    fun saveApiSettings() {
        coroutineScope.launch {
            try {
                val configId = _activeConfigId.value

                // 更新所有API相关配置
                modelConfigManager.updateModelConfig(
                        configId,
                        _apiKey.value,
                        _apiEndpoint.value,
                        _modelName.value,
                        _apiProviderType.value
                )

                AppLogger.d(TAG, "API配置已保存到ModelConfigManager")

                // 在IO线程上创建服务，避免阻塞
                val enhancedAiService = withContext(Dispatchers.IO) {
                    EnhancedAIService.getInstance(context)
                }

                // 通知ViewModel配置已更改
                onConfigChanged(enhancedAiService)

                // 更新已配置状态
                _isConfigured.value = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存API密钥失败: ${e.message}", e)
            }
        }
    }

    /** 切换AI计划功能 */
    fun toggleAiPlanning() {
        coroutineScope.launch {
            val newValue = !_enableAiPlanning.value
            apiPreferences.saveEnableAiPlanning(newValue)
            _enableAiPlanning.value = newValue
        }
    }

    /** 切换思考模式 */
    fun toggleThinkingMode() {
        coroutineScope.launch {
            val newValue = !_enableThinkingMode.value
            apiPreferences.saveEnableThinkingMode(newValue)
            _enableThinkingMode.value = newValue
        }
    }

    /** 切换思考引导 */
    fun toggleThinkingGuidance() {
        coroutineScope.launch {
            val newValue = !_enableThinkingGuidance.value
            apiPreferences.saveEnableThinkingGuidance(newValue)
            _enableThinkingGuidance.value = newValue
        }
    }

    /** 切换记忆附着 */
    fun toggleMemoryQuery() {
        coroutineScope.launch {
            val newValue = !_enableMemoryQuery.value
            apiPreferences.saveEnableMemoryQuery(newValue)
            _enableMemoryQuery.value = newValue
        }
    }

    /** 切换自动朗读 */
    fun toggleAutoRead() {
        coroutineScope.launch {
            val newValue = !_enableAutoRead.value
            apiPreferences.saveEnableAutoRead(newValue)
            _enableAutoRead.value = newValue
        }
    }

    /** 切换禁用流式输出 */
    fun toggleDisableStreamOutput() {
        coroutineScope.launch {
            val newValue = !_disableStreamOutput.value
            apiPreferences.saveDisableStreamOutput(newValue)
            _disableStreamOutput.value = newValue
        }
    }

    /** 更新上下文长度 */
    fun updateContextLength(length: Float) {
        coroutineScope.launch {
            _contextLength.value = length
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateContextSettings(
                    configId = configId,
                    contextLength = length,
                    maxContextLength = current.maxContextLength,
                    enableMaxContextMode = current.enableMaxContextMode
            )
        }
    }
    fun updateSummaryTokenThreshold(threshold: Float) {
        coroutineScope.launch {
            _summaryTokenThreshold.value = threshold
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateSummarySettings(
                    configId = configId,
                    enableSummary = current.enableSummary,
                    summaryTokenThreshold = threshold,
                    enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                    summaryMessageCountThreshold = current.summaryMessageCountThreshold
            )
        }
    }

    fun updateMaxContextLength(length: Float) {
        coroutineScope.launch {
            _maxContextLength.value = length
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateContextSettings(
                    configId = configId,
                    contextLength = current.contextLength,
                    maxContextLength = length,
                    enableMaxContextMode = current.enableMaxContextMode
            )
        }
    }

    fun toggleEnableMaxContextMode() {
        coroutineScope.launch {
            val newValue = !_enableMaxContextMode.value
            _enableMaxContextMode.value = newValue
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateContextSettings(
                    configId = configId,
                    contextLength = current.contextLength,
                    maxContextLength = current.maxContextLength,
                    enableMaxContextMode = newValue
            )
        }
    }
    /** 切换启用总结功能 */
    fun toggleEnableSummary() {
        coroutineScope.launch {
            val newValue = !_enableSummary.value
            _enableSummary.value = newValue
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateSummarySettings(
                    configId = configId,
                    enableSummary = newValue,
                    summaryTokenThreshold = current.summaryTokenThreshold,
                    enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                    summaryMessageCountThreshold = current.summaryMessageCountThreshold
            )
        }
    }

    /** 切换按消息数量启用总结 */
    fun toggleEnableSummaryByMessageCount() {
        coroutineScope.launch {
            val newValue = !_enableSummaryByMessageCount.value
            _enableSummaryByMessageCount.value = newValue
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateSummarySettings(
                    configId = configId,
                    enableSummary = current.enableSummary,
                    summaryTokenThreshold = current.summaryTokenThreshold,
                    enableSummaryByMessageCount = newValue,
                    summaryMessageCountThreshold = current.summaryMessageCountThreshold
            )
        }
    }

    /** 更新总结消息数量阈值 */
    fun updateSummaryMessageCountThreshold(threshold: Int) {
        coroutineScope.launch {
            _summaryMessageCountThreshold.value = threshold
            val configId = _activeConfigId.value
            val current = modelConfigManager.getModelConfig(configId) ?: return@launch
            modelConfigManager.updateSummarySettings(
                    configId = configId,
                    enableSummary = current.enableSummary,
                    summaryTokenThreshold = current.summaryTokenThreshold,
                    enableSummaryByMessageCount = current.enableSummaryByMessageCount,
                    summaryMessageCountThreshold = threshold
            )
        }
    }

    /** 切换工具启用/禁用 */
    fun toggleTools() {
        coroutineScope.launch {
            val newValue = !_enableTools.value
            apiPreferences.saveEnableTools(newValue)
            _enableTools.value = newValue
        }
    }
}
