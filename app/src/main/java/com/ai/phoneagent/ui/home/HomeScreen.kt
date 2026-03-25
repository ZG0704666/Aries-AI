package com.ai.phoneagent.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.messages.ConversationTranscript
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.topbar.MainTopBar
import kotlinx.coroutines.launch

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
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollToBottomSignal) {
        if (scrollToBottomSignal > 0L) {
            launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
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
                    )
                },
            ) { paddingValues: PaddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .imePadding(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacingMd)
                            .padding(top = spacingXxxs)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(spacingXxxs),
                    ) {
                        AnimatedContent(
                            targetState = transcriptAnimationKey,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                            },
                            label = "conversationSwitch",
                        ) {
                            ConversationTranscript(
                                items = transcriptItems,
                                onCopyMessage = onCopyMessage,
                                onRetryMessage = onRetryMessage,
                                onAutomationAction = onAutomationAction,
                                thinkingExpandedByDefault = thinkingExpandedByDefault,
                                onEditMessage = onEditMessage,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
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
