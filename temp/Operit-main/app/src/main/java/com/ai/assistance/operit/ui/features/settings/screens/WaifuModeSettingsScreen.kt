package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.model.CharacterCard
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.components.CustomScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaifuModeSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCustomEmoji: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val waifuPreferences = remember { WaifuPreferences.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    // 获取当前活跃角色卡
    val activeCharacterCard = characterCardManager.activeCharacterCardFlow.collectAsState(
        initial = CharacterCard(
            id = "default_character",
            name = "默认角色卡",
            description = "",
            characterSetting = "",
            otherContent = "",
            attachedTagIds = emptyList(),
            advancedCustomPrompt = "",
            marks = "",
            isDefault = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    ).value

    // 状态
    var showSaveSuccess by remember { mutableStateOf(false) }
    val isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.collectAsState(initial = false).value
    val charDelay = waifuPreferences.waifuCharDelayFlow.collectAsState(initial = 500).value
    val removePunctuation = waifuPreferences.waifuRemovePunctuationFlow.collectAsState(initial = false).value
    val disableActions = waifuPreferences.waifuDisableActionsFlow.collectAsState(initial = false).value
    val enableEmoticons = waifuPreferences.waifuEnableEmoticonsFlow.collectAsState(initial = false).value
    val enableSelfie = waifuPreferences.waifuEnableSelfieFlow.collectAsState(initial = false).value
    val selfiePrompt = waifuPreferences.waifuSelfiePromptFlow.collectAsState(initial = "").value
    
    // 辅助保存函数，同时保存到角色卡
    val saveSettings: (suspend () -> Unit) -> Unit = { saveAction ->
        scope.launch {
            saveAction()
            characterCardManager.saveWaifuSettingsForActiveCharacterCard()
            showSaveSuccess = true
        }
    }
    
    // 显示保存成功的提示
    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            kotlinx.coroutines.delay(2000)
            showSaveSuccess = false
        }
    }

    CustomScaffold(

    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 页面标题和说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.waifu_mode),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.waifu_mode_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 角色卡绑定提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "配置已绑定到角色卡",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = activeCharacterCard.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Waifu模式开关
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.enable_waifu_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.waifu_mode_toggle_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Switch(
                            checked = isWaifuModeEnabled,
                            onCheckedChange = { enabled ->
                                saveSettings {
                                    waifuPreferences.saveEnableWaifuMode(enabled)
                                }
                            }
                        )
                    }
                }
            }

            // 延迟时间配置（仅在waifu模式启用时显示）
            if (isWaifuModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.typing_speed_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.typing_speed_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示当前速度
                        val charsPerSecond = if (charDelay > 0) 1000f / charDelay else 0f
                        Text(
                            text = stringResource(R.string.current_speed_format, charsPerSecond),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 延迟时间滑块
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.speed_fast),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(30.dp)
                            )
                            Slider(
                                value = charDelay.toFloat(),
                                onValueChange = { newDelay ->
                                    saveSettings {
                                        waifuPreferences.saveWaifuCharDelay(newDelay.toInt())
                                    }
                                },
                                valueRange = 200f..1000f, // 200ms-1000ms per character (5-1字符/秒)
                                steps = 39, // 20ms步长
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.speed_slow),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(30.dp)
                            )
                        }
                        
                        Text(
                            text = stringResource(R.string.current_delay_format, charDelay),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // 标点符号配置（仅在waifu模式启用时显示）
            if (isWaifuModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.remove_punctuation),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.remove_punctuation_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            
                            Switch(
                                checked = removePunctuation,
                                onCheckedChange = { enabled ->
                                    saveSettings {
                                        waifuPreferences.saveWaifuRemovePunctuation(enabled)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 动作表情配置（仅在waifu模式启用时显示）
            if (isWaifuModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.disable_action_emoticons),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.disable_action_emoticons_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.action_emoticons_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                            
                            Switch(
                                checked = disableActions,
                                onCheckedChange = { enabled ->
                                    saveSettings {
                                        waifuPreferences.saveWaifuDisableActions(enabled)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 表情包配置（仅在waifu模式启用时显示）
            if (isWaifuModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.enable_emoticons),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.emoticons_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.available_emoticons),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                            
                            Switch(
                                checked = enableEmoticons,
                                onCheckedChange = { enabled ->
                                    saveSettings {
                                        waifuPreferences.saveWaifuEnableEmoticons(enabled)
                                    }
                                }
                            )
                        }
                    }
                }
                
                // 管理自定义表情入口
                Card(
                    onClick = onNavigateToCustomEmoji,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.manage_custom_emoji),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.custom_emoji_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = stringResource(R.string.manage_custom_emoji),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 自拍功能配置（仅在waifu模式启用时显示）
            if (isWaifuModeEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.enable_selfie_feature),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.selfie_feature_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            
                            Switch(
                                checked = enableSelfie,
                                onCheckedChange = { enabled ->
                                    saveSettings {
                                        waifuPreferences.saveWaifuEnableSelfie(enabled)
                                    }
                                }
                            )
                        }
                        
                        // 如果启用了自拍功能，显示外貌提示词输入框
                        if (enableSelfie) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.appearance_prompt),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.appearance_prompt_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var promptText by remember { mutableStateOf(selfiePrompt) }
                            
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = { newText ->
                                    promptText = newText
                                    saveSettings {
                                        waifuPreferences.saveWaifuSelfiePrompt(newText)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.appearance_description_label)) },
                                placeholder = { Text(stringResource(R.string.appearance_prompt_placeholder)) },
                                minLines = 3,
                                maxLines = 6,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.appearance_prompt_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 功能说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.feature_explanation),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.waifu_mode_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 保存成功提示
            if (showSaveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_saved_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
} 