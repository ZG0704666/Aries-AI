package com.ai.assistance.operit.ui.features.settings.screens

import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.data.preferences.PersonaCardChatHistoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- 本地最小工具执行器：仅处理 save_character_info ---
private object LocalCharacterToolExecutor {
    const val TOOL_NAME = "save_character_info"

    fun extractInvocations(raw: String): List<Pair<String, Map<String, String>>> {
        val list = mutableListOf<Pair<String, Map<String, String>>>()
        // 简单 XML 提取：<tool name="..."> <param name="field">..</param><param name="content">..</param></tool>
        val toolRegex = Regex("(?s)<tool\\s+name=\"([^\"]+)\">([\\s\\S]*?)</tool>")
        val paramRegex = Regex("(?s)<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>")
        toolRegex.findAll(raw).forEach { m ->
            val name = m.groupValues.getOrNull(1)?.trim().orEmpty()
            val body = m.groupValues.getOrNull(2) ?: ""
            val params = mutableMapOf<String, String>()
            paramRegex.findAll(body).forEach { pm ->
                val pName = pm.groupValues.getOrNull(1)?.trim().orEmpty()
                val pVal = pm.groupValues.getOrNull(2)?.trim().orEmpty()
                params[pName] = pVal
            }
            list.add(name to params)
        }
        return list
    }

    suspend fun executeSaveCharacterInfo(
        context: android.content.Context,
        characterCardId: String,
        field: String,
        content: String
    ): ToolResult {
        return try {
            val manager = CharacterCardManager.getInstance(context)
            
            // 获取当前角色卡
            val currentCard = manager.getCharacterCard(characterCardId)
            if (currentCard == null) {
                return ToolResult(
                    toolName = TOOL_NAME,
                    success = false,
                    result = StringResultData(""),
                    error = "角色卡不存在"
                )
            }
            
            // 根据字段更新对应内容
            val updatedCard = when (field) {
                "name" -> currentCard.copy(name = content)
                "description" -> currentCard.copy(description = content)
                "characterSetting" -> currentCard.copy(characterSetting = content)
                "openingStatement" -> currentCard.copy(openingStatement = content)
                "otherContent" -> currentCard.copy(otherContent = content)
                "advancedCustomPrompt" -> currentCard.copy(advancedCustomPrompt = content)
                "marks" -> currentCard.copy(marks = content)
                else -> {
                    return ToolResult(
                        toolName = TOOL_NAME,
                        success = false,
                        result = StringResultData(""),
                        error = "不支持的字段: $field"
                    )
                }
            }
            
            withContext(Dispatchers.IO) { 
                manager.updateCharacterCard(updatedCard)
            }
            
            ToolResult(
                toolName = TOOL_NAME,
                success = true,
                result = StringResultData("ok"),
                error = null
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = TOOL_NAME,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        }
    }
}

private data class CharacterChatMessage(
    val role: String, // "user" | "assistant"
    var content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaCardGenerationScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToUserPreferences: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    onNavigateToModelPrompts: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val TAG = "CharacterCardGeneration"

    // 引导文案（顶部说明）
    val characterAssistantIntro = remember {
        """
        嗨嗨～这里是你的角色卡小助手(｡･ω･｡)ﾉ♡ 我会陪你一起把专属角色慢慢捏出来～
        我们按部就班来哦：先告诉我你的称呼，再说说你想要的角色大方向，比方说：
        - 角色名字和身份大概是怎样的？
        - 有哪些可爱的性格关键词？
        - 长相/发型/瞳色/穿搭想要什么感觉？
        - 有没有特别的小设定或能力？
        - 跟其他角色的关系要不要安排一点点？
        
        接下来我会一步步问你关键问题，帮你把细节补齐～
        """.trimIndent()
    }

    val listState = rememberLazyListState()
    val chatMessages = remember { mutableStateListOf<CharacterChatMessage>() }
    var userInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // 角色卡数据
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val tagManager = remember { PromptTagManager.getInstance(context) }
    val chatHistoryManager = remember { PersonaCardChatHistoryManager.getInstance(context) }
    var allCharacterCards by remember { mutableStateOf(listOf<CharacterCard>()) }
    var allTags by remember { mutableStateOf(listOf<PromptTag>()) }
    var activeCardId by remember { mutableStateOf("") }
    var activeCard by remember { mutableStateOf<CharacterCard?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showMessageLimitWarning by remember { mutableStateOf(false) }
    var newCardName by remember { mutableStateOf("") }
    
    // 对话数量限制
    val MESSAGE_LIMIT = 40

    // 编辑器值
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editCharacterSetting by remember { mutableStateOf("") }
    var editOpeningStatement by remember { mutableStateOf("") }
    var editOtherContent by remember { mutableStateOf("") }
    var editAdvancedCustomPrompt by remember { mutableStateOf("") }
    var editMarks by remember { mutableStateOf("") }

    // 1. 一次性初始化：加载所有卡片和标签，并确定初始活跃卡片ID
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            characterCardManager.initializeIfNeeded()
            val cards = characterCardManager.getAllCharacterCards()
            allCharacterCards = cards
            allTags = tagManager.getAllTags()

            var currentId = characterCardManager.activeCharacterCardIdFlow.first()

            // 如果记录的活跃ID无效（例如卡被删除），则默认使用第一张卡
            if (characterCardManager.getCharacterCard(currentId) == null && cards.isNotEmpty()) {
                val firstCardId = cards.first().id
                characterCardManager.setActiveCharacterCard(firstCardId)
                currentId = firstCardId
            }

            // 在主线程更新 activeCardId 以触发后续的 Effect
            withContext(Dispatchers.Main) {
                activeCardId = currentId
            }
        }
    }

