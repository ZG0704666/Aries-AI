package com.ai.assistance.operit.ui.features.settings.sections

import android.annotation.SuppressLint
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.LocationUtils
import kotlinx.coroutines.launch

val TAG = "ModelApiSettings"

@Composable
@SuppressLint("MissingPermission")
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        showNotification: (String) -> Unit,
        onSaveRequested: (() -> Unit) -> Unit = {},
        navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 区域告警可见性
    var showRegionWarning by remember { mutableStateOf(false) }

    // 获取每个提供商的默认模型名称
    fun getDefaultModelName(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "gpt-4o"
            ApiProviderType.OPENAI_GENERIC -> ""
            ApiProviderType.ANTHROPIC -> "claude-3-opus-20240229"
            ApiProviderType.GOOGLE -> "gemini-2.0-flash"
            ApiProviderType.GEMINI_GENERIC -> "gemini-2.0-flash"
            ApiProviderType.DEEPSEEK -> "deepseek-chat"
            ApiProviderType.BAIDU -> "ernie-bot-4"
            ApiProviderType.ALIYUN -> "qwen-max"
            ApiProviderType.XUNFEI -> "spark3.5"
            ApiProviderType.ZHIPU -> "glm-4.5"
            ApiProviderType.BAICHUAN -> "baichuan4"
            ApiProviderType.MOONSHOT -> "moonshot-v1-128k"
            ApiProviderType.MISTRAL -> "codestral-latest"
            ApiProviderType.SILICONFLOW -> "yi-1.5-34b"
            ApiProviderType.OPENROUTER -> "google/gemini-pro"
            ApiProviderType.INFINIAI -> "infini-mini"
            ApiProviderType.ALIPAY_BAILING -> "Ling-1T"
            ApiProviderType.DOUBAO -> "Doubao-pro-4k"
            ApiProviderType.LMSTUDIO -> "meta-llama-3.1-8b-instruct"
            ApiProviderType.MNN -> ""
            ApiProviderType.PPINFRA -> "gpt-4o-mini"
            ApiProviderType.OTHER -> ""
        }
    }

    // 检查当前模型名称是否是某个提供商的默认值
    fun isDefaultModelName(modelName: String): Boolean {
        return ApiProviderType.values().any { getDefaultModelName(it) == modelName }
    }

    // API编辑状态
    var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
    var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
    var modelNameInput by remember(config.id) { mutableStateOf(config.modelName) }
    var selectedApiProvider by remember(config.id) { mutableStateOf(config.apiProviderType) }
    
    // MNN特定配置状态
    var mnnForwardTypeInput by remember(config.id) { mutableStateOf(config.mnnForwardType) }
    var mnnThreadCountInput by remember(config.id) { mutableStateOf(config.mnnThreadCount.toString()) }
    
    // 图片处理配置状态
    var enableDirectImageProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectImageProcessing) }

    var enableDirectAudioProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectAudioProcessing) }

    var enableDirectVideoProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectVideoProcessing) }
    
    // Google Search Grounding 配置状态 (仅Gemini)
    var enableGoogleSearchInput by remember(config.id) { mutableStateOf(config.enableGoogleSearch) }
    
    // Tool Call配置状态
    var enableToolCallInput by remember(config.id) { mutableStateOf(config.enableToolCall) }
    
    // DeepSeek推理模式配置状态 (仅DeepSeek)
    var enableDeepseekReasoningInput by remember(config.id) { mutableStateOf(config.enableDeepseekReasoning) }

    // 保存设置的通用函数
    val saveSettings: () -> Unit = {
        scope.launch {
            // 允许用户自定义模型名称，即使使用默认API密钥
            val modelToSave = modelNameInput

            AppLogger.d(
                    TAG,
                    "保存API设置: apiKey=${apiKeyInput.take(5)}..., endpoint=$apiEndpointInput, model=$modelToSave, providerType=${selectedApiProvider.name}"
            )

            // 更新配置
            configManager.updateModelConfig(
                    configId = config.id,
                    apiKey = apiKeyInput,
                    apiEndpoint = apiEndpointInput,
                    modelName = modelToSave,
                    apiProviderType = selectedApiProvider,
                    mnnForwardType = mnnForwardTypeInput,
                    mnnThreadCount = mnnThreadCountInput.toIntOrNull() ?: 4
            )

            // 更新图片直接处理配置
            configManager.updateDirectImageProcessing(
                    configId = config.id,
                    enableDirectImageProcessing = enableDirectImageProcessingInput
            )

            configManager.updateDirectAudioProcessing(
                configId = config.id,
                enableDirectAudioProcessing = enableDirectAudioProcessingInput
            )

            configManager.updateDirectVideoProcessing(
                configId = config.id,
                enableDirectVideoProcessing = enableDirectVideoProcessingInput
            )
            
            // 更新 Google Search Grounding 配置 (仅Gemini)
            configManager.updateGoogleSearch(
                    configId = config.id,
                    enableGoogleSearch = enableGoogleSearchInput
            )
            
            // 更新 Tool Call 配置
            configManager.updateToolCall(
                    configId = config.id,
                    enableToolCall = enableToolCallInput
            )
            
            // 更新 DeepSeek推理模式配置 (仅DeepSeek)
            configManager.updateDeepseekReasoning(
                    configId = config.id,
                    enableDeepseekReasoning = enableDeepseekReasoningInput
            )

            // 刷新所有AI服务实例，确保使用最新配置
            EnhancedAIService.refreshAllServices(
                    configManager.appContext
            )

            AppLogger.d(TAG, "API设置保存完成并刷新服务")
            showNotification(context.getString(R.string.api_settings_saved))
        }
    }

    // 将保存函数暴露给父组件
    LaunchedEffect(Unit) {
        onSaveRequested(saveSettings)
    }

    // 根据API提供商获取默认的API端点URL
    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
            ApiProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            ApiProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/models"
            // Gemini通用交给用户自定义端点
            ApiProviderType.GEMINI_GENERIC -> ""
            ApiProviderType.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            ApiProviderType.BAIDU ->
                    "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            ApiProviderType.ALIYUN ->
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            ApiProviderType.XUNFEI -> "https://spark-api-open.xf-yun.com/v2/chat/completions"
            ApiProviderType.ZHIPU ->
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            ApiProviderType.BAICHUAN -> "https://api.baichuan-ai.com/v1/chat/completions"
            ApiProviderType.MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions"
            ApiProviderType.MISTRAL -> "https://codestral.mistral.ai/v1/chat/completions"
            ApiProviderType.SILICONFLOW -> "https://api.siliconflow.cn/v1/chat/completions"
            ApiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            ApiProviderType.INFINIAI -> "https://cloud.infini-ai.com/maas/v1/chat/completions"
            ApiProviderType.ALIPAY_BAILING -> "https://api.tbox.cn/api/llm/v1/chat/completions"
            ApiProviderType.DOUBAO -> "https://ark.cn-beijing.volces.com/api/v3/chat/completions"
            ApiProviderType.LMSTUDIO -> "http://localhost:1234/v1/chat/completions"
            ApiProviderType.MNN -> "" // MNN本地推理不需要endpoint
            ApiProviderType.PPINFRA -> "https://api.ppinfra.com/openai/v1/chat/completions"
            ApiProviderType.OPENAI_GENERIC -> ""
            ApiProviderType.OTHER -> ""
        }
    }

    // 添加一个函数检查当前API端点是否为某个提供商的默认端点
    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return ApiProviderType.values().any { getDefaultApiEndpoint(it) == endpoint }
    }

    // 当API提供商改变时更新端点
    LaunchedEffect(selectedApiProvider) {
        AppLogger.d("ModelApiSettingsSection", "API提供商改变")
        if (selectedApiProvider == ApiProviderType.OPENAI || selectedApiProvider == ApiProviderType.OPENAI_GENERIC || selectedApiProvider == ApiProviderType.GOOGLE
            || selectedApiProvider == ApiProviderType.GEMINI_GENERIC
            || selectedApiProvider == ApiProviderType.ANTHROPIC || selectedApiProvider == ApiProviderType.MISTRAL) {
            val inChina = LocationUtils.isDeviceInMainlandChina(context)
            showRegionWarning = inChina
            if (inChina) {
                AppLogger.d("ModelApiSettingsSection", "检测到位于中国大陆")
                showNotification(context.getString(R.string.overseas_provider_warning))
            } else {
                AppLogger.d("ModelApiSettingsSection", "检测到位于海外")
            }
        } else {
            showRegionWarning = false
        }

        // 非通用供应商（有强制端点的）切换时，强制重置为该供应商默认端点，避免从“其他供应商”等通用配置带入自定义值
        val isGenericProviderForEndpoint =
            selectedApiProvider == ApiProviderType.OPENAI_GENERIC ||
            selectedApiProvider == ApiProviderType.OTHER ||
            selectedApiProvider == ApiProviderType.GEMINI_GENERIC

        if (isGenericProviderForEndpoint) {
            // 通用供应商仍保留原逻辑：只有在为空或当前就是某个默认端点时才写入默认值
            if (apiEndpointInput.isEmpty() || isDefaultApiEndpoint(apiEndpointInput)) {
                apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider)
            }
        } else {
            // 有强制内容的供应商：直接覆盖为该供应商默认端点，实现清空+填充+锁定的效果
            apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider)
        }
    }

    // 模型列表状态
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }
    var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    // 检查是否使用默认API密钥（仅用于UI显示）
    val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY

    // 移除了强制锁定模型名称的逻辑，允许用户自由修改

    Card(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSectionHeader(
                    icon = Icons.Default.Api,
                    title = stringResource(R.string.api_settings)
            )

            var showApiProviderDialog by remember { mutableStateOf(false) }

            SettingsSelectorRow(
                    title = stringResource(R.string.api_provider),
                    subtitle = stringResource(R.string.select_api_provider),
                    value = getProviderDisplayName(selectedApiProvider, context),
                    onClick = { showApiProviderDialog = true }
            )

            if (showApiProviderDialog) {
                ApiProviderDialog(
                        onDismissRequest = { showApiProviderDialog = false },
                        onProviderSelected = { provider ->
                            selectedApiProvider = provider

                            // 对有默认模型名的供应商，视为“有强制内容”：切换时总是重置为该供应商默认模型名
                            val hasForcedModelName = getDefaultModelName(provider).isNotEmpty()
                            if (hasForcedModelName) {
                                modelNameInput = getDefaultModelName(provider)
                            } else if (modelNameInput.isEmpty() || isDefaultModelName(modelNameInput)) {
                                // 通用/无默认模型名的供应商仍沿用旧逻辑
                                modelNameInput = getDefaultModelName(provider)
                            }

                            showApiProviderDialog = false
                        }
                )
            }

            AnimatedVisibility(visible = showRegionWarning) {
                SettingsInfoBanner(text = stringResource(R.string.overseas_provider_warning))
            }

            // 仅 OpenAI通用(OPENAI_GENERIC)、其他供应商(OTHER) 和 Gemini通用(GEMINI_GENERIC) 允许自定义端点
            val isGenericProvider =
                selectedApiProvider == ApiProviderType.OPENAI_GENERIC ||
                selectedApiProvider == ApiProviderType.OTHER ||
                selectedApiProvider == ApiProviderType.GEMINI_GENERIC

            if (selectedApiProvider == ApiProviderType.MNN) {
                MnnSettingsBlock(
                        mnnForwardTypeInput = mnnForwardTypeInput,
                        onForwardTypeSelected = { mnnForwardTypeInput = it },
                        mnnThreadCountInput = mnnThreadCountInput,
                        onThreadCountChange = { input ->
                            if (input.isEmpty() || input.toIntOrNull() != null) {
                                mnnThreadCountInput = input
                            }
                        },
                        navigateToMnnModelDownload = navigateToMnnModelDownload
                )
            } else {
                SettingsTextField(
                        title = stringResource(R.string.api_endpoint),
                        subtitle = stringResource(R.string.api_endpoint_placeholder),
                    value = apiEndpointInput,
                    onValueChange = { 
                        apiEndpointInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                    },
                        enabled = isGenericProvider,
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                        )
                )

            val completedEndpoint = EndpointCompleter.completeEndpoint(apiEndpointInput)
            if (completedEndpoint != apiEndpointInput) {
                Text(
                    text = stringResource(R.string.actual_request_url, completedEndpoint),
                    style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.endpoint_completion_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val apiKeyInteractionSource = remember { MutableInteractionSource() }
                val isApiKeyFocused by apiKeyInteractionSource.collectIsFocusedAsState()

                SettingsTextField(
                        title = stringResource(R.string.api_key),
                        subtitle =
                                if (isUsingDefaultApiKey)
                                        stringResource(R.string.api_key_placeholder_default)
                                else
                                        stringResource(R.string.api_key_placeholder_custom),
                        value = if (isUsingDefaultApiKey) "" else apiKeyInput,
                        onValueChange = {
                            val filteredInput = it.replace("\n", "").replace("\r", "").replace(" ", "")
                            apiKeyInput = filteredInput
                        },
                        keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                        ),
                        visualTransformation = if (isApiKeyFocused || apiKeyInput.isEmpty()) VisualTransformation.None else ApiKeyVisualTransformation(),
                        interactionSource = apiKeyInteractionSource
                )
            }

            val isMnnProvider = selectedApiProvider == ApiProviderType.MNN
            SettingsTextField(
                    title = stringResource(R.string.model_name),
                    subtitle = if (isMnnProvider) stringResource(R.string.mnn_select_downloaded_model) else stringResource(
                            R.string.model_name_placeholder) + " (可用逗号分隔多个模型)",
                        value = modelNameInput,
                        onValueChange = {
                        if (!isMnnProvider && !isUsingDefaultApiKey) {
                                modelNameInput = it.replace("\n", "").replace("\r", "")
                            }
                        },
                    enabled = if (isMnnProvider) false else !isUsingDefaultApiKey,
                    trailingContent = {
                IconButton(
                        onClick = {
                                    if (isMnnProvider) {
                                        AppLogger.d(TAG, "获取MNN本地模型列表")
                                        val gettingModelsText =
                                                context.getString(R.string.getting_models_list)
                                        val modelsListSuccessText =
                                                context.getString(R.string.models_list_success)
                                        showNotification(gettingModelsText)

                                        scope.launch {
                                            isLoadingModels = true
                                            modelLoadError = null

                                            try {
                                                val result = ModelListFetcher.getMnnLocalModels(context)
                                                if (result.isSuccess) {
                                                    val models = result.getOrThrow()
                                                    AppLogger.d(TAG, "MNN模型列表获取成功，共 ${models.size} 个模型")
                                                    modelsList = models
                                                    showModelsDialog = true
                                                    showNotification(modelsListSuccessText.format(models.size))
                                                } else {
                                                    val errorMsg =
                                                            result.exceptionOrNull()?.message
                                                                    ?: context.getString(R.string.unknown_error)
                                                    AppLogger.e(TAG, "MNN模型列表获取失败: $errorMsg")
                                                    modelLoadError =
                                                            context.getString(
                                                                    R.string.get_models_list_failed,
                                                                    errorMsg
                                                            )
                                                    showNotification(modelLoadError!!)
                                                }
                                            } catch (e: Exception) {
                                                AppLogger.e(TAG, "获取MNN模型列表发生异常", e)
                                                modelLoadError =
                                                        context.getString(
                                                                R.string.get_models_list_failed,
                                                                e.message ?: ""
                                                        )
                                                showNotification(modelLoadError!!)
                                            } finally {
                                                isLoadingModels = false
                                            }
                                        }
                                    } else {
                            AppLogger.d(
                                    TAG,
                                    "模型列表按钮被点击 - API端点: $apiEndpointInput, API类型: ${selectedApiProvider.name}"
                            )
                            val gettingModelsText = context.getString(R.string.getting_models_list)
                            val unknownErrorText = context.getString(R.string.unknown_error)
                            val getModelsFailedText = context.getString(R.string.get_models_list_failed)
                            val defaultConfigNoModelsText = context.getString(R.string.default_config_no_models_list)
                            val fillEndpointKeyText = context.getString(R.string.fill_endpoint_and_key)
                            val modelsListSuccessText = context.getString(R.string.models_list_success)
                            val refreshModelsFailedText = context.getString(R.string.refresh_models_failed)
                            
                            showNotification(gettingModelsText)

                            scope.launch {
                                if (apiEndpointInput.isNotBlank() &&
                                                apiKeyInput.isNotBlank() &&
                                                !isUsingDefaultApiKey
                                ) {
                                    isLoadingModels = true
                                    modelLoadError = null
                                    AppLogger.d(
                                            TAG,
                                            "开始获取模型列表: 端点=$apiEndpointInput, API类型=${selectedApiProvider.name}"
                                    )

                                    try {
                                        val result =
                                                ModelListFetcher.getModelsList(
                                                        apiKeyInput,
                                                        apiEndpointInput,
                                                        selectedApiProvider
                                                )
                                        if (result.isSuccess) {
                                            val models = result.getOrThrow()
                                            AppLogger.d(TAG, "模型列表获取成功，共 ${models.size} 个模型")
                                            modelsList = models
                                            showModelsDialog = true
                                            showNotification(modelsListSuccessText.format(models.size))
                                        } else {
                                            val errorMsg =
                                                    result.exceptionOrNull()?.message ?: unknownErrorText
                                            AppLogger.e(TAG, "模型列表获取失败: $errorMsg")
                                            modelLoadError = getModelsFailedText.format(errorMsg)
                                            showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "获取模型列表发生异常", e)
                                        modelLoadError = getModelsFailedText.format(e.message ?: "")
                                        showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                    } finally {
                                        isLoadingModels = false
                                        AppLogger.d(TAG, "模型列表获取流程完成")
                                    }
                                } else if (isUsingDefaultApiKey) {
                                    AppLogger.d(TAG, "使用默认配置，不获取模型列表")
                                    showNotification(defaultConfigNoModelsText)
                                } else {
                                    AppLogger.d(TAG, "API端点或密钥为空")
                                    showNotification(fillEndpointKeyText)
                                }
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        colors =
                                IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                ),
                                enabled = if (isMnnProvider) true else !isUsingDefaultApiKey
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = stringResource(R.string.get_models_list),
                                tint =
                                                if (!isMnnProvider && isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
            )


            if (selectedApiProvider != ApiProviderType.MNN) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_direct_image_processing),
                        subtitle = stringResource(R.string.enable_direct_image_processing_desc),
                            checked = enableDirectImageProcessingInput,
                            onCheckedChange = { enableDirectImageProcessingInput = it }
                    )
            }

            if (selectedApiProvider != ApiProviderType.MNN) {
                SettingsSwitchRow(
                    title = stringResource(R.string.enable_direct_audio_processing),
                    subtitle = stringResource(R.string.enable_direct_audio_processing_desc),
                    checked = enableDirectAudioProcessingInput,
                    onCheckedChange = { enableDirectAudioProcessingInput = it }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.enable_direct_video_processing),
                    subtitle = stringResource(R.string.enable_direct_video_processing_desc),
                    checked = enableDirectVideoProcessingInput,
                    onCheckedChange = { enableDirectVideoProcessingInput = it }
                )
            }
            
            // Google Search Grounding 开关 (仅Gemini支持)
            if (selectedApiProvider == ApiProviderType.GOOGLE ||
                selectedApiProvider == ApiProviderType.GEMINI_GENERIC) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_google_search),
                        subtitle = stringResource(R.string.enable_google_search_desc),
                            checked = enableGoogleSearchInput,
                            onCheckedChange = { enableGoogleSearchInput = it }
                    )
            }
            
            // Tool Call 开关 (非MNN模型)
            if (selectedApiProvider != ApiProviderType.MNN) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_tool_call),
                        subtitle = stringResource(R.string.enable_tool_call_desc),
                            checked = enableToolCallInput,
                            onCheckedChange = { enableToolCallInput = it }
                    )
            }
            
            // DeepSeek推理模式开关 (仅DeepSeek)
            if (selectedApiProvider == ApiProviderType.DEEPSEEK) {
                SettingsSwitchRow(
                        title = stringResource(R.string.enable_deepseek_reasoning),
                        subtitle = stringResource(R.string.enable_deepseek_reasoning_desc),
                            checked = enableDeepseekReasoningInput,
                            onCheckedChange = { enableDeepseekReasoningInput = it }
                    )
            }

            Button(
                    onClick = { saveSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.save_api_settings))
            }
        }
    }

    // 模型列表对话框
    if (showModelsDialog) {
        var searchQuery by remember { mutableStateOf("") }
        // 维护已选中的模型集合
        val selectedModels = remember {
            mutableStateOf(
                modelNameInput.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
        val filteredModelsList =
                remember(searchQuery, modelsList) {
                    if (searchQuery.isEmpty()) modelsList
                    else modelsList.filter { it.id.contains(searchQuery, ignoreCase = true) }
                }

        Dialog(onDismissRequest = { showModelsDialog = false }) {
            Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题栏
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                stringResource(R.string.available_models_list),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )

                        FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        if (apiEndpointInput.isNotBlank() &&
                                                        apiKeyInput.isNotBlank() &&
                                                        !isUsingDefaultApiKey
                                        ) {
                                            isLoadingModels = true
                                            try {
                                                val result =
                                                        ModelListFetcher.getModelsList(
                                                                apiKeyInput,
                                                                apiEndpointInput,
                                                                selectedApiProvider
                                                        )
                                                if (result.isSuccess) {
                                                    modelsList = result.getOrThrow()
                                                } else {
                                                    val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                                                    modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                    showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                                }
                                            } catch (e: Exception) {
                                                val errorMsg = e.message ?: context.getString(R.string.unknown_error)
                                                modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                            } finally {
                                                isLoadingModels = false
                                            }
                                        }
                                    }
                                },
                                colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                contentColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_models_list),
                                        modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 搜索框 - 用普通的OutlinedTextField替代实验性的SearchBar
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_models), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search),
                                        modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.clear),
                                                modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier =
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(48.dp),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor =
                                                    MaterialTheme.colorScheme.outline,
                                            focusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                            unfocusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    // 模型列表
                    if (modelsList.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = modelLoadError ?: stringResource(R.string.no_models_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredModelsList.size) { index ->
                                val model = filteredModelsList[index]
                                val isSelected = selectedModels.value.contains(model.id)
                                
                                // 使用带Checkbox的Row实现多选
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable {
                                                            // 切换选中状态
                                                            val newSelection = selectedModels.value.toMutableSet()
                                                            if (isSelected) {
                                                                newSelection.remove(model.id)
                                                            } else {
                                                                newSelection.add(model.id)
                                                            }
                                                            selectedModels.value = newSelection
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                val newSelection = selectedModels.value.toMutableSet()
                                                if (checked) {
                                                    newSelection.add(model.id)
                                                } else {
                                                    newSelection.remove(model.id)
                                                }
                                                selectedModels.value = newSelection
                                            },
                                            colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) 
                                                    MaterialTheme.colorScheme.primary 
                                                else 
                                                    MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (index < filteredModelsList.size - 1) {
                                    HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部信息
                    if (filteredModelsList.isNotEmpty()) {
                        Text(
                                text =
                                        stringResource(R.string.models_displayed, filteredModelsList.size) +
                                                (if (searchQuery.isNotEmpty()) stringResource(R.string.models_displayed_filtered) else "") +
                                                (if (selectedModels.value.isNotEmpty()) " • ${selectedModels.value.size} 已选中" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                                fontSize = 12.sp
                        )
                    }

                    // 底部按钮
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        FilledTonalButton(
                                onClick = { showModelsDialog = false },
                                modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.close), fontSize = 14.sp) }
                        
                        Button(
                                onClick = {
                                    // 将选中的模型用逗号连接
                                    val orderedSelection = modelsList.map { it.id }
                                        .filter { selectedModels.value.contains(it) }
                                    modelNameInput = orderedSelection.joinToString(",")
                                    if (selectedApiProvider == ApiProviderType.MNN) {
                                        AppLogger.d(TAG, "选择MNN模型: $modelNameInput")
                                    }
                                    showModelsDialog = false
                                },
                                modifier = Modifier.height(36.dp),
                                enabled = selectedModels.value.isNotEmpty()
                        ) { 
                            Text(
                                stringResource(R.string.confirm_action) + 
                                    if (selectedModels.value.isNotEmpty()) " (${selectedModels.value.size})" else "",
                                fontSize = 14.sp
                            ) 
                        }
                    }
                }
            }
        }
    }
}

