package com.ai.phoneagent.ui.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R

data class TranscriptMessageUi(
    val conversationId: Long,
    val messageIndex: Int,
    val id: String,
    val author: String,
    val body: String,
    val thinking: String?,
    val isUser: Boolean,
    val attachments: List<String>,
    val isAutomation: Boolean,
    val automation: TranscriptAutomationUi? = null,
    val copyText: String,
    val retryText: String?,
    val isStreaming: Boolean = false,
    val thinkingDurationMs: Long? = null,
)

data class TranscriptAutomationUi(
    val command: String,
    val status: String,
    val logs: List<String>,
    val actionLabel: String?,
    val actionEnabled: Boolean,
    val isDestructive: Boolean,
    val confirmInstruction: String?,
)

@Composable
fun ConversationTranscript(
    items: List<TranscriptMessageUi>,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacingMd),
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        if (items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingMd + spacingSm),
                    verticalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.transcript_empty_hint),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.input_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        items.forEach { item ->
            if (item.isUser) {
                UserMessageBubble(item = item)
            } else {
                AssistantMessageBlock(
                    item = item,
                    thinkingExpandedByDefault = thinkingExpandedByDefault,
                    onCopyMessage = onCopyMessage,
                    onRetryMessage = onRetryMessage,
                    onAutomationAction = onAutomationAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserMessageBubble(item: TranscriptMessageUi) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val bubbleMaxWidth = dimensionResource(R.dimen.m3t_message_user_bubble_max_width)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = bubbleMaxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 1.dp,
        ) {
            SelectionContainer {
                Text(
                    text = item.body,
                    modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (item.attachments.isNotEmpty()) {
                    FlowRow(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(spacingXs),
                verticalArrangement = Arrangement.spacedBy(spacingXs),
            ) {
                item.attachments.forEach { attachmentName ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            text = attachmentName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageBlock(
    item: TranscriptMessageUi,
    thinkingExpandedByDefault: Boolean,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val maxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val actionGap = dimensionResource(R.dimen.m3t_message_action_gap)
    val actionButtonSize = dimensionResource(R.dimen.m3t_message_action_button_size)
    val actionIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Text(
                text = item.author,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.isStreaming) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.message_streaming_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                    )
                }
            }
        }

        ThinkingSection(
            item = item,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
        )

        if (item.automation != null) {
            AutomationMessageCard(
                item = item,
                automation = item.automation,
                onAutomationAction = onAutomationAction,
            )
        } else if (item.body.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (item.isStreaming) 2.dp else 1.dp,
            ) {
                SelectionContainer {
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingMd),
                    )
                }
            }
        }

        if (!item.isStreaming &&
            item.automation == null &&
            (item.copyText.isNotBlank() || !item.retryText.isNullOrBlank())
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(actionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.copyText.isNotBlank()) {
                    MessageActionButton(
                        onClick = { onCopyMessage(item) },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.common_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                }
                if (!item.retryText.isNullOrBlank()) {
                    MessageActionButton(
                        onClick = { onRetryMessage(item) },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.retry),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingSection(
    item: TranscriptMessageUi,
    thinkingExpandedByDefault: Boolean,
) {
    val thinking = item.thinking?.trim().orEmpty()
    if (thinking.isEmpty()) return

    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val thinkingIconBox = dimensionResource(R.dimen.m3t_message_thinking_icon_box_size)
    val actionButtonSize = dimensionResource(R.dimen.m3t_message_action_button_size)
    val actionIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)
    var expanded by rememberSaveable(item.id) {
        mutableStateOf(item.isStreaming)
    }
    var wasStreaming by rememberSaveable(item.id) {
        mutableStateOf(item.isStreaming)
    }

    LaunchedEffect(item.id, item.isStreaming) {
        if (item.isStreaming && !wasStreaming) {
            expanded = true
        } else if (!item.isStreaming && wasStreaming) {
            expanded = false
        }
        wasStreaming = item.isStreaming
    }

    val thinkingLabel =
        item.thinkingDurationMs?.let { durationMs ->
            stringResource(
                R.string.message_thinking_duration_format,
                durationMs / 1000f,
            )
        } ?: if (item.isStreaming) {
            stringResource(R.string.message_thinking_in_progress)
        } else {
            stringResource(R.string.message_thinking_label)
        }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "thinkingArrowRotation",
    )
    var bodyVisible by rememberSaveable(item.id) {
        mutableStateOf(expanded)
    }
    var hasExpandedOnce by rememberSaveable(item.id) {
        mutableStateOf(expanded)
    }

    LaunchedEffect(item.id, expanded) {
        if (expanded) {
            hasExpandedOnce = true
            kotlinx.coroutines.delay(150)
            bodyVisible = true
        } else {
            bodyVisible = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        var collapsedWidthPx by rememberSaveable(item.id, thinkingLabel) { mutableStateOf(0) }
        val expandedWidth = maxWidth
        val collapsedWidth =
            if (collapsedWidthPx > 0) {
                with(density) { collapsedWidthPx.toDp() }
            } else {
                Dp.Unspecified
            }
        val needsCollapsedBootstrap = !expanded && collapsedWidth == Dp.Unspecified
        val shouldAnimateWidth = expanded || hasExpandedOnce
        val animatedWidth =
            if (needsCollapsedBootstrap) {
                collapsedWidth
            } else {
                val targetWidth = if (expanded) expandedWidth else collapsedWidth
                animateDpAsState(
                    targetValue = targetWidth,
                    animationSpec =
                        if (shouldAnimateWidth) {
                            tween(durationMillis = 320, easing = FastOutSlowInEasing)
                        } else {
                            snap()
                        },
                    label = "thinkingCardWidth",
                ).value
            }

        ThinkingCollapsedMeasure(
            thinkingLabel = thinkingLabel,
            thinkingIconBox = thinkingIconBox,
            actionButtonSize = actionButtonSize,
            actionIconSize = actionIconSize,
            spacingSm = spacingSm,
            spacingMd = spacingMd,
            spacingXs = spacingXs,
            onMeasured = { measuredWidth -> collapsedWidthPx = measuredWidth },
        )

        val surfaceModifier =
            if (needsCollapsedBootstrap) {
                Modifier.wrapContentWidth()
            } else {
                Modifier.width(animatedWidth)
            }
        val contentWidthModifier = if (needsCollapsedBootstrap) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()

        Surface(
            modifier =
                surfaceModifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .then(contentWidthModifier)
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
                        )
                        .padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
                verticalArrangement = Arrangement.spacedBy(spacingXs + spacingSm),
            ) {
                ThinkingHeaderRow(
                    thinkingLabel = thinkingLabel,
                    thinkingIconBox = thinkingIconBox,
                    actionButtonSize = actionButtonSize,
                    actionIconSize = actionIconSize,
                    spacingSm = spacingSm,
                    arrowRotation = arrowRotation,
                    onToggle = { expanded = !expanded },
                    expandLabel = !needsCollapsedBootstrap,
                    modifier = contentWidthModifier,
                )

                AnimatedVisibility(
                    visible = bodyVisible,
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                ) {
                    SelectionContainer {
                        Text(
                            text = thinking,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingCollapsedMeasure(
    thinkingLabel: String,
    thinkingIconBox: Dp,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    spacingSm: Dp,
    spacingMd: Dp,
    spacingXs: Dp,
    onMeasured: (Int) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .wrapContentWidth()
                .graphicsLayer { alpha = 0f }
                .onSizeChanged { onMeasured(it.width) },
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        ThinkingHeaderRow(
            thinkingLabel = thinkingLabel,
            thinkingIconBox = thinkingIconBox,
            actionButtonSize = actionButtonSize,
            actionIconSize = actionIconSize,
            spacingSm = spacingSm,
            arrowRotation = 0f,
            onToggle = {},
            expandLabel = false,
            modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
        )
    }
}

@Composable
private fun ThinkingHeaderRow(
    thinkingLabel: String,
    thinkingIconBox: Dp,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    spacingSm: Dp,
    arrowRotation: Float,
    onToggle: () -> Unit,
    expandLabel: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Box(
            modifier = Modifier.size(thinkingIconBox),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cognition_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(actionIconSize),
            )
        }
        Text(
            text = thinkingLabel,
            modifier = if (expandLabel) Modifier.weight(1f) else Modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            modifier = Modifier.size(actionButtonSize),
            onClick = onToggle,
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .size(actionIconSize)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                        },
            )
        }
    }
}

@Composable
private fun AutomationMessageCard(
    item: TranscriptMessageUi,
    automation: TranscriptAutomationUi,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacingMd),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Text(
                text = automation.status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = automation.command,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (automation.logs.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacingXs),
                ) {
                    automation.logs.forEach { logLine ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                            )
                        }
                    }
                }
            }
            automation.actionLabel?.let { label ->
                Button(
                    onClick = { onAutomationAction(item) },
                    enabled = automation.actionEnabled,
                    colors =
                        if (automation.isDestructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.64f),
                                disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.84f),
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                ) {
                    Text(text = label)
                }
            }
        }
    }
}

@Composable
private fun MessageActionButton(
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.wrapContentWidth(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            content = { content() },
        )
    }
}
