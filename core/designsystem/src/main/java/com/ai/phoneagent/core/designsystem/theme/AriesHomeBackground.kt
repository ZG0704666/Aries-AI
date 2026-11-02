package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/** 在主页已有的 backdrop 层内绘制局部渐变，避免增加录制层或影响其他页面的卡片材质。 */
@Composable
fun Modifier.ariesHomeBackground(): Modifier {
    val actionStyle = ariesActionStyle()
    val background = MaterialTheme.colorScheme.background
    val amoled = LocalAriesBackdrop.current.amoled
    return drawWithCache {
        val top = Brush.radialGradient(
            colors = listOf(actionStyle.homeGlowStart, Color.Transparent),
            center = Offset(size.width * 0.18f, 0f), radius = size.width * 0.85f,
        )
        val corner = Brush.radialGradient(
            colors = listOf(actionStyle.homeGlowEnd, Color.Transparent),
            center = Offset(size.width, size.height * 0.12f), radius = size.width * 0.65f,
        )
        onDrawBehind {
            drawRect(background)
            if (!amoled) {
                drawRect(top)
                drawRect(corner)
            }
        }
    }
}