    // 2. 响应式效果：当 activeCardId 变化时（初始化或切换），加载卡片详情并重置对话
    LaunchedEffect(activeCardId) {
        if (activeCardId.isBlank()) {
            // 没有活跃卡片的情况
            activeCard = null
            editName = ""; editDescription = ""; editCharacterSetting = ""; editOpeningStatement = ""
            editOtherContent = ""; editAdvancedCustomPrompt = ""; editMarks = ""
            chatMessages.clear()
            chatMessages.add(CharacterChatMessage("assistant", context.getString(R.string.please_select_or_create_card)))
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val card = characterCardManager.getCharacterCard(activeCardId)
            withContext(Dispatchers.Main) {
                activeCard = card

                // 更新编辑器内容
                card?.let {
                    editName = it.name
                    editDescription = it.description
                    editCharacterSetting = it.characterSetting
                    editOpeningStatement = it.openingStatement
                    editOtherContent = it.otherContent
                    editAdvancedCustomPrompt = it.advancedCustomPrompt
                    editMarks = it.marks
                } ?: run {
                    // 如果卡片加载失败，则清空编辑器
                    editName = ""; editDescription = ""; editCharacterSetting = ""; editOpeningStatement = ""
                    editOtherContent = ""; editAdvancedCustomPrompt = ""; editMarks = ""
                }

                // 加载该角色卡的聊天历史
                chatMessages.clear()
                val savedHistory = chatHistoryManager.loadChatHistory(activeCardId)
                if (savedHistory.isNotEmpty()) {
                    // 转换为界面使用的消息格式
                    savedHistory.forEach { msg ->
                        chatMessages.add(CharacterChatMessage(msg.role, msg.content, msg.timestamp))
                    }
                } else {
                    // 如果没有历史记录，添加欢迎消息
                    chatMessages.add(CharacterChatMessage("assistant",
                        context.getString(R.string.persona_generation_welcome, 
                        card?.name ?: context.getString(R.string.new_character))
                    ))
                }
            }
        }
    }

    fun refreshData() {
        scope.launch(Dispatchers.IO) {
            allCharacterCards = characterCardManager.getAllCharacterCards()
            activeCardId = characterCardManager.activeCharacterCardIdFlow.first()
            activeCard = characterCardManager.getCharacterCard(activeCardId)
            
            withContext(Dispatchers.Main) {
                activeCard?.let { card ->
                    editName = card.name
                    editDescription = card.description
                    editCharacterSetting = card.characterSetting
                    editOpeningStatement = card.openingStatement
                    editOtherContent = card.otherContent
                    editAdvancedCustomPrompt = card.advancedCustomPrompt
                    editMarks = card.marks
                }
            }
        }
    }

