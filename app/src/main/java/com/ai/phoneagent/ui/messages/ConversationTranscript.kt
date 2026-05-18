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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample

private val DESC_DO_REGEX = Regex("""desc\s*=\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val DESC_JSON_REGEX = Regex("""\"desc\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val DESCRIPTION_JSON_REGEX = Regex("""\"description\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
private val FENCED_CODE_BLOCK_REGEX = Regex("(?s)```([\\w+-]*)\\n(.*?)```")
private const val CODE_BLOCK_COLLAPSE_LINE_THRESHOLD = 10
private const val STREAMING_MARKDOWN_RENDER_INTERVAL_MS = 80L
private const val STREAMING_MARKDOWN_MIN_CHUNK_DELTA = 12
private const val STREAMING_PENDING_INLINE_TAIL_LIMIT = 32
private const val TRANSCRIPT_EMPTY_SUGGESTION_PAGE_SIZE = 3
private const val TRANSCRIPT_EMPTY_SUGGESTION_ROTATE_DELAY_MS = 4200L
private val STREAMING_MARKDOWN_ORDERED_LIST_RE = Regex("""(?m)^\s*\d+\.\s""")
private val STREAMING_MARKDOWN_FENCE_LINE_RE = Regex("""(?m)^[ \t]*`{3,}.*$""")
private val STREAMING_MARKDOWN_STRUCTURE_RE = Regex(
    """(?m)(^#{1,6}\s|^\s*[-*+]\s|^\s*\d+\.\s|^\s*>\s|`|\*\*|__|\]\(|\$\$|\|)"""
)
private val STREAMING_DANGLING_BLOCK_MARKER_RE = Regex("""^\s{0,3}(?:#{1,6}|[-*+]|\d+\.)\s?$""")

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
private data class TranscriptEmptySuggestionItem(
    val label: String,
    val prompt: String,
    val icon: ImageVector,
)

@Immutable
data class StreamingTranscriptBodyPreview(
    val usesMarkdownPreview: Boolean,
    val committedBlocks: ImmutableList<String>,
    val committedPrefixLength: Int,
    val tailText: String,
    val renderMarkdownText: String,
    val showLoadingIndicator: Boolean,
    val fullTextLength: Int,
    val layoutVersion: Int,
)

@Stable
class StreamingTranscriptMessageState(
    val conversationId: Long,
    val messageIndex: Int,
    val id: String,
    val author: String,
    val retryText: String?,
    initialBody: String,
    initialThinking: String?,
    initialCopyText: String,
    initialBodyPreview: StreamingTranscriptBodyPreview = buildStreamingTranscriptBodyPreview(initialBody),
) {
    private var bodyText: String = initialBody

    var bodyPreview by mutableStateOf(initialBodyPreview)
        private set

    var thinking by mutableStateOf(initialThinking)
        private set

    var copyText by mutableStateOf(initialCopyText)
        private set

    val bodyLength: Int
        get() = bodyPreview.fullTextLength

    val bodyLayoutVersion: Int
        get() = bodyPreview.layoutVersion

    val thinkingLength: Int
        get() = thinking?.length ?: 0

    val thinkingLayoutVersion: Int
        get() = computeStreamingTextLayoutVersion(thinking.orEmpty())

    fun update(
        nextBody: String,
        nextThinking: String?,
        nextCopyText: String,
        nextBodyPreview: StreamingTranscriptBodyPreview? = null,
    ) {
        if (bodyText != nextBody) {
            bodyText = nextBody
            val resolvedBodyPreview = nextBodyPreview ?: buildStreamingTranscriptBodyPreview(nextBody)
            val selectedBodyPreview =
                selectStreamingBodyPreview(
                    current = bodyPreview,
                    next = resolvedBodyPreview,
                )
            if (bodyPreview != selectedBodyPreview) {
                bodyPreview = selectedBodyPreview
            }
        } else if (nextBodyPreview != null && bodyPreview != nextBodyPreview) {
            bodyPreview = nextBodyPreview
        }
        if (thinking != nextThinking) {
            thinking = nextThinking
        }
        if (copyText != nextCopyText) {
            copyText = nextCopyText
        }
    }
}

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
    items(
        items = items,
        key = { it.id },
        contentType = { transcriptItemContentType(it) },
    ) { item ->
        TranscriptMessageListItem(
            item = item,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )
    }
}

