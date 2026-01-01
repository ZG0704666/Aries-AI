package com.ai.assistance.operit.ui.features.chat.screens

import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.util.AppLogger
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.ui.features.chat.components.AndroidExportDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportProgressDialog
import com.ai.assistance.operit.ui.features.chat.components.WindowsExportDialog
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen
import com.ai.assistance.operit.ui.features.chat.webview.WorkspaceFileSelector
import com.ai.assistance.operit.ui.features.chat.webview.computer.ComputerScreen
import com.ai.assistance.operit.ui.features.chat.util.ConfigurationStateHolder
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.SharedFileHandler
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun AIChatScreen(
        padding: PaddingValues = PaddingValues(),
        viewModel: ChatViewModel? = null,
        isFloatingMode: Boolean = false,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        hasBackgroundImage: Boolean = false,
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToUserPreferences: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {},
        onNavigateToModelPrompts: () -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
// Correctly initialize ViewModel using the viewModel() composable function
val actualViewModel: ChatViewModel = viewModel ?: viewModel { ChatViewModel(context.applicationContext) }

    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) { actualViewModel.setPermissionSystemColorScheme(colorScheme) }

    // Monitor shared files from external apps
    val sharedFiles by SharedFileHandler.sharedFiles.collectAsState()
    LaunchedEffect(sharedFiles) {
        sharedFiles?.let { uris ->
            if (uris.isNotEmpty()) {
                // Process the shared files
                actualViewModel.handleSharedFiles(uris)
                // Clear the shared files
                SharedFileHandler.clearSharedFiles()
            }
        }
    }

    // 添加麦克风权限请求启动器
    val requestMicrophonePermissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    // This launcher is now used inside the ViewModel's permission check flow
                    // It's kept here because it's tied to the composable lifecycle.
                    // The actual logic is now triggered from within the ViewModel after the check.
                } else {
                    // 权限被拒绝
                    android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.microphone_permission_denied),
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }

    // Get background image state
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val useBackgroundImage by preferencesManager.useBackgroundImage.collectAsState(initial = false)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsState(initial = null)
    val chatHeaderTransparent by preferencesManager.chatHeaderTransparent.collectAsState(initial = false)
    val chatInputTransparent by preferencesManager.chatInputTransparent.collectAsState(initial = false)
    val chatHeaderHistoryIconColor by preferencesManager.chatHeaderHistoryIconColor.collectAsState(
            initial = null
    )
    val chatHeaderPipIconColor by preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null)
    val chatHeaderOverlayMode by preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false)
    val showInputProcessingStatus by preferencesManager.showInputProcessingStatus.collectAsState(initial = true)
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

    // Collect chat style from preferences
    val chatStyleSetting by preferencesManager.chatStyle.collectAsState(initial = UserPreferencesManager.CHAT_STYLE_CURSOR)
    val chatStyle = remember(chatStyleSetting) {
        when (chatStyleSetting) {
            UserPreferencesManager.CHAT_STYLE_BUBBLE -> ChatStyle.BUBBLE
            else -> ChatStyle.CURSOR
        }
    }

    // Collect chat area horizontal padding from preferences
    val chatAreaHorizontalPadding by preferencesManager.chatAreaHorizontalPadding.collectAsState(initial = 16f)

    // 添加编辑按钮和编辑状态
    val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
    val editingMessageContent = remember { mutableStateOf("") }

    // Collect state from ViewModel
    val apiKey by actualViewModel.apiKey.collectAsState()
    val apiEndpoint by actualViewModel.apiEndpoint.collectAsState()
    val modelName by actualViewModel.modelName.collectAsState()
    val apiProviderType by actualViewModel.apiProviderType.collectAsState()
    val isConfigured by actualViewModel.isConfigured.collectAsState()
    val chatHistory by actualViewModel.chatHistory.collectAsState()
    val userMessage by actualViewModel.userMessage.collectAsState()
    // 仅对当前会话显示处理中状态（影响“停止/发送”按钮）
    val isLoading by actualViewModel.currentChatIsLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()
    // 按会话隔离的输入处理状态（用于进度条文案）
    val inputProcessingState by actualViewModel.currentChatInputProcessingState.collectAsState()

    val enableAiPlanning by actualViewModel.enableAiPlanning.collectAsState()
    val enableThinkingMode by actualViewModel.enableThinkingMode.collectAsState() // 收集思考模式状态
    val enableThinkingGuidance by
            actualViewModel.enableThinkingGuidance.collectAsState() // 收集思考引导状态
    val enableMemoryQuery by actualViewModel.enableMemoryQuery.collectAsState()
    val enableMaxContextMode by actualViewModel.enableMaxContextMode.collectAsState()
    val enableTools by actualViewModel.enableTools.collectAsState()
    val disableStreamOutput by actualViewModel.disableStreamOutput.collectAsState()
    val summaryTokenThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val popupMessage by actualViewModel.popupMessage.collectAsState()
    val attachments by actualViewModel.attachments.collectAsState()
    // 收集附件面板状态
    val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()
    // 收集滚动事件
    val scrollToBottomEvent = actualViewModel.scrollToBottomEvent
    // 从ViewModel收集新的状态
    val shouldShowConfigDialog by actualViewModel.shouldShowConfigDialog.collectAsState()
    val isWorkspaceOpen by actualViewModel.isWorkspaceOpen.collectAsState()
    val showWorkspaceFileSelector by actualViewModel.showWorkspaceFileSelector.collectAsState()

    // 添加模型建议对话框状态
    var showModelSuggestionDialog by remember { mutableStateOf(false) }
    
    // 添加记忆文件夹选择对话框状态
    var showMemoryFolderDialog by remember { mutableStateOf(false) }

    // 当模型名称加载后，检查是否为建议更换的模型
    LaunchedEffect(modelName) {
        if (modelName.isNotBlank() && modelName.contains("deepseek-r1-0528-qwen3-8b:free", ignoreCase = true)) {
            showModelSuggestionDialog = true
        }
    }

    // 模型建议对话框
    if (showModelSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showModelSuggestionDialog = false },
            title = { Text(stringResource(R.string.model_suggestion_title)) },
            text = { Text(stringResource(R.string.model_suggestion_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showModelSuggestionDialog = false
                        // 如果用户已输入token，直接保存配置
                        actualViewModel.updateApiKey(ApiPreferences.DEFAULT_API_KEY)
                        actualViewModel.updateApiEndpoint(ApiPreferences.DEFAULT_API_ENDPOINT)
                        actualViewModel.updateModelName(ApiPreferences.DEFAULT_MODEL_NAME)
                        actualViewModel.updateApiProviderType(ApiProviderType.DEEPSEEK)
                        actualViewModel.saveApiSettings()

                        // 新增：重置状态以重新显示配置界面
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = false
                        actualViewModel.showConfigurationScreen()
                        
                    }) {
                        Text(stringResource(R.string.change_model))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelSuggestionDialog = false }) {
                    Text(stringResource(R.string.ignore))
                }
            }
        )
    }


    // 添加WebView刷新相关状态
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()

    // Collect reply state
    val replyToMessage by actualViewModel.replyToMessage.collectAsState()

    // Floating window mode state
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // UI state
    val scrollState = rememberScrollState()
    val historyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    


    // 确保每次应用启动时正确处理配置界面的显示逻辑
    LaunchedEffect(apiKey) {
        // 只有当apiKey有效值时才执行逻辑，防止初始化阶段的不正确判断
        if (apiKey.isNotBlank()) {
            // 如果使用的是自定义配置，标记为已确认，不显示配置界面
            if (apiKey != ApiPreferences.DEFAULT_API_KEY) {
                ConfigurationStateHolder.hasConfirmedDefaultInSession = true
            }
        }
    }

    // Modern chat UI colors - Cursor风格
    val backgroundColor =
            if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 滚动状态
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val onAutoScrollToBottomChange = remember { { it: Boolean -> autoScrollToBottom = it } }

    // 处理来自ViewModel的滚动事件（流式输出时）
    LaunchedEffect(Unit) {
        scrollToBottomEvent.collect {
            if (autoScrollToBottom) {
                try {
                    scrollState.animateScrollTo(scrollState.maxValue)
                } catch (e: Exception) {
                    // AppLogger.e("AIChatScreen", "自动滚动失败", e)
                }
            }
        }
    }

    // 自动滚动处理 - 仅在消息数量变化时触发
    LaunchedEffect(chatHistory.size, scrollState.maxValue) {
        if (autoScrollToBottom) {
            try {
                scrollState.animateScrollTo(scrollState.maxValue)
            } catch (e: Exception) {
                // AppLogger.e("AIChatScreen", "自动滚动失败", e)
            }
        }
    }

    // 移除原有的 snackbar 错误处理
    val snackbarHostState = remember { SnackbarHostState() }

    // 用新的错误弹窗替换原有的错误显示逻辑
    errorMessage?.let { message ->
        ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.clearError() })
    }

    // 处理toast事件 (保留)
    val toastEvent by actualViewModel.toastEvent.collectAsState()

    toastEvent?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                    .show()
            actualViewModel.clearToastEvent()
        }
    }

    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }
    // 判断是否有默认配置可用
    val hasDefaultConfig = apiKey.isNotBlank()

    // 确定是否显示配置界面的最终逻辑
    val showConfig = shouldShowConfigDialog && !ConfigurationStateHolder.hasConfirmedDefaultInSession

    // 添加手势状态
    var chatScreenGestureConsumed by remember { mutableStateOf(false) }
    val onChatScreenGestureConsumedChange = remember {
        { it: Boolean -> chatScreenGestureConsumed = it }
    }

    // 添加累计滑动距离变量
    var currentDrag by remember { mutableStateOf(0f) }
    val onCurrentDragChange = remember { { it: Float -> currentDrag = it } }
    var verticalDrag by remember { mutableStateOf(0f) }
    val onVerticalDragChange = remember { { it: Float -> verticalDrag = it } }
    val dragThreshold = 40f // 与PhoneLayout保持一致

    // 收集WebView显示状态
    val showWebView by actualViewModel.showWebView.collectAsState()
    // 收集AI电脑显示状态
    val showAiComputer by actualViewModel.showAiComputer.collectAsState()
    var hasEverShownWebView by remember { mutableStateOf(false) }
    LaunchedEffect(showWebView) {
        if (showWebView) {
            hasEverShownWebView = true
        }
    }
    var hasEverShownAiComputer by remember { mutableStateOf(false) }
    LaunchedEffect(showAiComputer) {
        if (showAiComputer) {
            hasEverShownAiComputer = true
        }
    }
    val view = LocalView.current

    // 当手势状态改变时，通知父组件
    LaunchedEffect(chatScreenGestureConsumed, showWebView) {
        val finalGestureState = chatScreenGestureConsumed
        // 同时更新全局状态持有者，确保PhoneLayout能够访问到状态
        GestureStateHolder.isChatScreenGestureConsumed = finalGestureState
        onGestureConsumed(finalGestureState)
    }

    // 处理文件选择器请求
    val fileChooserRequest by actualViewModel.uiStateDelegate.fileChooserRequest.collectAsState()
    val fileChooserLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                // 处理文件选择结果
                actualViewModel.handleFileChooserResult(result.resultCode, result.data)
                // 清除请求
                actualViewModel.uiStateDelegate.clearFileChooserRequest()
            }

    // 启动文件选择器
    LaunchedEffect(fileChooserRequest) {
        fileChooserRequest?.let { fileChooserLauncher.launch(it) }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setTopBarActions = LocalTopBarActions.current
    val appBarContentColor = LocalAppBarContentColor.current
    val isCurrentScreen = LocalIsCurrentScreen.current


    // 当showWebView或showAiComputer状态改变时，更新TopAppBar的actions
    // 使用DisposableEffect确保当AIChatScreen离开组合时，actions被清空
    LaunchedEffect(isCurrentScreen, showWebView, showAiComputer, appBarContentColor) {
        if (isCurrentScreen) {
            setTopBarActions {
                // AI电脑模式切换按钮
                IconButton(
                        onClick = {
                            actualViewModel.onAiComputerButtonClick()
                        }
                ) {
                    Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "AI电脑",
                            tint =
                            if (showAiComputer) MaterialTheme.colorScheme.primaryContainer
                            else appBarContentColor
                    )
                }

                // Web开发模式切换按钮
                IconButton(
                        onClick = {
                            actualViewModel.onWorkspaceButtonClick()
                        }
                ) {
                    Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "代码编辑器",
                            tint =
                            if (showWebView) MaterialTheme.colorScheme.primaryContainer
                            else appBarContentColor
                    )
                }
            }
        }
    }

    // 导出相关状态
    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showAndroidExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var showExportCompleteDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportSuccess by remember { mutableStateOf(false) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var webContentDir by remember { mutableStateOf<File?>(null) }

    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val inputSurfaceColor = when {
        chatInputTransparent -> colorScheme.surface.copy(alpha = 0f)
        hasBackgroundImage -> colorScheme.surface.copy(alpha = 0.85f)
        else -> colorScheme.surface
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CustomScaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    // 只在不显示配置界面时显示底部输入框
                    if (!showConfig) {
                        // ChatInputSection is back in the bottomBar to reserve space
                        Box(
                                modifier = Modifier.onGloballyPositioned {
                                    bottomBarHeightPx = it.size.height
                                }
                        ) {
                            ChatInputSection(
                                    actualViewModel = actualViewModel,
                                    userMessage = userMessage,
                                    onUserMessageChange = { actualViewModel.updateUserMessage(it) },
                                    onSendMessage = {
                                        // 清除焦点，收起软键盘
                                        focusManager.clearFocus()

                                        actualViewModel.sendUserMessage()
                                        // 在发送消息后重置附件面板状态
                                        actualViewModel.resetAttachmentPanelState()
                                        // 发送新问题时，恢复自动滚动到底部
                                        autoScrollToBottom = true
                                    },
                                    onCancelMessage = { actualViewModel.cancelCurrentMessage() },
                                    isLoading = isLoading,
                                    inputState = inputProcessingState,
                                    allowTextInputWhileProcessing = true,
                                    onAttachmentRequest = { filePath ->
                                        // 处理附件 - 现在使用文件路径而不是Uri
                                        actualViewModel.handleAttachment(filePath)
                                    },
                                    attachments = attachments,
                                    onRemoveAttachment = { filePath ->
                                        // 删除附件 - 现在使用文件路径而不是Uri
                                        actualViewModel.removeAttachment(filePath)
                                    },
                                    onInsertAttachment = { attachment: AttachmentInfo ->
                                        // 在光标位置插入附件引用
                                        actualViewModel.insertAttachmentReference(attachment)
                                    },
                                    onAttachScreenContent = {
                                        // 添加屏幕内容附件
                                        actualViewModel.captureScreenContent()
                                    },
                                    onAttachNotifications = {
                                        // 添加当前通知附件
                                        actualViewModel.captureNotifications()
                                    },
                                    onAttachLocation = {
                                        // 添加当前位置附件
                                        actualViewModel.captureLocation()
                                    },
                                    onAttachMemory = {
                                        // 显示记忆文件夹选择对话框
                                        showMemoryFolderDialog = true
                                    },
                                    onTakePhoto = { uri ->
                                        // 处理拍摄的照片
                                        actualViewModel.handleTakenPhoto(uri)
                                    },
                                    hasBackgroundImage = hasBackgroundImage,
                                    chatInputTransparent = chatInputTransparent,
                                    // 传递附件面板状态
                                    externalAttachmentPanelState = attachmentPanelState,
                                    onAttachmentPanelStateChange = { newState ->
                                        actualViewModel.updateAttachmentPanelState(newState)
                                    },
                                    showInputProcessingStatus = showInputProcessingStatus,
                                    enableTools = enableTools,
                                    replyToMessage = replyToMessage,
                                    onClearReply = { actualViewModel.clearReplyToMessage() },
                                    isWorkspaceOpen = isWorkspaceOpen
                            )
                        }
                    }
                }
        ) { paddingValues ->
            // 根据前面的逻辑条件决定是否显示配置界面
            if (showConfig) {
                ConfigurationScreen(
                        apiEndpoint = apiEndpoint,
                        apiKey = apiKey,
                        modelName = modelName,
                        onApiEndpointChange = { actualViewModel.updateApiEndpoint(it) },
                        onApiKeyChange = { actualViewModel.updateApiKey(it) },
                        onModelNameChange = { actualViewModel.updateModelName(it) },
                        onApiProviderTypeChange = { actualViewModel.updateApiProviderType(it) },
                        onSaveConfig = {
                            actualViewModel.saveApiSettings()
                            // 保存配置后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        onError = { error -> actualViewModel.showErrorMessage(error) },
                        coroutineScope = coroutineScope,
                        // 新增：使用默认配置的回调
                        onUseDefault = {
                            actualViewModel.useDefaultConfig()
                            // 确认使用默认配置后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        // 标识是否在使用默认配置
                        isUsingDefault = true, // 当显示此屏幕时，总是因为使用了默认值
                        // 添加导航到聊天界面的回调
                        onNavigateToChat = {
                            // 当用户设置了自己的配置后保存
                            actualViewModel.saveApiSettings()
                            // 确认后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        // 添加导航到Token配置页面的回调
                        onNavigateToTokenConfig = onNavigateToTokenConfig,
                        // 添加导航到Settings页面的回调
                        onNavigateToSettings = onNavigateToSettings
                )
            } else {
                // The main content area is now a Box to allow overlaying.
                // It respects the padding from the Scaffold's bottomBar.
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ChatScreenContent(
                                // modifier = Modifier.weight(1f), // This is no longer needed here
                                paddingValues =
                                        PaddingValues(), // Padding is already handled by the parent Box
                                actualViewModel = actualViewModel,
                                showChatHistorySelector = showChatHistorySelector,
                                chatHistory = chatHistory,
                                enableAiPlanning = enableAiPlanning,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                hasBackgroundImage = hasBackgroundImage,
                                editingMessageIndex = editingMessageIndex,
                                editingMessageContent = editingMessageContent,
                                chatScreenGestureConsumed = chatScreenGestureConsumed,
                                onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                                currentDrag = currentDrag,
                                onCurrentDragChange = onCurrentDragChange,
                                verticalDrag = verticalDrag,
                                onVerticalDragChange = onVerticalDragChange,
                                dragThreshold = dragThreshold,
                                scrollState = scrollState,
                                autoScrollToBottom = autoScrollToBottom,
                                onAutoScrollToBottomChange = onAutoScrollToBottomChange,
                                coroutineScope = coroutineScope,
                                chatHistories = chatHistories,
                                currentChatId = currentChatId ?: "",
                                chatHeaderTransparent = chatHeaderTransparent,
                                chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                                chatHeaderPipIconColor = chatHeaderPipIconColor,
                                chatHeaderOverlayMode = chatHeaderOverlayMode,
                                chatStyle = chatStyle, // Pass chat style
                                historyListState = historyListState,
                                onSwitchCharacter = { characterId ->
                                    coroutineScope.launch {
                                        characterCardManager.setActiveCharacterCard(characterId)
                                    }
                                },
                                chatAreaHorizontalPadding = chatAreaHorizontalPadding
                        )

                        ChatSettingsBar(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                enableAiPlanning = enableAiPlanning,
                                onToggleAiPlanning = { actualViewModel.toggleAiPlanning() },
                                permissionLevel =
                                        actualViewModel.masterPermissionLevel
                                                .collectAsState()
                                                .value,
                                onTogglePermission = { actualViewModel.toggleMasterPermission() },
                                enableThinkingMode = enableThinkingMode,
                                onToggleThinkingMode = { actualViewModel.toggleThinkingMode() },
                                enableThinkingGuidance = enableThinkingGuidance,
                                onToggleThinkingGuidance = {
                                    actualViewModel.toggleThinkingGuidance()
                                },
                                maxWindowSizeInK =
                                        actualViewModel.maxWindowSizeInK.collectAsState().value,
                                baseContextLengthInK =
                                        actualViewModel.baseContextLengthInK.collectAsState().value,
                                maxContextLengthInK =
                                        actualViewModel.maxContextLengthInK.collectAsState().value,
                                onContextLengthChange = {
                                    actualViewModel.updateContextLength(it)
                                },
                                enableMemoryQuery = enableMemoryQuery,
                                onToggleMemoryQuery = {
                                    actualViewModel.toggleMemoryQuery()
                                },
                                enableMaxContextMode = enableMaxContextMode,
                                onToggleEnableMaxContextMode = {
                                    actualViewModel.toggleEnableMaxContextMode()
                                },
                                summaryTokenThreshold = summaryTokenThreshold,
                                onSummaryTokenThresholdChange = {
                                    actualViewModel.updateSummaryTokenThreshold(it)
                                },
                                onNavigateToUserPreferences = onNavigateToUserPreferences,
                                onNavigateToModelConfig = onNavigateToModelConfig,
                                onNavigateToModelPrompts = onNavigateToModelPrompts,
                                isAutoReadEnabled = isAutoReadEnabled,
                                onToggleAutoRead = { actualViewModel.toggleAutoRead() },
                                enableTools = enableTools,
                                onToggleTools = { actualViewModel.toggleTools() },
                                disableStreamOutput = disableStreamOutput,
                                onToggleDisableStreamOutput = { actualViewModel.toggleDisableStreamOutput() },
                                onManualMemoryUpdate = { actualViewModel.manuallyUpdateMemory() },
                                onManualSummarizeConversation = { actualViewModel.manuallySummarizeConversation() }
                        )
                    }
                }
            }
        }

        // 文件选择器，现在位于Scaffold外部，作为独立的浮层
        val bottomPaddingForSelector = with(density) { bottomBarHeightPx.toDp() }
        AnimatedVisibility(
            visible = showWorkspaceFileSelector,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { actualViewModel.hideWorkspaceFileSelector() }
                        )
                )
                WorkspaceFileSelector(
                    modifier = Modifier
                        .padding(bottom = bottomPaddingForSelector)
                        .animateEnterExit(
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 240)
                            ) { it },
                            exit = slideOutVertically(
                                animationSpec = tween(durationMillis = 200)
                            ) { it }
                        ),
                    viewModel = actualViewModel,
                    onFileSelected = { filePath ->
                        val currentChat = actualViewModel.chatHistories.value.find { it.id == actualViewModel.currentChatId.value }
                        val workspacePath = currentChat?.workspace
                        val relativePath = if (workspacePath != null) {
                            File(filePath).relativeTo(File(workspacePath)).path
                        } else {
                            filePath
                        }
                        val currentText = userMessage.text
                        val newText = currentText.replaceAfterLast('@', "$relativePath ")
                        val newTextFieldValue = TextFieldValue(
                            text = newText,
                            selection = TextRange(newText.length)
                        )
                        actualViewModel.updateUserMessage(newTextFieldValue)
                        actualViewModel.hideWorkspaceFileSelector()
                    },
                    onShouldHide = {
                        actualViewModel.hideWorkspaceFileSelector()
                    },
                    backgroundColor = inputSurfaceColor
                )
            }
        }

        // Web开发模式作为浮层，现在位于Scaffold外部，可以覆盖整个屏幕
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (showWebView) 1f else 0f)
                .clipToBounds(),
            content = {
                // The content is composed unconditionally, keeping it "alive"
                val currentChat = chatHistories.find { it.id == currentChatId }
                if (hasEverShownWebView && currentChat != null) {
                    WorkspaceScreen(
                        actualViewModel = actualViewModel,
                        currentChat = currentChat,
                        isVisible = showWebView, // Pass visibility state
                        onExportClick = { workDir ->
                            webContentDir = workDir
                            AppLogger.d(
                                "AIChatScreen",
                                "正在导出工作区: ${workDir.absolutePath}, 聊天ID: $currentChatId"
                            )
                            showExportPlatformDialog = true
                        }
                    )
                }
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                if (showWebView) {
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                } else {
                    // 当不可见时，我们让布局大小为0，并且完全不测量或放置子项。
                    // 这可以跳过子项昂贵的测量/布局过程，从而解决性能问题，
                    // 同时由于它仍在组合中，因此可以保持其状态。
                    layout(0, 0) {}
                }
            }
        }

        // AI电脑模式作为浮层，现在位于Scaffold外部，可以覆盖整个屏幕
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (showAiComputer) 1f else 0f)
                .clipToBounds(),
            content = {
                // The content is composed unconditionally, keeping it "alive"
                if (hasEverShownAiComputer) {
                    ComputerScreen()
                }
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                if (showAiComputer) {
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                } else {
                    // 当不可见时，我们强制测量为 0x0 并放置
                    // 这样可以触发底层 View 的 onSizeChanged，从而通知其暂停渲染
                    val placeable = measurables.first().measure(Constraints.fixed(0, 0))
                    layout(0, 0) {
                        placeable.placeRelative(0, 0)
                    }
                }
            }
        }

        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = {
                        showExportPlatformDialog = false
                        showAndroidExportDialog = true
                    },
                    onSelectWindows = {
                        showExportPlatformDialog = false
                        showWindowsExportDialog = true
                    }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri, versionName, versionCode ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    versionName = versionName,
                                    versionCode = versionCode,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = {
                        // TODO: 实现取消导出的逻辑
                        showExportProgressDialog = false
                    }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { path ->
                        val tool = AITool(
                            name = "open_file",
                            parameters = listOf(ToolParameter("path", path))
                        )
                        AIToolHandler.getInstance(context).executeTool(tool)
                    }
            )
        }
    }

    // Show popup message dialog when needed
    popupMessage?.let { message ->
        AlertDialog(
                onDismissRequest = { actualViewModel.clearPopupMessage() },
                title = { Text("提示") },
                text = { Text(message ?: "") },
                confirmButton = {
                    TextButton(onClick = { actualViewModel.clearPopupMessage() }) { Text("确定") }
                }
        )
    }

    // Check for overlay permission on resume
    LaunchedEffect(Unit) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)

        // If floating mode is on but no permission, turn it off
        if (isFloatingMode && !canDrawOverlays.value) {
            actualViewModel.toggleFloatingMode()
            Toast.makeText(
                            context,
                            "未获得悬浮窗权限，已关闭悬浮窗模式",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }
    
    // 记忆文件夹选择对话框
    MemoryFolderSelectionDialog(
        visible = showMemoryFolderDialog,
        onDismiss = { showMemoryFolderDialog = false },
        onConfirm = { selectedFolders ->
            actualViewModel.captureMemoryFolders(selectedFolders)
        }
    )
}
