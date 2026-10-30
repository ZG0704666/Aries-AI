package com.ai.phoneagent.ui.inputbar

import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.theme.LocalAriesBackdrop
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ScanLine
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import kotlin.math.abs

/** 语音待命呼吸辉光：在控件底部绘制径向光晕（随呼吸 alpha 摆动，仅重绘不重组）。 */
private fun Modifier.voiceBreathingGlow(
    glowAlpha: () -> Float,
    glowColor: Color,
    radius: Dp,
): Modifier = drawBehind {
    val glowRadius = radius.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = glowAlpha()), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = glowRadius,
        ),
        radius = glowRadius,
    )
}

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
    // 上滑取消手势上报：progress=上滑归一进度(0..1，阈值处=1)，isCancelling=是否已进入取消区
    onUpdateCancelState: (progress: Float, isCancelling: Boolean) -> Unit = { _, _ -> }
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
    val voiceRecordPillHeight = dimensionResource(R.dimen.m3t_voice_record_pill_height)
    val iconButtonSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_action)
    val iconSize = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.aries_composer_icon)
    val sendIconSize = dimensionResource(R.dimen.m3t_input_bar_send_icon_size) - spacingXxxs
    val inputShape = RoundedCornerShape(radiusXl)
    val showVoiceOverlay = state is InputState.VoiceRecording ||
        state is InputState.VoiceRecognizing || state is InputState.VoiceFinalizing
    val currentInputState by rememberUpdatedState(state)
    var pointerDown by remember { mutableStateOf(false) }
    // 外部（权限回调/看门狗）把状态复位时，清掉可能悬挂的按下标记，
    // 避免下一次长按被旧的 pointerDown 状态干扰。
    LaunchedEffect(state) {
        if (state is InputState.Idle || state is InputState.VoiceIdle) pointerDown = false
    }
    val completedWhileHolding = pointerDown && state is InputState.Idle
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay ||
        completedWhileHolding
    val voiceState = when {
        completedWhileHolding -> VoicePresentation.AwaitingRelease
        else -> voicePresentation(state)
    }
    val isGenerating = state is InputState.Generating
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val hasText = text.isNotBlank()
    val canSubmit = isGenerating || hasText || hasAttachments
    val showAssistantEntry = !canSubmit
    var renderedLineCount by remember { mutableIntStateOf(1) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val expansion = ((renderedLineCount - 1) / 2f).coerceIn(0f, 1f)
    val containerColor = Color.Transparent
    val actionContainerColor = Color.Transparent
    val sendContainerColor by animateColorAsState(
        targetValue =
            when {
                isGenerating -> colorScheme.error
                canSubmit -> colorScheme.primary
                else -> Color.Transparent
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
    // 语音待命呼吸辉光：单一合成值，底层仅触发重绘，不产生重组
    val breathingGlow = rememberInfiniteTransition(label = "voiceBreathingGlow")
    val glowAlpha by breathingGlow.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voiceBreathingGlowAlpha",
    )
    val breathingGlowRadius = dimensionResource(R.dimen.m3t_voice_breathing_glow_radius)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacingXxxs),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 录音中（按住说话/取消区）胶囊放大，待命/识别态维持常规语音高度
            val voicePillExpanded = voiceState == VoicePresentation.Listening ||
                voiceState == VoicePresentation.Cancelling
            val containerHeight by animateDpAsState(
                targetValue = when {
                    !isVoiceMode -> inputBarHeight
                    voicePillExpanded -> voiceRecordPillHeight
                    else -> inputBarVoiceHeight
                },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "inputBarContainerHeight",
            )
            // 录音胶囊底色：录音=蓝、取消区=红（参考图配色，深浅色走 m3t token），松手后渐回玻璃
            // 录音状态配色：本项目主题切换为 Compose 内部标志（不改资源配置），
            // values-night 限定符不会生效；按项目惯例以 LocalAriesBackdrop.dark 分支选择。
            val voiceDark = LocalAriesBackdrop.current.dark
            val recordActiveColor = colorResource(
                if (voiceDark) R.color.m3t_voice_record_pill_bg_dark else R.color.m3t_voice_record_pill_bg
            )
            val recordCancelColor = colorResource(
                if (voiceDark) {
                    R.color.m3t_voice_record_pill_bg_cancel_dark
                } else {
                    R.color.m3t_voice_record_pill_bg_cancel
                }
            )
            val recordHintColor = colorResource(
                if (voiceDark) R.color.m3t_voice_record_hint_dark else R.color.m3t_voice_record_hint
            )
            val voiceRecording = voiceState == VoicePresentation.Listening ||
                voiceState == VoicePresentation.Cancelling
            // 语音会话全程同一胶囊：录音/识别/收尾共用录音底色，仅取消区变红；
            // 停留在一个胶囊里完成“松手→识别→发送”，不再中途换成玻璃样式。
            val voicePillActive = voiceRecording ||
                voiceState == VoicePresentation.Recognizing ||
                voiceState == VoicePresentation.Finalizing
            val pillColor by animateColorAsState(
                targetValue = when (voiceState) {
                    VoicePresentation.Cancelling -> recordCancelColor
                    VoicePresentation.Listening,
                    VoicePresentation.Recognizing,
                    VoicePresentation.Finalizing,
                    -> recordActiveColor
                    else -> Color.Transparent
                },
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "voicePillColor",
            )
            // 上滑抬起上限：胶囊随上滑进度跟手抬起
            val voiceLiftMax = dimensionResource(R.dimen.m3t_voice_record_lift)
            // 手势层上报的原始上滑进度（0..1）。只在 graphicsLayer lambda 内读取：
            // 拖动过程逐帧更新只触发重绘，不触发重组。
            val cancelProgressState = remember { mutableFloatStateOf(0f) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg)
                    .widthIn(max = inputBarMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isVoiceMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                // 提示文字与胶囊整体跟随上滑进度抬起，
                                // 保证抬起过程中胶囊不会遮挡上方的提示文字。
                                translationY = -voiceLiftMax.toPx() * cancelProgressState.floatValue
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 状态大字：录音中提示“松手发送，上移取消”，进入取消区变红“松手取消”。
                        // 悬于胶囊上方居中，仅录音进行中显示（参考松手发送/取消两态图）。
                        AnimatedVisibility(
                            visible = voiceRecording,
                            enter = fadeIn(tween(140)) + slideInVertically(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            ) { it / 2 },
                            exit = fadeOut(tween(110)) + slideOutVertically(tween(110)) { it / 2 },
                            label = "voiceStatusHint",
                        ) {
                            val cancelling = voiceState == VoicePresentation.Cancelling
                            Text(
                                text = stringResource(
                                    if (cancelling) R.string.voice_release_to_cancel
                                    else R.string.voice_release_to_transcribe
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (cancelling) recordCancelColor else recordHintColor,
                                modifier = Modifier.padding(bottom = spacingSm),
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(containerHeight)
                                .testTag("voice_input_capsule")
                                .clip(inputShape)
                                .voiceBreathingGlow(
                                    glowAlpha = { glowAlpha },
                                    glowColor = colorScheme.primary,
                                    radius = breathingGlowRadius,
                                )
                                // 语音胶囊全程使用扁平状态底色；仅待命态用液态玻璃。
                                .then(if (voicePillActive) Modifier else Modifier.ariesGlass(inputShape)),
                            shape = inputShape,
                            color = pillColor,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(containerHeight)
                                    .padding(horizontal = spacingXs, vertical = spacingXs),
                                contentAlignment = Alignment.Center,
                            ) {
                                VoiceRecordButtonHandler(
                                    modifier = Modifier.padding(start = iconButtonSize + spacingXs),
                                    enabled = pointerDown || (voiceState != VoicePresentation.Recognizing &&
                                        voiceState != VoicePresentation.Finalizing),
                                    onPressStart = {
                                        pointerDown = true
                                        onVoiceStart()
                                    },
                                    onPressEnd = {
                                        pointerDown = false
                                        if (currentInputState !is InputState.Idle) onVoiceEnd()
                                    },
                                    onCancel = {
                                        pointerDown = false
                                        if (currentInputState !is InputState.Idle) {
                                            onVoiceCancel()
                                        }
                                    },
                                    onOffsetChange = { progress, cancelling ->
                                        cancelProgressState.floatValue = progress
                                        onUpdateCancelState(progress, cancelling)
                                    },
                                )

                                AnimatedContent(
                                    targetState = voiceState,
                                    transitionSpec = {
                                        fadeIn(tween(130)) togetherWith fadeOut(tween(90))
                                    },
                                    label = "voiceCapsuleContent",
                                ) { presentation ->
                                    when (presentation) {
                                        VoicePresentation.Idle -> Text(
                                            text = stringResource(R.string.input_hold_to_talk),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface,
                                        )
                                        VoicePresentation.Listening,
                                        VoicePresentation.Cancelling,
                                        -> Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            // 录音中：白色波形铺满状态色胶囊
                                            VoiceWaveform(
                                                amplitude = voiceAmplitude,
                                                solidColor = Color.White,
                                                spread = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(dimensionResource(R.dimen.m3t_voice_wave_height))
                                                    .testTag("voice_input_waveform"),
                                            )
                                        }
                                        else -> {
                                            // 识别/收尾：仍是同一状态胶囊。无转写时保持全宽白色波形；
                                            // 有转写则左侧小波形 + 实时转写 + 呼吸点（“正在识别”文案删除）。
                                            if (text.isBlank()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    VoiceWaveform(
                                                        amplitude = voiceAmplitude,
                                                        solidColor = Color.White,
                                                        spread = true,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(dimensionResource(R.dimen.m3t_voice_wave_height))
                                                            .testTag("voice_input_waveform"),
                                                    )
                                                }
                                            } else {
                                                val liveDot = rememberInfiniteTransition(label = "voiceFinalizingDot")
                                                val dotAlpha by liveDot.animateFloat(
                                                    initialValue = 0.35f,
                                                    targetValue = 1f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(700, easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse,
                                                    ),
                                                    label = "voiceFinalizingDotAlpha",
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = iconButtonSize, end = spacingXs)
                                                        .height(iconButtonSize),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(spacingXs),
                                                ) {
                                                    VoiceWaveform(
                                                        amplitude = voiceAmplitude,
                                                        solidColor = Color.White,
                                                        spread = false,
                                                        barCount = 3,
                                                        modifier = Modifier
                                                            .height(dimensionResource(R.dimen.m3t_voice_wave_height))
                                                            .testTag("voice_input_waveform"),
                                                    )
                                                    Text(
                                                        text = text,
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    // 呼吸点：持续可见的“仍在处理”信号。
                                                    Box(
                                                        Modifier
                                                            .size(spacingXs)
                                                            .graphicsLayer { alpha = dotAlpha }
                                                            .background(Color.White, CircleShape),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 长按录音/识别/收尾全程隐藏键盘切换按钮，避免浮在状态色胶囊上显得突兀；
                                // 仅语音待命态显示。
                                // （显式全限定调用：BoxScope 内存在外层 ColumnScope 隐式接收器，避免误解析到扩展重载）
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !voicePillActive,
                                    enter = fadeIn(tween(140)) +
                                        scaleIn(initialScale = 0.8f, animationSpec = tween(140)),
                                    exit = fadeOut(tween(110)) +
                                        scaleOut(targetScale = 0.8f, animationSpec = tween(110)),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    label = "voiceKeyboardToggle",
                                ) {
                                    InputBarIconButton(
                                        onClick = {
                                            pointerDown = false
                                            onModeChange(false)
                                        },
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
                        }
                    }
                } else {
                    // 只有一个持久文本框和一个外壳：焦点切换不会替换编辑器
                    // or splits the attachment/send controls into independent surfaces.
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            // 按动画边界绘制；放大镜生长过程中不要裁剪全尺寸镜头。
                            .ariesGlass(composerShape)
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
                                modifier = Modifier.ariesGlass(CircleShape)) {
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
                                modifier = Modifier.size(iconButtonSize).scale(sendButtonScale)
                                    .then(
                                        if (showAssistantEntry) Modifier
                                            .clip(CircleShape)
                                            .voiceBreathingGlow(
                                                glowAlpha = { glowAlpha },
                                                glowColor = colorScheme.primary,
                                                radius = breathingGlowRadius,
                                            )
                                            .ariesGlass(CircleShape)
                                        else Modifier
                                    ),
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

@Composable
fun VoiceRecordButtonHandler(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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

    val cancelEnterPx = -cancelEnterThreshold

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(gestureHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
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
                        // 上滑进度归一化：150dp 阈值处为 1.0（仅供视觉/反馈层使用）
                        val progress = (-totalDy / cancelEnterPx).coerceIn(0f, 1f)
                        onOffsetChange(progress, isCancelling)
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