fun LazyListScope.conversationTranscriptItem(
    item: TranscriptMessageUi,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    onEditMessage: (TranscriptMessageUi) -> Unit = {},
    codeBlockPrefs: CodeBlockPrefs = CodeBlockPrefs(),
) {
    item(
        key = item.id,
        contentType = transcriptItemContentType(item),
    ) {
        TranscriptMessageListItem(
            item = item,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )
    }
}

fun LazyListScope.conversationTranscriptItem(
    item: StreamingTranscriptMessageState,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    onEditMessage: (TranscriptMessageUi) -> Unit = {},
    codeBlockPrefs: CodeBlockPrefs = CodeBlockPrefs(),
) {
    item(
        key = item.id,
        contentType = streamingTranscriptItemContentType(item),
    ) {
        StreamingTranscriptMessageListItem(
            itemState = item,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )
    }
}

private fun transcriptItemContentType(item: TranscriptMessageUi): String =
    when {
        item.isUser -> "user_message"
        item.automation != null -> "automation_message"
        !item.thinking.isNullOrBlank() -> "thinking_section"
        else -> "assistant_message"
    }

private fun streamingTranscriptItemContentType(itemState: StreamingTranscriptMessageState): String =
    if (!itemState.thinking.isNullOrBlank()) {
        "thinking_section"
    } else {
        "assistant_message"
    }

@Composable
private fun TranscriptMessageListItem(
    item: TranscriptMessageUi,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    codeBlockPrefs: CodeBlockPrefs,
) {
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
                    autoWrap = codeBlockPrefs.autoWrap,
                    lineNumbers = codeBlockPrefs.lineNumbers,
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

@Composable
private fun StreamingTranscriptMessageListItem(
    itemState: StreamingTranscriptMessageState,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    thinkingExpandedByDefault: Boolean,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    codeBlockPrefs: CodeBlockPrefs,
) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacingSm / 2),
    ) {
        CompositionLocalProvider(
            LocalCodeBlockPrefs provides codeBlockPrefs,
            LocalMarkdownSettings provides MarkdownSettings(
                autoWrap = codeBlockPrefs.autoWrap,
                lineNumbers = codeBlockPrefs.lineNumbers,
                autoCollapse = codeBlockPrefs.autoCollapse,
            ),
        ) {
            StreamingAssistantMessageBlock(
                itemState = itemState,
                thinkingExpandedByDefault = thinkingExpandedByDefault,
            )
        }
    }
}

@Composable
fun TranscriptEmptyHintCard(
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val appIconSize = dimensionResource(R.dimen.m3t_about_icon_card_size)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val emptyHintCardMaxWidth = dimensionResource(R.dimen.m3t_transcript_empty_card_max_width)
    val suggestionIconSize = spacingLg
    val suggestionArrowSize = dimensionResource(R.dimen.m3t_input_bar_icon_size)
    val cardElevation = dimensionResource(R.dimen.m3t_message_streaming_tonal_elevation)
    val brandIconSize = appIconSize - spacingXs
    val suggestions = remember(context) {
        val suggestionIcons = listOf(
            Lucide.Search,
            Lucide.Sparkles,
            Lucide.MessageCircle,
        )
        context.resources
            .getStringArray(R.array.transcript_suggestion_examples)
            .toList()
            .take(suggestionIcons.size)
            .mapIndexed { index, prompt ->
                TranscriptEmptySuggestionItem(
                    label = prompt,
                    prompt = prompt,
                    icon = suggestionIcons.getOrElse(index) { Lucide.Sparkles },
                )
            }
    }
    val suggestionIconColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = emptyHintCardMaxWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacingXl),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.size(brandIconSize),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacingXs),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.transcript_empty_brand_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = cardElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg, vertical = spacingLg),
                verticalArrangement = Arrangement.spacedBy(spacingMd),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    Icon(
                        imageVector = Lucide.Sparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(suggestionIconSize),
                    )
                    Text(
                        text = stringResource(R.string.transcript_empty_suggestion_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(suggestion.prompt) }
                                .padding(vertical = spacingMd),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacingSm),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Icon(
                                    imageVector = suggestion.icon,
                                    contentDescription = null,
                                    tint = suggestionIconColors[index % suggestionIconColors.size],
                                    modifier = Modifier.padding(spacingSm).size(suggestionIconSize),
                                )
                            }

                            Text(
                                text = suggestion.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(suggestionArrowSize),
                            )
                        }
                    }
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
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)
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
            tonalElevation = messageElevation,
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
                Text(
                    text = item.body,
                    modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = messageElevation,
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
    val transcriptBlockGap = dimensionResource(R.dimen.m3t_transcript_block_gap)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(transcriptBlockGap + spacingXs),
    ) {
        if (item.automation == null) {
            AssistantMessageHeader(
                author = item.author,
                isStreaming = item.isStreaming,
                spacingSm = spacingSm,
                spacingXs = spacingXs,
            )
        }

        ThinkingSection(
            messageId = item.id,
            thinking = item.thinking,
            thinkingDurationMs = item.thinkingDurationMs,
            isStreaming = item.isStreaming,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
        )

        if (item.automation != null) {
            AutomationMessageCard(
                item = item,
                automation = item.automation,
                onAutomationAction = onAutomationAction,
            )
        } else if (item.body.isNotBlank()) {
            AssistantMessageBody(
                body = item.body,
                isStreaming = item.isStreaming,
                spacingMd = spacingMd,
            )
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
            AssistantMessageActions(
                item = item,
                actionGap = actionGap,
                actionButtonSize = actionButtonSize,
                actionIconSize = actionIconSize,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
            )
        }
    }
}

