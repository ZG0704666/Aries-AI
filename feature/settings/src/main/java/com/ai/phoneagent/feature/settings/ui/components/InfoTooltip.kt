package com.ai.phoneagent.feature.settings.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Info
import kotlinx.coroutines.launch

/**
 * 信息提示气泡组件 - Material 3 PlainTooltip
 *
 * 点击 ℹ️ 图标弹出轻量级气泡提示，点击外部区域自动消失
 *
 * @param tooltipText 气泡内显示的文本
 * @param modifier 自定义样式修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoTooltip(
    tooltipText: String,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState()
    val coroutineScope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text = tooltipText)
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        Icon(
            imageVector = Lucide.Info,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .clickable { coroutineScope.launch { tooltipState.show() } },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
