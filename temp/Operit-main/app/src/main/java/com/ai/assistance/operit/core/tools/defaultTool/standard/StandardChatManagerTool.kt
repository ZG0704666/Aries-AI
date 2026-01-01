package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.ChatCreationResultData
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.ChatServiceStartResultData
import com.ai.assistance.operit.core.tools.ChatSwitchResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.FloatingChatService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * 对话管理工具
 * 通过绑定 FloatingChatService 来管理对话，实现创建、切换、列出对话和发送消息等功能
 */
class StandardChatManagerTool(private val context: Context) {

    companion object {
        private const val TAG = "StandardChatManagerTool"
        private const val SERVICE_CONNECTION_TIMEOUT = 5000L // 5秒超时
    }

    // Service 连接状态
    private var chatCore: ChatServiceCore? = null
    private var floatingService: FloatingChatService? = null
    private var isBound = false
    private var connectionDeferred = CompletableDeferred<Boolean>()

    // Service 连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.d(TAG, "Service connected")
            val binder = service as? FloatingChatService.LocalBinder
            if (binder != null) {
                floatingService = binder.getService()
                chatCore = binder.getChatCore()
                isBound = true
                connectionDeferred.complete(true)
                AppLogger.d(TAG, "ChatServiceCore obtained successfully")
            } else {
                AppLogger.e(TAG, "Failed to cast binder")
                connectionDeferred.complete(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.d(TAG, "Service disconnected")
            chatCore = null
            floatingService = null
            isBound = false
        }
    }

    /**
     * 确保服务已连接
     * @return 是否成功连接
     */
    private suspend fun ensureServiceConnected(): Boolean {
        // 如果已经连接，直接返回
        if (isBound && chatCore != null) {
            return true
        }

        // 如果正在连接中，等待连接完成
        if (!connectionDeferred.isCompleted) {
            return try {
                withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                    connectionDeferred.await()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Service connection timeout", e)
                false
            }
        }

        // 重新启动和绑定服务
        return try {
            // 重置 deferred
            connectionDeferred = CompletableDeferred()
            
            val intent = Intent(context, FloatingChatService::class.java)
            
            // 启动服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // 等待服务启动
            delay(500)
            
            // 绑定服务
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            
            if (!bound) {
                AppLogger.e(TAG, "Failed to bind service")
                connectionDeferred.complete(false)
                return false
            }

            // 等待连接完成
            withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                connectionDeferred.await()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to ensure service connected", e)
            connectionDeferred.completeExceptionally(e)
            false
        }
    }

    /**
     * 解绑服务
     */
    fun unbindService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
                isBound = false
                chatCore = null
                floatingService = null
                AppLogger.d(TAG, "Service unbound")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unbinding service", e)
            }
        }
    }

    /**
     * 启动对话服务
     */
    suspend fun startChatService(tool: AITool): ToolResult {
        return try {
            val connected = ensureServiceConnected()
            
            if (connected) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatServiceStartResultData(isConnected = true)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "对话服务启动失败或连接超时"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatServiceStartResultData(isConnected = false),
                error = "启动对话服务时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 创建新的对话
     */
    suspend fun createNewChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "ChatServiceCore 未初始化"
            )

            // 获取创建前的 chatId
            val oldChatId = core.currentChatId.value
            
            // 创建新对话
            core.createNewChat()
            
            // 等待 chatId 更新（最多等待2秒）
            var attempts = 0
            while (attempts < 20 && core.currentChatId.value == oldChatId) {
                delay(100)
                attempts++
            }
            
            val newChatId = core.currentChatId.value
            
            if (newChatId != null && newChatId != oldChatId) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatCreationResultData(chatId = newChatId)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "创建对话失败，未能获取新的对话ID"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create new chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "创建对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 列出所有对话
     */
    suspend fun listChats(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatListResultData(
                        totalCount = 0,
                        currentChatId = null,
                        chats = emptyList()
                    ),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "ChatServiceCore 未初始化"
            )

            val chatHistories = core.chatHistories.value
            val currentChatId = core.currentChatId.value
            
            // 构建对话信息列表
            val chatInfoList = chatHistories.map { chat ->
                ChatListResultData.ChatInfo(
                    id = chat.id,
                    title = chat.title,
                    messageCount = chat.messages.size,
                    createdAt = chat.createdAt.toString(),
                    updatedAt = chat.updatedAt.toString(),
                    isCurrent = chat.id == currentChatId,
                    inputTokens = chat.inputTokens,
                    outputTokens = chat.outputTokens
                )
            }
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatListResultData(
                    totalCount = chatHistories.size,
                    currentChatId = currentChatId,
                    chats = chatInfoList
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list chats", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "列出对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 切换对话
     */
    suspend fun switchChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "ChatServiceCore 未初始化"
            )

            val chatId = tool.parameters.find { it.name == "chat_id" }?.value
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "参数错误：缺少 chat_id"
                )
            }

            // 检查对话是否存在并获取标题
            val targetChat = core.chatHistories.value.find { it.id == chatId }
            if (targetChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = ""),
                    error = "对话不存在：$chatId"
                )
            }

            // 切换对话
            core.switchChat(chatId)
            
            // 等待切换完成（最多等待1秒）
            var attempts = 0
            while (attempts < 10 && core.currentChatId.value != chatId) {
                delay(100)
                attempts++
            }
            
            if (core.currentChatId.value == chatId) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatSwitchResultData(
                        chatId = chatId,
                        chatTitle = targetChat.title
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = targetChat.title),
                    error = "切换对话失败，当前对话ID未更新"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to switch chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "切换对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 向AI发送消息
     */
    suspend fun sendMessageToAI(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "ChatServiceCore 未初始化"
            )

            val message = tool.parameters.find { it.name == "message" }?.value
            if (message.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "参数错误：缺少 message"
                )
            }

            // 可选的 chat_id 参数
            val targetChatId = tool.parameters.find { it.name == "chat_id" }?.value
            
            // 如果指定了 chat_id，先切换到该对话
            if (!targetChatId.isNullOrBlank()) {
                val chatExists = core.chatHistories.value.any { it.id == targetChatId }
                if (!chatExists) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = targetChatId, message = message),
                        error = "指定的对话不存在：$targetChatId"
                    )
                }
                
                // 切换到目标对话
                if (core.currentChatId.value != targetChatId) {
                    core.switchChat(targetChatId)
                    delay(200) // 等待切换完成
                }
            } else {
                // 如果没有当前对话，创建一个新对话
                if (core.currentChatId.value == null) {
                    core.createNewChat()
                    delay(200) // 等待创建完成
                }
            }

            val currentChatId = core.currentChatId.value
            if (currentChatId == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "无法获取当前对话ID"
                )
            }

            // 设置消息文本
            core.updateUserMessage(message)

            // 发送消息（包含总结逻辑）
            core.sendUserMessage(PromptFunctionType.CHAT)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = MessageSendResultData(
                    chatId = currentChatId,
                    message = message
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "发送消息时发生错误: ${e.message}"
            )
        }
    }
}