@Composable
private fun StreamingAssistantMessageBlock(
    itemState: StreamingTranscriptMessageState,
    thinkingExpandedByDefault: Boolean,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val maxWidth = dimensionResource(R.dimen.m3t_input_bar_max_width)
    val transcriptBlockGap = dimensionResource(R.dimen.m3t_transcript_block_gap)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(transcriptBlockGap + spacingXs),
    ) {
        AssistantMessageHeader(
            author = itemState.author,
            isStreaming = true,
            spacingSm = spacingSm,
            spacingXs = spacingXs,
        )

        StreamingAssistantThinkingSection(
            itemState = itemState,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
        )

        StreamingAssistantBodySection(itemState = itemState)
    }
}

@Composable
private fun StreamingAssistantThinkingSection(
    itemState: StreamingTranscriptMessageState,
    thinkingExpandedByDefault: Boolean,
) {
    ThinkingSection(
        messageId = itemState.id,
        thinking = itemState.thinking,
        thinkingDurationMs = null,
        isStreaming = true,
        thinkingExpandedByDefault = thinkingExpandedByDefault,
    )
}

@Composable
private fun StreamingAssistantBodySection(
    itemState: StreamingTranscriptMessageState,
) {
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val bodyPreview = itemState.bodyPreview
    val showLoadingIndicator =
        bodyPreview.showLoadingIndicator && itemState.thinking.isNullOrBlank()
    if (bodyPreview.fullTextLength == 0 && !showLoadingIndicator) return

    StreamingAssistantBodyPreview(
        preview = bodyPreview,
        showLoadingIndicator = showLoadingIndicator,
        spacingMd = spacingMd,
    )
}

@Composable
private fun StreamingAssistantBodyPreview(
    preview: StreamingTranscriptBodyPreview,
    showLoadingIndicator: Boolean,
    spacingMd: Dp,
) {
    val streamingElevation = dimensionResource(R.dimen.m3t_message_streaming_tonal_elevation)
    val transcriptBlockGap = dimensionResource(R.dimen.m3t_transcript_block_gap)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = streamingElevation,
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        if (showLoadingIndicator) {
            StreamingLoadingIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingMd),
            )
        } else if (!preview.usesMarkdownPreview || preview.committedBlocks.isEmpty()) {
            Markdown(
                text = preview.renderMarkdownText,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingMd),
                settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingMd),
                verticalArrangement = Arrangement.spacedBy(transcriptBlockGap),
            ) {
                preview.committedBlocks.forEachIndexed { index, block ->
                    key(index, block) {
                        Markdown(
                            text = block,
                            modifier = Modifier.fillMaxWidth(),
                            settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
                        )
                    }
                }

                if (preview.renderMarkdownText.isNotBlank()) {
                    Markdown(
                        text = preview.renderMarkdownText,
                        modifier = Modifier.fillMaxWidth(),
                        settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun AssistantMessageHeader(
    author: String,
    isStreaming: Boolean,
    spacingSm: Dp,
    spacingXs: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacingSm),
    ) {
        Text(
            text = author,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        if (isStreaming) {
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

@Composable
private fun AssistantMessageBody(
    body: String,
    isStreaming: Boolean,
    spacingMd: Dp,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)
    val streamingElevation = dimensionResource(R.dimen.m3t_message_streaming_tonal_elevation)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isStreaming) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isStreaming) streamingElevation else messageElevation,
    ) {
        if (isStreaming && shouldRenderStreamingMarkdown(body)) {
            StreamingMarkdownPreview(
                text = body,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingMd),
            )
        } else if (isStreaming) {
            Markdown(
                text = buildStreamingSafeMarkdown(body),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingMd),
                settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
            )
        } else {
            AssistantMessageFinalBody(
                body = body,
                spacingMd = spacingMd,
                spacingXs = spacingXs,
            )
        }
    }
}