private fun getProviderDisplayName(provider: ApiProviderType, context: android.content.Context): String {
    return when (provider) {
        ApiProviderType.OPENAI -> context.getString(R.string.provider_openai)
        ApiProviderType.OPENAI_GENERIC -> context.getString(R.string.provider_openai_generic)
        ApiProviderType.ANTHROPIC -> context.getString(R.string.provider_anthropic)
        ApiProviderType.GOOGLE -> context.getString(R.string.provider_google)
        ApiProviderType.GEMINI_GENERIC -> context.getString(R.string.provider_gemini_generic)
        ApiProviderType.BAIDU -> context.getString(R.string.provider_baidu)
        ApiProviderType.ALIYUN -> context.getString(R.string.provider_aliyun)
        ApiProviderType.XUNFEI -> context.getString(R.string.provider_xunfei)
        ApiProviderType.ZHIPU -> context.getString(R.string.provider_zhipu)
        ApiProviderType.BAICHUAN -> context.getString(R.string.provider_baichuan)
        ApiProviderType.MOONSHOT -> context.getString(R.string.provider_moonshot)
        ApiProviderType.DEEPSEEK -> context.getString(R.string.provider_deepseek)
        ApiProviderType.MISTRAL -> context.getString(R.string.provider_mistral)
        ApiProviderType.SILICONFLOW -> context.getString(R.string.provider_siliconflow)
        ApiProviderType.OPENROUTER -> context.getString(R.string.provider_openrouter)
        ApiProviderType.INFINIAI -> context.getString(R.string.provider_infiniai)
        ApiProviderType.ALIPAY_BAILING -> context.getString(R.string.provider_alipay_bailing)
        ApiProviderType.DOUBAO -> context.getString(R.string.provider_doubao)
        ApiProviderType.LMSTUDIO -> context.getString(R.string.provider_lmstudio)
        ApiProviderType.MNN -> context.getString(R.string.provider_mnn)
        ApiProviderType.PPINFRA -> context.getString(R.string.provider_ppinfra)
        ApiProviderType.OTHER -> context.getString(R.string.provider_other)
    }
}


