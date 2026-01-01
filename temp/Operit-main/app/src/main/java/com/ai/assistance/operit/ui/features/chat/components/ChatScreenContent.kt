package com.ai.assistance.operit.ui.features.chat.components

import android.content.ActivityNotFoundException
import android.content.Intent
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.CharacterCard

import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.InputChip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.MessageEditor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
        modifier: Modifier = Modifier,
        paddingValues: PaddingValues,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistory: List<ChatMessage>,
        enableAiPlanning: Boolean,
        isLoading: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        hasBackgroundImage: Boolean,
        editingMessageIndex: MutableState<Int?>,
        editingMessageContent: MutableState<String>,
        chatScreenGestureConsumed: Boolean,
        onChatScreenGestureConsumed: (Boolean) -> Unit,
        currentDrag: Float,
        onCurrentDragChange: (Float) -> Unit,
        verticalDrag: Float,
        onVerticalDragChange: (Float) -> Unit,
        dragThreshold: Float,
        scrollState: ScrollState,
        autoScrollToBottom: Boolean,
        onAutoScrollToBottomChange: (Boolean) -> Unit,
        coroutineScope: CoroutineScope,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        chatHeaderTransparent: Boolean,
        chatHeaderHistoryIconColor: Int?,
        chatHeaderPipIconColor: Int?,
        chatHeaderOverlayMode: Boolean,
        chatStyle: ChatStyle, // Add chatStyle parameter
        historyListState: LazyListState,
        onSwitchCharacter: (String) -> Unit,
        chatAreaHorizontalPadding: Float = 16f // 聊天区域水平内边距
) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var showCharacterSelector by remember { mutableStateOf(false) }

    // 多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedMessageIndices by remember { mutableStateOf(setOf<Int>()) }
    val selectableMessageIndices = remember(chatHistory) {
        chatHistory.mapIndexedNotNull { index, message ->
            if (message.sender == "user" || message.sender == "ai") index else null
        }.toSet()
    }
    var isGeneratingImage by remember { mutableStateOf(false) }

    // 获取WebView状态
    val showWebView = actualViewModel.showWebView.collectAsState().value
    
    // 使用 rememberLocal 持久化历史记录显示设置
    var historyDisplayMode by rememberLocal(
        "chat_history_display_mode", 
        ChatHistoryDisplayMode.BY_FOLDER
    )
    var autoSwitchCharacterCard by rememberLocal(
        "chat_history_auto_switch_character_card", 
        false
    )
    
    // 同步 autoSwitchCharacterCard 到 ViewModel（ViewModel 内部逻辑需要这个值）
    LaunchedEffect(autoSwitchCharacterCard) {
        actualViewModel.setAutoSwitchCharacterCard(autoSwitchCharacterCard)
    }
    
    val currentChat = chatHistories.find { it.id == currentChatId }

    // 导出相关状态
    val context = LocalContext.current
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
    var editingMessageType by remember { mutableStateOf<String?>(null) }
    var pendingRollbackIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRewindIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRewindContent by remember { mutableStateOf<String?>(null) }
    var rollbackPreview by remember { mutableStateOf<List<WorkspaceBackupManager.WorkspaceFileChange>>(emptyList()) }
    var rewindPreview by remember { mutableStateOf<List<WorkspaceBackupManager.WorkspaceFileChange>>(emptyList()) }
    
    // 监听朗读状态
    val isPlaying by actualViewModel.isPlaying.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val activeCharacterCard by characterCardManager.activeCharacterCardFlow.collectAsState(initial = null)
    val displayedChatHistories =
            remember(chatHistories, activeCharacterCard, historyDisplayMode) {
                when (historyDisplayMode) {
                    ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> {
                        val activeCard = activeCharacterCard ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            val historyCard = history.characterCardName
                            if (activeCard.isDefault) {
                                historyCard == null || historyCard == activeCard.name
                            } else {
                                historyCard == activeCard.name
                            }
                        }
                    }

                    else -> {
                        chatHistories
                    }
                }
            }

    LaunchedEffect(activeCharacterCard, displayedChatHistories, currentChatId) {
        val activeCard = activeCharacterCard ?: return@LaunchedEffect
        if (displayedChatHistories.isEmpty()) {
            return@LaunchedEffect
        }
        val hasCurrentChatInFilter = displayedChatHistories.any { it.id == currentChatId }
        if (currentChatId.isBlank() || !hasCurrentChatInFilter) {
            actualViewModel.switchChat(displayedChatHistories.first().id)
        }
    }

    LaunchedEffect(pendingRollbackIndex) {
        val index = pendingRollbackIndex
        if (index != null) {
            rollbackPreview = actualViewModel.previewWorkspaceChangesForMessage(index)
        } else {
            rollbackPreview = emptyList()
        }
    }

    LaunchedEffect(pendingRewindIndex) {
        val index = pendingRewindIndex
        if (index != null) {
            rewindPreview = actualViewModel.previewWorkspaceChangesForMessage(index)
        } else {
            rewindPreview = emptyList()
        }
    }

    val onSelectMessageToEditCallback = remember(editingMessageIndex, editingMessageContent, editingMessageType) {
        { index: Int, message: ChatMessage, senderType: String ->
            editingMessageIndex.value = index
            editingMessageContent.value = message.content
            editingMessageType = senderType
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
        if (chatHeaderOverlayMode && chatHeaderTransparent) {
            // 覆盖模式：Header浮动在ChatArea之上
            Box(modifier = Modifier.fillMaxSize()) {
                ChatArea(
                        chatHistory = chatHistory,
                        scrollState = scrollState,
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
                        modifier = Modifier.fillMaxSize(),
                        onSelectMessageToEdit = onSelectMessageToEditCallback,
                        onDeleteMessage = { index -> actualViewModel.deleteMessage(index) },
                        onDeleteMessagesFrom = { index -> actualViewModel.deleteMessagesFrom(index) },
                        onRollbackToMessage = { index -> pendingRollbackIndex = index },
                        onSpeakMessage = { content -> actualViewModel.speakMessage(content) }, // 添加朗读回调
                        onAutoReadMessage = { content -> actualViewModel.enableAutoReadAndSpeak(content) }, // 添加自动朗读回调
                        onReplyToMessage = { message -> actualViewModel.setReplyToMessage(message) }, // 添加回复回调
                        onCreateBranch = { timestamp -> actualViewModel.createBranch(timestamp) }, // 添加创建分支回调
                        onInsertSummary = { index, message -> actualViewModel.insertSummary(index, message) }, // 添加插入总结回调
                        topPadding = headerHeight,
                        chatStyle = chatStyle, // Pass chat style
                        isMultiSelectMode = isMultiSelectMode,
                        selectedMessageIndices = selectedMessageIndices,
                        onToggleMultiSelectMode = { initialIndex ->
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) {
                                selectedMessageIndices = emptySet()
                            } else if (initialIndex != null) {
                                // 进入多选模式时，自动选中触发的消息
                                selectedMessageIndices = setOf(initialIndex)
                            }
                        },
                        onToggleMessageSelection = { index ->
                            selectedMessageIndices = if (selectedMessageIndices.contains(index)) {
                                selectedMessageIndices - index
                            } else {
                                selectedMessageIndices + index
                            }
                        },
                        horizontalPadding = chatAreaHorizontalPadding.dp
                )
                ChatScreenHeader(
                        modifier =
                                Modifier.onGloballyPositioned { coordinates ->
                                    headerHeight = with(density) { coordinates.size.height.toDp() }
                                },
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        chatHeaderTransparent = chatHeaderTransparent,
                        chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                        chatHeaderPipIconColor = chatHeaderPipIconColor,
                        onCharacterSwitcherClick = { showCharacterSelector = true }
                )
            }
        } else {
            // 正常模式：Header和ChatArea在Column中顺序排列
            Column(modifier = Modifier.fillMaxSize()) {
                ChatScreenHeader(
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        chatHeaderTransparent = chatHeaderTransparent,
                        chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                        chatHeaderPipIconColor = chatHeaderPipIconColor,
                        onCharacterSwitcherClick = { showCharacterSelector = true }
                )
                ChatArea(
                        chatHistory = chatHistory,
                        scrollState = scrollState,
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
                        modifier = Modifier.fillMaxSize(),
                        onSelectMessageToEdit = onSelectMessageToEditCallback,
                        onDeleteMessage = { index -> actualViewModel.deleteMessage(index) },
                        onDeleteMessagesFrom = { index -> actualViewModel.deleteMessagesFrom(index) },
                        onRollbackToMessage = { index -> pendingRollbackIndex = index },
                        onSpeakMessage = { content -> actualViewModel.speakMessage(content) }, // 添加朗读回调
                        onReplyToMessage = { message -> actualViewModel.setReplyToMessage(message) }, // 添加回复回调
                        onCreateBranch = { timestamp -> actualViewModel.createBranch(timestamp) }, // 添加创建分支回调
                        onInsertSummary = { index, message -> actualViewModel.insertSummary(index, message) }, // 添加插入总结回调
                        onAutoReadMessage = { content -> actualViewModel.enableAutoReadAndSpeak(content) }, // 添加自动朗读回调
                        chatStyle = chatStyle, // Pass chat style
                        isMultiSelectMode = isMultiSelectMode,
                        selectedMessageIndices = selectedMessageIndices,
                        horizontalPadding = chatAreaHorizontalPadding.dp,
                        onToggleMultiSelectMode = { initialIndex ->
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) {
                                selectedMessageIndices = emptySet()
                            } else if (initialIndex != null) {
                                // 进入多选模式时，自动选中触发的消息
                                selectedMessageIndices = setOf(initialIndex)
                            }
                        },
                        onToggleMessageSelection = { index ->
                            selectedMessageIndices = if (selectedMessageIndices.contains(index)) {
                                selectedMessageIndices - index
                            } else {
                                selectedMessageIndices + index
                            }
                        }
                )
            }
        }

        // 多选模式底部操作栏
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 显示选中数量
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 取消按钮移到左侧
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedMessageIndices = emptySet()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.exit_multi_select),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Text(
                            text = if (selectedMessageIndices.isEmpty()) {
                                stringResource(R.string.multi_select)
                            } else {
                                stringResource(R.string.selected_count, selectedMessageIndices.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelectableSelected =
                            selectableMessageIndices.isNotEmpty() &&
                                    selectableMessageIndices.all { selectedMessageIndices.contains(it) }

                        TextButton(
                            onClick = {
                                selectedMessageIndices =
                                        if (allSelectableSelected) {
                                            emptySet()
                                        } else {
                                            selectableMessageIndices
                                        }
                            },
                            enabled = selectableMessageIndices.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(
                                        if (allSelectableSelected) R.string.clear_selection else R.string.select_all_messages
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 分享按钮
                        FilledIconButton(
                            onClick = {
                                if (selectedMessageIndices.isNotEmpty() && !isGeneratingImage) {
                                    isGeneratingImage = true
                                    
                                    actualViewModel.shareMessages(
                                        context = context,
                                        messageIndices = selectedMessageIndices,
                                        userMessageColor = userMessageColor,
                                        aiMessageColor = aiMessageColor,
                                        userTextColor = userTextColor,
                                        aiTextColor = aiTextColor,
                                        systemMessageColor = systemMessageColor,
                                        systemTextColor = systemTextColor,
                                        thinkingBackgroundColor = thinkingBackgroundColor,
                                        thinkingTextColor = thinkingTextColor,
                                        chatStyle = chatStyle,
                                        onSuccess = { uri ->
                                            isGeneratingImage = false
                                            
                                            // 调用系统分享
                                            try {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(shareIntent, context.getString(R.string.share_selected))
                                                )
                                                
                                                // 退出多选模式
                                                isMultiSelectMode = false
                                                selectedMessageIndices = emptySet()
                                            } catch (e: Exception) {
                                                AppLogger.e("ChatScreenContent", "分享失败", e)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.share_failed),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onError = { error ->
                                            isGeneratingImage = false
                                            AppLogger.e("ChatScreenContent", "生成图片失败: $error")
                                            android.widget.Toast.makeText(
                                                context,
                                                error,
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            },
                            enabled = selectedMessageIndices.isNotEmpty() && !isGeneratingImage,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_selected),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 删除按钮
                        FilledIconButton(
                            onClick = {
                                if (selectedMessageIndices.isNotEmpty()) {
                                    actualViewModel.deleteMessages(selectedMessageIndices)
                                    selectedMessageIndices = emptySet()
                                    isMultiSelectMode = false
                                }
                            },
                            enabled = selectedMessageIndices.isNotEmpty(),
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_selected),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 停止朗读按钮
        AnimatedVisibility(
            visible = isPlaying || isAutoReadEnabled,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (isAutoReadEnabled) {
                        actualViewModel.disableAutoRead()
                    } else {
                        actualViewModel.stopSpeaking()
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.stop_reading),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 角色选择器
        CharacterSelectorPanel(
            isVisible = showCharacterSelector,
            onDismiss = { showCharacterSelector = false },
            onSelectCharacter = onSwitchCharacter
        )

        // 遮罩层 - 独立的淡入淡出效果，覆盖整个屏幕
        AnimatedVisibility(
                visible = showChatHistorySelector,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
        ) {
            Box(
                    modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    actualViewModel.toggleChatHistorySelector()
                                }
                            }
                            .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        // 历史选择器面板 - 滑入滑出效果
        AnimatedVisibility(
                visible = showChatHistorySelector,
                enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)),
                exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.TopStart)
        ) {
            val chatHistorySearchQuery by actualViewModel.chatHistorySearchQuery.collectAsState()
            ChatHistorySelectorPanel(
                    actualViewModel = actualViewModel,
                    chatHistories = displayedChatHistories,
                    currentChatId = currentChatId,
                    showChatHistorySelector = showChatHistorySelector,
                    historyListState = historyListState,
                    searchQuery = chatHistorySearchQuery,
                    onSearchQueryChange = actualViewModel::onChatHistorySearchQueryChange,
                    activeCharacterCard = activeCharacterCard,
                    historyDisplayMode = historyDisplayMode,
                    onDisplayModeChange = { historyDisplayMode = it },
                    autoSwitchCharacterCard = autoSwitchCharacterCard,
                    onAutoSwitchCharacterCardChange = { autoSwitchCharacterCard = it }
            )
        }

        // 滚动到底部按钮
        ScrollToBottomButton(
            scrollState = scrollState,
            coroutineScope = coroutineScope,
            autoScrollToBottom = autoScrollToBottom,
            onAutoScrollToBottomChange = onAutoScrollToBottomChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )


        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = { showAndroidExportDialog = true },
                    onSelectWindows = { showWindowsExportDialog = true }
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
                    onCancel = { showExportProgressDialog = false }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { filePath ->
                        try {
                            val file = File(filePath)
                            val fileUri =
                                    FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName +
                                                    ".fileprovider",
                                            file
                                    )
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                    fileUri,
                                    "application/vnd.android.package-archive"
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            AppLogger.e("ChatScreenContent", "无法打开文件: $filePath", e)
                        } catch (e: Exception) {
                            AppLogger.e("ChatScreenContent", "文件操作错误: ${e.message}", e)
                        }
                    }
            )
        }

        // 当需要编辑消息时，显示消息编辑器
        if (editingMessageIndex.value != null) {
            MessageEditor(
                editingMessageContent = editingMessageContent,
                onCancel = {
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                onSave = {
                    val index = editingMessageIndex.value
                    if (index != null) {
                        val editedMessage =
                            chatHistory[index].copy(
                                content = editingMessageContent.value,
                                contentStream = null // 修复：清除stream，强制UI使用content
                            )
                        actualViewModel.updateMessage(index, editedMessage)
                    }
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                onResend = {
                    val index = editingMessageIndex.value
                    if (index != null) {
                        val currentChat = chatHistories.find { it.id == currentChatId }
                        val hasWorkspace = !currentChat?.workspace.isNullOrBlank()

                        if (hasWorkspace) {
                            pendingRewindIndex = index
                            pendingRewindContent = editingMessageContent.value
                        } else {
                            // 没有绑定工作区时，直接执行编辑并重发，无需确认弹窗
                            actualViewModel.rewindAndResendMessage(index, editingMessageContent.value)
                        }
                    }
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                showResendButton = editingMessageType == "user"
            )
        }

        if (pendingRollbackIndex != null) {
            WorkspaceChangeConfirmDialog(
                mode = WorkspaceChangeConfirmMode.ROLLBACK,
                changes = rollbackPreview,
                onConfirm = {
                    val index = pendingRollbackIndex
                    if (index != null) {
                        actualViewModel.rollbackToMessage(index)
                    }
                    pendingRollbackIndex = null
                },
                onDismiss = {
                    pendingRollbackIndex = null
                }
            )
        }

        if (pendingRewindIndex != null && pendingRewindContent != null) {
            WorkspaceChangeConfirmDialog(
                mode = WorkspaceChangeConfirmMode.EDIT_AND_RESEND,
                changes = rewindPreview,
                onConfirm = {
                    val index = pendingRewindIndex
                    val content = pendingRewindContent
                    if (index != null && content != null) {
                        actualViewModel.rewindAndResendMessage(index, content)
                    }
                    pendingRewindIndex = null
                    pendingRewindContent = null
                },
                onDismiss = {
                    pendingRewindIndex = null
                    pendingRewindContent = null
                }
            )
        }
    }
}

@Composable
fun ChatHistorySelectorPanel(
        actualViewModel: ChatViewModel,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        showChatHistorySelector: Boolean,
        historyListState: LazyListState,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        activeCharacterCard: CharacterCard?,
        historyDisplayMode: ChatHistoryDisplayMode,
        onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
        autoSwitchCharacterCard: Boolean,
        onAutoSwitchCharacterCardChange: (Boolean) -> Unit
) {
    // 历史选择器面板（不再包含遮罩层，遮罩层已在外部处理）
    Box(
            modifier =
                    Modifier.width(280.dp)
                            .fillMaxHeight()
                            .background(
                                    color =
                                            MaterialTheme.colorScheme.surface.copy(
                                                    alpha = 0.95f
                                            ),
                                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                            )
    ) {
        if (activeCharacterCard == null) {
            Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 直接使用ChatHistorySelector
            ChatHistorySelector(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    onNewChat = { characterCardName ->
                        actualViewModel.createNewChat(characterCardName)
                        // 创建新对话后自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onSelectChat = { chatId ->
                        actualViewModel.switchChat(chatId)
                        // 切换聊天后也自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onDeleteChat = { chatId -> actualViewModel.deleteChatHistory(chatId) },
                    onUpdateChatTitle = { chatId, newTitle ->
                        actualViewModel.updateChatTitle(chatId, newTitle)
                    },
                    onUpdateChatBinding = { chatId, characterCardName ->
                        actualViewModel.updateChatCharacterCardBinding(chatId, characterCardName)
                    },
                    onCreateGroup = { groupName, characterCardName -> 
                        actualViewModel.createGroup(groupName, characterCardName)
                    },
                    onUpdateChatOrderAndGroup = { reorderedHistories, movedItem, targetGroup ->
                        actualViewModel.updateChatOrderAndGroup(
                                reorderedHistories,
                                movedItem,
                                targetGroup
                        )
                    },
                    onUpdateGroupName = { oldName, newName, characterCardName ->
                        actualViewModel.updateGroupName(oldName, newName, characterCardName)
                    },
                    onDeleteGroup = { groupName, deleteChats, characterCardName ->
                        actualViewModel.deleteGroup(groupName, deleteChats, characterCardName)
                    },
                    chatHistories = chatHistories,
                    currentId = currentChatId,
                    lazyListState = historyListState,
                    onBack = { actualViewModel.toggleChatHistorySelector() },
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    historyDisplayMode = historyDisplayMode,
                    onDisplayModeChange = onDisplayModeChange,
                    autoSwitchCharacterCard = autoSwitchCharacterCard,
                    onAutoSwitchCharacterCardChange = onAutoSwitchCharacterCardChange,
                    activeCharacterCard = activeCharacterCard
            )
        }
    }
}
