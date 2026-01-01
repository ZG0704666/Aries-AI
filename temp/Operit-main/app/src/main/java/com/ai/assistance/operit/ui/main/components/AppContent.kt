package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.RequiresApi
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.components.CustomScaffold

// 定义一个 CompositionLocal，用于向下传递当前屏幕是否可见的状态
val LocalIsCurrentScreen = compositionLocalOf { true }

// 用于屏幕切换动画的状态
private enum class ScreenVisibility {
    VISIBLE,
    HIDDEN
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppContent(
        currentScreen: Screen,
        selectedItem: NavItem,
        useTabletLayout: Boolean,
        isTabletSidebarExpanded: Boolean,
        isLoading: Boolean,
        navController: NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        onScreenChange: (Screen) -> Unit,
        onNavItemChange: (NavItem) -> Unit,
        onToggleSidebar: () -> Unit,
        navigateToTokenConfig: () -> Unit,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {},
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        actions: @Composable RowScope.() -> Unit = {}
) {
    // Get background image state
    val context = LocalContext.current
    val preferencesManager = UserPreferencesManager.getInstance(context)
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

    // Get toolbar transparency setting
    val toolbarTransparent =
            preferencesManager.toolbarTransparent.collectAsState(initial = false).value
    
    // Get AppBar custom color settings
    val useCustomAppBarColor =
            preferencesManager.useCustomAppBarColor.collectAsState(initial = false).value
    val customAppBarColor =
            preferencesManager.customAppBarColor.collectAsState(initial = null).value

    // Get AppBar content color settings
    val forceAppBarContentColor =
            preferencesManager.forceAppBarContentColor.collectAsState(initial = false).value
    val appBarContentColorMode =
            preferencesManager.appBarContentColorMode.collectAsState(
                            initial = UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT
                    )
                    .value

    val appBarContentColor =
            if (forceAppBarContentColor) {
                when (appBarContentColorMode) {
                    UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_LIGHT -> Color.White
                    UserPreferencesManager.APP_BAR_CONTENT_COLOR_MODE_DARK -> Color.Black
                    else -> MaterialTheme.colorScheme.onPrimary
                }
            } else {
                MaterialTheme.colorScheme.onPrimary
            }

    // 获取聊天历史管理器
    val chatHistoryManager = ChatHistoryManager.getInstance(context)
    val currentChatId = chatHistoryManager.currentChatIdFlow.collectAsState(initial = null).value
    val chatHistories =
            chatHistoryManager.chatHistoriesFlow.collectAsState(initial = emptyList()).value

    // Get custom chat title from preferences
    val customChatTitle by preferencesManager.customChatTitle.collectAsState(initial = null)


    // 当前聊天标题
    val currentChatTitle =
            if (currentChatId != null) {
                chatHistories.find { it.id == currentChatId }?.title ?: ""
            } else {
                ""
            }

    // 屏幕缓存 Map - 保存已访问过的屏幕，使其状态得以保留
    val screenCache = remember { mutableStateMapOf<String, @Composable () -> Unit>() }
    // 使用 Screen 对象的 toString() 作为 key，这对于 data class 和 data object 都能生成一个唯一的、
    // 包含其内部状态的字符串，从而实现通用且可靠的状态缓存。
    val currentScreenKey = currentScreen.toString()

    // 这是一个对前一个屏幕的引用。我们用它来识别当向后导航时要从缓存中删除哪个屏幕。
    // 在没有 `by` 委托的情况下使用 `mutableStateOf`，可以让我们在 `LaunchedEffect` 内部读写它，而不会导致父 Composable 重组。
    val previousScreenState = remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(currentScreen, isNavigatingBack) {
        val previousScreen = previousScreenState.value
        // 当 `isNavigatingBack` 为 true 时，表示 `currentScreen` 变为活动状态是因为用户向后导航了。
        // 他们导航 *来自* 的屏幕是 `previousScreen`。我们应该从缓存中删除这个现在被丢弃的屏幕。
        if (isNavigatingBack && previousScreen != null) {
            val keyToRemove = previousScreen.toString()
            // 作为保障，确保我们不会意外地删除当前屏幕。
            if (keyToRemove != currentScreenKey) {
                screenCache.remove(keyToRemove)
            }
        }
        // 为下一次导航事件更新我们对前一个屏幕的引用。
        previousScreenState.value = currentScreen
    }

    CompositionLocalProvider(LocalAppBarContentColor provides appBarContentColor) {
        // 使用Scaffold来正确处理顶部栏和内容的布局
        // contentWindowInsets = WindowInsets(0) 让内容可以延伸到系统栏下方，使背景能够完全填充
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // 单一工具栏 - 使用小型化的设计
                // 使用 windowInsets 参数让 TopAppBar 自己处理状态栏的 insets
                SmallTopAppBar(
                    windowInsets = WindowInsets.statusBars,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 使用Screen的标题或导航项的标题
                            Text(
                                text =
                                when {
                                    // 如果是AI对话界面且有自定义标题，则优先显示
                                    currentScreen is Screen.AiChat && !customChatTitle.isNullOrEmpty() ->
                                        customChatTitle!!
                                    // 优先使用Screen的标题
                                    currentScreen.getTitle().isNotBlank() ->
                                        currentScreen.getTitle()
                                    // 回退到导航项的标题资源
                                    selectedItem.titleResId != 0 ->
                                        stringResource(id = selectedItem.titleResId)
                                    // 最后的默认值
                                    else -> ""
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = appBarContentColor
                            )

                            // 显示当前聊天标题（仅在AI对话页面)
                            if (currentScreen is Screen.AiChat && currentChatTitle.isNotBlank()) {
                                Text(
                                    text = "- $currentChatTitle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appBarContentColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        // 导航按钮逻辑
                        IconButton(
                            onClick = {
                                if (canGoBack) {
                                    onGoBack()
                                } else {
                                    // 平板模式下切换侧边栏展开/收起状态
                                    if (useTabletLayout) {
                                        onToggleSidebar()
                                    } else {
                                        // 手机模式下打开抽屉
                                        scope.launch { drawerState.open() }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (canGoBack) Icons.Default.ArrowBack
                                else if (useTabletLayout)
                                // 平板模式下使用开关图标表示收起/展开
                                    if (isTabletSidebarExpanded) Icons.Filled.ChevronLeft
                                    else Icons.Default.Menu
                                else Icons.Default.Menu,
                                contentDescription =
                                when {
                                    canGoBack -> "返回"
                                    useTabletLayout ->
                                        if (isTabletSidebarExpanded) "收起侧边栏"
                                        else "展开侧边栏"
                                    else -> stringResource(id = R.string.menu)
                                },
                                tint = appBarContentColor
                            )
                        }
                    },
                    actions = actions,
                    colors =
                    TopAppBarDefaults.smallTopAppBarColors(
                        containerColor =
                        when {
                            toolbarTransparent -> Color.Transparent
                            useCustomAppBarColor && customAppBarColor != null -> Color(customAppBarColor)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        titleContentColor = appBarContentColor,
                        navigationIconContentColor = appBarContentColor,
                        actionIconContentColor = appBarContentColor
                    ),
                    // Scaffold会处理 insets, 这里不再需要手动添加 modifier
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            // 主内容区域
            // 添加底部导航栏的 padding，确保内容不会被导航栏遮挡
            Surface(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .fillMaxSize(),
                color =
                if (hasBackgroundImage) Color.Transparent
                else MaterialTheme.colorScheme.background
            ) {
                if (isLoading) {
                    // 加载中状态
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "正在加载...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    // 主要内容 - 使用 Box 堆叠所有访问过的屏幕，保留状态
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 将当前屏幕的 Composable 缓存起来
                        if (!screenCache.containsKey(currentScreenKey)) {
                            screenCache[currentScreenKey] = {
                                currentScreen.Content(
                                    navController = navController,
                                    navigateTo = onScreenChange,
                                    updateNavItem = onNavItemChange,
                                    onGoBack = onGoBack,
                                    hasBackgroundImage = hasBackgroundImage,
                                    onLoading = onLoading,
                                    onError = onError,
                                    onGestureConsumed = if (currentScreen is Screen.AiChat) onGestureConsumed else { _ -> }
                                )
                            }
                        }

                        // 优化渲染：只渲染当前屏幕和正在过渡的屏幕
                        // 记录上一个屏幕用于过渡动画
                        var previousScreenKey by remember { mutableStateOf<String?>(null) }
                        var isTransitioning by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(currentScreenKey) {
                            if (previousScreenKey != null && previousScreenKey != currentScreenKey) {
                                isTransitioning = true
                                // 等待动画完成后停止过渡状态
                                kotlinx.coroutines.delay(400) // 与动画时长一致
                                isTransitioning = false
                            }
                            previousScreenKey = currentScreenKey
                        }

                        screenCache.forEach { (screenKey, screenContent) ->
                            val isCurrentScreen = screenKey == currentScreenKey
                            val isPreviousScreen = screenKey == previousScreenKey && isTransitioning
                            
                            // 只渲染当前屏幕或正在过渡的上一个屏幕
                            if (isCurrentScreen || isPreviousScreen) {
                                key(screenKey) {
                                    // 为每个屏幕维护一个独立的可见性状态
                                    var visibility by remember { mutableStateOf(ScreenVisibility.HIDDEN) }

                                    // 使用 LaunchedEffect 在 isCurrentScreen 状态变化后更新可见性
                                    LaunchedEffect(isCurrentScreen) {
                                        visibility = if (isCurrentScreen) ScreenVisibility.VISIBLE else ScreenVisibility.HIDDEN
                                    }

                                    // 使用 updateTransition 来处理动画状态
                                    val transition = updateTransition(
                                        targetState = visibility,
                                        label = "ScreenVisibilityTransition"
                                    )

                                    val alpha by transition.animateFloat(
                                        transitionSpec = {
                                            tween(durationMillis = 400)
                                        },
                                        label = "ScreenAlphaAnimation"
                                    ) { visibility ->
                                        if (visibility == ScreenVisibility.VISIBLE) 1f else 0f
                                    }

                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .zIndex(if (isCurrentScreen) 1f else 0f)
                                                .graphicsLayer { this.alpha = alpha }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            CompositionLocalProvider(LocalIsCurrentScreen provides isCurrentScreen) {
                                                screenContent()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 帧率计数器 - 放在所有屏幕之上
                        if (showFpsCounter) {
                            FpsCounter(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 80.dp, end = 16.dp)
                                    .zIndex(2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

