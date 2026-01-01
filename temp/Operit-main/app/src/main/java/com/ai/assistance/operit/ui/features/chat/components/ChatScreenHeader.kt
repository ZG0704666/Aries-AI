package com.ai.assistance.operit.ui.features.chat.components

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.flowOf

@Composable
fun useFloatingWindowLauncher(
    actualViewModel: ChatViewModel,
    permissionLauncher: ActivityResultLauncher<String>
): () -> Unit {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return {
        val isCurrentlyFloating = actualViewModel.isFloatingMode.value
        actualViewModel.onFloatingButtonClick(
            FloatingMode.WINDOW,
            permissionLauncher,
            colorScheme,
            typography
        )
        // 如果当前不是悬浮模式，说明是要启动悬浮窗，则最小化应用
        if (!isCurrentlyFloating) {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ChatScreenHeader(
        modifier: Modifier = Modifier,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        chatHeaderTransparent: Boolean,
        chatHeaderHistoryIconColor: Int?,
        chatHeaderPipIconColor: Int?,
        onCharacterSwitcherClick: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val currentChatTitle = chatHistories.find { it.id == currentChatId }?.title
    val scope = rememberCoroutineScope()

    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activeCharacterCard by characterCardManager.activeCharacterCardFlow.collectAsState(initial = null)
    val activeCharacterAvatarUri by remember(activeCharacterCard?.id) {
        activeCharacterCard?.id?.let { userPreferencesManager.getAiAvatarForCharacterCardFlow(it) } ?: flowOf(null)
    }.collectAsState(initial = null)

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchFloatingModeIn(FloatingMode.WINDOW, colorScheme, typography)
                // WINDOW模式启动后最小化应用
                (context as? android.app.Activity)?.moveTaskToBack(true)
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied))
            }
        }


    val launchFloatingWindow = useFloatingWindowLauncher(actualViewModel, permissionLauncher)

    Box(
            modifier =
                    modifier
                            .fillMaxWidth()
                            .background(
                                    if (chatHeaderTransparent) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 左侧：聊天历史按钮
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChatHeader(
                    showChatHistorySelector = showChatHistorySelector,
                    onToggleChatHistorySelector = { actualViewModel.toggleChatHistorySelector() },
                    modifier = Modifier,
                    isFloatingMode = actualViewModel.isFloatingMode.value,
                    onLaunchFloatingWindow = launchFloatingWindow,
                    historyIconColor = chatHeaderHistoryIconColor,
                    pipIconColor = chatHeaderPipIconColor,
                    activeCharacterName = activeCharacterCard?.name ?: "",
                    activeCharacterAvatarUri = activeCharacterAvatarUri,
                    onCharacterClick = onCharacterSwitcherClick
            )
        }

        // 右侧：统计信息，水平排列
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // 统计信息
            val currentWindowSize = actualViewModel.currentWindowSize.value
            val maxWindowSizeInK = actualViewModel.maxWindowSizeInK.value
            val maxWindowSize = (maxWindowSizeInK * 1024).toInt()

            val inputTokenCount = actualViewModel.inputTokenCount.value
            val outputTokenCount = actualViewModel.outputTokenCount.value
            val totalTokenCount = inputTokenCount + outputTokenCount
            val contextUsagePercentage =
                    if (maxWindowSize > 0) {
                        (currentWindowSize.toFloat() / maxWindowSize) * 100
                    } else {
                        0f
                    }

            // 使用一个状态来跟踪是否显示详细信息
            val (showDetailedStats, setShowDetailedStats) = remember { mutableStateOf(false) }

            Box {
                // 主要显示（圆环进度）
                val progress = contextUsagePercentage / 100f
                val animatedProgress by animateFloatAsState(targetValue = progress, label = "TokenProgressAnimation")
                val progressColor = when {
                    contextUsagePercentage > 90 -> MaterialTheme.colorScheme.error
                    contextUsagePercentage > 75 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .clickable { setShowDetailedStats(!showDetailedStats) }
                        .size(32.dp)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier.fillMaxSize(),
                        color = progressColor,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "${contextUsagePercentage.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }

                // 简化的下拉框
                DropdownMenu(
                        expanded = showDetailedStats,
                        onDismissRequest = { setShowDetailedStats(false) },
                        modifier =
                                Modifier.width(IntrinsicSize.Min)
                                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.context_window, currentWindowSize)) },
                        onClick = {},
                        enabled = false
                    )
                    
                    DropdownMenuItem(
                            text = { Text(stringResource(R.string.input_tokens, inputTokenCount)) },
                            onClick = {},
                            enabled = false
                    )
                    DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.output_tokens, outputTokenCount))
                            },
                            onClick = {},
                            enabled = false
                    )
                    DropdownMenuItem(
                            text = {
                                Text(
                                        stringResource(R.string.total_tokens, totalTokenCount),
                                        style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight =
                                                                androidx.compose.ui.text.font
                                                                        .FontWeight.Bold
                                                ),
                                        color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {},
                            enabled = false
                    )
                    
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, isHighlighted: Boolean = false) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
                text = value,
                style =
                        MaterialTheme.typography.labelMedium.copy(
                                fontWeight =
                                        if (isHighlighted)
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                        else androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                color =
                        if (isHighlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
        )
    }
}