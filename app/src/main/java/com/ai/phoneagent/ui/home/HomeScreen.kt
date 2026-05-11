package com.ai.phoneagent.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.ai.phoneagent.ui.messages.TranscriptEmptyHintCard
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.conversationTranscriptItem
import com.ai.phoneagent.ui.messages.conversationTranscriptItems
import com.ai.phoneagent.ui.topbar.MainTopBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    transcriptItems: List<TranscriptMessageUi>,
    streamingTranscriptItem: TranscriptMessageUi? = null,
    transcriptAnimationKey: Long,
    thinkingExpandedByDefault: Boolean,
    codeBlockPrefs: CodeBlockPrefs = CodeBlockPrefs(),
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    inputBarContent: @Composable () -> Unit,
    aiNoticeText: String,
    scrollToBottomSignal: Long,
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
    val reversedTranscriptItems = remember(transcriptItems) {
        transcriptItems.asReversed().toImmutableList()
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentAlpha
                        scaleX = contentScale
                        scaleY = contentScale
                    },
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
                    HomeTranscriptPane(
                        reversedTranscriptItems = reversedTranscriptItems,
                        streamingTranscriptItem = streamingTranscriptItem,
                        transcriptItems = transcriptItems,
                        transcriptAnimationKey = transcriptAnimationKey,
                        thinkingExpandedByDefault = thinkingExpandedByDefault,
                        codeBlockPrefs = codeBlockPrefs,
                        onCopyMessage = onCopyMessage,
                        onRetryMessage = onRetryMessage,
                        onEditMessage = onEditMessage,
                        onAutomationAction = onAutomationAction,
                        scrollToBottomSignal = scrollToBottomSignal,
                        bottomOverlayPadding = bottomOverlayPadding,
                        spacingMd = spacingMd,
                        spacingXxxs = spacingXxxs,
                    )

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
private fun HomeTranscriptPane(
    reversedTranscriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: TranscriptMessageUi?,
    transcriptItems: List<TranscriptMessageUi>,
    transcriptAnimationKey: Long,
    thinkingExpandedByDefault: Boolean,
    codeBlockPrefs: CodeBlockPrefs,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    scrollToBottomSignal: Long,
    bottomOverlayPadding: Dp,
    spacingMd: Dp,
    spacingXxxs: Dp,
) {
    DebugRecomposeLogger(scope = "HomeTranscriptPane")
    Crossfade(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacingMd)
            .padding(top = spacingXxxs),
        targetState = transcriptAnimationKey,
        animationSpec = tween(200),
        label = "conversationSwitch",
    ) { animKey ->
        if (reversedTranscriptItems.isEmpty() && streamingTranscriptItem == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                TranscriptEmptyHintCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = spacingMd),
                )
            }
            return@Crossfade
        }

        val conversationId = remember(
            reversedTranscriptItems,
            streamingTranscriptItem,
            animKey,
        ) {
            streamingTranscriptItem
                ?.conversationId
                ?.takeIf { it >= 0L }
                ?: reversedTranscriptItems.firstOrNull()?.conversationId
                ?: animKey
        }
        val listState = key(conversationId) { rememberLazyListState() }
        val atBottom by remember(listState) {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 50
            }
        }

        LaunchedEffect(conversationId) {
            listState.scrollToItem(0)
        }

        LaunchedEffect(scrollToBottomSignal, conversationId, atBottom) {
            if (scrollToBottomSignal > 0L && atBottom) {
                listState.animateScrollToItem(0)
            }
        }

        LaunchedEffect(
            conversationId,
            transcriptItems.size,
            transcriptItems.lastOrNull()?.id,
            streamingTranscriptItem?.id,
            atBottom,
            listState.isScrollInProgress,
        ) {
            if (atBottom &&
                !listState.isScrollInProgress &&
                (listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0)
            ) {
                listState.scrollToItem(0)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            reverseLayout = true,
        ) {
            item(key = "bottom_overlay_spacer", contentType = "bottom_spacer") {
                Spacer(modifier = Modifier.height(bottomOverlayPadding))
            }

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

            conversationTranscriptItems(
                items = reversedTranscriptItems,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
                onAutomationAction = onAutomationAction,
                thinkingExpandedByDefault = thinkingExpandedByDefault,
                onEditMessage = onEditMessage,
                codeBlockPrefs = codeBlockPrefs,
            )
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
