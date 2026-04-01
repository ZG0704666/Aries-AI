package com.ai.phoneagent.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.messages.CodeBlockPrefs
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.conversationTranscriptItems
import com.ai.phoneagent.ui.topbar.MainTopBar
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
    val drawerWidth = dimensionResource(R.dimen.m3t_drawer_width)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val density = LocalDensity.current
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomOverlayPadding = with(density) { bottomOverlayHeightPx.toDp() }
    val lazyTranscriptItems = remember(transcriptItems) {
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
                    Crossfade(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacingMd)
                            .padding(top = spacingXxxs),
                        targetState = transcriptAnimationKey,
                        animationSpec = tween(200),
                        label = "conversationSwitch",
                    ) { animKey ->
                        val conversationId = remember(lazyTranscriptItems, animKey) {
                            lazyTranscriptItems.firstOrNull()?.conversationId ?: animKey
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
                            transcriptItems.lastOrNull()?.isStreaming,
                            transcriptItems.lastOrNull()?.body?.length,
                            atBottom,
                        ) {
                            if (atBottom &&
                                (listState.firstVisibleItemIndex > 0 ||
                                    listState.firstVisibleItemScrollOffset > 0)
                            ) {
                                listState.animateScrollToItem(0)
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

                            conversationTranscriptItems(
                                items = lazyTranscriptItems,
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage,
                                onAutomationAction = onAutomationAction,
                                thinkingExpandedByDefault = thinkingExpandedByDefault,
                                onEditMessage = onEditMessage,
                                codeBlockPrefs = codeBlockPrefs,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { bottomOverlayHeightPx = it.height }
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
            }

            onboardingContent?.invoke()
            historyDialogContent?.invoke()
        }
    }
}
