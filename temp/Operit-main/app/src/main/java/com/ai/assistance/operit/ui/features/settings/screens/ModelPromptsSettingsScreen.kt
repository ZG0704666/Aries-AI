package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.MoreVert
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ModelPromptsSettingsScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToMarket: () -> Unit = {},
    onNavigateToPersonaGeneration: () -> Unit = {},
    onNavigateToChatManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTagSavedHighlight by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var showDuplicateSuccessMessage by remember { mutableStateOf(false) }

    // 管理器
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val promptTagManager = remember { PromptTagManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }

    // 获取当前活跃角色卡ID
    val activeCharacterCardId by characterCardManager.activeCharacterCardIdFlow.collectAsState(initial = "")

    // 状态
    var currentTab by remember { mutableStateOf(0) } // 0: 角色卡, 1: 标签
    var refreshTrigger by remember { mutableStateOf(0) }

    // 角色卡相关状态
    val characterCardList by characterCardManager.characterCardListFlow.collectAsState(initial = emptyList())
    var showAddCharacterCardDialog by remember { mutableStateOf(false) }
    var showEditCharacterCardDialog by remember { mutableStateOf(false) }
    var editingCharacterCard by remember { mutableStateOf<CharacterCard?>(null) }
    var editingOriginalName by remember { mutableStateOf<String?>(null) }

    // 删除确认对话框状态
    var showDeleteCharacterCardConfirm by remember { mutableStateOf(false) }
    var deletingCharacterCardId by remember { mutableStateOf("") }
    var deletingCharacterCardName by remember { mutableStateOf("") }

    // 重置确认对话框状态
    var showResetDefaultConfirm by remember { mutableStateOf(false) }

    // 酒馆角色卡导入相关状态
    var showImportSuccessMessage by remember { mutableStateOf(false) }
    var showImportErrorMessage by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    var showChatManagementPrompt by remember { mutableStateOf(false) }

    // Avatar picker and cropper launcher
    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    editingCharacterCard?.let { card ->
                        val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, "avatar_${card.id}")
                        if (internalUri != null) {
                            userPreferencesManager.saveAiAvatarForCharacterCard(card.id, internalUri.toString())
                            Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = "裁剪头像"
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchAvatarCrop(uri)
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(fileUri)
                    val fileName = fileUri.lastPathSegment ?: ""

                    val result = when {
                        mimeType == "image/png" || fileName.lowercase().endsWith(".png") -> {
                            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                requireNotNull(inputStream) { "无法读取文件" }
                                characterCardManager.createCharacterCardFromTavernPng(inputStream)
                            }
                        }
                        mimeType == "application/json" || fileName.lowercase().endsWith(".json") -> {
                            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                requireNotNull(inputStream) { "无法读取文件" }
                                val jsonContent = inputStream.bufferedReader().readText()
                                characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                            }
                        }
                        else -> {
                            // Fallback for unknown file types
                            try {
                                // Try JSON first
                                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                    requireNotNull(inputStream) { "无法读取文件" }
                                    val jsonContent = inputStream.bufferedReader().readText()
                                    characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                                }
                            } catch (e: Exception) {
                                // If JSON fails, try PNG
                                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                    requireNotNull(inputStream) { "无法读取文件" }
                                    characterCardManager.createCharacterCardFromTavernPng(inputStream)
                                }
                            }
                        }
                    }

                    result.onSuccess {
                        showImportSuccessMessage = true
                        refreshTrigger++
                    }.onFailure { exception ->
                        importErrorMessage = exception.message ?: context.getString(R.string.unknown_error)
                        showImportErrorMessage = true
                    }
                } catch (e: Exception) {
                    importErrorMessage = context.getString(R.string.file_read_error, e.message ?: "")
                    showImportErrorMessage = true
                }
            }
        }
    }

    // 标签相关状态
    val allTags by promptTagManager.allTagsFlow.collectAsState(initial = emptyList())
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showEditTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<PromptTag?>(null) }

    // 标签删除确认对话框状态
    var showDeleteTagConfirm by remember { mutableStateOf(false) }
    var deletingTagId by remember { mutableStateOf("") }
    var deletingTagName by remember { mutableStateOf("") }

    // 初始化
    LaunchedEffect(Unit) {
        characterCardManager.initializeIfNeeded()
    }

    // 获取所有角色卡
    var allCharacterCards by remember { mutableStateOf(emptyList<CharacterCard>()) }
    LaunchedEffect(characterCardList, refreshTrigger) {
        scope.launch {
            val cards = characterCardManager.getAllCharacterCards()
            allCharacterCards = cards
        }
    }

    // 保存角色卡
    fun saveCharacterCard() {
        editingCharacterCard?.let { card ->
            val originalNameSnapshot = editingOriginalName
            val isExistingCard = card.id.isNotEmpty()
            val nameChanged = isExistingCard &&
                    !originalNameSnapshot.isNullOrEmpty() &&
                    originalNameSnapshot != card.name

            scope.launch {
                if (!isExistingCard) {
                    val newCardId = characterCardManager.createCharacterCard(card)
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(newCardId, card.name.ifEmpty { null })
                } else {
                    characterCardManager.updateCharacterCard(card)
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(card.id, card.name.ifEmpty { null })
                }
                showAddCharacterCardDialog = false
                showEditCharacterCardDialog = false
                editingCharacterCard = null
                editingOriginalName = null
                showSaveSuccessMessage = true
                refreshTrigger++
                if (nameChanged) {
                    showChatManagementPrompt = true
                }
            }
        }
    }

    // 保存标签
    fun saveTag() {
        editingTag?.let { tag ->
            scope.launch {
                if (tag.id.isEmpty()) {
                    // 新建
                    promptTagManager.createPromptTag(
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent,
                        tagType = tag.tagType
                    )
                    showAddTagDialog = false
                    editingTag = null
                    showTagSavedHighlight = true
                } else {
                    // 更新
                    promptTagManager.updatePromptTag(
                        id = tag.id,
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent,
                        tagType = tag.tagType
                    )
                    showSaveSuccessMessage = true
                }
                showEditTagDialog = false
            }
        }
    }

    // 删除角色卡
    fun deleteCharacterCard(id: String) {
        scope.launch {
            characterCardManager.deleteCharacterCard(id)
            refreshTrigger++
        }
    }

    // 显示删除角色卡确认对话框
    fun showDeleteCharacterCardConfirm(id: String, name: String) {
        deletingCharacterCardId = id
        deletingCharacterCardName = name
        showDeleteCharacterCardConfirm = true
    }

    // 确认删除角色卡
    fun confirmDeleteCharacterCard() {
        scope.launch {
            characterCardManager.deleteCharacterCard(deletingCharacterCardId)
            showDeleteCharacterCardConfirm = false
            deletingCharacterCardId = ""
            deletingCharacterCardName = ""
            refreshTrigger++
            showChatManagementPrompt = true
        }
    }

    // 确认重置默认角色卡
    fun confirmResetDefaultCharacterCard() {
        scope.launch {
            characterCardManager.resetDefaultCharacterCard()
            showResetDefaultConfirm = false
            refreshTrigger++
            Toast.makeText(context, context.getString(R.string.reset_successful), Toast.LENGTH_SHORT).show()
        }
    }

    // 复制角色卡
    fun duplicateCharacterCard(card: CharacterCard) {
        scope.launch {
            val duplicatedCard = card.copy(
                id = "", // 将由createCharacterCard生成新ID
                name = "${card.name} (副本)",
                isDefault = false
            )
            val newCardId = characterCardManager.createCharacterCard(duplicatedCard)
            characterCardManager.cloneBindingsFromCharacterCard(card.id, newCardId)
            userPreferencesManager.saveCustomChatTitleForCharacterCard(newCardId, duplicatedCard.name.ifEmpty { null })
            showDuplicateSuccessMessage = true
            refreshTrigger++
        }
    }

    // 删除标签
    fun deleteTag(id: String) {
        scope.launch {
            promptTagManager.deletePromptTag(id)
            }
        }

    // 显示删除标签确认对话框
    fun showDeleteTagConfirm(id: String, name: String) {
        deletingTagId = id
        deletingTagName = name
        showDeleteTagConfirm = true
    }

    // 确认删除标签
    fun confirmDeleteTag() {
        scope.launch {
            promptTagManager.deletePromptTag(deletingTagId)
            showDeleteTagConfirm = false
            deletingTagId = ""
            deletingTagName = ""
        }
    }

    CustomScaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标签栏（移除旧配置选项）
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.character_cards), fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.tags), fontSize = 12.sp)
                        }
                    }
                    // 旧配置选项已废弃，删除对应标签
                }

                // 内容区域（仅保留角色卡和标签两页）
                when (currentTab) {
                    0 -> CharacterCardTab(
                        characterCards = allCharacterCards,
                        activeCharacterCardId = activeCharacterCardId,
                        allTags = allTags,
                        onAddCharacterCard = {
                            editingOriginalName = null
                            editingCharacterCard = CharacterCard(
                                id = "",
                                name = "",
                                description = "",
                                characterSetting = CharacterCardManager.DEFAULT_CHARACTER_SETTING,
                                otherContent = CharacterCardManager.DEFAULT_CHARACTER_OTHER_CONTENT,
                                attachedTagIds = emptyList(),
                                advancedCustomPrompt = "",
                                marks = ""
                            )
                            showAddCharacterCardDialog = true
                        },
                        onEditCharacterCard = { card ->
                            editingOriginalName = card.name
                            editingCharacterCard = card.copy()
                            showEditCharacterCardDialog = true
                        },
                        onDeleteCharacterCard = { card -> showDeleteCharacterCardConfirm(card.id, card.name) },
                        onDuplicateCharacterCard = { card -> duplicateCharacterCard(card) },
                        onResetDefaultCharacterCard = { showResetDefaultConfirm = true },
                        onSetActiveCharacterCard = { cardId ->
                            scope.launch {
                                characterCardManager.setActiveCharacterCard(cardId)
                                refreshTrigger++
                            }
                        },
                        onNavigateToPersonaGeneration = onNavigateToPersonaGeneration,
                        onImportTavernCard = {
                            filePickerLauncher.launch("*/*")
                        }
                    )
                    1 -> TagTab(
                        tags = allTags,
                        onAddTag = {
                            editingTag = PromptTag(
                                id = "",
                                name = "",
                                description = "",
                                promptContent = "",
                                tagType = TagType.CUSTOM
                            )
                            showAddTagDialog = true
                        },
                        onEditTag = { tag ->
                            editingTag = tag.copy()
                            showEditTagDialog = true
                        },
                        onDeleteTag = { tag -> showDeleteTagConfirm(tag.id, tag.name) },
                        onNavigateToMarket = onNavigateToMarket
                    )
                }
            }

            // 成功保存消息
            if (showSaveSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    showSaveSuccessMessage = false
                }

                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                            imageVector = Icons.Default.Check,
                                        contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                                    )
                        Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                            text = stringResource(R.string.save_successful),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                                    )
                                }
                            }
                        }

            // 创建副本成功消息
            if (showDuplicateSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    showDuplicateSuccessMessage = false
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.duplicate_successful),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 导入成功消息
            if (showImportSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showImportSuccessMessage = false
                }

                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tavern_card_import_success),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 导入失败消息
            if (showImportErrorMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showImportErrorMessage = false
                }

                Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                            ) {
                                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                                    Text(
                                text = stringResource(R.string.tavern_card_import_failed),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (importErrorMessage.isNotBlank()) {
                                Text(
                                    text = importErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    // 新建角色卡对话框
    if (showAddCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContent = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showAddCharacterCardDialog = false
                editingCharacterCard = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
            },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        userPreferencesManager.saveAiAvatarForCharacterCard(it.id, null)
                        refreshTrigger++
                    }
                }
            }
        )
    }

    // 编辑角色卡对话框
    if (showEditCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContent = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showEditCharacterCardDialog = false
                editingCharacterCard = null
                editingOriginalName = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
                                },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        userPreferencesManager.saveAiAvatarForCharacterCard(it.id, null)
                        refreshTrigger++
                    }
                }
            }
        )
                        }

    // 新建标签对话框
    if (showAddTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showAddTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
            }
        )
    }

    // 编辑标签对话框
    if (showEditTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showEditTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
                                        }
        )
    }

    // 删除角色卡确认对话框
    if (showDeleteCharacterCardConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteCharacterCardConfirm = false
                deletingCharacterCardId = ""
                deletingCharacterCardName = ""
            },
            title = { Text(stringResource(R.string.delete_character_card)) },
            text = { Text(stringResource(R.string.delete_character_card_confirm, deletingCharacterCardName)) },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteCharacterCard() }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteCharacterCardConfirm = false
                        deletingCharacterCardId = ""
                        deletingCharacterCardName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 角色卡变更提示
    if (showChatManagementPrompt) {
        AlertDialog(
            onDismissRequest = { showChatManagementPrompt = false },
            title = { Text(stringResource(R.string.character_card_change_title)) },
            text = { Text(stringResource(R.string.character_card_change_prompt)) },
            confirmButton = {
                Button(
                    onClick = {
                        showChatManagementPrompt = false
                        onNavigateToChatManagement()
                    }
                ) {
                    Text(stringResource(R.string.go_to_chat_management))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChatManagementPrompt = false }) {
                    Text(stringResource(R.string.maybe_later))
                }
            }
        )
    }

    // 重置默认角色卡确认对话框
    if (showResetDefaultConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDefaultConfirm = false },
            title = { Text(stringResource(R.string.reset_default_character)) },
            text = { Text(stringResource(R.string.reset_default_character_confirm)) },
            confirmButton = {
                Button(
                    onClick = { confirmResetDefaultCharacterCard() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除标签确认对话框
    if (showDeleteTagConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteTagConfirm = false
                deletingTagId = ""
                deletingTagName = ""
            },
            title = { Text(stringResource(R.string.delete_tag)) },
            text = { Text(stringResource(R.string.delete_tag_confirm, deletingTagName)) },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteTag() }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteTagConfirm = false
                        deletingTagId = ""
                        deletingTagName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 手动添加标签成功的高亮提示（底部显著提示，1.5s 自动消失）
    if (showTagSavedHighlight) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showTagSavedHighlight = false
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.save_successful),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

enum class CharacterCardSortOption {
    DEFAULT,
    NAME_ASC,
    CREATED_DESC
}

// 角色卡标签页
@Composable
fun CharacterCardTab(
    characterCards: List<CharacterCard>,
    activeCharacterCardId: String,
    allTags: List<PromptTag>,
    onAddCharacterCard: () -> Unit,
    onEditCharacterCard: (CharacterCard) -> Unit,
    onDeleteCharacterCard: (CharacterCard) -> Unit,
    onDuplicateCharacterCard: (CharacterCard) -> Unit,
    onResetDefaultCharacterCard: () -> Unit,
    onSetActiveCharacterCard: (String) -> Unit,
    onNavigateToPersonaGeneration: () -> Unit,
    onImportTavernCard: () -> Unit
) {
    var sortOption by remember { mutableStateOf(CharacterCardSortOption.DEFAULT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortedCharacterCards = remember(characterCards, sortOption) {
        when (sortOption) {
            CharacterCardSortOption.DEFAULT -> characterCards
            CharacterCardSortOption.NAME_ASC -> characterCards.sortedBy { it.name.lowercase() }
            CharacterCardSortOption.CREATED_DESC -> characterCards.sortedByDescending { it.updatedAt }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.character_card_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onAddCharacterCard,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_new), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：功能按钮 + 排序图标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToPersonaGeneration,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ai_creation), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onImportTavernCard,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.import_tavern_card), fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = { sortMenuExpanded = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = stringResource(R.string.character_card_sort),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.character_card_sort_default)) },
                            onClick = {
                                sortOption = CharacterCardSortOption.DEFAULT
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.character_card_sort_by_name)) },
                            onClick = {
                                sortOption = CharacterCardSortOption.NAME_ASC
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.character_card_sort_by_created)) },
                            onClick = {
                                sortOption = CharacterCardSortOption.CREATED_DESC
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 角色卡列表
        items(sortedCharacterCards) { characterCard ->
            CharacterCardItem(
                characterCard = characterCard,
                isActive = characterCard.id == activeCharacterCardId,
                allTags = allTags,
                onEdit = { onEditCharacterCard(characterCard) },
                onDelete = { onDeleteCharacterCard(characterCard) },
                onDuplicate = { onDuplicateCharacterCard(characterCard) },
                onReset = onResetDefaultCharacterCard,
                onSetActive = { onSetActiveCharacterCard(characterCard.id) }
            )
        }
    }
}