    // 构建稳定的系统提示词
    fun buildSystemPrompt(): String {
        return """
            你是"角色卡生成助手"。请严格按照以下流程进行角色卡生成：
            
            [生成流程]
            1) 角色名称：询问并确认角色名称
            2) 角色描述：简短的角色描述
            3) 角色设定：详细的角色设定，包括身份、外貌、性格等
            4) 开场白：角色的第一句话或开场白，用于开始对话时的问候语
            5) 其他内容：背景故事、特殊能力等补充信息
            6) 高级自定义：特殊的提示词或交互方式
            7) 备注：不会被拼接到提示词的备注信息，用于记录创作想法或注意事项
            
            [重要规则]
            - 全程语气要活泼可爱喵~
            - 严格按照 1→2→3→4→5→6→7 的顺序进行，不要跳跃
            - 每轮对话只能处理一个步骤，完成后进入下一步
            - 如果用户输入了角色设定，对其进行适当优化与丰富
            - 如果用户说"随便/你看着写"，就帮用户体贴地生成设定内容
            - 生成或补充完后，用一小段话总结当前进度
            - 对于下一个步骤提几个最关键、最具体的小问题
            - 不要重复问已经确认过的内容
            
            [完成条件]
            - 当所有7个步骤都完成时，输出："🎉 角色卡生成完成！所有信息都已保存。"
            - 完成后不再询问任何问题，等待用户的新指令
            
            [工具调用]
            - 每轮对话如果得到了新的角色信息，必须调用工具保存
            - field 取值："name" | "description" | "characterSetting" | "openingStatement" | "otherContent" | "advancedCustomPrompt" | "marks"
            - 工具调用格式为: <tool name="save_character_info"><param name="field">字段名</param><param name="content">内容</param></tool>
            - 例如，如果角色名称确认是“奶糖”，则必须在回答的末尾调用: <tool name="save_character_info"><param name="field">name</param><param name="content">奶糖</param></tool>
            """.trimIndent()
    }
    
    // 检查是否所有字段都已完成
    fun isCharacterCardComplete(): Boolean {
        return activeCard?.let { card ->
            listOf(
                card.name,
                card.description, 
                card.characterSetting,
                card.openingStatement,
                card.otherContent,
                card.advancedCustomPrompt,
                card.marks
            ).all { it.isNotBlank() }
        } ?: false
    }

    // 通过默认底层 AIService 发送消息
    suspend fun requestFromDefaultService(
        prompt: String,
        historyPairs: List<Pair<String, String>>,
        systemPrompt: String? = null
    ): Pair<Stream<String>, AIService> = withContext(Dispatchers.IO) {
        val aiService = EnhancedAIService
            .getInstance(context)
            .getAIServiceForFunction(FunctionType.CHAT)
        val functionalConfigManager = FunctionalConfigManager(context)
        functionalConfigManager.initializeIfNeeded()

        val fullHistory = mutableListOf<Pair<String, String>>()
        if (systemPrompt != null) {
            fullHistory.add("system" to systemPrompt)
        }
        fullHistory.addAll(historyPairs)

        val stream = aiService.sendMessage(
            message = prompt,
            chatHistory = fullHistory
        )
        Pair(stream, aiService)
    }

