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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// 基础颜色定义
private val ColorMainBlue = Color(0xFF007AFF)
private val ColorDeepBlue = Color(0xFF0051D5)
private val ColorTextMain = Color(0xFF1A1A1A)
private val ColorTextSecondary = Color(0xFF666666)
private val ColorHint = Color(0xFF999999)
private val ColorBgGray = Color(0xFFF5F5F5)
private val ColorRedCancel = Color(0xFFFF3B30)
private val ColorWhite = Color.White
private val ColorOverlayMask = Color(0x99FFFFFF) // 半透明白色遮罩，让背景模糊隐约可见

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
    onAgentClick: () -> Unit,
    onModelSelect: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
    onUpdateCancelState: (Boolean) -> Unit = {}
) {
    // 状态为 Recording (录音中), Recognizing (识别中) 时显示全屏悬浮层
    val showVoiceOverlay = state is InputState.VoiceRecording || state is InputState.VoiceRecognizing
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay

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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            shape = RoundedCornerShape(24.dp),
            color = ColorWhite,
            shadowElevation = 4.dp
        ) {
            val sideSlotWidth = 72.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧等宽槽位：模式切换按钮
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = { onModeChange(!isVoiceMode) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isVoiceMode) Icons.Default.Keyboard else Icons.Default.Mic,
                            contentDescription = if (isVoiceMode) "切换键盘" else "语音输入",
                            tint = ColorTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 中间区域：文本输入框 或 "按住说话"按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isVoiceMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) with
                            fadeOut(animationSpec = tween(150))
                        },
                        label = "inputModeTransition"
                    ) { voiceMode ->
                        if (voiceMode) {
                            // 语音模式：显示"按住说话"按钮，需要居中显示
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "按住说话",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextMain
                                    )
                                )
                                
                                // 覆盖透明的触摸区域来处理长按逻辑
                                VoiceRecordButtonHandler(
                                    onPressStart = onVoiceStart,
                                    onPressEnd = onVoiceEnd,
                                    onCancel = onVoiceCancel,
                                    onOffsetChange = { offsetY, _ -> 
                                        val isCancelling = offsetY < -150f 
                                        onUpdateCancelState(isCancelling)
                                    }
                                )
                            }
                        } else {
                            // 文本模式：显示输入框
                            Box(
                                contentAlignment = Alignment.CenterStart, 
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "尽管问...",
                                        color = ColorHint,
                                        fontSize = 15.sp
                                    )
                                }
                                BasicTextField(
                                    value = text,
                                    onValueChange = onTextChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = ColorTextMain, fontSize = 15.sp),
                                    cursorBrush = SolidColor(ColorMainBlue)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧等宽槽位：附件 + 发送
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onAttachmentClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "附件",
                                tint = ColorTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 发送按钮 (圆形背景，黑色)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (text.isNotEmpty()) Color.Black else Color.LightGray.copy(alpha = 0.4f))
                                .clickable(enabled = text.isNotEmpty(), onClick = onSend),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_send_24),
                                contentDescription = "发送",
                                tint = Color.White,
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
        
        val waveColor = if (isCancelled) ColorRedCancel else ColorMainBlue
        
        // 模拟波形点
        VoiceWaveformDots(amplitude = if (isRecording) amplitude else 0f, color = waveColor)

        Spacer(modifier = Modifier.height(16.dp))

        // 提示文字
        Text(
            text = if (isCancelled) "松开取消" else "松开输入，上滑取消",
            fontSize = 14.sp,
            color = ColorTextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(ColorWhite.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp) 
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        totalDy = 0f
                        onPressStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDy += dragAmount.y
                        onOffsetChange(totalDy, totalDy < -100f)
                    },
                    onDragEnd = {
                        if (totalDy < -100f) {
                            onCancel()
                        } else {
                            onPressEnd()
                        }
                        totalDy = 0f
                    },
                    onDragCancel = {
                        onCancel()
                        totalDy = 0f
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
    tint: Color = ColorTextSecondary
) {
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
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