// 角色卡项目
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardItem(
    characterCard: CharacterCard,
    isActive: Boolean,
    allTags: List<PromptTag>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onSetActive: () -> Unit
) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
                        ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题和操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = characterCard.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.currently_active), fontSize = 10.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.currently_active),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    if (characterCard.description.isNotBlank()) {
                                Text(
                            text = characterCard.description,
                            style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // 三点菜单
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!isActive) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_active)) },
                                onClick = {
                                    onSetActive()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate)) },
                            onClick = {
                                onDuplicate()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        
                        if (characterCard.isDefault) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset)) },
                                onClick = {
                                    onReset()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                        
                        if (!characterCard.isDefault) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }

            // 角色设定预览
            if (characterCard.characterSetting.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                    text = stringResource(R.string.character_setting_preview, characterCard.characterSetting.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // 其他内容预览
            if (characterCard.otherContent.isNotBlank()) {
                                    Text(
                    text = stringResource(R.string.other_content_preview, characterCard.otherContent.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // 附着的标签
            if (characterCard.attachedTagIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    characterCard.attachedTagIds.take(3).forEach { tagId ->
                        val tag = allTags.find { it.id == tagId }
                        tag?.let {
                            AssistChip(
                                onClick = { },
                                label = { Text(it.name, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.height(24.dp)
                                        )
                        }
                    }
                    if (characterCard.attachedTagIds.size > 3) {
                                        Text(
                            text = "+${characterCard.attachedTagIds.size - 3}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 高级自定义提示词预览
            if (characterCard.advancedCustomPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                    text = stringResource(R.string.advanced_custom_preview, characterCard.advancedCustomPrompt.take(40)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// 标签标签页
@Composable
fun TagTab(
    tags: List<PromptTag>,
    onAddTag: () -> Unit,
    onEditTag: (PromptTag) -> Unit,
    onDeleteTag: (PromptTag) -> Unit,
    onNavigateToMarket: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                    Text(
                        text = stringResource(R.string.tag_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onAddTag,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_new_tag), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：标签市场按钮
                OutlinedButton(
                    onClick = onNavigateToMarket,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tag_market), fontSize = 12.sp)
                }
            }
        }

        // 系统标签
        val systemTags = tags.filter { it.isSystemTag }
        if (systemTags.isNotEmpty()) {
            item {
                                                    Text(
                    text = stringResource(R.string.system_tags),
                    style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(systemTags) { tag ->
                TagItem(
                    tag = tag,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag) }
                )
            }
        }

        // 自定义标签
        val customTags = tags.filter { !it.isSystemTag }
        if (customTags.isNotEmpty()) {
            item {
                                Text(
                    text = stringResource(R.string.custom_tags),
                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(customTags) { tag ->
                TagItem(
                    tag = tag,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag) }
                )
            }
        }
    }
}

// 标签项目
@Composable
fun TagItem(
    tag: PromptTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
                                        ) {
                                            Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isSystemTag)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
                                                        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                    text = tag.name,
                                            style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                                        )
                if (tag.description.isNotBlank()) {
                                                        Text(
                        text = tag.description,
                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (tag.promptContent.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.content_preview, tag.promptContent.take(50)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                                        )
                                    }
                                }

                                    Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                }

                if (!tag.isSystemTag) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                                                            }
                                    }
            }
        }
    }
}

// 标签对话框
@Composable
fun TagDialog(
    tag: PromptTag,
    onDismiss: () -> Unit,
    onSave: (PromptTag) -> Unit
) {
    var name by remember { mutableStateOf(tag.name) }
    var description by remember { mutableStateOf(tag.description) }
    var promptContent by remember { mutableStateOf(tag.promptContent) }

        AlertDialog(
        onDismissRequest = onDismiss,
            title = {
                Text(
                if (tag.id.isEmpty()) stringResource(R.string.create_tag) else stringResource(R.string.edit_tag),
                style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = promptContent,
                    onValueChange = { promptContent = it },
                    label = { Text(stringResource(R.string.prompt_content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                    minLines = 4,
                    maxLines = 12
                )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                    onSave(
                        tag.copy(
                            name = name,
                            description = description,
                            promptContent = promptContent,
                            tagType = tag.tagType
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
            },
            dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// 旧配置查看对话框已废弃
