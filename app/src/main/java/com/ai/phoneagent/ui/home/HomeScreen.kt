package com.ai.phoneagent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import com.ai.phoneagent.ui.debug.DebugRecomposeLogger
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.messages.CodeBlockPrefs
import com.ai.phoneagent.ui.messages.StreamingTranscriptMessageState
import com.ai.phoneagent.ui.messages.TranscriptEmptyHintCard
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.conversationTranscriptItem
import com.ai.phoneagent.ui.messages.conversationTranscriptItems
import com.ai.phoneagent.ui.topbar.MainTopBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    drawerGesturesEnabled: Boolean,
    statusText: String,
    statusVisible: Boolean,
    onToggleStatus: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenFloatingWindow: () -> Unit,
    modelName: String = "",
    drawerSearchQuery: String,
    drawerItems: List<DrawerConversationUiItem>,
    drawerEmptyMessage: String,
    onDrawerSearchQueryChange: (String) -> Unit,
    onDrawerConversationClick: (Long) -> Unit,
    onDrawerConversationLongClick: (Long) -> Unit,
    onDrawerSettingsClick: () -> Unit,
    transcriptPaneContent: @Composable (bottomOverlayPadding: Dp, spacingMd: Dp, spacingXxxs: Dp) -> Unit,
    inputBarContent: @Composable () -> Unit,
    aiNoticeText: String,
    contentAlpha: Float,
    contentScale: Float,
    onboardingContent: @Composable (() -> Unit)? = null,
    historyDialogContent: @Composable (() -> Unit)? = null,
    onDrawerClosed: () -> Unit = {},
) {
    DebugRecomposeLogger(scope = "HomeScreen")
    val drawerWidth = dimensionResource(R.dimen.m3t_drawer_width)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val density = LocalDensity.current
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomOverlayPadding = with(density) { bottomOverlayHeightPx.toDp() }
    val scaffoldModifier =
        if (contentAlpha != 1f || contentScale != 1f) {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        } else {
            Modifier.fillMaxSize()
        }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        scrimColor = colorResource(R.color.m3t_drawer_scrim),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = dialogPadding, vertical = spacingXl),
                ) {
                    ConversationDrawer(
                        searchQuery = drawerSearchQuery,
                        items = drawerItems,
                        emptyMessage = drawerEmptyMessage,
                        onSearchQueryChange = onDrawerSearchQueryChange,
                        onConversationClick = onDrawerConversationClick,
                        onConversationLongClick = onDrawerConversationLongClick,
                        onSettingsClick = onDrawerSettingsClick,
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = scaffoldModifier,
                topBar = {
                    MainTopBar(
                        statusText = statusText,
                        statusVisible = statusVisible,
                        onToggleStatus = onToggleStatus,
                        onOpenDrawer = onOpenDrawer,
                        onNewChat = onNewChat,
                        onOpenFloatingWindow = onOpenFloatingWindow,
                        modelName = modelName,
                    )
                },
            ) { paddingValues: PaddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                ) {
                    transcriptPaneContent(bottomOverlayPadding, spacingMd, spacingXxxs)

                    HomeBottomOverlay(
                        aiNoticeText = aiNoticeText,
                        inputBarContent = inputBarContent,
                        spacingXxxs = spacingXxxs,
                        onHeightChanged = { height ->
                            if (bottomOverlayHeightPx != height) {
                                bottomOverlayHeightPx = height
                            }
                        },
                    )
                }
            }

            onboardingContent?.invoke()
            historyDialogContent?.invoke()
        }
    }
}

