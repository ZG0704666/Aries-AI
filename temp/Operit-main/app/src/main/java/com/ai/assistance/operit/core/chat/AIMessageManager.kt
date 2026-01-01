package com.ai.assistance.operit.core.chat

import android.annotation.SuppressLint
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.plan.PlanModeManager
import com.ai.assistance.operit.api.chat.llmprovider.ImageLinkParser
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceAttachmentProcessor
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 单例对象，负责管理与 EnhancedAIService 的所有通信。
 *
 * 主要职责:
 * - 构建发送给AI的消息请求。
 * - 发送消息并处理流式响应。
 * - 请求生成对话总结。
 *
 * 设计原则:
 * - **无状态**: 本身不持有任何特定聊天的状态。所有需要的数据都通过方法参数传入。
 * - **职责明确**: 仅处理与AI服务的交互，UI更新和数据持久化由调用方负责。
 * - **封装逻辑**: 内部封装了与AI交互的策略，如是否需要总结、如何从历史中提取记忆等。
 */
@SuppressLint("StaticFieldLeak")
object AIMessageManager {
    private const val TAG = "AIMessageManager"
    // 聊天总结的消息数量阈值 - 移除硬编码，改用动态设置
    // private const val SUMMARY_CHUNK_SIZE = 4

    // 使用独立的协程作用域，确保AI操作的生命周期独立于任何特定的ViewModel
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeEnhancedAiService: EnhancedAIService? = null
    private var activePlanModeManager: PlanModeManager? = null

