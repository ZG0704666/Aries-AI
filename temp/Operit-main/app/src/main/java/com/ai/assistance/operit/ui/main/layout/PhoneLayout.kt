package com.ai.assistance.operit.ui.main.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavGroup
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.components.DrawerContent
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.RowScope

/** Layout for phone devices with a modal navigation drawer */
@Composable
fun PhoneLayout(
        currentScreen: Screen,
        selectedItem: NavItem,
        isLoading: Boolean,
        navGroups: List<NavGroup>,
        isNetworkAvailable: Boolean,
        networkType: String,
        drawerWidth: Dp,
        navController: androidx.navigation.NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        onScreenChange: (Screen) -> Unit,
        onNavItemChange: (NavItem) -> Unit,
        navigateToTokenConfig: () -> Unit,
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        topBarActions: @Composable RowScope.() -> Unit = {}
) {
        // 使用 updateTransition 来创建更复杂的动画
        val transition = updateTransition(drawerState.targetValue, label = "drawer_transition")

        val animatedOffset by
                transition.animateDp(
                        label = "contentOffset",
                        transitionSpec = {
                                if (targetState == DrawerValue.Open) {
                                        spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = 1000f
                                        )
                                } else {
                                        spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = 1000f
                                        )
                                }
                        }
                ) { state -> if (state == DrawerValue.Open) drawerWidth else 0.dp }

        // 抽屉动画状态
        val isDrawerOpen =
                drawerState.currentValue == DrawerValue.Open ||
                        drawerState.targetValue == DrawerValue.Open

        val drawerOffset by
                transition.animateDp(
                        label = "drawerOffset",
                        transitionSpec = {
                                if (targetState == DrawerValue.Open) {
                                        spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = 1000f
                                        )
                                } else {
                                        spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = 1000f
                                        )
                                }
                        }
                ) { state -> if (state == DrawerValue.Open) 0.dp else -drawerWidth }

        // 阴影大小动画
        val sidebarElevation by
                animateDpAsState(
                        targetValue = if (isDrawerOpen) 3.dp else 0.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "sidebarElevation"
                )

        // 侧边栏内容透明度动画 - 使抽屉内容更流畅
        val drawerContentAlpha by
                animateFloatAsState(
                        targetValue = if (isDrawerOpen) 1f else 0.8f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "drawerContentAlpha"
                )

        // 侧边栏相关拖拽状态
        var currentDrag by remember { mutableStateOf(0f) }
        var verticalDrag by remember { mutableStateOf(0f) }
        val dragThreshold = 40f

        // 添加内部手势消费状态
        var internalGestureConsumed by remember { mutableStateOf(false) }

        // 缓存抽屉内容以避免重组
        var cachedDrawerContent by remember { mutableStateOf<@Composable () -> Unit>({}) }

        // 仅在相关数据变化时更新抽屉内容
        LaunchedEffect(navGroups, currentScreen, selectedItem, isNetworkAvailable, networkType) {
                cachedDrawerContent = {
                        DrawerContent(
                                navGroups = navGroups,
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                scope = scope,
                                drawerState = drawerState,
                                onScreenSelected = { screen, item ->
                                        onScreenChange(screen)
                                        onNavItemChange(item)
                                }
                        )
                }
        }

        // 拖拽状态 - 用于控制抽屉拉出和关闭
        val draggableState = rememberDraggableState { delta ->
                // 如果内部手势已被消费，则不处理全局拖拽
                if (!GestureStateHolder.isChatScreenGestureConsumed) {
                        currentDrag += delta

                        if (!isDrawerOpen &&
                                        currentDrag > dragThreshold &&
                                        Math.abs(currentDrag) > Math.abs(verticalDrag)
                        ) {
                                scope.launch {
                                        drawerState.open()
                                        currentDrag = 0f
                                        verticalDrag = 0f
                                }
                        }

                        if (isDrawerOpen && currentDrag < -dragThreshold) {
                                scope.launch {
                                        drawerState.close()
                                        currentDrag = 0f
                                        verticalDrag = 0f
                                }
                        }
                }
        }

        // 使用Box布局来手动控制抽屉和内容的位置关系
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .draggable(
                                        state = draggableState,
                                        orientation = Orientation.Horizontal,
                                        onDragStarted = {
                                                currentDrag = 0f
                                                verticalDrag = 0f
                                        },
                                        onDragStopped = {
                                                currentDrag = 0f
                                                verticalDrag = 0f
                                        }
                                )
                                .draggable(
                                        state =
                                                rememberDraggableState { delta ->
                                                        verticalDrag += delta
                                                },
                                        orientation = Orientation.Vertical,
                                        onDragStarted = { /* 不需要额外操作 */},
                                        onDragStopped = { /* 不需要额外操作 */}
                                )
        ) {
                // 主内容区域 - 使用自定义布局修饰符优化性能
                // 该修饰符只会影响布局，不会触发内容重组
                Box(
                    modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer { translationX = animatedOffset.toPx() }
                ) {
                    // 普通调用AppContent，但由于我们的优化，它不会在动画时重组
                    AppContent(
                        currentScreen = currentScreen,
                        selectedItem = selectedItem,
                        useTabletLayout = false,
                        isTabletSidebarExpanded = false,
                        isLoading = isLoading,
                        navController = navController,
                        scope = scope,
                        drawerState = drawerState,
                        showFpsCounter = showFpsCounter,
                        onScreenChange = onScreenChange,
                        onNavItemChange = onNavItemChange,
                        onToggleSidebar = { /* Not used in phone layout */},
                        navigateToTokenConfig = navigateToTokenConfig,
                        canGoBack = canGoBack,
                        onGoBack = onGoBack,
                        isNavigatingBack = isNavigatingBack,
                        actions = topBarActions
                    )
                }

                // // 添加一个小方块，填充圆角和工具栏之间的空隙
                // Box(
                //         modifier =
                //                 Modifier.width(16.dp)
                //                         .height(64.dp)
                //                         .offset(x = drawerOffset + drawerWidth - 16.dp)
                //                         .background(MaterialTheme.colorScheme.primary)
                //                         .zIndex(1f)
                // )

                // 抽屉内容，从左侧滑动进入 - 使用缓存内容
                Surface(
                        modifier =
                                Modifier.width(drawerWidth)
                                        .fillMaxHeight()
                                        .graphicsLayer { translationX = drawerOffset.toPx() }
                                        .zIndex(2f),
                        shape =
                                MaterialTheme.shapes.medium.copy(
                                        topEnd = CornerSize(16.dp),
                                        bottomEnd = CornerSize(16.dp),
                                        topStart = CornerSize(0.dp),
                                        bottomStart = CornerSize(0.dp)
                                ),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = sidebarElevation
                ) {
                        // 使用缓存的抽屉内容
                        Box(modifier = Modifier.fillMaxSize()) { cachedDrawerContent() }
                }

                // 移除黑色遮罩层，改为透明的可点击区域以关闭抽屉
                if (isDrawerOpen) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .offset(x = drawerWidth)
                                                .zIndex(0.5f)
                                                .clickable(
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                },
                                                        indication = null
                                                ) { scope.launch { drawerState.close() } }
                        )
                }
        }
}
