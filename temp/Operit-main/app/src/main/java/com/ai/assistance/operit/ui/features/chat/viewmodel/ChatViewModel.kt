package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextFieldValue.Companion
import androidx.core.content.FileProvider
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.floating.ui.pet.AvatarEmotionManager
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.CommandConfig
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.util.TtsCleaner
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
// 使用 services/core 的 Delegate 类
import com.ai.assistance.operit.services.core.MessageProcessingDelegate
import com.ai.assistance.operit.services.core.ChatHistoryDelegate
import com.ai.assistance.operit.services.core.ApiConfigDelegate
import com.ai.assistance.operit.services.core.TokenStatisticsDelegate
import com.ai.assistance.operit.services.core.AttachmentDelegate
import com.ai.assistance.operit.services.core.MessageCoordinationDelegate
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.ui.features.chat.util.MessageImageGenerator

enum class ChatHistoryDisplayMode {
    BY_CHARACTER_CARD,
    BY_FOLDER,
    CURRENT_CHARACTER_ONLY
}

class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // 添加语音服务
    private var voiceService: VoiceService? = null
    private val speechServicesPreferences = SpeechServicesPreferences(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)

    // 添加语音播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 添加自动朗读状态 - Now managed by ApiConfigDelegate
    val isAutoReadEnabled: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAutoRead }

    // 添加回复相关状态
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    // API服务
    private var enhancedAiService: EnhancedAIService? = null

    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    // 终端管理器（用于执行工作区命令）
    @RequiresApi(Build.VERSION_CODES.O)
    private val terminal: Terminal? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Terminal.getInstance(context)
    } else {
        null
    }
    
    // 工作区终端会话映射表：workspacePath -> sessionId
    private val workspaceTerminalSessions = mutableMapOf<String, String>()

    // 附件管理器 - 使用 services/core 版本
    private val attachmentDelegate = AttachmentDelegate(context, toolHandler)

    // 委托类 - 使用 services/core 版本
    val uiStateDelegate = UiStateDelegate()
    private val tokenStatsDelegate =
            TokenStatisticsDelegate(
                    coroutineScope = viewModelScope,  // 改用 coroutineScope 参数
                    getEnhancedAiService = { enhancedAiService }
            )
    private val apiConfigDelegate =
            ApiConfigDelegate(
                    context = context,
                    coroutineScope = viewModelScope,  // 改用 coroutineScope 参数
                    onConfigChanged = { service ->
                        enhancedAiService = service
                        // API配置变更后，异步设置服务收集器
                        viewModelScope.launch {
                            AppLogger.d(TAG, "API配置变更，设置 token 统计收集器")
                            tokenStatsDelegate.setupCollectors()
                        }
                        // 设置输入处理状态监听
                        setupInputProcessingStateListener(service)
                    }
            )


    // Break circular dependency with lateinit
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var floatingWindowDelegate: FloatingWindowDelegate
    private lateinit var messageCoordinationDelegate: MessageCoordinationDelegate

    // Use lazy initialization for exposed properties to avoid circular reference issues
    // API配置相关
    val apiKey: StateFlow<String> by lazy { apiConfigDelegate.apiKey }
    val apiEndpoint: StateFlow<String> by lazy { apiConfigDelegate.apiEndpoint }
    val modelName: StateFlow<String> by lazy { apiConfigDelegate.modelName }
    val apiProviderType: StateFlow<ApiProviderType> by lazy { apiConfigDelegate.apiProviderType }
    val isConfigured: StateFlow<Boolean> by lazy { apiConfigDelegate.isConfigured }
    val isApiConfigInitialized: StateFlow<Boolean> by lazy { apiConfigDelegate.isInitialized }

    private val _shouldShowConfigDialog = MutableStateFlow(false)
    val shouldShowConfigDialog: StateFlow<Boolean> = _shouldShowConfigDialog.asStateFlow()

    fun onConfigDialogConfirmed() {
        _shouldShowConfigDialog.value = false
    }

    fun showConfigurationScreen() {
        _shouldShowConfigDialog.value = true
    }

    val enableAiPlanning: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAiPlanning }
    val keepScreenOn: StateFlow<Boolean> by lazy { apiConfigDelegate.keepScreenOn }

    // 思考模式和思考引导状态现在由ApiConfigDelegate管理
    val enableThinkingMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingMode }
    val enableThinkingGuidance: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingGuidance }
    val enableMemoryQuery: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMemoryQuery }
    val enableTools: StateFlow<Boolean> by lazy { apiConfigDelegate.enableTools }
    val disableStreamOutput: StateFlow<Boolean> by lazy { apiConfigDelegate.disableStreamOutput }

    val summaryTokenThreshold: StateFlow<Float> by lazy { apiConfigDelegate.summaryTokenThreshold }
    val enableSummary: StateFlow<Boolean> by lazy { apiConfigDelegate.enableSummary }
    val enableSummaryByMessageCount: StateFlow<Boolean> by lazy { apiConfigDelegate.enableSummaryByMessageCount }
    val summaryMessageCountThreshold: StateFlow<Int> by lazy { apiConfigDelegate.summaryMessageCountThreshold }

    // 上下文长度
    val maxWindowSizeInK: StateFlow<Float> by lazy { apiConfigDelegate.contextLength }
    val baseContextLengthInK: StateFlow<Float> by lazy { apiConfigDelegate.baseContextLength }
    val maxContextLengthInK: StateFlow<Float> by lazy { apiConfigDelegate.maxContextLengthSetting }
    val enableMaxContextMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMaxContextMode }

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>> by lazy { chatHistoryDelegate.chatHistory }
    val showChatHistorySelector: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.showChatHistorySelector
    }
    val chatHistories: StateFlow<List<ChatHistory>> by lazy { chatHistoryDelegate.chatHistories }
    val currentChatId: StateFlow<String?> by lazy { chatHistoryDelegate.currentChatId }

    // 消息处理相关
    val userMessage: StateFlow<TextFieldValue> by lazy { messageProcessingDelegate.userMessage }
    val isLoading: StateFlow<Boolean> by lazy { messageProcessingDelegate.isLoading }
    val inputProcessingState: StateFlow<InputProcessingState> by lazy {
        messageProcessingDelegate.inputProcessingState
    }

    // 会话隔离：仅当“当前聊天ID == 正在流式的聊天ID”时，才显示处理中/停止按钮
    val activeStreamingChatId: StateFlow<String?> by lazy { messageProcessingDelegate.activeStreamingChatId }
    val currentChatIsLoading: StateFlow<Boolean> by lazy {
        kotlinx.coroutines.flow.combine(
            messageProcessingDelegate.isLoading,
            chatHistoryDelegate.currentChatId,
            messageProcessingDelegate.activeStreamingChatId
        ) { isLoading, currentId, activeId ->
            isLoading && activeId != null && activeId == currentId
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = false
        )
    }
    val currentChatInputProcessingState: StateFlow<InputProcessingState> by lazy {
        kotlinx.coroutines.flow.combine(
            messageProcessingDelegate.inputProcessingState,
            chatHistoryDelegate.currentChatId,
            messageProcessingDelegate.activeStreamingChatId
        ) { state, currentId, activeId ->
            if (activeId != null && activeId == currentId) state
            else InputProcessingState.Idle
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = InputProcessingState.Idle
        )
    }

    val scrollToBottomEvent: SharedFlow<Unit> by lazy {
        messageProcessingDelegate.scrollToBottomEvent
    }

    // UI状态相关
    val errorMessage: StateFlow<String?> by lazy { uiStateDelegate.errorMessage }
    val popupMessage: StateFlow<String?> by lazy { uiStateDelegate.popupMessage }
    val toastEvent: StateFlow<String?> by lazy { uiStateDelegate.toastEvent }
    val masterPermissionLevel: StateFlow<PermissionLevel> by lazy {
        uiStateDelegate.masterPermissionLevel
    }

    // 聊天统计相关
    val currentWindowSize: StateFlow<Int> by lazy { tokenStatsDelegate.currentWindowSizeFlow }
    val inputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeInputTokensFlow }
    val outputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeOutputTokensFlow }
    val perRequestTokenCount: StateFlow<Pair<Int, Int>?> by lazy { tokenStatsDelegate.perRequestTokenCountFlow }



    // 悬浮窗相关
    val isFloatingMode: StateFlow<Boolean> by lazy { floatingWindowDelegate.isFloatingMode }

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>> by lazy { attachmentDelegate.attachments }

    // 聊天历史搜索状态
    private val _chatHistorySearchQuery = MutableStateFlow("")
    val chatHistorySearchQuery: StateFlow<String> = _chatHistorySearchQuery.asStateFlow()

    fun onChatHistorySearchQueryChange(query: String) {
        _chatHistorySearchQuery.value = query
    }

    fun setHistoryDisplayMode(mode: ChatHistoryDisplayMode) {
        _historyDisplayMode.value = mode
    }

    fun setAutoSwitchCharacterCard(enabled: Boolean) {
        _autoSwitchCharacterCard.value = enabled
    }

    fun toggleAutoSwitchCharacterCard() {
        setAutoSwitchCharacterCard(!_autoSwitchCharacterCard.value)
    }

    private val _historyDisplayMode =
            MutableStateFlow(ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY)
    val historyDisplayMode: StateFlow<ChatHistoryDisplayMode> = _historyDisplayMode.asStateFlow()

    private val _autoSwitchCharacterCard = MutableStateFlow(false)
    val autoSwitchCharacterCard: StateFlow<Boolean> = _autoSwitchCharacterCard.asStateFlow()
    
    // 总结状态
    val isSummarizing: StateFlow<Boolean> by lazy {
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.isSummarizing
        } else {
            MutableStateFlow(false)
        }
    }

    // 添加一个用于跟踪附件面板状态的变量
    private val _attachmentPanelState = MutableStateFlow(false)
    val attachmentPanelState: StateFlow<Boolean> = _attachmentPanelState

    // 添加WebView显示状态的状态流
    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView

    // 添加工作区状态
    val isWorkspaceOpen: StateFlow<Boolean> by lazy {
        combine(currentChatId, chatHistories) { id, histories ->
            histories.find { it.id == id }?.workspace?.isNotBlank() == true
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }

    // 添加AI电脑显示状态的状态流
    private val _showAiComputer = MutableStateFlow(false)
    val showAiComputer: StateFlow<Boolean> = _showAiComputer

    // 添加WebView刷新控制流 - 使用Int计数器避免重复刷新问题
    private val _webViewRefreshCounter = MutableStateFlow(0)
    val webViewRefreshCounter: StateFlow<Int> = _webViewRefreshCounter

    // 控制工作区文件选择器的可见性
    private val _showWorkspaceFileSelector = MutableStateFlow(false)
    val showWorkspaceFileSelector: StateFlow<Boolean> = _showWorkspaceFileSelector.asStateFlow()

    // 工作区文件搜索词
    private val _workspaceFileSearchQuery = MutableStateFlow("")
    val workspaceFileSearchQuery: StateFlow<String> = _workspaceFileSearchQuery.asStateFlow()

    // 文件选择相关回调
    private var fileChooserCallback: ((Int, Intent?) -> Unit)? = null

    init {
        // Initialize delegates in correct order to avoid circular references
        initializeDelegates()

        // Setup additional components
        setupPermissionSystemCollection()
        setupAttachmentDelegateToastCollection()

        // 初始化语音服务
        initializeVoiceService()

        // 观察ApiConfigDelegate的初始化状态
        viewModelScope.launch {
            isApiConfigInitialized.collect { initialized ->
                if (initialized) {
                    checkConfigAndShowDialog()
                }
            }
        }
    }

    private fun initializeDelegates() {
        // First initialize chat history delegate
        chatHistoryDelegate =
                ChatHistoryDelegate(
                        context = context,
                        coroutineScope = viewModelScope,
                        onTokenStatisticsLoaded = { inputTokens, outputTokens, windowSize ->
                            tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens, windowSize)
                        },
                        getEnhancedAiService = { enhancedAiService },
                        ensureAiServiceAvailable = { ensureAiServiceAvailable() },
                        getChatStatistics = {
                            val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
                            val windowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                            Triple(inputTokens, outputTokens, windowSize)
                        },
                        onScrollToBottom = { messageProcessingDelegate.scrollToBottom() }
                )

        // Then initialize message processing delegate
        messageProcessingDelegate =
                MessageProcessingDelegate(
                        context = context,
                        coroutineScope = viewModelScope,  // 改用 coroutineScope 参数
                        getEnhancedAiService = { enhancedAiService },
                        getChatHistory = { chatHistoryDelegate.chatHistory.value },
                        addMessageToChat = { targetChatId, message ->
                            // 将消息固定写入指定聊天，避免在切换会话后串流到新会话
                            // 这是suspend函数，在suspend上下文中会等待完成
                            chatHistoryDelegate.addMessageToChat(message, targetChatId)
                        },
                        saveCurrentChat = {
                            val (inputTokens, outputTokens) =
                                    tokenStatsDelegate.getCumulativeTokenCounts()
                            val currentWindowSize =
                                    tokenStatsDelegate.getLastCurrentWindowSize()
                            chatHistoryDelegate.saveCurrentChat(
                                inputTokens,
                                outputTokens,
                                currentWindowSize
                            )
                            // 立即更新UI上的实际窗口大小
                            if (currentWindowSize > 0) {
                                // uiStateDelegate.updateCurrentWindowSize(currentWindowSize)
                            }
                        },
                        showErrorMessage = { message -> uiStateDelegate.showErrorMessage(message) },
                        updateChatTitle = { chatId, title ->
                            chatHistoryDelegate.updateChatTitle(chatId, title)
                        },
                        onTurnComplete = {
                            // 轮次完成后，更新累计统计并保存聊天
                            tokenStatsDelegate.updateCumulativeStatistics()
                            val (inputTokens, outputTokens) =
                                tokenStatsDelegate.getCumulativeTokenCounts()
                            val windowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, windowSize)
                            
                            // 如果悬浮窗正在运行，通知其重新加载消息
                            if (isFloatingMode.value) {
                                viewModelScope.launch {
                                    floatingWindowDelegate.notifyFloatingServiceReload()
                                }
                            }
                        },
                        // 传递自动朗读状态和方法
                        getIsAutoReadEnabled = { isAutoReadEnabled.value },
                        speakMessage = ::speakMessage,
                        onTokenLimitExceeded = {
                            messageCoordinationDelegate.handleTokenLimitExceeded()
                        }
                )

        // Initialize message coordination delegate
        messageCoordinationDelegate = 
                MessageCoordinationDelegate(
                        coroutineScope = viewModelScope,
                        chatHistoryDelegate = chatHistoryDelegate,
                        messageProcessingDelegate = messageProcessingDelegate,
                        tokenStatsDelegate = tokenStatsDelegate,
                        apiConfigDelegate = apiConfigDelegate,
                        attachmentDelegate = attachmentDelegate,
                        uiStateDelegate = uiStateDelegate,
                        getEnhancedAiService = { enhancedAiService },
                        updateWebServerForCurrentChat = ::updateWebServerForCurrentChat,
                        resetAttachmentPanelState = ::resetAttachmentPanelState,
                        clearReplyToMessage = ::clearReplyToMessage,
                        getReplyToMessage = { replyToMessage.value }
                )

        // Finally initialize floating window delegate
        floatingWindowDelegate =
                FloatingWindowDelegate(
                        context = context,
                        coroutineScope = viewModelScope,
                        inputProcessingState = this.inputProcessingState,
                        chatHistoryFlow = chatHistoryDelegate.chatHistory,
                        chatHistoryDelegate = chatHistoryDelegate
                )
    }

    private fun setupPermissionSystemCollection() {
        viewModelScope.launch {
            toolPermissionSystem.masterSwitchFlow.collect { level ->
                uiStateDelegate.updateMasterPermissionLevel(level)
            }
        }
    }

    private fun setupAttachmentDelegateToastCollection() {
        viewModelScope.launch {
            attachmentDelegate.toastEvent.collect { message -> uiStateDelegate.showToast(message) }
        }
    }

    private fun checkIfShouldCreateNewChat() {
        viewModelScope.launch {
            // 检查历史记录加载后是否需要创建新聊天
            if (chatHistoryDelegate.checkIfShouldCreateNewChat() && isConfigured.value) {
                chatHistoryDelegate.createNewChat()
            }
        }
    }

    /** 设置服务相关的流收集逻辑 */
    /**
     * 设置输入处理状态监听
     * 当 EnhancedAIService 初始化或更新时调用
     */
    private fun setupInputProcessingStateListener(service: EnhancedAIService) {
        AppLogger.d(TAG, "EnhancedAIService 已就绪，开始监听输入处理状态")
        viewModelScope.launch {
            try {
                service.inputProcessingState.collect { state ->
                    if (state is InputProcessingState.Completed && 
                        ::messageCoordinationDelegate.isInitialized &&
                        (messageCoordinationDelegate.isSummarizing.value ||
                         messageCoordinationDelegate.isSendTriggeredSummarizing.value)
                    ) {
                        messageProcessingDelegate.handleInputProcessingState(
                            InputProcessingState.Summarizing("正在总结记忆...")
                        )
                    } else if (::messageProcessingDelegate.isInitialized) {
                        messageProcessingDelegate.handleInputProcessingState(state)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "输入处理状态收集出错: ${e.message}", e)
                uiStateDelegate.showErrorMessage("输入处理状态收集失败: ${e.message}")
            }
        }
    }

    // API配置相关方法
    fun updateApiKey(key: String) = apiConfigDelegate.updateApiKey(key)

    fun updateApiEndpoint(endpoint: String) = apiConfigDelegate.updateApiEndpoint(endpoint)

    fun updateModelName(modelName: String) = apiConfigDelegate.updateModelName(modelName)

    fun updateApiProviderType(providerType: ApiProviderType) = apiConfigDelegate.updateApiProviderType(providerType)
    fun saveApiSettings() = apiConfigDelegate.saveApiSettings()
    fun useDefaultConfig() {
        if (apiConfigDelegate.useDefaultConfig()) {
            uiStateDelegate.showToast("使用默认配置继续")
        } else {
            // 修改：使用错误弹窗而不是Toast显示配置错误
            uiStateDelegate.showErrorMessage("默认配置不完整，请填写必要信息")
        }
    }
    fun toggleAiPlanning() {
        apiConfigDelegate.toggleAiPlanning()
        // 移除Toast提示
    }

    // 切换思考模式的方法现在委托给ApiConfigDelegate
    fun toggleThinkingMode() {
        apiConfigDelegate.toggleThinkingMode()
    }

    // 切换思考引导的方法现在委托给ApiConfigDelegate
    fun toggleThinkingGuidance() {
        apiConfigDelegate.toggleThinkingGuidance()
    }

    // 切换记忆附着的方法现在委托给ApiConfigDelegate
    fun toggleMemoryQuery() {
        apiConfigDelegate.toggleMemoryQuery()
    }

    // 更新上下文长度
    fun updateContextLength(length: Float) {
        apiConfigDelegate.updateContextLength(length)
    }

    fun updateMaxContextLength(length: Float) {
        apiConfigDelegate.updateMaxContextLength(length)
    }

    fun toggleEnableMaxContextMode() {
        apiConfigDelegate.toggleEnableMaxContextMode()
    }

    fun updateSummaryTokenThreshold(threshold: Float) {
        apiConfigDelegate.updateSummaryTokenThreshold(threshold)
    }

    fun toggleEnableSummary() {
        apiConfigDelegate.toggleEnableSummary()
    }

    fun toggleEnableSummaryByMessageCount() {
        apiConfigDelegate.toggleEnableSummaryByMessageCount()
    }

    fun updateSummaryMessageCountThreshold(threshold: Int) {
        apiConfigDelegate.updateSummaryMessageCountThreshold(threshold)
    }

    fun toggleTools() {
        apiConfigDelegate.toggleTools()
    }

    fun toggleDisableStreamOutput() {
        apiConfigDelegate.toggleDisableStreamOutput()
    }

    // 聊天历史相关方法
    fun createNewChat(characterCardName: String? = null) {
        chatHistoryDelegate.createNewChat(characterCardName)
    }

    fun switchChat(chatId: String) {
        chatHistoryDelegate.switchChat(chatId)

        // 如果当前WebView正在显示，则更新工作区并触发刷新
        if (_showWebView.value) {
            updateWebServerForCurrentChat(chatId)
            // 延迟一点时间再触发刷新，等待服务器工作区更新完成
            viewModelScope.launch {
                refreshWebView()
            }
        }

        if (_autoSwitchCharacterCard.value) {
            viewModelScope.launch {
                autoSwitchCharacterCardForChat(chatId)
            }
        }
    }

    fun deleteChatHistory(chatId: String) {
        chatHistoryDelegate.deleteChatHistory(chatId) { deleted ->
            if (!deleted) {
                uiStateDelegate.showToast("该聊天已锁定，无法删除")
            }
        }
    }

    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat { deleted ->
            if (deleted) {
                uiStateDelegate.showToast("聊天记录已清空")
            } else {
                uiStateDelegate.showToast("该聊天已锁定，无法删除")
            }
        }
    }
    fun toggleChatHistorySelector() = chatHistoryDelegate.toggleChatHistorySelector()
    fun showChatHistorySelector(show: Boolean) {
        chatHistoryDelegate.showChatHistorySelector(show)
    }

    private suspend fun autoSwitchCharacterCardForChat(chatId: String) {
        val targetHistory = chatHistories.value.firstOrNull { it.id == chatId } ?: return
        val targetCardName = targetHistory.characterCardName ?: return

        runCatching {
            val targetCard = characterCardManager.findCharacterCardByName(targetCardName) ?: return
            val activeId = characterCardManager.activeCharacterCardIdFlow.first()
            if (activeId != targetCard.id) {
                characterCardManager.setActiveCharacterCard(targetCard.id)
            }
        }.onFailure { throwable ->
            AppLogger.w(TAG, "Auto switch character card failed: ${throwable.message}")
        }
    }

    /** 创建对话分支 */
    fun createBranch(upToMessageTimestamp: Long? = null) {
        chatHistoryDelegate.createBranch(upToMessageTimestamp)
        uiStateDelegate.showToast("已创建对话分支")
    }

    /** 插入总结 */
    fun insertSummary(index: Int, message: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId == null) {
                    uiStateDelegate.showToast("当前没有活跃的对话")
                    return@launch
                }
                
                // 先设置activeStreamingChatId，确保UI能显示状态
                messageProcessingDelegate.setActiveStreamingChatId(currentChatId)
                // 设置输入处理状态
                messageProcessingDelegate.setInputProcessingState(true, "正在生成总结...")
                
                val currentHistory = chatHistoryDelegate.chatHistory.value
                
                // 确定插入位置：用户消息插入在上面（index），AI消息插入在下面（index+1）
                val insertPosition = if (message.sender == "user") {
                    index
                } else {
                    index + 1
                }
                
                // 获取要总结的消息：从开始到插入位置的消息
                val messagesToSummarize = currentHistory.subList(0, insertPosition)
                    .filter { it.sender == "user" || it.sender == "ai" }
                
                if (messagesToSummarize.isEmpty()) {
                    uiStateDelegate.showToast("没有可总结的消息")
                    messageProcessingDelegate.setInputProcessingState(false, "")
                    messageProcessingDelegate.setActiveStreamingChatId(null)
                    return@launch
                }
                
                // 显示生成中提示
                uiStateDelegate.showToast("正在生成总结...")
                
                // 调用AI生成总结
                if (enhancedAiService == null) {
                    uiStateDelegate.showToast("AI服务未初始化")
                    messageProcessingDelegate.setInputProcessingState(false, "")
                    messageProcessingDelegate.setActiveStreamingChatId(null)
                    return@launch
                }
                
                val summaryMessage = AIMessageManager.summarizeMemory(
                    enhancedAiService!!,
                    messagesToSummarize,
                    autoContinue = false
                )
                
                if (summaryMessage != null) {
                    // 插入总结消息
                    chatHistoryDelegate.addSummaryMessage(summaryMessage, insertPosition)

                    // 插入总结后，重新计算窗口大小并保存
                    val newHistoryForTokens =
                        AIMessageManager.getMemoryFromMessages(chatHistoryDelegate.chatHistory.value)
                    val chatService = enhancedAiService!!.getAIServiceForFunction(FunctionType.CHAT)
                    val newWindowSize = chatService.calculateInputTokens("", newHistoryForTokens)
                    val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
                    chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, newWindowSize)
                    tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens, newWindowSize)

                    uiStateDelegate.showToast("总结已插入")
                } else {
                    uiStateDelegate.showToast("生成总结失败")
                }
                
                // 清除输入处理状态
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "插入总结时发生错误", e)
                uiStateDelegate.showToast("插入总结失败: ${e.message}")
                // 发生错误时也需要清除状态
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            }
        }
    }

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        AppLogger.d(TAG, "准备删除消息，索引: $index")
        chatHistoryDelegate.deleteMessage(index)
    }

    /** 从指定索引删除后续所有消息 */
    fun deleteMessagesFrom(index: Int) {
        viewModelScope.launch {
            AppLogger.d(TAG, "准备从索引 $index 开始删除后续消息")
            chatHistoryDelegate.deleteMessagesFrom(index)
        }
    }

    /** 批量删除消息 */
    fun deleteMessages(indices: Set<Int>) {
        viewModelScope.launch {
            AppLogger.d(TAG, "准备批量删除消息，索引: $indices")
            // 按降序排列索引后依次删除，避免索引偏移问题
            val sortedIndices = indices.sortedDescending()
            sortedIndices.forEach { index ->
                chatHistoryDelegate.deleteMessage(index)
            }
            AppLogger.d(TAG, "批量删除完成")
        }
    }

    /** 分享消息为图片 */
    fun shareMessages(
        context: Context,
        messageIndices: Set<Int>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        chatStyle: ChatStyle,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "开始生成分享图片，消息索引: $messageIndices")
                
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value
                
                // 验证索引有效性
                if (messageIndices.any { it < 0 || it >= currentHistory.size }) {
                    onError("无效的消息索引")
                    return@launch
                }
                
                // 获取选中的消息
                val selectedMessages = messageIndices.sorted().map { currentHistory[it] }
                
                AppLogger.d(TAG, "准备生成图片，选中消息数量: ${selectedMessages.size}")
                
                // 生成图片（内部会自动处理线程切换）
                val imageFile = MessageImageGenerator
                    .generateMessageImage(
                        context = context,
                        messages = selectedMessages,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        chatStyle = chatStyle
                    )
                
                AppLogger.d(TAG, "图片文件生成成功: ${imageFile.absolutePath}, 大小: ${imageFile.length()} bytes")
                
                // 使用 FileProvider 获取 Uri
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )
                
                AppLogger.d(TAG, "Uri 获取成功: $uri")
                
                onSuccess(uri)
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成分享图片失败", e)
                onError("生成图片失败: ${e.message}")
            }
        }
    }

    fun saveCurrentChat() {
        val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
        val currentWindowSize = tokenStatsDelegate.getLastCurrentWindowSize()
        chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, currentWindowSize)
    }

    // 添加消息编辑方法
    fun updateMessage(index: Int, editedMessage: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage("无效的消息索引")
                    return@launch
                }

                // 更新消息
                currentHistory[index] = editedMessage

                // 将更新后的历史记录保存到ChatHistoryDelegate
                // 注意：这里仅更新内存，因为此方法只用于单个消息内容的修改，不涉及历史截断
                chatHistoryDelegate.updateChatHistory(currentHistory)

                // 直接在数据库中更新该条消息
                chatHistoryDelegate.addMessageToChat(editedMessage)

                // 更新统计信息并保存
                tokenStatsDelegate.updateCumulativeStatistics()
                val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
                val currentWindowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                chatHistoryDelegate.saveCurrentChat(
                    inputTokens,
                    outputTokens,
                    currentWindowSize
                )

                // 显示成功提示
                uiStateDelegate.showToast("消息已更新")
            } catch (e: Exception) {
                AppLogger.e(TAG, "更新消息失败", e)
                uiStateDelegate.showErrorMessage("更新消息失败: ${e.message}")
            }
        }
    }

    /**
     * 回档到指定消息并重新发送
     * @param index 要回档到的消息索引
     * @param editedContent 编辑后的消息内容（如果有）
     */
    fun rewindAndResendMessage(index: Int, editedContent: String) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage("无效的消息索引")
                    return@launch
                }

                // 获取目标消息
                val targetMessage = currentHistory[index]

                // 检查目标消息是否是用户消息
                if (targetMessage.sender != "user") {
                    uiStateDelegate.showErrorMessage("只能对用户消息执行此操作")
                        return@launch
                    }

                // **核心修复**: 确定回滚的时间戳。
                // 我们需要恢复到目标消息 *之前* 的状态,
                // 所以我们使用前一条消息的时间戳。
                // 如果目标是第一条消息，则回滚到初始状态 (时间戳 0)。
                val rewindTimestamp = if (index > 0) {
                    currentHistory[index - 1].timestamp
                } else {
                    0L
                }

                // 获取当前工作区路径
                val chatId = currentChatId.value
                val currentChat = chatHistories.value.find { it.id == chatId }
                val workspacePath = currentChat?.workspace

                AppLogger.d(TAG, "[Rewind] Target message timestamp: ${targetMessage.timestamp}")
                if (index > 0) {
                    AppLogger.d(TAG, "[Rewind] Previous message timestamp: ${currentHistory[index - 1].timestamp}")
                } else {
                    AppLogger.d(TAG, "[Rewind] No previous message, target is the first message.")
                }
                AppLogger.d(TAG, "[Rewind] Timestamp passed to syncState: $rewindTimestamp")

                // 如果绑定了工作区，则执行回滚
                if (!workspacePath.isNullOrBlank()) {
                    AppLogger.d(TAG, "Rewinding workspace to timestamp: $rewindTimestamp")
                    withContext(Dispatchers.IO) {
                        WorkspaceBackupManager.getInstance(context)
                            .syncState(workspacePath, rewindTimestamp)
                    }
                    AppLogger.d(TAG, "Workspace rewind complete.")
                }

                // 截取到指定消息的历史记录（不包含该消息本身）
                val rewindHistory = currentHistory.subList(0, index)
                
                // 获取要删除的第一条消息的时间戳
                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp

                // **核心修复**：调用新的委托方法，原子性地更新数据库和内存
                chatHistoryDelegate.truncateChatHistory(
                        rewindHistory,
                        timestampOfFirstDeletedMessage
                )

                // 显示重新发送的消息准备状态
                uiStateDelegate.showToast("正在准备重新发送消息")

                // 使用修改后的消息内容来发送
                messageProcessingDelegate.updateUserMessage(editedContent)
                sendUserMessage()
            } catch (e: Exception) {
                AppLogger.e(TAG, "回档并重新发送消息失败", e)
                uiStateDelegate.showErrorMessage("回档失败: ${e.message}")
            }
        }
    }

    suspend fun previewWorkspaceChangesForMessage(index: Int): List<WorkspaceBackupManager.WorkspaceFileChange> {
        return withContext(Dispatchers.IO) {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                if (index < 0 || index >= currentHistory.size) {
                    emptyList()
                } else {
                    val rewindTimestamp = if (index > 0) {
                        currentHistory[index - 1].timestamp
                    } else {
                        0L
                    }

                    val chatId = currentChatId.value
                    val currentChat = chatHistories.value.find { it.id == chatId }
                    val workspacePath = currentChat?.workspace

                    if (workspacePath.isNullOrBlank()) {
                        emptyList()
                    } else {
                        val workspaceDir = File(workspacePath)
                        val backupDir = File(workspaceDir, ".backup")
                        val existingBackups = backupDir.listFiles { file ->
                            file.isFile && file.name.endsWith(".json")
                        }?.mapNotNull {
                            it.nameWithoutExtension.toLongOrNull()
                        }?.sorted() ?: emptyList()

                        val newerBackups = existingBackups.filter { it > rewindTimestamp }
                        if (newerBackups.isEmpty()) {
                            emptyList()
                        } else {
                            val restoreTimestamp = newerBackups.first()
                            WorkspaceBackupManager.getInstance(context)
                                .previewChanges(workspacePath, restoreTimestamp)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "预览工作区变更失败", e)
                emptyList()
            }
        }
    }

    fun rollbackToMessage(index: Int) {
        viewModelScope.launch {
            try {
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage("无效的消息索引")
                    return@launch
                }

                val targetMessage = currentHistory[index]

                // 目前UI只允许对用户消息执行回滚，这里再做一次保护
                if (targetMessage.sender != "user") {
                    uiStateDelegate.showErrorMessage("只能对用户消息执行此操作")
                    return@launch
                }

                val rewindTimestamp = if (index > 0) {
                    currentHistory[index - 1].timestamp
                } else {
                    0L
                }

                val chatId = currentChatId.value
                val currentChat = chatHistories.value.find { it.id == chatId }
                val workspacePath = currentChat?.workspace

                if (!workspacePath.isNullOrBlank()) {
                    AppLogger.d(TAG, "[Rollback] Rewinding workspace to timestamp: $rewindTimestamp")
                    withContext(Dispatchers.IO) {
                        WorkspaceBackupManager.getInstance(context)
                            .syncState(workspacePath, rewindTimestamp)
                    }
                    AppLogger.d(TAG, "[Rollback] Workspace rewind complete.")
                }

                // 删除目标消息及其之后的所有消息
                val newHistory = currentHistory.subList(0, index)

                val timestampOfFirstDeletedMessage = currentHistory[index].timestamp
                chatHistoryDelegate.truncateChatHistory(
                    newHistory,
                    timestampOfFirstDeletedMessage
                )

                val plainText = AvatarEmotionManager.stripXmlLikeTags(targetMessage.content)
                updateUserMessage(TextFieldValue(plainText))

                uiStateDelegate.showToast("已回滚，消息内容已放入输入框")
            } catch (e: Exception) {
                AppLogger.e(TAG, "回滚到指定消息失败", e)
                uiStateDelegate.showErrorMessage("回滚失败: ${e.message}")
            }
        }
    }

    // 消息处理相关方法
    fun updateUserMessage(value: TextFieldValue) {
        messageProcessingDelegate.updateUserMessage(value)
        val message = value.text
        // 当用户输入@且工作-区打开时，显示文件选择器
        // 新逻辑：提取@后的内容作为搜索词，并根据条件决定是否显示选择器
        val lastAt = message.lastIndexOf('@')
        if (isWorkspaceOpen.value && lastAt != -1) {
            val substringAfterAt = message.substring(lastAt + 1)
            if (substringAfterAt.contains(' ')) {
                // 如果@后面有空格，则认为提及结束，隐藏选择器并清空搜索词
                _showWorkspaceFileSelector.value = false
                _workspaceFileSearchQuery.value = ""
            } else {
                // 如果@后面没有空格，则显示选择器，并更新搜索词
                _showWorkspaceFileSelector.value = true
                _workspaceFileSearchQuery.value = substringAfterAt
            }
        } else {
            // 如果没有@或者工作区未打开，则隐藏并清空
            _showWorkspaceFileSelector.value = false
            _workspaceFileSearchQuery.value = ""
        }
    }

    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        messageCoordinationDelegate.sendUserMessage(promptFunctionType)
    }

    fun cancelCurrentMessage() {
        // 先取消总结（如果正在进行）
        if (::messageCoordinationDelegate.isInitialized) {
            messageCoordinationDelegate.cancelSummary()
        }
        // 然后取消消息处理
        messageProcessingDelegate.cancelCurrentMessage()
        uiStateDelegate.showToast("已取消当前对话")
    }

    // UI状态相关方法
    fun showErrorMessage(message: String) = uiStateDelegate.showErrorMessage(message)
    fun clearError() = uiStateDelegate.clearError()
    fun popupMessage(message: String) = uiStateDelegate.showPopupMessage(message)
    fun clearPopupMessage() = uiStateDelegate.clearPopupMessage()
    fun showToast(message: String) = uiStateDelegate.showToast(message)
    fun clearToastEvent() = uiStateDelegate.clearToastEvent()

    // 悬浮窗相关方法
    fun onFloatingButtonClick(mode: FloatingMode, permissionLauncher: ActivityResultLauncher<String>, colorScheme: ColorScheme, typography: Typography) {
        viewModelScope.launch {
            // 如果悬浮窗已经开启，则关闭它
            if (isFloatingMode.value) {
                toggleFloatingMode()
                return@launch
            }

            when(mode) {
                FloatingMode.WINDOW -> launchFloatingWindowWithPermissionCheck(permissionLauncher) {
                    launchFloatingModeIn(FloatingMode.WINDOW, colorScheme, typography)
                }
                FloatingMode.FULLSCREEN -> launchFullscreenVoiceModeWithPermissionCheck(permissionLauncher, colorScheme, typography)
                FloatingMode.SCREEN_OCR -> launchFloatingWindowWithPermissionCheck(permissionLauncher) {
                    launchFloatingModeIn(FloatingMode.WINDOW, colorScheme, typography)
                }
                FloatingMode.BALL,
                FloatingMode.VOICE_BALL,
                FloatingMode.RESULT_DISPLAY -> {
                    // 这些模式暂时不处理，或者可以添加默认行为
                    AppLogger.d(TAG, "未实现的悬浮窗模式: $mode")
                }
            }
        }
    }


    fun toggleFloatingMode(colorScheme: ColorScheme? = null, typography: Typography? = null) {
        floatingWindowDelegate.toggleFloatingMode(colorScheme, typography)
    }

    // 权限相关方法
    fun toggleMasterPermission() {
        viewModelScope.launch {
            val newLevel =
                    if (masterPermissionLevel.value == PermissionLevel.ASK) {
                        PermissionLevel.ALLOW
                    } else {
                        PermissionLevel.ASK
                    }
            toolPermissionSystem.saveMasterSwitch(newLevel)

            // 移除Toast提示
        }
    }

    // 附件相关方法
    /** Handles a file or image attachment selected by the user */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setActiveStreamingChatId(currentChatId)
                }
                
                // 显示附件处理进度
                messageProcessingDelegate.setInputProcessingState(true, "正在处理附件...")

                attachmentDelegate.handleAttachment(filePath)

                // 清除附件处理进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理附件失败", e)
                // 修改: 使用错误弹窗而不是 Toast 显示附件处理错误
                uiStateDelegate.showErrorMessage("处理附件失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            }
        }
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        attachmentDelegate.removeAttachment(filePath)
    }

    /** Inserts a reference to an attachment at the current cursor position in the user's message */
    fun insertAttachmentReference(attachment: AttachmentInfo) {
        val currentMessage = userMessage.value
        val attachmentRef = attachmentDelegate.createAttachmentReference(attachment)

        // Insert at the end of the current message
        val currentText = currentMessage.text
        val newText = "$currentText $attachmentRef "
        updateUserMessage(TextFieldValue(newText, selection = TextRange(newText.length)))

        // Show a toast to confirm insertion
        uiStateDelegate.showToast("已插入附件引用: ${attachment.fileName}")
    }

    /** 隐藏工作区文件选择器 */
    fun hideWorkspaceFileSelector() {
        _showWorkspaceFileSelector.value = false
    }

    /** Captures the current screen content and attaches it to the message */
    fun captureScreenContent() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage(TextFieldValue(""))
                
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setActiveStreamingChatId(currentChatId)
                }
                
                // 显示屏幕内容获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取屏幕内容...")
                uiStateDelegate.showToast("正在获取屏幕内容...")

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureScreenContent()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "截取屏幕内容失败", e)
                uiStateDelegate.showErrorMessage("截取屏幕内容失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            }
        }
    }

    /** 获取设备当前通知数据并添加为附件 */
    fun captureNotifications() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage(TextFieldValue(""))
                
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setActiveStreamingChatId(currentChatId)
                }
                
                // 显示通知获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取当前通知...")
                uiStateDelegate.showToast("正在获取当前通知...")

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureNotifications()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取通知数据失败", e)
                uiStateDelegate.showErrorMessage("获取通知数据失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            }
        }
    }

    /** 获取设备当前位置数据并添加为附件 */
    fun captureLocation() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage(TextFieldValue(""))
                
                // 获取当前会话ID并绑定
                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != null) {
                    messageProcessingDelegate.setActiveStreamingChatId(currentChatId)
                }
                
                // 显示位置获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取位置信息...")
                uiStateDelegate.showToast("正在获取位置信息...")

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureLocation()
                
                // 隐藏进度状态
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error capturing location", e)
                uiStateDelegate.showToast("获取位置失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
                messageProcessingDelegate.setActiveStreamingChatId(null)
            }
        }
    }

    /**
     * 捕获记忆文件夹作为附件
     */
    fun captureMemoryFolders(folderPaths: List<String>) {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage(TextFieldValue(""))
                // 显示记忆文件夹附着进度
                messageProcessingDelegate.setInputProcessingState(true, "正在附着记忆文件夹...")
                uiStateDelegate.showToast("正在附着记忆文件夹...")

                // 直接委托给attachmentDelegate执行
                attachmentDelegate.captureMemoryFolders(folderPaths)

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                AppLogger.e(TAG, "附着记忆文件夹失败", e)
                uiStateDelegate.showErrorMessage("附着记忆文件夹失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** Handles a photo taken by the camera */
    fun handleTakenPhoto(uri: Uri) {
        viewModelScope.launch {
            attachmentDelegate.handleTakenPhoto(uri)
        }
    }

    /**
     * Handles shared files from external apps
     * Creates a new chat, attaches files, and pre-fills message
     */
    fun handleSharedFiles(uris: List<Uri>) {
        AppLogger.d(TAG, "handleSharedFiles called with ${uris.size} file(s)")
        uris.forEachIndexed { index, uri ->
            AppLogger.d(TAG, "  [$index] URI: $uri")
        }
        
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Creating new chat for shared files...")
                // Create a new chat for the shared file(s)
                createNewChat()
                
                // Wait for chat to be created
                var waitCount = 0
                while (currentChatId.value == null && waitCount < 20) {
                    delay(50)
                    waitCount++
                }
                
                if (currentChatId.value == null) {
                    AppLogger.e(TAG, "Failed to create chat after waiting")
                    uiStateDelegate.showErrorMessage("创建新对话失败")
                    return@launch
                }
                
                AppLogger.d(TAG, "Chat created successfully: ${currentChatId.value}")
                
                // Show processing state
                messageProcessingDelegate.setInputProcessingState(true, "正在处理分享的文件...")
                
                // Attach each file
                AppLogger.d(TAG, "Starting to attach ${uris.size} file(s)...")
                uris.forEachIndexed { index, uri ->
                    val filePath = uri.toString()
                    AppLogger.d(TAG, "Attaching file [$index]: $filePath")
                    attachmentDelegate.handleAttachment(filePath)
                    delay(100) // Small delay between files
                }
                AppLogger.d(TAG, "All files attached successfully")
                
                // Set the pre-filled message
                AppLogger.d(TAG, "Setting pre-filled message")
                messageProcessingDelegate.updateUserMessage(TextFieldValue("帮我看看这个文件"))

                // Clear processing state
                messageProcessingDelegate.setInputProcessingState(false, "")
                
                AppLogger.d(TAG, "Successfully processed shared files")
                uiStateDelegate.showToast("已添加 ${uris.size} 个文件")
            } catch (e: Exception) {
                AppLogger.e(TAG, "处理分享文件失败", e)
                uiStateDelegate.showErrorMessage("处理分享文件失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 确保AI服务可用，如果当前实例为空则创建一个默认实例 */
    fun ensureAiServiceAvailable() {
        if (enhancedAiService == null) {
            viewModelScope.launch {
                try {
                    // 使用默认配置或保存的配置创建一个新实例
                    AppLogger.d(TAG, "创建默认EnhancedAIService实例")
                    apiConfigDelegate.useDefaultConfig()

                    // 等待服务实例创建完成
                    var retryCount = 0
                    while (enhancedAiService == null && retryCount < 3) {
                        kotlinx.coroutines.delay(500)
                        retryCount++
                    }

                    if (enhancedAiService == null) {
                        AppLogger.e(TAG, "无法创建EnhancedAIService实例")
                        // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                        uiStateDelegate.showErrorMessage("无法初始化AI服务，请检查网络和API设置")
                    } else {
                        AppLogger.d(TAG, "成功创建EnhancedAIService实例")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "创建EnhancedAIService实例时出错", e)
                    // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                    uiStateDelegate.showErrorMessage("初始化AI服务失败: ${e.message}")
                }
            }
        }
    }

    /** 重置附件面板状态 - 在发送消息后关闭附件面板 */
    fun resetAttachmentPanelState() {
        _attachmentPanelState.value = false
    }

    /** 更新附件面板状态 */
    fun updateAttachmentPanelState(isExpanded: Boolean) {
        _attachmentPanelState.value = isExpanded
    }

    // WebView控制方法
    fun toggleWebView() {
        // 如果要显示WebView，先关闭AI电脑
        if (!_showWebView.value && _showAiComputer.value) {
            _showAiComputer.value = false
            AppLogger.d(TAG, "AI电脑已关闭（由于打开工作区）")
        }
        
        // 如果要显示WebView，确保本地Web服务器已启动
        if (!_showWebView.value) {
            // Get the WORKSPACE server instance and ensure it's running
            val workspaceServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            if (!workspaceServer.isRunning()) {
                try {
                    workspaceServer.start()
                } catch (e: IOException) {
                    AppLogger.e(TAG, "Failed to start workspace web server", e)
                    showErrorMessage("Failed to start workspace server.")
                    return
                }
            }

            // 获取当前聊天ID
            val chatId = currentChatId.value
            if (chatId != null) {
                // 更新Web服务器工作区
                updateWebServerForCurrentChat(chatId)
            } else {
                // 如果没有聊天ID，先创建一个新对话
                viewModelScope.launch {
                    createNewChat()

                    // 等待聊天ID创建完成
                    var waitCount = 0
                    while (currentChatId.value == null && waitCount < 10) {
                        delay(100)
                        waitCount++
                    }

                    // 使用新创建的聊天ID更新Web服务器
                    currentChatId.value?.let { newChatId ->
                        updateWebServerForCurrentChat(newChatId)
                    }
                }
            }
        }

        // 切换WebView显示状态
        val newShowState = !_showWebView.value
        _showWebView.value = newShowState

        // 每次切换时，增加刷新计数器
        if (_showWebView.value) {
            _webViewRefreshCounter.value += 1
        }
    }


    // 更新当前聊天ID的Web服务器工作空间
    fun updateWebServerForCurrentChat(chatId: String) {
        try {
            // Find the chat and its workspace
            val chat = chatHistories.value.find { it.id == chatId }
            val workspacePath = chat?.workspace

            if (workspacePath == null) {
                AppLogger.w(TAG, "Chat $chatId has no workspace bound. Web server not updated.")
                return
            }

            // 使用单例模式获取LocalWebServer实例
            val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            // 确保服务器已启动
            if (!webServer.isRunning()) {
                webServer.start()
            }
            webServer.updateChatWorkspace(workspacePath)
            AppLogger.d(TAG, "Web服务器工作空间已更新为: $workspacePath for chat $chatId")
        } catch (e: Exception) {
            AppLogger.e(TAG, "更新Web服务器工作空间失败", e)
            uiStateDelegate.showErrorMessage("更新Web工作空间失败: ${e.message}")
        }
    }

    // 强制WebView刷新
    fun refreshWebView() {
        _webViewRefreshCounter.value += 1
    }

    // 判断是否正在使用默认API配置
    private suspend fun checkConfigAndShowDialog() {
        // 初始化ModelConfigManager以检查所有配置
        val modelConfigManager = ModelConfigManager(context)
        var hasDefaultKey = false

        // 异步检查所有配置
        withContext(Dispatchers.IO) {
            // 获取所有配置ID
            val configIds = modelConfigManager.configListFlow.first()

            // 检查每个配置是否使用默认API key
            for (id in configIds) {
                val config = modelConfigManager.getModelConfigFlow(id).first()
                if (config.apiKey == ApiPreferences.DEFAULT_API_KEY) {
                    hasDefaultKey = true
                    break
                }
            }
        }

        _shouldShowConfigDialog.value = hasDefaultKey
    }
    
    // 用于启动文件选择器并处理结果
    fun startFileChooserForResult(intent: Intent, callback: (Int, Intent?) -> Unit) {
        fileChooserCallback = callback
        // 通过UIStateDelegate广播一个请求，让Activity处理文件选择
        uiStateDelegate.requestFileChooser(intent)
    }

    // 供Activity调用，处理文件选择结果
    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        fileChooserCallback?.invoke(resultCode, data)
        fileChooserCallback = null
    }

    /** 设置权限系统的颜色方案 */
    fun setPermissionSystemColorScheme(colorScheme: ColorScheme?) {
        toolPermissionSystem.setColorScheme(colorScheme)
    }

    fun launchFloatingModeIn(
            mode: FloatingMode,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        floatingWindowDelegate.launchInMode(mode, colorScheme, typography)
    }
    
    /**
     * 从Widget启动悬浮窗到指定模式（使用默认主题）
     */
    fun launchFloatingWindowInMode(mode: FloatingMode) {
        launchFloatingModeIn(mode, null, null)
    }

    fun launchFloatingWindowWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            onPermissionGranted: () -> Unit
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                    )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            showToast("需要悬浮窗权限才能启动语音助手")
        } else {
            onPermissionGranted()
        }
    }

    fun launchFullscreenVoiceModeWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                    )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            showToast("需要悬浮窗权限才能启动语音助手")
        } else {
            // Directly launch fullscreen voice mode
            launchFloatingModeIn(FloatingMode.FULLSCREEN, colorScheme, typography)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理悬浮窗资源
        floatingWindowDelegate.cleanup()
        
        // 清理语音服务资源
        voiceService?.shutdown()

        // 不再在这里停止Web服务器，因为使用的是单例模式
        // 服务器应在应用退出时由Application类或专门的服务管理类关闭
        // 这样可以在界面切换时保持服务器的连续运行
    }

    /** 更新指定聊天的标题 */
    fun updateChatTitle(chatId: String, newTitle: String) {
        chatHistoryDelegate.updateChatTitle(chatId, newTitle)
    }

    /** 更新指定聊天绑定的角色卡 */
    fun updateChatCharacterCardBinding(chatId: String, characterCardName: String?) {
        chatHistoryDelegate.updateChatCharacterCard(chatId, characterCardName)
    }

    /** 更新指定聊天的标题 */
    fun bindChatToWorkspace(chatId: String, workspace: String) {
        // 1. Persist the change
        chatHistoryDelegate.bindChatToWorkspace(chatId, workspace)

        // 2. Update the web server with the new path and refresh
        viewModelScope.launch {
            try {
                val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
                if (!webServer.isRunning()) {
                    webServer.start()
                }
                webServer.updateChatWorkspace(workspace)
                AppLogger.d(TAG, "Web server workspace updated to: $workspace for chat $chatId")

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update web server workspace after binding", e)
                uiStateDelegate.showErrorMessage("更新Web工作空间失败: ${e.message}")
            }
        }
    }

    /** 解绑聊天的工作区 */
    fun unbindChatFromWorkspace(chatId: String) {
        // 1. Persist the change
        chatHistoryDelegate.unbindChatFromWorkspace(chatId)

        // 2. Stop the web server or clear workspace
        viewModelScope.launch {
            try {
                val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
                if (webServer.isRunning()) {
                    webServer.stop()
                }
                AppLogger.d(TAG, "Web server stopped after unbinding workspace for chat $chatId")

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to stop web server after unbinding", e)
                uiStateDelegate.showErrorMessage("停止Web工作空间失败: ${e.message}")
            }
        }
    }

    /** 在工作区中执行命令（来自 config.json 按钮） */
    @RequiresApi(Build.VERSION_CODES.O)
    fun executeCommandInWorkspace(command: CommandConfig, workspacePath: String) {
        if (terminal == null) {
            uiStateDelegate.showErrorMessage("终端功能需要 Android 8.0 或更高版本")
            return
        }

        // 立即切换 UI，给用户即时反馈
        if (_showWebView.value) {
            _showWebView.value = false
        }
        _showAiComputer.value = true

        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Executing workspace command: ${command.command} in $workspacePath")
                
                val sessionId: String
                val workspaceDir = File(workspacePath)
                
                if (command.usesDedicatedSession) {
                    // 为长时间运行的命令创建独立会话
                    val sessionTitle = command.sessionTitle ?: command.label
                    val dedicatedSessionId = terminal.createSessionAndWait(sessionTitle)
                    if (dedicatedSessionId == null) {
                        AppLogger.e(TAG, "Failed to create dedicated terminal session")
                        uiStateDelegate.showErrorMessage("无法创建独立终端会话")
                        return@launch
                    }
                    
                    // 切换到工作区目录
                    terminal.executeCommand(dedicatedSessionId, "cd \"${workspaceDir.absolutePath}\"")
                    sessionId = dedicatedSessionId
                    
                    AppLogger.d(TAG, "Created dedicated terminal session $sessionId for command: ${command.label}")
                } else {
                    // 使用工作区的共享会话
                    var sharedSessionId = workspaceTerminalSessions[workspacePath]
                    
                    // 如果会话不存在或已关闭，创建新会话
                    if (sharedSessionId == null || terminal.terminalState.value.sessions.none { it.id == sharedSessionId }) {
                        val workspaceName = workspaceDir.name.take(4) // 只取前4位
                        
                        sharedSessionId = terminal.createSessionAndWait("Workspace: $workspaceName")
                        if (sharedSessionId == null) {
                            AppLogger.e(TAG, "Failed to create workspace terminal session")
                            uiStateDelegate.showErrorMessage("无法创建工作区终端会话")
                            return@launch
                        }
                        
                        // 保存会话 ID
                        workspaceTerminalSessions[workspacePath] = sharedSessionId
                        
                        // 切换到工作区目录
                        terminal.executeCommand(sharedSessionId, "cd \"${workspaceDir.absolutePath}\"")
                        AppLogger.d(TAG, "Created new workspace terminal session $sharedSessionId for $workspacePath")
                    }
                    
                    sessionId = sharedSessionId
                }
                
                // 切换到该会话
                terminal.switchToSession(sessionId)
                
                // 执行命令（用户可以立即看到输出）
                terminal.executeCommand(sessionId, command.command)
                
                AppLogger.d(TAG, "Switched to computer view and executing command in session $sessionId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to execute workspace command", e)
                uiStateDelegate.showErrorMessage("命令执行失败: ${e.message}")
            }
        }
    }

    /** 更新聊天顺序和分组 */
    fun updateChatOrderAndGroup(
        reorderedHistories: List<ChatHistory>,
        movedItem: ChatHistory,
        targetGroup: String?
    ) {
        chatHistoryDelegate.updateChatOrderAndGroup(reorderedHistories, movedItem, targetGroup)
    }

    /** 创建新分组（通过创建新聊天实现） */
    fun createGroup(groupName: String, characterCardName: String?) {
        chatHistoryDelegate.createGroup(groupName, characterCardName)
    }

    /** 重命名分组 */
    fun updateGroupName(oldName: String, newName: String, characterCardName: String?) {
        chatHistoryDelegate.updateGroupName(oldName, newName, characterCardName)
    }

    /** 删除分组 */
    fun deleteGroup(groupName: String, deleteChats: Boolean, characterCardName: String?) {
        chatHistoryDelegate.deleteGroup(groupName, deleteChats, characterCardName)
    }

    fun onWorkspaceButtonClick() {
        toggleWebView()
        refreshWebView()
    }

    fun onAiComputerButtonClick() {
        toggleAiComputer()
    }

    // AI电脑控制方法
    fun toggleAiComputer() {
        viewModelScope.launch {
            // 如果要显示AI电脑，先关闭工作区
            if (!_showAiComputer.value && _showWebView.value) {
                _showWebView.value = false
                AppLogger.d(TAG, "工作区已关闭（由于打开AI电脑）")
            }
            
            val newShowState = !_showAiComputer.value
            _showAiComputer.value = newShowState
            
            if (newShowState) {
                // 初始化AI电脑管理器
                try {
                    AppLogger.d(TAG, "AI电脑已启动")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "启动AI电脑失败", e)
                    _showAiComputer.value = false
                    uiStateDelegate.showErrorMessage("启动AI电脑失败: ${e.message}")
                }
            } else {
                AppLogger.d(TAG, "AI电脑已关闭")
            }
        }
    }



    /** 初始化语音服务 */
    private fun initializeVoiceService() {
        // 监听TTS服务类型和配置的变化
        viewModelScope.launch {
            combine(
                speechServicesPreferences.ttsServiceTypeFlow,
                speechServicesPreferences.ttsHttpConfigFlow
            ) { type, config ->
                type to config
            }.collect { (type, config) ->
                try {
                    AppLogger.d(TAG, "TTS配置变化，重新初始化语音服务: type=$type")
                    
                    // 停止当前播放
                    voiceService?.stop()
                    
                    // 重置工厂单例，强制重新创建
                    VoiceServiceFactory.resetInstance()
                    
                    // 重新获取服务实例
                    voiceService = VoiceServiceFactory.getInstance(context)
                    val initialized = voiceService?.initialize() ?: false
                    if (!initialized) {
                        AppLogger.w(TAG, "语音服务初始化失败")
                    } else {
                        AppLogger.i(TAG, "语音服务初始化成功")
                        
                        // 初始化成功后，重新监听播放状态
                        viewModelScope.launch {
                            voiceService?.speakingStateFlow?.collect { isSpeaking ->
                                _isPlaying.value = isSpeaking
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "初始化语音服务时出错", e)
                }
            }
        }
    }

    /** 朗读消息内容 */
    fun speakMessage(message: String) {
        viewModelScope.launch {
            try {
                // 如果服务未初始化，等待一段时间让监听协程完成初始化
                if (voiceService == null) {
                    AppLogger.d(TAG, "语音服务尚未初始化，等待初始化完成...")
                    delay(1000)
                }

                if (voiceService == null) {
                    uiStateDelegate.showToast("语音服务初始化失败，请检查设置")
                    AppLogger.e(TAG, "语音服务初始化超时")
                    return@launch
                }

                val cleanerRegexs = speechServicesPreferences.ttsCleanerRegexsFlow.first()
                val cleanedText = TtsCleaner.clean(message, cleanerRegexs)
                val cleanMessage = WaifuMessageProcessor.cleanContentForWaifu(cleanedText)

                val success = voiceService?.speak(
                    text = cleanMessage,
                    interrupt = true, // 中断当前播放
                    rate = 1.0f,
                    pitch = 1.0f
                ) ?: false

                if (!success) {
                    uiStateDelegate.showToast("朗读失败")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "朗读消息失败", e)
                uiStateDelegate.showToast("朗读消息失败: ${e.message}")
            }
        }
    }

    /** 停止朗读 */
    fun stopSpeaking() {
        viewModelScope.launch {
            try {
                voiceService?.stop()
            } catch (e: Exception) {
                AppLogger.e(TAG, "停止朗读失败", e)
            }
        }
    }

    fun toggleAutoRead() {
        apiConfigDelegate.toggleAutoRead()
        // Stop speaking if auto-read is being turned off.
        // We check the new value directly from the delegate's state flow.
        viewModelScope.launch {
            // A small delay to allow the state flow to update, although it's often fast.
            delay(50)
            if (!isAutoReadEnabled.value) {
                stopSpeaking()
            }
        }
    }

    fun disableAutoRead() {
        if (isAutoReadEnabled.value) {
            apiConfigDelegate.toggleAutoRead() // This will set it to false
            stopSpeaking()
        }
    }

    fun enableAutoReadAndSpeak(content: String) {
        if (!isAutoReadEnabled.value) {
            apiConfigDelegate.toggleAutoRead() // This will set it to true
        }
        speakMessage(content)
    }

    /** 设置回复目标消息 */
    fun setReplyToMessage(message: ChatMessage) {
        _replyToMessage.value = message
    }

    /** 清除回复状态 */
    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    fun manuallyUpdateMemory() {
        messageCoordinationDelegate.manuallyUpdateMemory()
    }

    fun manuallySummarizeConversation() {
        messageCoordinationDelegate.manuallySummarizeConversation()
    }

}
