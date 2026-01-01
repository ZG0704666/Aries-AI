package com.ai.assistance.operit.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.data.converter.*
import com.ai.assistance.operit.data.exporter.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// 仅保留这个DataStore用于存储当前聊天ID
private val Context.currentChatIdDataStore by preferencesDataStore(name = "current_chat_id")

class ChatHistoryManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ChatHistoryManager"

        @Volatile
        private var INSTANCE: ChatHistoryManager? = null

        fun getInstance(context: Context): ChatHistoryManager {
            return INSTANCE
                ?: synchronized(this) {
                    val instance = ChatHistoryManager(context.applicationContext)
                    INSTANCE = instance
                    instance
                }
        }
    }

    // 使用Room数据库
    private val database = AppDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    init {
        // 确保数据库被初始化
        AppLogger.d(TAG, "ChatHistoryManager初始化，预加载数据库")
        // 使用独立的协程作用域触发数据库初始化
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 预先尝试执行一个简单查询
                val chats = chatDao.getAllChats().first()
                AppLogger.d(TAG, "数据库预加载完成，现有聊天数：${chats.size}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "数据库预加载失败", e)
            }
        }
    }

    // 互斥锁用于同步操作
    private val mutex = Mutex()

    // DataStore键
    private object PreferencesKeys {
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }

    // 辅助函数：将ChatEntity转换为ChatHistory
    private fun ChatEntity.toChatHistory(): ChatHistory {
        val createdAt = Instant.ofEpochMilli(this.createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val updatedAt = Instant.ofEpochMilli(this.updatedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        return ChatHistory(
            id = this.id,
            title = this.title,
            messages = emptyList(), // 关键改动：不加载完整消息，以提高侧边栏性能
            createdAt = createdAt,
            updatedAt = updatedAt,
            inputTokens = this.inputTokens,
            outputTokens = this.outputTokens,
            currentWindowSize = this.currentWindowSize,
            group = this.group, // 映射group字段
            displayOrder = this.displayOrder,
            workspace = this.workspace, // 映射workspace字段
            parentChatId = this.parentChatId, // 映射parentChatId字段
            characterCardName = this.characterCardName, // 映射characterCardName字段
            locked = this.locked
        )
    }

    // 获取所有聊天历史（转换为UI层需要的ChatHistory对象）
    private val _chatHistoriesFlow: Flow<List<ChatHistory>> =
        // 使用原始的Flow方式，这样可以确保数据库变化时会自动刷新
        chatDao.getAllChats().map { chatEntities ->
            // AppLogger.d(TAG, "加载聊天列表，共 ${chatEntities.size} 个聊天")

            // 使用withContext将处理移至IO线程
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                chatEntities.map { it.toChatHistory() }
            }
        }

    // 转换为StateFlow以便共享
    val chatHistoriesFlow =
        _chatHistoriesFlow.stateIn(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            SharingStarted.Lazily,
            emptyList()
        )

    // 角色卡聊天统计
    val characterCardStatsFlow: Flow<List<CharacterCardChatStats>> =
        chatDao.getCharacterCardChatStats()

    /**
     * 根据角色卡过滤聊天历史
     * @param characterCardName 角色卡名称
     * @param isDefault 是否为默认角色卡
     * @return 过滤后的聊天历史Flow
     */
    fun getChatHistoriesByCharacterCard(
        characterCardName: String,
        isDefault: Boolean
    ): Flow<List<ChatHistory>> {
        val sourceFlow = if (isDefault) {
            // 默认角色卡：显示该角色卡名称的对话 + 所有characterCardName为null的对话
            chatDao.getChatsByCharacterCardOrNull(characterCardName)
        } else {
            // 非默认角色卡：只显示该角色卡名称的对话
            chatDao.getChatsByCharacterCard(characterCardName)
        }

        return sourceFlow.map { chatEntities ->
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                chatEntities.map { it.toChatHistory() }
            }
        }
    }

    // 获取当前聊天ID
    private val _currentChatIdFlow: Flow<String?> =
        context.currentChatIdDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences -> preferences[PreferencesKeys.CURRENT_CHAT_ID] }

    // 转换为StateFlow以便共享
    val currentChatIdFlow =
        _currentChatIdFlow.stateIn(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            SharingStarted.Lazily,
            null
        )

    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        mutex.withLock {
            try {
                // 创建聊天实体
                val chatEntity = ChatEntity.fromChatHistory(history)

                // 保存聊天实体
                chatDao.insertChat(chatEntity)

                // 先删除该聊天的所有现有消息
                messageDao.deleteAllMessagesForChat(chatEntity.id)

                // 批量插入所有消息
                val messageEntities =
                    history.messages.mapIndexed { index, message ->
                        MessageEntity.fromChatMessage(chatEntity.id, message, index)
                    }
                messageDao.insertMessages(messageEntities)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    /** 更新聊天锁定状态 */
    suspend fun updateChatLocked(chatId: String, locked: Boolean) {
        mutex.withLock {
            try {
                chatDao.updateChatLocked(chatId, locked)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat locked state for chat $chatId", e)
                throw e
            }
        }
    }

    // 添加单条消息
    suspend fun addMessage(chatId: String, message: ChatMessage, position: Int? = null) {
        mutex.withLock {
            try {
                val messageToPersist =
                    if (position != null) {
                        val messages =
                            messageDao.getMessagesForChat(
                                chatId
                            ) // Already ordered by timestamp
                        if (messages.isEmpty()) {
                            message
                        } else {
                            val validPosition = position.coerceIn(0, messages.size)
                            val newTimestamp =
                                when {
                                    validPosition == 0 -> messages.first().timestamp - 1
                                    validPosition >= messages.size ->
                                        messages.last().timestamp + 1

                                    else -> {
                                        val before = messages[validPosition - 1].timestamp
                                        val after = messages[validPosition].timestamp
                                        // Take the average to find a point in between.
                                        // This assumes timestamps have enough space.
                                        before + (after - before) / 2
                                    }
                                }
                            message.copy(timestamp = newTimestamp)
                        }
                    } else {
                        message
                    }

                // Create message entity, orderIndex is no longer used for ordering.
                val messageEntity =
                    MessageEntity.fromChatMessage(
                        chatId = chatId,
                        message = messageToPersist,
                        orderIndex = 0
                    )
                messageDao.insertMessage(messageEntity)

                // Update chat metadata
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add message for chat $chatId", e)
                throw e
            }
        }
    }

    /**
     * 批量更新聊天记录的顺序和分组
     * @param updatedHistories 包含更新信息的ChatHistory列表
     */
    suspend fun updateChatOrderAndGroup(updatedHistories: List<ChatHistory>) {
        mutex.withLock {
            try {
                val timestamp = System.currentTimeMillis()
                val entitiesToUpdate = updatedHistories.map { history ->
                    // Find the original entity to keep other fields intact
                    val originalEntity = chatDao.getChatById(history.id)
                    originalEntity?.copy(
                        displayOrder = history.displayOrder,
                        group = history.group,
                        updatedAt = timestamp
                    ) ?: ChatEntity.fromChatHistory(history.copy(updatedAt = LocalDateTime.now()))
                }
                chatDao.updateChats(entitiesToUpdate)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat order and group", e)
                throw e
            }
        }
    }

    /**
     * 重命名分组
     * @param oldName 旧的分组名称
     * @param newName 新的分组名称
     * @param characterCardName 角色卡名称，如果为null则更新所有同名分组
     */
    suspend fun updateGroupName(oldName: String, newName: String, characterCardName: String?) {
        mutex.withLock {
            try {
                if (characterCardName != null) {
                    // 只更新指定角色卡下的分组（使用 SQL 批量操作）
                    chatDao.updateGroupNameForCharacter(oldName, newName, characterCardName)
                } else {
                    // 更新所有同名分组
                    chatDao.updateGroupName(oldName, newName)
                }
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to rename group from $oldName to $newName (character: $characterCardName)",
                    e
                )
                throw e
            }
        }
    }

    /**
     * 删除分组
     * @param groupName 要删除的分组名称
     * @param deleteChats 是否同时删除分组下的聊天记录
     * @param characterCardName 角色卡名称，如果为null则删除所有同名分组
     */
    suspend fun deleteGroup(groupName: String, deleteChats: Boolean, characterCardName: String?) {
        mutex.withLock {
            try {
                if (characterCardName != null) {
                    // 只删除指定角色卡下的分组（使用 SQL 批量操作）
                    if (deleteChats) {
                        chatDao.deleteChatsInGroupForCharacter(groupName, characterCardName)
                        // 保留被锁定的聊天：仅清除锁定聊天的分组
                        chatDao.removeGroupFromLockedChatsForCharacter(groupName, characterCardName)
                    } else {
                        chatDao.removeGroupFromChatsForCharacter(groupName, characterCardName)
                    }
                } else {
                    // 删除所有同名分组
                    if (deleteChats) {
                        chatDao.deleteChatsInGroup(groupName)
                        // 保留被锁定的聊天：仅清除锁定聊天的分组
                        chatDao.removeGroupFromLockedChats(groupName)
                    } else {
                        chatDao.removeGroupFromChats(groupName)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Failed to delete group $groupName (deleteChats: $deleteChats, character: $characterCardName)",
                    e
                )
                throw e
            }
        }
    }

    /**
     * 删除单条消息.
     * @param chatId 聊天ID
     * @param timestamp 消息时间戳
     */
    suspend fun deleteMessage(chatId: String, timestamp: Long) {
        mutex.withLock {
            try {
                AppLogger.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp: $timestamp")
                messageDao.deleteMessageByTimestamp(chatId, timestamp)
                AppLogger.d(TAG, "消息从数据库删除成功.")

                // Update chat metadata
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to delete message with timestamp $timestamp for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新现有消息
    suspend fun updateMessage(chatId: String, message: ChatMessage) {
        mutex.withLock {
            try {
                // 找到相应的消息实体
                val existingMessage = messageDao.getMessageByTimestamp(chatId, message.timestamp)

                if (existingMessage != null) {
                    // 更新现有消息
                    messageDao.updateMessageContent(existingMessage.messageId, message.content)

                    // 更新聊天元数据时间戳
                    val chat = chatDao.getChatById(chatId)
                    if (chat != null) {
                        chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens,
                            currentWindowSize = chat.currentWindowSize
                        )
                    }
                } else {
                    // 如果找不到现有消息，则添加新消息
                    addMessage(chatId, message)
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    /**
     * 从数据库中删除指定时间戳之后的所有消息。 这需要您在MessageDao中添加相应的@Query。
     *
     * 示例:
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp >= :timestamp")
     * suspend fun deleteMessagesFrom(chatId: String, timestamp: Long)
     * ```
     */
    suspend fun deleteMessagesFrom(chatId: String, timestamp: Long) {
        mutex.withLock {
            try {
                AppLogger.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp >=: $timestamp")
                messageDao.deleteMessagesFrom(chatId, timestamp)
                AppLogger.d(TAG, "后续消息从数据库删除成功.")
                // 更新聊天元数据时间戳
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = chat.inputTokens,
                        outputTokens = chat.outputTokens,
                        currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "从 $timestamp 开始为聊天 $chatId 删除消息失败", e)
                throw e
            }
        }
    }

    /**
     * 清除一个聊天中的所有消息，但保留聊天本身。
     *
     * 这需要您在MessageDao中添加相应的@Query。
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId")
     * suspend fun deleteAllMessagesForChat(chatId: String)
     * ```
     */
    suspend fun clearChatMessages(chatId: String) {
        mutex.withLock {
            try {
                messageDao.deleteAllMessagesForChat(chatId)
                // 更新聊天元数据
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = 0,
                        outputTokens = 0,
                        currentWindowSize = 0
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "为聊天 $chatId 清除消息失败", e)
                throw e
            }
        }
    }

    // 更新聊天标题
    suspend fun updateChatTitle(chatId: String, title: String) {
        mutex.withLock {
            try {
                chatDao.updateChatTitle(chatId, title)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat title for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天绑定的角色卡
    suspend fun updateChatCharacterCardName(chatId: String, characterCardName: String?) {
        mutex.withLock {
            try {
                chatDao.updateChatCharacterCardName(chatId, characterCardName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat character card for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天的token计数
    suspend fun updateChatTokenCounts(
        chatId: String,
        inputTokens: Int,
        outputTokens: Int,
        currentWindowSize: Int
    ) {
        mutex.withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                        chatId = chatId,
                        title = chat.title,
                        timestamp = System.currentTimeMillis(),
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        currentWindowSize = currentWindowSize
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 设置当前聊天ID
    suspend fun setCurrentChatId(chatId: String) {
        context.currentChatIdDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID] = chatId
        }
    }

    // 删除聊天历史
    suspend fun deleteChatHistory(chatId: String): Boolean {
        mutex.withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat?.locked == true) {
                    AppLogger.w(TAG, "Chat $chatId is locked; skip deletion")
                    return false
                }
                if (chat == null) {
                    return false
                }
                // 删除聊天实体（级联删除所有消息）
                chatDao.deleteChat(chatId)

                // 如果删除的是当前聊天，清除当前聊天ID
                val currentChatId = currentChatIdFlow.first()
                if (currentChatId == chatId) {
                    context.currentChatIdDataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                    }
                }
                return true
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 创建新对话
    suspend fun createNewChat(
        group: String? = null,
        inheritGroupFromChatId: String? = null,
        characterCardName: String? = null
    ): ChatHistory {
        val dateTime = LocalDateTime.now()
        val formattedTime =
            "${dateTime.hour}:${
                dateTime.minute.toString().padStart(2, '0')
            }:${dateTime.second.toString().padStart(2, '0')}"

        val localizedContext = LocaleUtils.getLocalizedContext(context)

        // 确定新对话的分组
        val finalGroup = when {
            // 如果显式指定了分组，使用指定的分组
            group != null -> group
            // 如果要继承分组，尝试从指定的对话获取分组
            inheritGroupFromChatId != null -> {
                chatDao.getChatById(inheritGroupFromChatId)?.group
            }
            // 默认为空分组（不分组）
            else -> null
        }

        val newHistory =
            ChatHistory(
                title = "${localizedContext.getString(R.string.new_conversation)} $formattedTime",
                messages = listOf<ChatMessage>(),
                inputTokens = 0,
                outputTokens = 0,
                group = finalGroup,
                characterCardName = characterCardName // 使用传入的角色卡名称，如果为null则不绑定
            )

        // 保存新聊天
        val chatEntity = ChatEntity.fromChatHistory(newHistory)
        chatDao.insertChat(chatEntity)

        // 设置为当前聊天
        setCurrentChatId(newHistory.id)

        return newHistory
    }

    /** 更新聊天工作区 */
    suspend fun updateChatWorkspace(chatId: String, workspace: String?) {
        mutex.withLock {
            try {
                chatDao.updateChatWorkspace(chatId, workspace)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat workspace for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天分组
    suspend fun updateChatGroup(chatId: String, group: String?) {
        mutex.withLock {
            try {
                chatDao.updateChatGroup(chatId, group)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to update chat group for chat $chatId", e)
                throw e
            }
        }
    }

    // 直接加载聊天消息
    suspend fun loadChatMessages(chatId: String): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // AppLogger.d(TAG, "直接从数据库加载聊天 $chatId 的消息")
                val messages = messageDao.getMessagesForChat(chatId)
                // AppLogger.d(TAG, "聊天 $chatId 共加载 ${messages.size} 条消息")
                messages.map { it.toChatMessage() }
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载聊天消息失败", e)
                emptyList()
            }
        }
    }

    /** 搜索包含特定关键词的聊天ID列表 */
    suspend fun searchChatIdsByContent(query: String): Set<String> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) {
                    return@withContext emptySet()
                }
                val chatIds = messageDao.searchChatIdsByContent(query)
                chatIds.toSet()
            } catch (e: Exception) {
                AppLogger.e(TAG, "搜索聊天内容失败: $query", e)
                emptySet()
            }
        }
    }

    /**
     * 创建对话分支
     * @param parentChatId 父对话ID
     * @param upToMessageTimestamp 复制消息到指定时间戳（包含该时间戳的消息）
     * @return 新创建的分支对话
     */
    suspend fun createBranch(
        parentChatId: String,
        upToMessageTimestamp: Long? = null
    ): ChatHistory {
        return mutex.withLock {
            try {
                // 获取父对话
                val parentChat = chatDao.getChatById(parentChatId)
                    ?: throw IllegalArgumentException("父对话不存在: $parentChatId")

                // 获取父对话的消息
                val parentMessages = messageDao.getMessagesForChat(parentChatId)
                    .map { it.toChatMessage() }
                    .sortedBy { it.timestamp }

                // 如果指定了时间戳，只复制到该时间戳的消息
                val messagesToCopy = if (upToMessageTimestamp != null) {
                    parentMessages.filter { it.timestamp <= upToMessageTimestamp }
                } else {
                    parentMessages
                }

                // 创建新对话
                // 分支标题保持与父对话相同，通过 parentChatId 字段和 UI 图标来区分
                val branchHistory = ChatHistory(
                    title = parentChat.title,
                    messages = messagesToCopy,
                    inputTokens = parentChat.inputTokens, // 继承父对话的token统计
                    outputTokens = parentChat.outputTokens, // 继承父对话的token统计
                    currentWindowSize = parentChat.currentWindowSize, // 继承父对话的窗口大小
                    group = parentChat.group,
                    workspace = parentChat.workspace,
                    parentChatId = parentChatId,
                    characterCardName = parentChat.characterCardName // 分支继承父对话的角色卡绑定
                )

                // 保存分支对话
                val branchEntity = ChatEntity.fromChatHistory(branchHistory)
                chatDao.insertChat(branchEntity)

                // 复制消息
                val messageEntities = messagesToCopy.mapIndexed { index, message ->
                    MessageEntity.fromChatMessage(branchEntity.id, message, index)
                }
                messageDao.insertMessages(messageEntities)

                // 设置为当前聊天
                setCurrentChatId(branchHistory.id)

                AppLogger.d(
                    TAG,
                    "创建分支对话: ${branchHistory.id}, 父对话: $parentChatId, 消息数: ${messagesToCopy.size}"
                )
                branchHistory
            } catch (e: Exception) {
                AppLogger.e(TAG, "创建分支对话失败", e)
                throw e
            }
        }
    }

    /**
     * 获取指定对话的所有分支
     * @param parentChatId 父对话ID
     * @return 分支对话列表
     */
    suspend fun getBranches(parentChatId: String): List<ChatHistory> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val branchEntities = chatDao.getBranchesByParentId(parentChatId)
                branchEntities.map { entity ->
                    val createdAt = Instant.ofEpochMilli(entity.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                    val updatedAt = Instant.ofEpochMilli(entity.updatedAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()

                    ChatHistory(
                        id = entity.id,
                        title = entity.title,
                        messages = emptyList(), // 不加载消息以提高性能
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        inputTokens = entity.inputTokens,
                        outputTokens = entity.outputTokens,
                        currentWindowSize = entity.currentWindowSize,
                        group = entity.group,
                        displayOrder = entity.displayOrder,
                        workspace = entity.workspace,
                        parentChatId = entity.parentChatId,
                        characterCardName = entity.characterCardName,
                        locked = entity.locked
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取分支对话失败: $parentChatId", e)
                emptyList()
            }
        }
    }

    /**
     * 获取指定对话的所有分支（Flow版本）
     * @param parentChatId 父对话ID
     * @return 分支对话Flow
     */
    fun getBranchesFlow(parentChatId: String): Flow<List<ChatHistory>> {
        return chatDao.getBranchesByParentIdFlow(parentChatId).map { branchEntities ->
            branchEntities.map { entity ->
                val createdAt = Instant.ofEpochMilli(entity.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                val updatedAt = Instant.ofEpochMilli(entity.updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                ChatHistory(
                    id = entity.id,
                    title = entity.title,
                    messages = emptyList(), // 不加载消息以提高性能
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    inputTokens = entity.inputTokens,
                    outputTokens = entity.outputTokens,
                    currentWindowSize = entity.currentWindowSize,
                    group = entity.group,
                    displayOrder = entity.displayOrder,
                    workspace = entity.workspace,
                    parentChatId = entity.parentChatId,
                    characterCardName = entity.characterCardName,
                    locked = entity.locked
                )
            }
        }
    }

    /**
     * 导出所有聊天记录到「下载/Operit」目录（默认 JSON 格式）
     * @return 生成的文件绝对路径，失败时返回null
     */
    suspend fun exportChatHistoriesToDownloads(): String? =
        exportChatHistoriesToDownloads(ExportFormat.JSON)

    /**
     * 导出所有聊天记录到「下载/Operit」目录（支持多种格式）
     * @param format 导出格式
     * @return 生成的文件绝对路径，失败时返回null
     */
    suspend fun exportChatHistoriesToDownloads(format: ExportFormat): String? =
        withContext(Dispatchers.IO) {
            try {
                val chatHistoriesBasic = chatHistoriesFlow.first()

                val completeHistories = mutableListOf<ChatHistory>()
                for (chatHistory in chatHistoriesBasic) {
                    val messages = loadChatMessages(chatHistory.id)
                    val completeHistory = chatHistory.copy(messages = messages)
                    completeHistories.add(completeHistory)
                }

                val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val exportDir = File(downloadDir, "Operit")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())

                val exportFile = when (format) {
                    ExportFormat.MARKDOWN -> {
                        val zipFile = File(exportDir, "chat_backup_$timestamp.zip")
                        val usedNames = HashSet<String>()
                        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                            for (history in completeHistories) {
                                val content = MarkdownExporter.exportSingle(history)
                                // 处理文件名中的非法字符
                                var safeTitle = history.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                // 避免文件名过长
                                if (safeTitle.length > 50) {
                                    safeTitle = safeTitle.substring(0, 50)
                                }
                                safeTitle = safeTitle.trim()
                                
                                // 确保文件名唯一
                                var baseName = "$safeTitle.md"
                                var counter = 1
                                while (usedNames.contains(baseName)) {
                                    baseName = "$safeTitle ($counter).md"
                                    counter++
                                }
                                usedNames.add(baseName)

                                zos.putNextEntry(ZipEntry(baseName))
                                zos.write(content.toByteArray())
                                zos.closeEntry()
                            }
                        }
                        zipFile
                    }

                    ExportFormat.JSON -> {
                        val file = File(exportDir, "chat_backup_$timestamp.json")
                        val json = Json {
                            prettyPrint = true
                            encodeDefaults = true
                        }
                        file.writeText(json.encodeToString(completeHistories))
                        file
                    }

                    ExportFormat.HTML -> {
                        val file = File(exportDir, "chat_backup_$timestamp.html")
                        file.writeText(HtmlExporter.exportMultiple(completeHistories))
                        file
                    }

                    ExportFormat.TXT -> {
                        val file = File(exportDir, "chat_backup_$timestamp.txt")
                        file.writeText(TextExporter.exportMultiple(completeHistories))
                        file
                    }

                    ExportFormat.CSV -> {
                        val file = File(exportDir, "chat_backup_$timestamp.json")
                        val json = Json {
                            prettyPrint = true
                            encodeDefaults = true
                        }
                        file.writeText(json.encodeToString(completeHistories))
                        file
                    }
                }

                exportFile.absolutePath
            } catch (e: Exception) {
                AppLogger.e(TAG, "导出聊天记录失败", e)
                null
            }
        }
    /**
     * 从指定URI导入聊天记录（指定格式）
     * @param uri 备份文件URI
     * @param format 指定的格式
     * @return 导入结果统计
     */
    suspend fun importChatHistoriesFromUri(uri: Uri, format: ChatFormat): ChatImportResult =
        withContext(Dispatchers.IO) {
            try {
                val chatHistories = mutableListOf<ChatHistory>()
                var isZipProcessed = false

                // 如果是 Markdown 格式，尝试作为 Zip 处理
                if (format == ChatFormat.MARKDOWN) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { fis ->
                            // 尝试作为 Zip 读取
                            // 注意：ZipInputStream 可能会消耗流，如果不是 Zip，后续重读需要重新 openInputStream
                            ZipInputStream(fis).use { zipStream ->
                                var entry = zipStream.nextEntry
                                if (entry != null) {
                                    // 确实是 Zip 文件
                                    do {
                                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".md")) {
                                            val buffer = ByteArrayOutputStream()
                                            val data = ByteArray(4096)
                                            var count: Int
                                            while (zipStream.read(data).also { count = it } != -1) {
                                                buffer.write(data, 0, count)
                                            }
                                            val content = buffer.toString("UTF-8")
                                            if (content.isNotBlank()) {
                                                chatHistories.addAll(convertToOperitFormat(content, ChatFormat.MARKDOWN))
                                            }
                                        }
                                        zipStream.closeEntry()
                                        entry = zipStream.nextEntry
                                    } while (entry != null)
                                    isZipProcessed = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "尝试解析 Zip 失败，将尝试作为普通文件读取: ${e.message}")
                    }
                }

                if (!isZipProcessed) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext ChatImportResult(0, 0, 0)
                    val content = inputStream.bufferedReader().use { it.readText() }

                    if (content.isBlank()) {
                        throw Exception("导入的文件为空")
                    }

                    AppLogger.d(TAG, "使用指定格式导入: $format")
                    
                    // 转换为 ChatHistory 列表
                    chatHistories.addAll(convertToOperitFormat(content, format))
                }

                if (chatHistories.isEmpty()) {
                    return@withContext ChatImportResult(0, 0, 0)
                }

                // 保存导入的对话
                val existingIds = chatHistoriesFlow.first().map { it.id }.toSet()

                var newCount = 0
                var updatedCount = 0
                var skippedCount = 0

                for (chatHistory in chatHistories) {
                    if (chatHistory.messages.isEmpty()) {
                        skippedCount++
                        continue
                    }

                    if (existingIds.contains(chatHistory.id)) {
                        updatedCount++
                    } else {
                        newCount++
                    }

                    saveChatHistory(chatHistory)
                }

                AppLogger.d(TAG, "导入完成: 新增=$newCount, 更新=$updatedCount, 跳过=$skippedCount")
                ChatImportResult(newCount, updatedCount, skippedCount)
            } catch (e: Exception) {
                AppLogger.e(TAG, "导入聊天记录失败", e)
                throw e
            }
        }
    
    /**
     * 将内容转换为 Operit 格式
     */
    private fun convertToOperitFormat(content: String, format: ChatFormat): List<ChatHistory> {
        return try {
            when (format) {
                ChatFormat.OPERIT -> {
                    // 尝试直接解析为 Operit 格式
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        encodeDefaults = true
                    }
                    try {
                        json.decodeFromString<List<ChatHistory>>(content)
                    } catch (e: Exception) {
                        // 回退到 Gson
                        val gson = GsonBuilder()
                            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                            .create()
                        val type = object : TypeToken<List<ChatHistory>>() {}.type
                        gson.fromJson<List<ChatHistory>>(content, type)
                    }
                }
                
                ChatFormat.CHATGPT -> {
                    AppLogger.d(TAG, "使用 ChatGPT 转换器")
                    ChatGPTConverter().convert(content)
                }
                
                ChatFormat.CHATBOX -> {
                    AppLogger.d(TAG, "使用 ChatBox 转换器")
                    ChatBoxConverter().convert(content)
                }
                
                ChatFormat.MARKDOWN -> {
                    AppLogger.d(TAG, "使用 Markdown 转换器")
                    MarkdownConverter().convert(content)
                }
                
                ChatFormat.GENERIC_JSON -> {
                    AppLogger.d(TAG, "使用通用 JSON 转换器")
                    GenericJsonConverter().convert(content)
                }
                
                ChatFormat.CLAUDE -> {
                    // Claude 格式暂不支持，回退到通用 JSON
                    AppLogger.d(TAG, "Claude 格式回退到通用 JSON 转换器")
                    GenericJsonConverter().convert(content)
                }
                
                else -> {
                    throw ConversionException("不支持的格式: $format")
                }
            }
        } catch (e: ConversionException) {
            throw Exception("格式转换失败: ${e.message}", e)
        } catch (e: Exception) {
            throw Exception("无法解析备份文件：${e.message}\n请确保文件格式正确", e)
        }
    }

    /**
     * 清理绑定已删除角色卡的对话（将characterCardName设为null）
     * @param characterCardName 已删除的角色卡名称
     */
    suspend fun clearCharacterCardBinding(characterCardName: String) {
        try {
            withContext(Dispatchers.IO) {
                chatDao.clearCharacterCardBinding(characterCardName)
                AppLogger.d(TAG, "已清理绑定角色卡 '$characterCardName' 的对话")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理角色卡绑定失败: $characterCardName", e)
        }
    }

    /**
     * 将指定角色卡或未绑定的对话转移到新的角色卡
     * @return 受影响的对话数量
     */
    suspend fun reassignChatsToCharacterCard(
            sourceCharacterCardName: String?,
            targetCharacterCardName: String
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val updated = if (sourceCharacterCardName == null) {
                    chatDao.assignCharacterCardToUnbound(targetCharacterCardName)
                } else {
                    chatDao.renameCharacterCardBinding(sourceCharacterCardName, targetCharacterCardName)
                }
                AppLogger.d(
                        TAG,
                        "角色卡聊天重分配: ${sourceCharacterCardName ?: "未绑定"} -> $targetCharacterCardName, 更新 $updated 条记录"
                )
                updated
            } catch (e: Exception) {
                AppLogger.e(
                        TAG,
                        "重命名角色卡绑定失败: ${sourceCharacterCardName ?: "未绑定"} -> $targetCharacterCardName",
                        e
                )
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天更新角色卡绑定
     * @return 受影响的对话数量
     */
    suspend fun assignCharacterCardToChats(
        chatIds: List<String>,
        targetCharacterCardName: String?
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.updateCharacterCardForChats(chatIds, targetCharacterCardName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量更新聊天角色卡失败: $targetCharacterCardName, chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量为特定聊天更新分组
     * @return 受影响的对话数量
     */
    suspend fun assignGroupToChats(
        chatIds: List<String>,
        groupName: String?
    ): Int {
        if (chatIds.isEmpty()) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                chatDao.updateGroupForChats(chatIds, groupName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量更新聊天分组失败: $groupName, chatIds=$chatIds", e)
                throw e
            }
        }
    }

    /**
     * 批量重命名对话中绑定的角色卡名称
     * @return 受影响的对话数量
     */
    suspend fun renameCharacterCardInChats(oldName: String, newName: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                chatDao.renameCharacterCardBinding(oldName, newName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量重命名对话绑定角色卡失败: $oldName -> $newName", e)
                throw e
            }
        }
    }

    /**
     * 批量重命名消息中的角色名称
     * @return 受影响的消息数量
     */
    suspend fun renameRoleNameInMessages(oldName: String, newName: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                messageDao.renameRoleName(oldName, newName)
            } catch (e: Exception) {
                AppLogger.e(TAG, "批量重命名消息中的角色名失败: $oldName -> $newName", e)
                throw e
            }
        }
    }
}

data class ChatImportResult(
    val new: Int,
    val updated: Int,
    val skipped: Int
) {
    val total: Int
        get() = new + updated
}
