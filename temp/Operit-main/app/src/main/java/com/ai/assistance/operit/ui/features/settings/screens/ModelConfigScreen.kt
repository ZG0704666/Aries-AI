package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.sections.AdvancedSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelParametersSection
import com.ai.assistance.operit.ui.features.settings.sections.SettingsInfoBanner
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSectionHeader
import com.ai.assistance.operit.ui.features.settings.sections.SettingsSwitchRow
import com.ai.assistance.operit.ui.features.settings.sections.SettingsTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelConfigScreen(
    onBackPressed: () -> Unit = {},
    navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configManager = remember { ModelConfigManager(context) }
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 配置状态
    val configList = configManager.configListFlow.collectAsState(initial = listOf("default")).value
    // 不再使用activeConfigIdFlow，默认选择第一个配置
    var selectedConfigId by remember { mutableStateOf(configList.firstOrNull() ?: "default") }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var showRenameConfigDialog by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    var renameConfigName by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }

    // 连接测试状态
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    
    // 保存API设置的函数引用
    var saveApiSettings: (() -> Unit)? by remember { mutableStateOf(null) }

    // 初始化配置管理器
    LaunchedEffect(Unit) { configManager.initializeIfNeeded() }

    // 加载所有配置名称
    LaunchedEffect(configList) {
        configList.forEach { id ->
            val config = configManager.getModelConfigFlow(id).first()
            configNameMap[id] = config.name
        }
    }

    // 加载选中的配置
    LaunchedEffect(selectedConfigId) {
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    // 自动隐藏测试结果
    LaunchedEffect(testResult) {
        if (testResult != null) {
            kotlinx.coroutines.delay(5000)
            testResult = null
        }
    }

    // 显示通知消息
    fun showNotification(message: String) {
        confirmMessage = message
        showSaveSuccessMessage = true
        scope.launch {
            kotlinx.coroutines.delay(3000)
            showSaveSuccessMessage = false
        }
    }

    // 主界面内容
    CustomScaffold() { paddingValues ->
        LazyColumn(
                state = listState,
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            item {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border =
                                BorderStroke(
                                        0.7.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    stringResource(R.string.select_model_config),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                            )

                            OutlinedButton(
                                    onClick = { showAddConfigDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors =
                                            ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary
                                            )
                            ) {
                                Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                        stringResource(R.string.new_action),
                                        fontSize = 12.sp,
                                        style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        val selectedConfigName =
                                configNameMap[selectedConfigId] ?: stringResource(R.string.default_profile)

                        Surface(
                                modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                tonalElevation = 0.5.dp,
                        ) {
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = selectedConfigName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )

                                @OptIn(ExperimentalAnimationApi::class)
                                AnimatedContent(
                                        targetState = isDropdownExpanded,
                                        transitionSpec = {
                                            fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                        }
                                ) { expanded ->
                                    Icon(
                                            if (expanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.select_config),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FlowRow(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedConfigId != "default") {
                                TextButton(
                                        onClick = {
                                            renameConfigName = selectedConfig.value?.name ?: ""
                                            showRenameConfigDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.rename_action), fontSize = 14.sp)
                                }
                                
                                TextButton(
                                        onClick = {
                                            scope.launch {
                                                configManager.deleteConfig(selectedConfigId)
                                                selectedConfigId = configList.firstOrNull() ?: "default"
                                                showNotification(context.getString(R.string.config_deleted))
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error
                                                ),
                                        modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.delete_action), fontSize = 14.sp)
                                }
                            }

                            TextButton(
                                    onClick = {
                                        scope.launch {
                                            saveApiSettings?.invoke()
                                            kotlinx.coroutines.delay(300)

                                            isTestingConnection = true
                                            testResult = null
                                            try {
                                                selectedConfig.value?.let { config ->
                                                    val modelNameToTest = getModelByIndex(config.modelName, 0)
                                                    val configForTest = config.copy(modelName = modelNameToTest)
                                                    val customHeadersJson = apiPreferences.getCustomHeaders()
                                                    val service =
                                                            AIServiceFactory.createService(
                                                                    config = configForTest,
                                                                    customHeadersJson = customHeadersJson,
                                                                    modelConfigManager = configManager,
                                                                    context = context
                                                            )
                                                    testResult = service.testConnection()
                                                } ?: run {
                                                    testResult =
                                                            Result.failure(
                                                                    Exception(
                                                                            context.getString(R.string.no_config_selected)
                                                                    )
                                                            )
                                                }
                                            } catch (e: Exception) {
                                                testResult = Result.failure(e)
                                            }
                                            isTestingConnection = false
                                        }
                                    },
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                            Icons.Default.Dns,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.test_connection_desc), fontSize = 14.sp)
                            }
                        }

                        AnimatedVisibility(
                                visible = testResult != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                        ) {
                            testResult?.let { result ->
                                val isSuccess = result.isSuccess
                                val message =
                                        if (isSuccess)
                                                result.getOrNull() ?: context.getString(R.string.connection_test_success)
                                        else
                                                context.getString(
                                                        R.string.connection_test_failed,
                                                        result.exceptionOrNull()?.message ?: ""
                                                )
                                val containerColor =
                                        if (isSuccess)
                                                MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer
                                val contentColor =
                                        if (isSuccess)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onErrorContainer
                                val icon =
                                        if (isSuccess) Icons.Default.CheckCircle
                                        else Icons.Default.Warning

                                Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        colors = CardDefaults.cardColors(containerColor = containerColor),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = contentColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = contentColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier.width(280.dp),
                            properties = PopupProperties(focusable = true)
                    ) {
                        configList.forEach { configId ->
                            val configName =
                                    configNameMap[configId] ?: stringResource(R.string.unnamed_profile)
                            val isSelected = configId == selectedConfigId

                            DropdownMenuItem(
                                    text = {
                                        Text(
                                                text = configName,
                                                fontWeight =
                                                        if (isSelected) FontWeight.SemiBold
                                                        else FontWeight.Normal,
                                                color =
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon =
                                            if (isSelected) {
                                                {
                                                    Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = stringResource(R.string.selected_desc),
                                                            modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                    onClick = {
                                        selectedConfigId = configId
                                        isDropdownExpanded = false
                                    },
                                    colors =
                                            MenuDefaults.itemColors(
                                                    textColor =
                                                            if (isSelected)
                                                                    MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurface
                                            ),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            if (configId != configList.last()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            selectedConfig.value?.let { config ->
                item {
                    ModelApiSettingsSection(
                            config = config,
                            configManager = configManager,
                            showNotification = { message -> showNotification(message) },
                            onSaveRequested = { saveFunction -> saveApiSettings = saveFunction },
                            navigateToMnnModelDownload = navigateToMnnModelDownload
                    )
                }

                item {
                    ContextSummarySettingsSection(
                            config = config,
                            configManager = configManager,
                            scope = scope,
                            showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    ModelParametersSection(
                            config = config,
                            configManager = configManager,
                            showNotification = { message -> showNotification(message) }
                    )
                }

                item {
                    AdvancedSettingsSection(
                            config = config,
                            configManager = configManager,
                            showNotification = { message -> showNotification(message) }
                    )
                }
            }

            if (showSaveSuccessMessage) {
                item {
                    AnimatedVisibility(
                            visible = showSaveSuccessMessage,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = confirmMessage,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 新建配置对话框
        if (showAddConfigDialog) {
            AlertDialog(
                    onDismissRequest = {
                        showAddConfigDialog = false
                        newConfigName = ""
                    },
                    title = {
                        Text(
                                stringResource(R.string.new_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    stringResource(R.string.new_model_config_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = newConfigName,
                                    onValueChange = { newConfigName = it },
                                    label = { Text(stringResource(R.string.model_config_name), fontSize = 12.sp) },
                                    placeholder = { Text(stringResource(R.string.model_config_name_placeholder), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (newConfigName.isNotBlank()) {
                                        scope.launch {
                                            val configId = configManager.createConfig(newConfigName)
                                            selectedConfigId = configId
                                            showAddConfigDialog = false
                                            newConfigName = ""
                                            showNotification(context.getString(R.string.new_config_created))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.create_action), fontSize = 13.sp) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showAddConfigDialog = false
                                    newConfigName = ""
                                }
                        ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }

        // 重命名配置对话框
        if (showRenameConfigDialog) {
            AlertDialog(
                    onDismissRequest = {
                        showRenameConfigDialog = false
                        renameConfigName = ""
                    },
                    title = {
                        Text(
                                stringResource(R.string.rename_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    stringResource(R.string.rename_model_config_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = renameConfigName,
                                    onValueChange = { renameConfigName = it },
                                    label = { Text(stringResource(R.string.model_config_name), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (renameConfigName.isNotBlank()) {
                                        scope.launch {
                                            configManager.updateConfigBase(selectedConfigId, renameConfigName)
                                            configNameMap[selectedConfigId] = renameConfigName
                                            showRenameConfigDialog = false
                                            renameConfigName = ""
                                            showNotification(context.getString(R.string.config_renamed))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.confirm_rename), fontSize = 13.sp) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showRenameConfigDialog = false
                                    renameConfigName = ""
                                }
                        ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun ContextSummarySettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        scope: CoroutineScope,
        showNotification: (String) -> Unit
) {
        var contextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.contextLength)) }
        var maxContextLengthInput by remember(config.id) { mutableStateOf(formatFloatValue(config.maxContextLength)) }
        var contextError by remember { mutableStateOf<String?>(null) }

        var enableSummary by remember(config.id) { mutableStateOf(config.enableSummary) }
        var summaryTokenThresholdInput by remember(config.id) { mutableStateOf(formatFloatValue(config.summaryTokenThreshold)) }
        var enableSummaryByMessageCount by remember(config.id) { mutableStateOf(config.enableSummaryByMessageCount) }
        var summaryMessageCountThresholdInput by remember(config.id) { mutableStateOf(config.summaryMessageCountThreshold.toString()) }
        var summaryError by remember { mutableStateOf<String?>(null) }

        var contextExpanded by rememberSaveable { mutableStateOf(false) }
        var summaryExpanded by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(config.id, config.contextLength) {
                contextLengthInput = formatFloatValue(config.contextLength)
        }
        LaunchedEffect(config.id, config.maxContextLength) {
                maxContextLengthInput = formatFloatValue(config.maxContextLength)
        }
        LaunchedEffect(config.id, config.enableSummary) {
                enableSummary = config.enableSummary
        }
        LaunchedEffect(config.id, config.summaryTokenThreshold) {
                summaryTokenThresholdInput = formatFloatValue(config.summaryTokenThreshold)
        }
        LaunchedEffect(config.id, config.enableSummaryByMessageCount) {
                enableSummaryByMessageCount = config.enableSummaryByMessageCount
        }
        LaunchedEffect(config.id, config.summaryMessageCountThreshold) {
                summaryMessageCountThresholdInput = config.summaryMessageCountThreshold.toString()
        }

        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { contextExpanded = !contextExpanded }
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.settings_context_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                        imageVector = if (contextExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (contextExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        AnimatedVisibility(
                                visible = contextExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                        ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SettingsInfoBanner(text = stringResource(id = R.string.settings_context_card_content))

                                        SettingsTextField(
                                                title = stringResource(id = R.string.settings_context_length),
                                                subtitle = stringResource(id = R.string.settings_context_length_subtitle),
                                                value = contextLengthInput,
                                                onValueChange = {
                                                        contextLengthInput = it
                                                        contextError = null
                                                },
                                                unitText = "K",
                                                keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal,
                                                        imeAction = ImeAction.Next
                                                )
                                        )

                                        SettingsTextField(
                                                title = stringResource(id = R.string.settings_max_context_length),
                                                subtitle = stringResource(id = R.string.settings_max_context_length_subtitle),
                                                value = maxContextLengthInput,
                                                onValueChange = {
                                                        maxContextLengthInput = it
                                                        contextError = null
                                                },
                                                unitText = "K",
                                                keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal,
                                                        imeAction = ImeAction.Done
                                                )
                                        )

                                        contextError?.let {
                                                Text(
                                                        text = it,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }

                                        Button(
                                                onClick = {
                                                        val contextValue = contextLengthInput.toFloatOrNull()
                                                        val maxValue = maxContextLengthInput.toFloatOrNull()
                                                        when {
                                                                contextValue == null || contextValue <= 0f -> {
                                                                        contextError = "请输入有效的上下文长度"
                                                                }
                                                                maxValue == null || maxValue <= 0f -> {
                                                                        contextError = "请输入有效的最大上下文长度"
                                                                }
                                                                else -> {
                                                                        scope.launch {
                                                                                try {
                                                                                        configManager.updateContextSettings(
                                                                                                configId = config.id,
                                                                                                contextLength = contextValue,
                                                                                                maxContextLength = maxValue,
                                                                                                enableMaxContextMode = config.enableMaxContextMode
                                                                                        )
                                                                                        showNotification("上下文设置已保存")
                                                                                        contextError = null
                                                                                } catch (e: Exception) {
                                                                                        contextError = e.message ?: "保存失败"
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Text("保存上下文设置")
                                        }
                                }
                        }
                }
        }

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
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { summaryExpanded = !summaryExpanded }
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Summarize,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.settings_summary_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                        imageVector = if (summaryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (summaryExpanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        AnimatedVisibility(
                                visible = summaryExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                        ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SettingsSwitchRow(
                                                title = stringResource(id = R.string.settings_enable_summary),
                                                subtitle = stringResource(id = R.string.settings_enable_summary_desc),
                                                checked = enableSummary,
                                                onCheckedChange = { enableSummary = it }
                                        )

                                        SettingsTextField(
                                                title = stringResource(id = R.string.settings_summary_threshold),
                                                subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
                                                value = summaryTokenThresholdInput,
                                                onValueChange = {
                                                        summaryTokenThresholdInput = it
                                                        summaryError = null
                                                },
                                                enabled = enableSummary,
                                                keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal,
                                                        imeAction = ImeAction.Next
                                                )
                                        )

                                        SettingsSwitchRow(
                                                title = stringResource(id = R.string.settings_enable_summary_by_message_count),
                                                subtitle = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
                                                checked = enableSummaryByMessageCount,
                                                onCheckedChange = { enableSummaryByMessageCount = it },
                                                enabled = enableSummary
                                        )

                                        SettingsTextField(
                                                title = stringResource(id = R.string.settings_summary_message_count_threshold),
                                                subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
                                                value = summaryMessageCountThresholdInput,
                                                onValueChange = {
                                                        summaryMessageCountThresholdInput = it
                                                        summaryError = null
                                                },
                                                unitText = "条",
                                                enabled = enableSummary && enableSummaryByMessageCount,
                                                keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Done
                                                )
                                        )

                                        summaryError?.let {
                                                Text(
                                                        text = it,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }

                                        Button(
                                                onClick = {
                                                        val threshold = summaryTokenThresholdInput.toFloatOrNull()
                                                        val messageCount = summaryMessageCountThresholdInput.toIntOrNull()
                                                        when {
                                                                !enableSummary -> {
                                                                        scope.launch {
                                                                                configManager.updateSummarySettings(
                                                                                        configId = config.id,
                                                                                        enableSummary = false,
                                                                                        summaryTokenThreshold = config.summaryTokenThreshold,
                                                                                        enableSummaryByMessageCount = config.enableSummaryByMessageCount,
                                                                                        summaryMessageCountThreshold = config.summaryMessageCountThreshold
                                                                                )
                                                                                showNotification("总结设置已更新")
                                                                        }
                                                                }
                                                                threshold == null || threshold <= 0f || threshold >= 1f -> {
                                                                        summaryError = "请输入0-1之间的总结触发阈值"
                                                                }
                                                                enableSummaryByMessageCount && (messageCount == null || messageCount <= 0) -> {
                                                                        summaryError = "请输入有效的消息数量阈值"
                                                                }
                                                                else -> {
                                                                        scope.launch {
                                                                                try {
                                                                                        configManager.updateSummarySettings(
                                                                                                configId = config.id,
                                                                                                enableSummary = enableSummary,
                                                                                                summaryTokenThreshold = threshold,
                                                                                                enableSummaryByMessageCount = enableSummaryByMessageCount,
                                                                                                summaryMessageCountThreshold = if (enableSummaryByMessageCount) messageCount ?: config.summaryMessageCountThreshold else config.summaryMessageCountThreshold
                                                                                        )
                                                                                        showNotification("总结设置已保存")
                                                                                        summaryError = null
                                                                                } catch (e: Exception) {
                                                                                        summaryError = e.message ?: "保存失败"
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                Text("保存总结设置")
                                        }
                                }
                        }
                }
        }
        }
}

private fun formatFloatValue(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else String.format("%.2f", value)
}