package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 滚动到底部按钮组件
 * 
 * @param scrollState 滚动状态
 * @param coroutineScope 协程作用域
 * @param autoScrollToBottom 是否自动滚动到底部
 * @param onAutoScrollToBottomChange 自动滚动状态变化回调
 * @param modifier 修饰符
 */
@Composable
fun ScrollToBottomButton(
    scrollState: ScrollState,
    coroutineScope: CoroutineScope,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScrollButton by remember { mutableStateOf(false) }
    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()

    // 核心滚动逻辑 - 监听用户的手动滚动行为
    LaunchedEffect(scrollState) {
        var lastPosition = scrollState.value
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { currentPosition ->
                // isScrollInProgress 为 true 表示正在滚动（包括动画和手势）
                if (scrollState.isScrollInProgress) {
                    val scrolledUp = currentPosition < lastPosition
                    if (scrolledUp) {
                        // 用户向上滚动，禁用自动滚动并显示按钮
                        // 只有在用户实际拖动时才禁用自动滚动，避免被自动滚动动画触发
                        if (autoScrollToBottom && isDragged) {
                            onAutoScrollToBottomChange(false)
                            showScrollButton = true
                        }
                    } else {
                        // 用户向下滚动，检查是否已到达底部
                        val isAtBottom = scrollState.value >= scrollState.maxValue
                        if (isAtBottom && !autoScrollToBottom) {
                            onAutoScrollToBottomChange(true)
                            showScrollButton = false
                        }
                    }
                }
                lastPosition = currentPosition
            }
    }

    AnimatedVisibility(
        visible = showScrollButton,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        IconButton(
            onClick = {
                coroutineScope.launch {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                onAutoScrollToBottomChange(true) // 重新启用自动滚动
                showScrollButton = false // 点击后隐藏按钮
            },
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll to bottom",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
