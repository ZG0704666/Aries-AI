package com.ai.assistance.operit.ui.features.settings.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.api.chat.EnhancedAIService
import kotlinx.coroutines.launch
import android.content.Context

// 角色卡名片对话框
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardDialog(
    characterCard: CharacterCard,
    allTags: List<PromptTag>,
    userPreferencesManager: UserPreferencesManager,
    onDismiss: () -> Unit,
    onSave: (CharacterCard) -> Unit,
    onAvatarChange: () -> Unit,
    onAvatarReset: () -> Unit
) {
    var name by remember(characterCard.id) { mutableStateOf(characterCard.name) }
    var description by remember(characterCard.id) { mutableStateOf(characterCard.description) }
    var characterSetting by remember(characterCard.id) { mutableStateOf(characterCard.characterSetting) }
    var openingStatement by remember(characterCard.id) { mutableStateOf(characterCard.openingStatement) } // 新增：开场白
    var otherContent by remember(characterCard.id) { mutableStateOf(characterCard.otherContent) }
    var attachedTagIds by remember(characterCard.id) { mutableStateOf(characterCard.attachedTagIds) }
    var advancedCustomPrompt by remember(characterCard.id) { mutableStateOf(characterCard.advancedCustomPrompt) }
    var marks by remember(characterCard.id) { mutableStateOf(characterCard.marks) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    // 翻译相关状态
    var isTranslating by remember { mutableStateOf(false) }
    
    // 大屏编辑状态
    var showFullScreenEdit by remember { mutableStateOf(false) }
    var fullScreenEditTitle by remember { mutableStateOf("") }
    var fullScreenEditValue by remember { mutableStateOf("") }
    var fullScreenEditField by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarUri by userPreferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
        .collectAsState(initial = null)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 名片头部区域 - 头像 + 基本信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 紧凑型头像
                    CompactAvatarPicker(
                        avatarUri = avatarUri,
                        onAvatarChange = onAvatarChange,
                        onAvatarReset = onAvatarReset
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // 基本信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        CompactTextFieldWithExpand(
                            value = name,
                            onValueChange = { name = it },
                            label = stringResource(R.string.character_card_name),
                            singleLine = true,
                            onExpandClick = {
                                fullScreenEditTitle = context.getString(R.string.character_card_edit_name)
                                fullScreenEditValue = name
                                fullScreenEditField = "name"
                                showFullScreenEdit = true
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        CompactTextFieldWithExpand(
                            value = description,
                            onValueChange = { description = it },
                            label = stringResource(R.string.character_card_description),
                            maxLines = 2,
                            onExpandClick = {
                                fullScreenEditTitle = context.getString(R.string.character_card_edit_description)
                                fullScreenEditValue = description
                                fullScreenEditField = "description"
                                showFullScreenEdit = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 角色设定
                    Text(
                        text = stringResource(R.string.character_card_character_setting),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CompactTextFieldWithExpand(
                        value = characterSetting,
                        onValueChange = { characterSetting = it },
                        placeholder = stringResource(R.string.character_card_character_setting_placeholder),
                        minLines = 2,
                        maxLines = 4,
                        onExpandClick = {
                            fullScreenEditTitle = context.getString(R.string.character_card_edit_character_setting)
                            fullScreenEditValue = characterSetting
                            fullScreenEditField = "characterSetting"
                            showFullScreenEdit = true
                        }
                    )

                    // 开场白
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.character_card_opening_statement),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 翻译按钮
                        IconButton(
                            onClick = {
                                if (openingStatement.isNotBlank() && !isTranslating) {
                                    scope.launch {
                                        isTranslating = true
                                        try {
                                            val enhancedAIService = EnhancedAIService.getInstance(context)
                                            val translatedText = enhancedAIService.translateText(openingStatement)
                                            openingStatement = translatedText
                                        } catch (e: Exception) {
                                            // 翻译失败，可以显示错误提示
                                        } finally {
                                            isTranslating = false
                                        }
                                    }
                                }
                            },
                            enabled = openingStatement.isNotBlank() && !isTranslating,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.character_card_translate_opening_statement),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    CompactTextFieldWithExpand(
                        value = openingStatement,
                        onValueChange = { openingStatement = it },
                        placeholder = stringResource(R.string.character_card_opening_statement_placeholder),
                        minLines = 2,
                        maxLines = 4,
                        onExpandClick = {
                            fullScreenEditTitle = context.getString(R.string.character_card_edit_opening_statement)
                            fullScreenEditValue = openingStatement
                            fullScreenEditField = "openingStatement"
                            showFullScreenEdit = true
                        }
                    )

                    // 其他内容
                    Text(
                        text = stringResource(R.string.character_card_other_content),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CompactTextFieldWithExpand(
                        value = otherContent,
                        onValueChange = { otherContent = it },
                        placeholder = stringResource(R.string.character_card_other_content_placeholder),
                        minLines = 2,
                        maxLines = 4,
                        onExpandClick = {
                            fullScreenEditTitle = context.getString(R.string.character_card_edit_other_content)
                            fullScreenEditValue = otherContent
                            fullScreenEditField = "otherContent"
                            showFullScreenEdit = true
                        }
                    )

                    // 标签选择
                    Text(
                        text = stringResource(R.string.character_card_tags),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val customTags = allTags.filter { !it.isSystemTag }
                    if (customTags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            customTags.forEach { tag ->
                                val isSelected = attachedTagIds.contains(tag.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        attachedTagIds = if (isSelected) {
                                            attachedTagIds.filter { it != tag.id }
                                        } else {
                                            attachedTagIds + tag.id
                                        }
                                    },
                                    label = { Text(tag.name, fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.character_card_no_custom_tags),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // 高级选项折叠区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.character_card_advanced_options),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showAdvanced) {
                        // 高级自定义提示词
                        CompactTextFieldWithExpand(
                            value = advancedCustomPrompt,
                            onValueChange = { advancedCustomPrompt = it },
                            label = stringResource(R.string.character_card_custom_prompt),
                            placeholder = stringResource(R.string.character_card_custom_prompt_placeholder),
                            minLines = 2,
                            maxLines = 3,
                            onExpandClick = {
                                fullScreenEditTitle = context.getString(R.string.character_card_edit_custom_prompt)
                                fullScreenEditValue = advancedCustomPrompt
                                fullScreenEditField = "advancedCustomPrompt"
                                showFullScreenEdit = true
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 备注
                        CompactTextFieldWithExpand(
                            value = marks,
                            onValueChange = { marks = it },
                            label = stringResource(R.string.character_card_marks),
                            placeholder = stringResource(R.string.character_card_marks_placeholder),
                            minLines = 1,
                            maxLines = 2,
                            onExpandClick = {
                                fullScreenEditTitle = context.getString(R.string.character_card_edit_marks)
                                fullScreenEditValue = marks
                                fullScreenEditField = "marks"
                                showFullScreenEdit = true
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontSize = 13.sp)
                    }
                    
                    Button(
                        onClick = {
                            onSave(
                                characterCard.copy(
                                    name = name,
                                    description = description,
                                    characterSetting = characterSetting,
                                    openingStatement = openingStatement, // 新增
                                    otherContent = otherContent,
                                    attachedTagIds = attachedTagIds,
                                    advancedCustomPrompt = advancedCustomPrompt,
                                    marks = marks
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(stringResource(R.string.save), fontSize = 13.sp)
                    }
                }
            }
        }
    }
    
    // 全屏编辑对话框
    if (showFullScreenEdit) {
        FullScreenEditDialog(
            title = fullScreenEditTitle,
            value = fullScreenEditValue,
            onDismiss = { showFullScreenEdit = false },
            onSave = { newValue ->
                when (fullScreenEditField) {
                    "name" -> name = newValue
                    "description" -> description = newValue
                    "characterSetting" -> characterSetting = newValue
                    "openingStatement" -> openingStatement = newValue // 新增
                    "otherContent" -> otherContent = newValue
                    "advancedCustomPrompt" -> advancedCustomPrompt = newValue
                    "marks" -> marks = newValue
                }
                showFullScreenEdit = false
            }
        )
    }
}

// 带展开按钮的紧凑输入框
@Composable
fun CompactTextFieldWithExpand(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    onExpandClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label?.let { { Text(it, fontSize = 11.sp) } },
            placeholder = placeholder?.let { { Text(it, fontSize = 11.sp) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 32.dp), // 为右上角按钮留空间
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(6.dp)
        )
        
        // 右上角展开按钮
        IconButton(
            onClick = onExpandClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .offset(x = (-2).dp, y = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = stringResource(R.string.character_card_fullscreen_edit),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// 全屏编辑对话框
@Composable
fun FullScreenEditDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editValue by remember { mutableStateOf(value) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 编辑区域
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    placeholder = { Text(stringResource(R.string.character_card_fullscreen_edit_placeholder)) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Button(
                        onClick = { onSave(editValue) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

// 紧凑型头像选择器
@Composable
fun CompactAvatarPicker(
    avatarUri: String?,
    onAvatarChange: () -> Unit,
    onAvatarReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAvatarChange)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                    contentDescription = stringResource(R.string.character_card_avatar),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.character_card_default_avatar),
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 重置按钮
        if (avatarUri != null) {
            TextButton(
                onClick = onAvatarReset,
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(R.string.character_card_reset), fontSize = 10.sp)
            }
        } else {
            TextButton(
                onClick = onAvatarChange,
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(R.string.character_card_add), fontSize = 10.sp)
            }
        }
    }
}