    // 解析并执行工具调用
    suspend fun processToolInvocations(rawContent: String, assistantIndex: Int) {
        try {
            val invList = LocalCharacterToolExecutor.extractInvocations(rawContent)
            if (invList.isEmpty()) return

            AppLogger.d(TAG, "Found ${invList.size} tool invocation(s).")
            invList.forEach { (name, params) ->
                AppLogger.d(TAG, "Tool invocation: name='$name', params=$params")

                if (name != LocalCharacterToolExecutor.TOOL_NAME) {
                    AppLogger.w(TAG, "Skipping unknown tool: '$name'")
                    return@forEach
                }
                val field = params["field"].orEmpty().trim()
                val content = params["content"].orEmpty().trim()
                val cardId = activeCardId

                if (field.isBlank() || content.isBlank()) {
                    AppLogger.w(TAG, "Skipping tool call with blank field or content.")
                    return@forEach
                }

                val result = LocalCharacterToolExecutor.executeSaveCharacterInfo(context, cardId, field, content)
                if (result.success) {
                    AppLogger.d(TAG, "Tool '$name' executed successfully for field '$field'.")
                } else {
                    AppLogger.e(TAG, "Tool '$name' execution failed for field '$field': ${result.error}")
                }

                // 刷新数据
                withContext(Dispatchers.Main) {
                    refreshData()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Local tool processing failed: ${e.message}", e)
        }
    }

    // 保存聊天历史
    fun saveChatHistory() {
        scope.launch(Dispatchers.IO) {
            val messages = chatMessages.map { msg ->
                PersonaCardChatHistoryManager.ChatMessage(msg.role, msg.content, msg.timestamp)
            }
            chatHistoryManager.saveChatHistory(activeCardId, messages)
        }
    }

    fun sendMessage() {
        if (userInput.isBlank() || isGenerating) return
        
        // 检查对话数量限制
        if (chatMessages.size >= MESSAGE_LIMIT) {
            showMessageLimitWarning = true
            return
        }
        
        val input = userInput
        userInput = ""

        scope.launch(Dispatchers.Main) {
            chatMessages.add(CharacterChatMessage("user", input))
            saveChatHistory() // 保存用户消息
            isGenerating = true

            // 检查是否已完成，如果已完成则直接结束
            if (isCharacterCardComplete()) {
                chatMessages.add(CharacterChatMessage("assistant", context.getString(R.string.character_card_complete)))
                saveChatHistory() // 保存完成消息
                isGenerating = false
                scope.launch { listState.animateScrollToItem(chatMessages.lastIndex) }
                return@launch
            }

            // 构建稳定的上下文
            val systemPrompt = buildSystemPrompt()
            // val characterStatus = buildCharacterStatus() // REMOVED: 不再每次都发送状态
            
            val historyPairs = withContext(Dispatchers.Default) {
                chatMessages.map { it.role to it.content }
            }

            val (stream, aiService) = requestFromDefaultService(input, historyPairs, systemPrompt)

            // 提前插入占位的"生成中…"助手消息
            val generatingText = context.getString(R.string.generating)
            chatMessages.add(CharacterChatMessage("assistant", generatingText))
            val assistantIndex = chatMessages.lastIndex

            val toolTagRegex = Regex("(?s)\\s*<tool\\b[\\s\\S]*?</tool>\\s*")
            val toolResultRegex = Regex("(?s)\\s*<tool_result\\s+name=\"[^\"]+\"\\s+status=\"[^\"]+\"[^>]*>[\\s\\S]*?</tool_result>\\s*")
            val statusRegex = Regex("(?s)\\s*<status\\b[^>]*>[\\s\\S]*?</status>\\s*")

            // 原始缓冲，用于工具解析
            val rawBuffer = StringBuilder()
            var firstChunkReceived = false

            try {
                withContext(Dispatchers.IO) {
                    stream.collect { chunk ->
                        rawBuffer.append(chunk)
                        withContext(Dispatchers.Main) {
                            if (!firstChunkReceived) {
                                firstChunkReceived = true
                                isGenerating = false
                            }
                            val sanitized = (chatMessages[assistantIndex].content.replace(generatingText, "") + chunk)
                                .replace(toolTagRegex, "")
                                .replace(toolResultRegex, "")
                                .replace(statusRegex, "")
                                .replace(Regex("(\\r?\\n){2,}"), "\n")
                            chatMessages[assistantIndex] = chatMessages[assistantIndex].copy(content = sanitized)
                            scope.launch { listState.animateScrollToItem(chatMessages.lastIndex) }
                        }
                    }
                }

                // Update token and request count statistics
                withContext(Dispatchers.IO) {
                    val apiPreferences = ApiPreferences.getInstance(context)
                    apiPreferences.updateTokensForProviderModel(
                        aiService.providerModel,
                        aiService.inputTokenCount,
                        aiService.outputTokenCount,
                        aiService.cachedInputTokenCount
                    )
                    apiPreferences.incrementRequestCountForProviderModel(aiService.providerModel)
                }

                // 流结束后解析并执行工具
                withContext(Dispatchers.IO) {
                    processToolInvocations(rawBuffer.toString(), assistantIndex)
                }
                
                // 保存助手回复
                saveChatHistory()
            } catch (e: Exception) {
                chatMessages.add(
                    CharacterChatMessage(
                        role = "assistant",
                        content = context.getString(R.string.send_failed, e.message ?: "Unknown error")
                    )
                )
                saveChatHistory() // 保存错误消息
            } finally {
                isGenerating = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(windowInsets = WindowInsets(0, 0, 0, 0)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 关闭按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = context.getString(R.string.close)
                            )
                        }
                    }

                    Text(context.getString(R.string.character_card_config), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    // 选择不同角色卡
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = activeCard?.name ?: context.getString(R.string.no_character_card),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(context.getString(R.string.current_character_card)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            allCharacterCards.forEach { card ->
                                DropdownMenuItem(
                                    text = { Text(card.name) },
                                    onClick = {
                                        expanded = false
                                        scope.launch {
                                            characterCardManager.setActiveCharacterCard(card.id)
                                            activeCardId = card.id // 更新ID以触发Effect
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.create_new_character_card)) },
                                onClick = {
                                    expanded = false
                                    showCreateDialog = true
                                }
                            )
                        }
                    }

                    // 删除当前角色卡（默认卡不可删）
                    if (activeCard?.isDefault == false) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(context.getString(R.string.delete_current_character_card))
                            }
                        }
                    }

