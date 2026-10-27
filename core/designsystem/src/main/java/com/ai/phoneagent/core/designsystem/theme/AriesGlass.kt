package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R

/**
 * 带边缘反光的透明玻璃效果：镜面内部不叠加不透明明亮层或阴影。
 *
 * 材质等级（Liquid Glass 材质系统）：
 * - Material A 交互玻璃层（真实背景折射，滚动/按压仍有 RenderEffect）：
 *     首页导航胶囊、底部输入框、悬浮操作按钮、浮动工具栏（ariesLiquidGlass/ariesLiquidInteractive，
 *     tier = Pill/Bar/Card/Chip）。
 * - Material B 标准内容材质（本函数）：设置列表、功能卡片、普通内容面板 ——
 *     零实时模糊、零背景采样，仅静态表面色 + 边缘高光 + 极细描边（滚动区零 GPU 特效成本），
 *     `selected` 提供与液态版一致的选中着色（浅色 #1A000000 / 深色 #1AFFFFFF）。
 * - Material C 模态浮层材质：弹窗/菜单走系统 Dialog 默认遮罩+各自 Surface。
 */
@Composable
fun Modifier.ariesGlass(shape: Shape, selected: Boolean = false): Modifier {
    val dark = LocalAriesBackdrop.current.dark
    val base = colorResource(if (dark) R.color.aries_glass_dark else R.color.aries_glass_light)
    val highlight = colorResource(if (dark) R.color.aries_glass_highlight_dark else R.color.aries_glass_highlight_light)
    val edge = colorResource(if (dark) R.color.aries_glass_edge_dark else R.color.aries_glass_edge_light)
    val selection = colorResource(if (dark) R.color.aries_glass_selected_dark else R.color.aries_glass_selected_light)
    // 卡片边界感：浅色下白玻璃与白背景融为一体，补一圈细描边；深色保持原有边缘高光。
    val border = colorResource(if (dark) R.color.aries_glass_border_dark else R.color.aries_glass_border_light)
    val rim = dimensionResource(R.dimen.aries_hairline)
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val lens = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val reflection = Brush.linearGradient(
            0f to highlight, .22f to edge, .48f to edge.copy(alpha = edge.alpha * .22f),
            .73f to highlight.copy(alpha = highlight.alpha * .65f), 1f to edge,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        onDrawBehind {
            drawPath(lens, base)
            if (selected) drawPath(lens, selection)
            // 将半描边裁剪到镜面内，避免边框影响布局尺寸。
            clipPath(lens) { drawPath(lens, reflection, style = Stroke(width = rim.toPx() * 2)) }
            if (border.alpha > 0f) {
                drawPath(lens, border, style = Stroke(width = rim.toPx() * 2))
            }
        }
    }
}
