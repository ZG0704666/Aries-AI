package com.ai.phoneagent.ui.inputbar

import com.ai.phoneagent.R
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun InputBar(
    state: InputState,
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    onVoiceCancel: () -> Unit,
    onAttachmentClick: () -> Unit,
    agentModeEnabled: Boolean,
    onAgentToggle: (Boolean) -> Unit,
    onModelSelect: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
    onUpdateCancelState: (Boolean) -> Unit = {}
) {
    // 基础颜色定义（统一从 MaterialTheme 动态获取）
    val colorScheme = MaterialTheme.colorScheme
    val colorTextMain = colorScheme.onSurface
    val colorTextSecondary = colorScheme.onSurfaceVariant
    val colorHint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val colorInputField = colorScheme.surfaceVariant.copy(alpha = 0.9f)
    val colorButtonDisabled = colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val colorButtonEnabled = colorScheme.primary
    val colorButtonIcon = colorScheme.onPrimary
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)

    // 状态为 Recording (录音中), Recognizing (识别中) 时显示全屏悬浮层
    val showVoiceOverlay = state is InputState.VoiceRecording || state is InputState.VoiceRecognizing
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay
    val isGenerating = state is InputState.Generating

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // 语音输入时的波形显示区域 - 直接嵌入在输入栏上方，不遮挡全屏
        AnimatedVisibility(
            visible = showVoiceOverlay,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            VoiceInputOverlayContent(
                isVisible = true, // 由父容器控制可见性
                amplitude = voiceAmplitude,
                inputState = state
            )
        }

        // 底部常驻栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacingMd, top = 0.dp, end = spacingMd, bottom = spacingXxxs)
        ) {
            val containerHeight by animateDpAsState(
                targetValue = if (isVoiceMode) 48.dp else 52.dp,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "inputBarContainerHeight"
            )
            val containerHorizontalPadding by animateDpAsState(
                targetValue = if (isVoiceMode) 14.dp else spacingXs,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "inputBarContainerPadding"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingXs)
                    .height(containerHeight)
                    .clip(RoundedCornerShape(30.dp))
                    .background(colorInputField)
                    .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                    .padding(horizontal = containerHorizontalPadding, vertical = spacingSm),
                contentAlignment = if (isVoiceMode) Alignment.Center else Alignment.CenterStart
            ) {
                if (isVoiceMode) {
                    // 语音模式：按住说话区域扩展到整个底栏，键盘图标融入按钮内部
                    VoiceRecordButtonHandler(
                        onPressStart = onVoiceStart,
                        onPressEnd = onVoiceEnd,
                        onCancel = onVoiceCancel,
                        onOffsetChange = { offsetY, _ ->
                            val isCancelling = offsetY < -150f
                            onUpdateCancelState(isCancelling)
                        }
                    )

                    Text(
                        text = "按住说话",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorTextMain
                        )
                    )

                    IconButton(
                        onClick = { onModeChange(false) },
                        modifier = Modifier.align(Alignment.CenterStart).size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "切换键盘",
                            tint = colorTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    // 文本模式：输入框扩展为整条底栏，操作图标内嵌到输入框
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onModeChange(true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "语音输入",
                                tint = colorTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(spacingXs))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 32.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "尽管问...",
                                    color = colorHint,
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = colorTextMain, fontSize = 15.sp),
                                cursorBrush = SolidColor(colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.width(spacingXs))

                        IconButton(
                            onClick = onAttachmentClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "附件",
                                tint = colorTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isGenerating -> colorScheme.error
                                        text.isNotEmpty() -> colorButtonEnabled
                                        else -> colorButtonDisabled
                                    }
                                )
                                .clickable(
                                    enabled = isGenerating || text.isNotEmpty(),
                                    onClick = onSend
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24
                                ),
                                contentDescription = if (isGenerating) "终止生成" else "发送",
                                tint = if (isGenerating) colorScheme.onError else colorButtonIcon,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 这是一个叠加层组件，应该放在 UI 树的顶层 (例如 Scaffold 或者 Box)，覆盖整个屏幕内容。
 * 它不再使用 Popup，而是作为一个全屏的 Overlay 直接叠加在内容之上。
 * 背景使用半透明遮罩，而不是全白。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VoiceInputOverlayContent(
    isVisible: Boolean,
    amplitude: Float,
    inputState: InputState
) {
    val colorScheme = MaterialTheme.colorScheme
    val isRecording = inputState is InputState.VoiceRecording || inputState is InputState.VoiceRecognizing
    val isCancelled = (inputState as? InputState.VoiceRecording)?.isCancelling == true
    
    // 改为内容自适应高度，不再全屏覆盖
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent) // 透明背景
            .padding(bottom = 80.dp), // 为底部的输入栏留出空间
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        val waveColor = if (isCancelled) colorScheme.error else colorScheme.primary
        
        // 模拟波形点
        VoiceWaveformDots(amplitude = if (isRecording) amplitude else 0f, color = waveColor)

        Spacer(modifier = Modifier.height(16.dp))

        // 提示文字
        Text(
            text = if (isCancelled) "松开取消" else "松开输入，上滑取消",
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        // 移除了底部大色块，保持简洁
    }
}

