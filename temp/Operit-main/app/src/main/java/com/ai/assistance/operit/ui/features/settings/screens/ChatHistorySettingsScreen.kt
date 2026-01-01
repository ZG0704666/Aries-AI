package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardAssignDialog
import java.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 无绑定工作区信息
 */
data class UnboundWorkspaceInfo(
    val name: String,
    val fullPath: String,
    val location: String // "内部存储" 或 "外部存储"
)

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    val characterCardStatsState by chatHistoryManager.characterCardStatsFlow
        .collectAsState(initial = null as List<CharacterCardChatStats>?)
    val characterCardStats = characterCardStatsState ?: emptyList()
    val isCharacterCardStatsLoading = characterCardStatsState == null

    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var characterCardsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
            if (characterCardsLoading) {
                characterCardsLoading = false
            }
        }
    }

    var chatHistories by remember { mutableStateOf<List<ChatHistory>>(emptyList()) }
    var totalChatCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { histories ->
            chatHistories = histories
            totalChatCount = histories.size
        }
    }
    
    // 获取无绑定的工作区文件夹
    var unboundWorkspaces by remember { mutableStateOf<List<UnboundWorkspaceInfo>>(emptyList()) }
    LaunchedEffect(chatHistories) {
        scope.launch {
            try {
                val result = mutableListOf<UnboundWorkspaceInfo>()
                
                // 获取已绑定的工作区路径集合
                val boundWorkspacePaths = chatHistories
                    .mapNotNull { it.workspace }
                    .toSet()
                
                // 1. 检查内部存储工作区 (/data/data/files/workspace)
                val internalWorkspaceDir = File(context.filesDir, "workspace")
                if (internalWorkspaceDir.exists() && internalWorkspaceDir.isDirectory) {
                    internalWorkspaceDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                        val fullPath = dir.absolutePath
                        if (fullPath !in boundWorkspacePaths) {
                            result.add(
                                UnboundWorkspaceInfo(
                                    name = dir.name,
                                    fullPath = fullPath,
                                    location = "内部存储"
                                )
                            )
                        }
                    }
                }
                
                // 2. 检查外部存储工作区（旧位置）
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val externalWorkspaceDir = File(downloadDir, "Operit/workspace")
                if (externalWorkspaceDir.exists() && externalWorkspaceDir.isDirectory) {
                    externalWorkspaceDir.listFiles { file -> file.isDirectory }?.forEach { dir ->
                        val fullPath = dir.absolutePath
                        if (fullPath !in boundWorkspacePaths) {
                            result.add(
                                UnboundWorkspaceInfo(
                                    name = dir.name,
                                    fullPath = fullPath,
                                    location = "外部存储"
                                )
                            )
                        }
                    }
                }
                
                unboundWorkspaces = result
            } catch (e: Exception) {
                AppLogger.e("ChatHistorySettings", "获取无绑定工作区失败", e)
                unboundWorkspaces = emptyList()
            }
        }
    }

    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    
    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }
    
    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name ?: context.getString(R.string.default_profile_name)

    var showAssignCharacterDialog by remember { mutableStateOf(false) }
    var pendingAssignStat by remember { mutableStateOf<CharacterCardChatStats?>(null) }
    var selectedCharacterCardId by remember { mutableStateOf<String?>(null) }
    var assignInProgress by remember { mutableStateOf(false) }

    val isScreenLoading = isCharacterCardStatsLoading || characterCardsLoading

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ChatManagementOverviewCard(
                    totalChatCount = totalChatCount,
                    activeProfileName = activeProfileName
                )
            }
            item {
                CharacterCardStatsCard(
                    stats = characterCardStats,
                    characterCards = availableCharacterCards,
                    isLoading = isCharacterCardStatsLoading || characterCardsLoading,
                    onAssignMissing = { stat ->
                        if (availableCharacterCards.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.no_available_character_cards_toast), Toast.LENGTH_SHORT).show()
                            return@CharacterCardStatsCard
                        }
                        pendingAssignStat = stat
                        selectedCharacterCardId = availableCharacterCards.firstOrNull()?.id
                        showAssignCharacterDialog = true
                    }
                )
            }
            item {
                ChatHistoryBatchSelectorCard(
                    chatHistories = chatHistories,
                    characterCards = availableCharacterCards,
                    onApply = { selectedIds, targetCharacterName, targetGroupName, shouldUnbindCharacterCard ->
                        if (selectedIds.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.please_select_chats_first), Toast.LENGTH_SHORT).show()
                            return@ChatHistoryBatchSelectorCard false
                        }
                        try {
                            var messageParts = mutableListOf<String>()
                            
                            // 更新角色卡绑定（仅在明确指定时更新）
                            // shouldUnbindCharacterCard 为 true 表示移除绑定
                            // targetCharacterName 不为 null 表示设置新的角色卡
                            // 如果两者都为 false/null，且提供了分组，则只更新分组，不更新角色卡
                            val shouldUpdateCharacterCard = shouldUnbindCharacterCard || targetCharacterName != null
                            
                            if (shouldUpdateCharacterCard) {
                                chatHistoryManager.assignCharacterCardToChats(
                                    chatIds = selectedIds,
                                    targetCharacterCardName = targetCharacterName
                                )
                                messageParts.add(
                                    if (targetCharacterName.isNullOrBlank()) {
                                        context.getString(R.string.removed_character_card_binding, selectedIds.size)
                                    } else {
                                        context.getString(R.string.assigned_chats_to_character_card, selectedIds.size, targetCharacterName)
                                    }
                                )
                            }
                            
                            // 更新分组
                            if (targetGroupName != null) {
                                chatHistoryManager.assignGroupToChats(
                                    chatIds = selectedIds,
                                    groupName = targetGroupName
                                )
                                messageParts.add(context.getString(R.string.assigned_chats_to_group, selectedIds.size, targetGroupName))
                            }
                            
                            val message = messageParts.joinToString("；")
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            true
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.batch_update_failed, e.localizedMessage ?: e.toString()),
                                Toast.LENGTH_LONG
                            ).show()
                            false
                        }
                    }
                )
            }
            
            // 无绑定工作区管理卡片
            item {
                UnboundWorkspaceCard(
                    unboundWorkspaces = unboundWorkspaces,
                    onDelete = { selectedWorkspacePaths ->
                        scope.launch {
                            try {
                                var deletedCount = 0
                                selectedWorkspacePaths.forEach { workspacePath ->
                                    val workspaceDir = File(workspacePath)
                                    if (workspaceDir.exists() && workspaceDir.deleteRecursively()) {
                                        deletedCount++
                                    }
                                }
                                
                                Toast.makeText(context, context.getString(R.string.deleted_unbound_workspaces, deletedCount), Toast.LENGTH_SHORT).show()
                                
                                // 刷新列表
                                unboundWorkspaces = unboundWorkspaces.filter { it.fullPath !in selectedWorkspacePaths }
                            } catch (e: Exception) {
                                AppLogger.e("ChatHistorySettings", "删除工作区失败", e)
                                Toast.makeText(context, context.getString(R.string.delete_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isScreenLoading,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
        modifier = Modifier
            .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.loading_data_please_wait),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showAssignCharacterDialog && pendingAssignStat != null) {
        CharacterCardAssignDialog(
            missingChatCount = pendingAssignStat?.chatCount ?: 0,
            characterCards = availableCharacterCards,
            selectedCardId = selectedCharacterCardId,
            onCardSelected = { selectedCharacterCardId = it },
            onDismiss = {
                if (!assignInProgress) {
                    showAssignCharacterDialog = false
                    pendingAssignStat = null
                    selectedCharacterCardId = null
                }
            },
            onConfirm = {
                val stat = pendingAssignStat
                val targetCard = availableCharacterCards.firstOrNull { it.id == selectedCharacterCardId }

                if (assignInProgress) {
                    return@CharacterCardAssignDialog
                }

                if (stat == null) {
                    Toast.makeText(context, context.getString(R.string.no_chat_stats_to_assign), Toast.LENGTH_SHORT).show()
                    showAssignCharacterDialog = false
                    return@CharacterCardAssignDialog
                }

                if (targetCard == null) {
                    Toast.makeText(context, context.getString(R.string.please_select_a_character_card), Toast.LENGTH_SHORT).show()
                    return@CharacterCardAssignDialog
                }

                assignInProgress = true
                scope.launch {
                    try {
                        chatHistoryManager.reassignChatsToCharacterCard(
                            sourceCharacterCardName = stat.characterCardName,
                            targetCharacterCardName = targetCard.name
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.assigned_to_character_card, targetCard.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        showAssignCharacterDialog = false
                        pendingAssignStat = null
                        selectedCharacterCardId = null
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.assign_failed_error, e.localizedMessage ?: e.toString()),
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        assignInProgress = false
                    }
                }
            },
            inProgress = assignInProgress
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatManagementOverviewCard(
    totalChatCount: Int,
    activeProfileName: String
) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                    text = context.getString(R.string.chat_history_overview),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = context.getString(R.string.current_config_label, activeProfileName),
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatChip(
                icon = Icons.Default.History,
                title = "$totalChatCount",
                subtitle = context.getString(R.string.chat_records_label)
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CharacterCardStatsCard(
    stats: List<CharacterCardChatStats>,
    characterCards: List<CharacterCard>,
    isLoading: Boolean,
    onAssignMissing: (CharacterCardChatStats) -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = context.getString(R.string.character_card_statistics),
                subtitle = context.getString(R.string.character_card_statistics_subtitle),
                icon = Icons.Default.AssignmentInd
            )

            if (isLoading) {
                Column(
        modifier = Modifier
            .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = context.getString(R.string.counting_chat_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (stats.isEmpty()) {
            Text(
                    text = context.getString(R.string.no_chat_data_available),
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedStats = remember(stats) {
                    stats.sortedWith(
                        compareByDescending<CharacterCardChatStats> { it.characterCardName.isNullOrBlank() }
                            .thenBy { it.characterCardName ?: "" }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedStats.forEach { stat ->
                        key(stat.characterCardName ?: "missing-${stat.hashCode()}") {
                            val matchedCard = characterCards.firstOrNull { card ->
                                card.name == stat.characterCardName
                            }
                            CharacterCardStatRow(
                                stat = stat,
                                characterCard = matchedCard,
                                userPreferencesManager = userPreferencesManager,
                                onAssignMissing = if (matchedCard == null) {
                                    { onAssignMissing(stat) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardStatRow(
    stat: CharacterCardChatStats,
    characterCard: CharacterCard?,
    userPreferencesManager: UserPreferencesManager,
    onAssignMissing: (() -> Unit)?
) {
    val context = LocalContext.current
    val isMissing = stat.characterCardName.isNullOrBlank()
    val needsAttention = characterCard == null
    val iconBackground = if (needsAttention) {
        MaterialTheme.colorScheme.errorContainer
                        } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .let {
            if (needsAttention && onAssignMissing != null) {
                it.clickable { onAssignMissing() }
                } else {
                it
            }
        }
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (characterCard != null) {
            val avatarUri by userPreferencesManager
                .getAiAvatarForCharacterCardFlow(characterCard.id)
                .collectAsState(initial = null)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!avatarUri.isNullOrBlank()) Color.Transparent
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = context.getString(R.string.character_card_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
            Text(
                        text = characterCard.name.firstOrNull()?.toString() ?: context.getString(R.string.character_fallback),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBackground.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
                    Text(
                text = stat.characterCardName ?: context.getString(R.string.unbound_character_card),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
                    )
                    Text(
                text = context.getString(R.string.chats_and_messages_count, stat.chatCount, stat.messageCount),
                style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (needsAttention && onAssignMissing != null) {
                Text(
                    text = context.getString(R.string.click_to_assign_to_card),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (needsAttention && onAssignMissing != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistoryBatchSelectorCard(
    chatHistories: List<ChatHistory>,
    characterCards: List<CharacterCard>,
    onApply: suspend (selectedChatIds: List<String>, targetCharacterCardName: String?, targetGroupName: String?, shouldUnbindCharacterCard: Boolean) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedTargetName by remember { mutableStateOf<String?>(null) }
    var targetIsUnbind by remember { mutableStateOf(false) }
    var targetGroupName by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val normalizedQuery = searchQuery.trim()
    val filteredHistories = remember(chatHistories, normalizedQuery) {
        val base = if (normalizedQuery.isBlank()) {
            chatHistories
        } else {
            chatHistories.filter { history ->
                history.title.contains(normalizedQuery, ignoreCase = true) ||
                        (history.group?.contains(normalizedQuery, ignoreCase = true) == true) ||
                        (history.characterCardName?.contains(normalizedQuery, ignoreCase = true) == true)
            }
        }
        // 先按是否有角色卡、分组分区，再按角色卡名称和分组名称排序，方便成批管理
        base.sortedWith(
            compareBy<ChatHistory> {
                // 无角色卡的排在后面
                it.characterCardName.isNullOrBlank()
            }.thenBy {
                // 再按角色卡名称排序
                it.characterCardName ?: ""
            }.thenBy {
                // 然后按分组名称排序（空分组排在后面）
                it.group.isNullOrBlank()
            }.thenBy {
                it.group ?: ""
            }.thenByDescending {
                // 同一角色卡+分组内按最近更新时间倒序
                it.updatedAt
            }
        )
    }

    LaunchedEffect(chatHistories) {
        val availableIds = chatHistories.map { it.id }.toSet()
        selectedChatIds = selectedChatIds.filter { it in availableIds }.toSet()
    }

    LaunchedEffect(characterCards) {
        if (selectedTargetName != null && characterCards.none { it.name == selectedTargetName }) {
            selectedTargetName = null
        }
    }

    val hasSelection = selectedChatIds.isNotEmpty()
    val hasTargetSelection = targetIsUnbind || !selectedTargetName.isNullOrBlank()
    val hasTargetGroup = targetGroupName.isNotBlank()
    val canSubmit = hasSelection && (hasTargetSelection || hasTargetGroup) && !submitting

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = stringResource(R.string.batch_assign_title),
                subtitle = stringResource(R.string.batch_assign_subtitle),
                icon = Icons.AutoMirrored.Filled.PlaylistAddCheck
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(context.getString(R.string.search_by_title_group_card)) },
                label = { Text(context.getString(R.string.filter_chat_history)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (chatHistories.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_chat_records_for_batch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@ElevatedCard
            }

            if (filteredHistories.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_matching_chats_adjust_filter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.selected_chats_count, selectedChatIds.size, filteredHistories.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val ids = filteredHistories.map { it.id }
                                selectedChatIds = selectedChatIds.toMutableSet().apply { addAll(ids) }
                            },
                            enabled = filteredHistories.isNotEmpty()
                        ) {
                            Text(context.getString(R.string.select_all_current_list))
                        }
                        TextButton(
                            onClick = { selectedChatIds = emptySet() },
                            enabled = selectedChatIds.isNotEmpty()
                        ) {
                            Text(context.getString(R.string.clear_selection))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    itemsIndexed(filteredHistories, key = { _, history -> history.id }) { index, history ->
                        ChatHistorySelectableRow(
                            history = history,
                            selected = selectedChatIds.contains(history.id),
                            onSelectionChange = { selected ->
                                selectedChatIds = if (selected) {
                                    selectedChatIds + history.id
                                } else {
                                    selectedChatIds - history.id
                                }
                            }
                        )
                        if (index < filteredHistories.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    val targetLabel = when {
                        targetIsUnbind -> context.getString(R.string.remove_character_card_binding)
                        !selectedTargetName.isNullOrBlank() -> selectedTargetName!!
                        else -> ""
                    }
                    OutlinedTextField(
                        value = targetLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(context.getString(R.string.target_character_card_optional)) },
                        placeholder = { Text(context.getString(R.string.select_card_or_unbind)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.remove_character_card_binding)) },
                            onClick = {
                                targetIsUnbind = true
                                selectedTargetName = null
                                dropdownExpanded = false
                            }
                        )
                        if (characterCards.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(context.getString(R.string.no_available_character_cards_dropdown), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            characterCards.forEach { card ->
                                DropdownMenuItem(
                                    text = { Text(card.name) },
                                    onClick = {
                                        selectedTargetName = card.name
                                        targetIsUnbind = false
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = targetGroupName,
                    onValueChange = { targetGroupName = it },
                    singleLine = true,
                    maxLines = 1,
                    label = { Text(stringResource(R.string.target_group_optional)) },
                    placeholder = { Text(stringResource(R.string.enter_group_name_hint)) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.batch_assign_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (!canSubmit) return@Button
                            scope.launch {
                                submitting = true
                                val groupName = targetGroupName.takeIf { it.isNotBlank() }
                                val success = onApply(
                                    selectedChatIds.toList(),
                                    if (targetIsUnbind) null else selectedTargetName,
                                    groupName,
                                    targetIsUnbind
                                )
                                if (success) {
                                    selectedChatIds = emptySet()
                                    targetGroupName = ""
                                }
                                submitting = false
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val buttonText = when {
                            targetIsUnbind && targetGroupName.isNotBlank() -> context.getString(R.string.apply_changes)
                            targetIsUnbind -> context.getString(R.string.remove_character_card_binding)
                            targetGroupName.isNotBlank() && selectedTargetName.isNullOrBlank() -> context.getString(R.string.apply_group)
                            else -> context.getString(R.string.apply_character_card)
                        }
                        Text(buttonText)
                    }
                    TextButton(
                        onClick = {
                            selectedChatIds = emptySet()
                        },
                        enabled = selectedChatIds.isNotEmpty(),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(context.getString(R.string.cancel_selection))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistorySelectableRow(
    history: ChatHistory,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!selected) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onSelectionChange(it) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = history.title.ifBlank { context.getString(R.string.unnamed_conversation) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                history.group?.let { group ->
                    append(context.getString(R.string.group_label, group))
                    append(" · ")
                }
                val cardInfo = if (history.characterCardName.isNullOrBlank()) {
                    context.getString(R.string.unbound_character_card)
                } else {
                    context.getString(R.string.character_card_label, history.characterCardName)
                }
                append(cardInfo)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Icon(
            imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                style = MaterialTheme.typography.titleMedium
                )
                Text(
                text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

/**
 * 无绑定工作区管理卡片
 */
@Composable
private fun UnboundWorkspaceCard(
    unboundWorkspaces: List<UnboundWorkspaceInfo>,
    onDelete: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selectedWorkspaces by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = context.getString(R.string.unbound_workspaces_title),
                subtitle = context.getString(R.string.unbound_workspaces_subtitle),
                icon = Icons.Default.FolderOff
            )
            
            if (unboundWorkspaces.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_unbound_workspaces),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 选择控制栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.selected_workspaces_count, selectedWorkspaces.size, unboundWorkspaces.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { selectedWorkspaces = unboundWorkspaces.map { it.fullPath }.toSet() },
                            enabled = unboundWorkspaces.isNotEmpty()
                        ) {
                            Text(context.getString(R.string.select_all_current_list))
                        }
                        TextButton(
                            onClick = { selectedWorkspaces = emptySet() },
                            enabled = selectedWorkspaces.isNotEmpty()
                        ) {
                            Text(context.getString(R.string.clear_all))
                        }
                    }
                }
                
                // 工作区列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    itemsIndexed(unboundWorkspaces, key = { _, workspace -> workspace.fullPath }) { index, workspace ->
                        UnboundWorkspaceRow(
                            workspaceInfo = workspace,
                            selected = selectedWorkspaces.contains(workspace.fullPath),
                            onSelectionChange = { selected ->
                                selectedWorkspaces = if (selected) {
                                    selectedWorkspaces + workspace.fullPath
                                } else {
                                    selectedWorkspaces - workspace.fullPath
                                }
                            }
                        )
                        if (index < unboundWorkspaces.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                        }
                    }
                }
                
                // 删除按钮
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    enabled = selectedWorkspaces.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.delete_selected_workspaces, selectedWorkspaces.size))
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(context.getString(R.string.confirm_delete)) },
            text = { 
                Text(context.getString(R.string.delete_workspaces_confirmation, selectedWorkspaces.size)) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(selectedWorkspaces)
                        selectedWorkspaces = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(context.getString(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 无绑定工作区行项目
 */
@Composable
private fun UnboundWorkspaceRow(
    workspaceInfo: UnboundWorkspaceInfo,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChange(!selected) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onSelectionChange(it) }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspaceInfo.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = workspaceInfo.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = context.getString(R.string.not_used_by_any_chat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = if (workspaceInfo.location == "内部存储") Icons.Default.Folder else Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