@Composable
private fun AssistantMessageFinalBody(
    body: String,
    spacingMd: Dp,
    spacingXs: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacingMd, vertical = spacingXs),
    ) {
        Markdown(
            text = body,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AssistantMessageActions(
    item: TranscriptMessageUi,
    actionGap: Dp,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
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

private fun shouldRenderStreamingMarkdown(text: String): Boolean {
    if (text.length < 2) return false
    return text.contains("```") ||
        text.contains('`') ||
        text.contains("$$") ||
        text.contains("\\(") ||
        text.contains("\\[") ||
        text.contains("\\begin{") ||
        text.contains("**") ||
        text.contains("__") ||
        text.startsWith("# ") ||
        text.contains("\n# ") ||
        text.startsWith("- ") ||
        text.contains("\n- ") ||
        text.startsWith("* ") ||
        text.contains("\n* ") ||
        text.startsWith("> ") ||
        text.contains("\n> ") ||
        text.contains("](") ||
        text.contains("|") ||
        STREAMING_MARKDOWN_ORDERED_LIST_RE.containsMatchIn(text)
}

@Composable
@OptIn(FlowPreview::class)
private fun StreamingMarkdownPreview(
    text: String,
    modifier: Modifier = Modifier,
) {
    val transcriptBlockGap = dimensionResource(R.dimen.m3t_transcript_block_gap)
    val latestTextState = rememberUpdatedState(text)
    var renderedSnapshot by remember {
        mutableStateOf(buildStreamingMarkdownSnapshot(text))
    }
    val renderedTailText = remember(text, renderedSnapshot.committedPrefixLength) {
        text
            .drop(renderedSnapshot.committedPrefixLength.coerceAtMost(text.length))
            .trimStart('\n', '\r')
    }

    LaunchedEffect(Unit) {
        snapshotFlow { latestTextState.value }
            .sample(STREAMING_MARKDOWN_RENDER_INTERVAL_MS)
            .collect { candidate ->
                val nextSnapshot = buildStreamingMarkdownSnapshot(candidate)
                if (shouldAdvanceStreamingMarkdownSnapshot(renderedSnapshot, nextSnapshot)) {
                    renderedSnapshot = nextSnapshot
                }
            }
    }

    LaunchedEffect(text) {
        val nextSnapshot = buildStreamingMarkdownSnapshot(text)
        if (shouldAdvanceStreamingMarkdownSnapshot(renderedSnapshot, nextSnapshot)) {
            renderedSnapshot = nextSnapshot
        }
    }

    if (renderedSnapshot.blocks.isEmpty()) {
        Markdown(
            text = buildStreamingSafeMarkdown(renderedTailText.ifBlank { text }),
            modifier = modifier.fillMaxWidth(),
            settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(transcriptBlockGap),
    ) {
        renderedSnapshot.blocks.forEach { block ->
            Markdown(
                text = block,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (renderedTailText.isNotBlank()) {
            Markdown(
                text = buildStreamingSafeMarkdown(renderedTailText),
                modifier = Modifier.fillMaxWidth(),
                settings = LocalMarkdownSettings.current.copy(enableCodeHighlight = false),
            )
        }
    }
}

@Immutable
private data class StreamingMarkdownSnapshot(
    val blocks: ImmutableList<String>,
    val committedPrefixLength: Int,
)

fun buildStreamingTranscriptBodyPreview(text: String): StreamingTranscriptBodyPreview {
    if (text.isBlank()) {
        return StreamingTranscriptBodyPreview(
            usesMarkdownPreview = false,
            committedBlocks = emptyList<String>().toImmutableList(),
            committedPrefixLength = 0,
            tailText = "",
            renderMarkdownText = "",
            showLoadingIndicator = true,
            fullTextLength = 0,
            layoutVersion = 1,
        )
    }

    if (!shouldRenderStreamingMarkdown(text)) {
        val renderText = buildStreamingSafeMarkdown(text)
        return StreamingTranscriptBodyPreview(
            usesMarkdownPreview = false,
            committedBlocks = emptyList<String>().toImmutableList(),
            committedPrefixLength = 0,
            tailText = text,
            renderMarkdownText = renderText,
            showLoadingIndicator = false,
            fullTextLength = text.length,
            layoutVersion = computeStreamingTextLayoutVersion(renderText),
        )
    }

    val snapshot = buildStreamingMarkdownSnapshot(text)
    if (snapshot.committedPrefixLength >= text.length) {
        val renderText = buildStreamingSafeMarkdown(text)
        return StreamingTranscriptBodyPreview(
            usesMarkdownPreview = true,
            committedBlocks = emptyList<String>().toImmutableList(),
            committedPrefixLength = 0,
            tailText = text,
            renderMarkdownText = renderText,
            showLoadingIndicator = false,
            fullTextLength = text.length,
            layoutVersion = computeStreamingBodyLayoutVersion(0, renderText),
        )
    }

    val tailText = renderableStreamingTailText(text, snapshot.committedPrefixLength)
    val renderedTailText = if (snapshot.blocks.isEmpty()) text else tailText
    val renderMarkdownText = buildStreamingSafeMarkdown(renderedTailText)
    return StreamingTranscriptBodyPreview(
        usesMarkdownPreview = true,
        committedBlocks = snapshot.blocks,
        committedPrefixLength = snapshot.committedPrefixLength,
        tailText = renderedTailText,
        renderMarkdownText = renderMarkdownText,
        showLoadingIndicator = false,
        fullTextLength = text.length,
        layoutVersion = computeStreamingBodyLayoutVersion(snapshot.blocks.size, renderMarkdownText),
    )
}

private fun selectStreamingBodyPreview(
    current: StreamingTranscriptBodyPreview,
    next: StreamingTranscriptBodyPreview,
): StreamingTranscriptBodyPreview {
    if (next.showLoadingIndicator) return next
    if (current.showLoadingIndicator) {
        return if (next.renderMarkdownText.length >= 2 || hasStreamingRenderBoundary(next.renderMarkdownText)) {
            next
        } else {
            current
        }
    }
    if (next.renderMarkdownText.isBlank()) return current
    if (current.renderMarkdownText.isBlank()) return next
    if (next.committedBlocks.size != current.committedBlocks.size) return next
    if (next.usesMarkdownPreview != current.usesMarkdownPreview) return next
    if (!next.renderMarkdownText.startsWith(current.renderMarkdownText)) return next
    if (hasStreamingRenderBoundary(next.renderMarkdownText)) return next

    val visibleDelta = next.renderMarkdownText.length - current.renderMarkdownText.length
    return if (visibleDelta >= STREAMING_MARKDOWN_MIN_CHUNK_DELTA) next else current
}

private fun hasStreamingRenderBoundary(text: String): Boolean {
    val last = text.lastOrNull() ?: return false
    if (last == '\n' || last == '\r') return true
    if (last in listOf('.', '!', '?', ':', ';')) return true
    if (last in listOf('。', '！', '？', '：', '；', '，', ',')) return true
    val recentTail = text.takeLast(STREAMING_MARKDOWN_MIN_CHUNK_DELTA + 4)
    return text.endsWith("```") ||
        text.endsWith("$$") ||
        STREAMING_MARKDOWN_STRUCTURE_RE.containsMatchIn(recentTail)
}

private fun renderableStreamingTailText(
    text: String,
    committedPrefixLength: Int,
): String =
    text
        .drop(committedPrefixLength.coerceAtMost(text.length))
        .trimStart('\n', '\r')

private fun buildStreamingSafeMarkdown(text: String): String {
    if (text.isBlank()) return ""
    val withoutDanglingBlockMarker = suppressDanglingBlockMarkerTail(text)
    val withoutDanglingInlineTail = suppressDanglingInlineMarkdownTail(withoutDanglingBlockMarker)
    return closeStreamingMarkdownBlocks(withoutDanglingInlineTail)
}

private fun suppressDanglingBlockMarkerTail(text: String): String {
    val lineStart = text.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
    val lastLine = text.substring(lineStart)
    if (!STREAMING_DANGLING_BLOCK_MARKER_RE.matches(lastLine)) return text
    return text.substring(0, lineStart).trimEnd('\n', '\r')
}

private fun suppressDanglingInlineMarkdownTail(text: String): String {
    if (hasOpenStreamingCodeFence(text) || hasOpenStreamingBlockMath(text)) {
        return text
    }

    val pendingStart =
        listOfNotNull(
            findDanglingInlineCodeStart(text),
            findDanglingStrongStart(text),
            findDanglingEmphasisStart(text),
            findDanglingLinkStart(text),
        )
            .minOrNull()
            ?: return text

    return if (text.length - pendingStart <= STREAMING_PENDING_INLINE_TAIL_LIMIT) {
        text.substring(0, pendingStart).trimEnd()
    } else {
        text
    }
}

private fun closeStreamingMarkdownBlocks(text: String): String {
    if (text.isBlank()) return text
    val builder = StringBuilder(text)
    if (hasOpenStreamingCodeFence(text)) {
        if (!builder.endsWithLineBreak()) builder.append('\n')
        builder.append("```")
    } else if (hasOpenStreamingBlockMath(text)) {
        if (!builder.endsWithLineBreak()) builder.append('\n')
        builder.append("$$")
    }
    return builder.toString()
}

private fun hasOpenStreamingCodeFence(text: String): Boolean {
    var open = false
    STREAMING_MARKDOWN_FENCE_LINE_RE.findAll(text).forEach {
        open = !open
    }
    return open
}

private fun hasOpenStreamingBlockMath(text: String): Boolean {
    if (hasOpenStreamingCodeFence(text)) return false
    var count = 0
    var index = 0
    while (index < text.length - 1) {
        if (text.startsWith("$$", index) && !isEscapedMarkdownChar(text, index)) {
            count += 1
            index += 2
        } else {
            index += 1
        }
    }
    return count % 2 == 1
}

private fun findDanglingInlineCodeStart(text: String): Int? =
    findLastUnclosedDelimiterStart(text, "`")

private fun findDanglingStrongStart(text: String): Int? =
    findLastUnclosedDelimiterStart(text, "**")

private fun findDanglingEmphasisStart(text: String): Int? {
    val positions = mutableListOf<Int>()
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        val isSingleAsterisk =
            ch == '*' &&
                !isEscapedMarkdownChar(text, index) &&
                !text.startsWith("**", index) &&
                !(index > 0 && text[index - 1] == '*') &&
                !isListMarkerAsterisk(text, index)
        if (isSingleAsterisk) {
            positions += index
        }
        index += 1
    }
    return if (positions.size % 2 == 1) positions.last() else null
}

private fun findDanglingLinkStart(text: String): Int? {
    val lastOpenBracket = findLastUnescapedChar(text, '[') ?: return null
    val lastCloseBracket = findLastUnescapedChar(text, ']')
    if (lastCloseBracket == null || lastCloseBracket < lastOpenBracket) {
        return lastOpenBracket
    }
    val linkStart = text.indexOf("](", startIndex = lastOpenBracket)
    if (linkStart >= 0 && text.indexOf(')', startIndex = linkStart + 2) < 0) {
        return lastOpenBracket
    }
    return null
}

private fun findLastUnclosedDelimiterStart(
    text: String,
    delimiter: String,
): Int? {
    val positions = mutableListOf<Int>()
    var index = 0
    while (index <= text.length - delimiter.length) {
        if (text.startsWith(delimiter, index) && !isEscapedMarkdownChar(text, index)) {
            positions += index
            index += delimiter.length
        } else {
            index += 1
        }
    }
    return if (positions.size % 2 == 1) positions.last() else null
}

private fun findLastUnescapedChar(text: String, target: Char): Int? {
    var index = text.lastIndex
    while (index >= 0) {
        if (text[index] == target && !isEscapedMarkdownChar(text, index)) {
            return index
        }
        index -= 1
    }
    return null
}

private fun isListMarkerAsterisk(text: String, index: Int): Boolean {
    if (text[index] != '*') return false
    val lineStart = text.lastIndexOf('\n', startIndex = index).let { if (it < 0) 0 else it + 1 }
    val before = text.substring(lineStart, index)
    val after = text.getOrNull(index + 1)
    return before.isBlank() && after == ' '
}

private fun StringBuilder.endsWithLineBreak(): Boolean =
    isNotEmpty() && (last() == '\n' || last() == '\r')

private fun computeStreamingBodyLayoutVersion(
    committedBlockCount: Int,
    tailText: String,
): Int = (committedBlockCount * 4096) + computeStreamingTextLayoutVersion(tailText)

private fun computeStreamingTextLayoutVersion(text: String): Int {
    if (text.isBlank()) return 0
    val lineBreakCount = text.count { it == '\n' }
    val chunkCount = (text.length + STREAMING_MARKDOWN_MIN_CHUNK_DELTA - 1) / STREAMING_MARKDOWN_MIN_CHUNK_DELTA
    return (lineBreakCount * 1024) + chunkCount
}

private fun buildStreamingMarkdownSnapshot(text: String): StreamingMarkdownSnapshot {
    val stablePrefix = extractStableStreamingMarkdownPrefix(text)
    if (stablePrefix.isBlank()) {
        return StreamingMarkdownSnapshot(
            blocks = emptyList<String>().toImmutableList(),
            committedPrefixLength = 0,
        )
    }

    val partition = partitionCommittedStreamingMarkdown(stablePrefix)
    return StreamingMarkdownSnapshot(
        blocks = partition.blocks.toImmutableList(),
        committedPrefixLength = partition.committedPrefixLength,
    )
}

private fun shouldAdvanceStreamingMarkdownSnapshot(
    previous: StreamingMarkdownSnapshot,
    next: StreamingMarkdownSnapshot,
): Boolean {
    if (next.committedPrefixLength <= previous.committedPrefixLength) return false
    return shouldAdvanceStreamingMarkdownPreview(
        previousRendered = previous.committedPrefixLength,
        nextRendered = next.committedPrefixLength,
    )
}

private fun shouldAdvanceStreamingMarkdownPreview(
    previousRendered: Int,
    nextRendered: Int,
): Boolean {
    if (previousRendered <= 0) return true
    val delta = nextRendered - previousRendered
    return delta >= STREAMING_MARKDOWN_MIN_CHUNK_DELTA ||
        delta > 0
}

private fun extractStableStreamingMarkdownPrefix(text: String): String {
    if (text.isBlank()) return text
    val stableEnd = findStableStreamingMarkdownEnd(text)
    if (stableEnd <= 0) return ""
    if (stableEnd >= text.length) return text
    return text.substring(0, stableEnd)
}

private fun findStableStreamingMarkdownEnd(text: String): Int {
    var safeEnd = 0
    var inFence = false
    var inInlineCode = false
    var inBlockMath = false
    var inInlineMath = false
    var index = 0

    while (index < text.length) {
        if (!inInlineCode && !inBlockMath && !inInlineMath && text.startsWith("```", index)) {
            inFence = !inFence
            index += 3
            continue
        }
        if (!inFence && !inInlineCode && !inInlineMath && text.startsWith("$$", index)) {
            inBlockMath = !inBlockMath
            index += 2
            continue
        }

        val ch = text[index]
        if (!inFence && !inBlockMath && ch == '`') {
            inInlineCode = !inInlineCode
            index += 1
            continue
        }
        if (!inFence && !inInlineCode && !inBlockMath && ch == '$' && !isEscapedMarkdownChar(text, index)) {
            inInlineMath = !inInlineMath
            index += 1
            continue
        }
        if (ch == '\n' && !inFence && !inInlineCode && !inBlockMath && !inInlineMath) {
            safeEnd = index + 1
        }
        index += 1
    }

    if (!inFence && !inInlineCode && !inBlockMath && !inInlineMath) {
        safeEnd = text.length
    }
    return safeEnd
}

@Immutable
private data class StreamingMarkdownPartition(
    val blocks: List<String>,
    val committedPrefixLength: Int,
)

private fun partitionCommittedStreamingMarkdown(text: String): StreamingMarkdownPartition {
    if (text.isBlank()) {
        return StreamingMarkdownPartition(
            blocks = emptyList(),
            committedPrefixLength = 0,
        )
    }

    val blocks = mutableListOf<String>()
    var blockStart = 0
    var lineStart = 0
    var inFence = false
    var inInlineCode = false
    var inBlockMath = false
    var inInlineMath = false
    var index = 0
    var lastClosedStructuredBlockEnd = 0

    fun flushBlock(endExclusive: Int) {
        if (endExclusive <= blockStart) return
        val block = text.substring(blockStart, endExclusive).trim('\n', '\r')
        if (block.isNotBlank()) {
            blocks += block
        }
        blockStart = endExclusive
    }

    while (index < text.length) {
        if (!inInlineCode && !inBlockMath && !inInlineMath && text.startsWith("```", index)) {
            inFence = !inFence
            if (!inFence) {
                lastClosedStructuredBlockEnd = (index + 3).coerceAtMost(text.length)
            }
            index += 3
            continue
        }
        if (!inFence && !inInlineCode && !inInlineMath && text.startsWith("$$", index)) {
            inBlockMath = !inBlockMath
            if (!inBlockMath) {
                lastClosedStructuredBlockEnd = (index + 2).coerceAtMost(text.length)
            }
            index += 2
            continue
        }

        val ch = text[index]
        if (!inFence && !inBlockMath && ch == '`') {
            inInlineCode = !inInlineCode
            index += 1
            continue
        }
        if (!inFence && !inInlineCode && !inBlockMath && ch == '$' && !isEscapedMarkdownChar(text, index)) {
            inInlineMath = !inInlineMath
            index += 1
            continue
        }
        if (ch == '\n') {
            if (!inFence && !inInlineCode && !inBlockMath && !inInlineMath) {
                val line = text.substring(lineStart, index)
                if (line.isBlank()) {
                    flushBlock(index + 1)
                } else if (lastClosedStructuredBlockEnd in (blockStart + 1)..index) {
                    flushBlock(index + 1)
                    lastClosedStructuredBlockEnd = 0
                }
            }
            lineStart = index + 1
        }
        index += 1
    }

    if (!inFence && !inInlineCode && !inBlockMath && !inInlineMath &&
        lastClosedStructuredBlockEnd in (blockStart + 1)..text.length
    ) {
        flushBlock(text.length)
    }

    return StreamingMarkdownPartition(
        blocks = blocks,
        committedPrefixLength = blockStart,
    )
}

private fun isEscapedMarkdownChar(text: String, index: Int): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        slashCount += 1
        cursor -= 1
    }
    return slashCount % 2 == 1
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
    messageId: String,
    thinking: String?,
    thinkingDurationMs: Long?,
    isStreaming: Boolean,
    thinkingExpandedByDefault: Boolean,
) {
    val thinkingText = thinking?.trim().orEmpty()
    if (thinkingText.isEmpty()) return

    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val thinkingIconBox = dimensionResource(R.dimen.m3t_message_thinking_icon_box_size)
    val actionIconSize = dimensionResource(R.dimen.m3t_message_action_icon_size)
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)

    val thinkingLabel =
        thinkingDurationMs?.let { durationMs ->
            stringResource(
                R.string.message_thinking_duration_format,
                durationMs / 1000f,
            )
        } ?: if (isStreaming) {
            stringResource(R.string.message_thinking_in_progress)
        } else {
            stringResource(R.string.message_thinking_label)
        }

    if (isStreaming) {
        StreamingThinkingSection(
            thinkingLabel = thinkingLabel,
            thinkingText = thinkingText,
            thinkingIconBox = thinkingIconBox,
            actionIconSize = actionIconSize,
            spacingSm = spacingSm,
            spacingMd = spacingMd,
            spacingXs = spacingXs,
        )
        return
    }

    val actionButtonSize = dimensionResource(R.dimen.m3t_message_action_button_size)
    var expanded by rememberSaveable(messageId) {
        mutableStateOf(isStreaming || thinkingExpandedByDefault)
    }
    var wasStreaming by rememberSaveable(messageId) {
        mutableStateOf(isStreaming)
    }

    LaunchedEffect(messageId, isStreaming) {
        if (isStreaming && !wasStreaming) {
            expanded = true
        }
        wasStreaming = isStreaming
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "thinkingArrowRotation",
    )
    var bodyVisible by rememberSaveable(messageId) {
        mutableStateOf(expanded)
    }
    var hasExpandedOnce by rememberSaveable(messageId) {
        mutableStateOf(expanded)
    }

    LaunchedEffect(messageId, expanded) {
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
        var collapsedWidthPx by rememberSaveable(messageId, thinkingLabel) { mutableStateOf(0) }
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
        val contentWidthModifier =
            if (needsCollapsedBootstrap) {
                Modifier.wrapContentWidth()
            } else {
                Modifier.fillMaxWidth()
            }

        Surface(
            modifier = surfaceModifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = messageElevation,
        ) {
            val contentModifier =
                Modifier
                    .then(contentWidthModifier)
                    .let { baseModifier ->
                        if (isStreaming) {
                            baseModifier
                        } else {
                            baseModifier.animateContentSize(
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
                            )
                        }
                    }
            Column(
                modifier =
                    contentModifier
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
                    if (isStreaming) {
                        Text(
                            text = thinkingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Markdown(
                            text = thinkingText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingThinkingSection(
    thinkingLabel: String,
    thinkingText: String,
    thinkingIconBox: Dp,
    actionIconSize: Dp,
    spacingSm: Dp,
    spacingMd: Dp,
    spacingXs: Dp,
) {
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = messageElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingXs + spacingSm),
            verticalArrangement = Arrangement.spacedBy(spacingXs + spacingSm),
        ) {
            Row(
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = thinkingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            )
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
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = messageElevation,
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
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val messageElevation = dimensionResource(R.dimen.m3t_message_tonal_elevation)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = messageElevation,
        modifier = Modifier.wrapContentWidth(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            content = { content() },
        )
    }
}