    private lateinit var toolHandler: AIToolHandler
    private lateinit var context: Context
    private lateinit var apiPreferences: ApiPreferences

    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        apiPreferences = ApiPreferences.getInstance(context)
    }

    /**
     * 构建用户消息的完整内容，包括附件和记忆标签。
     *
     * @param messageText 用户输入的原始文本。
     * @param attachments 附件列表。
     * @param enableMemoryQuery 是否允许AI查询记忆。
     * @param enableWorkspaceAttachment 是否启用工作区附着功能。
     * @param workspacePath 工作区路径。
     * @param replyToMessage 回复消息。
     * @param enableDirectImageProcessing 是否将图片附件转换为link标签（用于直接图片处理）。
     * @param enableDirectAudioProcessing 是否将音频附件转换为link标签（用于直接音频处理）。
     * @param enableDirectVideoProcessing 是否将视频附件转换为link标签（用于直接视频处理）。
     * @return 格式化后的完整消息字符串。
     */
    suspend fun buildUserMessageContent(
        messageText: String,
        attachments: List<AttachmentInfo>,
        enableMemoryQuery: Boolean,
        enableWorkspaceAttachment: Boolean = false,
        workspacePath: String? = null,
        replyToMessage: ChatMessage? = null,
        enableDirectImageProcessing: Boolean = false,
        enableDirectAudioProcessing: Boolean = false,
        enableDirectVideoProcessing: Boolean = false
    ): String {
        // 1. 构建回复标签（如果有回复消息）
        val replyTag = replyToMessage?.let { message ->
            val cleanContent = message.content
                .replace(Regex("<[^>]*>"), "") // 移除XML标签
                .trim()
                .let { if (it.length > 100) it.take(100) + "..." else it }

            val roleName = message.roleName ?: if (message.sender == "ai") "AI" else "用户"
            val instruction = "用户正在回复你之前的这条消息："
            "<reply_to sender=\"${roleName}\" timestamp=\"${message.timestamp}\">${instruction}\"${cleanContent}\"</reply_to>"
        } ?: ""

        // 3. 根据开关决定是否生成工作区附着
        val workspaceTag = if (enableWorkspaceAttachment && !workspacePath.isNullOrBlank() && !messageText.contains("<workspace_attachment>", ignoreCase = true)) {
            try {
                val workspaceContent = WorkspaceAttachmentProcessor.generateWorkspaceAttachment(
                    context = context,
                    workspacePath = workspacePath
                )
                "<workspace_attachment>$workspaceContent</workspace_attachment>"
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成工作区附着失败", e)
                ""
            }
        } else ""

        // 4. 构建附件标签
        val attachmentTags = if (attachments.isNotEmpty()) {
            attachments.joinToString(" ") { attachment ->
                // 如果启用直接图片处理且附件是图片，转换为link标签
                if (enableDirectImageProcessing && attachment.mimeType.startsWith("image/", ignoreCase = true)) {
                    try {
                        val imageId = ImagePoolManager.addImage(attachment.filePath)
                        "<link type=\"image\" id=\"$imageId\"></link>"
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加图片到池失败: ${attachment.filePath}", e)
                        // 失败时回退到普通附件格式
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else if (enableDirectAudioProcessing && attachment.mimeType.startsWith("audio/", ignoreCase = true)) {
                    try {
                        val audioId = MediaPoolManager.addMedia(attachment.filePath, attachment.mimeType)
                        if (audioId == "error") {
                            throw IllegalStateException("addMedia returned error")
                        }
                        "<link type=\"audio\" id=\"$audioId\"></link>"
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加音频到池失败: ${attachment.filePath}", e)
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else if (enableDirectVideoProcessing && attachment.mimeType.startsWith("video/", ignoreCase = true)) {
                    try {
                        val videoId = MediaPoolManager.addMedia(attachment.filePath, attachment.mimeType)
                        if (videoId == "error") {
                            throw IllegalStateException("addMedia returned error")
                        }
                        "<link type=\"video\" id=\"$videoId\"></link>"
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "添加视频到池失败: ${attachment.filePath}", e)
                        val attributes = buildString {
                            append("id=\"${attachment.filePath}\" ")
                            append("filename=\"${attachment.fileName}\" ")
                            append("type=\"${attachment.mimeType}\"")
                            if (attachment.fileSize > 0) {
                                append(" size=\"${attachment.fileSize}\"")
                            }
                        }
                        "<attachment $attributes>${attachment.content}</attachment>"
                    }
                } else {
                    // 非图片或未启用直接图片处理，使用普通附件格式
                    val attributes = buildString {
                        append("id=\"${attachment.filePath}\" ")
                        append("filename=\"${attachment.fileName}\" ")
                        append("type=\"${attachment.mimeType}\"")
                        if (attachment.fileSize > 0) {
                            append(" size=\"${attachment.fileSize}\"")
                        }
                    }
                    "<attachment $attributes>${attachment.content}</attachment>"
                }
            }
        } else ""

        // 5. 组合最终消息
        return listOf(messageText, attachmentTags, workspaceTag, replyTag)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * 发送消息给AI服务。
     *
     * @param enhancedAiService AI服务实例。
     * @param messageContent 已经构建好的完整消息内容。
     * @param chatHistory 完整的聊天历史记录。
     * @param workspacePath 当前工作区路径。
     * @param promptFunctionType 提示功能类型。
     * @param enableThinking 是否启用思考过程。
     * @param thinkingGuidance 是否启用思考引导。
     * @param enableMemoryQuery 是否允许AI查询记忆。
     * @param maxTokens 最大token数量。
     * @param tokenUsageThreshold token使用阈值。
     * @param onNonFatalError 非致命错误回调。
     * @param onTokenLimitExceeded token限制超出回调。
     * @param characterName 角色名称，用于通知。
     * @param avatarUri 角色头像URI，用于通知。
     * @return 包含AI响应流的ChatMessage对象。
     */
    suspend fun sendMessage(
        enhancedAiService: EnhancedAIService,
        messageContent: String,
        chatHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        enableThinking: Boolean,
        thinkingGuidance: Boolean,
        enableMemoryQuery: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit,
        onTokenLimitExceeded: (suspend () -> Unit)? = null, // 新增回调
        characterName: String? = null,
        avatarUri: String? = null
    ): SharedStream<String> {
        activeEnhancedAiService = enhancedAiService // Keep a reference to the service for cancellation
        val memory = getMemoryFromMessages(chatHistory)
        
        // 检查是否启用了深度搜索模式（计划模式）
        val isDeepSearchEnabled = apiPreferences.enableAiPlanningFlow.first()
        
        return withContext(Dispatchers.IO) {
            val maxImageHistoryUserTurns = apiPreferences.maxImageHistoryUserTurnsFlow.first()
            val maxMediaHistoryUserTurns = apiPreferences.maxMediaHistoryUserTurnsFlow.first()

            val memoryAfterImageLimit = limitImageLinksInChatHistory(memory, maxImageHistoryUserTurns)
            val memoryForRequest = limitMediaLinksInChatHistory(memoryAfterImageLimit, maxMediaHistoryUserTurns)
            val beforeImageLinkCount = memory.count { (_, content) -> ImageLinkParser.hasImageLinks(content) }
            val afterImageLinkCount = memoryForRequest.count { (_, content) -> ImageLinkParser.hasImageLinks(content) }
            if (beforeImageLinkCount != afterImageLinkCount) {
                AppLogger.d(
                    TAG,
                    "历史图片裁剪生效: limit=$maxImageHistoryUserTurns, before=$beforeImageLinkCount, after=$afterImageLinkCount"
                )
            }

            val beforeMediaLinkCount = memory.count { (_, content) -> MediaLinkParser.hasMediaLinks(content) }
            val afterMediaLinkCount = memoryForRequest.count { (_, content) -> MediaLinkParser.hasMediaLinks(content) }
            if (beforeMediaLinkCount != afterMediaLinkCount) {
                AppLogger.d(
                    TAG,
                    "历史音视频裁剪生效: limit=$maxMediaHistoryUserTurns, before=$beforeMediaLinkCount, after=$afterMediaLinkCount"
                )
            }
            
            if (isDeepSearchEnabled) {
                // 创建计划模式管理器
                val planModeManager = PlanModeManager(context, enhancedAiService)
                
                // 检查消息是否适合使用深度搜索模式
                val shouldUseDeepSearch = planModeManager.shouldUseDeepSearchMode(messageContent)
                
                if (shouldUseDeepSearch) {
                    activePlanModeManager = planModeManager // Store active manager
                    AppLogger.d(TAG, "启用深度搜索模式处理消息")
                    
                    // 设置执行计划的特定UI状态
                    enhancedAiService.setInputProcessingState(
                        InputProcessingState.ExecutingPlan("正在执行深度搜索...")
                    )
                    
                    // 使用深度搜索模式
                    return@withContext planModeManager.executeDeepSearchMode(
                        userMessage = messageContent,
                        chatHistory = memoryForRequest,
                        workspacePath = workspacePath,
                        maxTokens = maxTokens,
                        tokenUsageThreshold = tokenUsageThreshold,
                        onNonFatalError = onNonFatalError
                    ).share(scope)
                } else {
                    activePlanModeManager = null // Clear manager if not used
                    AppLogger.d(TAG, "消息不适合深度搜索模式，使用普通模式")
                }
            } else {
                activePlanModeManager = null // Clear manager if disabled
            }
            
            // 获取流式输出设置
            val disableStreamOutput = apiPreferences.disableStreamOutputFlow.first()
            val enableStream = !disableStreamOutput
            
            // 使用普通模式
            enhancedAiService.sendMessage(
                message = messageContent,
                chatHistory = memoryForRequest, // Correct parameter name is chatHistory
                workspacePath = workspacePath,
                promptFunctionType = promptFunctionType,
                enableThinking = enableThinking,
                thinkingGuidance = thinkingGuidance,
                enableMemoryQuery = enableMemoryQuery,
                maxTokens = maxTokens,
                tokenUsageThreshold = tokenUsageThreshold,
                onNonFatalError = onNonFatalError,
                onTokenLimitExceeded = onTokenLimitExceeded, // 传递回调
                characterName = characterName,
                avatarUri = avatarUri,
                stream = enableStream
            ).share(scope) // 使用.share()将其转换为共享流
        }
    }

    private fun limitMediaLinksInChatHistory(
        history: List<Pair<String, String>>,
        keepLastUserMediaTurns: Int
    ): List<Pair<String, String>> {
        val limit = keepLastUserMediaTurns.coerceAtLeast(0)
        val totalUserTurns = history.count { (role, _) -> role == "user" }
        val keepFromTurn = (totalUserTurns - limit).coerceAtLeast(0)

        var currentUserTurnIndex = -1
        return history.map { (role, content) ->
            if (role == "user") {
                currentUserTurnIndex += 1
            }

            val shouldKeepMedia = limit > 0 && currentUserTurnIndex >= keepFromTurn
            if (!shouldKeepMedia && MediaLinkParser.hasMediaLinks(content)) {
                val removed = MediaLinkParser.removeMediaLinks(content).trim()
                role to (removed.ifBlank { "[音视频已省略]" })
            } else {
                role to content
            }
        }
    }

    private fun limitImageLinksInChatHistory(
        history: List<Pair<String, String>>,
        keepLastUserImageTurns: Int
    ): List<Pair<String, String>> {
        val limit = keepLastUserImageTurns.coerceAtLeast(0)
        val totalUserTurns = history.count { (role, _) -> role == "user" }
        val keepFromTurn = (totalUserTurns - limit).coerceAtLeast(0)

        var currentUserTurnIndex = -1
        return history.map { (role, content) ->
            if (role == "user") {
                currentUserTurnIndex += 1
            }

            val shouldKeepImages = limit > 0 && currentUserTurnIndex >= keepFromTurn
            if (!shouldKeepImages && ImageLinkParser.hasImageLinks(content)) {
                val removed = ImageLinkParser.removeImageLinks(content).trim()
                role to (removed.ifBlank { "[图片已省略]" })
            } else {
                role to content
            }
        }
    }

    /**
     * 取消当前正在进行的AI操作。
     * 这会同时尝试取消计划执行（如果正在进行）和底层的AI流。
     */
    fun cancelCurrentOperation() {
        AppLogger.d(TAG, "请求取消当前AI操作...")

        // 1. 取消计划模式（如果正在运行）
        activePlanModeManager?.let {
            AppLogger.d(TAG, "正在取消计划模式执行...")
            it.cancel()
            activePlanModeManager = null // 取消后清除引用
        }

        // 2. 取消底层的 EnhancedAIService（处理普通流和工具调用）
        activeEnhancedAiService?.let {
            AppLogger.d(TAG, "正在取消 EnhancedAIService 对话...")
            it.cancelConversation()
        }

        AppLogger.d(TAG, "AI操作取消请求已发送。")
    }

    /**
     * 请求AI服务生成对话总结。
     *
     * @param enhancedAiService AI服务实例。
     * @param messages 需要总结的消息列表。
     * @param autoContinue 是否为自动续写模式，如果是则在总结消息尾部添加续写提示。
     * @return 包含总结内容的ChatMessage对象，如果无需总结或总结失败则返回null。
     */
    suspend fun summarizeMemory(
        enhancedAiService: EnhancedAIService,
        messages: List<ChatMessage>,
        autoContinue: Boolean = false
    ): ChatMessage? {
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val previousSummary = if (lastSummaryIndex != -1) messages[lastSummaryIndex].content.trim() else null

        val messagesToSummarize = when {
            lastSummaryIndex == -1 -> messages.filter { it.sender == "user" || it.sender == "ai" }
            else -> messages.subList(lastSummaryIndex + 1, messages.size)
                .filter { it.sender == "user" || it.sender == "ai" }
        }

        if (messagesToSummarize.isEmpty()) {
            AppLogger.d(TAG, "没有新消息需要总结")
            return null
        }

        val memoryTagRegex = Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL)
        val conversationReviewEntries = mutableListOf<Pair<String, String>>()
        fun String.condenseForQuote(): String {
            val normalized = trim()
            return if (normalized.length <= 40) {
                normalized
            } else {
                "${normalized.take(20)}...${normalized.takeLast(20)}"
            }
        }
        val conversationToSummarize = messagesToSummarize.mapIndexed { index, message ->
            val role = if (message.sender == "user") "user" else "assistant"
            val cleanedContent = if (role == "user") {
                message.content.replace(memoryTagRegex, "").trim()
            } else {
                message.content
            }
            if (cleanedContent.isNotBlank()) {
                val displayContent =
                    if (role == "assistant") cleanedContent.condenseForQuote() else cleanedContent
                conversationReviewEntries.add(role to displayContent)
            }
            Pair(role, "#${index + 1}: $cleanedContent")
        }

        return try {
            AppLogger.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")
            val summary = enhancedAiService.generateSummary(conversationToSummarize, previousSummary)
            AppLogger.d(TAG, "AI生成总结完成: ${summary.take(50)}...")

            if (summary.isBlank()) {
                AppLogger.e(TAG, "AI生成的总结内容为空，放弃本次总结")
                null
            } else {
                // 如果是自动续写，在总结消息尾部添加续写提示
                val trimmedSummary = summary.trim()
                val summaryWithQuotes = buildString {
                    append(trimmedSummary)
                    if (conversationReviewEntries.isNotEmpty()) {
                        append("\n\n对话回顾：\n")
                        conversationReviewEntries.forEach { (role, content) ->
                            append("- ")
                            append(if (role == "user") "用户" else "AI")
                            append(": ")
                            append(content)
                            append("\n")
                        }
                    }
                }.trimEnd()

                val finalSummary = if (autoContinue) {
                    "$summaryWithQuotes\n\n请你继续，如果任务完成，输出总结"
                } else {
                    summaryWithQuotes
                }
                
                ChatMessage(
                    sender = "summary",
                    content = finalSummary,
                    timestamp = System.currentTimeMillis(),
                    roleName = "system" // 总结消息的角色名
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI生成总结过程中发生异常", e)
            null
        }
    }

    /**
     * 判断是否应该生成对话总结。
     *
     * @param messages 完整的消息列表。
     * @param currentTokens 当前上下文的token数量。
     * @param maxTokens 上下文窗口的最大token数量。
     * @return 如果应该生成总结，则返回true。
     */
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        enableSummary: Boolean,
        enableSummaryByMessageCount: Boolean,
        summaryMessageCountThreshold: Int
    ): Boolean {
        // 首先检查总结功能是否启用
        if (!enableSummary) {
            return false
        }

        // 检查Token阈值
        if (maxTokens > 0) {
            val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()
            if (usageRatio >= tokenUsageThreshold) {
                AppLogger.d(TAG, "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold). Triggering summary.")
                return true
            }
        }

        // 检查消息条数阈值（如果启用）
        if (enableSummaryByMessageCount) {
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
            val relevantMessages = if (lastSummaryIndex != -1) {
                messages.subList(lastSummaryIndex + 1, messages.size)
            } else {
                messages
            }
            val userAiMessagesSinceLastSummary = relevantMessages.count { it.sender == "user"}

            if (userAiMessagesSinceLastSummary >= summaryMessageCountThreshold) {
                AppLogger.d(TAG, "自上次总结后新消息数量达到阈值 ($userAiMessagesSinceLastSummary)，生成总结.")
                return true
            }
        }

        AppLogger.d(TAG, "未达到生成总结的条件. Token使用率: ${if (maxTokens > 0) currentTokens.toDouble() / maxTokens else 0.0}")
        return false
    }

    /**
     * 从完整的聊天记录中提取用于AI上下文的“记忆”。
     * 这会获取上次总结之后的所有消息。
     *
     * @param messages 完整的聊天记录。
     * @return 一个Pair列表，包含角色和内容，用于AI请求。
     */
    fun getMemoryFromMessages(messages: List<ChatMessage>): List<Pair<String, String>> {
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val relevantMessages = if (lastSummaryIndex != -1) {
            messages.subList(lastSummaryIndex, messages.size)
        } else {
            messages
        }
        return relevantMessages
            .filter { it.sender == "user" || it.sender == "ai" || it.sender == "summary" }
            .map {
                val role = if (it.sender == "ai") "assistant" else "user" // "summary" is treated as user-side context
                Pair(role, it.content)
            }
    }
} 