@Composable
fun HomeTranscriptPane(
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    transcriptResetKey: Long,
    thinkingExpandedByDefault: Boolean,
    codeBlockPrefs: CodeBlockPrefs,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    onEmptySuggestionClick: (String) -> Unit,
    scrollToBottomSignal: Long,
    bottomOverlayPadding: Dp,
    spacingMd: Dp,
    spacingXxxs: Dp,
) {
    DebugRecomposeLogger(scope = "HomeTranscriptPane")
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    if (transcriptItems.isEmpty() && streamingTranscriptItem == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacingMd),
        ) {
            TranscriptEmptyHintCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -spacingSm)
                    .padding(bottom = bottomOverlayPadding / 3),
                onSuggestionClick = onEmptySuggestionClick,
            )
        }
        return
    }

    val conversationId = remember(
        transcriptItems,
        streamingTranscriptItem,
        transcriptResetKey,
    ) {
        streamingTranscriptItem
            ?.conversationId
            ?.takeIf { it >= 0L }
            ?: transcriptItems.firstOrNull()?.conversationId
            ?: transcriptResetKey
    }
    val listKey = remember(conversationId, transcriptResetKey) {
        "$conversationId-$transcriptResetKey"
    }
    val listState = rememberLazyListState()
    val bottomListIndex = transcriptItems.size + if (streamingTranscriptItem != null) 1 else 0

    HomeTranscriptAutoFollowController(
        listState = listState,
        listKey = listKey,
        bottomListIndex = bottomListIndex,
        scrollToBottomSignal = scrollToBottomSignal,
        transcriptItems = transcriptItems,
        streamingTranscriptItem = streamingTranscriptItem,
        bottomOverlayPadding = bottomOverlayPadding,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacingMd)
            .padding(top = spacingXxxs),
        state = listState,
    ) {
        conversationTranscriptItems(
            items = transcriptItems,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )

        streamingTranscriptItem?.let { item ->
            conversationTranscriptItem(
                item = item,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
                onAutomationAction = onAutomationAction,
                thinkingExpandedByDefault = thinkingExpandedByDefault,
                onEditMessage = onEditMessage,
                codeBlockPrefs = codeBlockPrefs,
            )
        }

        item(key = "bottom_overlay_spacer", contentType = "bottom_spacer") {
            Spacer(modifier = Modifier.height(bottomOverlayPadding))
        }
    }
}

@Composable
private fun HomeTranscriptAutoFollowController(
    listState: androidx.compose.foundation.lazy.LazyListState,
    listKey: String,
    bottomListIndex: Int,
    scrollToBottomSignal: Long,
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    bottomOverlayPadding: Dp,
) {
    val streamingContentVersion = remember(
        transcriptItems.lastOrNull()?.id,
        transcriptItems.lastOrNull()?.body?.length,
        transcriptItems.lastOrNull()?.thinking?.length,
        streamingTranscriptItem?.bodyLayoutVersion,
        streamingTranscriptItem?.thinkingLayoutVersion,
        bottomOverlayPadding,
    ) {
        listOf(
            transcriptItems.lastOrNull()?.id,
            transcriptItems.lastOrNull()?.body?.length,
            transcriptItems.lastOrNull()?.thinking?.length,
            streamingTranscriptItem?.bodyLayoutVersion,
            streamingTranscriptItem?.thinkingLayoutVersion,
            bottomOverlayPadding.value,
        )
    }
    val isAtBottom by remember(listState) {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    var autoFollowBottom by remember(listKey) { mutableStateOf(true) }

    LaunchedEffect(listKey) {
        autoFollowBottom = true
        listState.scrollToItem(bottomListIndex)
    }

    LaunchedEffect(listState, listKey) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .distinctUntilChanged()
            .collect { (isScrollInProgress, atBottom) ->
                if (isScrollInProgress) {
                    autoFollowBottom = atBottom
                }
            }
    }

    LaunchedEffect(scrollToBottomSignal, listKey, bottomListIndex) {
        if (scrollToBottomSignal > 0L) {
            autoFollowBottom = true
            listState.scrollToItem(bottomListIndex)
        }
    }

    LaunchedEffect(
        listKey,
        streamingContentVersion,
        autoFollowBottom,
        listState.isScrollInProgress,
        bottomListIndex,
    ) {
        if (autoFollowBottom && !listState.isScrollInProgress && listState.canScrollForward) {
            listState.scrollToItem(bottomListIndex)
        }
    }
}

@Composable
private fun BoxScope.HomeBottomOverlay(
    aiNoticeText: String,
    inputBarContent: @Composable () -> Unit,
    spacingXxxs: Dp,
    onHeightChanged: (Int) -> Unit,
) {
    DebugRecomposeLogger(scope = "HomeBottomOverlay")
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) }
            .padding(bottom = spacingXxxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        inputBarContent()
        Text(
            text = aiNoticeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacingXxxs),
        )
    }
}
