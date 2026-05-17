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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val radiusXl = dimensionResource(R.dimen.m3t_radius_xl)
    val inputBarMaxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val inputBarHeight = dimensionResource(R.dimen.m3t_input_bar_height) - spacingXs
    val inputBarVoiceHeight = dimensionResource(R.dimen.m3t_input_bar_voice_height) - spacingXs
    val iconButtonSize = dimensionResource(R.dimen.m3t_input_bar_icon_button_size) - spacingXs
    val iconSize = dimensionResource(R.dimen.m3t_input_bar_icon_size) - spacingXxxs
    val outerButtonSize = inputBarHeight
    val collapsedPrimaryButtonSize = outerButtonSize - spacingXs - spacingXxxs
    val sendIconSize = dimensionResource(R.dimen.m3t_input_bar_send_icon_size) - spacingXxxs
    val textMinHeight = dimensionResource(R.dimen.m3t_input_bar_text_min_height) - spacingXs
    val inputShape = RoundedCornerShape(radiusXl)
    val showVoiceOverlay = state is InputState.VoiceRecording || state is InputState.VoiceRecognizing
    val isVoiceMode = state is InputState.VoiceIdle || showVoiceOverlay
    val isGenerating = state is InputState.Generating
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val hasText = text.isNotBlank()
    val canSubmit = isGenerating || hasText || hasAttachments
    val showAssistantEntry = !canSubmit
    var wantsExpandedComposer by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isComposerExpanded = wantsExpandedComposer || isTextFieldFocused
    val containerColor = colorScheme.surfaceContainerHigh
    val actionContainerColor = colorScheme.surfaceContainerHighest
    val collapsedDisplayText =
        when {
            hasText -> text.replace("\n", " ").trim()
            hasAttachments -> stringResource(R.string.input_attachment_ready)
            else -> stringResource(R.string.input_hint)
        }
    val collapsedDisplayColor = if (canSubmit) colorScheme.onSurface else colorScheme.onSurfaceVariant
    val sendContainerColor by animateColorAsState(
        targetValue =
            when {
                isGenerating -> colorScheme.error
                canSubmit -> colorScheme.primary
                else -> colorScheme.primary
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "inputBarSendContainerColor",
    )
    val sendContentColor by animateColorAsState(
        targetValue =
            when {
                isGenerating -> colorScheme.onError
                canSubmit -> colorScheme.onPrimary
                else -> colorScheme.onPrimary
            },
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "inputBarSendContentColor",
    )
    val sendButtonScale by animateFloatAsState(
        targetValue = if (showAssistantEntry || canSubmit) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "inputBarSendButtonScale",
    )
    val assistantEntryBrush = remember(colorScheme) {
        Brush.horizontalGradient(
            colors = listOf(colorScheme.primary, colorScheme.secondary),
        )
    }

    LaunchedEffect(isComposerExpanded, isVoiceMode) {
        if (isVoiceMode) {
            wantsExpandedComposer = false
            isTextFieldFocused = false
            imeWasVisible = false
            return@LaunchedEffect
        }
        if (isComposerExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(imeVisible, isVoiceMode) {
        if (isVoiceMode) {
            imeWasVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            collapseComposer(
                focusManager = focusManager,
                onFocusChanged = { isTextFieldFocused = it },
                onExpandedChanged = { wantsExpandedComposer = it },
            )
            imeWasVisible = false
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg)
                    .widthIn(max = inputBarMaxWidth)
                    .animateContentSize(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (isVoiceMode) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(containerHeight),
                        shape = inputShape,
                        color = containerColor,
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
                    AnimatedContent(
                        targetState = isComposerExpanded,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            val enterTransition =
                                fadeIn(
                                    animationSpec = tween(durationMillis = 240, delayMillis = 40, easing = FastOutSlowInEasing),
                                ) + scaleIn(
                                    initialScale = 0.985f,
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                )
                            val exitTransition =
                                fadeOut(
                                    animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                                ) + scaleOut(
                                    targetScale = 0.985f,
                                    animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                                )

                            enterTransition.togetherWith(exitTransition).using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ ->
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        )
                                    },
                                ),
                            )
                        },
                        label = "inputBarComposerAnimatedContent",
                    ) { expanded ->
                        if (expanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                InputBarIconButton(
                                    onClick = onAttachmentClick,
                                    modifier = Modifier.animateEnterExit(
                                        enter = slideInHorizontally(
                                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                            initialOffsetX = { -it / 3 },
                                        ) + fadeIn(
                                            animationSpec = tween(durationMillis = 220, delayMillis = 30),
                                        ),
                                        exit = slideOutHorizontally(
                                            animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
                                            targetOffsetX = { -it / 4 },
                                        ) + fadeOut(
                                            animationSpec = tween(durationMillis = 90),
                                        ),
                                    ),
                                    buttonSize = outerButtonSize,
                                    iconSize = iconSize,
                                    containerColor = actionContainerColor,
                                ) {
                                    Icon(
                                        imageVector = Lucide.Plus,
                                        contentDescription = stringResource(R.string.input_attachment),
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(iconSize),
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = containerHeight)
                                        .clip(inputShape)
                                        .background(containerColor)
                                        .padding(horizontal = spacingMd, vertical = spacingXs)
                                        .animateEnterExit(
                                            enter = fadeIn(
                                                animationSpec = tween(durationMillis = 220, delayMillis = 50),
                                            ) + expandHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                                expandFrom = Alignment.Start,
                                            ),
                                            exit = fadeOut(
                                                animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing),
                                            ) + shrinkHorizontally(
                                                animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                                                shrinkTowards = Alignment.Start,
                                            ),
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BasicTextField(
                                        value = text,
                                        onValueChange = onTextChange,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = textMinHeight)
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { focusState ->
                                                isTextFieldFocused = focusState.isFocused
                                                if (!focusState.isFocused && !imeVisible) {
                                                    collapseComposer(
                                                        focusManager = focusManager,
                                                        onFocusChanged = { isTextFieldFocused = it },
                                                        onExpandedChanged = { wantsExpandedComposer = it },
                                                    )
                                                }
                                            },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                                        cursorBrush = SolidColor(colorScheme.primary),
                                        minLines = 1,
                                        maxLines = 4,
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = spacingSm, vertical = spacingSm),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                if (text.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.input_hint),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        },
                                    )
                                }

                                PrimaryInputBarActionButton(
                                    onClick = if (showAssistantEntry) {
                                        { onModeChange(true) }
                                    } else {
                                        onSend
                                    },
                                    modifier = Modifier
                                        .animateEnterExit(
                                            enter = slideInHorizontally(
                                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                                initialOffsetX = { it / 3 },
                                            ) + fadeIn(
                                                animationSpec = tween(durationMillis = 220, delayMillis = 30),
                                            ),
                                            exit = slideOutHorizontally(
                                                animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
                                                targetOffsetX = { it / 4 },
                                            ) + fadeOut(
                                                animationSpec = tween(durationMillis = 90),
                                            ),
                                        )
                                        .size(outerButtonSize)
                                        .scale(sendButtonScale),
                                    containerColor = sendContainerColor,
                                    contentColor = sendContentColor,
                                    brush = if (showAssistantEntry) assistantEntryBrush else null,
                                ) {
                                    if (showAssistantEntry) {
                                        Icon(
                                            imageVector = Lucide.Mic,
                                            contentDescription = stringResource(R.string.voice_input),
                                            tint = sendContentColor,
                                            modifier = Modifier.size(iconSize),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24,
                                            ),
                                            contentDescription = if (isGenerating) {
                                                stringResource(R.string.input_stop_generating)
                                            } else {
                                                stringResource(R.string.send)
                                            },
                                            tint = sendContentColor,
                                            modifier = Modifier.size(sendIconSize),
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = containerHeight)
                                    .animateEnterExit(
                                        enter = fadeIn(
                                            animationSpec = tween(durationMillis = 220, delayMillis = 40, easing = FastOutSlowInEasing),
                                        ) + scaleIn(
                                            initialScale = 0.99f,
                                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                                        ),
                                        exit = fadeOut(
                                            animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing),
                                        ) + scaleOut(
                                            targetScale = 0.99f,
                                            animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                                        ),
                                    ),
                                shape = inputShape,
                                color = containerColor,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacingMd, vertical = spacingXs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    InputBarIconButton(
                                        onClick = onAttachmentClick,
                                        buttonSize = iconButtonSize,
                                        iconSize = iconSize,
                                        containerColor = Color.Transparent,
                                    ) {
                                        Icon(
                                            imageVector = Lucide.Plus,
                                            contentDescription = stringResource(R.string.input_attachment),
                                            tint = colorScheme.onSurface,
                                            modifier = Modifier.size(iconSize),
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                wantsExpandedComposer = true
                                            }
                                            .padding(horizontal = spacingXs, vertical = spacingSm),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            text = collapsedDisplayText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = collapsedDisplayColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    PrimaryInputBarActionButton(
                                        onClick = if (showAssistantEntry) {
                                            { onModeChange(true) }
                                        } else {
                                            onSend
                                        },
                                        modifier = Modifier
                                            .size(collapsedPrimaryButtonSize)
                                            .scale(sendButtonScale),
                                        containerColor = sendContainerColor,
                                        contentColor = sendContentColor,
                                        brush = if (showAssistantEntry) assistantEntryBrush else null,
                                    ) {
                                        if (showAssistantEntry) {
                                            Icon(
                                                imageVector = Lucide.Mic,
                                                contentDescription = stringResource(R.string.voice_input),
                                                tint = sendContentColor,
                                                modifier = Modifier.size(iconSize),
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(
                                                    id = if (isGenerating) R.drawable.ic_stop_24 else R.drawable.ic_send_24,
                                                ),
                                                contentDescription = if (isGenerating) {
                                                    stringResource(R.string.input_stop_generating)
                                                } else {
                                                    stringResource(R.string.send)
                                                },
                                                tint = sendContentColor,
                                                modifier = Modifier.size(sendIconSize),
                                            )
                                        }
                                    }
                                }
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

private fun collapseComposer(
    focusManager: FocusManager,
    onFocusChanged: (Boolean) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
) {
    focusManager.clearFocus(force = true)
    onFocusChanged(false)
    onExpandedChanged(false)
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
