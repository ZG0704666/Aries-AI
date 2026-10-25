package com.ai.phoneagent.ui.home

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ai.phoneagent.core.designsystem.theme.glass.AriesLiquidTabs
import com.ai.phoneagent.core.designsystem.theme.glass.LocalAriesGlassBackdrop
import com.ai.phoneagent.core.designsystem.theme.glass.LocalAriesTabScale
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.pager.HorizontalPager

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import kotlin.math.roundToInt
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
    onOpenDrawer: () -> Unit,
    drawerSearchQuery: String,
    drawerItems: List<DrawerConversationUiItem>,
    drawerEmptyMessage: String,
    onDrawerSearchQueryChange: (String) -> Unit,
    onDrawerConversationClick: (Long) -> Unit,
    onDrawerConversationLongClick: (Long) -> Unit,
    onDrawerSettingsClick: () -> Unit,
    transcriptPaneContent: @Composable (topOverlayPadding: Dp, bottomOverlayPadding: Dp, spacingMd: Dp, spacingXxxs: Dp) -> Unit,
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
    val hapticContext = LocalContext.current
    val pager = rememberPagerState(initialPage = selectedPage) { 3 }
    val defaultPageConnection = PagerDefaults.pageNestedScrollConnection(pager, Orientation.Horizontal)
    val horizontalPageConnection = remember(defaultPageConnection) {
        HorizontalPagerScrollConnection(defaultPageConnection)
    }
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    var lastReportedPage by remember { mutableIntStateOf(selectedPage) }
    // 点击/路由切换产生的目的页。程序 tween 期间 pager.currentPage 会先穿过中间页签
    // （foundation 1.12：currentPage 表示“最接近对齐位的页”，tween 滑过中点即翻转），
    // 若把它直接喂给胶囊会被当成外部选择，把胶囊重定向到主页并取消正确的 tween。
    // 因此在 tween 进行中只暴露目的页，tween 结束后再回退读 pager.currentPage。
    var flyingToPage by remember { mutableIntStateOf(-1) }
    val navigate: (Int) -> Unit = { page ->
        // 回声去重：胶囊同步（onSelect）会以同一目的页再次调用 navigate；
        // 若此时 cancel 正在飞的 tween，finally 窗口会把目的页清空，
        // selectedDestination 洞穿回 pager 过程值并再次回声导航回去（BACK 回主页失效的根因）。
        if (!(page == flyingToPage && navigationJob?.isActive == true)) {
            flyingToPage = page
            navigationJob?.cancel()
            navigationJob = scope.launch {
                try {
                    pager.animateScrollToPage(page, animationSpec = tween(motionMs, easing = FastOutSlowInEasing))
                } finally {
                    if (flyingToPage == page) flyingToPage = -1
                }
            }
        }
    }
    // 胶囊导航/页签选择的推导值：飞行目的页优先，其次才是 pager 过程值。
    val selectedDestination by remember(pager) {
        derivedStateOf {
            val intent = flyingToPage
            if (intent >= 0) intent else pager.currentPage
        }
    }
    LaunchedEffect(selectedPage) {
        if (lastReportedPage != selectedPage) navigate(selectedPage)
    }
    LaunchedEffect(pager) {
        // 本页签保存期间，外层路由可能请求切换到其他页签。
        // 在把恢复的页码上报给 Activity 之前，先应用该选择。
        if (pager.currentPage != selectedPage) pager.scrollToPage(selectedPage)
        // 上报“已落定”页：tween 过程中 currentPage 会穿过主页，不能把中间值
        // 上报给 Activity（会污染持久化的页签，甚至拉起错误的恢复页）。
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { lastReportedPage = it; onPageSelected(it) }
    }
    // 兼容旧的回调逻辑：过去需要等待抽屉关闭。
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            navigate(0)
            drawerState.close()
        } else onDrawerClosed()
    }
    BackHandler(enabled = com.ai.phoneagent.navigation.homeBackAction(pager.currentPage, settingsAtRoot) == com.ai.phoneagent.navigation.HomeBackAction.HOME) {
        navigate(1)
    }
    val navRegion =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacingSm * 2 + navHeight
    val navVisible = pager.currentPage != 2 || settingsAtRoot
    // 内容层 backdrop：让悬浮胶囊真正折射其下方滚动的对话内容。
    val contentBackdrop = rememberLayerBackdrop()
    val navBackdrop = rememberCombinedBackdrop(LocalAriesGlassBackdrop.current, contentBackdrop)

    // 只有用户手指拖动 pager 时（DragInteraction），选中胶囊才逐帧跟随进度；
    // 点击/程序触发的 tween 动画不跟随，避免弹簧飞行与 pager 动画互相覆盖。
    var pagerDragging by remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        var dragCount = 0
        pager.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> dragCount++
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragCount--
            }
            pagerDragging = dragCount > 0
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().layerBackdrop(contentBackdrop),
            beyondViewportPageCount = 1,
            pageNestedScrollConnection = horizontalPageConnection,
            userScrollEnabled = drawerGesturesEnabled && settingsAtRoot,
            flingBehavior = PagerDefaults.flingBehavior(
                pager, 
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        ) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize().navigationBarsPadding()
                    .padding(top = navRegion + dimensionResource(DesignR.dimen.aries_home_hero_gap))
                    .padding(
                        start = dimensionResource(DesignR.dimen.aries_page_padding),
                        end = dimensionResource(DesignR.dimen.aries_page_padding),
                        bottom = dimensionResource(DesignR.dimen.aries_home_hero_gap),
                    )) {
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
                        Box(Modifier.weight(1f)) { transcriptPaneContent(navRegion, 0.dp, spacingMd, spacingXxxs) }
                    }
                    HomeBottomOverlay(aiNoticeText, inputBarContent, spacingXxxs)
                }
                2 -> Box(Modifier.fillMaxSize().padding(top = if (settingsAtRoot) navRegion else 0.dp)) {
                    settingsContent(pager.currentPage == 2)
                }
            }
        }
        if (navVisible) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = spacingSm),
                contentAlignment = Alignment.Center,
            ) {
                val effectiveNavWidth = minOf(navWidth, maxWidth - dimensionResource(DesignR.dimen.aries_touch_target) * 2)
                AriesLiquidTabs(
                    selectedIndex = selectedDestination,
                    onSelect = { page -> hapticTick(hapticContext); navigate(page) },
                    progress = { pager.currentPage + pager.currentPageOffsetFraction },
                    userDragging = { pagerDragging },
                    backdrop = navBackdrop,
                    modifier = Modifier.width(effectiveNavWidth),
                    tabCount = 3,
                    onEdgeHaptic = { hapticTick(hapticContext) },
                ) { index ->
                    HomeNavTab(index = index, selected = selectedDestination == index)
                }
            }
        }
        onboardingContent?.invoke()
        historyDialogContent?.invoke()
    }
}

