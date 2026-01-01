package com.ai.assistance.operit.ui.features.toolbox.screens.autoglm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoGlmOneClickToolScreen(
    navController: NavController,
    onNavigateToModelConfig: () -> Unit
) {
    CustomScaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            AutoGlmOneClickScreen(onNavigateToModelConfig = onNavigateToModelConfig)
        }
    }
}

@Composable
private fun AutoGlmOneClickScreen(
    onNavigateToModelConfig: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelConfigManager = remember { ModelConfigManager(context) }
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }

    var apiKeyInput by remember { mutableStateOf("") }
    var isConfiguring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var advancedEndpoint by remember { mutableStateOf("") }
    var advancedModelName by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    fun startConfigure() {
        scope.launch {
            val apiKey = apiKeyInput.trim()
            statusMessage = null
            errorMessage = null

            if (apiKey.isEmpty()) {
                errorMessage = context.getString(R.string.autoglm_error_empty_api_key)
                return@launch
            }

            isConfiguring = true
            try {
                modelConfigManager.initializeIfNeeded()
                functionalConfigManager.initializeIfNeeded()

                val configName = context.getString(R.string.autoglm_config_name)
                val summaries = modelConfigManager.getAllConfigSummaries()
                val existing = summaries.find { it.name == configName }

                val useAdvanced = isAdvancedExpanded &&
                    advancedEndpoint.trim().isNotEmpty() &&
                    advancedModelName.trim().isNotEmpty()

                val endpoint = if (useAdvanced) {
                    advancedEndpoint.trim()
                } else {
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                }

                val modelName = if (useAdvanced) {
                    advancedModelName.trim()
                } else {
                    "autoglm-phone"
                }

                val providerType = if (useAdvanced) {
                    ApiProviderType.OPENAI_GENERIC
                } else {
                    ApiProviderType.ZHIPU
                }

                val configId = if (existing != null) {
                    modelConfigManager.updateModelConfig(
                        configId = existing.id,
                        apiKey = apiKey,
                        apiEndpoint = endpoint,
                        modelName = modelName,
                        apiProviderType = providerType
                    ).id
                } else {
                    val newId = modelConfigManager.createConfig(configName)
                    modelConfigManager.updateModelConfig(
                        configId = newId,
                        apiKey = apiKey,
                        apiEndpoint = endpoint,
                        modelName = modelName,
                        apiProviderType = providerType
                    ).id
                }

                modelConfigManager.updateDirectImageProcessing(configId, true)

                // 绑定到 UI_CONTROLLER 功能
                functionalConfigManager.setConfigForFunction(
                    FunctionType.UI_CONTROLLER,
                    configId,
                    0
                )
                EnhancedAIService.refreshServiceForFunction(
                    context,
                    FunctionType.UI_CONTROLLER
                )

                // 自动应用 AutoGLM 推荐参数
                try {
                    val parameters: List<ModelParameter<*>> =
                        modelConfigManager.getModelParametersForConfig(configId).map { param ->
                            when (param.id) {
                                "temperature" -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (param as ModelParameter<Float>).copy(
                                        currentValue = 0.0f,
                                        isEnabled = true
                                    ) as ModelParameter<*>
                                }

                                "top_p" -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (param as ModelParameter<Float>).copy(
                                        currentValue = 0.85f,
                                        isEnabled = true
                                    ) as ModelParameter<*>
                                }

                                "frequency_penalty" -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (param as ModelParameter<Float>).copy(
                                        currentValue = 0.2f,
                                        isEnabled = true
                                    ) as ModelParameter<*>
                                }

                                else -> param
                            }
                        }

                    modelConfigManager.updateParameters(configId, parameters)
                } catch (e: Exception) {
                    AppLogger.e("AutoGlmOneClick", "Failed to apply AutoGLM parameters", e)
                }

                // 切换 AutoGLM 工具包
                try {
                    val imported = packageManager.getImportedPackages()
                    if (imported.contains("Automatic_ui_base")) {
                        packageManager.removePackage("Automatic_ui_base")
                    }
                    if (!packageManager.isPackageImported("Automatic_ui_subagent")) {
                        packageManager.importPackage("Automatic_ui_subagent")
                    }
                } catch (e: Exception) {
                    AppLogger.e("AutoGlmOneClick", "Failed to update packages", e)
                }

                statusMessage = context.getString(R.string.autoglm_status_success)
            } catch (e: Exception) {
                errorMessage = context.getString(
                    R.string.autoglm_status_error,
                    e.message ?: "unknown"
                )
            } finally {
                isConfiguring = false
            }
        }
    }

    fun restoreOriginalAutomation() {
        scope.launch {
            statusMessage = null
            errorMessage = null
            isConfiguring = true
            try {
                val imported = packageManager.getImportedPackages()
                if (!imported.contains("Automatic_ui_base")) {
                    packageManager.importPackage("Automatic_ui_base")
                }
                if (packageManager.isPackageImported("Automatic_ui_subagent")) {
                    packageManager.removePackage("Automatic_ui_subagent")
                }

                statusMessage = "已恢复为软件原本的自动化逻辑"
            } catch (e: Exception) {
                AppLogger.e("AutoGlmOneClick", "Failed to restore base packages", e)
                errorMessage = context.getString(
                    R.string.autoglm_status_error,
                    e.message ?: "unknown"
                )
            } finally {
                isConfiguring = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AutoMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.autoglm_one_click_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = stringResource(R.string.autoglm_one_click_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "step1 配置基本对话模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "不要把autoglm模型作为对话主模型",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "注意：如果已经配置过就不需要管了，这里切勿改成 autoglm 等小模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateToModelConfig) {
                    Text("前往模型配置")
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text( 
                    text = "step2 访问智谱官网，获取 API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val apiKeyUrl = "https://open.bigmodel.cn/usercenter/apikeys"
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apiKeyUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.autoglm_open_apikey_center))
                    }
                    Text(
                        text = stringResource(R.string.autoglm_open_apikey_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "step3 填入 API Key，点击一键配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.autoglm_api_key_placeholder))
                    },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { isAdvancedExpanded = !isAdvancedExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAdvancedExpanded) {
                            "隐藏高级设置"
                        } else {
                            "显示高级设置（自定义 Endpoint / 模型名称）"
                        }
                    )
                }

                if (isAdvancedExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = advancedEndpoint,
                        onValueChange = { advancedEndpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("自定义 Endpoint（OpenAI 兼容）") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = advancedModelName,
                        onValueChange = { advancedModelName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名称") },
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { startConfigure() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConfiguring
                ) {
                    if (isConfiguring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.autoglm_status_running))
                    } else {
                        Text(stringResource(R.string.autoglm_one_click_button))
                    }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "step4 恢复非 AutoGLM 自动化（可选）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "如果你觉得在使用 AutoGLM 一键配置后自动化操作变得困难，可以点击下方按钮恢复为软件原本的自动化逻辑（启用 base 包，关闭 subagent 包，其余设置保持不变）。如果你想再次启用 AutoGLM，只需要按照 step3 输入密钥并再次点击一键配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { restoreOriginalAutomation() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConfiguring
                ) {
                    Text("恢复原本的自动化逻辑")
                }
            }
        }

        statusMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        errorMessage?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
