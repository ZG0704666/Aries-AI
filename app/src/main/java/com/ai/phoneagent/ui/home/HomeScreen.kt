package com.ai.phoneagent.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.History
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Settings
import com.ai.phoneagent.core.designsystem.R as DesignR
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
    selectedPage: Int = 1,
    onPageSelected: (Int) -> Unit = {},
    settingsAtRoot: Boolean = true,
    settingsContent: @Composable (Boolean) -> Unit = {},
) {
    val spacingMd = dimensionResource(DesignR.dimen.aries_page_padding)
    val spacingSm = dimensionResource(DesignR.dimen.m3t_spacing_sm)
    val spacingXxxs = dimensionResource(DesignR.dimen.m3t_spacing_xxxs)
    val navWidth = dimensionResource(DesignR.dimen.aries_nav_width)
    val navHeight = dimensionResource(DesignR.dimen.aries_nav_height)
    val motionMs = integerResource(DesignR.integer.aries_page_motion_ms)
    val density = LocalDensity.current
    val pager = rememberPagerState(initialPage = selectedPage) { 3 }
    val defaultPageConnection = PagerDefaults.pageNestedScrollConnection(pager, Orientation.Horizontal)
    val horizontalPageConnection = remember(defaultPageConnection) {
        HorizontalPagerScrollConnection(defaultPageConnection)
    }
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    var lastReportedPage by remember { mutableIntStateOf(selectedPage) }
    val navigate: (Int) -> Unit = { page ->
        navigationJob?.cancel()
        navigationJob = scope.launch {
            pager.animateScrollToPage(page, animationSpec = tween(motionMs, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(selectedPage) {
        if (lastReportedPage != selectedPage) navigate(selectedPage)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { lastReportedPage = it; onPageSelected(it) }
    }
    // Compatibility for existing callbacks which used to wait for the drawer to close.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            navigate(0)
            drawerState.close()
        } else onDrawerClosed()
    }
    BackHandler(enabled = com.ai.phoneagent.navigation.homeBackAction(pager.currentPage, settingsAtRoot) == com.ai.phoneagent.navigation.HomeBackAction.HOME) { navigate(1) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (pager.currentPage != 2 || settingsAtRoot) {
                BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = spacingSm), contentAlignment = Alignment.Center) {
                    val effectiveNavWidth = minOf(navWidth, maxWidth - dimensionResource(DesignR.dimen.aries_touch_target) * 2)
                    val colors = MaterialTheme.colorScheme
                    Surface(
                        modifier = Modifier.width(effectiveNavWidth).heightIn(min = navHeight)
                            .ariesGlass(RoundedCornerShape(dimensionResource(DesignR.dimen.aries_nav_radius))),
                        shape = RoundedCornerShape(dimensionResource(DesignR.dimen.aries_nav_radius)),
                        color = Color.Transparent,
                        contentColor = colors.onSurface,
                    ) {
                        Box(Modifier.padding(horizontal = dimensionResource(DesignR.dimen.aries_nav_inset_x), vertical = dimensionResource(DesignR.dimen.aries_nav_inset_y))) {
                            val itemHeight = navHeight - dimensionResource(DesignR.dimen.aries_nav_inset_y) * 2
                            val tabWidth = (effectiveNavWidth - dimensionResource(DesignR.dimen.aries_nav_inset_x) * 2) / 3
                            Box(Modifier.offset(x = tabWidth * (pager.currentPage + pager.currentPageOffsetFraction))
                                .width(tabWidth).height(itemHeight)
                                .ariesGlass(RoundedCornerShape(dimensionResource(DesignR.dimen.aries_nav_item_radius)), selected = true))
                            Row(Modifier.fillMaxWidth()) {
                                val labels = listOf(R.string.ui_v2_history, R.string.ui_v2_home, R.string.drawer_settings)
                                val icons = listOf(Lucide.History, Lucide.House, Lucide.Settings)
                                labels.forEachIndexed { index, label ->
                                    Row(Modifier.weight(1f).heightIn(min = itemHeight)
                                        .semantics { selected = pager.currentPage == index }
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { navigate(index) },
                                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icons[index], null, modifier = Modifier.height(dimensionResource(DesignR.dimen.aries_nav_icon)).width(dimensionResource(DesignR.dimen.aries_nav_icon)),
                                            tint = if (pager.currentPage == index) colors.primary else colors.onSurfaceVariant)
                                        Text(stringResource(label), Modifier.padding(start = spacingXxxs),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = with(density) { dimensionResource(DesignR.dimen.aries_nav_text).toSp() }),
                                            color = if (pager.currentPage == index) colors.onSurface else colors.onSurfaceVariant,
                                            fontWeight = if (pager.currentPage == index) FontWeight.SemiBold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 2,
                pageNestedScrollConnection = horizontalPageConnection,
                userScrollEnabled = drawerGesturesEnabled && settingsAtRoot,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(motionMs, easing = FastOutSlowInEasing)),
            ) { page ->
                when (page) {
                    0 -> Box(Modifier.fillMaxSize().navigationBarsPadding()
                        .padding(horizontal = dimensionResource(DesignR.dimen.aries_page_padding), vertical = dimensionResource(DesignR.dimen.aries_home_hero_gap))) {
                        ConversationDrawer(
                            searchQuery = drawerSearchQuery, items = drawerItems, emptyMessage = drawerEmptyMessage,
                            onSearchQueryChange = onDrawerSearchQueryChange,
                            onConversationClick = { onDrawerConversationClick(it); navigate(1) },
                            onConversationLongClick = onDrawerConversationLongClick,
                            onSettingsClick = { navigate(2) }, showSettingsEntry = false,
                        )
                    }
                    1 -> Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                        Column(Modifier.weight(1f).clipToBounds()) {
                            Text(modelName + if (statusVisible && statusText.isNotBlank()) " · " + statusText else "",
                                Modifier.padding(start = spacingMd, end = spacingMd, top = dimensionResource(DesignR.dimen.aries_home_top_gap)).clickable { onToggleStatus() },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Box(Modifier.weight(1f)) { transcriptPaneContent(0.dp, spacingMd, spacingXxxs) }
                        }
                        HomeBottomOverlay(aiNoticeText, inputBarContent, spacingXxxs)
                    }
                    2 -> settingsContent(pager.currentPage == 2)
                }
            }
        }
        onboardingContent?.invoke()
        historyDialogContent?.invoke()
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
    val spacingSm = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_sm)
    if (transcriptItems.isEmpty() && streamingTranscriptItem == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacingMd),
        ) {
            TranscriptEmptyHintCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(top = dimensionResource(DesignR.dimen.aries_home_hero_gap), bottom = bottomOverlayPadding),
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
private fun HomeBottomOverlay(
    aiNoticeText: String,
    inputBarContent: @Composable () -> Unit,
    spacingXxxs: Dp,
) {
    DebugRecomposeLogger(scope = "HomeBottomOverlay")
    Column(
        modifier = Modifier
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
