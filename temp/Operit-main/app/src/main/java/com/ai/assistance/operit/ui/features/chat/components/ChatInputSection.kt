package com.ai.assistance.operit.ui.features.chat.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.util.ChatUtils
import androidx.compose.ui.res.stringResource
import android.net.Uri

@Composable
fun ChatInputSection(
    actualViewModel: ChatViewModel,
    userMessage: TextFieldValue,
    onUserMessageChange: (TextFieldValue) -> Unit,
    onSendMessage: () -> Unit,
    onCancelMessage: () -> Unit,
    isLoading: Boolean,
    inputState: InputProcessingState = InputProcessingState.Idle,
    allowTextInputWhileProcessing: Boolean = false,
    onAttachmentRequest: (String) -> Unit = {},
    attachments: List<AttachmentInfo> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onInsertAttachment: (AttachmentInfo) -> Unit = {},
    onAttachScreenContent: () -> Unit = {},
    onAttachNotifications: () -> Unit = {},
    onAttachLocation: () -> Unit = {},
    onAttachMemory: () -> Unit = {},
    onTakePhoto: (Uri) -> Unit,
    hasBackgroundImage: Boolean = false,
    chatInputTransparent: Boolean = false,
    modifier: Modifier = Modifier,
    externalAttachmentPanelState: Boolean? = null,
    onAttachmentPanelStateChange: ((Boolean) -> Unit)? = null,
    showInputProcessingStatus: Boolean = true,
    enableTools: Boolean = true,
    replyToMessage: ChatMessage? = null, // 回复目标消息
    onClearReply: (() -> Unit)? = null, // 清除回复状态的回调
    isWorkspaceOpen: Boolean = false
) {
    val showTokenLimitDialog = remember { mutableStateOf(false) }
    val showFullscreenInput = remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showTokenLimitDialog.value) {
        AlertDialog(
            onDismissRequest = { showTokenLimitDialog.value = false },
            title = { Text(context.getString(R.string.token_limit_warning)) },
            text = { Text(context.getString(R.string.token_limit_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTokenLimitDialog.value = false
                        onSendMessage()
                    }
                ) { Text(context.getString(R.string.continue_send)) }
            },
            dismissButton = {
                TextButton(onClick = { showTokenLimitDialog.value = false }) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
    val modernTextStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val toolProgressEvent by ToolProgressBus.progress.collectAsState()

    val isProcessing = isLoading

    // Token limit calculation
    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val maxTokens = (maxWindowSizeInK * 1024).toInt()
    val userMessageTokens = remember(userMessage.text) { ChatUtils.estimateTokenCount(userMessage.text) }

    val isOverTokenLimit =
        if (maxTokens > 0) {
            (userMessageTokens + currentWindowSize) > maxTokens
        } else {
            false
        }

    val canSendMessage = userMessage.text.isNotBlank() || attachments.isNotEmpty()
    val sendButtonEnabled =
        when {
            isProcessing -> true // Cancel button
            canSendMessage -> true // Send button is always enabled if there's content
            else -> true // Mic button
        }

    val voicePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchFloatingModeIn(
                    FloatingMode.FULLSCREEN,
                    colorScheme,
                    typography
                )
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied_toast))
            }
        }

    // 控制附件面板的展开状态 - 使用外部状态或本地状态
    val (showAttachmentPanel, setShowAttachmentPanel) =
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(
                externalAttachmentPanelState ?: false
            )
        }

    // 当外部状态变化时更新本地状态
    androidx.compose.runtime.LaunchedEffect(externalAttachmentPanelState) {
        externalAttachmentPanelState?.let { setShowAttachmentPanel(it) }
    }

    // 当本地状态改变时通知外部
    androidx.compose.runtime.LaunchedEffect(showAttachmentPanel) {
        onAttachmentPanelStateChange?.invoke(showAttachmentPanel)
    }

    val surfaceColor = when {
        chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        hasBackgroundImage -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(color = surfaceColor) {
        Column {
            // Reply preview section
            replyToMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = context.getString(R.string.reply_message),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val previewText = message.content
                            .replace(Regex("<[^>]*>"), "") // 移除XML标签
                            .trim()
                            .let { if (it.length > 50) it.take(50) + "..." else it }
                        Text(
                            text = "${stringResource(R.string.reply_message)}: $previewText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { onClearReply?.invoke() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = context.getString(R.string.cancel_reply),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Input processing indicator
            SimpleAnimatedVisibility(visible = showInputProcessingStatus && inputState !is InputProcessingState.Idle && inputState !is InputProcessingState.Completed) {
                val (progressColor, baseMessage) = when (inputState) {
                    is InputProcessingState.Connecting -> MaterialTheme.colorScheme.tertiary to inputState.message
                    is InputProcessingState.ExecutingTool -> MaterialTheme.colorScheme.secondary to context.getString(R.string.executing_tool, inputState.toolName)
                    is InputProcessingState.ToolProgress -> MaterialTheme.colorScheme.secondary to inputState.message
                    is InputProcessingState.Processing -> MaterialTheme.colorScheme.primary to inputState.message
                    is InputProcessingState.ProcessingToolResult -> MaterialTheme.colorScheme.tertiary.copy(
                        alpha = 0.8f
                    ) to context.getString(R.string.processing_tool_result, inputState.toolName)
                    is InputProcessingState.Summarizing -> MaterialTheme.colorScheme.tertiary to inputState.message
                    is InputProcessingState.Receiving -> MaterialTheme.colorScheme.secondary to inputState.message
                    else -> MaterialTheme.colorScheme.primary to ""
                }

                var message = baseMessage
                var progressValue = when (inputState) {
                    is InputProcessingState.Processing -> 0.3f
                    is InputProcessingState.Connecting -> 0.6f
                    is InputProcessingState.Summarizing -> 0.9f
                    is InputProcessingState.ToolProgress -> inputState.progress
                    else -> 1f
                }

                if (inputState is InputProcessingState.ExecutingTool) {
                    val event = toolProgressEvent
                    if (event != null && inputState.toolName.contains(event.toolName)) {
                        progressValue = event.progress
                        if (event.message.isNotBlank()) {
                            message = event.message
                        }
                    }
                }

                SimpleLinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor
                )

                if (message.isNotBlank()) {
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }


            // Attachment chips row - only show if there are attachments
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(attachments) { attachment ->
                        AttachmentChip(
                            attachmentInfo = attachment,
                            onRemove = {
                                onRemoveAttachment(
                                    attachment.filePath
                                )
                            },
                            onInsert = {
                                onInsertAttachment(attachment)
                            }
                        )
                    }
                }
            }


            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Input field (保持原有高度)

                OutlinedTextField(
                    value = userMessage,
                    onValueChange = onUserMessageChange,
                    placeholder = {
                        Text(
                            if (isWorkspaceOpen) context.getString(R.string.input_question_with_workspace) else context.getString(R.string.input_question_hint),
                            style = modernTextStyle
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp),
                    textStyle = modernTextStyle,
                    maxLines = 5,
                    minLines = 1,
                    singleLine = false,
                    keyboardOptions =
                    KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor =
                        MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor =
                        MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (userMessage.text.contains("\n")) {
                            IconButton(onClick = { showFullscreenInput.value = true }) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen input",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    enabled = !isProcessing || allowTextInputWhileProcessing
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Attachment button (+ 按钮) - 确保圆形

                Box(
                    modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (showAttachmentPanel)
                                MaterialTheme.colorScheme
                                    .primary
                            else
                                MaterialTheme.colorScheme
                                    .surfaceVariant
                        )
                        .clickable(
                            enabled = !isProcessing,
                            onClick = {
                                setShowAttachmentPanel(
                                    !showAttachmentPanel
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = context.getString(R.string.add_attachment),
                        tint =
                        if (showAttachmentPanel)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Send button (发送按钮) - 确保圆形
                Box(
                    modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isProcessing ->
                                    MaterialTheme
                                        .colorScheme
                                        .error

                                canSendMessage ->
                                    if (isOverTokenLimit)
                                        MaterialTheme.colorScheme.secondary // Warning color
                                    else
                                        MaterialTheme.colorScheme.primary

                                else ->
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            }
                        )
                        .clickable(
                            enabled = sendButtonEnabled,
                            onClick = {
                                when {
                                    isProcessing ->
                                        onCancelMessage()

                                    canSendMessage -> {
                                        if (isOverTokenLimit) {
                                            showTokenLimitDialog.value = true
                                        } else {
                                            onSendMessage()
                                            // 发送消息后关闭附件面板
                                            setShowAttachmentPanel(
                                                false
                                            )
                                        }
                                    }

                                    else -> {
                                        actualViewModel.onFloatingButtonClick(
                                            FloatingMode.FULLSCREEN,
                                            voicePermissionLauncher,
                                            colorScheme,
                                            typography
                                        )
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconTint =
                        when {
                            isProcessing -> MaterialTheme.colorScheme.onError
                            canSendMessage ->
                                if (isOverTokenLimit)
                                    MaterialTheme.colorScheme.onSecondary
                                else
                                    MaterialTheme.colorScheme.onPrimary

                            else -> MaterialTheme.colorScheme.onPrimary
                        }
                    Icon(
                        imageVector =
                        when {
                            isProcessing -> Icons.Default.Close
                            canSendMessage -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        },
                        contentDescription =
                        when {
                            isProcessing -> context.getString(R.string.cancel)
                            canSendMessage -> context.getString(R.string.send)
                            else -> context.getString(R.string.voice_input)
                        },
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

            }



            // Token limit warning
            if (isOverTokenLimit && canSendMessage) {
                Text(
                    text =
                    context.getString(R.string.token_limit_exceeded_message, userMessageTokens + currentWindowSize, maxTokens),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp)
                )
            }

            // 附件选择面板 - 移动到输入框下方

            AttachmentSelectorPanel(
                visible = showAttachmentPanel,
                onAttachImage = { filePath ->
                    // 传递文件路径给外部处理函数
                    onAttachmentRequest(filePath)
                },
                onAttachFile = { filePath ->
                    // 传递文件路径给外部处理函数
                    onAttachmentRequest(filePath)
                },
                onAttachScreenContent = onAttachScreenContent,
                onAttachNotifications = onAttachNotifications,
                onAttachLocation = onAttachLocation,
                onAttachMemory = onAttachMemory,
                onTakePhoto = onTakePhoto,
                userQuery = userMessage.text,
                onDismiss = { setShowAttachmentPanel(false) }
            )

            if (showFullscreenInput.value) {
                FullscreenInputDialog(
                    value = userMessage,
                    onValueChange = onUserMessageChange,
                    onDismiss = { showFullscreenInput.value = false },
                    onConfirm = { showFullscreenInput.value = false }
                )
            }

        }
    }
}

@Composable
fun AttachmentChip(attachmentInfo: AttachmentInfo, onRemove: () -> Unit, onInsert: () -> Unit) {
    val context = LocalContext.current
    val isImage = attachmentInfo.mimeType.startsWith("image/")
    val icon: ImageVector = if (isImage) Icons.Default.Image else Icons.Default.Description

    Surface(
        modifier =
        Modifier
            .height(26.dp)
            .border(
                width = 1.dp,
                color =
                MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.5f
                ),
                shape = RoundedCornerShape(13.dp)
            ),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = attachmentInfo.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 80.dp)
            )

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(onClick = onInsert, modifier = Modifier.size(14.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = context.getString(R.string.insert_attachment),
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(onClick = onRemove, modifier = Modifier.size(14.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = context.getString(R.string.remove_attachment),
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
