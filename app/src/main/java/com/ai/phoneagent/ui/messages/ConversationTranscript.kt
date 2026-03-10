package com.ai.phoneagent.ui.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val thinkingCardWidth = dimensionResource(R.dimen.m3t_message_thinking_card_max_width)
    val thinkingIconBox = dimensionResource(R.dimen.m3t_message_thinking_icon_box_size)
    val actionButtonSize = dimensionResource(R.dimen.m3t_message_action_button_size)
    val actionIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)
    var expanded by rememberSaveable(item.id) {
        mutableStateOf(thinkingExpandedByDefault)
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

    Surface(
        modifier = Modifier.widthIn(max = thinkingCardWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
            verticalArrangement = Arrangement.spacedBy(spacingXs + spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Box(
                    modifier = Modifier.size(thinkingIconBox),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(actionIconSize),
                    )
                }
                Text(
                    text = thinkingLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    modifier = Modifier.size(actionButtonSize),
                    onClick = {
                        expanded = !expanded
                    },
                ) {
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Outlined.KeyboardArrowUp
                            } else {
                                Icons.Outlined.KeyboardArrowDown
                            },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(actionIconSize),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut(),
            ) {
                SelectionContainer {
                    Text(
                        text = thinking,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.widthIn(max = thinkingCardWidth - spacingMd - spacingMd),
                    )
                }
            }
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
