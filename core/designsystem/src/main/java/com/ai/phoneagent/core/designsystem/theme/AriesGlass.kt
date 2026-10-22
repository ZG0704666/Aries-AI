package com.ai.phoneagent.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.core.designsystem.R
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 液态玻璃渲染参数，按亮/暗主题取值。
 *
 * @property blur 背景模糊半径。
 * @property refractionHeight 折射影响的高度。
 * @property refractionAmount 折射位移量。
 * @property contrast 采样背景的对比度。
 * @property saturation 采样背景的饱和度。
 * @property highlight 边缘镜面高光。
 * @property shadow 玻璃外侧投影。
 * @property innerShadow 玻璃内侧暗角。
 * @property edgeStart 描边渐变起点（左上高光）。
 * @property edgeMid 描边渐变中点。
 * @property edgeEnd 描边渐变终点（右下暗部）。
 */
private class AriesGlassStyle(
    val blur: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val contrast: Float,
    val saturation: Float,
    val highlight: Highlight,
    val shadow: Shadow,
    val innerShadow: InnerShadow,
    val edgeStart: Color,
    val edgeMid: Color,
    val edgeEnd: Color,
)

/**
 * 带边缘反光的透明玻璃效果：镜面内部不叠加不透明明亮层或阴影。
 *
 * - 亮色：玻璃底色为淡冷灰，叠加一根内描边（左上高光 → 右下暗部）与柔和外投影，
 *   避免在浅色背景上与背景混成一片。
 * - 暗色：底色大幅降低不透明度并提升采样饱和度，保留背景色彩层次，再叠加折射、
 *   镜面高光与内侧暗角，还原参考实现中的通透质感。
 *
 * 有可用背景层时采样真实背景做模糊与折射；否则（如 API 30 或在独立窗口中）
 * 回退到静态渐变玻璃。
 *
 * @param shape 玻璃外形。
 * @param selected 是否为选中态（叠加一层强调色）。
 * @param elevated 是否绘制外投影；列表中大量小气泡可关闭以节省图层。
 */
@Composable
fun Modifier.ariesGlass(
    shape: Shape,
    selected: Boolean = false,
    elevated: Boolean = true,
): Modifier {
    val dark = LocalAriesBackdrop.current.dark
    val backdrop = LocalAriesLayerBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return staticAriesGlass(shape, selected, dark, elevated)
    }
    val style = ariesGlassStyle(dark)
    val base = colorResource(if (dark) R.color.aries_glass_dark else R.color.aries_glass_light)
    val selection = colorResource(
        if (dark) R.color.aries_glass_selected_dark else R.color.aries_glass_selected_light,
    )
    val edgeWidth = dimensionResource(R.dimen.aries_glass_edge_width)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(contrast = style.contrast, saturation = style.saturation)
            blur(style.blur.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shape.supportsLens()) {
                lens(style.refractionHeight.toPx(), style.refractionAmount.toPx(), depthEffect = true)
            }
        },
        highlight = { style.highlight },
        shadow = { if (elevated) style.shadow else null },
        innerShadow = { style.innerShadow },
        onDrawSurface = {
            val path = shape.createPath(size, layoutDirection, this)
            drawPath(path, base)
            if (selected) drawPath(path, selection)
            // 描边按两倍宽度绘制：外侧一半被形状裁掉，可见宽度正好是一根发丝线。
            drawPath(
                path,
                Brush.linearGradient(
                    0f to style.edgeStart,
                    0.55f to style.edgeMid,
                    1f to style.edgeEnd,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = edgeWidth.toPx() * 2f),
            )
        },
    )
}