@Composable
fun VoiceRecordButtonHandler(
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onCancel: () -> Unit,
    onOffsetChange: (Float, Boolean) -> Unit
) {
    var totalDy by remember { mutableStateOf(0f) }
    var isLongPressConfirmed by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var activePointerId by remember { mutableStateOf<PointerId?>(null) }
    val cancelEnterThreshold = -150f
    val cancelExitThreshold = -110f

    fun resetGestureState() {
        totalDy = 0f
        isLongPressConfirmed = false
        isCancelling = false
        activePointerId = null
        onOffsetChange(0f, false)
    }

    fun finishGesture(cancelBySystem: Boolean) {
        if (!isLongPressConfirmed) {
            resetGestureState()
            return
        }
        if (cancelBySystem || isCancelling) {
            onCancel()
        } else {
            onPressEnd()
        }
        resetGestureState()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp) 
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        totalDy = 0f
                        isLongPressConfirmed = true
                        isCancelling = false
                        activePointerId = null
                        onOffsetChange(0f, false)
                        onPressStart()
                    },
                    onDrag = { change, dragAmount ->
                        if (!isLongPressConfirmed) return@detectDragGesturesAfterLongPress
                        if (activePointerId == null) {
                            activePointerId = change.id
                        }
                        if (activePointerId != change.id) return@detectDragGesturesAfterLongPress
                        change.consume()
                        totalDy += dragAmount.y

                        isCancelling =
                            when {
                                isCancelling && totalDy > cancelExitThreshold -> false
                                !isCancelling && totalDy < cancelEnterThreshold -> true
                                else -> isCancelling
                            }
                        onOffsetChange(totalDy, isCancelling)
                    },
                    onDragEnd = {
                        finishGesture(cancelBySystem = false)
                    },
                    onDragCancel = {
                        finishGesture(cancelBySystem = true)
                    }
                )
            }
    )
}

@Composable
fun VoiceWaveformDots(amplitude: Float, color: Color) {
    val dotCount = 8 
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp) 
    ) {
        repeat(dotCount) { index ->
            val startScale = 0.6f
            val targetScale = if (amplitude > 0.05f) {
                val centerFactor = 1f - abs(index - dotCount / 2f) / (dotCount / 2f)
                startScale + (amplitude * 2f * centerFactor) + (Random.nextFloat() * 0.3f)
            } else {
                startScale
            }

            val animatedScale by animateFloatAsState(
                targetValue = targetScale.coerceIn(0.6f, 2.5f),
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "dot"
            )
            
            Box(
                modifier = Modifier
                    .size(10.dp) 
                    .scale(animatedScale) 
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun IconButtonWithRipple(
    onClick: () -> Unit,
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val resolvedTint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp), 
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
