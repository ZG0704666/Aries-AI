package com.ai.phoneagent.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    modifier: Modifier = Modifier,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = spacingMd),
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        if (items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            ) {
                Text(
                    text = stringResource(R.string.transcript_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(spacingMd),
                )
            }
            return@Column
        }

        items.forEach { item ->
            TranscriptMessageCard(
                item = item,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
                onAutomationAction = onAutomationAction,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptMessageCard(
    item: TranscriptMessageUi,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val maxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val containerColor =
        if (item.isUser) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        }
    val contentColor =
        if (item.isUser) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (item.isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = maxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(spacingMd)) {
                if (!item.isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text =
                                if (item.isStreaming) {
                                    "${item.author} - ${stringResource(R.string.message_streaming_label)}"
                                } else {
                                    item.author
                                },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (item.isAutomation) {
                            Spacer(modifier = Modifier.width(spacingXs))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.automation_scene_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                                )
                            }
                        }
                    }
                }

                if (!item.thinking.isNullOrBlank()) {
                    if (!item.isUser) {
                        Spacer(modifier = Modifier.height(spacingXs))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = spacingSm, bottom = spacingSm),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(spacingSm)) {
                            Text(
                                text = stringResource(R.string.message_thinking_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(spacingXs))
                            SelectionContainer {
                                Text(
                                    text = item.thinking,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (item.automation != null) {
                    AutomationMessageCard(
                        item = item,
                        automation = item.automation,
                        spacingXs = spacingXs,
                        spacingSm = spacingSm,
                        spacingMd = spacingMd,
                        onAutomationAction = onAutomationAction,
                    )
                } else if (item.body.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                        )
                    }
                }

                if (item.isUser && item.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacingXs))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = spacingSm),
                        horizontalArrangement = Arrangement.spacedBy(spacingXs),
                        verticalArrangement = Arrangement.spacedBy(spacingXs),
                    ) {
                        item.attachments.forEach { attachmentName ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = attachmentName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                                )
                            }
                        }
                    }
                }

                if (!item.isUser &&
                    !item.isStreaming &&
                    item.automation == null &&
                    (item.copyText.isNotBlank() || !item.retryText.isNullOrBlank())
                ) {
                    Spacer(modifier = Modifier.height(spacingXs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.copyText.isNotBlank()) {
                            TextButton(onClick = { onCopyMessage(item) }) {
                                Text(text = stringResource(R.string.common_copy))
                            }
                        }
                        if (!item.retryText.isNullOrBlank() && !item.isAutomation) {
                            TextButton(onClick = { onRetryMessage(item) }) {
                                Text(text = stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationMessageCard(
    item: TranscriptMessageUi,
    automation: TranscriptAutomationUi,
    spacingXs: androidx.compose.ui.unit.Dp,
    spacingSm: androidx.compose.ui.unit.Dp,
    spacingMd: androidx.compose.ui.unit.Dp,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacingMd)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = automation.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                )
            }
            Spacer(modifier = Modifier.height(spacingSm))
            Text(
                text = automation.command,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (automation.logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(spacingSm))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacingXs),
                ) {
                    automation.logs.forEach { logLine ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = spacingSm, vertical = spacingXs),
                            )
                        }
                    }
                }
            }
            automation.actionLabel?.let { label ->
                Spacer(modifier = Modifier.height(spacingSm))
                Button(
                    onClick = { onAutomationAction(item) },
                    enabled = automation.actionEnabled,
                    colors =
                        if (automation.isDestructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                disabledContainerColor =
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.64f),
                                disabledContentColor =
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.84f),
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
