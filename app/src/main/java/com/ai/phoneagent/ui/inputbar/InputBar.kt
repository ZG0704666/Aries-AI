package com.ai.phoneagent.ui.inputbar

import com.ai.phoneagent.R
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    agentModeEnabled: Boolean,
    onAgentToggle: (Boolean) -> Unit,
    onModelSelect: () -> Unit,
    onModeChange: (Boolean) -> Unit,
    voiceAmplitude: Float = 0f,
    modifier: Modifier = Modifier,
    onUpdateCancelState: (Boolean) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val radiusLg = dimensionResource(R.dimen.m3t_radius_lg)
    val inputBarMaxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val inputBarHeight = dimensionResource(R.dimen.m3t_input_bar_height)
    val inputBarVoiceHeight = dimensionResource(R.dimen.m3t_input_bar_voice_height)
    val iconButtonSize = dimensionResource(R.dimen.m3t_input_bar_icon_button_size)
    val iconSize = dimensionResource(R.dimen.m3t_input_bar_icon_size)
    val sendButtonSize = dimensionResource(R.dimen.m3t_input_bar_send_button_size)
    val sendIconSize = dimensionResource(R.dimen.m3t_input_bar_send_icon_size)
    val textMinHeight = dimensionResource(R.dimen.m3t_input_bar_text_min_height)
    val inputShape = RoundedCornerShape(radiusLg)
    val showVoiceOverlay = state is InputState.VoiceRecording || state is InputState.VoiceRecognizing
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay
    val isGenerating = state is InputState.Generating
    val canSend = isGenerating || text.isNotBlank()
    val containerColor = colorScheme.surfaceContainerHigh
    val tertiaryContainerColor = colorScheme.surfaceContainer

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg)
                    .widthIn(max = inputBarMaxWidth)
                    .heightIn(min = containerHeight)
                    .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
                shape = inputShape,
                color = containerColor,
            ) {
                if (isVoiceMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(containerHeight)
                            .padding(horizontal = spacingSm, vertical = spacingXs),
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
                        ) {
                            Icon(
                                imageVector = Lucide.Keyboard,
                                contentDescription = stringResource(R.string.input_switch_keyboard),
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacingSm, vertical = spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InputBarIconButton(
                            onClick = { onModeChange(true) },
                            buttonSize = iconButtonSize,
                            iconSize = iconSize,
                        ) {
                            Icon(
                                imageVector = Lucide.Mic,
                                contentDescription = stringResource(R.string.voice_input),
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize),
                            )
                        }

                        Spacer(modifier = Modifier.width(spacingXs))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = textMinHeight),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.input_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                                cursorBrush = SolidColor(colorScheme.primary),
                            )
                        }

                        Spacer(modifier = Modifier.width(spacingXs))

                        InputBarIconButton(
                            onClick = onAttachmentClick,
                            buttonSize = iconButtonSize,
                            iconSize = iconSize,
                        ) {
                            Icon(
                                imageVector = Lucide.Plus,
                                contentDescription = stringResource(R.string.input_attachment),
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(sendButtonSize)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isGenerating -> colorScheme.error
                                        text.isNotBlank() -> colorScheme.primary
                                        else -> tertiaryContainerColor
                                    },
                                )
                                .clickable(
                                    enabled = canSend,
                                    onClick = onSend,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24,
                                ),
                                contentDescription = if (isGenerating) {
                                    stringResource(R.string.input_stop_generating)
                                } else {
                                    stringResource(R.string.send)
                                },
                                tint = if (isGenerating) colorScheme.onError else {
                                    if (text.isNotBlank()) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(sendIconSize),
                            )
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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
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
            .background(Color.Transparent)
            .padding(bottom = overlayBottomOffset),
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
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
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
            .padding(dimensionResource(R.dimen.m3t_spacing_xs)),
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
