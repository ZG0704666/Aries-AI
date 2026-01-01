package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.core.ApiConfigDelegate
import com.ai.assistance.operit.services.core.AttachmentDelegate
import com.ai.assistance.operit.services.core.ChatHistoryDelegate
import com.ai.assistance.operit.services.core.MessageCoordinationDelegate
import com.ai.assistance.operit.services.core.MessageProcessingDelegate
import com.ai.assistance.operit.services.core.TokenStatisticsDelegate
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import com.ai.assistance.operit.util.stream.SharedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 聊天服务核心类
 * 
 * 整合所有聊天业务逻辑，可被 FloatingChatService 或 ChatViewModel 使用
 * 生命周期独立于 ViewModel，绑定到传入的 CoroutineScope
 */
class ChatServiceCore(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ChatServiceCore"
    }

    // EnhancedAIService 实例（全局单例）
    private var enhancedAiService: EnhancedAIService? = null

    // 委托实例
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var apiConfigDelegate: ApiConfigDelegate
    private lateinit var tokenStatisticsDelegate: TokenStatisticsDelegate
    private lateinit var attachmentDelegate: AttachmentDelegate
    private lateinit var uiStateDelegate: UiStateDelegate
    private lateinit var messageCoordinationDelegate: MessageCoordinationDelegate

    // 初始化状态
    private var initialized = false

    init {
        AppLogger.d(TAG, "ChatServiceCore 初始化")
        initializeDelegates()
    }

    // 回调：当 EnhancedAIService 初始化或更新时
    private var onEnhancedAiServiceReady: ((EnhancedAIService) -> Unit)? = null
    
    // 额外的 onTurnComplete 回调（用于悬浮窗通知应用等场景）
    private var additionalOnTurnComplete: (() -> Unit)? = null
    
    private fun initializeDelegates() {
        // 初始化 UI 状态委托
        uiStateDelegate = UiStateDelegate()
        
        // 初始化 API 配置委托
        apiConfigDelegate = ApiConfigDelegate(
            context = context,
            coroutineScope = coroutineScope,
            onConfigChanged = { service ->
                enhancedAiService = service
                // 当服务初始化后，设置 token 统计收集器
                tokenStatisticsDelegate.setupCollectors()
                // 通知外部监听者
                onEnhancedAiServiceReady?.invoke(service)
                AppLogger.d(TAG, "EnhancedAIService 已更新")
            }
        )

        // 初始化 Token 统计委托
        tokenStatisticsDelegate = TokenStatisticsDelegate(
            coroutineScope = coroutineScope,
            getEnhancedAiService = { enhancedAiService }
        )

        // 初始化附件委托
        attachmentDelegate = AttachmentDelegate(
            context = context,
            toolHandler = AIToolHandler.getInstance(context)
        )

        // 初始化聊天历史委托
        chatHistoryDelegate = ChatHistoryDelegate(
            context = context,
            coroutineScope = coroutineScope,
            onTokenStatisticsLoaded = { inputTokens, outputTokens, windowSize ->
                tokenStatisticsDelegate.setTokenCounts(inputTokens, outputTokens, windowSize)
            },
            getEnhancedAiService = { enhancedAiService },
            ensureAiServiceAvailable = {
                // 确保 AI 服务可用
                if (enhancedAiService == null) {
                    enhancedAiService = EnhancedAIService.getInstance(context)
                }
            },
            getChatStatistics = {
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts()
                val windowSize = tokenStatisticsDelegate.getLastCurrentWindowSize()
                Triple(inputTokens, outputTokens, windowSize)
            },
            onScrollToBottom = {
                messageProcessingDelegate.scrollToBottom()
            }
        )

        // 初始化消息处理委托
        messageProcessingDelegate = MessageProcessingDelegate(
            context = context,
            coroutineScope = coroutineScope,
            getEnhancedAiService = { enhancedAiService },
            getChatHistory = { chatHistoryDelegate.chatHistory.value },
            addMessageToChat = { chatId, message ->
                chatHistoryDelegate.addMessageToChat(message, chatId)
            },
            saveCurrentChat = {
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts()
                val windowSize = tokenStatisticsDelegate.getLastCurrentWindowSize()
                chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, windowSize)
            },
            showErrorMessage = { error ->
                AppLogger.e(TAG, "错误: $error")
                // 错误消息可以通过回调传递给 UI
            },
            updateChatTitle = { chatId, title ->
                chatHistoryDelegate.updateChatTitle(chatId, title)
            },
            onTurnComplete = {
                // 对话轮次完成后的处理
                tokenStatisticsDelegate.updateCumulativeStatistics()
                val (inputTokens, outputTokens) = tokenStatisticsDelegate.getCumulativeTokenCounts()
                val windowSize = tokenStatisticsDelegate.getLastCurrentWindowSize()
                chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, windowSize)
                // 调用额外的回调（用于悬浮窗通知应用等场景）
                additionalOnTurnComplete?.invoke()
            },
            getIsAutoReadEnabled = {
                apiConfigDelegate.enableAutoRead.value
            },
            speakMessage = { text ->
                // TTS 功能需要在外部实现
                AppLogger.d(TAG, "朗读消息: $text")
            },
            onTokenLimitExceeded = {
                messageCoordinationDelegate.handleTokenLimitExceeded()
            }
        )

        // 初始化消息协调委托
        messageCoordinationDelegate = MessageCoordinationDelegate(
            coroutineScope = coroutineScope,
            chatHistoryDelegate = chatHistoryDelegate,
            messageProcessingDelegate = messageProcessingDelegate,
            tokenStatsDelegate = tokenStatisticsDelegate,
            apiConfigDelegate = apiConfigDelegate,
            attachmentDelegate = attachmentDelegate,
            uiStateDelegate = uiStateDelegate,
            getEnhancedAiService = { enhancedAiService },
            updateWebServerForCurrentChat = { _ -> }, // 空实现
            resetAttachmentPanelState = { }, // 空实现
            clearReplyToMessage = { }, // 空实现
            getReplyToMessage = { null } // 总是返回 null
        )

        initialized = true
        AppLogger.d(TAG, "所有委托已初始化")
    }

    // ========== 消息处理相关 ==========

    /** 发送用户消息（使用 MessageCoordinationDelegate，包含总结逻辑） */
    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        messageCoordinationDelegate.sendUserMessage(promptFunctionType)
    }

    /** 取消当前消息 */
    fun cancelCurrentMessage() {
        // 先取消总结（如果正在进行）
        messageCoordinationDelegate.cancelSummary()
        // 然后取消消息处理
        messageProcessingDelegate.cancelCurrentMessage()
    }

    /** 更新用户消息 */
    fun updateUserMessage(message: String) {
        messageProcessingDelegate.updateUserMessage(message)
    }

    /** 获取当前响应流 */
    fun getCurrentResponseStream(): SharedStream<String>? {
        return messageProcessingDelegate.getCurrentResponseStream()
    }

    /** 处理输入处理状态 */
    fun handleInputProcessingState(state: InputProcessingState) {
        messageProcessingDelegate.handleInputProcessingState(state)
    }

    // ========== 聊天历史相关 ==========

    /** 创建新的聊天 */
    fun createNewChat() {
        chatHistoryDelegate.createNewChat()
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        chatHistoryDelegate.switchChat(chatId)
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        chatHistoryDelegate.deleteChatHistory(chatId)
    }

    /** 删除消息 */
    fun deleteMessage(index: Int) {
        chatHistoryDelegate.deleteMessage(index)
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat()
    }

    /** 更新聊天标题 */
    fun updateChatTitle(chatId: String, title: String) {
        chatHistoryDelegate.updateChatTitle(chatId, title)
    }

    // ========== Token 统计相关 ==========

    /** 重置 token 统计 */
    fun resetTokenStatistics() {
        tokenStatisticsDelegate.resetTokenStatistics()
    }

    /** 更新累计统计 */
    fun updateCumulativeStatistics() {
        tokenStatisticsDelegate.updateCumulativeStatistics()
    }

    // ========== 附件管理相关 ==========

    /** 获取 AttachmentDelegate 实例 */
    fun getAttachmentDelegate(): AttachmentDelegate = attachmentDelegate

    /** 添加附件 */
    suspend fun handleAttachment(filePath: String) {
        attachmentDelegate.handleAttachment(filePath)
    }

    /** 移除附件 */
    fun removeAttachment(filePath: String) {
        attachmentDelegate.removeAttachment(filePath)
    }

    /** 清空所有附件 */
    fun clearAttachments() {
        attachmentDelegate.clearAttachments()
    }

    // ========== StateFlow 暴露 ==========

    // 消息处理相关
    val userMessage: StateFlow<TextFieldValue>
        get() = messageProcessingDelegate.userMessage

    val isLoading: StateFlow<Boolean>
        get() = messageProcessingDelegate.isLoading

    val inputProcessingState: StateFlow<InputProcessingState>
        get() = messageProcessingDelegate.inputProcessingState

    val activeStreamingChatId: StateFlow<String?>
        get() = messageProcessingDelegate.activeStreamingChatId

    val scrollToBottomEvent: SharedFlow<Unit>
        get() = messageProcessingDelegate.scrollToBottomEvent

    val nonFatalErrorEvent: SharedFlow<String>
        get() = messageProcessingDelegate.nonFatalErrorEvent

    val isSummarizing: StateFlow<Boolean>
        get() = messageCoordinationDelegate.isSummarizing

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>>
        get() = chatHistoryDelegate.chatHistory

    val currentChatId: StateFlow<String?>
        get() = chatHistoryDelegate.currentChatId

    val chatHistories: StateFlow<List<com.ai.assistance.operit.data.model.ChatHistory>>
        get() = chatHistoryDelegate.chatHistories

    val showChatHistorySelector: StateFlow<Boolean>
        get() = chatHistoryDelegate.showChatHistorySelector

    // API 配置相关
    val enableThinkingMode: StateFlow<Boolean>
        get() = apiConfigDelegate.enableThinkingMode

    val enableThinkingGuidance: StateFlow<Boolean>
        get() = apiConfigDelegate.enableThinkingGuidance

    val enableMemoryQuery: StateFlow<Boolean>
        get() = apiConfigDelegate.enableMemoryQuery

    val enableAutoRead: StateFlow<Boolean>
        get() = apiConfigDelegate.enableAutoRead

    val contextLength: StateFlow<Float>
        get() = apiConfigDelegate.contextLength

    val summaryTokenThreshold: StateFlow<Float>
        get() = apiConfigDelegate.summaryTokenThreshold

    val enableSummary: StateFlow<Boolean>
        get() = apiConfigDelegate.enableSummary

    val enableTools: StateFlow<Boolean>
        get() = apiConfigDelegate.enableTools

    // Token 统计相关
    val cumulativeInputTokensFlow: StateFlow<Int>
        get() = tokenStatisticsDelegate.cumulativeInputTokensFlow

    val cumulativeOutputTokensFlow: StateFlow<Int>
        get() = tokenStatisticsDelegate.cumulativeOutputTokensFlow

    val currentWindowSizeFlow: StateFlow<Int>
        get() = tokenStatisticsDelegate.currentWindowSizeFlow

    val perRequestTokenCountFlow: StateFlow<Pair<Int, Int>?>
        get() = tokenStatisticsDelegate.perRequestTokenCountFlow

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>>
        get() = attachmentDelegate.attachments

    val attachmentToastEvent: SharedFlow<String>
        get() = attachmentDelegate.toastEvent

    // ========== 其他方法 ==========

    /** 获取 UiStateDelegate 实例 */
    fun getUiStateDelegate(): UiStateDelegate = uiStateDelegate

    /** 获取 EnhancedAIService 实例 */
    fun getEnhancedAiService(): EnhancedAIService? = enhancedAiService

    /** 检查是否已初始化 */
    fun isInitialized(): Boolean = initialized
    
    /** 设置 EnhancedAIService 就绪回调 */
    fun setOnEnhancedAiServiceReady(callback: (EnhancedAIService) -> Unit) {
        onEnhancedAiServiceReady = callback
        // 如果已经初始化，立即调用回调
        enhancedAiService?.let { callback(it) }
    }
    
    /** 设置额外的 onTurnComplete 回调（用于悬浮窗通知应用等场景） */
    fun setAdditionalOnTurnComplete(callback: (() -> Unit)?) {
        additionalOnTurnComplete = callback
    }
    
    /** 重新加载聊天消息（智能合并） */
    suspend fun reloadChatMessagesSmart(chatId: String) {
        chatHistoryDelegate.reloadChatMessagesSmart(chatId)
    }
}

