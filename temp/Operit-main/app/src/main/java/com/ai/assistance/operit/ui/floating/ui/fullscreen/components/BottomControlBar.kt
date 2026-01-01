package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 底部控制栏组件
 * 包含返回按钮、麦克风按钮和缩小按钮
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun BottomControlBar(
    visible: Boolean,
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    floatContext: FloatContext,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    isWaveActive: Boolean,
    onToggleWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    onUserMessageChange: (String) -> Unit,
    attachScreenContent: Boolean,
    onAttachScreenContentChange: (Boolean) -> Unit,
    attachNotifications: Boolean,
    onAttachNotificationsChange: (Boolean) -> Unit,
    hasOcrSelection: Boolean,
    onHasOcrSelectionChange: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    // 底部输入模式：false = 文本输入框；true = 整条变成“按住说话”按钮
    var isHoldToSpeakMode by remember { mutableStateOf(false) }
    var isCancelRegion by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    // 简单的音量历史，用于在长按时绘制一个从右往左移动的波形条
    val volumeHistory = remember {
        mutableStateListOf<Float>().apply {
            repeat(24) { add(0f) }
        }
    }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var allowBlankFocus by remember { mutableStateOf(false) }
    val pillHeight = 48.dp
    val holdToSpeakTriggerMs = 450L
    val iconRegionWidth = 72.dp
    // 取消区域：大致拖出胶囊高度（56dp）之外才算取消
    val cancelThresholdPx = with(density) { pillHeight.toPx() }

    // 在长按语音时，根据当前音量持续更新历史，用于绘制音量波形
    LaunchedEffect(volumeLevel, isPressed, isRecording) {
        if (isPressed && isRecording && volumeHistory.isNotEmpty()) {
            volumeHistory.removeAt(0)
            volumeHistory.add(volumeLevel.coerceIn(0f, 1f))
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val pillColor = when {
                isCancelRegion && isHoldToSpeakMode -> MaterialTheme.colorScheme.error
                isHoldToSpeakMode && isPressed -> Color(0xFFE0E0E0) // 按下时使用实心浅灰色
                else -> Color.White
            }

            val canSend = userMessage.isNotBlank() || attachScreenContent || attachNotifications || hasOcrSelection
            val glowPadding = 10.dp
            val glowPaddingPx = with(density) { glowPadding.toPx() }
            val glowBaseColors = listOf(
                Color(0xFF42A5F5),
                Color(0xFF26C6DA),
                Color(0xFF66BB6A)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .offset(y = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScreenOcrMode = floatContext.currentMode == FloatingMode.SCREEN_OCR
                    val isScreenOcrSelected = isScreenOcrMode || hasOcrSelection

                    // 屏幕内容
                    GlassyChip(
                        selected = attachScreenContent,
                        text = "屏幕内容",
                        icon = Icons.Default.Check,
                        showIcon = attachScreenContent,
                        onClick = { onAttachScreenContentChange(!attachScreenContent) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 通知
                    GlassyChip(
                        selected = attachNotifications,
                        text = "通知",
                        icon = Icons.Default.Check,
                        showIcon = attachNotifications,
                        onClick = { onAttachNotificationsChange(!attachNotifications) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 圈选识别
                    GlassyChip(
                        selected = isScreenOcrSelected,
                        text = if (hasOcrSelection) "已圈选" else "圈选识别",
                        icon = if (isScreenOcrSelected) Icons.Default.Check else Icons.Default.Crop,
                        showIcon = true,
                        onClick = {
                            if (hasOcrSelection) {
                                // 已有圈选内容，点击清除
                                onHasOcrSelectionChange(false)
                            } else if (isScreenOcrMode) {
                                floatContext.onModeChange(floatContext.previousMode)
                            } else {
                                floatContext.onModeChange(FloatingMode.SCREEN_OCR)
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val innerRect = Rect(
                                left = glowPaddingPx,
                                top = glowPaddingPx,
                                right = size.width - glowPaddingPx,
                                bottom = size.height - glowPaddingPx
                            )

                            val baseRadius = innerRect.height / 2f
                            val maxGlow = glowPaddingPx
                            val layers = 12
                            
                            // Draw from largest (outer) to smallest (inner)
                            for (i in 0 until layers) {
                                val progress = i / (layers - 1f) // 0.0 -> 1.0
                                // progress 0 = Outer (Faint), progress 1 = Inner (Bright)
                                
                                val spread = maxGlow * (1f - progress)
                                val radius = baseRadius + spread
                                
                                // Steeper curve (pow 4) means the outer layers drop off much faster
                                val boost = progress * progress * progress * progress
                                // Base alpha extremely low for outer layers
                                val alpha = 0.005f + 0.7f * boost

                                // Whiteness only at the very core
                                val whiteFactor = progress * progress * progress * 0.9f 

                                val layerColors = glowBaseColors.map { color ->
                                    androidx.compose.ui.graphics.lerp(color, Color.White, whiteFactor).copy(alpha = alpha)
                                }

                                val rect = Rect(
                                    left = innerRect.left - spread,
                                    top = innerRect.top - spread,
                                    right = innerRect.right + spread,
                                    bottom = innerRect.bottom + spread
                                )

                                drawRoundRect(
                                    brush = Brush.horizontalGradient(colors = layerColors),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height),
                                    cornerRadius = CornerRadius(radius, radius)
                                )
                            }
                        }
                        .padding(glowPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.10f)
                            )
                            .clip(CircleShape)
                            .background(pillColor)
                    ) {
                        OutlinedTextField(
                            value = userMessage,
                            onValueChange = { if (!isHoldToSpeakMode) onUserMessageChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    canFocus = userMessage.isNotBlank() || allowBlankFocus
                                }
                            ,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = true,
                            readOnly = isHoldToSpeakMode,
                            placeholder = {
                                if (!isHoldToSpeakMode) {
                                    Text(
                                        text = "输入或者长按语音",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            leadingIcon = {
                                IconButton(onClick = onToggleWaveMode) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "语音通话",
                                        tint = if (isWaveActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                if (!isHoldToSpeakMode) {
                                    IconButton(
                                        onClick = {
                                            allowBlankFocus = false
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onSendClick()
                                        },
                                        enabled = canSend
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "发送",
                                            tint = if (canSend) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        )

                        if (userMessage.isBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(pillHeight)
                            ) {
                                Spacer(modifier = Modifier.width(iconRegionWidth))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(pillHeight)
                                        .pointerInput(cancelThresholdPx, userMessage) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)

                                                down.consume()
                                                allowBlankFocus = false
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()

                                                val releasedBeforeTimeout = withTimeoutOrNull(holdToSpeakTriggerMs) {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                                        if (event.changes.isEmpty()) {
                                                            return@withTimeoutOrNull true
                                                        }
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                        if (change == null) {
                                                            if (event.changes.none { it.pressed }) {
                                                                return@withTimeoutOrNull true
                                                            }
                                                            continue
                                                        }
                                                        if (!change.pressed) {
                                                            return@withTimeoutOrNull true
                                                        }
                                                    }
                                                }

                                                if (releasedBeforeTimeout == true) {
                                                    allowBlankFocus = true
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                    return@awaitEachGesture
                                                }

                                                val startPosition = down.position
                                                var totalDragY = 0f
                                                isHoldToSpeakMode = true
                                                isPressed = true
                                                isCancelRegion = false
                                                allowBlankFocus = false
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                onStartVoiceCapture()

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                                    if (event.changes.isEmpty()) {
                                                        allowBlankFocus = false
                                                        focusManager.clearFocus(force = true)
                                                        keyboardController?.hide()
                                                        onStopVoiceCapture(isCancelRegion)
                                                        isCancelRegion = false
                                                        isPressed = false
                                                        isHoldToSpeakMode = false
                                                        totalDragY = 0f
                                                        break
                                                    }

                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) {
                                                        allowBlankFocus = false
                                                        focusManager.clearFocus(force = true)
                                                        keyboardController?.hide()
                                                        onStopVoiceCapture(isCancelRegion)
                                                        isCancelRegion = false
                                                        isPressed = false
                                                        isHoldToSpeakMode = false
                                                        totalDragY = 0f
                                                        break
                                                    }

                                                    val position = change.position
                                                    val dy = position.y - startPosition.y
                                                    totalDragY = dy
                                                    isCancelRegion = abs(totalDragY) > cancelThresholdPx
                                                }
                                            }
                                        }
                                )
                                Spacer(modifier = Modifier.width(iconRegionWidth))
                            }
                        }
                    }

                    if (isHoldToSpeakMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
                        ) {
                            if (isPressed && !isCancelRegion) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(pillHeight)
                                ) {
                                    val barCount = volumeHistory.size
                                    if (barCount > 0) {
                                        val horizontalMargin = size.width * 0.28f
                                        val availableWidth = size.width - horizontalMargin * 2f
                                        val barWidth = availableWidth / (barCount * 1.4f)
                                        val gap = barWidth * 0.4f
                                        val maxHeight = size.height * 0.3f
                                        val centerY = size.height / 2f

                                        volumeHistory.forEachIndexed { index: Int, value: Float ->
                                            val xRight = size.width - horizontalMargin - (barWidth + gap) * (barCount - 1 - index).toFloat()
                                            val barHeight = (value.coerceIn(0f, 1f)) * maxHeight
                                            val top = centerY - barHeight / 2f
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = Offset(xRight - barWidth, top),
                                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isCancelRegion) "松手取消" else "松手结束",
                                    color = if (isCancelRegion) Color.White else Color.Black,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 返回按钮
 */
@Composable
private fun BackButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            floatContext.onModeChange(FloatingMode.WINDOW)
        },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "返回窗口模式",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 缩小成语音球按钮
 */
@Composable
private fun MinimizeToVoiceBallButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { floatContext.onModeChange(FloatingMode.VOICE_BALL) },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = "缩小成语音球",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 麦克风按钮和拖动提示
 */
@Composable
private fun MicrophoneButtonWithHints(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val isDraggingToCancel = remember { mutableStateOf(false) }
    val isDraggingToEdit = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 左侧编辑提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Edit,
            iconColor = MaterialTheme.colorScheme.primary,
            description = "编辑",
            isLeft = true,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp)
        )

        // 右侧取消提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            description = "取消",
            isLeft = false,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp)
        )

        // 麦克风按钮
        MicrophoneButton(
            isRecording = isRecording,
            isProcessingSpeech = isProcessingSpeech,
            isDraggingToCancel = isDraggingToCancel,
            isDraggingToEdit = isDraggingToEdit,
            onStartVoiceCapture = onStartVoiceCapture,
            onStopVoiceCapture = onStopVoiceCapture,
            onEnterWaveMode = onEnterWaveMode,
            onEnterEditMode = onEnterEditMode,
            onShowDragHintsChange = onShowDragHintsChange,
            onDragOffsetChange = { dragOffset = it },
            onDraggingToCancelChange = { isDraggingToCancel.value = it },
            onDraggingToEditChange = { isDraggingToEdit.value = it },
            userMessage = userMessage,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 拖动提示组件
 */
@Composable
private fun DragHint(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    description: String,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (isLeft) -it else it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (isLeft) -it else it }),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                // 编辑图标在左
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                DashedLine()
            } else {
                // 取消图标在右
                DashedLine()
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 虚线组件
 */
@Composable
private fun DashedLine() {
    Canvas(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = pathEffect
        )
    }
}

/**
 * 麦克风按钮
 */
@Composable
private fun MicrophoneButton(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    isDraggingToCancel: MutableState<Boolean>,
    isDraggingToEdit: MutableState<Boolean>,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDraggingToCancelChange: (Boolean) -> Unit,
    onDraggingToEditChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isRecording || isProcessingSpeech) {
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primary
                        )
                    }
                )
            )
            .clickable(enabled = false, onClick = {})
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onEnterWaveMode()
                    },
                    onLongPress = {
                        onDragOffsetChange(0f)
                        onDraggingToCancelChange(false)
                        onDraggingToEditChange(false)
                        onShowDragHintsChange(true)
                        onStartVoiceCapture()
                    }
                )
            }
            .pointerInput(isRecording) {
                // 仅在录音时追踪拖动和释放
                if (!isRecording) return@pointerInput
                
                awaitPointerEventScope {
                    var previousPosition: Offset? = null
                    var currentOffset = 0f
                    
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        
                        if (change == null) break
                        
                        // 检查是否手指抬起
                        if (!change.pressed) {
                            // 释放时的 处理
                            onShowDragHintsChange(false)
                            when {
                                isDraggingToCancel.value -> {
                                    onStopVoiceCapture(true)
                                }
                                isDraggingToEdit.value -> {
                                    onEnterEditMode(userMessage)
                                }
                                else -> {
                                    onStopVoiceCapture(false)
                                }
                            }
                            break
                        }
                        
                        val position = change.position
                        
                        if (previousPosition == null) {
                            previousPosition = position
                        } else {
                            // 计算拖动偏移
                            val horizontalDrag = position.x - previousPosition.x
                            currentOffset += horizontalDrag
                            onDragOffsetChange(currentOffset)

                            val dragThreshold = 60f
                            when {
                                currentOffset > dragThreshold -> {
                                    onDraggingToCancelChange(true)
                                    onDraggingToEditChange(false)
                                }
                                currentOffset < -dragThreshold -> {
                                    onDraggingToEditChange(true)
                                    onDraggingToCancelChange(false)
                                }
                                else -> {
                                    onDraggingToCancelChange(false)
                                    onDraggingToEditChange(false)
                                }
                            }
                            
                            previousPosition = position
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 图标显示
        when {
            isRecording && isDraggingToCancel.value -> {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "取消录音",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            isRecording && isDraggingToEdit.value -> {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑录音",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "按住说话",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

