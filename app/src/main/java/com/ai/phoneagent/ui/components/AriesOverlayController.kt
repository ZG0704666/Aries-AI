package com.ai.phoneagent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Activity 根级浮层控制器：让位于 LazyColumn 等受限容器中的内容
 * 把浮层渲染到 Activity 根节点，从而共享同一窗口的录制层与玻璃效果。
 */
@Stable
class AriesOverlayController {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    fun dismiss() {
        content = null
    }
}

val LocalAriesOverlayController = compositionLocalOf<AriesOverlayController?> { null }