/** 顶部胶囊导航的单个页签内容（跨三层复用：可见层、折射副本层）。 */
@Composable
private fun HomeNavTab(index: Int, selected: Boolean) {
    val icon = when (index) {
        0 -> Lucide.History
        1 -> Lucide.House
        else -> Lucide.Settings
    }
    val label = stringResource(
        when (index) {
            0 -> R.string.ui_v2_history
            1 -> R.string.ui_v2_home
            else -> R.string.drawer_settings
        }
    )
    val colors = MaterialTheme.colorScheme
    val scale = LocalAriesTabScale.current
    Row(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(dimensionResource(DesignR.dimen.aries_nav_icon)),
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        Text(
            label,
            Modifier.padding(start = dimensionResource(DesignR.dimen.m3t_spacing_xxxs)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = with(LocalDensity.current) {
                    dimensionResource(DesignR.dimen.aries_nav_text).toSp()
                },
            ),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
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
    topOverlayPadding: Dp,
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
                    .padding(top = dimensionResource(DesignR.dimen.aries_home_hero_gap) + topOverlayPadding, bottom = bottomOverlayPadding),
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
            .padding(horizontal = spacingMd),
        contentPadding = PaddingValues(top = topOverlayPadding + spacingXxxs),
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
            .padding(bottom = dimensionResource(DesignR.dimen.aries_composer_bottom_gap)),
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

private fun hapticTick(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}