                    // 新建角色卡弹窗
                    if (showCreateDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateDialog = false },
                            title = { Text(context.getString(R.string.new_character_card)) },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = newCardName,
                                        onValueChange = { newCardName = it },
                                        singleLine = true,
                                        label = { Text(context.getString(R.string.character_card_name)) },
                                        placeholder = { Text(context.getString(R.string.character_card_name_example)) }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val name = newCardName.trim().ifBlank { context.getString(R.string.new_character) }
                                    showCreateDialog = false
                                    newCardName = ""
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val newCard = CharacterCard(
                                                id = "",
                                                name = name,
                                                description = "",
                                                characterSetting = CharacterCardManager.DEFAULT_CHARACTER_SETTING,
                                                otherContent = CharacterCardManager.DEFAULT_CHARACTER_OTHER_CONTENT,
                                                attachedTagIds = emptyList(),
                                                advancedCustomPrompt = "",
                                                isDefault = false
                                            )
                                            val newId = characterCardManager.createCharacterCard(newCard)
                                            characterCardManager.setActiveCharacterCard(newId)
                                        }
                                        refreshData()
                                    }
                                }) { Text(context.getString(R.string.create)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateDialog = false }) { Text(context.getString(R.string.cancel)) }
                            }
                        )
                    }

                    // 删除角色卡确认对话框
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text(context.getString(R.string.delete_character_card)) },
                            text = { Text(context.getString(R.string.confirm_delete_character_card)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteConfirm = false
                                    scope.launch {
                                        activeCard?.let { card ->
                                            withContext(Dispatchers.IO) {
                                                characterCardManager.deleteCharacterCard(card.id)
                                                // 删除后，activeCharacterCardIdFlow 会自动更新为列表中的第一项
                                                // 或者如果没有角色卡，会是空字符串
                                            }
                                            refreshData()
                                        }
                                    }
                                }) { Text(context.getString(R.string.delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text(context.getString(R.string.cancel)) }
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(context.getString(R.string.current_character_card_content), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色名称
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { newValue ->
                            editName = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(name = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色描述
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { newValue ->
                            editDescription = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(description = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 角色设定
                    OutlinedTextField(
                        value = editCharacterSetting,
                        onValueChange = { newValue ->
                            editCharacterSetting = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(characterSetting = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_setting)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 开场白
                    OutlinedTextField(
                        value = editOpeningStatement,
                        onValueChange = { newValue ->
                            editOpeningStatement = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(openingStatement = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.opening_statement)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 其他内容
                    OutlinedTextField(
                        value = editOtherContent,
                        onValueChange = { newValue ->
                            editOtherContent = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(otherContent = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.other_content)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 高级自定义提示词
                    OutlinedTextField(
                        value = editAdvancedCustomPrompt,
                        onValueChange = { newValue ->
                            editAdvancedCustomPrompt = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(advancedCustomPrompt = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.advanced_custom_prompt)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // 备注信息
                    OutlinedTextField(
                        value = editMarks,
                        onValueChange = { newValue ->
                            editMarks = newValue
                            scope.launch {
                                activeCard?.let { card ->
                                    withContext(Dispatchers.IO) {
                                        characterCardManager.updateCharacterCard(card.copy(marks = newValue))
                                    }
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.character_marks)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.persona_card_generation_title), 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showClearHistoryConfirm = true }) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = context.getString(R.string.clear_chat_history)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text(activeCard?.name ?: context.getString(R.string.no_character_card))
                }
            }

            // 聊天列表
            val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            LaunchedEffect(chatMessages.size) {
                if (chatMessages.isNotEmpty()) {
                    listState.animateScrollToItem(chatMessages.lastIndex)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                items(chatMessages) { msg ->
                    val isUser = msg.role == "user"
                    val bubbleContainer = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val bubbleTextColor = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (!isUser) {
                            Card(colors = CardDefaults.cardColors(containerColor = bubbleContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(msg.content, color = bubbleTextColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeFormatter.format(Date(msg.timestamp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                            Card(colors = CardDefaults.cardColors(containerColor = bubbleContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    SelectionContainer {
                                        Text(msg.content, color = bubbleTextColor)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timeFormatter.format(Date(msg.timestamp)),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            // 底部输入栏
            Surface(color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            placeholder = { Text(if (isGenerating) context.getString(R.string.currently_generating) else context.getString(R.string.describe_character_hint)) },
                            enabled = !isGenerating && chatMessages.size < MESSAGE_LIMIT,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent
                            )
                        )
                        // 对话计数器 - 右上角小标签
                        Text(
                            text = "${chatMessages.size}/$MESSAGE_LIMIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (chatMessages.size >= MESSAGE_LIMIT) 
                                MaterialTheme.colorScheme.error 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 12.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { if (!isGenerating) sendMessage() },
                        enabled = !isGenerating && chatMessages.size < MESSAGE_LIMIT
                    ) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Filled.HourglassBottom else Icons.Filled.Send,
                            contentDescription = if (isGenerating) context.getString(R.string.generating) else context.getString(R.string.send)
                        )
                    }
                }
            }
        }
    }
    
    // 对话数量限制警告对话框
    if (showMessageLimitWarning) {
        AlertDialog(
            onDismissRequest = { showMessageLimitWarning = false },
            title = { Text(context.getString(R.string.message_limit_reached_title)) },
            text = { 
                Text(context.getString(R.string.message_limit_reached_message, MESSAGE_LIMIT))
            },
            confirmButton = {
                TextButton(onClick = {
                    showMessageLimitWarning = false
                    showClearHistoryConfirm = true
                }) { Text(context.getString(R.string.go_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showMessageLimitWarning = false }) { Text(context.getString(R.string.cancel)) }
            }
        )
    }
    
    // 清空对话记录确认对话框
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text(context.getString(R.string.clear_chat_history)) },
            text = { Text(context.getString(R.string.confirm_clear_chat_history)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryConfirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            chatHistoryManager.clearChatHistory(activeCardId)
                        }
                        // 清空界面显示的消息
                        chatMessages.clear()
                        // 添加欢迎消息
                        chatMessages.add(CharacterChatMessage("assistant",
                            context.getString(R.string.persona_generation_welcome, 
                            activeCard?.name ?: context.getString(R.string.new_character))
                        ))
                        saveChatHistory()
                    }
                }) { Text(context.getString(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text(context.getString(R.string.cancel)) }
            }
        )
    }
} 