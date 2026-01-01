package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.CircularProgressIndicator
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch

private data class GroupTarget(
    val groupName: String,
    val characterCardName: String?
)

private sealed interface HistoryListItem {
    data class CharacterHeader(val key: String, val name: String) : HistoryListItem
    data class Header(
        val key: String, 
        val name: String, 
        val groupValue: String?,
        val characterCardName: String? = null
    ) : HistoryListItem
    data class Item(val history: ChatHistory) : HistoryListItem
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: (characterCardName: String?) -> Unit,
        onSelectChat: (String) -> Unit,
        onDeleteChat: (String) -> Unit,
        onUpdateChatTitle: (chatId: String, newTitle: String) -> Unit,
        onUpdateChatBinding: (chatId: String, characterCardName: String?) -> Unit,
        onCreateGroup: (groupName: String, characterCardName: String?) -> Unit,
    onUpdateChatOrderAndGroup: (reorderedHistories: List<ChatHistory>, movedItem: ChatHistory, targetGroup: String?) -> Unit,
    onUpdateGroupName: (oldName: String, newName: String, characterCardName: String?) -> Unit,
    onDeleteGroup: (groupName: String, deleteChats: Boolean, characterCardName: String?) -> Unit,
        chatHistories: List<ChatHistory>,
        currentId: String?,
        lazyListState: LazyListState? = null,
        onBack: (() -> Unit)? = null,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        historyDisplayMode: ChatHistoryDisplayMode,
        onDisplayModeChange: (ChatHistoryDisplayMode) -> Unit,
        autoSwitchCharacterCard: Boolean,
        onAutoSwitchCharacterCardChange: (Boolean) -> Unit,
        activeCharacterCard: CharacterCard? = null
) {
    var chatToEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var chatItemActionTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var collapsedGroups by rememberLocal("chat_history_collapsed_groups", emptySet<String>())
    var collapsedCharacters by rememberLocal("chat_history_collapsed_characters", emptySet<String>())

    var groupActionTarget by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToRename by remember { mutableStateOf<GroupTarget?>(null) }
    var groupToDelete by remember { mutableStateOf<GroupTarget?>(null) }
    var hasLongPressedGroup by rememberLocal("has_long_pressed_group", defaultValue = false)
    
    // 搜索相关状态
    var showSearchBox by remember { mutableStateOf(false) }
    var matchedChatIdsByContent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
        }
    }
    val actualLazyListState = lazyListState ?: rememberLazyListState()
    val ungroupedText = stringResource(R.string.ungrouped)

    // 当搜索查询改变时，执行内容搜索（带防抖延迟）
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            // 延迟400ms，如果用户继续输入则取消本次搜索（LaunchedEffect会自动取消）
            delay(400)
            // 延迟后再次检查，确保 searchQuery 仍然有效
            // 注意：如果 searchQuery 在延迟期间改变，LaunchedEffect 会重新启动，这里检查的是当前值
            isSearching = true
            try {
                matchedChatIdsByContent = chatHistoryManager.searchChatIdsByContent(searchQuery)
            } catch (e: Exception) {
                // 如果搜索出错，清空结果
                matchedChatIdsByContent = emptySet()
            } finally {
                isSearching = false
            }
        } else {
            matchedChatIdsByContent = emptySet()
            isSearching = false
        }
    }

    val filteredHistories = remember(chatHistories, searchQuery, matchedChatIdsByContent) {
        if (searchQuery.isNotBlank()) {
            chatHistories.filter { history ->
                val matchesTitleOrGroup = history.title.contains(searchQuery, ignoreCase = true) ||
                        (history.group?.contains(searchQuery, ignoreCase = true) == true)
                val matchesContent = matchedChatIdsByContent.contains(history.id)
                matchesTitleOrGroup || matchesContent
            }
        } else {
            chatHistories
        }
    }

    val unboundCharacterText = stringResource(R.string.unbound_character_card)
    val flatItems =
            remember(
                    filteredHistories,
                    collapsedGroups,
                    collapsedCharacters,
                    ungroupedText,
                    unboundCharacterText,
                    historyDisplayMode,
                    activeCharacterCard
            ) {
                fun characterKey(name: String) = "character::$name"
                fun groupKey(characterName: String?, groupValue: String?): String {
                    val characterPart = characterName ?: "all"
                    val groupPart = groupValue ?: "ungrouped"
                    return "group::$characterPart::$groupPart"
                }

                when (historyDisplayMode) {
                    ChatHistoryDisplayMode.BY_CHARACTER_CARD -> {
                        filteredHistories
                            .groupBy { it.characterCardName ?: unboundCharacterText }
                            .flatMap { (characterName, histories) ->
                                val cKey = characterKey(characterName)
                                val children =
                                        if (collapsedCharacters.contains(cKey)) {
                                            emptyList()
                                        } else {
                                            histories
                                                .groupBy { it.group }
                                                .flatMap { (groupValue, groupedHistories) ->
                                                    val displayName = groupValue ?: ungroupedText
                                                    val gKey = groupKey(characterName, groupValue)
                                                    val header =
                                                            HistoryListItem.Header(
                                                                    key = gKey,
                                                                    name = displayName,
                                                                    groupValue = groupValue,
                                                                    // 未绑定角色卡使用 null 而不是字符串
                                                                    characterCardName = if (characterName == unboundCharacterText) null else characterName
                                                            )
                                                    val items =
                                                            if (collapsedGroups.contains(gKey)) {
                                                                emptyList()
                                                            } else {
                                                                groupedHistories.map {
                                                                    HistoryListItem.Item(it)
                                                                }
                                                            }
                                                    listOf(header) + items
                                                }
                                        }
                                listOf(HistoryListItem.CharacterHeader(cKey, characterName)) + children
                            }
                    }
                    else -> {
                        filteredHistories
                            .groupBy { it.group }
                            .flatMap { (groupValue, histories) ->
                                val displayName = groupValue ?: ungroupedText
                                val gKey = groupKey(null, groupValue)
                                // 在仅显示当前角色卡模式下，使用当前角色卡名称
                                val effectiveCharacterCardName = if (historyDisplayMode == ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) {
                                    activeCharacterCard?.name
                                } else {
                                    null
                                }
                                val header =
                                        HistoryListItem.Header(
                                                key = gKey,
                                                name = displayName,
                                                groupValue = groupValue,
                                                characterCardName = effectiveCharacterCardName
                                        )
                                val items =
                                        if (collapsedGroups.contains(gKey)) {
                                            emptyList()
                                        } else {
                                            histories.map { HistoryListItem.Item(it) }
                                        }
                                listOf(header) + items
                            }
                    }
                }
            }

    val reorderableState = rememberReorderableLazyListState(actualLazyListState) { from, to ->
        val movedItem = flatItems.getOrNull(from.index) as? HistoryListItem.Item
                ?: return@rememberReorderableLazyListState

        val reorderedFlatList = flatItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }

        var newGroup: String? = null
        var newCharacterCardName: String? = null
        val newOrderedHistories =
                reorderedFlatList
                    .mapNotNull {
                        when (it) {
                            is HistoryListItem.CharacterHeader -> {
                                // 在角色卡分类模式下，更新当前的角色卡名称
                                newCharacterCardName = if (it.name == unboundCharacterText) null else it.name
                                newGroup = null
                                null
                            }
                            is HistoryListItem.Header -> {
                                newGroup = it.groupValue
                                null
                            }
                            is HistoryListItem.Item -> {
                                // 根据当前显示模式决定是否更新 characterCardName
                                val updatedHistory = if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                    it.history.copy(
                                        group = newGroup,
                                        characterCardName = newCharacterCardName
                                    )
                                } else {
                                    it.history.copy(group = newGroup)
                                }
                                updatedHistory
                            }
                        }
                    }
                    .mapIndexed { index, history -> history.copy(displayOrder = index.toLong()) }

        val finalMovedItem =
                newOrderedHistories.find { it.id == movedItem.history.id }
                        ?: return@rememberReorderableLazyListState

        // 如果角色卡绑定发生了变化，需要额外通知
        if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD &&
            finalMovedItem.characterCardName != movedItem.history.characterCardName) {
            onUpdateChatBinding(finalMovedItem.id, finalMovedItem.characterCardName)
        }

        onUpdateChatOrderAndGroup(newOrderedHistories, finalMovedItem, finalMovedItem.group)
    }

    if (chatItemActionTarget != null) {
        val resolvedTargetChat = remember(chatItemActionTarget, chatHistories) {
            val target = chatItemActionTarget
            if (target == null) {
                null
            } else {
                chatHistories.firstOrNull { it.id == target.id } ?: target
            }
        }
        Dialog(onDismissRequest = { chatItemActionTarget = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.chat_history),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Text(
                        text = resolvedTargetChat?.title ?: chatItemActionTarget!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 编辑选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.edit_title)
                            }
                            .clickable {
                                chatToEdit = chatItemActionTarget
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.edit_title), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 上移选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.move_up)
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                                if (currentIndex > 0) {
                                    val newHistories = filteredHistories.toMutableList()
                                    newHistories.removeAt(currentIndex)
                                    newHistories.add(currentIndex - 1, targetChat)
                                    val reorderedHistories = newHistories.mapIndexed { index, history ->
                                        history.copy(displayOrder = index.toLong())
                                    }
                                    onUpdateChatOrderAndGroup(reorderedHistories, targetChat, targetChat.group)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.move_up), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 下移选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.move_down)
                            }
                            .clickable {
                                val targetChat = chatItemActionTarget!!
                                val currentIndex = filteredHistories.indexOfFirst { it.id == targetChat.id }
                                if (currentIndex >= 0 && currentIndex < filteredHistories.size - 1) {
                                    val newHistories = filteredHistories.toMutableList()
                                    newHistories.removeAt(currentIndex)
                                    newHistories.add(currentIndex + 1, targetChat)
                                    val reorderedHistories = newHistories.mapIndexed { index, history ->
                                        history.copy(displayOrder = index.toLong())
                                    }
                                    onUpdateChatOrderAndGroup(reorderedHistories, targetChat, targetChat.group)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.move_down), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }

                    // 锁定/解锁选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription =
                                    if (resolvedTargetChat?.locked == true) {
                                        context.getString(R.string.unlock_chat)
                                    } else {
                                        context.getString(R.string.lock_chat)
                                    }
                            }
                            .clickable {
                                val targetChat = resolvedTargetChat ?: chatItemActionTarget!!
                                val newLocked = !targetChat.locked
                                coroutineScope.launch {
                                    chatHistoryManager.updateChatLocked(targetChat.id, newLocked)
                                }
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (resolvedTargetChat?.locked == true) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (resolvedTargetChat?.locked == true) stringResource(R.string.unlock_chat) else stringResource(R.string.lock_chat),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 删除选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.delete)
                            }
                            .clickable {
                                onDeleteChat(chatItemActionTarget!!.id)
                                chatItemActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.delete), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { chatItemActionTarget = null },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (groupActionTarget != null) {
        Dialog(onDismissRequest = { groupActionTarget = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.manage_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Text(
                        text = groupActionTarget!!.groupName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 重命名选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.rename_group)
                            }
                            .clickable {
                                groupToRename = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.rename_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    // 删除选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .semantics {
                                contentDescription = context.getString(R.string.delete_group)
                            }
                            .clickable {
                                groupToDelete = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.delete_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { groupActionTarget = null },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (groupToRename != null) {
        var newGroupNameText by remember(groupToRename) { mutableStateOf(groupToRename!!.groupName) }
        AlertDialog(
            onDismissRequest = { groupToRename = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                OutlinedTextField(
                    value = newGroupNameText,
                    onValueChange = { newGroupNameText = it },
                    label = { Text(stringResource(R.string.new_group_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupNameText.isNotBlank() && newGroupNameText != groupToRename!!.groupName) {
                            onUpdateGroupName(
                                groupToRename!!.groupName, 
                                newGroupNameText,
                                groupToRename!!.characterCardName
                            )
                        }
                        groupToRename = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (groupToDelete != null) {
        Dialog(onDismissRequest = { groupToDelete = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.confirm_delete_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = groupToDelete!!.groupName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.choose_delete_method),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(
                                groupToDelete!!.groupName, 
                                true,
                                groupToDelete!!.characterCardName
                            )
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_and_chats),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.delete_operation_irreversible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
            }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(
                                groupToDelete!!.groupName, 
                                false,
                                groupToDelete!!.characterCardName
                            )
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_only),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.chats_move_to_ungrouped),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { groupToDelete = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (chatToEdit != null) {
        val editingChat = chatToEdit!!
        var newTitle by remember(editingChat) { mutableStateOf(editingChat.title) }
        var selectedCharacterCardName by remember(editingChat) { mutableStateOf(editingChat.characterCardName) }
        var bindingMenuExpanded by remember { mutableStateOf(false) }
        val bindingOptions = remember(availableCharacterCards) {
            listOf<String?>(null) + availableCharacterCards.map { it.name }
        }
        val unboundLabel = stringResource(R.string.unbound_character_card)
        val bindingLabel = stringResource(R.string.bind_character_card)
        val bindingHint = stringResource(R.string.chat_binding_scope_hint)
        val density = LocalDensity.current
        var bindingMenuWidth by remember { mutableStateOf(0.dp) }
        val dropdownClickSource = remember { MutableInteractionSource() }

        AlertDialog(
                onDismissRequest = { chatToEdit = null },
                title = { Text(stringResource(R.string.edit_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text(stringResource(R.string.new_title)) },
                                modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                    value = selectedCharacterCardName ?: unboundLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(bindingLabel) },
                                    trailingIcon = {
                                        Icon(
                                                imageVector = if (bindingMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { coordinates ->
                                                bindingMenuWidth = with(density) { coordinates.size.width.toDp() }
                                            }
                            )
                            Box(
                                    modifier = Modifier
                                            .matchParentSize()
                                            .clickable(
                                                    interactionSource = dropdownClickSource,
                                                    indication = null
                                            ) { bindingMenuExpanded = !bindingMenuExpanded }
                            )
                            DropdownMenu(
                                    expanded = bindingMenuExpanded,
                                    onDismissRequest = { bindingMenuExpanded = false },
                                    modifier = if (bindingMenuWidth > 0.dp) Modifier.width(bindingMenuWidth) else Modifier
                            ) {
                                bindingOptions.forEach { option ->
                                    DropdownMenuItem(
                                            text = { Text(option ?: unboundLabel) },
                                            onClick = {
                                                selectedCharacterCardName = option
                                                bindingMenuExpanded = false
                                            }
                                    )
                                }
                            }
                        }
                        Text(
                                text = bindingHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newTitle != editingChat.title) {
                                    onUpdateChatTitle(editingChat.id, newTitle)
                                }
                                if (selectedCharacterCardName != editingChat.characterCardName) {
                                    onUpdateChatBinding(editingChat.id, selectedCharacterCardName)
                                }
                                chatToEdit = null
                            }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToEdit = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "聊天记录设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "显示模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    listOf(
                        Triple(
                            ChatHistoryDisplayMode.BY_CHARACTER_CARD,
                            stringResource(R.string.history_filter_role_card),
                            stringResource(R.string.history_filter_role_card_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.BY_FOLDER,
                            stringResource(R.string.history_filter_folder),
                            stringResource(R.string.history_filter_folder_desc)
                        ),
                        Triple(
                            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY,
                            stringResource(R.string.history_filter_current_card),
                            stringResource(R.string.history_filter_current_card_desc)
                        )
                    ).forEach { (mode, title, description) ->
                        val selected = historyDisplayMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onDisplayModeChange(mode)
                                },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                onAutoSwitchCharacterCardChange(!autoSwitchCharacterCard)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_auto_switch_character),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.history_auto_switch_character_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoSwitchCharacterCard,
                                onCheckedChange = onAutoSwitchCharacterCardChange,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (showNewGroupDialog) {
        AlertDialog(
                onDismissRequest = { showNewGroupDialog = false },
                title = { Text(stringResource(R.string.new_group)) },
                text = {
                    OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text(stringResource(R.string.group_name)) },
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    // 根据当前显示模式确定新分组的角色卡归属
                                    val characterCardName = when (historyDisplayMode) {
                                        ChatHistoryDisplayMode.BY_CHARACTER_CARD -> activeCharacterCard?.name // 按角色卡分类时绑定到当前角色卡
                                        ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> activeCharacterCard?.name
                                        else -> null // 按文件夹分类时不绑定
                                    }
                                    onCreateGroup(newGroupName, characterCardName)
                                    newGroupName = ""
                                    showNewGroupDialog = false
                                }
                            }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewGroupDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSearchBox = !showSearchBox },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (showSearchBox) Icons.Default.SearchOff else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = if (showSearchBox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 新建对话按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { 
                    // 根据当前显示模式确定新对话的角色卡归属
                    val characterCardName = when (historyDisplayMode) {
                        ChatHistoryDisplayMode.BY_CHARACTER_CARD -> activeCharacterCard?.name
                        ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> activeCharacterCard?.name
                        else -> null
                    }
                    onNewChat(characterCardName)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat))
            }
            IconButton(
                onClick = { showNewGroupDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    contentDescription = stringResource(R.string.new_group),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 搜索框
        if (showSearchBox) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.search_chat_history_hint)) },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank() && !isSearching) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.SearchOff, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        var showSwipeHint by rememberLocal(key = "show_swipe_hint", defaultValue = true)

        if (showSwipeHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showSwipeHint = false },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = actualLazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            items(
                items = flatItems,
                key = {
                    when (it) {
                        is HistoryListItem.CharacterHeader -> it.key
                        is HistoryListItem.Header -> it.key
                        is HistoryListItem.Item -> it.history.id
                    }
                }
            ) { item ->
                when (item) {
                    is HistoryListItem.CharacterHeader -> {
                        val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
                        val characterCard = availableCharacterCards.find { it.name == item.name }
                        val avatarUri by userPreferencesManager.getAiAvatarForCharacterCardFlow(
                            characterCard?.id ?: ""
                        ).collectAsState(initial = null)
                        
                        val isExpanded = !collapsedCharacters.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp)
                                .semantics {
                                    contentDescription = "${item.name}, $stateDescription"
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            collapsedCharacters =
                                                if (collapsedCharacters.contains(item.key)) {
                                                    collapsedCharacters - item.key
                                                } else {
                                                    collapsedCharacters + item.key
                                                }
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 4.dp, bottomStart = 4.dp)
                                    )
                                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (avatarUri != null) Color.Transparent 
                                            else MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarUri != null) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clearAndSetSemantics {},
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clearAndSetSemantics {}
                                        )
                                    }
                                }
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .padding(horizontal = 8.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Icon(
                                imageVector = if (collapsedCharacters.contains(item.key)) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(24.dp)
                                    .clearAndSetSemantics {}
                            )
                        }
                    }
                    is HistoryListItem.Header -> {
                        val isExpanded = !collapsedGroups.contains(item.key)
                        val stateDescription = if (isExpanded) {
                            stringResource(R.string.expanded)
                        } else {
                            stringResource(R.string.collapsed)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .padding(start = 16.dp, end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(40.dp)
                                            .align(Alignment.Center)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .semantics {
                                        contentDescription = "${item.name}, $stateDescription"
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                collapsedGroups = if (collapsedGroups.contains(item.key)) {
                                                    collapsedGroups - item.key
                                                } else {
                                                    collapsedGroups + item.key
                                                }
                                            },
                                            onLongPress = {
                                                if (item.name != ungroupedText) {
                                                    groupActionTarget = GroupTarget(
                                                        groupName = item.name,
                                                        characterCardName = item.characterCardName
                                                    )
                                                    hasLongPressedGroup = true
                                                }
                                            }
                                        )
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 2.dp,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clearAndSetSemantics {}
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.clearAndSetSemantics {}
                                            )
                                            if (item.name != ungroupedText && !hasLongPressedGroup) {
                                                Text(
                                                    text = " (" + stringResource(R.string.long_press_manage) + ")",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.clearAndSetSemantics {}
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = if (collapsedGroups.contains(item.key)) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        modifier = Modifier.clearAndSetSemantics {}
                                    )
                                }
                            }
                        }
                    }
                    is HistoryListItem.Item -> {
                        val deleteAction = SwipeAction(
                            onSwipe = { onDeleteChat(item.history.id) },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.error
                        )

                        val editAction = SwipeAction(
                            onSwipe = { chatToEdit = item.history },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_title),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.primary
                        )

                        ReorderableItem(reorderableState, key = item.history.id) { isDragging ->
                            val isSelected = item.history.id == currentId
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (historyDisplayMode == ChatHistoryDisplayMode.BY_CHARACTER_CARD) {
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .padding(start = 16.dp, end = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(50.dp)
                                                .align(Alignment.Center)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    shape = RoundedCornerShape(1.dp)
                                                )
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    SwipeableActionsBox(
                                        startActions = listOf(editAction),
                                        endActions = listOf(deleteAction),
                                        swipeThreshold = 100.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            color = containerColor,
                                            shape = MaterialTheme.shapes.medium,
                                            shadowElevation = if (isDragging) 8.dp else 0.dp
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                            ) {
                                                val titlePreview = item.history.title.take(20)
                                                val groupName = item.history.group ?: ungroupedText
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp)
                                                        .semantics(mergeDescendants = false) {
                                                            contentDescription = "$titlePreview, $groupName"
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onTap = { onSelectChat(item.history.id) },
                                                                onLongPress = { chatItemActionTarget = item.history }
                                                            )
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val dragDescription = stringResource(R.string.drag_item, item.history.title)
                                                    IconButton(
                                                        modifier = Modifier
                                                            .draggableHandle()
                                                            .semantics {
                                                                contentDescription = dragDescription
                                                            },
                                                        onClick = {}
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DragHandle,
                                                            contentDescription = null,
                                                            tint = contentColor
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = item.history.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = contentColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .semantics { contentDescription = "" }
                                                    )
                                                    if (item.history.locked) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = null,
                                                            tint = contentColor.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    // 如果是分支，在右侧显示分支图标和父对话标题
                                                    if (item.history.parentChatId != null) {
                                                        val parentChat = chatHistories.find { it.id == item.history.parentChatId }
                                                        if (parentChat != null) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.AccountTree,
                                                                contentDescription = null,
                                                                tint = contentColor.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = parentChat.title,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = contentColor.copy(alpha = 0.6f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier
                                                                    .widthIn(max = 120.dp)
                                                                    .semantics { contentDescription = "" } // 限制最大宽度以便省略
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

