package com.ai.phoneagent.ui.inputbar

import com.ai.phoneagent.R
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ScanLine
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Plus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    hasAttachments: Boolean = false,
    agentModeEnabled: Boolean,
    modelName: String = "",
    onAgentToggle: (Boolean) -> Unit,
    onModelSelect: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
    onUpdateCancelState: (Boolean) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val spacingXxxs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xxxs)
    val spacingXs = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val radiusXl = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_radius)
    val inputBarMaxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val inputBarHeight = dimensionResource(R.dimen.m3t_input_bar_height)
    val inputBarVoiceHeight = dimensionResource(R.dimen.m3t_input_bar_voice_height)
    val iconButtonSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_action)
    val iconSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_icon)
    val sendIconSize = dimensionResource(R.dimen.m3t_input_bar_send_icon_size) - spacingXxxs
    val inputShape = RoundedCornerShape(radiusXl)
    val showVoiceOverlay = state is InputState.VoiceRecording || state is InputState.VoiceRecognizing
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay
    val isGenerating = state is InputState.Generating
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val hasText = text.isNotBlank()
    val canSubmit = isGenerating || hasText || hasAttachments
    val showAssistantEntry = !canSubmit
    var renderedLineCount by remember { mutableIntStateOf(1) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val expansion = ((renderedLineCount - 1) / 2f).coerceIn(0f, 1f)
    val actionContainerColor = colorResource(R.color.m3t_input_bar_action_bg)
    val inputBarStroke = dimensionResource(R.dimen.m3t_input_bar_stroke)
    val inputBarElevation = dimensionResource(R.dimen.m3t_input_bar_elevation)
    val sendContainerColor by animateColorAsState(
        targetValue =
            when {
                isGenerating -> colorScheme.error
                canSubmit -> colorScheme.primary
                else -> actionContainerColor
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "inputBarSendContainerColor",
    )
    val sendContentColor by animateColorAsState(
        targetValue =
            when {
                isGenerating -> colorScheme.onError
                canSubmit -> colorScheme.onPrimary
                else -> colorScheme.onSurface
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "inputBarSendContentColor",
    )
    val sendButtonScale by animateFloatAsState(
        targetValue = if (showAssistantEntry || canSubmit) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "inputBarSendButtonScale",
    )
    val composerMotionMs = integerResource(com.ai.phoneagent.core.designsystem.R.integer.aries_page_motion_ms)
    val expandedRadius = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_expanded_radius)
    val composerRadius by animateDpAsState(
        radiusXl + (expandedRadius - radiusXl) * expansion,
        tween(composerMotionMs, easing = FastOutSlowInEasing), label = "composerRadius",
    )
    val composerShape = RoundedCornerShape(composerRadius)

    LaunchedEffect(imeVisible, isVoiceMode) {
        if (isVoiceMode) {
            imeWasVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            focusManager.clearFocus(force = true)
            imeWasVisible = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacingLg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingXs)) {
            TextButton(onClick = { onAgentToggle(!agentModeEnabled) },
                contentPadding = PaddingValues(horizontal = spacingSm),
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)) {
                Icon(Lucide.ScanLine, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_icon)))
                Spacer(Modifier.width(spacingXs))
                Text(stringResource(if (agentModeEnabled) R.string.ui_v2_agent_on else R.string.ui_v2_agent_off), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onModelSelect, contentPadding = PaddingValues(horizontal = spacingXs),
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.onSurfaceVariant)) {
                Text(modelName, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Lucide.ChevronDown, null, Modifier.size(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_nav_icon)))
            }
        }
        AnimatedVisibility(
            visible = showVoiceOverlay,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            VoiceInputOverlayContent(
                amplitude = voiceAmplitude,
                inputState = state,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacingXxxs),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val containerHeight by animateDpAsState(
                targetValue = if (isVoiceMode) inputBarVoiceHeight else inputBarHeight,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "inputBarContainerHeight",
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg)
                    .widthIn(max = inputBarMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isVoiceMode) {
                    // 语音输入框上浮在消息之上：改用不透明底板 + 描边，
                    // 去掉透明玻璃，避免背景消息透出来影响可读性。
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(containerHeight),
                        shape = inputShape,
                        color = colorScheme.surfaceContainer,
                        border = BorderStroke(inputBarStroke, colorScheme.outlineVariant),
                        shadowElevation = inputBarElevation,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(containerHeight)
                                .padding(horizontal = spacingXs, vertical = spacingXs),
                            contentAlignment = Alignment.Center,
                        ) {
                            VoiceRecordButtonHandler(
                                onPressStart = onVoiceStart,
                                onPressEnd = onVoiceEnd,
                                onCancel = onVoiceCancel,
                                onOffsetChange = { _, isCancelling ->
                                    onUpdateCancelState(isCancelling)
                                },
                            )

                            Text(
                                text = stringResource(R.string.input_hold_to_talk),
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )

                            InputBarIconButton(
                                onClick = { onModeChange(false) },
                                modifier = Modifier.align(Alignment.CenterStart),
                                buttonSize = iconButtonSize,
                                iconSize = iconSize,
                                containerColor = actionContainerColor,
                            ) {
                                Icon(
                                    imageVector = Lucide.Keyboard,
                                    contentDescription = stringResource(R.string.input_switch_keyboard),
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize),
                                )
                            }
                        }
                    }
                } else {
                    // 只有一个持久文本框和一个外壳：焦点切换不会替换编辑器
                    // or splits the attachment/send controls into independent surfaces.
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            // 按动画边界绘制；放大镜生长过程中不要裁剪全尺寸镜头。
                            // 主页输入框上浮在消息之上：不透明底板 + 描边，去掉透明玻璃。
                            .shadow(inputBarElevation, composerShape, clip = false)
                            .clip(composerShape)
                            .background(colorScheme.surfaceContainer)
                            .border(BorderStroke(inputBarStroke, colorScheme.outlineVariant), composerShape)
                            .animateContentSize(tween(composerMotionMs, easing = FastOutSlowInEasing), alignment = Alignment.BottomCenter),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = inputBarHeight)
                                .padding(horizontal = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_inset_x),
                                    vertical = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_inset_y)),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            InputBarIconButton(onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onAttachmentClick()
                            },
                                buttonSize = iconButtonSize, iconSize = iconSize,
                                containerColor = actionContainerColor) {
                                Icon(Lucide.Plus, stringResource(R.string.input_attachment), Modifier.size(iconSize), tint = colorScheme.onSurface)
                            }
                            BasicTextField(
                                value = text, onValueChange = onTextChange,
                                modifier = Modifier.weight(1f).padding(horizontal = spacingSm)
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                                cursorBrush = SolidColor(colorScheme.primary), maxLines = 4,
                                onTextLayout = { renderedLineCount = it.lineCount.coerceIn(1, 4) },
                                decorationBox = { innerTextField ->
                                    Box(Modifier.fillMaxWidth().heightIn(min = iconButtonSize).padding(vertical = spacingSm),
                                        contentAlignment = Alignment.CenterStart) {
                                        if (text.isEmpty()) Text(stringResource(if (hasAttachments) R.string.input_attachment_ready else R.string.input_hint),
                                            style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                        innerTextField()
                                    }
                                },
                            )
                            PrimaryInputBarActionButton(
                                onClick = if (showAssistantEntry) { {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onModeChange(true)
                                } } else onSend,
                                modifier = Modifier.size(iconButtonSize).scale(sendButtonScale),
                                containerColor = sendContainerColor, contentColor = sendContentColor,
                            ) {
                                if (showAssistantEntry) Icon(Lucide.Mic, stringResource(R.string.voice_input), Modifier.size(iconSize))
                                else Icon(painterResource(if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24),
                                    stringResource(if (isGenerating) R.string.input_stop_generating else R.string.send), Modifier.size(sendIconSize))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 语音状态条固定展示在输入栏上方，避免模式切换时主布局跳变。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VoiceInputOverlayContent(
    amplitude: Float,
    inputState: InputState,
) {
    val colorScheme = MaterialTheme.colorScheme
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_md)
    val overlayBottomOffset = dimensionResource(R.dimen.m3t_input_bar_overlay_bottom_offset)
    val statusPaddingH = dimensionResource(R.dimen.m3t_input_bar_voice_status_padding_h)
    val statusPaddingV = dimensionResource(R.dimen.m3t_input_bar_voice_status_padding_v)
    val isRecording = inputState is InputState.VoiceRecording || inputState is InputState.VoiceRecognizing
    val isCancelled = (inputState as? InputState.VoiceRecording)?.isCancelling == true
    val statusText =
        when {
            inputState is InputState.VoiceRecognizing -> stringResource(R.string.voice_status_recognizing)
            else -> stringResource(R.string.voice_status_listening)
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = overlayBottomOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 语音输入框上浮在消息之上：不透明底板保证波形与状态文字始终可读。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colorScheme.surface,
            border = BorderStroke(
                dimensionResource(R.dimen.m3t_input_bar_stroke),
                colorScheme.outlineVariant,
            ),
            shadowElevation = dimensionResource(R.dimen.m3t_input_bar_elevation),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isCancelled) colorScheme.errorContainer else colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isCancelled) colorScheme.onErrorContainer else colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = statusPaddingH, vertical = statusPaddingV),
                    )
                }

                Spacer(modifier = Modifier.height(spacingSm))

                val waveColor = if (isCancelled) colorScheme.error else colorScheme.primary

                VoiceWaveformDots(amplitude = if (isRecording) amplitude else 0f, color = waveColor)

                Spacer(modifier = Modifier.height(spacingMd))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = if (isCancelled) {
                            stringResource(R.string.voice_release_to_cancel)
                        } else {
                            stringResource(R.string.voice_release_to_send)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = statusPaddingH, vertical = statusPaddingV),
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceRecordButtonHandler(
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onCancel: () -> Unit,
    onOffsetChange: (Float, Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val cancelEnterThreshold = with(density) {
        -dimensionResource(R.dimen.m3t_input_bar_voice_cancel_enter_offset).toPx()
    }
    val cancelExitThreshold = with(density) {
        -dimensionResource(R.dimen.m3t_input_bar_voice_cancel_exit_offset).toPx()
    }
    val gestureHeight = dimensionResource(R.dimen.m3t_input_bar_voice_height)
    var totalDy by remember { mutableStateOf(0f) }
    var isLongPressConfirmed by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var activePointerId by remember { mutableStateOf<PointerId?>(null) }

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
            .height(gestureHeight)
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
                    },
                )
            },
    )
}

@Composable
fun VoiceWaveformDots(amplitude: Float, color: Color) {
    val dotCount = 8
    val dotGap = dimensionResource(R.dimen.m3t_voice_wave_dot_gap)
    val waveHeight = dimensionResource(R.dimen.m3t_voice_wave_height)
    val dotSize = dimensionResource(R.dimen.m3t_voice_wave_dot_size)
    Row(
        horizontalArrangement = Arrangement.spacedBy(dotGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(waveHeight),
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
                label = "dot",
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun InputBarIconButton(
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun PrimaryInputBarActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    brush: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(brush = brush ?: Brush.linearGradient(listOf(containerColor, containerColor)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
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
            .size(dimensionResource(R.dimen.m3t_input_bar_icon_button_size))
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xs)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(dimensionResource(R.dimen.m3t_input_bar_icon_size)),
        )
    }
}
