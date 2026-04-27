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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.RefreshCw
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ai.phoneagent.helper.AutomationMessageParser
import com.ai.phoneagent.ui.components.markdown.Markdown
import com.ai.phoneagent.ui.components.markdown.MarkdownSettings
import com.ai.phoneagent.ui.components.markdown.LocalMarkdownSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val DESC_DO_REGEX = Regex("""desc\s*=\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val DESC_JSON_REGEX = Regex("""\"desc\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val DESCRIPTION_JSON_REGEX = Regex("""\"description\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val FENCED_CODE_BLOCK_REGEX = Regex("(?s)```([\\w+-]*)\\n(.*?)```")
private const val CODE_BLOCK_COLLAPSE_LINE_THRESHOLD = 10

@Immutable
data class CodeBlockPrefs(
    val autoWrap: Boolean = true,
    val lineNumbers: Boolean = false,
    val autoCollapse: Boolean = false,
)

val LocalCodeBlockPrefs = compositionLocalOf { CodeBlockPrefs() }

private sealed interface MessageBodySegment {
    data class MarkdownText(
        val content: String,
    ) : MessageBodySegment

    data class CodeFence(
        val language: String?,
        val content: String,
    ) : MessageBodySegment
}

@Immutable
data class TranscriptMessageUi(
    val conversationId: Long,
    val messageIndex: Int,
    val id: String,
    val author: String,
    val body: String,
    val thinking: String?,
    val isUser: Boolean,
    val attachments: ImmutableList<String>,
    val isAutomation: Boolean,
    val automation: TranscriptAutomationUi? = null,
    val copyText: String,
    val retryText: String?,
    val isStreaming: Boolean = false,
    val thinkingDurationMs: Long? = null,
)

@Immutable
data class TranscriptAutomationUi(
    val command: String,
    val status: String,
    val logs: ImmutableList<String>,
    val actionLabel: String?,
    val actionEnabled: Boolean,
    val isDestructive: Boolean,
    val confirmInstruction: String?,
    val autoCollapseLogs: Boolean = false,
    val retryInstruction: String? = null,
    val secondaryActionLabel: String? = null,
    val secondaryActionEnabled: Boolean = false,
    /** 当系统未就绪时为 true，secondaryActionLabel 是"去开启"按钮 */
    val openSetupAction: Boolean = false,
)

fun LazyListScope.conversationTranscriptItems(
    items: ImmutableList<TranscriptMessageUi>,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    onEditMessage: (TranscriptMessageUi) -> Unit = {},
    codeBlockPrefs: CodeBlockPrefs = CodeBlockPrefs(),
) {
    if (items.isEmpty()) {
        item(key = "transcript_empty_hint", contentType = "empty_hint") {
            val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
            val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacingMd),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingMd + spacingSm),
                    verticalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    Icon(
                        imageVector = Lucide.Lightbulb,
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
        }
        return
    }

    items(
        items = items,
        key = { it.id },
        contentType = {
            when {
                it.isUser -> "user_message"
                it.automation != null -> "automation_message"
                !it.thinking.isNullOrBlank() -> "thinking_section"
                else -> "assistant_message"
            }
        },
    ) { item ->
        val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacingSm / 2),
        ) {
            if (item.isUser) {
                UserMessageBubble(
                    item = item,
                    onCopyMessage = onCopyMessage,
                    onRetryMessage = onRetryMessage,
                    onEditMessage = onEditMessage,
                )
            } else {
                CompositionLocalProvider(
                    LocalCodeBlockPrefs provides codeBlockPrefs,
                    LocalMarkdownSettings provides MarkdownSettings(
                        autoWrap     = codeBlockPrefs.autoWrap,
                        lineNumbers  = codeBlockPrefs.lineNumbers,
                        autoCollapse = codeBlockPrefs.autoCollapse,
                    ),
                ) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserMessageBubble(
    item: TranscriptMessageUi,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val bubbleMaxWidth = dimensionResource(R.dimen.m3t_message_user_bubble_max_width)
    val actionGap = dimensionResource(R.dimen.m3t_message_action_gap)
    val actionButtonSize = dimensionResource(R.dimen.m3t_message_action_button_size)
    val actionIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(item.body) { mutableStateOf(item.body) }
    var showActions by remember(item.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showActions = !showActions },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = bubbleMaxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 1.dp,
        ) {
            if (isEditing) {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .widthIn(max = bubbleMaxWidth)
                        .padding(horizontal = spacingXs, vertical = spacingXs),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors =
                        TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    shape = MaterialTheme.shapes.large,
                    minLines = 2,
                    maxLines = 8,
                )
            } else {
                SelectionContainer {
                    Text(
                        text = item.body,
                        modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = (showActions || isEditing) && !item.isStreaming && item.attachments.isEmpty(),
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(animationSpec = tween(100)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(150, easing = FastOutSlowInEasing),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(actionGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditing) {
                    MessageActionButton(
                        onClick = {
                            if (editText.isNotBlank()) {
                                onEditMessage(item.copy(body = editText.trim()))
                                isEditing = false
                            }
                        },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = stringResource(R.string.automation_confirm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                    MessageActionButton(
                        onClick = {
                            editText = item.body
                            isEditing = false
                        },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                } else {
                    MessageActionButton(
                        onClick = { onCopyMessage(item) },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Lucide.Copy,
                            contentDescription = stringResource(R.string.common_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                    MessageActionButton(
                        onClick = { onRetryMessage(item) },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Lucide.RefreshCw,
                            contentDescription = stringResource(R.string.retry),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                    MessageActionButton(
                        onClick = { isEditing = true },
                        buttonSize = actionButtonSize,
                    ) {
                        Icon(
                            imageVector = Lucide.Pencil,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize),
                        )
                    }
                }
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
    val codeBlockPrefs = LocalCodeBlockPrefs.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        if (item.automation == null) {
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
                    if (item.isStreaming) {
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacingMd, vertical = spacingMd),
                        )
                    } else {
                        Markdown(
                            text = item.body,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacingMd, vertical = spacingMd),
                        )
                    }
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION")
        if (false) { // kept for reference – no longer called
            val bodySegments = remember(item.body) { parseMessageBodySegments(item.body) }
            bodySegments.forEachIndexed { index, segment ->
                when (segment) {
                    is MessageBodySegment.MarkdownText -> { /* replaced by Markdown() above */ }
                    is MessageBodySegment.CodeFence    -> { /* replaced by Markdown() above */ }
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
                            imageVector = Lucide.Copy,
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
                            imageVector = Lucide.RefreshCw,
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

private fun parseMessageBodySegments(content: String): List<MessageBodySegment> {
    val segments = mutableListOf<MessageBodySegment>()
    var cursor = 0

    FENCED_CODE_BLOCK_REGEX.findAll(content).forEach { match ->
        val start = match.range.first
        if (start > cursor) {
            val markdown = content.substring(cursor, start)
            if (markdown.isNotBlank()) {
                segments += MessageBodySegment.MarkdownText(markdown)
            }
        }

        val language = match.groupValues[1].trim().ifBlank { null }
        val code = match.groupValues[2].trimEnd('\n', '\r')
        segments += MessageBodySegment.CodeFence(language = language, content = code)
        cursor = match.range.last + 1
    }

    if (cursor < content.length) {
        val markdownTail = content.substring(cursor)
        if (markdownTail.isNotBlank()) {
            segments += MessageBodySegment.MarkdownText(markdownTail)
        }
    }

    return if (segments.isEmpty()) {
        listOf(MessageBodySegment.MarkdownText(content))
    } else {
        segments
    }
}

@Composable
private fun CodeBlockSegment(
    language: String?,
    code: String,
    blockKey: String,
    prefs: CodeBlockPrefs,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val lines = remember(code) { if (code.isEmpty()) listOf("") else code.split('\n') }
    val lineNumberWidth = remember(lines.size) { lines.size.toString().length }
    val canCollapse = prefs.autoCollapse && lines.size > CODE_BLOCK_COLLAPSE_LINE_THRESHOLD
    var expanded by rememberSaveable(blockKey, prefs.autoCollapse, code) {
        mutableStateOf(!canCollapse)
    }
    val visibleLines = if (expanded) lines else lines.take(CODE_BLOCK_COLLAPSE_LINE_THRESHOLD)

    Column(
        verticalArrangement = Arrangement.spacedBy(spacingXs),
    ) {
        SelectionContainer {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingSm, vertical = spacingSm),
                    verticalArrangement = Arrangement.spacedBy(spacingXs),
                ) {
                    if (!language.isNullOrBlank()) {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    visibleLines.forEachIndexed { index, line ->
                        if (prefs.lineNumbers) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = (index + 1).toString().padStart(lineNumberWidth, ' ') + "|",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = line.ifEmpty { " " },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = prefs.autoWrap,
                                )
                            }
                        } else {
                            Text(
                                text = line.ifEmpty { " " },
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = prefs.autoWrap,
                            )
                        }
                    }
                }
            }
        }

        if (canCollapse) {
            Button(
                onClick = { expanded = !expanded },
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(
                    text =
                        if (expanded) {
                            stringResource(R.string.automation_scene_collapse)
                        } else {
                            stringResource(R.string.automation_scene_expand)
                        },
                )
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
                    enter = fadeIn(animationSpec = tween(180)) + expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                    ),
                    exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ),
                ) {
                    SelectionContainer {
                        if (item.isStreaming) {
                            Text(
                                text = thinking,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Markdown(
                                text = thinking,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
                imageVector = Lucide.ChevronDown,
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
    val chipIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)
    val logBlocks = remember(automation.logs.size, automation.logs.lastOrNull()) {
        buildAutomationLogBlocks(automation.logs)
    }
    val actionDescription =
        remember(logBlocks) {
            logBlocks.firstNotNullOfOrNull { block ->
                block.summaryText?.takeIf { !block.fromThinking && it.isNotBlank() }
            }
        }
    val actionChips =
        remember(logBlocks) {
            logBlocks
                .asSequence()
                .filter { !it.fromThinking }
                .flatMap { it.actionChips.asSequence() }
                .toList()
        }
    val (statusContainerColor, statusContentColor) = resolveAutomationStatusColors(
        status = automation.status,
        isDestructive = automation.isDestructive,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacingMd),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusContainerColor,
                ) {
                    Text(
                        text = automation.status,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusContentColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                    )
                }
                Text(
                    text = automation.command,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }

            actionDescription?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (actionChips.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    AutomationActionChips(
                        chips = actionChips,
                        iconSize = chipIconSize,
                    )
                }
            }

            if (automation.actionLabel != null || automation.secondaryActionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    automation.actionLabel?.let { label ->
                        Button(
                            onClick = { onAutomationAction(item) },
                            modifier = Modifier.weight(1f),
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
                    automation.secondaryActionLabel?.let { label ->
                        FilledTonalButton(
                            onClick = { onAutomationAction(item) },
                            modifier = if (automation.actionLabel != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            enabled = automation.secondaryActionEnabled,
                        ) {
                            Text(text = label)
                        }
                    }
                }
            }
        }
    }
}

private data class AutomationActionChipUi(
    val label: String,
    val iconRes: Int,
)

private data class AutomationLogBlockUi(
    val summaryText: String?,
    val fromThinking: Boolean,
    val actionChips: List<AutomationActionChipUi> = emptyList(),
)

private data class MutableAutomationLogBlock(
    var summaryText: String? = null,
    val fromThinking: Boolean,
    val actionChips: MutableList<AutomationActionChipUi> = mutableListOf(),
)

private fun buildAutomationLogBlocks(logs: List<String>): List<AutomationLogBlockUi> {
    val blocks = mutableListOf<MutableAutomationLogBlock>()
    var currentBlockIndex = -1

    logs.forEach { line ->
        val text = AutomationMessageParser.normalizeAutomationLogLine(line)
        if (text.isEmpty()) return@forEach
        when {
            text.startsWith("思考：") -> {
                val summary = text.substringAfter("思考：").trim()
                if (summary.isNotBlank()) {
                    blocks += MutableAutomationLogBlock(summaryText = summary, fromThinking = true)
                    currentBlockIndex = blocks.lastIndex
                }
            }

            text.startsWith("修复思考：") -> {
                val summary = text.substringAfter("修复思考：").trim()
                if (summary.isNotBlank()) {
                    blocks += MutableAutomationLogBlock(summaryText = summary, fromThinking = true)
                    currentBlockIndex = blocks.lastIndex
                }
            }

            text.startsWith("输出：") || text.startsWith("修复输出：") -> {
                val desc = extractDescFromOutputLine(text) ?: return@forEach
                if (currentBlockIndex < 0) {
                    blocks += MutableAutomationLogBlock(summaryText = desc, fromThinking = false)
                    currentBlockIndex = blocks.lastIndex
                } else {
                    val currentBlock = blocks[currentBlockIndex]
                    if (!currentBlock.fromThinking && currentBlock.actionChips.isEmpty()) {
                        currentBlock.summaryText = desc
                    }
                }
            }

            text.startsWith("当前动作：") -> {
                val actionText = text.substringAfter("当前动作：").trim()
                if (actionText.isNotBlank()) {
                    if (currentBlockIndex < 0) {
                        blocks += MutableAutomationLogBlock(fromThinking = false)
                        currentBlockIndex = blocks.lastIndex
                    }
                    blocks[currentBlockIndex].actionChips += parseActionChip(actionText)
                }
            }
        }
    }

    return blocks
        .map { block ->
            AutomationLogBlockUi(
                summaryText = block.summaryText,
                fromThinking = block.fromThinking,
                actionChips = block.actionChips.toList(),
            )
        }
        .filter { !it.summaryText.isNullOrBlank() || it.actionChips.isNotEmpty() }
}

private fun extractDescFromOutputLine(normalizedLine: String): String? {
     val payload =
         when {
             normalizedLine.startsWith("输出：") -> normalizedLine.substringAfter("输出：").trim()
             normalizedLine.startsWith("修复输出：") -> normalizedLine.substringAfter("修复输出：").trim()
             else -> return null
         }
     if (payload.isBlank()) return null
 
     val descFromDo =
         DESC_DO_REGEX
             .find(payload)
             ?.groupValues
             ?.getOrNull(1)
             ?.trim()
     if (!descFromDo.isNullOrBlank()) return descFromDo
 
     val descFromJson =
         DESC_JSON_REGEX
             .find(payload)
             ?.groupValues
             ?.getOrNull(1)
             ?.trim()
     if (!descFromJson.isNullOrBlank()) return descFromJson
 
     val descriptionFromJson =
         DESCRIPTION_JSON_REGEX
             .find(payload)
             ?.groupValues
             ?.getOrNull(1)
             ?.trim()
     return descriptionFromJson?.takeIf { it.isNotBlank() }
 }

private fun parseActionChip(actionText: String): AutomationActionChipUi {
    val normalized = actionText.lowercase()
    return when {
        normalized.contains("launch") || actionText.contains("启动") || actionText.contains("打开") -> {
            AutomationActionChipUi("Launch", R.drawable.ic_home_24)
        }
        normalized.contains("tap") || normalized.contains("click") || actionText.contains("点击") -> {
            AutomationActionChipUi("点击", R.drawable.ic_check_circle_24)
        }
        normalized.contains("back") || actionText.contains("返回") -> {
            AutomationActionChipUi("返回", R.drawable.ic_arrow_back_24)
        }
        normalized.contains("type") || normalized.contains("input") || actionText.contains("输入") -> {
            AutomationActionChipUi("输入", R.drawable.ic_key_24)
        }
        normalized.contains("swipe") || actionText.contains("滑") -> {
            AutomationActionChipUi("滑动", R.drawable.ic_history_arrow_reverse_24)
        }
        else -> AutomationActionChipUi(actionText.take(10), R.drawable.ic_check_circle_24)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationActionChips(
    chips: List<AutomationActionChipUi>,
    iconSize: Dp,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacingXs),
        verticalArrangement = Arrangement.spacedBy(spacingXs),
    ) {
        chips.forEach { chip ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacingXs),
                ) {
                    Icon(
                        painter = painterResource(chip.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun resolveAutomationStatusColors(
    status: String,
    isDestructive: Boolean,
): Pair<Color, Color> {
    if (isDestructive) {
        return MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val normalized = status.lowercase()
    return when {
        normalized.contains("失败") || normalized.contains("error") || normalized.contains("终止") -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        normalized.contains("完成") || normalized.contains("已结束") || normalized.contains("已执行") || normalized.contains("success") -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
        normalized.contains("执行") || normalized.contains("运行") || normalized.contains("处理中") || normalized.contains("running") -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
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