/** 按主题组装玻璃渲染参数。 */
@Composable
private fun ariesGlassStyle(dark: Boolean): AriesGlassStyle {
    val edgeWidth = dimensionResource(R.dimen.aries_glass_edge_width)
    val highlight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Highlight.Default.copy(
            width = edgeWidth,
            blurRadius = edgeWidth,
            style = HighlightStyle.Default(color = Color.White.copy(alpha = if (dark) 0.55f else 0.85f)),
        )
    } else {
        Highlight.Plain.copy(
            width = edgeWidth,
            blurRadius = edgeWidth,
            style = HighlightStyle.Plain(color = Color.White.copy(alpha = if (dark) 0.45f else 0.8f)),
        )
    }
    val shadowRadius = dimensionResource(
        if (dark) R.dimen.aries_glass_shadow_radius_dark else R.dimen.aries_glass_shadow_radius_light,
    )
    val innerShadowRadius = dimensionResource(
        if (dark) R.dimen.aries_glass_inner_shadow_radius_dark else R.dimen.aries_glass_inner_shadow_radius_light,
    )
    return AriesGlassStyle(
        blur = dimensionResource(if (dark) R.dimen.aries_glass_blur_dark else R.dimen.aries_glass_blur_light),
        refractionHeight = dimensionResource(
            if (dark) R.dimen.aries_glass_refraction_dark else R.dimen.aries_glass_refraction_light,
        ),
        refractionAmount = dimensionResource(
            if (dark) R.dimen.aries_glass_refraction_amount_dark else R.dimen.aries_glass_refraction_amount_light,
        ),
        // 亮色保持克制；暗色按参考实现提升饱和度，透出背景色彩。
        contrast = if (dark) 1.08f else 1.04f,
        saturation = if (dark) 1.5f else 1.12f,
        highlight = highlight,
        shadow = Shadow(
            radius = shadowRadius,
            offset = DpOffset(
                0.dp,
                dimensionResource(
                    if (dark) R.dimen.aries_glass_shadow_offset_dark else R.dimen.aries_glass_shadow_offset_light,
                ),
            ),
            color = colorResource(if (dark) R.color.aries_glass_shadow_dark else R.color.aries_glass_shadow_light),
        ),
        innerShadow = InnerShadow(
            radius = innerShadowRadius,
            offset = DpOffset(0.dp, innerShadowRadius),
            color = colorResource(
                if (dark) R.color.aries_glass_inner_shadow_dark else R.color.aries_glass_inner_shadow_light,
            ),
        ),
        edgeStart = colorResource(if (dark) R.color.aries_glass_edge_dark else R.color.aries_glass_edge_light),
        edgeMid = colorResource(if (dark) R.color.aries_glass_edge_fade_dark else R.color.aries_glass_edge_fade_light),
        edgeEnd = colorResource(
            if (dark) R.color.aries_glass_edge_shadow_dark else R.color.aries_glass_edge_shadow_light,
        ),
    )
}

/** 无可用背景层时的静态玻璃：保留渐变反光、外侧羽化阴影与内描边。 */
@Composable
private fun Modifier.staticAriesGlass(
    shape: Shape,
    selected: Boolean,
    dark: Boolean,
    elevated: Boolean,
): Modifier {
    val base = colorResource(if (dark) R.color.aries_glass_dark else R.color.aries_glass_light)
    val highlight = colorResource(
        if (dark) R.color.aries_glass_highlight_dark else R.color.aries_glass_highlight_light,
    )
    val edge = colorResource(if (dark) R.color.aries_glass_edge_dark else R.color.aries_glass_edge_light)
    val edgeMid = colorResource(
        if (dark) R.color.aries_glass_edge_fade_dark else R.color.aries_glass_edge_fade_light,
    )
    val edgeEnd = colorResource(
        if (dark) R.color.aries_glass_edge_shadow_dark else R.color.aries_glass_edge_shadow_light,
    )
    val shade = colorResource(if (dark) R.color.aries_glass_shadow_dark else R.color.aries_glass_shadow_light)
    val selection = colorResource(
        if (dark) R.color.aries_glass_selected_dark else R.color.aries_glass_selected_light,
    )
    val rim = dimensionResource(R.dimen.aries_glass_edge_width)
    val spread = dimensionResource(R.dimen.aries_glass_shadow_spread)
    return drawWithCache {
        val path = shape.createPath(size, layoutDirection, this)
        val reflection = Brush.linearGradient(
            0f to highlight, .22f to edge, .48f to edge.copy(alpha = edge.alpha * .22f),
            .73f to highlight.copy(alpha = highlight.alpha * .65f), 1f to edge,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        val outline = Brush.linearGradient(
            0f to edge, .55f to edgeMid, 1f to edgeEnd,
            start = Offset.Zero, end = Offset(size.width, size.height),
        )
        onDrawBehind {
            if (elevated) {
                // 部分 Android 渲染器会把 elevation 阴影填充进透明轮廓，
                // 因此改为在镜面「外侧」绘制逐层递减的羽化环。
                clipPath(path, ClipOp.Difference) {
                    for (layer in 8 downTo 1) {
                        drawPath(path, shade.copy(alpha = shade.alpha * (9 - layer) / 36f),
                            style = Stroke(width = spread.toPx() * layer / 4f))
                    }
                }
            }
            drawPath(path, base)
            if (selected) drawPath(path, selection)
            // 将半描边裁剪到镜面内，避免边框影响布局尺寸。
            clipPath(path) { drawPath(path, reflection, style = Stroke(width = rim.toPx() * 2)) }
            clipPath(path) { drawPath(path, outline, style = Stroke(width = rim.toPx() * 2)) }
        }
    }
}

private fun Shape.supportsLens(): Boolean = this is CornerBasedShape || this is AbsoluteRoundedCornerShape

private fun Shape.createPath(size: Size, layoutDirection: LayoutDirection, density: Density): Path =
    Path().apply {
        when (val outline = createOutline(size, layoutDirection, density)) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