@Composable
internal fun SettingsSectionHeader(
        icon: ImageVector,
        title: String,
        subtitle: String? = null
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun SettingsInfoBanner(text: String) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
internal fun SettingsTextField(
        title: String,
        subtitle: String? = null,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String = "",
        enabled: Boolean = true,
        singleLine: Boolean = true,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        keyboardActions: KeyboardActions = KeyboardActions.Default,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        interactionSource: MutableInteractionSource? = null,
        valueFilter: ((String) -> String)? = null,
        trailingContent: @Composable (() -> Unit)? = null,
        unitText: String? = null
) {
    val focusManager = LocalFocusManager.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = backgroundColor,
                        tonalElevation = 0.dp
                ) {
                    BasicTextField(
                            value = value,
                            onValueChange = { newValue ->
                                if (!enabled) return@BasicTextField
                                val filtered = valueFilter?.invoke(newValue) ?: newValue
                                onValueChange(filtered)
                            },
                            singleLine = singleLine,
                            enabled = enabled,
                            keyboardOptions = keyboardOptions,
                            keyboardActions = keyboardActions,
                            visualTransformation = visualTransformation,
                            interactionSource = resolvedInteractionSource,
                            textStyle =
                                    TextStyle(
                                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                    ),
                            decorationBox = { innerTextField ->
                                Row(
                                        modifier =
                                                Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (value.isEmpty()) {
                                            Text(
                                                    text = placeholder,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                    unitText?.let {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                    )
                }
                trailingContent?.invoke()
            }
        }
    }
}

@Composable
private fun SettingsSelectorRow(
        title: String,
        subtitle: String,
        value: String,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClick() },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                            .padding(end = 8.dp)
                            .weight(0.5f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean = true
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun MnnSettingsBlock(
        mnnForwardTypeInput: Int,
        onForwardTypeSelected: (Int) -> Unit,
        mnnThreadCountInput: String,
        onThreadCountChange: (String) -> Unit,
        navigateToMnnModelDownload: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsInfoBanner(text = stringResource(R.string.mnn_local_model_tip))

        navigateToMnnModelDownload?.let { navigate ->
            Button(
                    onClick = navigate,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
            ) {
                Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.mnn_model_download))
            }
        }

        var showForwardTypeDialog by remember { mutableStateOf(false) }

        SettingsSelectorRow(
                title = stringResource(R.string.mnn_forward_type),
                subtitle = stringResource(R.string.select),
                value = forwardTypeName(mnnForwardTypeInput),
                onClick = { showForwardTypeDialog = true }
        )

        if (showForwardTypeDialog) {
            Dialog(onDismissRequest = { showForwardTypeDialog = false }) {
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                text = stringResource(R.string.mnn_forward_type),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                        )
                        listOf(
                                0 to "CPU",
                                3 to "OpenCL",
                                4 to "Auto",
                                6 to "OpenGL",
                                7 to "Vulkan"
                        ).forEach { (type, name) ->
                            Surface(
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                onForwardTypeSelected(type)
                                                showForwardTypeDialog = false
                                            },
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                            if (mnnForwardTypeInput == type)
                                                    MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                        text = name,
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsTextField(
                title = stringResource(R.string.mnn_thread_count),
                value = mnnThreadCountInput,
                onValueChange = onThreadCountChange,
                placeholder = "4",
                keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                ),
                valueFilter = { input -> input.filter { it.isDigit() } }
        )
    }
}

private fun forwardTypeName(type: Int): String {
    return when (type) {
        0 -> "CPU"
        3 -> "OpenCL"
        4 -> "Auto"
        6 -> "OpenGL"
        7 -> "Vulkan"
        else -> "CPU"
    }
}

@Composable
private fun ApiProviderDialog(
        onDismissRequest: () -> Unit,
        onProviderSelected: (ApiProviderType) -> Unit
) {
    val providers = ApiProviderType.values()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredProviders = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            providers.toList()
        } else {
            providers.filter { provider ->
                getProviderDisplayName(provider, context).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题和搜索框
                Text(
                        stringResource(R.string.select_api_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 搜索框
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_providers), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear),
                                            modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                )

                // 提供商列表
                androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                ) {
                    items(filteredProviders.size) { index ->
                        val provider = filteredProviders[index]
                        // 美化的提供商选项
                        Surface(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onProviderSelected(provider) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 提供商图标（使用圆形背景色）
                                Box(
                                        modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                        getProviderColor(provider),
                                                        CircleShape
                                                ),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            text = getProviderDisplayName(provider, context).first().toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                        text = getProviderDisplayName(provider, context),
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                
                // 底部按钮
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

// 为不同提供商生成不同的颜色
@Composable
private fun getProviderColor(provider: ApiProviderType): androidx.compose.ui.graphics.Color {
    return when (provider) {
        ApiProviderType.OPENAI -> MaterialTheme.colorScheme.primary
        ApiProviderType.OPENAI_GENERIC -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        ApiProviderType.ANTHROPIC -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.GOOGLE -> MaterialTheme.colorScheme.secondary
        ApiProviderType.GEMINI_GENERIC -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.BAIDU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ApiProviderType.ALIYUN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        ApiProviderType.XUNFEI -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        ApiProviderType.ZHIPU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ApiProviderType.BAICHUAN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        ApiProviderType.MOONSHOT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        ApiProviderType.DEEPSEEK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ApiProviderType.MISTRAL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
        ApiProviderType.SILICONFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        ApiProviderType.OPENROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        ApiProviderType.INFINIAI -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ApiProviderType.ALIPAY_BAILING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        ApiProviderType.DOUBAO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
        ApiProviderType.LMSTUDIO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        ApiProviderType.MNN -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ApiProviderType.PPINFRA -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.OTHER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
}
